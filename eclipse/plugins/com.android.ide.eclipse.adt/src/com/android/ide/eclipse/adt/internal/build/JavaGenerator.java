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
import com.android.ide.eclipse.adt.internal.project.BaseProjectHelper;
import com.android.ide.eclipse.adt.internal.project.ProjectHelper;
import com.android.sdklib.IAndroidTarget;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IJavaProject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    public final static int COMPILE_STATUS_NONE = 0;
    public final static int COMPILE_STATUS_CODE = 0x1;
    public final static int COMPILE_STATUS_RES = 0x2;

    /** List of all source files, their dependencies, and their output. */
    private final Map<IFile, NonJavaFileBundle> mFiles = new HashMap<IFile, NonJavaFileBundle>();

    private final IJavaProject mJavaProject;
    private final IFolder mGenFolder;
    private final GeneratorDeltaVisitor mDeltaVisitor;

    /** List of source files pending compilation at the next build */
    private final List<IFile> mToCompile = new ArrayList<IFile>();

    /** List of removed source files pending cleaning at the next build. */
    private final List<IFile> mRemoved = new ArrayList<IFile>();

    protected JavaGenerator(IJavaProject javaProject, IFolder genFolder,
            GeneratorDeltaVisitor deltaVisitor) {
        mJavaProject = javaProject;
        mGenFolder = genFolder;
        mDeltaVisitor = deltaVisitor;

        mDeltaVisitor.init(this);

        IProject project = javaProject.getProject();

        // get all the source files
        buildSourceFileList();

        // load the known dependencies
        loadOutputAndDependencies();

        boolean mustCompile = loadState(project);

        // if we stored that we have to compile some files, we build the list that will compile them
        // all. For now we have to reuse the full list since we don't know which files needed
        // compilation.
        if (mustCompile) {
            mToCompile.addAll(mFiles.keySet());
        }
    }

    protected JavaGenerator(IJavaProject javaProject, IFolder genFolder) {
        this(javaProject, genFolder, new GeneratorDeltaVisitor());
    }


    /**
     * Returns whether the given file is an output of this generator by return the source
     * file that generated it.
     * @param file the file to test.
     * @return the source file that generated the given file or null.
     */
    IFile isOutput(IFile file) {
        for (NonJavaFileBundle bundle : mFiles.values()) {
            if (bundle.generated(file)) {
                return bundle.getSourceFile();
            }
        }

        return null;
    }

    /**
     * Returns whether the given file is a dependency for other files by returning a list
     * of file depending on the given file.
     * @param file the file to test.
     * @return a list of files that depend on the given file or an empty list if there
     *    are no matches.
     */
    List<IFile> isDependency(IFile file) {
        ArrayList<IFile> files = new ArrayList<IFile>();
        for (NonJavaFileBundle bundle : mFiles.values()) {
            if (bundle.dependsOn(file)) {
                files.add(bundle.getSourceFile());
            }
        }

        return files;
    }

    void addBundle(NonJavaFileBundle bundle) {
        mFiles.put(bundle.getSourceFile(), bundle);
    }

    NonJavaFileBundle getBundle(IFile file) {
        return mFiles.get(file);
    }

    Collection<NonJavaFileBundle> getBundles() {
        return mFiles.values();
    }

    public final GeneratorDeltaVisitor getDeltaVisitor() {
        return mDeltaVisitor;
    }

    final IJavaProject getJavaProject() {
        return mJavaProject;
    }

    final IFolder getGenFolder() {
        return mGenFolder;
    }

    final List<IFile> getToCompile() {
        return mToCompile;
    }

    final List<IFile> getRemovedFile() {
        return mRemoved;
    }

    final void addFileToCompile(IFile file) {
        mToCompile.add(file);
    }

    public final void prepareFullBuild(IProject project) {
        mDeltaVisitor.reset();

        // get all the source files
        buildSourceFileList();

        mToCompile.addAll(mFiles.keySet());
    }

    public final void doneVisiting(IProject project) {
        // merge the previous file modification lists and the new one.
        mergeFileModifications(mDeltaVisitor);

        mDeltaVisitor.reset();

        saveState(project);
    }

    /**
     * Returns the extension of the source files handled by this generator.
     * @return
     */
    protected abstract String getExtension();

    protected abstract String getSavePropertyName();

    /**
     * Compiles the source files and return what type of file was generated.
     *
     * @see #getCompilationType()
     */
    public final int compileFiles(BaseBuilder builder,
            IProject project, IAndroidTarget projectTarget,
            List<IPath> sourceFolders, IProgressMonitor monitor) throws CoreException {

        if (mToCompile.size() == 0 && mRemoved.size() == 0) {
            return COMPILE_STATUS_NONE;
        }

        // if a source file is being removed before we managed to compile it, it'll be in
        // both list. We *need* to remove it from the compile list or it'll never go away.
        for (IFile sourceFile : mRemoved) {
            int pos = mToCompile.indexOf(sourceFile);
            if (pos != -1) {
                mToCompile.remove(pos);
            }
        }

        // list of files that have failed compilation.
        List<IFile> stillNeedCompilation = new ArrayList<IFile>();

        doCompileFiles(mToCompile, builder, project, projectTarget, sourceFolders,
                stillNeedCompilation, monitor);

        mToCompile.clear();
        mToCompile.addAll(stillNeedCompilation);

        // Remove the files created from source files that have been removed.
        for (IFile sourceFile : mRemoved) {
            // look if we already know the output
            NonJavaFileBundle bundle = getBundle(sourceFile);
            if (bundle != null) {
                doRemoveFiles(bundle);
            }
        }

        // remove the associated bundles.
        for (IFile removedFile : mRemoved) {
            mFiles.remove(removedFile);
        }

        mRemoved.clear();

        // store the build state. If there are any files that failed to compile, we will
        // force a full aidl compile on the next project open. (unless a full compilation succeed
        // before the project is closed/re-opened.)
        saveState(project);

        return getCompilationType();
    }

    protected abstract void doCompileFiles(
            List<IFile> filesToCompile, BaseBuilder builder,
            IProject project, IAndroidTarget projectTarget,
            List<IPath> sourceFolders, List<IFile> notCompiledOut, IProgressMonitor monitor)
            throws CoreException;

    /**
     * Returns the type of compilation. It can be any of (in combination too):
     * <p/>
     * {@link #COMPILE_STATUS_CODE} means this generator created source files.
     * {@link #COMPILE_STATUS_RES} means this generator created resources.
     */
    protected abstract int getCompilationType();

    protected void doRemoveFiles(NonJavaFileBundle bundle) throws CoreException {
        List<IFile> outputFiles = bundle.getOutputFiles();
        for (IFile outputFile : outputFiles) {
            if (outputFile.exists()) {
                outputFile.getLocation().toFile().delete();
            }
        }
    }

    public final boolean loadState(IProject project) {
        return ProjectHelper.loadBooleanProperty(project, getSavePropertyName(),
                true /*defaultValue*/);
    }

    public final void saveState(IProject project) {
        // TODO: Optimize by saving only the files that need compilation
        ProjectHelper.saveStringProperty(project, getSavePropertyName(),
                Boolean.toString(getToCompile().size() > 0));
    }

    protected abstract void loadOutputAndDependencies();


    protected IPath getSourceFolderFor(IFile file) {
        // find the source folder for the class so that we can infer the package from the
        // difference between the file and its source folder.
        List<IPath> sourceFolders = BaseProjectHelper.getSourceClasspaths(getJavaProject());
        IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();

        for (IPath sourceFolderPath : sourceFolders) {
            IFolder sourceFolder = root.getFolder(sourceFolderPath);
            // we don't look in the 'gen' source folder as there will be no source in there.
            if (sourceFolder.exists() && sourceFolder.equals(getGenFolder()) == false) {
                // look for the source file parent, until we find this source folder.
                IResource parent = file;
                while ((parent = parent.getParent()) != null) {
                    if (parent.equals(sourceFolder)) {
                        return sourceFolderPath;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Goes through the build paths and fills the list of files to compile.
     *
     * @param project The project.
     * @param sourceFolderPathList The list of source folder paths.
     */
    private final void buildSourceFileList() {
        mFiles.clear();

        IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
        List<IPath> sourceFolderPathList = BaseProjectHelper.getSourceClasspaths(mJavaProject);

        for (IPath sourceFolderPath : sourceFolderPathList) {
            IFolder sourceFolder = root.getFolder(sourceFolderPath);
            // we don't look in the 'gen' source folder as there will be no source in there.
            if (sourceFolder.exists() && sourceFolder.equals(getGenFolder()) == false) {
                scanFolderForSourceFiles(sourceFolder, sourceFolder);
            }
        }
    }

    /**
     * Scans a folder and fills the list of files to compile.
     * @param sourceFolder the root source folder.
     * @param folder The folder to scan.
     */
    private void scanFolderForSourceFiles(IFolder sourceFolder, IFolder folder) {
        try {
            IResource[] members = folder.members();
            for (IResource r : members) {
                // get the type of the resource
               switch (r.getType()) {
                   case IResource.FILE:
                       // if this a file, check that the file actually exist
                       // and that it's the type of of file that's used in this generator
                       if (r.exists() &&
                               getExtension().equalsIgnoreCase(r.getFileExtension())) {
                           mFiles.put((IFile) r, new NonJavaFileBundle((IFile) r));
                       }
                       break;
                   case IResource.FOLDER:
                       // recursively go through children
                       scanFolderForSourceFiles(sourceFolder, (IFolder)r);
                       break;
                   default:
                       // this would mean it's a project or the workspace root
                       // which is unlikely to happen. we do nothing
                       break;
               }
            }
        } catch (CoreException e) {
            // Couldn't get the members list for some reason. Just return.
        }
    }


    /**
     * Merge the current list of source file to compile/remove with the one coming from the
     * delta visitor
     * @param visitor the delta visitor.
     */
    private void mergeFileModifications(GeneratorDeltaVisitor visitor) {
        Set<IFile> toRemove = visitor.getRemovedFiles();
        Set<IFile> toCompile = visitor.getFilesToCompile();

        // loop through the new toRemove list, and add it to the old one,
        // plus remove any file that was still to compile and that are now
        // removed
        for (IFile r : toRemove) {
            if (mRemoved.indexOf(r) == -1) {
                mRemoved.add(r);
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
        for (IFile r : toCompile) {
            if (mToCompile.indexOf(r) == -1) {
                mToCompile.add(r);
            }

            int index = mRemoved.indexOf(r);
            if (index != -1) {
                mRemoved.remove(index);
            }
        }
    }
}
