/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.ide.eclipse.adt.internal.build.builders;

import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.AndroidConstants;
import com.android.ide.eclipse.adt.internal.build.AaptParser;
import com.android.ide.eclipse.adt.internal.build.AidlProcessor;
import com.android.ide.eclipse.adt.internal.build.SourceProcessor;
import com.android.ide.eclipse.adt.internal.build.Messages;
import com.android.ide.eclipse.adt.internal.build.RenderScriptProcessor;
import com.android.ide.eclipse.adt.internal.preferences.AdtPrefs;
import com.android.ide.eclipse.adt.internal.preferences.AdtPrefs.BuildVerbosity;
import com.android.ide.eclipse.adt.internal.project.AndroidManifestHelper;
import com.android.ide.eclipse.adt.internal.project.BaseProjectHelper;
import com.android.ide.eclipse.adt.internal.project.FixLaunchConfig;
import com.android.ide.eclipse.adt.internal.project.ProjectHelper;
import com.android.ide.eclipse.adt.internal.project.XmlErrorHandler.BasicXmlErrorListener;
import com.android.ide.eclipse.adt.internal.sdk.ProjectState;
import com.android.ide.eclipse.adt.internal.sdk.Sdk;
import com.android.ide.eclipse.adt.io.IFileWrapper;
import com.android.ide.eclipse.adt.io.IFolderWrapper;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkConstants;
import com.android.sdklib.xml.AndroidManifest;
import com.android.sdklib.xml.ManifestData;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pre Java Compiler.
 * This incremental builder performs 2 tasks:
 * <ul>
 * <li>compiles the resources located in the res/ folder, along with the
 * AndroidManifest.xml file into the R.java class.</li>
 * <li>compiles any .aidl files into a corresponding java file.</li>
 * </ul>
 *
 */
public class PreCompilerBuilder extends BaseBuilder {

    /** This ID is used in plugin.xml and in each project's .project file.
     * It cannot be changed even if the class is renamed/moved */
    public static final String ID = "com.android.ide.eclipse.adt.PreCompilerBuilder"; //$NON-NLS-1$

    private static final String PROPERTY_PACKAGE = "manifestPackage"; //$NON-NLS-1$

    private static final String PROPERTY_COMPILE_RESOURCES = "compileResources"; //$NON-NLS-1$

    /**
     * Resource Compile flag. This flag is reset to false after each successful compilation, and
     * stored in the project persistent properties. This allows the builder to remember its state
     * when the project is closed/opened.
     */
    private boolean mMustCompileResources = false;

    private final List<SourceProcessor> mProcessors = new ArrayList<SourceProcessor>();

    /** cache of the java package defined in the manifest */
    private String mManifestPackage;

    /** Output folder for generated Java File. Created on the Builder init
     * @see #startupOnInitialize()
     */
    private IFolder mGenFolder;

    /**
     * Progress monitor used at the end of every build to refresh the content of the 'gen' folder
     * and set the generated files as derived.
     */
    private DerivedProgressMonitor mDerivedProgressMonitor;


    /**
     * Progress monitor waiting the end of the process to set a persistent value
     * in a file. This is typically used in conjunction with <code>IResource.refresh()</code>,
     * since this call is asynchronous, and we need to wait for it to finish for the file
     * to be known by eclipse, before we can call <code>resource.setPersistentProperty</code> on
     * a new file.
     */
    private static class DerivedProgressMonitor implements IProgressMonitor {
        private boolean mCancelled = false;
        private boolean mDone = false;
        private final IFolder mGenFolder;

        public DerivedProgressMonitor(IFolder genFolder) {
            mGenFolder = genFolder;
        }

        void reset() {
            mDone = false;
        }

        public void beginTask(String name, int totalWork) {
        }

        public void done() {
            if (mDone == false) {
                mDone = true;
                processChildrenOf(mGenFolder);
            }
        }

        private void processChildrenOf(IFolder folder) {
            IResource[] list;
            try {
                list = folder.members();
            } catch (CoreException e) {
                return;
            }

            for (IResource member : list) {
                if (member.exists()) {
                    if (member.getType() == IResource.FOLDER) {
                        processChildrenOf((IFolder) member);
                    }

                    try {
                        member.setDerived(true);
                    } catch (CoreException e) {
                        // This really shouldn't happen since we check that the resource
                        // exist.
                        // Worst case scenario, the resource isn't marked as derived.
                    }
                }
            }
        }

