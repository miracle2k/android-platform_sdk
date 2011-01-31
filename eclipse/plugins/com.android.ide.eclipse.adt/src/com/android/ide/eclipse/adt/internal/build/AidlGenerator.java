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
import com.android.ide.eclipse.adt.internal.project.ProjectHelper;
import com.android.sdklib.IAndroidTarget;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jdt.core.IJavaProject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A {@link JavaGenerator} for aidl files.
 *
 */
public class AidlGenerator extends JavaGenerator {

    private static final String PROPERTY_COMPILE_AIDL = "compileAidl"; //$NON-NLS-1$

    /**
     * Single line aidl error<br>
     * "&lt;path&gt;:&lt;line&gt;: &lt;error&gt;"
     * or
     * "&lt;path&gt;:&lt;line&gt; &lt;error&gt;"
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


    /**
     * The custom delta visitor for aidl files. Based on the base JavaGenerator's delta visitor.
     */
    public static class AidlGeneratorDeltaVisitor extends JavaGeneratorDeltaVisitor {

        @Override
        public void handleChangedNonJavaFile(IFolder currentSourceFolder, IFile file, int kind) {
            // get the extension of the resource
            String ext = file.getFileExtension();
            if (AndroidConstants.EXT_AIDL.equalsIgnoreCase(ext)) {
                // first check whether it's a regular file or a parcelable.
                AidlType type = getAidlType(file);

                if (type == AidlType.INTERFACE) {
                    if (kind == IResourceDelta.REMOVED) {
                        // we'll have to remove the generated file.
                        addFileToRemove(currentSourceFolder, file);
                    } else if (getForceCompile() == false) {
                        // add the aidl file to the list of file to (re)compile
                        addFileToCompile(currentSourceFolder, file);
                    }
                } else {
                    // force recompilations of all Aidl Files.
                    setForceCompile();
                    //mAidlToCompile.clear();
                }
            }
        }

        @Override
        public boolean handleChangedGeneratedJavaFile(IFolder currentSourceFolder, IFile file,
                List<IPath> sourceFolders) {

            String fileName = file.getName();
            String[] segments = file.getFullPath().segments();

            // Look for the source aidl file in all the source folders that would match this given
            // java file.
            String aidlFileName = fileName.replaceAll(AndroidConstants.RE_JAVA_EXT,
                    AndroidConstants.DOT_AIDL);

            for (IPath sourceFolderPath : sourceFolders) {
                // do not search in the current source folder as it is the 'gen' folder.
                if (sourceFolderPath.equals(currentSourceFolder.getFullPath())) {
                    continue;
                }

                IFolder sourceFolder = getFolder(sourceFolderPath);
                if (sourceFolder != null) {
                    // go recursively, segment by segment.
                    // index starts at 2 (0 is project, 1 is 'gen'
                    IFile sourceFile = findFile(sourceFolder, segments, 2, aidlFileName);

                    if (sourceFile != null) {
                        // found the source. add it to the list of files to compile
                        addFileToCompile(currentSourceFolder, sourceFile);
                        return true;
                    }
                }
            }

            return false;
        }
    }

    public AidlGenerator(IJavaProject javaProject, IFolder genFolder) {
        super(new AidlGeneratorDeltaVisitor(), genFolder);

        IProject project = javaProject.getProject();

        boolean mustCompileAidl = ProjectHelper.loadBooleanProperty(project,
                PROPERTY_COMPILE_AIDL, true /*defaultValue*/);

        // if we stored that we have to compile some aidl, we build the list that will compile them
        // all
        if (mustCompileAidl) {
            ArrayList<IPath> sourceFolderPathList = BaseProjectHelper.getSourceClasspaths(
                    javaProject);

            buildCompilationList(project, sourceFolderPathList);
        }
    }

    @Override
    public boolean compileFiles(BaseBuilder builder, IProject project, IAndroidTarget projectTarget,
            List<IPath> sourceFolders, IProgressMonitor monitor) throws CoreException {
        List<SourceData> toCompile = getToCompile();
        List<SourceData> toRemove = getToRemove();

        if (toCompile.size() == 0 && toRemove.size() == 0) {
            return false;
        }

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

        // list of files that have failed compilation.
        List<SourceData> stillNeedCompilation = new ArrayList<SourceData>();

        // if an aidl file is being removed before we managed to compile it, it'll be in
        // both list. We *need* to remove it from the compile list or it'll never go away.
        for (SourceData aidlFile : toRemove) {
            int pos = toCompile.indexOf(aidlFile);
            if (pos != -1) {
                toCompile.remove(pos);
            }
        }

        // loop until we've compile them all
        for (SourceData aidlData : toCompile) {
            // Remove the AIDL error markers from the aidl file
            builder.removeMarkersFromFile(aidlData.sourceFile, AndroidConstants.MARKER_AIDL);

            // get the path of the source file.
            IPath sourcePath = aidlData.sourceFile.getLocation();
            String osSourcePath = sourcePath.toOSString();

            IFile javaFile = getGenDestinationFile(aidlData, true /*createFolders*/, monitor);

            // finish to set the command line.
            command[index] = osSourcePath;
            command[index + 1] = javaFile.getLocation().toOSString();

            // launch the process
            if (execAidl(builder, project, command, aidlData.sourceFile) == false) {
                // aidl failed. File should be marked. We add the file to the list
                // of file that will need compilation again.
                stillNeedCompilation.add(aidlData);

                // and we move on to the next one.
                continue;
            }
        }

        // change the list to only contains the file that have failed compilation
        toCompile.clear();
        toCompile.addAll(stillNeedCompilation);

        // Remove the java files created from aidl files that have been removed.
        for (SourceData aidlData : toRemove) {
            IFile javaFile = getGenDestinationFile(aidlData, false /*createFolders*/, monitor);
            if (javaFile.exists()) {
                // This confirms the java file was generated by the builder,
                // we can delete the aidlFile.
                javaFile.getLocation().toFile().delete();
            }
        }

        toRemove.clear();

        // store the build state. If there are any files that failed to compile, we will
        // force a full aidl compile on the next project open. (unless a full compilation succeed
        // before the project is closed/re-opened.)
        saveState(project);

        return true;
    }

