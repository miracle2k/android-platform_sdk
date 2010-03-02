/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.eclipse.org/org/documents/epl-v10.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.ide.eclipse.adt.internal.sdk;

import com.android.ddmlib.IDevice;
import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.internal.project.AndroidClasspathContainerInitializer;
import com.android.ide.eclipse.adt.internal.project.BaseProjectHelper;
import com.android.ide.eclipse.adt.internal.project.ProjectState;
import com.android.ide.eclipse.adt.internal.project.ProjectState.LibraryState;
import com.android.ide.eclipse.adt.internal.resources.manager.GlobalProjectMonitor;
import com.android.ide.eclipse.adt.internal.resources.manager.GlobalProjectMonitor.IFileListener;
import com.android.ide.eclipse.adt.internal.resources.manager.GlobalProjectMonitor.IProjectListener;
import com.android.ide.eclipse.adt.internal.sdk.AndroidTargetData.LayoutBridge;
import com.android.prefs.AndroidLocation.AndroidLocationException;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.ISdkLog;
import com.android.sdklib.SdkConstants;
import com.android.sdklib.SdkManager;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.sdklib.internal.project.ApkConfigurationHelper;
import com.android.sdklib.internal.project.ApkSettings;
import com.android.sdklib.internal.project.ProjectProperties;
import com.android.sdklib.internal.project.ProjectProperties.PropertyType;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarkerDelta;
import org.eclipse.core.resources.IPathVariableManager;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Central point to load, manipulate and deal with the Android SDK. Only one SDK can be used
 * at the same time.
 *
 * To start using an SDK, call {@link #loadSdk(String)} which returns the instance of
 * the Sdk object.
 *
 * To get the list of platforms or add-ons present in the SDK, call {@link #getTargets()}.
 */
public final class Sdk  {
    private final static Object sLock = new Object();

    private static Sdk sCurrentSdk = null;

    /**
     * Map associating {@link IProject} and their state {@link ProjectState}.
     * <p/>This <b>MUST NOT</b> be accessed directly. Instead use {@link #getProject(IProject)}.
     */
    private final static HashMap<IProject, ProjectState> sProjectStateMap =
            new HashMap<IProject, ProjectState>();

    /**
     * Data bundled using during the load of Target data.
     * <p/>This contains the {@link LoadStatus} and a list of projects that attempted
     * to compile before the loading was finished. Those projects will be recompiled
     * at the end of the loading.
     */
    private final static class TargetLoadBundle {
        LoadStatus status;
        final HashSet<IJavaProject> projecsToReload = new HashSet<IJavaProject>();
    }

    private final SdkManager mManager;
    private final AvdManager mAvdManager;

    /** Map associating an {@link IAndroidTarget} to an {@link AndroidTargetData} */
    private final HashMap<IAndroidTarget, AndroidTargetData> mTargetDataMap =
        new HashMap<IAndroidTarget, AndroidTargetData>();
    /** Map associating an {@link IAndroidTarget} and its {@link TargetLoadBundle}. */
    private final HashMap<IAndroidTarget, TargetLoadBundle> mTargetDataStatusMap =
        new HashMap<IAndroidTarget, TargetLoadBundle>();

    private final String mDocBaseUrl;

    private final LayoutDeviceManager mLayoutDeviceManager = new LayoutDeviceManager();

    /**
     * Classes implementing this interface will receive notification when targets are changed.
     */
    public interface ITargetChangeListener {
        /**
         * Sent when project has its target changed.
         */
        void onProjectTargetChange(IProject changedProject);

        /**
         * Called when the targets are loaded (either the SDK finished loading when Eclipse starts,
         * or the SDK is changed).
         */
        void onTargetLoaded(IAndroidTarget target);

        /**
         * Called when the base content of the SDK is parsed.
         */
        void onSdkLoaded();
    }

    /**
     * Basic abstract implementation of the ITargetChangeListener for the case where both
     * {@link #onProjectTargetChange(IProject)} and {@link #onTargetLoaded(IAndroidTarget)}
     * use the same code based on a simple test requiring to know the current IProject.
     */
    public static abstract class TargetChangeListener implements ITargetChangeListener {
        /**
         * Returns the {@link IProject} associated with the listener.
         */
        public abstract IProject getProject();

        /**
         * Called when the listener needs to take action on the event. This is only called
         * if {@link #getProject()} and the {@link IAndroidTarget} associated with the project
         * match the values received in {@link #onProjectTargetChange(IProject)} and
         * {@link #onTargetLoaded(IAndroidTarget)}.
         */
        public abstract void reload();

        public void onProjectTargetChange(IProject changedProject) {
            if (changedProject != null && changedProject.equals(getProject())) {
                reload();
            }
        }

        public void onTargetLoaded(IAndroidTarget target) {
            IProject project = getProject();
            if (target != null && target.equals(Sdk.getCurrent().getTarget(project))) {
                reload();
            }
        }

        public void onSdkLoaded() {
            // do nothing;
        }
    }

    /**
     * Returns the lock object used to synchronize all operations dealing with SDK, targets and
     * projects.
     */
    public static final Object getLock() {
        return sLock;
    }

    /**
     * Loads an SDK and returns an {@link Sdk} object if success.
     * <p/>If the SDK failed to load, it displays an error to the user.
     * @param sdkLocation the OS path to the SDK.
     */
    public static Sdk loadSdk(String sdkLocation) {
        synchronized (sLock) {
            if (sCurrentSdk != null) {
                sCurrentSdk.dispose();
                sCurrentSdk = null;
            }

            final ArrayList<String> logMessages = new ArrayList<String>();
            ISdkLog log = new ISdkLog() {
                public void error(Throwable throwable, String errorFormat, Object... arg) {
                    if (errorFormat != null) {
                        logMessages.add(String.format("Error: " + errorFormat, arg));
                    }

                    if (throwable != null) {
                        logMessages.add(throwable.getMessage());
                    }
                }

                public void warning(String warningFormat, Object... arg) {
                    logMessages.add(String.format("Warning: " + warningFormat, arg));
                }

                public void printf(String msgFormat, Object... arg) {
                    logMessages.add(String.format(msgFormat, arg));
                }
            };

            // get an SdkManager object for the location
            SdkManager manager = SdkManager.createManager(sdkLocation, log);
            if (manager != null) {
                AvdManager avdManager = null;
                try {
                    avdManager = new AvdManager(manager, log);
                } catch (AndroidLocationException e) {
                    log.error(e, "Error parsing the AVDs");
                }
                sCurrentSdk = new Sdk(manager, avdManager);
                return sCurrentSdk;
            } else {
                StringBuilder sb = new StringBuilder("Error Loading the SDK:\n");
                for (String msg : logMessages) {
                    sb.append('\n');
                    sb.append(msg);
                }
                AdtPlugin.displayError("Android SDK", sb.toString());
            }
            return null;
        }
    }

    /**
     * Returns the current {@link Sdk} object.
     */
    public static Sdk getCurrent() {
        synchronized (sLock) {
            return sCurrentSdk;
        }
    }

    /**
     * Returns the location (OS path) of the current SDK.
     */
    public String getSdkLocation() {
        return mManager.getLocation();
    }

    /**
     * Returns the URL to the local documentation.
     * Can return null if no documentation is found in the current SDK.
     *
     * @return A file:// URL on the local documentation folder if it exists or null.
     */
    public String getDocumentationBaseUrl() {
        return mDocBaseUrl;
    }

    /**
     * Returns the list of targets that are available in the SDK.
     */
    public IAndroidTarget[] getTargets() {
        return mManager.getTargets();
    }

    /**
     * Returns a target from a hash that was generated by {@link IAndroidTarget#hashString()}.
     *
     * @param hash the {@link IAndroidTarget} hash string.
     * @return The matching {@link IAndroidTarget} or null.
     */
    public IAndroidTarget getTargetFromHashString(String hash) {
        return mManager.getTargetFromHashString(hash);
    }

    /**
     * Initializes a new project with a target. This creates the <code>default.properties</code>
     * file.
     * @param project the project to intialize
     * @param target the project's target.
     * @throws IOException if creating the file failed in any way.
     */
    public void initProject(IProject project, IAndroidTarget target) throws IOException {
        if (project == null || target == null) {
            return;
        }

        synchronized (sLock) {
            // check if there's already a state?
            ProjectState state = getProject(project);

            ProjectProperties properties = null;

            if (state != null) {
                properties = state.getProperties();
            }

            if (properties == null) {
                IPath location = project.getLocation();
                if (location == null) {  // can return null when the project is being deleted.
                    // do nothing and return null;
                    return;
                }

                properties = ProjectProperties.create(location.toOSString(), PropertyType.DEFAULT);
            }

            // save the target hash string in the project persistent property
            properties.setProperty(ProjectProperties.PROPERTY_TARGET, target.hashString());
            properties.save();
        }
    }

    /**
     * Sets a new target and/or a new set of APK settings for a given project.
     *
     * @param project the project to receive the new apk configurations.
     * @param target The new target to set, or <code>null</code> to not change the current target.
     * @param settings a new {@link ApkSettings} object to set or <code>null</code> to not change
     * the current settings.
     */
    public void setProject(IProject project, IAndroidTarget target,
            ApkSettings settings) {
        if (target == null && settings == null) {
            return;
        }


        synchronized (sLock) {
            boolean resolveProject = false;

            ProjectState state = getProject(project);
            if (state == null) {
                return;
            }

            ProjectProperties properties = state.getProperties();

            if (target != null) {
                // look for the current target of the project
                IAndroidTarget previousTarget = state.getTarget();

                if (target != previousTarget) {
                    // save the target hash string in the project persistent property
                    properties.setProperty(ProjectProperties.PROPERTY_TARGET, target.hashString());

                    // put it in a local map for easy access.
                    state.setTarget(target);

                    resolveProject = true;
                }
            }

            if (settings != null) {
                state.setApkSettings(settings);

                // save the project settings into the project persistent property
                ApkConfigurationHelper.setProperties(properties, settings);
            }

            // we are done with the modification. Save the property file.
            try {
                properties.save();
            } catch (IOException e) {
                AdtPlugin.log(e, "Failed to save default.properties for project '%s'",
                        project.getName());
            }

            if (resolveProject) {
                // force a resolve of the project by updating the classpath container.
                // This will also force a recompile.
                IJavaProject javaProject = JavaCore.create(project);
                AndroidClasspathContainerInitializer.updateProjects(
                        new IJavaProject[] { javaProject });
            } else {
                // always do a full clean/build.
                try {
                    project.build(IncrementalProjectBuilder.CLEAN_BUILD, null);
                } catch (CoreException e) {
                    // failed to build? force resolve instead.
                    IJavaProject javaProject = JavaCore.create(project);
                    AndroidClasspathContainerInitializer.updateProjects(
                            new IJavaProject[] { javaProject });
                }
            }

            // finally, update the opened editors.
            if (resolveProject) {
                AdtPlugin.getDefault().updateTargetListeners(project);
            }
        }
    }

    /**
     * Returns the {@link ProjectState} object associated with a given project.
     * <p/>
     * This method is the only way to properly get the project's {@link ProjectState}
     * If the project has not yet been loaded, then it is loaded.
     * <p/>Because this methods deals with projects, it's not linked to an actual {@link Sdk}
     * objects, and therefore is static.
     * <p/>The value returned by {@link ProjectState#getTarget()} will change as {@link Sdk} objects
     * are replaced.
     * @param project the request project
     * @return the ProjectState for the project.
     */
    public static ProjectState getProject(IProject project) {
        if (project == null) {
            return null;
        }

        synchronized (sLock) {
            ProjectState state = sProjectStateMap.get(project);
            if (state == null) {
                // load the default.properties from the project folder.
                IPath location = project.getLocation();
                if (location == null) {  // can return null when the project is being deleted.
                    // do nothing and return null;
                    return null;
                }

                ProjectProperties properties = ProjectProperties.load(location.toOSString(),
                        PropertyType.DEFAULT);
                if (properties == null) {
                    AdtPlugin.log(IStatus.ERROR, "Failed to load properties file for project '%s'",
                            project.getName());
                    return null;
                }

                state = new ProjectState(project, properties);
                sProjectStateMap.put(project, state);

                // try to resolve the target
                if (AdtPlugin.getDefault().getSdkLoadStatus() == LoadStatus.LOADED) {
                    sCurrentSdk.loadTarget(state);
                }
            }

            return state;
        }
    }

    /**
     * Returns the {@link IAndroidTarget} object associated with the given {@link IProject}.
     */
    public IAndroidTarget getTarget(IProject project) {
        if (project == null) {
            return null;
        }

        ProjectState state = getProject(project);
        if (state != null) {
            return state.getTarget();
        }

        return null;
    }

    /**
     * Loads the {@link IAndroidTarget} for a given project.
     * <p/>This method will get the target hash string from the project properties, and resolve
     * it to an {@link IAndroidTarget} object and store it inside the {@link ProjectState}.
     * @param state the state representing the project to load.
     */
    public void loadTarget(ProjectState state) {
        String hash = state.getTargetHashString();
        if (hash != null) {
            state.setTarget(getTargetFromHashString(hash));
        }
    }

    /**
     * Checks and loads (if needed) the data for a given target.
     * <p/> The data is loaded in a separate {@link Job}, and opened editors will be notified
     * through their implementation of {@link ITargetChangeListener#onTargetLoaded(IAndroidTarget)}.
     * <p/>An optional project as second parameter can be given to be recompiled once the target
     * data is finished loading.
     * <p/>The return value is non-null only if the target data has already been loaded (and in this
     * case is the status of the load operation)
     * @param target the target to load.
     * @param project an optional project to be recompiled when the target data is loaded.
     * If the target is already loaded, nothing happens.
     * @return The load status if the target data is already loaded.
     */
    public LoadStatus checkAndLoadTargetData(final IAndroidTarget target, IJavaProject project) {
        boolean loadData = false;

        synchronized (sLock) {
            TargetLoadBundle bundle = mTargetDataStatusMap.get(target);
            if (bundle == null) {
                bundle = new TargetLoadBundle();
                mTargetDataStatusMap.put(target,bundle);

                // set status to loading
                bundle.status = LoadStatus.LOADING;

                // add project to bundle
                if (project != null) {
                    bundle.projecsToReload.add(project);
                }

                // and set the flag to start the loading below
                loadData = true;
            } else if (bundle.status == LoadStatus.LOADING) {
                // add project to bundle
                if (project != null) {
                    bundle.projecsToReload.add(project);
                }

                return bundle.status;
            } else if (bundle.status == LoadStatus.LOADED || bundle.status == LoadStatus.FAILED) {
                return bundle.status;
            }
        }

        if (loadData) {
            Job job = new Job(String.format("Loading data for %1$s", target.getFullName())) {
                @Override
                protected IStatus run(IProgressMonitor monitor) {
                    AdtPlugin plugin = AdtPlugin.getDefault();
                    try {
                        IStatus status = new AndroidTargetParser(target).run(monitor);

                        IJavaProject[] javaProjectArray = null;

                        synchronized (sLock) {
                            TargetLoadBundle bundle = mTargetDataStatusMap.get(target);

                            if (status.getCode() != IStatus.OK) {
                                bundle.status = LoadStatus.FAILED;
                                bundle.projecsToReload.clear();
                            } else {
                                bundle.status = LoadStatus.LOADED;

                                // Prepare the array of project to recompile.
                                // The call is done outside of the synchronized block.
                                javaProjectArray = bundle.projecsToReload.toArray(
                                        new IJavaProject[bundle.projecsToReload.size()]);

                                // and update the UI of the editors that depend on the target data.
                                plugin.updateTargetListeners(target);
                            }
                        }

                        if (javaProjectArray != null) {
                            AndroidClasspathContainerInitializer.updateProjects(javaProjectArray);
                        }

                        return status;
                    } catch (Throwable t) {
                        synchronized (sLock) {
                            TargetLoadBundle bundle = mTargetDataStatusMap.get(target);
                            bundle.status = LoadStatus.FAILED;
                        }

                        AdtPlugin.log(t, "Exception in checkAndLoadTargetData.");    //$NON-NLS-1$
                        return new Status(IStatus.ERROR, AdtPlugin.PLUGIN_ID,
                                String.format(
                                        "Parsing Data for %1$s failed", //$NON-NLS-1$
                                        target.hashString()),
                                t);
                    }
                }
            };
            job.setPriority(Job.BUILD); // build jobs are run after other interactive jobs
            job.schedule();
        }

        // The only way to go through here is when the loading starts through the Job.
        // Therefore the current status of the target is LOADING.
        return LoadStatus.LOADING;
    }

    /**
     * Return the {@link AndroidTargetData} for a given {@link IAndroidTarget}.
     */
    public AndroidTargetData getTargetData(IAndroidTarget target) {
        synchronized (sLock) {
            return mTargetDataMap.get(target);
        }
    }

    /**
     * Return the {@link AndroidTargetData} for a given {@link IProject}.
     */
    public AndroidTargetData getTargetData(IProject project) {
        synchronized (sLock) {
            IAndroidTarget target = getTarget(project);
            if (target != null) {
                return getTargetData(target);
            }
        }

        return null;
    }

    /**
     * Returns the {@link AvdManager}. If the AvdManager failed to parse the AVD folder, this could
     * be <code>null</code>.
     */
    public AvdManager getAvdManager() {
        return mAvdManager;
    }

    public static AndroidVersion getDeviceVersion(IDevice device) {
        try {
            Map<String, String> props = device.getProperties();
            String apiLevel = props.get(IDevice.PROP_BUILD_API_LEVEL);
            if (apiLevel == null) {
                return null;
            }

            return new AndroidVersion(Integer.parseInt(apiLevel),
                    props.get((IDevice.PROP_BUILD_CODENAME)));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public LayoutDeviceManager getLayoutDeviceManager() {
        return mLayoutDeviceManager;
    }

    private Sdk(SdkManager manager, AvdManager avdManager) {
        mManager = manager;
        mAvdManager = avdManager;

        // listen to projects closing
        GlobalProjectMonitor monitor = GlobalProjectMonitor.getMonitor();
        monitor.addProjectListener(mProjectListener);
        monitor.addFileListener(mFileListener, IResourceDelta.CHANGED | IResourceDelta.ADDED);

        // pre-compute some paths
        mDocBaseUrl = getDocumentationBaseUrl(mManager.getLocation() +
                SdkConstants.OS_SDK_DOCS_FOLDER);

        // load the built-in and user layout devices
        mLayoutDeviceManager.loadDefaultAndUserDevices(mManager.getLocation());
        // and the ones from the add-on
        loadLayoutDevices();

        // update whatever ProjectState is already present with new IAndroidTarget objects.
        synchronized (sLock) {
            for (Entry<IProject, ProjectState> entry: sProjectStateMap.entrySet()) {
                entry.getValue().setTarget(
                        getTargetFromHashString(entry.getValue().getTargetHashString()));
            }
        }
    }

    /**
     *  Cleans and unloads the SDK.
     */
    private void dispose() {
        GlobalProjectMonitor monitor = GlobalProjectMonitor.getMonitor();
        monitor.removeProjectListener(mProjectListener);
        monitor.removeFileListener(mFileListener);

        // the IAndroidTarget objects are now obsolete so update the project states.
        synchronized (sLock) {
            for (Entry<IProject, ProjectState> entry: sProjectStateMap.entrySet()) {
                entry.getValue().setTarget(null);
            }
        }
    }

    void setTargetData(IAndroidTarget target, AndroidTargetData data) {
        synchronized (sLock) {
            mTargetDataMap.put(target, data);
        }
    }

    /**
     * Returns the URL to the local documentation.
     * Can return null if no documentation is found in the current SDK.
     *
     * @param osDocsPath Path to the documentation folder in the current SDK.
     *  The folder may not actually exist.
     * @return A file:// URL on the local documentation folder if it exists or null.
     */
    private String getDocumentationBaseUrl(String osDocsPath) {
        File f = new File(osDocsPath);

        if (f.isDirectory()) {
            try {
                // Note: to create a file:// URL, one would typically use something like
                // f.toURI().toURL().toString(). However this generates a broken path on
                // Windows, namely "C:\\foo" is converted to "file:/C:/foo" instead of
                // "file:///C:/foo" (i.e. there should be 3 / after "file:"). So we'll
                // do the correct thing manually.

                String path = f.getAbsolutePath();
                if (File.separatorChar != '/') {
                    path = path.replace(File.separatorChar, '/');
                }

                // For some reason the URL class doesn't add the mandatory "//" after
                // the "file:" protocol name, so it has to be hacked into the path.
                URL url = new URL("file", null, "//" + path);  //$NON-NLS-1$ //$NON-NLS-2$
                String result = url.toString();
                return result;
            } catch (MalformedURLException e) {
                // ignore malformed URLs
            }
        }

        return null;
    }

    /**
     * Parses the SDK add-ons to look for files called {@link SdkConstants#FN_DEVICES_XML} to
     * load {@link LayoutDevice} from them.
     */
    private void loadLayoutDevices() {
        IAndroidTarget[] targets = mManager.getTargets();
        for (IAndroidTarget target : targets) {
            if (target.isPlatform() == false) {
                File deviceXml = new File(target.getLocation(), SdkConstants.FN_DEVICES_XML);
                if (deviceXml.isFile()) {
                    mLayoutDeviceManager.parseAddOnLayoutDevice(deviceXml);
                }
            }
        }

        mLayoutDeviceManager.sealAddonLayoutDevices();
    }

    /**
     * Delegate listener for project changes.
     */
    private IProjectListener mProjectListener = new IProjectListener() {
        public void projectClosed(IProject project) {
            // get the target project
            synchronized (sLock) {
                // Don't use getProject() as it could create the ProjectState if it's not
                // there yet and this is not what we want. We want the current object.
                // Therefore, direct access to the map.
                ProjectState state = sProjectStateMap.get(project);
                if (state != null) {
                    // 1. clear the layout lib cache associated with this project
                    IAndroidTarget target = state.getTarget();
                    if (target != null) {
                        // get the bridge for the target, and clear the cache for this project.
                        AndroidTargetData data = mTargetDataMap.get(target);
                        if (data != null) {
                            LayoutBridge bridge = data.getLayoutBridge();
                            if (bridge != null && bridge.status == LoadStatus.LOADED) {
                                bridge.bridge.clearCaches(project);
                            }
                        }
                    }

                    // 2. if the project is a library, make sure to update the
                    // LibraryState for any main project using this.
                    for (ProjectState projectState : sProjectStateMap.values()) {
                        LibraryState libState = projectState.getLibrary(project);
                        if (libState != null) {
                            // reset the library
                            libState.close();

                            // update the main project
                            // FIXME update the main project. maybe before library.close()?
                        }
                    }

                    // now remove the project for the project map.
                    sProjectStateMap.remove(project);
                }
            }
        }

        public void projectDeleted(IProject project) {
            projectClosed(project);
        }

        public void projectOpened(IProject openedProject) {
            ProjectState openedState = getProject(openedProject);
            if (openedState != null) {
                // find dependencies, if any
                if (openedState.isMissingLibraries()) {
                    // FIXME look for all opened projects to see if they are valid library
                    // for this project.
                }

                // if the project is a library, then try to see if it's required by other projects.
                if (openedState.isLibrary()) {
                    setupLibraryProject(openedProject);

                    synchronized (sLock) {
                        for (ProjectState projectState : sProjectStateMap.values()) {
                            if (projectState != openedState && projectState.isMissingLibraries()) {
                                LibraryState libState = projectState.needs(openedProject);
                                if (libState != null) {
                                    linkProjectAndLibrary(projectState, libState, null);
                                }
                            }
                        }
                    }
                }
            }
        }

        public void projectOpenedWithWorkspace(IProject project) {
            projectOpened(project);
        }

        public void projectRenamed(IProject project, IPath from) {
            // a project was renamed.
            // if the project is a library, look for any project that depended on it
            // and update it. (default.properties and linked source folder)
            ProjectState renamedState = getProject(project);
            if (renamedState.isLibrary()) {
                // remove the variable
                cleanLibraryProject(from.lastSegment());

                // update the project depending on the library
                synchronized (sLock) {
                    for (ProjectState projectState : sProjectStateMap.values()) {
                        if (projectState != renamedState && projectState.isMissingLibraries()) {
                            IPath oldRelativePath = makeRelativeTo(from,
                                    projectState.getProject().getFullPath());

                            IPath newRelativePath = makeRelativeTo(project.getFullPath(),
                                    projectState.getProject().getFullPath());

                            // update the library for the main project.
                            LibraryState libState = projectState.updateLibrary(
                                    oldRelativePath.toString(), newRelativePath.toString(),
                                    project);
                            if (libState != null) {
                                linkProjectAndLibrary(projectState, libState, from);
                            }
                        }
                    }
                }
            }
        }
    };

    /**
     * Delegate listener for file changes.
     */
    private IFileListener mFileListener = new IFileListener() {
        public void fileChanged(final IFile file, IMarkerDelta[] markerDeltas, int kind) {
            if (SdkConstants.FN_DEFAULT_PROPERTIES.equals(file.getName()) &&
                    file.getParent() == file.getProject()) {
                // we can't do the change from the Workspace resource change notification
                // so we create build-type job for it.
                Job job = new Job("Project Update") {
                    @Override
                    protected IStatus run(IProgressMonitor monitor) {
                        try {
                            // reload the content of the default.properties file and update
                            // the target.
                            IProject iProject = file.getProject();
                            ProjectState state = Sdk.getProject(iProject);
                            state.reloadProperties();
                            loadTarget(state);

                            IJavaProject javaProject = BaseProjectHelper.getJavaProject(
                                    file.getProject());
                            if (javaProject != null) {
                                AndroidClasspathContainerInitializer.updateProjects(
                                        new IJavaProject[] { javaProject });
                            }
                        } catch (CoreException e) {
                            // This can't happen as it's only for closed project (or non existing)
                            // but in that case we can't get a fileChanged on this file.
                        }

                        return Status.OK_STATUS;
                    }
                };
                job.setPriority(Job.BUILD); // build jobs are run after other interactive jobs
                job.schedule();
            }
        }
    };

    private void setupLibraryProject(IProject libProject) {
        // if needed add a path var for this library
        IPathVariableManager pathVarMgr =
            ResourcesPlugin.getWorkspace().getPathVariableManager();
        IPath libPath = libProject.getLocation();

        final String libName = libProject.getName();
        final String varName = "_android_" + libName; //$NON-NLS-1$

        if (libPath.equals(pathVarMgr.getValue(varName)) == false) {
            try {
                pathVarMgr.setValue(varName, libPath);
            } catch (CoreException e) {
                String message = String.format(
                        "Unable to set linked path var '%1$s' for library %2$s", //$NON-NLS-1$
                        varName, libPath.toOSString());
                AdtPlugin.log(e, message);
            }
        }
    }

    private void cleanLibraryProject(String libName) {
        IPathVariableManager pathVarMgr =
            ResourcesPlugin.getWorkspace().getPathVariableManager();

        final String varName = "_android_" + libName; //$NON-NLS-1$

        // remove the value by setting the value to null.
        try {
            pathVarMgr.setValue(varName, null /*path*/);
        } catch (CoreException e) {
            String message = String.format("Unable to remove linked path var '%1$s'", //$NON-NLS-1$
                    varName);
            AdtPlugin.log(e, message);
        }
    }

    /**
     * Links a project and a library so that the project can use the library code and resources.
     * <p/>This is done in a job to be sure that the workspace is not locked for resource
     * modification.
     * @param projectState the {@link ProjectState} for the main project
     * @param libraryState the {@link LibraryState} for the library project.
     * @param previousLibraryPath
     */
    private void linkProjectAndLibrary(
            final ProjectState projectState,
            final LibraryState libraryState,
            final IPath previousLibraryPath) {
        Job job = new Job("Android Library link creation") { //$NON-NLS-1$
            @Override
            protected IStatus run(IProgressMonitor monitor) {
                try {
                    IProject project = projectState.getProject();
                    IProject library = libraryState.getProject();

                    // add the library to the list of dynamic references
                    IProjectDescription projectDescription = project.getDescription();
                    IProject[] refs = projectDescription.getDynamicReferences();

                    if (refs.length > 0) {
                        ArrayList<IProject> list = new ArrayList<IProject>(Arrays.asList(refs));

                        // remove a previous library if needed (in case of a rename)
                        if (previousLibraryPath != null) {
                            final int count = list.size();
                            for (int i = 0 ; i < count ; i++) {
                                // since project basically have only one segment that matter,
                                // just check the names
                                if (list.get(i).getName().equals(
                                        previousLibraryPath.lastSegment())) {
                                    list.remove(i);
                                    break;
                                }
                            }

                        }

                        // add the new one.
                        list.add(library);

                        // set the changed list
                        projectDescription.setDynamicReferences(
                                list.toArray(new IProject[list.size()]));
                    } else {
                        projectDescription.setDynamicReferences(new IProject[] { library });
                    }

                    // add a linked resource for the source of the library and add it to the project
                    final String libName = library.getName();
                    final String varName = "_android_" + libName; //$NON-NLS-1$

                    // create a linked resource for the library using the path var.
                    IFolder libSrc = project.getFolder(libName);
                    // FIXME: make sure src has not been overriden?
                    String libSrcFolder = "src"; //$NON-NLS-1$
                    libSrc.createLink(new Path(varName + "/" + libSrcFolder), //$NON-NLS-1$
                            IResource.REPLACE, monitor);

                    // use the folder as a source folder. get the current list first.
                    IJavaProject javaProject = JavaCore.create(project);
                    IClasspathEntry[] entries = javaProject.getRawClasspath();
                    ArrayList<IClasspathEntry> list = new ArrayList<IClasspathEntry>(
                            Arrays.asList(entries));

                    // add the new one.
                    IPath path = libSrc.getFullPath();
                    list.add(JavaCore.newSourceEntry(path));

                    // remove a previous one if needed (in case of a rename)
                    if (previousLibraryPath != null) {
                        String oldLibName = previousLibraryPath.lastSegment();
                        IPath oldEntryPath = new Path("/").append(project.getName()).append(oldLibName);
                        // first remove the class path entry.
                        final int count = list.size();
                        for (int i = 0 ; i < count ; i++) {
                            if (list.get(i).getPath().equals(oldEntryPath)) {
                                list.remove(i);
                                break;
                            }
                        }

                        // then remove the folder itself.
                        IFolder oldLinkedFolder = project.getFolder(oldLibName);
                        oldLinkedFolder.delete(true, monitor);
                    }

                    // set the new list
                    javaProject.setRawClasspath(list.toArray(new IClasspathEntry[list.size()]),
                            monitor);

                    return Status.OK_STATUS;
                } catch (CoreException e) {
                    return e.getStatus();
                }
            }
        };
        job.setPriority(Job.BUILD);
        job.schedule();
    }

    /**
     * Computes a new IPath targeting a given target, but relative to a given base.
     * <p/>IPath{@link #makeRelativeTo(IPath, IPath)} is only available in 3.5 and later.
     * <p/>This is based on the implementation {@link Path#makeRelativeTo(IPath)}.
     * @param target the target of the IPath
     * @param base the IPath to base the relative path on.
     * @return the relative IPath
     */
    private static IPath makeRelativeTo(IPath target, IPath base) {
        //can't make relative if devices are not equal
        if (target.getDevice() != base.getDevice() && (target.getDevice() == null ||
                !target.getDevice().equalsIgnoreCase(base.getDevice())))
            return target;
        int commonLength = target.matchingFirstSegments(base);
        final int differenceLength = base.segmentCount() - commonLength;
        final int newSegmentLength = differenceLength + target.segmentCount() - commonLength;
        if (newSegmentLength == 0)
            return Path.EMPTY;
        String[] newSegments = new String[newSegmentLength];
        //add parent references for each segment different from the base
        Arrays.fill(newSegments, 0, differenceLength, ".."); //$NON-NLS-1$
        //append the segments of this path not in common with the base
        System.arraycopy(target.segments(), commonLength, newSegments,
                differenceLength, newSegmentLength - differenceLength);

        StringBuilder sb = new StringBuilder();
        for (String s : newSegments) {
            sb.append(s).append('/');
        }

        return new Path(null, sb.toString());
    }
}