        public void internalWorked(double work) {
        }

        public boolean isCanceled() {
            return mCancelled;
        }

        public void setCanceled(boolean value) {
            mCancelled = value;
        }

        public void setTaskName(String name) {
        }

        public void subTask(String name) {
        }

        public void worked(int work) {
        }
    }

    public PreCompilerBuilder() {
        super();
    }

    // build() returns a list of project from which this project depends for future compilation.
    @SuppressWarnings("unchecked")
    @Override
    protected IProject[] build(int kind, Map args, IProgressMonitor monitor)
            throws CoreException {
        // get a project object
        IProject project = getProject();

        // For the PreCompiler, only the library projects are considered Referenced projects,
        // as only those projects have an impact on what is generated by this builder.
        IProject[] result = null;

        try {
            mDerivedProgressMonitor.reset();

            // get the project info
            ProjectState projectState = Sdk.getProjectState(project);

            // this can happen if the project has no default.properties.
            if (projectState == null) {
                return null;
            }

            IAndroidTarget projectTarget = projectState.getTarget();

            // get the libraries
            List<IProject> libProjects = projectState.getFullLibraryProjects();
            result = libProjects.toArray(new IProject[libProjects.size()]);

            IJavaProject javaProject = JavaCore.create(project);

            // Top level check to make sure the build can move forward.
            abortOnBadSetup(javaProject);

            // now we need to get the classpath list
            List<IPath> sourceFolderPathList = BaseProjectHelper.getSourceClasspaths(javaProject);

            PreCompilerDeltaVisitor dv = null;
            String javaPackage = null;
            String minSdkVersion = null;

            if (kind == FULL_BUILD) {
                AdtPlugin.printBuildToConsole(BuildVerbosity.VERBOSE, project,
                        Messages.Start_Full_Pre_Compiler);

                // do some clean up.
                doClean(project, monitor);

                mMustCompileResources = true;

                for (SourceProcessor processor : mProcessors) {
                    processor.prepareFullBuild(project);
                }
            } else {
                AdtPlugin.printBuildToConsole(BuildVerbosity.VERBOSE, project,
                        Messages.Start_Inc_Pre_Compiler);

                // Go through the resources and see if something changed.
                // Even if the mCompileResources flag is true from a previously aborted
                // build, we need to go through the Resource delta to get a possible
                // list of aidl files to compile/remove.
                IResourceDelta delta = getDelta(project);
                if (delta == null) {
                    mMustCompileResources = true;

                    for (SourceProcessor processor : mProcessors) {
                        processor.prepareFullBuild(project);
                    }
                } else {
                    dv = new PreCompilerDeltaVisitor(this, sourceFolderPathList, mProcessors);
                    delta.accept(dv);

                    // record the state
                    mMustCompileResources |= dv.getCompileResources();
                    for (SourceProcessor processor : mProcessors) {
                        processor.doneVisiting(project);
                    }

                    // get the java package from the visitor
                    javaPackage = dv.getManifestPackage();
                    minSdkVersion = dv.getMinSdkVersion();

                    // if the main resources didn't change, then we check for the library
                    // ones (will trigger resource recompilation too)
                    if (mMustCompileResources == false && libProjects.size() > 0) {
                        for (IProject libProject : libProjects) {
                            delta = getDelta(libProject);
                            if (delta != null) {
                                LibraryDeltaVisitor visitor = new LibraryDeltaVisitor();
                                delta.accept(visitor);

                                mMustCompileResources = visitor.getResChange();

                                if (mMustCompileResources) {
                                    break;
                                }
                            }
                        }
                    }
                }
            }

            // store the build status in the persistent storage
            saveProjectBooleanProperty(PROPERTY_COMPILE_RESOURCES , mMustCompileResources);

            // if there was some XML errors, we just return w/o doing
            // anything since we've put some markers in the files anyway.
            if (dv != null && dv.mXmlError) {
                AdtPlugin.printErrorToConsole(project, Messages.Xml_Error);

                return result;
            }


            // get the manifest file
            IFile manifestFile = ProjectHelper.getManifest(project);

            if (manifestFile == null) {
                String msg = String.format(Messages.s_File_Missing,
                        SdkConstants.FN_ANDROID_MANIFEST_XML);
                AdtPlugin.printErrorToConsole(project, msg);
                markProject(AndroidConstants.MARKER_ADT, msg, IMarker.SEVERITY_ERROR);

                return result;

                // TODO: document whether code below that uses manifest (which is now guaranteed
                // to be null) will actually be executed or not.
            }

            // lets check the XML of the manifest first, if that hasn't been done by the
            // resource delta visitor yet.
            if (dv == null || dv.getCheckedManifestXml() == false) {
                BasicXmlErrorListener errorListener = new BasicXmlErrorListener();
                ManifestData parser = AndroidManifestHelper.parse(new IFileWrapper(manifestFile),
                        true /*gather data*/,
                        errorListener);

                if (errorListener.mHasXmlError == true) {
                    // There was an error in the manifest, its file has been marked
                    // by the XmlErrorHandler. The stopBuild() call below will abort
                    // this with an exception.
                    String msg = String.format(Messages.s_Contains_Xml_Error,
                            SdkConstants.FN_ANDROID_MANIFEST_XML);
                    AdtPlugin.printBuildToConsole(BuildVerbosity.VERBOSE, project, msg);

                    return result;
                }

                // Get the java package from the parser.
                // This can be null if the parsing failed because the resource is out of sync,
                // in which case the error will already have been logged anyway.
                if (parser != null) {
                    javaPackage = parser.getPackage();
                    minSdkVersion = parser.getMinSdkVersionString();
                }
            }

            if (minSdkVersion != null) {
                int minSdkValue = -1;
                try {
                    minSdkValue = Integer.parseInt(minSdkVersion);
                } catch (NumberFormatException e) {
                    // it's ok, it means minSdkVersion contains a (hopefully) valid codename.
                }

                AndroidVersion projectVersion = projectTarget.getVersion();

                // remove earlier marker from the manifest
                removeMarkersFromFile(manifestFile, AndroidConstants.MARKER_ADT);

                if (minSdkValue != -1) {
                    String codename = projectVersion.getCodename();
                    if (codename != null) {
                        // integer minSdk when the target is a preview => fatal error
                        String msg = String.format(
                                "Platform %1$s is a preview and requires application manifest to set %2$s to '%1$s'",
                                codename, AndroidManifest.ATTRIBUTE_MIN_SDK_VERSION);
                        AdtPlugin.printErrorToConsole(project, msg);
                        BaseProjectHelper.markResource(manifestFile, AndroidConstants.MARKER_ADT,
                                msg, IMarker.SEVERITY_ERROR);
                        return result;
                    } else if (minSdkValue < projectVersion.getApiLevel()) {
                        // integer minSdk is not high enough for the target => warning
                        String msg = String.format(
                                "Attribute %1$s (%2$d) is lower than the project target API level (%3$d)",
                                AndroidManifest.ATTRIBUTE_MIN_SDK_VERSION,
                                minSdkValue, projectVersion.getApiLevel());
                        AdtPlugin.printBuildToConsole(BuildVerbosity.VERBOSE, project, msg);
                        BaseProjectHelper.markResource(manifestFile, AndroidConstants.MARKER_ADT,
                                msg, IMarker.SEVERITY_WARNING);
                    } else if (minSdkValue > projectVersion.getApiLevel()) {
                        // integer minSdk is too high for the target => warning
                        String msg = String.format(
                                "Attribute %1$s (%2$d) is higher than the project target API level (%3$d)",
                                AndroidManifest.ATTRIBUTE_MIN_SDK_VERSION,
                                minSdkValue, projectVersion.getApiLevel());
                        AdtPlugin.printBuildToConsole(BuildVerbosity.VERBOSE, project, msg);
                        BaseProjectHelper.markResource(manifestFile, AndroidConstants.MARKER_ADT,
                                msg, IMarker.SEVERITY_WARNING);
                    }
                } else {
                    // looks like the min sdk is a codename, check it matches the codename
                    // of the platform
                    String codename = projectVersion.getCodename();
                    if (codename == null) {
                        // platform is not a preview => fatal error
                        String msg = String.format(
                                "Manifest attribute '%1$s' is set to '%2$s'. Integer is expected.",
                                AndroidManifest.ATTRIBUTE_MIN_SDK_VERSION, minSdkVersion);
                        AdtPlugin.printErrorToConsole(project, msg);
                        BaseProjectHelper.markResource(manifestFile, AndroidConstants.MARKER_ADT,
                                msg, IMarker.SEVERITY_ERROR);
                        return result;
                    } else if (codename.equals(minSdkVersion) == false) {
                        // platform and manifest codenames don't match => fatal error.
                        String msg = String.format(
                                "Value of manifest attribute '%1$s' does not match platform codename '%2$s'",
                                AndroidManifest.ATTRIBUTE_MIN_SDK_VERSION, codename);
                        AdtPlugin.printErrorToConsole(project, msg);
                        BaseProjectHelper.markResource(manifestFile, AndroidConstants.MARKER_ADT,
                                msg, IMarker.SEVERITY_ERROR);
                        return result;
                    }
                }
            } else if (projectTarget.getVersion().isPreview()) {
                // else the minSdkVersion is not set but we are using a preview target.
                // Display an error
                String codename = projectTarget.getVersion().getCodename();
                String msg = String.format(
                        "Platform %1$s is a preview and requires application manifests to set %2$s to '%1$s'",
                        codename, AndroidManifest.ATTRIBUTE_MIN_SDK_VERSION);
                AdtPlugin.printErrorToConsole(project, msg);
                BaseProjectHelper.markResource(manifestFile, AndroidConstants.MARKER_ADT, msg,
                        IMarker.SEVERITY_ERROR);
                return result;
            }

            if (javaPackage == null || javaPackage.length() == 0) {
                // looks like the AndroidManifest file isn't valid.
                String msg = String.format(Messages.s_Doesnt_Declare_Package_Error,
                        SdkConstants.FN_ANDROID_MANIFEST_XML);
                AdtPlugin.printErrorToConsole(project, msg);
                BaseProjectHelper.markResource(manifestFile, AndroidConstants.MARKER_ADT,
                        msg, IMarker.SEVERITY_ERROR);

                return result;
            } else if (javaPackage.indexOf('.') == -1) {
                // The application package name does not contain 2+ segments!
                String msg = String.format(
                        "Application package '%1$s' must have a minimum of 2 segments.",
                        SdkConstants.FN_ANDROID_MANIFEST_XML);
                AdtPlugin.printErrorToConsole(project, msg);
                BaseProjectHelper.markResource(manifestFile, AndroidConstants.MARKER_ADT,
                        msg, IMarker.SEVERITY_ERROR);

                return result;
            }

            // at this point we have the java package. We need to make sure it's not a different
            // package than the previous one that were built.
            if (javaPackage.equals(mManifestPackage) == false) {
                // The manifest package has changed, the user may want to update
                // the launch configuration
                if (mManifestPackage != null) {
                    AdtPlugin.printBuildToConsole(BuildVerbosity.VERBOSE, project,
                            Messages.Checking_Package_Change);

                    FixLaunchConfig flc = new FixLaunchConfig(project, mManifestPackage,
                            javaPackage);
                    flc.start();
                }

                // record the new manifest package, and save it.
                mManifestPackage = javaPackage;
                saveProjectStringProperty(PROPERTY_PACKAGE, mManifestPackage);

                // force a clean
                doClean(project, monitor);
                mMustCompileResources = true;
                for (SourceProcessor processor : mProcessors) {
                    processor.prepareFullBuild(project);
                }

                saveProjectBooleanProperty(PROPERTY_COMPILE_RESOURCES , mMustCompileResources);
            }

            // run the source processors
            int processorStatus = SourceProcessor.COMPILE_STATUS_NONE;
            for (SourceProcessor processor : mProcessors) {
                try {
                    processorStatus |= processor.compileFiles(this,
                            project, projectTarget, sourceFolderPathList, monitor);
                } catch (Throwable t) {
                }
            }

            // if a processor created some resources file, force recompilation of the resources.
            if ((processorStatus & SourceProcessor.COMPILE_STATUS_RES) != 0) {
                mMustCompileResources = true;
                // save the current state before attempting the compilation
                saveProjectBooleanProperty(PROPERTY_COMPILE_RESOURCES , mMustCompileResources);
            }

            // handle the resources, after the processors are run since some (renderscript)
            // generate resources.
            boolean compiledTheResources = mMustCompileResources;
            if (mMustCompileResources) {
                handleResources(project, javaPackage, projectTarget, manifestFile, libProjects);
                saveProjectBooleanProperty(PROPERTY_COMPILE_RESOURCES , false);
            }

            if (processorStatus == SourceProcessor.COMPILE_STATUS_NONE &&
                    compiledTheResources == false) {
                AdtPlugin.printBuildToConsole(BuildVerbosity.VERBOSE, project,
                        Messages.Nothing_To_Compile);
            }
        } catch (AbortBuildException e) {
            return result;
        } finally {
            // refresh the 'gen' source folder. Once this is done with the custom progress
            // monitor to mark all new files as derived
            mGenFolder.refreshLocal(IResource.DEPTH_INFINITE, mDerivedProgressMonitor);
        }

        return result;
    }

