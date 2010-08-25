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

package com.android.ide.eclipse.adt.internal.actions;

import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.internal.preferences.AdtPrefs.BuildVerbosity;
import com.android.ide.eclipse.adt.internal.sdk.Sdk;
import com.android.sdklib.SdkConstants;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Iterator;

/**
 * Runs dexdump on the classes.dex of a selected project.
 */
public class DexDumpAction implements IObjectActionDelegate {

    private ISelection mSelection;

    public void setActivePart(IAction action, IWorkbenchPart targetPart) {
        // pass
    }

    public void run(IAction action) {
        if (mSelection instanceof IStructuredSelection) {
            for (Iterator<?> it = ((IStructuredSelection)mSelection).iterator(); it.hasNext();) {
                Object element = it.next();
                IProject project = null;
                if (element instanceof IProject) {
                    project = (IProject)element;
                } else if (element instanceof IAdaptable) {
                    project = (IProject)((IAdaptable)element).getAdapter(IProject.class);
                }
                if (project != null) {
                    dexDumpProject(project);
                }
            }
        }
    }

    public void selectionChanged(IAction action, ISelection selection) {
        mSelection = selection;
    }

    /**
     * Calls {@link #runDexDump(IProject, IProgressMonitor)} inside a job.
     *
     * @param project on which to run dexdump.
     */
    private void dexDumpProject(final IProject project) {
        new Job("Dexdump") {
            @Override
            protected IStatus run(IProgressMonitor monitor) {
                return runDexDump(project, monitor);
            }
        }.schedule();
    }

    /**
     * Runs <code>dexdump</code> on the classex.dex of the project.
     * Saves the output in a temporary file.
     * On success, opens the file in the default text editor.
     *
     * @param project on which to run dexdump.
     * @param monitor The job's monitor.
     */
    private IStatus runDexDump(final IProject project, IProgressMonitor monitor) {
        File dstFile = null;
        boolean removeDstFile = true;
        try {
            if (monitor != null) {
                monitor.beginTask(String.format("Dump dex of %1$s", project.getName()), 2);
            }

            Sdk current = Sdk.getCurrent();
            if (current == null) {
                AdtPlugin.printErrorToConsole(project,
                        "DexDump: missing current SDK");                            //$NON-NLS-1$
                return Status.OK_STATUS;
            }

            String sdkOsPath = current.getSdkLocation();
            File dexDumpFile = new File(new File(sdkOsPath, SdkConstants.FD_PLATFORM_TOOLS),
                                        SdkConstants.FN_DEXDUMP);

            IPath binPath = project.getFolder(SdkConstants.FD_OUTPUT).getLocation();
            if (binPath == null) {
                AdtPlugin.printErrorToConsole(project,
                    "DexDump: missing project /bin folder. Please compile first."); //$NON-NLS-1$
                return Status.OK_STATUS;
            }

            File classesDexFile =
                new File(binPath.toOSString(), SdkConstants.FN_APK_CLASSES_DEX);
            if (!classesDexFile.exists()) {
                AdtPlugin.printErrorToConsole(project,
                    "DexDump: missing classex.dex for project. Please compile first.");//$NON-NLS-1$
                return Status.OK_STATUS;
            }

            try {
                dstFile = File.createTempFile(
                        "dexdump_" + project.getName() + "_",         //$NON-NLS-1$ //$NON-NLS-2$
                        ".txt");                                                    //$NON-NLS-1$
            } catch (Exception e) {
                AdtPlugin.logAndPrintError(e, project.getName(),
                        "DexDump: createTempFile failed.");                         //$NON-NLS-1$
                return Status.OK_STATUS;
            }

            // --- Exec command line and save result to dst file

            String[] command = new String[2];
            command[0] = dexDumpFile.getAbsolutePath();
            command[1] = classesDexFile.getAbsolutePath();

            try {
                int err = grabProcessOutput(project, command, dstFile);
                if (err == 0) {
                    // The command worked. In this case we don't remove the
                    // temp file in the finally block.
                    removeDstFile = false;
                } else {
                    AdtPlugin.printErrorToConsole(project,
                        "DexDump failed with code " + Integer.toString(err));       //$NON-NLS-1$
                    return Status.OK_STATUS;
                }
            } catch (InterruptedException e) {
                // ?
            }

            if (monitor != null) {
                monitor.worked(1);
            }

            // --- Open the temp file in an editor

            final String dstPath = dstFile.getAbsolutePath();
            AdtPlugin.getDisplay().asyncExec(new Runnable() {
                public void run() {
                    IFileStore fileStore =
                        EFS.getLocalFileSystem().getStore(new Path(dstPath));
                    if (!fileStore.fetchInfo().isDirectory() &&
                            fileStore.fetchInfo().exists()) {

                        IWorkbench wb = PlatformUI.getWorkbench();
                        IWorkbenchWindow win = wb == null ? null : wb.getActiveWorkbenchWindow();
                        final IWorkbenchPage page = win == null ? null : win.getActivePage();

                        if (page != null) {
                            try {
                                IDE.openEditorOnFileStore(page, fileStore);
                            } catch (PartInitException e) {
                                AdtPlugin.logAndPrintError(e, project.getName(),
                                "Opening DexDump result failed. Result is available at %1$s", //$NON-NLS-1$
                                dstPath);
                            }
                        }
                    }
                }
            });

            if (monitor != null) {
                monitor.worked(1);
            }

            return Status.OK_STATUS;

        } catch (IOException e) {
            AdtPlugin.logAndPrintError(e, project.getName(),
                    "DexDump failed.");                                     //$NON-NLS-1$
            return Status.OK_STATUS;

        } finally {
            // By default we remove the temp file on failure.
            if (removeDstFile && dstFile != null) {
                try {
                    dstFile.delete();
                } catch (Exception e) {
                    AdtPlugin.logAndPrintError(e, project.getName(),
                            "DexDump: can't delete temp file %1$s.",        //$NON-NLS-1$
                            dstFile.getAbsoluteFile());
                }
            }
            if (monitor != null) {
                monitor.done();
            }
        }
    }


