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

package com.android.ide.eclipse.adt.internal.build;

import com.android.sdklib.SdkConstants;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;

/**
 * Delta visitor specifically for Library resources.
 * The goal is to detect library resource/library changes when compiling the main project
 * and trigger a resource recompilation/repackaging.
 *
 */
public class LibraryDeltaVisitor implements IResourceDeltaVisitor {

    private boolean mResChange = false;
    private boolean mLibChange = false;

    public boolean getResChange() {
        return mResChange;
    }

    public boolean getLibChange() {
        return mLibChange;
    }

    public boolean visit(IResourceDelta delta) throws CoreException {
        // we are only going to look for changes in res/
        // Since the delta visitor goes through the main
        // folder before its children we can check when the path segment
        // count is 2 (format will be /$Project/folder) and make sure we are
        // processing res/

        IResource resource = delta.getResource();
        IPath path = resource.getFullPath();
        String[] segments = path.segments();

        // since the delta visitor also visits the root we return true if
        // segments.length = 1
        if (segments.length == 1) {
            // this is always the Android project since we call
            // Builder#getDelta(IProject) on the project itself.
            return true;
        } else if (segments.length == 2) {
            if (SdkConstants.FD_RESOURCES.equalsIgnoreCase(segments[1])) {
                // res folder was changed!
                // This is all that matters, we can stop (return false below)
                mResChange = true;
            } else if (SdkConstants.FD_NATIVE_LIBS.equalsIgnoreCase(segments[1])) {
                // libs folder was changed.
                // This is all that matters, we can stop (return false below)
                mLibChange = true;
            }
        }

        return false;
    }
}