    @Override
    protected void clean(IProgressMonitor monitor) throws CoreException {
        super.clean(monitor);

        doClean(getProject(), monitor);
        if (mGenFolder != null) {
            mGenFolder.refreshLocal(IResource.DEPTH_INFINITE, monitor);
        }
    }

    private void doClean(IProject project, IProgressMonitor monitor) throws CoreException {
        AdtPlugin.printBuildToConsole(BuildVerbosity.VERBOSE, project,
                Messages.Removing_Generated_Classes);

        // remove all the derived resources from the 'gen' source folder.
        if (mGenFolder != null) {
            removeDerivedResources(mGenFolder, monitor);
        }

        // Clear the project of the generic markers
        removeMarkersFromProject(project, AndroidConstants.MARKER_AAPT_COMPILE);
        removeMarkersFromProject(project, AndroidConstants.MARKER_XML);
        removeMarkersFromProject(project, AndroidConstants.MARKER_AIDL);
        removeMarkersFromProject(project, AndroidConstants.MARKER_ANDROID);
    }

    @Override
    protected void startupOnInitialize() {
        super.startupOnInitialize();

        IProject project = getProject();

        // load the previous IFolder and java package.
        mManifestPackage = loadProjectStringProperty(PROPERTY_PACKAGE);

        // get the source folder in which all the Java files are created
        mGenFolder = project.getFolder(SdkConstants.FD_GEN_SOURCES);

        // Load the current compile flags. We ask for true if not found to force a recompile.
        mMustCompileResources = loadProjectBooleanProperty(PROPERTY_COMPILE_RESOURCES, true);

        IJavaProject javaProject = JavaCore.create(project);

        // load the source processors
        SourceProcessor aidlProcessor = new AidlProcessor(javaProject, mGenFolder);
        mProcessors.add(aidlProcessor);
        SourceProcessor renderScriptProcessor = new RenderScriptProcessor(javaProject, mGenFolder);
        mProcessors.add(renderScriptProcessor);

        mDerivedProgressMonitor = new DerivedProgressMonitor(mGenFolder);
    }

