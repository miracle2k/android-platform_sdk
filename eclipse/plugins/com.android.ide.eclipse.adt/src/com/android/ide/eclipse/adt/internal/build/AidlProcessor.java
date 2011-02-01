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

import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.AndroidConstants;
import com.android.ide.eclipse.adt.internal.build.builders.BaseBuilder;
import com.android.ide.eclipse.adt.internal.preferences.AdtPrefs;
import com.android.ide.eclipse.adt.internal.preferences.AdtPrefs.BuildVerbosity;
import com.android.ide.eclipse.adt.internal.project.BaseProjectHelper;
import com.android.sdklib.IAndroidTarget;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jdt.core.IJavaProject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A {@link SourceProcessor} for aidl files.
 *
 */
public class AidlProcessor extends SourceProcessor {

    private static final String PROPERTY_COMPILE_AIDL = "compileAidl"; //$NON-NLS-1$

    /**
     * Single line aidl error<br>
     * {@code <path>:<line>: <error>}<br>
     * or<br>
     * {@code <path>:<line> <error>}<br>
     */
    private static Pattern sAidlPattern1 = Pattern.compile("^(.+?):(\\d+):?\\s(.+)$"); //$NON-NLS-1$


    private enum AidlType {
        UNKNOWN, INTERFACE, PARCELABLE;
    }

    // See comment in #getAidlType()
//  private final static Pattern sParcelablePattern = Pattern.compile(
//          "^\\s*parcelable\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s*;\\s*$");
//
//  private final static Pattern sInterfacePattern = Pattern.compile(
//          "^\\s*interface\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s*(?:\\{.*)?$");


    public AidlProcessor(IJavaProject javaProject, IFolder genFolder) {
        super(javaProject, genFolder);
    }

    @Override
    protected String getExtension() {
        return AndroidConstants.EXT_AIDL;
    }

    @Override
    protected String getSavePropertyName() {
        return PROPERTY_COMPILE_AIDL;
    }

    @Override
    protected int getCompilationType() {
        return COMPILE_STATUS_CODE;
    }

    @Override
    protected void doCompileFiles(List<IFile> sources, BaseBuilder builder,
            IProject project, IAndroidTarget projectTarget,
            List<IPath> sourceFolders, List<IFile> notCompiledOut, IProgressMonitor monitor)
            throws CoreException {
        // create the command line
        String[] command = new String[4 + sourceFolders.size()];
        int index = 0;
        command[index++] = projectTarget.getPath(IAndroidTarget.AIDL);
        command[index++] = "-p" + projectTarget.getPath(IAndroidTarget.ANDROID_AIDL);

        // since the path are relative to the workspace and not the project itself, we need
        // the workspace root.
        IWorkspaceRoot wsRoot = ResourcesPlugin.getWorkspace().getRoot();
        for (IPath p : sourceFolders) {
            IFolder f = wsRoot.getFolder(p);
            command[index++] = "-I" + f.getLocation().toOSString(); //$NON-NLS-1$
        }

        boolean verbose = AdtPrefs.getPrefs().getBuildVerbosity() == BuildVerbosity.VERBOSE;

        // loop until we've compile them all
        for (IFile sourceFile : sources) {
            if (verbose) {
                String name = sourceFile.getName();
                IPath sourceFolderPath = getSourceFolderFor(sourceFile);
                if (sourceFolderPath != null) {
                    // make a path to the source file relative to the source folder.
                    IPath relative = sourceFile.getFullPath().makeRelativeTo(sourceFolderPath);
                    name = relative.toString();
                }
                AdtPlugin.printToConsole(project, "AIDL: " + name);
            }

            // Remove the AIDL error markers from the aidl file
            builder.removeMarkersFromFile(sourceFile, AndroidConstants.MARKER_AIDL);

            // get the path of the source file.
            IPath sourcePath = sourceFile.getLocation();
            String osSourcePath = sourcePath.toOSString();

            // look if we already know the output
            SourceFileData data = getFileData(sourceFile);
            if (data == null) {
                IFile javaFile = getAidlOutputFile(sourceFile, true /*createFolders*/, monitor);
                data = new SourceFileData(sourceFile, javaFile);
                addData(data);
            }

            // finish to set the command line.
            command[index] = osSourcePath;
            command[index + 1] = data.getOutput().getLocation().toOSString();

            // launch the process
            if (execAidl(builder, project, command, sourceFile, verbose) == false) {
                // aidl failed. File should be marked. We add the file to the list
                // of file that will need compilation again.
                notCompiledOut.add(sourceFile);
            }
        }
    }

