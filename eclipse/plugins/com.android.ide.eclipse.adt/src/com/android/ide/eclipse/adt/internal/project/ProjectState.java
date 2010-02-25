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

import com.android.ide.eclipse.adt.internal.sdk.Sdk;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.internal.project.ApkConfigurationHelper;
import com.android.sdklib.internal.project.ApkSettings;
import com.android.sdklib.internal.project.ProjectProperties;

import org.eclipse.core.resources.IProject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Centralized state for Android Eclipse project.
 * <p>This gives raw access to the properties (from <code>default.properties</code>), as well
 * as direct access to target, apksettings and library information.
 *
 */
public final class ProjectState {

    public final class LibraryState {
        private final String mRelativePath;
        private IProject mProject;
        private String mPath;

        private LibraryState(String relativePath) {
            mRelativePath = relativePath;
        }

        private void setProject(IProject project) {
            mProject = project;
            mPath = project.getLocation().toOSString();

            updateLibraries();
        }

        public String getRelativePath() {
            return mRelativePath;
        }

        public IProject getProject() {
            return mProject;
        }

        public String getProjectLocation() {
            return mPath;
        }
    }

    private final IProject mProject;
    private final ProjectProperties mProperties;
    private final ArrayList<LibraryState> mLibraries = new ArrayList<LibraryState>();
    private IAndroidTarget mTarget;
    private ApkSettings mApkSettings;
    private IProject[] mLibraryProjects;

    public ProjectState(IProject project, ProjectProperties properties) {
        mProject = project;
        mProperties = properties;

        // load the ApkSettings
        mApkSettings = ApkConfigurationHelper.getSettings(properties);

        // load the libraries
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

    /**
     * Reloads the content of the properties.
     * <p/>This also reset the reference to the target as it may have changed.
     * <p/>This should be followed by a call to {@link Sdk#loadTarget(ProjectState)}.
     */
    public void reloadProperties() {
        mTarget = null;
        mProperties.reload();
    }

    public void setApkSettings(ApkSettings apkSettings) {
        mApkSettings = apkSettings;
    }

    public ApkSettings getApkSettings() {
        return mApkSettings;
    }

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
     * Returns whether the project is missing some required libraries.
     */
    public boolean isMissingLibraries() {
        for (LibraryState state : mLibraries) {
            if (state.getProject() == null) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns whether a given library project is needed by the receiver.
     * @param libraryProject the library project to check.
     * @return a non null object if the project is a library dependency.
     */
    public LibraryState needs(IProject libraryProject) {
        // compute current location
        File projectFile = new File(mProject.getLocation().toOSString());

        // get the location of the library.
        File libraryFile = new File(libraryProject.getLocation().toOSString());

        // loop on all libraries and check if the path match
        for (LibraryState state : mLibraries) {
            if (state.getProject() == null) {
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

        return null;
    }

    private void updateLibraries() {
        ArrayList<IProject> list = new ArrayList<IProject>();
        for (LibraryState state : mLibraries) {
            if (state.getProject() != null) {
                list.add(state.getProject());
            }
        }

        mLibraryProjects = list.toArray(new IProject[list.size()]);
    }

    /**
     * Converts a path containing only / by the proper platform separator.
     */
    private String convertPath(String path) {
        return path.replaceAll("/", File.separator); //$NON-NLS-1$
    }
}