    /**
     * Handles resource changes and regenerate whatever files need regenerating.
     * @param project the main project
     * @param javaPackage the app package for the main project
     * @param projectTarget the target of the main project
     * @param manifest the {@link IFile} representing the project manifest
     * @param libProjects the library dependencies
     * @throws CoreException
     * @throws AbortBuildException
     */
    private void handleResources(IProject project, String javaPackage, IAndroidTarget projectTarget,
            IFile manifest, List<IProject> libProjects) throws CoreException, AbortBuildException {
        // get the resource folder
        IFolder resFolder = project.getFolder(AndroidConstants.WS_RESOURCES);

        // get the file system path
        IPath outputLocation = mGenFolder.getLocation();
        IPath resLocation = resFolder.getLocation();
        IPath manifestLocation = manifest == null ? null : manifest.getLocation();

        // those locations have to exist for us to do something!
        if (outputLocation != null && resLocation != null
                && manifestLocation != null) {
            String osOutputPath = outputLocation.toOSString();
            String osResPath = resLocation.toOSString();
            String osManifestPath = manifestLocation.toOSString();

            // remove the aapt markers
            removeMarkersFromFile(manifest, AndroidConstants.MARKER_AAPT_COMPILE);
            removeMarkersFromContainer(resFolder, AndroidConstants.MARKER_AAPT_COMPILE);

            AdtPlugin.printBuildToConsole(BuildVerbosity.VERBOSE, project,
                    Messages.Preparing_Generated_Files);

            // we need to figure out where to store the R class.
            // get the parent folder for R.java and update mManifestPackageSourceFolder
            IFolder mainPackageFolder = getGenManifestPackageFolder();

            // handle libraries
            ArrayList<IFolder> libResFolders = new ArrayList<IFolder>();
            ArrayList<IFolder> libOutputFolders = new ArrayList<IFolder>();
            ArrayList<String> libJavaPackages = new ArrayList<String>();
            if (libProjects != null) {
                for (IProject lib : libProjects) {
                    IFolder libResFolder = lib.getFolder(SdkConstants.FD_RES);
                    if (libResFolder.exists()) {
                        libResFolders.add(libResFolder);
                    }

                    try {
                        String libJavaPackage = AndroidManifest.getPackage(new IFolderWrapper(lib));
                        if (libJavaPackage.equals(javaPackage) == false) {
                            libJavaPackages.add(libJavaPackage);
                            libOutputFolders.add(getGenManifestPackageFolder(libJavaPackage));
                        }
                    } catch (Exception e) {
                    }
                }
            }

            execAapt(project, projectTarget, osOutputPath, osResPath, osManifestPath,
                    mainPackageFolder, libResFolders, null /* custom java package */);

            final int count = libOutputFolders.size();
            if (count > 0) {
                for (int i = 0 ; i < count ; i++) {
                    IFolder libFolder = libOutputFolders.get(i);
                    String libJavaPackage = libJavaPackages.get(i);
                    execAapt(project, projectTarget, osOutputPath, osResPath, osManifestPath,
                            libFolder, libResFolders, libJavaPackage);
                }
            }
        }
    }

