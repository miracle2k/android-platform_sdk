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

import com.android.ide.eclipse.adt.internal.build.builders.BaseBuilder;
import com.android.sdklib.IAndroidTarget;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class to handle generated java code.
 *
 * It provides management for modified source file list, deleted source file list, reconciliation
 * of previous lists, storing the current state of the build.
 *
 * It also provides a base class for delta visitor that should be customized for each Generator
 * extending this class.
 *
 */
public abstract class JavaGenerator {

    /**
     * Data to temporarily store source file information.
     */
    protected static class SourceData {
        IFile sourceFile;
        /** this is the root source folder, not the file parent. */
        IFolder sourceFolder;

        SourceData(IFolder sourceFolder, IFile sourceFile) {
            this.sourceFolder = sourceFolder;
            this.sourceFile = sourceFile;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((sourceFile == null) ? 0 : sourceFile.hashCode());
            result = prime * result + ((sourceFolder == null) ? 0 : sourceFolder.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            SourceData other = (SourceData) obj;
            if (sourceFile == null) {
                if (other.sourceFile != null)
                    return false;
            } else if (!sourceFile.equals(other.sourceFile))
                return false;
            if (sourceFolder == null) {
                if (other.sourceFolder != null)
                    return false;
            } else if (!sourceFolder.equals(other.sourceFolder))
                return false;
            return true;
        }

        @Override
        public String toString() {
            return "SourceData [sourceFile=" //$NON-NLS-1$
                + sourceFile
                + ", sourceFolder="          //$NON-NLS-1$
                + sourceFolder
                + "]";                       //$NON-NLS-1$
        }
    }

    /**
     * Base delta visitor for the Java generator classes.
     *
     * It provides storage for modified and deleted source files and a few other features.
     */
    public abstract static class JavaGeneratorDeltaVisitor {

        private IWorkspaceRoot mRoot;

        private boolean mForceCompile;

        /** List of source files found that are modified or new. */
        private final List<SourceData> mToCompile = new ArrayList<SourceData>();

        /** List of .aidl files that have been removed. */
        private final List<SourceData> mToRemove = new ArrayList<SourceData>();

        public abstract boolean handleChangedGeneratedJavaFile(IFolder currentSourceFolder,
                IFile file, List<IPath> sourceFolders);

        public abstract void handleChangedNonJavaFile(IFolder currentSourceFolder,
                IFile file, int kind);

        public void setWorkspaceRoot(IWorkspaceRoot root) {
            mRoot = root;
        }

        public void setForceCompile() {
            mForceCompile = true;
        }

        boolean getForceCompile() {
            return mForceCompile;
        }

        public void addFileToCompile(IFolder sourceFolder, IFile sourceFile) {
            mToCompile.add(new SourceData(sourceFolder, sourceFile));
        }

        List<SourceData> getFilesToCompile() {
            return mToCompile;
        }

        public void addFileToRemove(IFolder sourceFolder, IFile sourceFile) {
            mToRemove.add(new SourceData(sourceFolder, sourceFile));
        }

        List<SourceData> getFilesToRemove() {
            return mToRemove;
        }

        public void reset() {
            mForceCompile = false;
            mToCompile.clear();
            mToRemove.clear();
        }

        /**
         * Returns a handle to the folder identified by the given path in this container.
         * <p/>The different with {@link IContainer#getFolder(IPath)} is that this returns a non
         * null object only if the resource actually exists and is a folder (and not a file)
         * @param path the path of the folder to return.
         * @return a handle to the folder if it exists, or null otherwise.
         */
        protected IFolder getFolder(IPath path) {
            IResource resource = mRoot.findMember(path);
            if (resource != null && resource.exists() && resource.getType() == IResource.FOLDER) {
                return (IFolder)resource;
            }

            return null;
        }

