/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.ide.eclipse.adt.internal.project;

import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.internal.sdk.Sdk;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkConstants;
import com.android.sdklib.internal.project.ApkSettings;
import com.android.sdklib.internal.project.ProjectProperties;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;

/**
 * Centralized state for Android Eclipse project.
 * <p>This gives raw access to the properties (from <code>default.properties</code>), as well
 * as direct access to target, apksettings and library information.
 *
 */
public final class ProjectState {

    /**
     * A class that represents a library linked to a project.
     * <p/>It does not represent the library uniquely. Instead the {@link LibraryState} is linked
     * to the main project which is accessible through {@link #getMainProjectState()}.
     * <p/>If a library is used by two different projects, then there will be two different
     * instances of {@link LibraryState} for the library.
     *
     * @see ProjectState#getLibrary(IProject)
     */
    public final class LibraryState {
        private String mRelativePath;
        private ProjectState mProjectState;
        private String mPath;

        private LibraryState(String relativePath) {
            mRelativePath = relativePath;
        }

        /**
         * Returns the {@link ProjectState} of the main project using this library.
         */
        public ProjectState getMainProjectState() {
            return ProjectState.this;
        }

        /**
         * Closes the library. This resets the IProject from this object ({@link #getProjectState()} will
         * return <code>null</code>), and updates the main project data so that the library
         * {@link IProject} object does not show up in the return value of
         * {@link ProjectState#getLibraryProjects()}.
         */
        public void close() {
            mProjectState = null;
            mPath = null;

            updateLibraries();
        }

        private void setRelativePath(String relativePath) {
            mRelativePath = relativePath;
        }

        private void setProject(ProjectState project) {
            mProjectState = project;
            mPath = project.getProject().getLocation().toOSString();

            updateLibraries();
        }

        /**
         * Returns the relative path of the library from the main project.
         * <p/>This is identical to the value defined in the main project's default.properties.
         */
        public String getRelativePath() {
            return mRelativePath;
        }

        /**
         * Returns the {@link ProjectState} item for the library. This can be null if the project
         * is not actually opened in Eclipse.
         */
        public ProjectState getProjectState() {
            return mProjectState;
        }

        /**
         * Returns the OS-String location of the library project.
         * <p/>This is based on location of the Eclipse project that matched
         * {@link #getRelativePath()}.
         *
         * @return The project location, or null if the project is not opened in Eclipse.
         */
        public String getProjectLocation() {
            return mPath;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof LibraryState) {
                // the only thing that's always non-null is the relative path.
                LibraryState objState = (LibraryState)obj;
                return mRelativePath.equals(objState.mRelativePath) &&
                        getMainProjectState().equals(objState.getMainProjectState());
            } else if (obj instanceof ProjectState || obj instanceof IProject) {
                return mProjectState != null && mProjectState.equals(obj);
            } else if (obj instanceof String) {
                return normalizePath(mRelativePath).equals(normalizePath((String) obj));
            }

            return false;
        }