    @Override
    protected void loadOutputAndDependencies() {
        IProgressMonitor monitor = new NullProgressMonitor();
        Collection<SourceFileData> dataList = getAllFileData();
        for (SourceFileData data : dataList) {
            try {
                IFile javaFile = getAidlOutputFile(data.getSourceFile(),
                        false /*createFolders*/, monitor);
                data.setOutputFile(javaFile);
            } catch (CoreException e) {
                // ignore, we're not asking to create the folder so this won't happen anyway.
            }

        }
    }


    /**
     * Execute the aidl command line, parse the output, and mark the aidl file
     * with any reported errors.
     * @param command the String array containing the command line to execute.
     * @param file The IFile object representing the aidl file being
     *      compiled.
     * @param verbose the build verbosity
     * @return false if the exec failed, and build needs to be aborted.
     */
    private boolean execAidl(BaseBuilder builder, IProject project, String[] command, IFile file,
            boolean verbose) {
        // do the exec
        try {
            if (verbose) {
                StringBuilder sb = new StringBuilder();
                for (String c : command) {
                    sb.append(c);
                    sb.append(' ');
                }
                String cmd_line = sb.toString();
                AdtPlugin.printToConsole(project, cmd_line);
            }

            Process p = Runtime.getRuntime().exec(command);

            // list to store each line of stderr
            ArrayList<String> results = new ArrayList<String>();

            // get the output and return code from the process
            int result = BuildHelper.grabProcessOutput(project, p, results);

            // attempt to parse the error output
            boolean error = parseAidlOutput(results, file);

            // If the process failed and we couldn't parse the output
            // we print a message, mark the project and exit
            if (result != 0) {

                if (error || verbose) {
                    // display the message in the console.
                    if (error) {
                        AdtPlugin.printErrorToConsole(project, results.toArray());

                        // mark the project
                        BaseProjectHelper.markResource(project, AndroidConstants.MARKER_ADT,
                                Messages.Unparsed_AIDL_Errors, IMarker.SEVERITY_ERROR);
                    } else {
                        AdtPlugin.printToConsole(project, results.toArray());
                    }
                }
                return false;
            }
        } catch (IOException e) {
            // mark the project and exit
            String msg = String.format(Messages.AIDL_Exec_Error, command[0]);
            BaseProjectHelper.markResource(project, AndroidConstants.MARKER_ADT, msg,
                    IMarker.SEVERITY_ERROR);
            return false;
        } catch (InterruptedException e) {
            // mark the project and exit
            String msg = String.format(Messages.AIDL_Exec_Error, command[0]);
            BaseProjectHelper.markResource(project, AndroidConstants.MARKER_ADT, msg,
                    IMarker.SEVERITY_ERROR);
            return false;
        }

        return true;
    }

    /**
     * Parse the output of aidl and mark the file with any errors.
     * @param lines The output to parse.
     * @param file The file to mark with error.
     * @return true if the parsing failed, false if success.
     */
    private boolean parseAidlOutput(ArrayList<String> lines, IFile file) {
        // nothing to parse? just return false;
        if (lines.size() == 0) {
            return false;
        }

        Matcher m;

        for (int i = 0; i < lines.size(); i++) {
            String p = lines.get(i);

            m = sAidlPattern1.matcher(p);
            if (m.matches()) {
                // we can ignore group 1 which is the location since we already
                // have a IFile object representing the aidl file.
                String lineStr = m.group(2);
                String msg = m.group(3);

                // get the line number
                int line = 0;
                try {
                    line = Integer.parseInt(lineStr);
                } catch (NumberFormatException e) {
                    // looks like the string we extracted wasn't a valid
                    // file number. Parsing failed and we return true
                    return true;
                }

                // mark the file
                BaseProjectHelper.markResource(file, AndroidConstants.MARKER_AIDL, msg, line,
                        IMarker.SEVERITY_ERROR);

                // success, go to the next line
                continue;
            }

            // invalid line format, flag as error, and bail
            return true;
        }

        return false;
    }

