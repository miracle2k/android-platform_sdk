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

import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.AndroidConstants;
import com.android.ide.eclipse.adt.internal.preferences.AdtPrefs;
import com.android.ide.eclipse.adt.internal.preferences.AdtPrefs.BuildVerbosity;
import com.android.ide.eclipse.adt.internal.project.BaseProjectHelper;
import com.android.ide.eclipse.adt.internal.sdk.AndroidTargetData;
import com.android.ide.eclipse.adt.internal.sdk.DexWrapper;
import com.android.ide.eclipse.adt.internal.sdk.Sdk;
import com.android.prefs.AndroidLocation.AndroidLocationException;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkConstants;
import com.android.sdklib.build.ApkBuilder;
import com.android.sdklib.build.ApkCreationException;
import com.android.sdklib.build.DuplicateFileException;
import com.android.sdklib.build.SealedApkException;
import com.android.sdklib.build.ApkBuilder.JarStatus;
import com.android.sdklib.internal.build.DebugKeyProvider;
import com.android.sdklib.internal.build.SignedJarBuilder;
import com.android.sdklib.internal.build.DebugKeyProvider.KeytoolException;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.preference.IPreferenceStore;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Helper with methods for the last 3 steps of the generation of an APK.
 *
 * {@link #packageResources(IFile, IProject[], String, int, String, String)} packages the
 * application resources using aapt into a zip file that is ready to be integrated into the apk.
 *
 * {@link #executeDx(IJavaProject, String, String, IJavaProject[])} will convert the Java byte
 * code into the Dalvik bytecode.
 *
 * {@link #finalPackage(String, String, String, boolean, IJavaProject, IProject[], IJavaProject[], String, boolean)}
 * will make the apk from all the previous components.
 *
 */
public class PostCompilerHelper {

    private final IProject mProject;
    private final PrintStream mOutStream;
    private final PrintStream mErrStream;

    public PostCompilerHelper(IProject project, PrintStream outStream, PrintStream errStream) {
        mProject = project;
        mOutStream = outStream;
        mErrStream = errStream;
    }

    /**
     * Packages the resources of the projet into a .ap_ file.
     * @param manifestFile the manifest of the project.
     * @param libProjects the list of library projects that this project depends on.
     * @param resFilter an optional resource filter to be used with the -c option of aapt. If null
     * no filters are used.
     * @param versionCode an optional versionCode to be inserted in the manifest during packaging.
     * If the value is <=0, no values are inserted.
     * @param outputFolder where to write the resource ap_ file.
     * @param outputFilename the name of the resource ap_ file.
     * @return true if success.
     */
    public boolean packageResources(IFile manifestFile, IProject[] libProjects, String resFilter,
            int versionCode, String outputFolder, String outputFilename) {
        // need to figure out some path before we can execute aapt;

        // get the resource folder
        IFolder resFolder = mProject.getFolder(AndroidConstants.WS_RESOURCES);

        // and the assets folder
        IFolder assetsFolder = mProject.getFolder(AndroidConstants.WS_ASSETS);

        // we need to make sure this one exists.
        if (assetsFolder.exists() == false) {
            assetsFolder = null;
        }

        IPath resLocation = resFolder.getLocation();
        IPath manifestLocation = manifestFile.getLocation();

        if (resLocation != null && manifestLocation != null) {
            // list of res folder (main project + maybe libraries)
            ArrayList<String> osResPaths = new ArrayList<String>();
            osResPaths.add(resLocation.toOSString()); //main project

            // libraries?
            if (libProjects != null) {
                for (IProject lib : libProjects) {
                    IFolder libResFolder = lib.getFolder(SdkConstants.FD_RES);
                    if (libResFolder.exists()) {
                        osResPaths.add(libResFolder.getLocation().toOSString());
                    }
                }
            }

            String osManifestPath = manifestLocation.toOSString();

            String osAssetsPath = null;
            if (assetsFolder != null) {
                osAssetsPath = assetsFolder.getLocation().toOSString();
            }

            // build the default resource package
            if (executeAapt(osManifestPath, osResPaths, osAssetsPath,
                    outputFolder + File.separator + outputFilename, resFilter,
                    versionCode) == false) {
                // aapt failed. Whatever files that needed to be marked
                // have already been marked. We just return.
                return false;
            }
        }

        return true;
    }

    /**
     * Makes the final package. Package the dex files, the temporary resource file into the final
     * package file.
     * @param intermediateApk The path to the temporary resource file.
     * @param dex The path to the dex file.
     * @param output The path to the final package file to create.
     * @param debugSign whether the apk must be signed with the debug key.
     * @param javaProject the java project being compiled
     * @param libProjects an optional list of library projects (can be null)
     * @param referencedJavaProjects referenced projects.
     * @param abiFilter an optional filter. If not null, then only the matching ABI is included in
     * the final archive
     * @param debuggable whether the project manifest has debuggable==true. If true, any gdbserver
     * executables will be packaged with the native libraries.
     * @return true if success, false otherwise.
     */
    public boolean finalPackage(String intermediateApk, String dex, String output,
            boolean debugSign, final IJavaProject javaProject, IProject[] libProjects,
            IJavaProject[] referencedJavaProjects, String abiFilter, boolean debuggable) {

        IProject project = javaProject.getProject();

        String keystoreOsPath = null;
        if (debugSign) {
            IPreferenceStore store = AdtPlugin.getDefault().getPreferenceStore();
            keystoreOsPath = store.getString(AdtPrefs.PREFS_CUSTOM_DEBUG_KEYSTORE);
            if (keystoreOsPath == null || new File(keystoreOsPath).isFile() == false) {
                try {
                    keystoreOsPath = DebugKeyProvider.getDefaultKeyStoreOsPath();
                    AdtPlugin.printBuildToConsole(BuildVerbosity.VERBOSE, mProject,
                            Messages.ApkBuilder_Using_Default_Key);
                } catch (KeytoolException e) {
                    String eMessage = e.getMessage();

                    // mark the project with the standard message
                    String msg = String.format(Messages.Final_Archive_Error_s, eMessage);
                    BaseProjectHelper.markResource(mProject, AndroidConstants.MARKER_PACKAGING, msg,
                            IMarker.SEVERITY_ERROR);

                    // output more info in the console
                    AdtPlugin.printErrorToConsole(mProject,
                            msg,
                            String.format(Messages.ApkBuilder_JAVA_HOME_is_s, e.getJavaHome()),
                            Messages.ApkBuilder_Update_or_Execute_manually_s,
                            e.getCommandLine());

                    return false;
                } catch (AndroidLocationException e) {
                    String eMessage = e.getMessage();

                    // mark the project with the standard message
                    String msg = String.format(Messages.Final_Archive_Error_s, eMessage);
                    BaseProjectHelper.markResource(mProject, AndroidConstants.MARKER_PACKAGING, msg,
                            IMarker.SEVERITY_ERROR);

                    return false;
                }
            } else {
                AdtPlugin.printBuildToConsole(BuildVerbosity.VERBOSE, mProject,
                        String.format(Messages.ApkBuilder_Using_s_To_Sign, keystoreOsPath));
            }
        }


        try {
            ApkBuilder apkBuilder = new ApkBuilder(output, intermediateApk, dex, keystoreOsPath,
                    AdtPrefs.getPrefs().getBuildVerbosity() == BuildVerbosity.VERBOSE ?
                            AdtPlugin.getOutPrintStream(project, null): null);
            apkBuilder.setDebugMode(debuggable);

            // Now we write the standard resources from the project and the referenced projects.
            writeStandardResources(apkBuilder, javaProject, referencedJavaProjects);

            // Now we write the standard resources from the external jars
            for (String libraryOsPath : getExternalJars()) {
                JarStatus status = apkBuilder.addResourcesFromJar(new File(libraryOsPath));

                // check if we found native libraries in the external library. This
                // constitutes an error or warning depending on if they are in lib/
                if (status.getNativeLibs().size() > 0) {
                    String libName = new File(libraryOsPath).getName();
                    String msg = String.format(
                            "Native libraries detected in '%1$s'. See console for more information.",
                            libName);

                    BaseProjectHelper.markResource(mProject, AndroidConstants.MARKER_PACKAGING,
                            msg,
                            status.hasNativeLibsConflicts() ||
                                    AdtPrefs.getPrefs().getBuildForceErrorOnNativeLibInJar() ?
                                    IMarker.SEVERITY_ERROR : IMarker.SEVERITY_WARNING);

                    ArrayList<String> consoleMsgs = new ArrayList<String>();
                    consoleMsgs.add(String.format(
                            "The library '%1$s' contains native libraries that will not run on the device.",
                            libName));
                    if (status.hasNativeLibsConflicts()) {
                        consoleMsgs.add("Additionally some of those libraries will interfer with the installation of the application because of their location in lib/");
                        consoleMsgs.add("lib/ is reserved for NDK libraries.");
                    }
                    consoleMsgs.add("The following libraries were found:");
                    for (String lib : status.getNativeLibs()) {
                        consoleMsgs.add(" - " + lib);
                    }
                    AdtPlugin.printErrorToConsole(mProject,
                            consoleMsgs.toArray());

                    return false;
                }
            }

            // now write the native libraries.
            // First look if the lib folder is there.
            IResource libFolder = mProject.findMember(SdkConstants.FD_NATIVE_LIBS);
            if (libFolder != null && libFolder.exists() &&
                    libFolder.getType() == IResource.FOLDER) {
                // get a File for the folder.
                apkBuilder.addNativeLibraries(libFolder.getLocation().toFile(), abiFilter);
            }

            // write the native libraries for the library projects.
            if (libProjects != null) {
                for (IProject lib : libProjects) {
                    libFolder = lib.findMember(SdkConstants.FD_NATIVE_LIBS);
                    if (libFolder != null && libFolder.exists() &&
                            libFolder.getType() == IResource.FOLDER) {
                        apkBuilder.addNativeLibraries(libFolder.getLocation().toFile(), abiFilter);
                    }
                }
            }

            // seal the APK.
            apkBuilder.sealApk();
            return true;
        } catch (CoreException e) {
            // mark project and return
            String msg = String.format(Messages.Final_Archive_Error_s, e.getMessage());
            AdtPlugin.printErrorToConsole(mProject, msg);
            BaseProjectHelper.markResource(mProject, AndroidConstants.MARKER_PACKAGING, msg,
                    IMarker.SEVERITY_ERROR);
        } catch (ApkCreationException e) {
            // mark project and return
            String msg = String.format(Messages.Final_Archive_Error_s, e.getMessage());
            AdtPlugin.printErrorToConsole(mProject, msg);
            BaseProjectHelper.markResource(mProject, AndroidConstants.MARKER_PACKAGING, msg,
                    IMarker.SEVERITY_ERROR);
        } catch (DuplicateFileException e) {
            String msg1 = String.format(
                    "Found duplicate file for APK: %1$s\nOrigin 1: %2$s\nOrigin 2: %3$s",
                    e.getArchivePath(), e.getFile1(), e.getFile2());
            String msg2 = String.format(Messages.Final_Archive_Error_s, msg1);
            AdtPlugin.printErrorToConsole(mProject, msg2);
            BaseProjectHelper.markResource(mProject, AndroidConstants.MARKER_PACKAGING, msg2,
                    IMarker.SEVERITY_ERROR);
        } catch (SealedApkException e) {
            // this won't happen as we control when the apk is sealed.
        } catch (Exception e) {
            // try to catch other exception to actually display an error. This will be useful
            // if we get an NPE or something so that we can at least notify the user that something
            // went wrong (otherwise the build appears to succeed but the zip archive is not closed
            // and therefore invalid.
            String msg = e.getMessage();
            if (msg == null) {
                msg = e.getClass().getCanonicalName();
            }

            msg = String.format("Unknown error: %1$s", msg);
            AdtPlugin.printErrorToConsole(mProject, msg);
            BaseProjectHelper.markResource(mProject, AndroidConstants.MARKER_PACKAGING, msg,
                    IMarker.SEVERITY_ERROR);
        }

        return false;
    }

    /**
     * Execute the Dx tool for dalvik code conversion.
     * @param javaProject The java project
     * @param osBinPath the path to the output folder of the project
     * @param osOutFilePath the path of the dex file to create.
     * @param referencedJavaProjects the list of referenced projects for this project.
     *
     * @throws CoreException
     */
    boolean executeDx(IJavaProject javaProject, String osBinPath, String osOutFilePath,
            IJavaProject[] referencedJavaProjects) throws CoreException {

        IAndroidTarget target = Sdk.getCurrent().getTarget(mProject);
        AndroidTargetData targetData = Sdk.getCurrent().getTargetData(target);
        if (targetData == null) {
            throw new CoreException(new Status(IStatus.ERROR, AdtPlugin.PLUGIN_ID,
                    Messages.ApkBuilder_UnableBuild_Dex_Not_loaded));
        }

        // get the dex wrapper
        DexWrapper wrapper = targetData.getDexWrapper();

        if (wrapper == null) {
            throw new CoreException(new Status(IStatus.ERROR, AdtPlugin.PLUGIN_ID,
                    Messages.ApkBuilder_UnableBuild_Dex_Not_loaded));
        }

        try {
            // get the list of libraries to include with the source code
            String[] libraries = getExternalJars();

            // get the list of referenced projects output to add
            String[] projectOutputs = getProjectOutputs(referencedJavaProjects);

            String[] fileNames = new String[1 + projectOutputs.length + libraries.length];

            // first this project output
            fileNames[0] = osBinPath;

            // then other project output
            System.arraycopy(projectOutputs, 0, fileNames, 1, projectOutputs.length);

            // then external jars.
            System.arraycopy(libraries, 0, fileNames, 1 + projectOutputs.length, libraries.length);

            int res = wrapper.run(osOutFilePath, fileNames,
                    AdtPrefs.getPrefs().getBuildVerbosity() == BuildVerbosity.VERBOSE,
                    mOutStream, mErrStream);

            if (res != 0) {
                // output error message and marker the project.
                String message = String.format(Messages.Dalvik_Error_d,
                        res);
                AdtPlugin.printErrorToConsole(mProject, message);
                BaseProjectHelper.markResource(mProject, AndroidConstants.MARKER_PACKAGING,
                        message, IMarker.SEVERITY_ERROR);
                return false;
            }
        } catch (Throwable ex) {
            String message = ex.getMessage();
            if (message == null) {
                message = ex.getClass().getCanonicalName();
            }
            message = String.format(Messages.Dalvik_Error_s, message);
            AdtPlugin.printErrorToConsole(mProject, message);
            BaseProjectHelper.markResource(mProject, AndroidConstants.MARKER_PACKAGING,
                    message, IMarker.SEVERITY_ERROR);
            if ((ex instanceof NoClassDefFoundError)
                    || (ex instanceof NoSuchMethodError)) {
                AdtPlugin.printErrorToConsole(mProject, Messages.Incompatible_VM_Warning,
                        Messages.Requires_1_5_Error);
            }
            return false;
        }

        return true;
    }


    /**
     * Executes aapt. If any error happen, files or the project will be marked.
     * @param osManifestPath The path to the manifest file
     * @param osResPath The path to the res folder
     * @param osAssetsPath The path to the assets folder. This can be null.
     * @param osOutFilePath The path to the temporary resource file to create.
     * @param configFilter The configuration filter for the resources to include
     * (used with -c option, for example "port,en,fr" to include portrait, English and French
     * resources.)
     * @param versionCode optional version code to insert in the manifest during packaging. If <=0
     * then no value is inserted
     * @return true if success, false otherwise.
     */
    private boolean executeAapt(String osManifestPath,
            List<String> osResPaths, String osAssetsPath, String osOutFilePath,
            String configFilter, int versionCode) {
        IAndroidTarget target = Sdk.getCurrent().getTarget(mProject);

        // Create the command line.
        ArrayList<String> commandArray = new ArrayList<String>();
        commandArray.add(target.getPath(IAndroidTarget.AAPT));
        commandArray.add("package"); //$NON-NLS-1$
        commandArray.add("-f");//$NON-NLS-1$
        if (AdtPrefs.getPrefs().getBuildVerbosity() == BuildVerbosity.VERBOSE) {
            commandArray.add("-v"); //$NON-NLS-1$
        }

        // if more than one res, this means there's a library (or more) and we need
        // to activate the auto-add-overlay
        if (osResPaths.size() > 1) {
            commandArray.add("--auto-add-overlay"); //$NON-NLS-1$
        }

        if (versionCode > 0) {
            commandArray.add("--version-code"); //$NON-NLS-1$
            commandArray.add(Integer.toString(versionCode));
        }

        if (configFilter != null) {
            commandArray.add("-c"); //$NON-NLS-1$
            commandArray.add(configFilter);
        }

        commandArray.add("-M"); //$NON-NLS-1$
        commandArray.add(osManifestPath);

        for (String path : osResPaths) {
            commandArray.add("-S"); //$NON-NLS-1$
            commandArray.add(path);
        }

        if (osAssetsPath != null) {
            commandArray.add("-A"); //$NON-NLS-1$
            commandArray.add(osAssetsPath);
        }

        commandArray.add("-I"); //$NON-NLS-1$
        commandArray.add(target.getPath(IAndroidTarget.ANDROID_JAR));

        commandArray.add("-F"); //$NON-NLS-1$
        commandArray.add(osOutFilePath);

        String command[] = commandArray.toArray(
                new String[commandArray.size()]);

        if (AdtPrefs.getPrefs().getBuildVerbosity() == BuildVerbosity.VERBOSE) {
            StringBuilder sb = new StringBuilder();
            for (String c : command) {
                sb.append(c);
                sb.append(' ');
            }
            AdtPlugin.printToConsole(mProject, sb.toString());
        }

        // launch
        int execError = 1;
        try {
            // launch the command line process
            Process process = Runtime.getRuntime().exec(command);

            // list to store each line of stderr
            ArrayList<String> results = new ArrayList<String>();

            // get the output and return code from the process
            execError = BaseBuilder.grabProcessOutput(mProject, process, results);

            // attempt to parse the error output
            boolean parsingError = AaptParser.parseOutput(results, mProject);

            // if we couldn't parse the output we display it in the console.
            if (parsingError) {
                if (execError != 0) {
                    AdtPlugin.printErrorToConsole(mProject, results.toArray());
                } else {
                    AdtPlugin.printBuildToConsole(BuildVerbosity.ALWAYS, mProject,
                            results.toArray());
                }
            }

            // We need to abort if the exec failed.
            if (execError != 0) {
                // if the exec failed, and we couldn't parse the error output (and therefore
                // not all files that should have been marked, were marked), we put a generic
                // marker on the project and abort.
                if (parsingError) {
                    BaseProjectHelper.markResource(mProject, AndroidConstants.MARKER_PACKAGING,
                            Messages.Unparsed_AAPT_Errors,
                            IMarker.SEVERITY_ERROR);
                }

                // abort if exec failed.
                return false;
            }
        } catch (IOException e1) {
            String msg = String.format(Messages.AAPT_Exec_Error, command[0]);
            BaseProjectHelper.markResource(mProject, AndroidConstants.MARKER_PACKAGING, msg,
                    IMarker.SEVERITY_ERROR);
            return false;
        } catch (InterruptedException e) {
            String msg = String.format(Messages.AAPT_Exec_Error, command[0]);
            BaseProjectHelper.markResource(mProject, AndroidConstants.MARKER_PACKAGING, msg,
                    IMarker.SEVERITY_ERROR);
            return false;
        }

        return true;
    }


    /**
     * Writes the standard resources of a project and its referenced projects
     * into a {@link SignedJarBuilder}.
     * Standard resources are non java/aidl files placed in the java package folders.
     * @param apkBuilder the {@link ApkBuilder}.
     * @param javaProject the javaProject object.
     * @param referencedJavaProjects the java projects that this project references.
     * @throws ApkCreationException if an error occurred
     * @throws SealedApkException if the APK is already sealed.
     * @throws DuplicateFileException if a file conflicts with another already added to the APK
     *                                   at the same location inside the APK archive.
     * @throws CoreException
     */
    private void writeStandardResources(ApkBuilder apkBuilder, IJavaProject javaProject,
            IJavaProject[] referencedJavaProjects)
            throws DuplicateFileException, ApkCreationException, SealedApkException,
            CoreException  {
        IWorkspace ws = ResourcesPlugin.getWorkspace();
        IWorkspaceRoot wsRoot = ws.getRoot();

        // create a list of path already put into the archive, in order to detect conflict
        ArrayList<String> list = new ArrayList<String>();

        writeStandardProjectResources(apkBuilder, javaProject, wsRoot, list);

        for (IJavaProject referencedJavaProject : referencedJavaProjects) {
            // only include output from non android referenced project
            // (This is to handle the case of reference Android projects in the context of
            // instrumentation projects that need to reference the projects to be tested).
            if (referencedJavaProject.getProject().hasNature(
                    AndroidConstants.NATURE_DEFAULT) == false) {
                writeStandardProjectResources(apkBuilder, referencedJavaProject, wsRoot, list);
            }
        }
    }

    /**
     * Writes the standard resources of a {@link IJavaProject} into a {@link SignedJarBuilder}.
     * Standard resources are non java/aidl files placed in the java package folders.
     * @param jarBuilder the {@link ApkBuilder}.
     * @param javaProject the javaProject object.
     * @param wsRoot the {@link IWorkspaceRoot}.
     * @param list a list of files already added to the archive, to detect conflicts.
     * @throws ApkCreationException if an error occurred
     * @throws SealedApkException if the APK is already sealed.
     * @throws DuplicateFileException if a file conflicts with another already added to the APK
     *                                   at the same location inside the APK archive.
     */
    private void writeStandardProjectResources(ApkBuilder apkBuilder,
            IJavaProject javaProject, IWorkspaceRoot wsRoot, ArrayList<String> list)
            throws DuplicateFileException, ApkCreationException, SealedApkException {
        // get the source pathes
        ArrayList<IPath> sourceFolders = BaseProjectHelper.getSourceClasspaths(javaProject);

        // loop on them and then recursively go through the content looking for matching files.
        for (IPath sourcePath : sourceFolders) {
            IResource sourceResource = wsRoot.findMember(sourcePath);
            if (sourceResource != null && sourceResource.getType() == IResource.FOLDER) {
                // get a File from the IResource
                apkBuilder.addSourceFolder(sourceResource.getLocation().toFile());
            }
        }
    }

    /**
     * Returns an array of external jar files used by the project.
     * @return an array of OS-specific absolute file paths
     */
    private final String[] getExternalJars() {
        // get a java project from it
        IJavaProject javaProject = JavaCore.create(mProject);

        IWorkspaceRoot wsRoot = ResourcesPlugin.getWorkspace().getRoot();

        ArrayList<String> oslibraryList = new ArrayList<String>();
        IClasspathEntry[] classpaths = javaProject.readRawClasspath();
        if (classpaths != null) {
            for (IClasspathEntry e : classpaths) {
                if (e.getEntryKind() == IClasspathEntry.CPE_LIBRARY ||
                        e.getEntryKind() == IClasspathEntry.CPE_VARIABLE) {
                    // if this is a classpath variable reference, we resolve it.
                    if (e.getEntryKind() == IClasspathEntry.CPE_VARIABLE) {
                        e = JavaCore.getResolvedClasspathEntry(e);
                    }

                    // get the IPath
                    IPath path = e.getPath();

                    // check the name ends with .jar
                    if (AndroidConstants.EXT_JAR.equalsIgnoreCase(path.getFileExtension())) {
                        IResource resource = wsRoot.findMember(path);
                        if (resource != null && resource.exists() &&
                                resource.getType() == IResource.FILE) {
                            oslibraryList.add(resource.getLocation().toOSString());
                        } else {
                            // if the jar path doesn't match a workspace resource,
                            // then we get an OSString and check if this links to a valid file.
                            String osFullPath = path.toOSString();

                            File f = new File(osFullPath);
                            if (f.exists()) {
                                oslibraryList.add(osFullPath);
                            } else {
                                String message = String.format( Messages.Couldnt_Locate_s_Error,
                                        path);
                                AdtPlugin.printBuildToConsole(BuildVerbosity.VERBOSE,
                                        mProject, message);

                                // Also put a warning marker on the project
                                BaseProjectHelper.markResource(mProject,
                                        AndroidConstants.MARKER_PACKAGING, message,
                                        IMarker.SEVERITY_WARNING);
                            }
                        }
                    }
                }
            }
        }

        return oslibraryList.toArray(new String[oslibraryList.size()]);
    }

    /**
     * Returns the list of the output folders for the specified {@link IJavaProject} objects, if
     * they are Android projects.
     *
     * @param referencedJavaProjects the java projects.
     * @return an array, always. Can be empty.
     * @throws CoreException
     */
    private String[] getProjectOutputs(IJavaProject[] referencedJavaProjects) throws CoreException {
        ArrayList<String> list = new ArrayList<String>();

        IWorkspace ws = ResourcesPlugin.getWorkspace();
        IWorkspaceRoot wsRoot = ws.getRoot();

        for (IJavaProject javaProject : referencedJavaProjects) {
            // only include output from non android referenced project
            // (This is to handle the case of reference Android projects in the context of
            // instrumentation projects that need to reference the projects to be tested).
            if (javaProject.getProject().hasNature(AndroidConstants.NATURE_DEFAULT) == false) {
                // get the output folder
                IPath path = null;
                try {
                    path = javaProject.getOutputLocation();
                } catch (JavaModelException e) {
                    continue;
                }

                IResource outputResource = wsRoot.findMember(path);
                if (outputResource != null && outputResource.getType() == IResource.FOLDER) {
                    String outputOsPath = outputResource.getLocation().toOSString();

                    list.add(outputOsPath);
                }
            }
        }

        return list.toArray(new String[list.size()]);
    }

    /**
     * Checks a {@link IFile} to make sure it should be packaged as standard resources.
     * @param file the IFile representing the file.
     * @return true if the file should be packaged as standard java resources.
     */
    static boolean checkFileForPackaging(IFile file) {
        String name = file.getName();

        String ext = file.getFileExtension();
        return ApkBuilder.checkFileForPackaging(name, ext);
    }

    /**
     * Checks whether an {@link IFolder} and its content is valid for packaging into the .apk as
     * standard Java resource.
     * @param folder the {@link IFolder} to check.
     */
    static boolean checkFolderForPackaging(IFolder folder) {
        String name = folder.getName();
        return ApkBuilder.checkFolderForPackaging(name);
    }

    /**
     * Returns an array of {@link IJavaProject} matching the provided {@link IProject} objects.
     * @param projects the IProject objects.
     * @return an array, always. Can be empty.
     * @throws CoreException
     */
    public static IJavaProject[] getJavaProjects(IProject[] projects) throws CoreException {
        ArrayList<IJavaProject> list = new ArrayList<IJavaProject>();

        for (IProject p : projects) {
            if (p.isOpen() && p.hasNature(JavaCore.NATURE_ID)) {

                list.add(JavaCore.create(p));
            }
        }

        return list.toArray(new IJavaProject[list.size()]);
    }


}