        @Override
        public int hashCode() {
            return mRelativePath.hashCode();
        }
    }

    private final IProject mProject;
    private final ProjectProperties mProperties;
    /**
     * list of libraries. Access to this list must be protected by
     * <code>synchronized(mLibraries)</code>, but it is important that such code do not call
     * out to other classes (especially those protected by {@link Sdk#getLock()}.)
     */
    private final ArrayList<LibraryState> mLibraries = new ArrayList<LibraryState>();
    private IAndroidTarget mTarget;
    private ApkSettings mApkSettings;
    private IProject[] mLibraryProjects;

    public ProjectState(IProject project, ProjectProperties properties) {
        mProject = project;
        mProperties = properties;

        // load the ApkSettings
        mApkSettings = new ApkSettings(properties);

        // load the libraries
        synchronized (mLibraries) {
            int index = 1;
            while (true) {
                String propName = ProjectProperties.PROPERTY_LIB_REF + Integer.toString(index++);
                String rootPath = mProperties.getProperty(propName);

                if (rootPath == null) {
                    break;
                }

                mLibraries.add(new LibraryState(convertPath(rootPath)));
            }
        }
    }

    public IProject getProject() {
        return mProject;
    }

    public ProjectProperties getProperties() {
        return mProperties;
    }

    public void setTarget(IAndroidTarget target) {
        mTarget = target;
    }

    /**
     * Returns the project's target's hash string.
     * <p/>If {@link #getTarget()} returns a valid object, then this returns the value of
     * {@link IAndroidTarget#hashString()}.
     * <p/>Otherwise this will return the value of the property
     * {@link ProjectProperties#PROPERTY_TARGET} from {@link #getProperties()} (if valid).
     * @return the target hash string or null if not found.
     */
    public String getTargetHashString() {
        if (mTarget != null) {
            return mTarget.hashString();
        }

        if (mProperties != null) {
            return mProperties.getProperty(ProjectProperties.PROPERTY_TARGET);
        }

        return null;
    }

    public IAndroidTarget getTarget() {
        return mTarget;
    }

    public static class LibraryDifference {
        public List<LibraryState> removed = new ArrayList<LibraryState>();
        public boolean added = false;

        public boolean hasDiff() {
            return removed.size() > 0 || added;
        }
    }

    /**
     * Reloads the content of the properties.
     * <p/>This also reset the reference to the target as it may have changed.
     * <p/>This should be followed by a call to {@link Sdk#loadTarget(ProjectState)}.
     *
     * @return an instance of {@link LibraryDifference} describing the change in libraries.
     */
    public LibraryDifference reloadProperties() {
        mTarget = null;
        mProperties.reload();

        // compare/reload the libraries.

        // if the order change it won't impact the java part, so instead try to detect removed/added
        // libraries.

        LibraryDifference diff = new LibraryDifference();

        synchronized (mLibraries) {
            List<LibraryState> oldLibraries = new ArrayList<LibraryState>(mLibraries);
            mLibraries.clear();

            // load the libraries
            int index = 1;
            while (true) {
                String propName = ProjectProperties.PROPERTY_LIB_REF + Integer.toString(index++);
                String rootPath = mProperties.getProperty(propName);

                if (rootPath == null) {
                    break;
                }

                // search for a library with the same path (not exact same string, but going
                // to the same folder).
                String convertedPath = convertPath(rootPath);
                boolean found = false;
                for (int i = 0 ; i < oldLibraries.size(); i++) {
                    LibraryState libState = oldLibraries.get(i);
                    if (libState.equals(convertedPath)) {
                        // it's a match. move it back to mLibraries and remove it from the
                        // old library list.
                        found = true;
                        mLibraries.add(libState);
                        oldLibraries.remove(i);
                        break;
                    }
                }

                if (found == false) {
                    diff.added = true;
                    mLibraries.add(new LibraryState(convertedPath));
                }
            }

            // whatever's left in oldLibraries is removed.
            diff.removed.addAll(oldLibraries);

            // update the library with what IProjet are known at the time.
            updateLibraries();
        }

        return diff;
    }

    public void setApkSettings(ApkSettings apkSettings) {
        mApkSettings = apkSettings;
    }

    public ApkSettings getApkSettings() {
        return mApkSettings;
    }

    /**
     * Returns the list of {@link LibraryState}.
     */
    public List<LibraryState> getLibraries() {
        synchronized (mLibraries) {
            return Collections.unmodifiableList(mLibraries);
        }
    }

    /**
     * Convenience method returning all the IProject objects for the resolved libraries.
     * <p/>If some dependencies are not resolved (or their projects is not opened in Eclipse),
     * they will not show up in this list.
     * @return the resolved projects or null if there are no project (either no resolved or no
     * dependencies)
     */
    public IProject[] getLibraryProjects() {
        return mLibraryProjects;
    }

    /**
     * Returns whether this is a library project.
     */
    public boolean isLibrary() {
        String value = mProperties.getProperty(ProjectProperties.PROPERTY_LIBRARY);
        return value != null && Boolean.valueOf(value);
    }

    /**
     * Returns whether the project depends on one or more libraries.
     */
    public boolean hasLibraries() {
        synchronized (mLibraries) {
            return mLibraries.size() > 0;
        }
    }

    /**
     * Returns whether the project is missing some required libraries.
     */
    public boolean isMissingLibraries() {
        synchronized (mLibraries) {
            for (LibraryState state : mLibraries) {
                if (state.getProjectState() == null) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Returns the {@link LibraryState} object for a given {@link IProject}.
     * </p>This can only return a non-null object if the link between the main project's
     * {@link IProject} and the library's {@link IProject} was done.
     *
     * @return the matching LibraryState or <code>null</code>
     *
     * @see #needs(IProject)
     */
    public LibraryState getLibrary(IProject library) {
        synchronized (mLibraries) {
            for (LibraryState state : mLibraries) {
                ProjectState ps = state.getProjectState();
                if (ps != null && ps.equals(library)) {
                    return state;
                }
            }
        }

        return null;
    }

    public LibraryState getLibrary(String name) {
        synchronized (mLibraries) {
            for (LibraryState state : mLibraries) {
                ProjectState ps = state.getProjectState();
                if (ps != null && ps.getProject().getName().equals(name)) {
                    return state;
                }
            }
        }

        return null;
    }


    /**
     * Returns whether a given library project is needed by the receiver.
     * <p/>If the library is needed, this finds the matching {@link LibraryState}, initializes it
     * so that it contains the library's {@link IProject} object (so that
     * {@link LibraryState#getProjectState()} does not return null) and then returns it.
     *
     * @param libraryProject the library project to check.
     * @return a non null object if the project is a library dependency,
     * <code>null</code> otherwise.
     *
     * @see LibraryState#getProjectState()
     */
    public LibraryState needs(ProjectState libraryProject) {
        // compute current location
        File projectFile = mProject.getLocation().toFile();

        // get the location of the library.
        File libraryFile = libraryProject.getProject().getLocation().toFile();

        // loop on all libraries and check if the path match
        synchronized (mLibraries) {
            for (LibraryState state : mLibraries) {
                if (state.getProjectState() == null) {
                    File library = new File(projectFile, state.getRelativePath());
                    try {
                        File absPath = library.getCanonicalFile();
                        if (absPath.equals(libraryFile)) {
                            state.setProject(libraryProject);
                            return state;
                        }
                    } catch (IOException e) {
                        // ignore this library
                    }
                }
            }
        }

        return null;
    }

    /**
     * Updates a library with a new path.
     * <p/>This method acts both as a check and an action. If the project does not depend on the
     * given <var>oldRelativePath</var> then no action is done and <code>null</code> is returned.
     * <p/>If the project depends on the library, then the project is updated with the new path,
     * and the {@link LibraryState} for the library is returned.
     * <p/>Updating the project does two things:<ul>
     * <li>Update LibraryState with new relative path and new {@link IProject} object.</li>
     * <li>Update the main project's <code>default.properties</code> with the new relative path
     * for the changed library.</li>
     * </ul>
     *
     * @param oldRelativePath the old library path relative to this project
     * @param newRelativePath the new library path relative to this project
     * @param newLibraryState the new {@link ProjectState} object.
     * @return a non null object if the project depends on the library.
     *
     * @see LibraryState#getProjectState()
     */
    public LibraryState updateLibrary(String oldRelativePath, String newRelativePath,
            ProjectState newLibraryState) {
        // compute current location
        File projectFile = mProject.getLocation().toFile();

        // loop on all libraries and check if the path matches
        synchronized (mLibraries) {
            for (LibraryState state : mLibraries) {
                if (state.getProjectState() == null) {
                    try {
                        // oldRelativePath may not be the same exact string as the
                        // one in the project properties (trailing separator could be different
                        // for instance).
                        // Use java.io.File to deal with this and also do a platform-dependent
                        // path comparison
                        File library1 = new File(projectFile, oldRelativePath);
                        File library2 = new File(projectFile, state.getRelativePath());
                        if (library1.getCanonicalPath().equals(library2.getCanonicalPath())) {
                            // save the exact property string to replace.
                            String oldProperty = state.getRelativePath();

                            // then update the LibraryPath.
                            state.setRelativePath(newRelativePath);
                            state.setProject(newLibraryState);

                            // update the default.properties file
                            IStatus status = replaceLibraryProperty(oldProperty, newRelativePath);
                            if (status != null) {
                                if (status.getSeverity() != IStatus.OK) {
                                    // log the error somehow.
                                }
                            } else {
                                // This should not happen since the library wouldn't be here in the
                                // first place
                            }

                            // return the LibraryState object.
                            return state;
                        }
                    } catch (IOException e) {
                        // ignore this library
                    }
                }
            }
        }

        return null;
    }

    /**
     * Saves the default.properties file and refreshes it to make sure that it gets reloaded
     * by Eclipse
     */
    public void saveProperties() {
        try {
            mProperties.save();

            IResource defaultProp = mProject.findMember(SdkConstants.FN_DEFAULT_PROPERTIES);
            defaultProp.refreshLocal(IResource.DEPTH_ZERO, new NullProgressMonitor());
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (CoreException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private IStatus replaceLibraryProperty(String oldValue, String newValue) {
        int index = 1;
        while (true) {
            String propName = ProjectProperties.PROPERTY_LIB_REF + Integer.toString(index++);
            String rootPath = mProperties.getProperty(propName);

            if (rootPath == null) {
                break;
            }

            if (rootPath.equals(oldValue)) {
                mProperties.setProperty(propName, newValue);
                try {
                    mProperties.save();
                } catch (IOException e) {
                    return new Status(IStatus.ERROR, AdtPlugin.PLUGIN_ID,
                            String.format("Failed to save %1$s for project %2$s",
                                    mProperties.getType().getFilename(), mProject.getName()),
                            e);
                }
                return Status.OK_STATUS;
            }
        }

        return null;
    }

    private void updateLibraries() {
        ArrayList<IProject> list = new ArrayList<IProject>();
        synchronized (mLibraries) {
            for (LibraryState state : mLibraries) {
                if (state.getProjectState() != null) {
                    list.add(state.getProjectState().getProject());
                }
            }
        }

        mLibraryProjects = list.toArray(new IProject[list.size()]);
    }

    /**
     * Converts a path containing only / by the proper platform separator.
     */
    private String convertPath(String path) {
        return path.replaceAll("/", Matcher.quoteReplacement(File.separator)); //$NON-NLS-1$
    }

    /**
     * Normalizes a relative path.
     */
    private String normalizePath(String path) {
        path = convertPath(path);
        if (path.endsWith("/")) { //$NON-NLS-1$
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ProjectState) {
            return mProject.equals(((ProjectState) obj).mProject);
        } else if (obj instanceof IProject) {
            return mProject.equals(obj);
        }

        return false;
    }

    @Override
    public int hashCode() {
        return mProject.hashCode();
    }
}