    /**
     * Returns the {@link IFile} handle to the destination file for a given aidl source file
     * ({@link AidlData}).
     * @param aidlData the data for the aidl source file.
     * @param createFolders whether or not the parent folder of the destination should be created
     * if it does not exist.
     * @param monitor the progress monitor
     * @return the handle to the destination file.
     * @throws CoreException
     */
    private IFile getAidlOutputFile(IFile sourceFile, boolean createFolders,
            IProgressMonitor monitor) throws CoreException {

        IPath sourceFolderPath = getSourceFolderFor(sourceFile);

        // this really shouldn't happen since the sourceFile must be in a source folder
        // since it comes from the delta visitor
        if (sourceFolderPath != null) {
            // make a path to the source file relative to the source folder.
            IPath relative = sourceFile.getFullPath().makeRelativeTo(sourceFolderPath);
            // remove the file name. This is now the destination folder.
            relative = relative.removeLastSegments(1);

            // get an IFolder for this path.
            IFolder destinationFolder = getGenFolder().getFolder(relative);

            // create it if needed.
            if (destinationFolder.exists() == false && createFolders) {
                createFolder(destinationFolder, monitor);
            }

            // Build the Java file name from the aidl name.
            String javaName = sourceFile.getName().replaceAll(
                    AndroidConstants.RE_AIDL_EXT, AndroidConstants.DOT_JAVA);

            // get the resource for the java file.
            IFile javaFile = destinationFolder.getFile(javaName);
            return javaFile;
        }

        return null;
    }

    /**
     * Creates the destination folder. Because
     * {@link IFolder#create(boolean, boolean, IProgressMonitor)} only works if the parent folder
     * already exists, this goes and ensure that all the parent folders actually exist, or it
     * creates them as well.
     * @param destinationFolder The folder to create
     * @param monitor the {@link IProgressMonitor},
     * @throws CoreException
     */
    private void createFolder(IFolder destinationFolder, IProgressMonitor monitor)
            throws CoreException {

        // check the parent exist and create if necessary.
        IContainer parent = destinationFolder.getParent();
        if (parent.getType() == IResource.FOLDER && parent.exists() == false) {
            createFolder((IFolder)parent, monitor);
        }

        // create the folder.
        destinationFolder.create(true /*force*/, true /*local*/,
                new SubProgressMonitor(monitor, 10));
    }

    /**
     * Returns the type of the aidl file. Aidl files can either declare interfaces, or declare
     * parcelables. This method will attempt to parse the file and return the type. If the type
     * cannot be determined, then it will return {@link AidlType#UNKNOWN}.
     * @param file The aidl file
     * @return the type of the aidl.
     */
    private static AidlType getAidlType(IFile file) {
        // At this time, parsing isn't available, so we return UNKNOWN. This will force
        // a recompilation of all aidl file as soon as one is changed.
        return AidlType.UNKNOWN;

        // TODO: properly parse aidl file to determine type and generate dependency graphs.
//
//        String className = file.getName().substring(0,
//                file.getName().length() - AndroidConstants.DOT_AIDL.length());
//
//        InputStream input = file.getContents(true /* force*/);
//        try {
//            BufferedReader reader = new BufferedReader(new InputStreateader(input));
//            String line;
//            while ((line = reader.readLine()) != null) {
//                if (line.length() == 0) {
//                    continue;
//                }
//
//                Matcher m = sParcelablePattern.matcher(line);
//                if (m.matches() && m.group(1).equals(className)) {
//                    return AidlType.PARCELABLE;
//                }
//
//                m = sInterfacePattern.matcher(line);
//                if (m.matches() && m.group(1).equals(className)) {
//                    return AidlType.INTERFACE;
//                }
//            }
//        } catch (IOException e) {
//            throw new CoreException(new Status(IStatus.ERROR, AdtPlugin.PLUGIN_ID,
//                    "Error parsing aidl file", e));
//        } finally {
//            try {
//                input.close();
//            } catch (IOException e) {
//                throw new CoreException(new Status(IStatus.ERROR, AdtPlugin.PLUGIN_ID,
//                        "Error parsing aidl file", e));
//            }
//        }
//
//        return AidlType.UNKNOWN;
    }
}