        /**
         * Searches for and return a file in a folder. The file is defined by its segments, and
         * a new name (replacing the last segment).
         * @param folder the folder we are searching
         * @param segments the segments of the file to search.
         * @param index the index of the current segment we are looking for
         * @param filename the new name to replace the last segment.
         * @return the {@link IFile} representing the searched file, or null if not found
         */
        protected IFile findFile(IFolder folder, String[] segments, int index, String filename) {
            boolean lastSegment = index == segments.length - 1;
            IResource resource = folder.findMember(lastSegment ? filename : segments[index]);
            if (resource != null && resource.exists()) {
                if (lastSegment) {
                    if (resource.getType() == IResource.FILE) {
                        return (IFile)resource;
                    }
                } else {
                    if (resource.getType() == IResource.FOLDER) {
                        return findFile((IFolder)resource, segments, index+1, filename);
                    }
                }
            }
            return null;
        }
    }

    /** List of source files found that are modified or new. */
    private final List<SourceData> mToCompile = new ArrayList<SourceData>();

    /** List of .aidl files that have been removed. */
    private final List<SourceData> mToRemove = new ArrayList<SourceData>();

    private final JavaGeneratorDeltaVisitor mDeltaVisitor;

    private final IFolder mGenFolder;

    protected JavaGenerator(JavaGeneratorDeltaVisitor deltaVisitor, IFolder genFolder) {
        mDeltaVisitor = deltaVisitor;
        mGenFolder = genFolder;
    }

    public JavaGeneratorDeltaVisitor getDeltaVisitor() {
        return mDeltaVisitor;
    }

    IFolder getGenFolder() {
        return mGenFolder;
    }

    List<SourceData> getToCompile() {
        return mToCompile;
    }

    List<SourceData> getToRemove() {
        return mToRemove;
    }

    void addFileToCompile(IFolder sourceFolder, IFile sourceFile) {
        mToCompile.add(new SourceData(sourceFolder, sourceFile));
    }

    public void prepareFullBuild(IProject project, List<IPath> sourceFolders) {
        mDeltaVisitor.reset();
        buildCompilationList(project, sourceFolders);
    }

    public void doneVisiting(IProject project, List<IPath> sourceFolders) {
        if (mDeltaVisitor.getForceCompile()) {
            buildCompilationList(project, sourceFolders);
        } else {
            // merge the previous file modification lists and the new one.
            mergeFileModifications(mDeltaVisitor);
        }

        mDeltaVisitor.reset();

        saveState(project);
    }

    protected abstract void buildCompilationList(IProject project, List<IPath> sourceFolders);

    public abstract boolean compileFiles(BaseBuilder builder,
            IProject project, IAndroidTarget projectTarget,
            List<IPath> sourceFolders, IProgressMonitor monitor) throws CoreException;

    public abstract void saveState(IProject project);

    /**
     * Merge the current list of source file to compile/remove with the one coming from the
     * delta visitor
     * @param visitor the delta visitor.
     */
    private void mergeFileModifications(JavaGeneratorDeltaVisitor visitor) {
        List<SourceData> toRemove = visitor.getFilesToRemove();
        List<SourceData> toCompile = visitor.getFilesToCompile();

        // loop through the new toRemove list, and add it to the old one,
        // plus remove any file that was still to compile and that are now
        // removed
        for (SourceData r : toRemove) {
            if (mToRemove.indexOf(r) == -1) {
                mToRemove.add(r);
            }

            int index = mToCompile.indexOf(r);
            if (index != -1) {
                mToCompile.remove(index);
            }
        }

        // now loop through the new files to compile and add it to the list.
        // Also look for them in the remove list, this would mean that they
        // were removed, then added back, and we shouldn't remove them, just
        // recompile them.
        for (SourceData r : toCompile) {
            if (mToCompile.indexOf(r) == -1) {
                mToCompile.add(r);
            }

            int index = mToRemove.indexOf(r);
            if (index != -1) {
                mToRemove.remove(index);
            }
        }
    }
}
