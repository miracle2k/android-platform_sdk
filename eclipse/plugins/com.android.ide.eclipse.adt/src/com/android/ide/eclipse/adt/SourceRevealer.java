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

import com.android.ide.eclipse.adt.internal.project.BaseProjectHelper;
import com.android.ide.eclipse.adt.internal.project.ProjectHelper;
import com.android.ide.eclipse.ddms.ISourceRevealer;

import org.eclipse.core.resources.IProject;

/**
 * Implementation of the com.android.ide.ddms.sourceRevealer extension point.
 *
 */
public class SourceRevealer implements ISourceRevealer {

    public boolean reveal(String applicationName, String className, int line) {
        IProject project = ProjectHelper.findAndroidProjectByAppName(applicationName);
        if (project != null) {
            return BaseProjectHelper.revealSource(project, className, line);
        }

        return false;
    }
}
