/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.ide.eclipse.pdt.internal;

import com.android.ide.eclipse.pdt.PdtPlugin;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.JavaCore;

/**
 * Base class providing a {@link #getProject()} method to find the project matching the dev tree.
 *
 */
class DevTreeProjectProvider {

    protected IProject getProject() {
        // Get the list of project for the current workspace
        IWorkspace workspace = ResourcesPlugin.getWorkspace();
        IProject[] projects = workspace.getRoot().getProjects();

        // get the location of the Dev tree
        String devTree = PdtPlugin.getDevTree();

        if (devTree != null) {

            // look for a project that matches the location of the dev tree
            for (IProject p : projects) {
                if (p.isOpen()) {
                    try {
                        if (p.hasNature(JavaCore.NATURE_ID) == false) {
                            // ignore non Java projects
                            continue;
                        }
                    } catch (CoreException e) {
                        // failed to get the nature? skip project.
                        continue;
                    }

                    // check the location of the project
                    if (devTree.equals(p.getLocation().toOSString())) {
                        return p;
                    }
                }
            }
        }

        return null;
    }
}
