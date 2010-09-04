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

package com.android.ide.eclipse.adt.internal.refactoring.changes;

import org.eclipse.core.resources.IFile;

import java.util.HashSet;
import java.util.Set;

/**
 * Set of layout files with required text changes
 *
 */
public class AndroidLayoutFileChanges {
    private IFile mFile;

    private Set<AndroidLayoutChangeDescription> mChanges =
        new HashSet<AndroidLayoutChangeDescription>();

    /**
     * Creates a new <code>AndroidLayoutFileChanges</code>
     *
     * @param file the layout file
     */
    public AndroidLayoutFileChanges(IFile file) {
        this.mFile = file;
    }

    /**
     * Return the layout file
     *
     * @return the file
     */
    public IFile getFile() {
        return mFile;
    }

    /**
     * Return the text changes
     *
     * @return the set of changes
     */
    public Set<AndroidLayoutChangeDescription> getChanges() {
        return mChanges;
    }

}
