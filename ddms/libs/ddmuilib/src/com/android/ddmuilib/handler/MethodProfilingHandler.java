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

import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.Log;
import com.android.ddmlib.SyncService;
import com.android.ddmlib.ClientData.IMethodProfilingHandler;
import com.android.ddmlib.SyncService.SyncResult;
import com.android.ddmuilib.DdmUiPreferences;
import com.android.ddmuilib.console.DdmConsole;

import org.eclipse.swt.widgets.Shell;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;

/**
 * Handler for Method tracing.
 * This will pull the trace file into a temp file and launch traceview.
 */
public class MethodProfilingHandler extends BaseFileHandler
        implements IMethodProfilingHandler {

    public MethodProfilingHandler(Shell parentShell) {
        super(parentShell);
    }

    @Override
    protected String getDialogTitle() {
        return "Method Profiling Error";
    }

    public void onStartFailure(final Client client, final String message) {
        displayErrorInUiThread(
                "Unable to create Method Profiling file for application '%1$s'\n\n%2$s" +
                "Check logcat for more information.",
                client.getClientData().getClientDescription(),
                message != null ? message + "\n\n" : "");
    }

    public void onEndFailure(final Client client, final String message) {
        displayErrorInUiThread(
                "Unable to finish Method Profiling for application '%1$s'\n\n%2$s" +
                "Check logcat for more information.",
                client.getClientData().getClientDescription(),
                message != null ? message + "\n\n" : "");
    }

    public void onSuccess(final String remoteFilePath, final Client client) {
        mParentShell.getDisplay().asyncExec(new Runnable() {
            public void run() {
                if (remoteFilePath == null) {
                    displayErrorFromUiThread(
                            "Unable to download trace file: unknown file name.\n" +
                            "This can happen if you disconnected the device while recording the trace.");
                    return;
                }

                final IDevice device = client.getDevice();
                try {
                    // get the sync service to pull the HPROF file
                    final SyncService sync = client.getDevice().getSyncService();
                    if (sync != null) {
                        pullAndOpen(sync, remoteFilePath);
                    } else {
                        displayErrorFromUiThread("Unable to download trace file from device '%1$s'.",
                                device.getSerialNumber());
                    }
                } catch (Exception e) {
                    displayErrorFromUiThread("Unable to download trace file from device '%1$s'.",
                            device.getSerialNumber());
                }
            }

        });
    }

    public void onSuccess(byte[] data, final Client client) {
        try {
            File tempFile = saveTempFile(data);
            openInTraceview(tempFile.getAbsolutePath());
        } catch (IOException e) {
            String errorMsg = e.getMessage();
            displayErrorInUiThread(
                    "Failed to save trace data into temp file%1$s",
                    errorMsg != null ? ":\n" + errorMsg : ".");
        }
    }

    /**
     * pulls and open a file. This is run from the UI thread.
     */
    private void pullAndOpen(SyncService sync, String remoteFilePath)
            throws InvocationTargetException, InterruptedException, IOException {
        // get a temp file
        File temp = File.createTempFile("android", ".trace"); //$NON-NLS-1$ //$NON-NLS-2$
        String tempPath = temp.getAbsolutePath();

        // pull the file
        SyncResult result = pull(sync, tempPath, remoteFilePath);
        if (result != null) {
            if (result.getCode() == SyncService.RESULT_OK) {
                // open the temp file in traceview
                openInTraceview(tempPath);
            } else {
                displayErrorFromUiThread("Unable to download trace file:\n\n%1$s",
                        result.getMessage());
            }
        } else {
            // this really shouldn't happen.
            displayErrorFromUiThread("Unable to download trace file.");
        }
    }

    private void openInTraceview(String tempPath) {
        // now that we have the file, we need to launch traceview
        String[] command = new String[2];
        command[0] = DdmUiPreferences.getTraceview();
        command[1] = tempPath;

        try {
            final Process p = Runtime.getRuntime().exec(command);

            // create a thread for the output
            new Thread("Traceview output") {
                @Override
                public void run() {
                    // create a buffer to read the stderr output
                    InputStreamReader is = new InputStreamReader(p.getErrorStream());
                    BufferedReader resultReader = new BufferedReader(is);

                    // read the lines as they come. if null is returned, it's
                    // because the process finished
                    try {
                        while (true) {
                            String line = resultReader.readLine();
                            if (line != null) {
                                DdmConsole.printErrorToConsole("Traceview: " + line);
                            } else {
                                break;
                            }
                        }
                        // get the return code from the process
                        p.waitFor();
                    } catch (Exception e) {
                        Log.e("traceview", e);
                    }
                }
            }.start();
        } catch (IOException e) {
            Log.e("traceview", e);
        }
    }
}
