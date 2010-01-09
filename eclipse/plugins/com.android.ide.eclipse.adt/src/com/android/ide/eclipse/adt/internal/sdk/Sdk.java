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
import org.eclipse.core.resources.IMarkerDelta;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * Central point to load, manipulate and deal with the Android SDK. Only one SDK can be used
 * at the same time.
 *
 * To start using an SDK, call {@link #loadSdk(String)} which returns the instance of
 * the Sdk object.
 *
 * To get the list of platforms or add-ons present in the SDK, call {@link #getTargets()}.
 */
public class Sdk implements IProjectListener, IFileListener {
    private static Sdk sCurrentSdk = null;

    private final SdkManager mManager;
    private final AvdManager mAvdManager;

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

    /** Map associating an {@link IAndroidTarget} to an {@link AndroidTargetData} */
    private final HashMap<IAndroidTarget, AndroidTargetData> mTargetDataMap =
        new HashMap<IAndroidTarget, AndroidTargetData>();
    /** Map associating an {@link IAndroidTarget} and its {@link TargetLoadBundle}. */
    private final HashMap<IAndroidTarget, TargetLoadBundle> mTargetDataStatusMap =
        new HashMap<IAndroidTarget, TargetLoadBundle>();

    /** Map associating {@link IProject} and their resolved {@link IAndroidTarget}. */
    private final HashMap<IProject, IAndroidTarget> mProjectTargetMap =
            new HashMap<IProject, IAndroidTarget>();
    /** Map associating {@link IProject} and their APK creation settings ({@link ApkSettings}). */
    private final HashMap<IProject, ApkSettings> mApkSettingsMap =
            new HashMap<IProject, ApkSettings>();

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
     * Loads an SDK and returns an {@link Sdk} object if success.
     * <p/>If the SDK failed to load, it displays an error to the user.
     * @param sdkLocation the OS path to the SDK.
     */
    public static synchronized Sdk loadSdk(String sdkLocation) {
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

    /**
     * Returns the current {@link Sdk} object.
     */
    public static synchronized Sdk getCurrent() {
        return sCurrentSdk;
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
     * Sets a new target and a new list of Apk configuration for a given project.
     *
     * @param project the project to receive the new apk configurations
     * @param target The new target to set, or <code>null</code> to not change the current target.
     * @param apkConfigMap a map of apk configurations. The map contains (name, filter) where name
     * is the name of the configuration (a-zA-Z0-9 only), and filter is the comma separated list of
     * resource configuration to include in the apk (see aapt -c). Can be <code>null</code> if the
     * apk configurations should not be updated.
     */
    public void setProject(IProject project, IAndroidTarget target,
            ApkSettings settings) {
        synchronized (AdtPlugin.getDefault().getSdkLockObject()) {
            boolean resolveProject = false;

            ProjectProperties properties = ProjectProperties.load(
                    project.getLocation().toOSString(), PropertyType.DEFAULT);
            if (properties == null) {
                // doesn't exist yet? we create it.
                properties = ProjectProperties.create(project.getLocation().toOSString(),
                        PropertyType.DEFAULT);
            }

            if (target != null) {
                // look for the current target of the project
                IAndroidTarget previousTarget = mProjectTargetMap.get(project);

                if (target != previousTarget) {
                    // save the target hash string in the project persistent property
                    properties.setAndroidTarget(target);

                    // put it in a local map for easy access.
                    mProjectTargetMap.put(project, target);

                    resolveProject = true;
                }
            }

            // if there's no settings, force default values (to reset possibly changed
            // values in a previous call.
            if (settings == null) {
                settings = new ApkSettings();
            }

            // save the project settings into the project persistent property
            ApkConfigurationHelper.setProperties(properties, settings);

            // put it in a local map for easy access.
            mApkSettingsMap.put(project, settings);

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
     * Returns the {@link IAndroidTarget} object associated with the given {@link IProject}.
     */
    public IAndroidTarget getTarget(IProject project) {
        if (project == null) {
            return null;
        }
        synchronized (AdtPlugin.getDefault().getSdkLockObject()) {
            IAndroidTarget target = mProjectTargetMap.get(project);
            if (target == null) {
                // get the value from the project persistent property.
                String targetHashString = loadProjectProperties(project, this);

                if (targetHashString != null) {
                    target = mManager.getTargetFromHashString(targetHashString);
                }
            }

            return target;
        }
    }


    /**
     * Parses the project properties and returns the hash string uniquely identifying the
     * target of the given project.
     * <p/>
     * This methods reads the content of the <code>default.properties</code> file present in
     * the root folder of the project.
     * <p/>The returned string is equivalent to the return of {@link IAndroidTarget#hashString()}.
     * @param project The project for which to return the target hash string.
     * @param sdkStorage The sdk in which to store the Apk Configs. Can be null.
     * @return the hash string or null if the project does not have a target set.
     */
    private static String loadProjectProperties(IProject project, Sdk sdkStorage) {
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

        if (sdkStorage != null) {
            synchronized (AdtPlugin.getDefault().getSdkLockObject()) {
                ApkSettings settings = ApkConfigurationHelper.getSettings(properties);

                if (settings != null) {
                    sdkStorage.mApkSettingsMap.put(project, settings);
                }
            }
        }

        return properties.getProperty(ProjectProperties.PROPERTY_TARGET);
    }

    /**
     * Returns the hash string uniquely identifying the target of a project.
     * <p/>
     * This methods reads the content of the <code>default.properties</code> file present in
     * the root folder of the project.
     * <p/>The string is equivalent to the return of {@link IAndroidTarget#hashString()}.
     * @param project The project for which to return the target hash string.
     * @return the hash string or null if the project does not have a target set.
     */
    public static String getProjectTargetHashString(IProject project) {
        return loadProjectProperties(project, null /*storeConfigs*/);
    }

    /**
     * Sets a target hash string in given project's <code>default.properties</code> file.
     * @param project The project in which to save the hash string.
     * @param targetHashString The target hash string to save. This must be the result from
     * {@link IAndroidTarget#hashString()}.
     */
    public static void setProjectTargetHashString(IProject project, String targetHashString) {
        // because we don't want to erase other properties from default.properties, we first load
        // them
        ProjectProperties properties = ProjectProperties.load(project.getLocation().toOSString(),
                PropertyType.DEFAULT);
        if (properties == null) {
            // doesn't exist yet? we create it.
            properties = ProjectProperties.create(project.getLocation().toOSString(),
                    PropertyType.DEFAULT);
        }

        // add/change the target hash string.
        properties.setProperty(ProjectProperties.PROPERTY_TARGET, targetHashString);

        // and rewrite the file.
        try {
            properties.save();
        } catch (IOException e) {
            AdtPlugin.log(e, "Failed to save default.properties for project '%s'",
                    project.getName());
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

        synchronized (AdtPlugin.getDefault().getSdkLockObject()) {
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

                        synchronized (plugin.getSdkLockObject()) {
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
                        synchronized (plugin.getSdkLockObject()) {
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
        synchronized (AdtPlugin.getDefault().getSdkLockObject()) {
            return mTargetDataMap.get(target);
        }
    }

    /**
     * Return the {@link AndroidTargetData} for a given {@link IProject}.
     */
    public AndroidTargetData getTargetData(IProject project) {
        synchronized (AdtPlugin.getDefault().getSdkLockObject()) {
            IAndroidTarget target = getTarget(project);
            if (target != null) {
                return getTargetData(target);
            }
        }

        return null;
    }

    /**
     * Returns the APK settings for a given project.
     */
    public ApkSettings getApkSettings(IProject project) {
        synchronized (AdtPlugin.getDefault().getSdkLockObject()) {
            return mApkSettingsMap.get(project);
        }
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
        monitor.addProjectListener(this);
        monitor.addFileListener(this, IResourceDelta.CHANGED);

        // pre-compute some paths
        mDocBaseUrl = getDocumentationBaseUrl(mManager.getLocation() +
                SdkConstants.OS_SDK_DOCS_FOLDER);

        // load the built-in and user layout devices
        mLayoutDeviceManager.loadDefaultAndUserDevices(mManager.getLocation());
        // and the ones from the add-on
        loadLayoutDevices();
    }

    /**
     *  Cleans and unloads the SDK.
     */
    private void dispose() {
        GlobalProjectMonitor monitor = GlobalProjectMonitor.getMonitor();
        monitor.removeProjectListener(this);
        monitor.removeFileListener(this);
    }

    void setTargetData(IAndroidTarget target, AndroidTargetData data) {
        synchronized (AdtPlugin.getDefault().getSdkLockObject()) {
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

    public void projectClosed(IProject project) {
        // get the target project
        synchronized (AdtPlugin.getDefault().getSdkLockObject()) {
            IAndroidTarget target = mProjectTargetMap.get(project);
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

            // now remove the project for the maps.
            mProjectTargetMap.remove(project);
            mApkSettingsMap.remove(project);
        }
    }

    public void projectDeleted(IProject project) {
        projectClosed(project);
    }

    public void projectOpened(IProject project) {
        // ignore this. The project will be added to the map the first time the target needs
        // to be resolved.
    }

    public void projectOpenedWithWorkspace(IProject project) {
        // ignore this. The project will be added to the map the first time the target needs
        // to be resolved.
    }

    public void fileChanged(final IFile file, IMarkerDelta[] markerDeltas, int kind) {
        if (SdkConstants.FN_DEFAULT_PROPERTIES.equals(file.getName()) &&
                file.getParent() == file.getProject()) {
            // we can't do the change from the Workspace resource change notification
            // so we create build-type job for it.
            Job job = new Job("Project Update") {
                @Override
                protected IStatus run(IProgressMonitor monitor) {
                    try {
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
}