    /**
     * Executes AAPT to generate R.java/Manifest.java
     * @param project the main project
     * @param projectTarget the main project target
     * @param osOutputPath the OS output path for the generated file. This is the source folder, not
     * the package folder.
     * @param osResPath the OS path to the res folder for the main project
     * @param osManifestPath the OS path to the manifest of the main project
     * @param packageFolder the IFolder that will contain the generated file. Unlike
     * <var>osOutputPath</var> this is the direct parent of the generated files.
     * If <var>customJavaPackage</var> is not null, this must match the new destination triggered
     * by its value.
     * @param libResFolders the list of res folders for the library.
     * @param customJavaPackage an optional javapackage to replace the main project java package.
     * can be null.
     * @throws AbortBuildException
     */
    private void execAapt(IProject project, IAndroidTarget projectTarget, String osOutputPath,
            String osResPath, String osManifestPath, IFolder packageFolder,
            ArrayList<IFolder> libResFolders, String customJavaPackage) throws AbortBuildException {
        // We actually need to delete the manifest.java as it may become empty and
        // in this case aapt doesn't generate an empty one, but instead doesn't
        // touch it.
        IFile manifestJavaFile = packageFolder.getFile(AndroidConstants.FN_MANIFEST_CLASS);
        manifestJavaFile.getLocation().toFile().delete();

        // launch aapt: create the command line
        ArrayList<String> array = new ArrayList<String>();
        array.add(projectTarget.getPath(IAndroidTarget.AAPT));
        array.add("package"); //$NON-NLS-1$
        array.add("-m"); //$NON-NLS-1$
        if (AdtPrefs.getPrefs().getBuildVerbosity() == BuildVerbosity.VERBOSE) {
            array.add("-v"); //$NON-NLS-1$
        }

        if (libResFolders.size() > 0) {
            array.add("--auto-add-overlay"); //$NON-NLS-1$
        }

        if (customJavaPackage != null) {
            array.add("--custom-package"); //$NON-NLS-1$
            array.add(customJavaPackage);
        }

        array.add("-J"); //$NON-NLS-1$
        array.add(osOutputPath);
        array.add("-M"); //$NON-NLS-1$
        array.add(osManifestPath);
        array.add("-S"); //$NON-NLS-1$
        array.add(osResPath);
        for (IFolder libResFolder : libResFolders) {
            array.add("-S"); //$NON-NLS-1$
            array.add(libResFolder.getLocation().toOSString());
        }

        array.add("-I"); //$NON-NLS-1$
        array.add(projectTarget.getPath(IAndroidTarget.ANDROID_JAR));

        if (AdtPrefs.getPrefs().getBuildVerbosity() == BuildVerbosity.VERBOSE) {
            StringBuilder sb = new StringBuilder();
            for (String c : array) {
                sb.append(c);
                sb.append(' ');
            }
            String cmd_line = sb.toString();
            AdtPlugin.printToConsole(project, cmd_line);
        }

        // launch
        int execError = 1;
        try {
            // launch the command line process
            Process process = Runtime.getRuntime().exec(
                    array.toArray(new String[array.size()]));

            // list to store each line of stderr
            ArrayList<String> results = new ArrayList<String>();

            // get the output and return code from the process
            execError = grabProcessOutput(process, results);

            // attempt to parse the error output
            boolean parsingError = AaptParser.parseOutput(results, project);

            // if we couldn't parse the output we display it in the console.
            if (parsingError) {
                if (execError != 0) {
                    AdtPlugin.printErrorToConsole(project, results.toArray());
                } else {
                    AdtPlugin.printBuildToConsole(BuildVerbosity.NORMAL,
                            project, results.toArray());
                }
            }

            if (execError != 0) {
                // if the exec failed, and we couldn't parse the error output
                // (and therefore not all files that should have been marked,
                // were marked), we put a generic marker on the project and abort.
                if (parsingError) {
                    markProject(AndroidConstants.MARKER_ADT,
                            Messages.Unparsed_AAPT_Errors, IMarker.SEVERITY_ERROR);
                }

                AdtPlugin.printBuildToConsole(BuildVerbosity.VERBOSE, project,
                        Messages.AAPT_Error);

                // abort if exec failed.
                throw new AbortBuildException();
            }
        } catch (IOException e1) {
            // something happen while executing the process,
            // mark the project and exit
            String msg = String.format(Messages.AAPT_Exec_Error, array.get(0));
            markProject(AndroidConstants.MARKER_ADT, msg, IMarker.SEVERITY_ERROR);

            // This interrupts the build.
            throw new AbortBuildException();
        } catch (InterruptedException e) {
            // we got interrupted waiting for the process to end...
            // mark the project and exit
            String msg = String.format(Messages.AAPT_Exec_Error, array.get(0));
            markProject(AndroidConstants.MARKER_ADT, msg, IMarker.SEVERITY_ERROR);

            // This interrupts the build.
            throw new AbortBuildException();
        }

        // if the return code was OK, we refresh the folder that
        // contains R.java to force a java recompile.
        if (execError == 0) {
            // build has been done. reset the state of the builder
            mMustCompileResources = false;

            // and store it
            saveProjectBooleanProperty(PROPERTY_COMPILE_RESOURCES,
                    mMustCompileResources);
        }
    }

