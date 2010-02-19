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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

/**
 * An implementation of {@link IAbstractFile} on top of a {@link File} object.
 *
 */
public class FileWrapper implements IAbstractFile {

    private final File mFile;

    /**
     * Constructs a {@link FileWrapper} object. The underlying {@link File} object needs not
     * exist or be a valid file.
     */
    public FileWrapper(File file) {
        mFile = file;
    }

    public InputStream getContents() throws StreamException {
        try {
            return new FileInputStream(mFile);
        } catch (FileNotFoundException e) {
            throw new StreamException(e);
        }
    }

    public String getOsLocation() {
        return mFile.getAbsolutePath();
    }

    public String getName() {
        return mFile.getName();
    }

    public boolean exists() {
        return mFile.isFile();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof FileWrapper) {
            return mFile.equals(((FileWrapper)obj).mFile);
        }

        if (obj instanceof File) {
            return mFile.equals(obj);
        }

        return super.equals(obj);
    }

    @Override
    public int hashCode() {
        return mFile.hashCode();
    }
}
