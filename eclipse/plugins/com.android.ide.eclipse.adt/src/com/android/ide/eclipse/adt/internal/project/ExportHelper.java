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

package com.android.ide.eclipse.adt.internal.project;

import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.AndroidConstants;
import com.android.ide.eclipse.adt.AndroidPrintStream;
import com.android.ide.eclipse.adt.internal.build.BuildHelper;
import com.android.ide.eclipse.adt.internal.sdk.ProjectState;
import com.android.ide.eclipse.adt.internal.sdk.Sdk;
import com.android.sdklib.SdkConstants;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Shell;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

/**
 * Export helper for project.
 */
public final class ExportHelper {

    /**
     * Exports a release version of the application created by the given project.
     * @param project the project to export
     * @param outputFile the file to write
     * @param key the key to used for signing. Can be null.
     * @param certificate the certificate used for signing. Can be null.
     * @param monitor
     */
    public static void export(IProject project, File outputFile, PrivateKey key,
            X509Certificate certificate, IProgressMonitor monitor) throws CoreException {

        // the export, takes the output of the precompiler & Java builders so it's
        // important to call build in case the auto-build option of the workspace is disabled.
        project.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, monitor);

        // if either key or certificate is null, ensure the other is null.
        if (key == null) {
            certificate = null;
        } else if (certificate == null) {
            key = null;
        }

        try {
            AndroidPrintStream fakeStream = new AndroidPrintStream(null, null, new OutputStream() {
                @Override
                public void write(int b) throws IOException {
                    // do nothing
                }
            });

            BuildHelper helper = new BuildHelper(project,
                    fakeStream, fakeStream,
                    false /*debugMode*/, false /*verbose*/);

            // get the list of library projects
            ProjectState projectState = Sdk.getProjectState(project);
            IProject[] libProjects = projectState.getFullLibraryProjects();

            // Step 1. Package the resources.

            // tmp file for the packaged resource file. To not disturb the incremental builders
            // output, all intermediary files are created in tmp files.
            File resourceFile = File.createTempFile("android_", ".ap_");

            // package the resources.
            helper.packageResources(
                    project.getFile(SdkConstants.FN_ANDROID_MANIFEST_XML),
                    libProjects,
                    null,   // res filter
                    0,      // versionCode
                    resourceFile.getParent(),
                    resourceFile.getName());

            // Step 2. Convert the byte code to Dalvik bytecode

            // tmp file for the packaged resource file.
            File dexFile = File.createTempFile("android_", ".dex");

            IFolder outputFolder = BaseProjectHelper.getOutputFolder(project);

            IJavaProject javaProject = JavaCore.create(project);
            IProject[] javaProjects = ProjectHelper.getReferencedProjects(project);
            IJavaProject[] referencedJavaProjects = BuildHelper.getJavaProjects(
                    javaProjects);

            helper.executeDx(
                    javaProject,
                    outputFolder.getLocation().toOSString(),
                    dexFile.getAbsolutePath(),
                    referencedJavaProjects,
                    null /*resourceMarker*/);

            // Step 3. Final package

            helper.finalPackage(
                    resourceFile.getAbsolutePath(),
                    dexFile.getAbsolutePath(),
                    outputFile.getAbsolutePath(),
                    javaProject,
                    libProjects,
                    referencedJavaProjects,
                    null /*abiFilter*/,
                    key,
                    certificate,
                    null); //resourceMarker

            // success!
        } catch (CoreException e) {
            throw e;
        } catch (Exception e) {
           //?
        }
    }

    /**
     * Exports an unsigned release APK after prompting the user for a location.
     *
     * <strong>Must be called from the UI thread.</strong>
     *
     * @param project the project to export
     */
    public static void exportUnsignedReleaseApk(final IProject project) {
        Shell shell = Display.getCurrent().getActiveShell();

        // get the java project to get the output directory
        IFolder outputFolder = BaseProjectHelper.getOutputFolder(project);
        if (outputFolder != null) {
            IPath binLocation = outputFolder.getLocation();

            // make the full path to the package
            String fileName = project.getName() + AndroidConstants.DOT_ANDROID_PACKAGE;

            File file = new File(binLocation.toOSString() + File.separator + fileName);

            if (file.exists() == false || file.isFile() == false) {
                MessageDialog.openError(Display.getCurrent().getActiveShell(),
                        "Android IDE Plug-in",
                        String.format("Failed to export %1$s: %2$s doesn't exist!",
                                project.getName(), file.getPath()));
                return;
            }

            // ok now pop up the file save window
            FileDialog fileDialog = new FileDialog(shell, SWT.SAVE);

            fileDialog.setText("Export Project");
            fileDialog.setFileName(fileName);

            final String saveLocation = fileDialog.open();
            if (saveLocation != null) {
                new Job("Android Release Export") {
                    @Override
                    protected IStatus run(IProgressMonitor monitor) {
                        try {
                            export(project,
                                    new File(saveLocation),
                                    null, //key
                                    null, //certificate
                                    monitor);

                            // this is unsigned export. Let's tell the developers to run zip align
                            AdtPlugin.displayWarning("Android IDE Plug-in", String.format(
                                    "An unsigned package of the application was saved at\n%1$s\n\n" +
                                    "Before publishing the application you will need to:\n" +
                                    "- Sign the application with your release key,\n" +
                                    "- run zipalign on the signed package. ZipAlign is located in <SDK>/tools/\n\n" +
                                    "Aligning applications allows Android to use application resources\n" +
                                    "more efficiently.", saveLocation));

                            return Status.OK_STATUS;
                        } catch (CoreException e) {
                            return e.getStatus();
                        }
                    }
                }.schedule();


            }
        } else {
            MessageDialog.openError(shell, "Android IDE Plug-in",
                    String.format("Failed to export %1$s: Could not get project output location",
                            project.getName()));
        }
    }
}
