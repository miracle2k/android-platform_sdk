/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.sdklib.internal.io;


import java.io.File;

/**
 * An implementation of {@link IAbstractFolder} on top of a {@link File} object.
 */
public class FolderWrapper implements IAbstractFolder {

    private final File mFolder;

    /**
     * Constructs a {@link FileWrapper} object. The underlying {@link File} object needs not exists
     * or be a valid directory.
     */
    public FolderWrapper(File folder) {
        mFolder = folder;
    }

    public boolean hasFile(String name) {
        return false;
    }

    public IAbstractFile getFile(String name) {
        return new FileWrapper(new File(mFolder, name));
    }

    public String getName() {
        return mFolder.getName();
    }

    public boolean exists() {
        return mFolder.isDirectory();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof FolderWrapper) {
            return mFolder.equals(((FolderWrapper)obj).mFolder);
        }

        if (obj instanceof File) {
            return mFolder.equals(obj);
        }

        return super.equals(obj);
    }

    @Override
    public int hashCode() {
        return mFolder.hashCode();
    }
}