    /**
     * Creates a relative {@link IPath} from a java package.
     * @param javaPackageName the java package.
     */
    private IPath getJavaPackagePath(String javaPackageName) {
        // convert the java package into path
        String[] segments = javaPackageName.split(AndroidConstants.RE_DOT);

        StringBuilder path = new StringBuilder();
        for (String s : segments) {
           path.append(AndroidConstants.WS_SEP_CHAR);
           path.append(s);
        }

        return new Path(path.toString());
    }

    /**
     * Returns an {@link IFolder} (located inside the 'gen' source folder), that matches the
     * package defined in the manifest. This {@link IFolder} may not actually exist
     * (aapt will create it anyway).
     * @return the {@link IFolder} that will contain the R class or null if
     * the folder was not found.
     * @throws CoreException
     */
    private IFolder getGenManifestPackageFolder() throws CoreException {
        // get the path for the package
        IPath packagePath = getJavaPackagePath(mManifestPackage);

        // get a folder for this path under the 'gen' source folder, and return it.
        // This IFolder may not reference an actual existing folder.
        return mGenFolder.getFolder(packagePath);
    }

    /**
     * Returns an {@link IFolder} (located inside the 'gen' source folder), that matches the
     * given package. This {@link IFolder} may not actually exist
     * (aapt will create it anyway).
     * @param javaPackage the java package that must match the folder.
     * @return the {@link IFolder} that will contain the R class or null if
     * the folder was not found.
     * @throws CoreException
     */
    private IFolder getGenManifestPackageFolder(String javaPackage) throws CoreException {
        // get the path for the package
        IPath packagePath = getJavaPackagePath(javaPackage);

        // get a folder for this path under the 'gen' source folder, and return it.
        // This IFolder may not reference an actual existing folder.
        return mGenFolder.getFolder(packagePath);
    }
}
