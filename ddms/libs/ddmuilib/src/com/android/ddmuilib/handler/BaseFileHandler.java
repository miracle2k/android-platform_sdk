/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.ddmuilib.handler;

import com.android.ddmlib.SyncService;
import com.android.ddmlib.ClientData.IHprofDumpHandler;
import com.android.ddmlib.ClientData.IMethodProfilingHandler;
import com.android.ddmlib.SyncService.SyncResult;
import com.android.ddmuilib.SyncProgressMonitor;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Shell;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

/**
 * Base handler class for handler dealing with files located on a device.
 *
 * @see IHprofDumpHandler
 * @see IMethodProfilingHandler
 */
public abstract class BaseFileHandler {

    protected final Shell mParentShell;

    public BaseFileHandler(Shell parentShell) {
        mParentShell = parentShell;
    }

    protected abstract String getDialogTitle();

    /**
     * Prompts the user for a save location and pulls the remote files into this location.
     * <p/>This <strong>must</strong> be called from the UI Thread.
     * @param sync the {@link SyncService} to use to pull the file from the device
     * @param localFileName The default local name
     * @param remoteFilePath The name of the file to pull off of the device
     * @param title The title of the File Save dialog.
     * @return The result of the pull as a {@link SyncResult} object, or null if the sync
     * didn't happen (canceled by the user).
     * @throws InvocationTargetException
     * @throws InterruptedException
     */
    protected SyncResult promptAndPull(SyncService sync,
            String localFileName, String remoteFilePath, String title)
            throws InvocationTargetException, InterruptedException {
        FileDialog fileDialog = new FileDialog(mParentShell, SWT.SAVE);

        fileDialog.setText(title);
        fileDialog.setFileName(localFileName);

        String localFilePath = fileDialog.open();
        if (localFilePath != null) {
            return pull(sync, localFilePath, remoteFilePath);
        }

        return null;
    }

    /**
     * Prompts the user for a save location and copies a temp file into it.
     * <p/>This <strong>must</strong> be called from the UI Thread.
     * @param localFileName The default local name
     * @param tempFilePath The name of the temp file to copy.
     * @param title The title of the File Save dialog.
     * @return true if success, false on error or cancel.
     */
    protected boolean promptAndSave(String localFileName, byte[] data, String title) {
        FileDialog fileDialog = new FileDialog(mParentShell, SWT.SAVE);

        fileDialog.setText(title);
        fileDialog.setFileName(localFileName);

        String localFilePath = fileDialog.open();
        if (localFilePath != null) {
            try {
                saveFile(data, new File(localFilePath));
                return true;
            } catch (IOException e) {
                String errorMsg = e.getMessage();
                displayErrorInUiThread(
                        "Failed to save file '%1$s'%2$s",
                        localFilePath,
                        errorMsg != null ? ":\n" + errorMsg : ".");
            }
        }

        return false;
    }

    /**
     * Pulls a file off of a device. This displays a {@link ProgressMonitorDialog} and therefore
     * must be run from the UI Thread.
     * @param sync the {@link SyncService} to use to pull the file.
     * @param localFilePath the path of the local file to create
     * @param remoteFilePath the path of the remote file to pull
     * @return the result of the sync as an instance of {@link SyncResult}
     * @throws InvocationTargetException
     * @throws InterruptedException
     */
    protected SyncResult pull(final SyncService sync, final String localFilePath,
            final String remoteFilePath)
            throws InvocationTargetException, InterruptedException {
        final SyncResult[] res = new SyncResult[1];
        new ProgressMonitorDialog(mParentShell).run(true, true, new IRunnableWithProgress() {
            public void run(IProgressMonitor monitor) {
                try {
                    res[0] = sync.pullFile(remoteFilePath, localFilePath,
                            new SyncProgressMonitor(monitor, String.format(
                                    "Pulling %1$s from the device", remoteFilePath)));
                } finally {
                    sync.close();
                }
            }
        });

        return res[0];
    }

    /**
     * Display an error message.
     * <p/>This will call about to {@link Display} to run this in an async {@link Runnable} in the
     * UI Thread. This is safe to be called from a non-UI Thread.
     * @param format the string to display
     * @param args the string arguments
     */
    protected void displayErrorInUiThread(final String format, final Object... args) {
        mParentShell.getDisplay().asyncExec(new Runnable() {
            public void run() {
                MessageDialog.openError(mParentShell, getDialogTitle(),
                        String.format(format, args));
            }
        });
    }

    /**
     * Display an error message.
     * This must be called from the UI Thread.
     * @param format the string to display
     * @param args the string arguments
     */
    protected void displayErrorFromUiThread(final String format, final Object... args) {
        MessageDialog.openError(mParentShell, getDialogTitle(),
                String.format(format, args));
    }

    /**
     * Saves a given data into a temp file and returns its corresponding {@link File} object.
     * @param data the data to save
     * @return the File into which the data was written or null if it failed.
     * @throws IOException
     */
    protected File saveTempFile(byte[] data) throws IOException {
        File f = File.createTempFile("ddms", null);
        saveFile(data, f);
        return f;
    }

    /**
     * Saves some data into a given File.
     * @param data the data to save
     * @param output the file into the data is saved.
     * @throws IOException
     */
    protected void saveFile(byte[] data, File output) throws IOException {
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(output);
            fos.write(data);
        } finally {
            if (fos != null) {
                fos.close();
            }
        }
    }
}
