/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.ide.eclipse.adt.io;

import com.android.sdklib.io.IAbstractFile;
import com.android.sdklib.io.IAbstractFolder;
import com.android.sdklib.io.StreamException;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * An implementation of {@link IAbstractFile} on top of an {@link IFile} object.
 */
public class IFileWrapper implements IAbstractFile {

    private final IFile mFile;

    public IFileWrapper(IFile file) {
        mFile = file;
    }

    public InputStream getContents() throws StreamException {
        try {
            return mFile.getContents();
        } catch (CoreException e) {
            throw new StreamException(e);
        }
    }

    public void setContents(InputStream source) throws StreamException {
        try {
            mFile.setContents(source, IResource.FORCE, null);
        } catch (CoreException e) {
            throw new StreamException(e);
        }
    }

    public OutputStream getOutputStream() throws StreamException {
        return new ByteArrayOutputStream() {
            @Override
            public void close() throws IOException {
                super.close();

                byte[] data = toByteArray();
                try {
                    setContents(new ByteArrayInputStream(data));
                } catch (StreamException e) {
                    throw new IOException();
                }
            }
        };
    }

    public PreferredWriteMode getPreferredWriteMode() {
        return PreferredWriteMode.INPUTSTREAM;
    }

    public String getOsLocation() {
        return mFile.getLocation().toOSString();
    }

    public String getName() {
        return mFile.getName();
    }

    public boolean exists() {
        return mFile.exists();
    }

    /**
     * Returns the {@link IFile} object that the receiver could represent. Can be <code>null</code>
     */
    public IFile getIFile() {
        return mFile;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof IFileWrapper) {
            return mFile.equals(((IFileWrapper)obj).mFile);
        }

        if (obj instanceof IFile) {
            return mFile.equals(obj);
        }

        return super.equals(obj);
    }

    @Override
    public int hashCode() {
        return mFile.hashCode();
    }

    public IAbstractFolder getParentFolder() {
        IContainer p = mFile.getParent();
        if (p != null) {
            return new IFolderWrapper(p);
        }

        return null;
    }
}
