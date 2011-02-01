/*
 * Copyright (C) 2011 The Android Open Source Project
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

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;

import java.util.HashSet;
import java.util.Set;

/**
 * Base delta visitor for the Java generator classes.
 *
 * It can be used as is, as long as the matching {@link JavaGenerator} properly implements
 * its abstract methods, and the generator does not output resource files,
 * or can be extended to provide custom implementation for:
 * {@link #handleSourceFile(IFile, int)}
 * {@link #handleGeneratedFile(IFile, int)}
 * {@link #handleResourceFile(IFile, int)}
 * {@link #filterResourceFolder(IContainer)}
 *
 * Note that this is not actually a {@link IResourceDeltaVisitor}, it's meant to be used by
 * PreCompilerDeltaVisitor
 */
public class GeneratorDeltaVisitor {

    private JavaGenerator mGenerator;

    /** List of source files found that are modified or new. */
    private final Set<IFile> mToCompile = new HashSet<IFile>();

    /** List of source files that have been removed. */
    private final Set<IFile> mRemoved = new HashSet<IFile>();

    public boolean handleGeneratedFile(IFile file, int kind) {
        if (kind == IResourceDelta.REMOVED || kind == IResourceDelta.CHANGED) {
            IFile sourceFile = mGenerator.isOutput(file);
            if (sourceFile != null) {
                mToCompile.add(sourceFile);
                return true;
            }
        }

        return false;
    }

    public void handleSourceFile(IFile file, int kind) {
        // first the file itself if this is a match for the generator's extension
        if (mGenerator.getExtension().equals(file.getFileExtension())) {
            if (kind == IResourceDelta.REMOVED) {
                mRemoved.add(file);
            } else {
                mToCompile.add(file);
            }
        }

        // now the dependencies. In all case we compile the files that depend on the
        // added/changed/removed file.
        mToCompile.addAll(mGenerator.isDependency(file));
    }

    public void handleResourceFile(IFile file, int kind) {
        if (filterResourceFolder(file.getParent())) {
            handleGeneratedFile(file, kind);
        }
    }

    /**
     * Called to restrict {@link #handleResourceFile(IFile, int)} on selected resource folders.
     * @param folder
     * @return
     */
    protected boolean filterResourceFolder(IContainer folder) {
        return false;
    }

    protected void addFileToCompile(IFile file) {
        mToCompile.add(file);
    }

    Set<IFile> getFilesToCompile() {
        return mToCompile;
    }

    protected void addRemovedFile(IFile file) {
        mRemoved.add(file);
    }

    Set<IFile> getRemovedFiles() {
        return mRemoved;
    }

    public void reset() {
        mToCompile.clear();
        mRemoved.clear();
    }

    protected JavaGenerator getGenerator() {
        return mGenerator;
    }

    void init(JavaGenerator generator) {
        mGenerator = generator;
    }
}