    /**
     * Execute the aidl command line, parse the output, and mark the aidl file
     * with any reported errors.
     * @param command the String array containing the command line to execute.
     * @param file The IFile object representing the aidl file being
     *      compiled.
     * @return false if the exec failed, and build needs to be aborted.
     */
    private boolean execAidl(BaseBuilder builder, IProject project, String[] command, IFile file) {
        // do the exec
        try {
            if (AdtPrefs.getPrefs().getBuildVerbosity() == BuildVerbosity.VERBOSE) {
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
            if (result != 0 && error == true) {
                // display the message in the console.
                AdtPlugin.printErrorToConsole(project, results.toArray());

                // mark the project and exit
                BaseProjectHelper.markResource(project, AndroidConstants.MARKER_ADT,
                        Messages.Unparsed_AIDL_Errors, IMarker.SEVERITY_ERROR);
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

    @Override
    public void saveState(IProject project) {
        // TODO: Optimize by saving only the files that need compilation
        ProjectHelper.saveStringProperty(project, PROPERTY_COMPILE_AIDL,
                Boolean.toString(getToCompile().size() > 0));
    }


    /**
     * Goes through the build paths and fills the list of aidl files to compile
     * ({@link #mAidlToCompile}).
     * @param project The project.
     * @param sourceFolderPathList The list of source folder paths.
     * @param genFolder the gen folder
     */
    @Override
    protected void buildCompilationList(IProject project, List<IPath> sourceFolderPathList) {
        IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
        for (IPath sourceFolderPath : sourceFolderPathList) {
            IFolder sourceFolder = root.getFolder(sourceFolderPath);
            // we don't look in the 'gen' source folder as there will be no source in there.
            if (sourceFolder.exists() && sourceFolder.equals(getGenFolder()) == false) {
                scanFolderForAidl(sourceFolder, sourceFolder);
            }
        }
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
    private IFile getGenDestinationFile(SourceData aidlData, boolean createFolders,
            IProgressMonitor monitor) throws CoreException {
        // build the destination folder path.
        // Use the path of the source file, except for the path leading to its source folder,
        // and for the last segment which is the filename.
        int segmentToSourceFolderCount = aidlData.sourceFolder.getFullPath().segmentCount();
        IPath packagePath = aidlData.sourceFile.getFullPath().removeFirstSegments(
                segmentToSourceFolderCount).removeLastSegments(1);
        Path destinationPath = new Path(packagePath.toString());

        // get an IFolder for this path. It's relative to the 'gen' folder already
        IFolder destinationFolder = getGenFolder().getFolder(destinationPath);

        // create it if needed.
        if (destinationFolder.exists() == false && createFolders) {
            createFolder(destinationFolder, monitor);
        }

        // Build the Java file name from the aidl name.
        String javaName = aidlData.sourceFile.getName().replaceAll(AndroidConstants.RE_AIDL_EXT,
                AndroidConstants.DOT_JAVA);

        // get the resource for the java file.
        IFile javaFile = destinationFolder.getFile(javaName);
        return javaFile;
    }

    /**
     * Scans a folder and fills the list of aidl files to compile.
     * @param sourceFolder the root source folder.
     * @param folder The folder to scan.
     */
    private void scanFolderForAidl(IFolder sourceFolder, IFolder folder) {
        try {
            IResource[] members = folder.members();
            for (IResource r : members) {
                // get the type of the resource
               switch (r.getType()) {
                   case IResource.FILE:
                       // if this a file, check that the file actually exist
                       // and that it's an aidl file
                       if (r.exists() &&
                               AndroidConstants.EXT_AIDL.equalsIgnoreCase(r.getFileExtension())) {
                           addFileToCompile(sourceFolder, (IFile)r);
                       }
                       break;
                   case IResource.FOLDER:
                       // recursively go through children
                       scanFolderForAidl(sourceFolder, (IFolder)r);
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
