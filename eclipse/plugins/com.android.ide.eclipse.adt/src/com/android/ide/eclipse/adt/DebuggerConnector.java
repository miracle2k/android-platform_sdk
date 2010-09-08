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

package com.android.ide.eclipse.adt;

import com.android.ide.eclipse.adt.internal.launch.AndroidLaunchController;
import com.android.ide.eclipse.adt.internal.project.ProjectHelper;
import com.android.ide.eclipse.ddms.IDebuggerConnector;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;

/**
 * Implementation of the com.android.ide.ddms.debuggerConnector extension point.
 */
public class DebuggerConnector implements IDebuggerConnector {

    public boolean connectDebugger(String appName, int port) {
        // search for an android project matching the process name
        IProject project = ProjectHelper.findAndroidProjectByAppName(appName);
        if (project != null) {
            AndroidLaunchController.debugRunningApp(project, port);
            return true;
        } else {
            // check to see if there's a platform project defined by an env var.
            String var = System.getenv("ANDROID_PLATFORM_PROJECT"); //$NON-NLS-1$
            if (var != null && var.length() > 0) {
                boolean auto = "AUTO".equals(var); //$NON-NLS-1$

                // Get the list of project for the current workspace
                IWorkspace workspace = ResourcesPlugin.getWorkspace();
                IProject[] projects = workspace.getRoot().getProjects();

                // look for a project that matches the env var or take the first
                // one if in automatic mode.
                for (IProject p : projects) {
                    if (p.isOpen()) {
                        if (auto || p.getName().equals(var)) {
                            AndroidLaunchController.debugRunningApp(p, port);
                            return true;
                        }
                    }
                }

            }
            return false;
        }
    }
}
