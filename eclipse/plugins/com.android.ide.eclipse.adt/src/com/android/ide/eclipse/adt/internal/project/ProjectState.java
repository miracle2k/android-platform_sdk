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

import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.internal.project.ApkSettings;
import com.android.sdklib.internal.project.ProjectProperties;

import org.eclipse.core.resources.IProject;

/**
 * Centralized state for Android Eclipse project.
 * <p>This gives raw access to the properties (from <code>default.properties</code>), as well
 * as direct access to target, apksettings and library information.
 *
 */
public final class ProjectState {

    private final IProject mProject;
    private final ProjectProperties mProperties;
    private IAndroidTarget mTarget;
    private ApkSettings mApkSettings;

    public ProjectState(IProject project, ProjectProperties properties) {
        mProject = project;
        mProperties = properties;
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

    public void setApkSettings(ApkSettings apkSettings) {
        mApkSettings = apkSettings;
    }

    public ApkSettings getApkSettings() {
        return mApkSettings;
    }
}
