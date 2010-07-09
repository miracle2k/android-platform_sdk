/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.ide.eclipse.adt.internal.wizards.actions;

import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.internal.sdk.AdtConsoleSdkLog;
import com.android.ide.eclipse.adt.internal.sdk.Sdk;
import com.android.sdkuilib.repository.UpdaterWindow;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWorkbenchWindowActionDelegate;

/**
 * Delegate for the toolbar/menu action "Android AVD Manager".
 * It displays the Android AVD Manager.
 */
public class AvdManagerAction implements IWorkbenchWindowActionDelegate, IObjectActionDelegate {

    public void dispose() {
        // nothing to dispose.
    }

    public void init(IWorkbenchWindow window) {
        // no init
    }

    public void run(IAction action) {
        Sdk sdk = Sdk.getCurrent();
        if (sdk != null) {

            // Runs the updater window, directing all logs to the ADT console.

            UpdaterWindow window = new UpdaterWindow(
                    AdtPlugin.getDisplay().getActiveShell(),
                    new AdtConsoleSdkLog(),
                    sdk.getSdkLocation(),
                    false /*userCanChangeSdkRoot*/);
            window.addListeners(new UpdaterWindow.ISdkListener() {
                public void onSdkChange(boolean init) {
                    if (init == false) { // ignore initial load of the SDK.
                        AdtPlugin.getDefault().reparseSdk();
                    }
                }
            });
            window.open();
        } else {
            AdtPlugin.displayError("Android SDK",
                    "Location of the Android SDK has not been setup in the preferences.");
        }
    }

    public void selectionChanged(IAction action, ISelection selection) {
        // nothing related to the current selection.
    }

    public void setActivePart(IAction action, IWorkbenchPart targetPart) {
        // nothing to do.
    }
}