    /**
     * Get the stdout+stderr output of a process and return when the process is done.
     * @param command The command line for the process to run.
     * @param dstFile The file where to write the stdout.
     * @return the process return code.
     * @throws InterruptedException
     * @throws IOException
     */
    private final int grabProcessOutput(
            final IProject project,
            String[] command,
            final File dstFile)
            throws InterruptedException, IOException {

        final BufferedWriter writer = new BufferedWriter(new FileWriter(dstFile));

        String sep = System.getProperty("line.separator");                  //$NON-NLS-1$
        if (sep == null || sep.length() < 1) {
            if (SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_WINDOWS) {
                sep = "\r\n";                                               //$NON-NLS-1$
            } else {
                sep = "\n";                                                 //$NON-NLS-1$
            }
        }
        final String lineSep = sep;

        final Process process = Runtime.getRuntime().exec(command);

        try {
            // read the lines as they come. if null is returned, it's
            // because the process finished
            Thread t1 = new Thread("") { //$NON-NLS-1$
                @Override
                public void run() {
                    // create a buffer to read the stderr output
                    InputStreamReader is = new InputStreamReader(process.getInputStream());
                    BufferedReader outReader = new BufferedReader(is);

                    try {
                        while (true) {
                            String line = outReader.readLine();
                            if (line != null) {
                                writer.write(line);
                                writer.write(lineSep);
                            } else {
                                break;
                            }
                        }
                    } catch (IOException e) {
                        // do nothing.
                    }
                }
            };

            Thread t2 = new Thread("") { //$NON-NLS-1$
                @Override
                public void run() {
                    InputStreamReader is = new InputStreamReader(process.getErrorStream());
                    BufferedReader errReader = new BufferedReader(is);

                    try {
                        while (true) {
                            String line = errReader.readLine();
                            if (line != null) {
                                AdtPlugin.printBuildToConsole(BuildVerbosity.VERBOSE,
                                        project, line);
                            } else {
                                break;
                            }
                        }
                    } catch (IOException e) {
                        // do nothing.
                    }
                }

            };

            t1.start();
            t2.start();

            // it looks like on windows process#waitFor() can return
            // before the thread have filled the arrays, so we wait for both threads and the
            // process itself.
            try {
                t1.join();
            } catch (InterruptedException e) {
            }
            try {
                t2.join();
            } catch (InterruptedException e) {
            }

            // get the return code from the process
            return process.waitFor();
        } finally {
            try {
                writer.close();
            } catch (IOException ignore) {
            }
        }
    }

}
