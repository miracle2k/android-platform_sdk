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
import com.android.ide.eclipse.adt.internal.build.DexWrapper;
import com.android.ide.eclipse.adt.internal.sdk.AdtConsoleSdkLog;
import com.android.ide.eclipse.adt.internal.sdk.Sdk;
import com.android.sdkuilib.repository.ISdkChangeListener;
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
                    sdk.getSdkLocation());

            ISdkChangeListener listener = new ISdkChangeListener() {
                public void onSdkLoaded() {
                    // Ignore initial load of the SDK.
                }

                /**
                 * Unload all we can from the SDK before new packages are installed.
                 * Typically we need to get rid of references to dx from platform-tools
                 * and to any platform resource data.
                 * <p/>
                 * {@inheritDoc}
                 */
                public void preInstallHook() {

                    // TODO we need to unload as much of as SDK as possible. Otherwise
                    // on Windows we end up with Eclipse locking some files and we can't
                    // replace them.
                    //
                    // At this point, we know what the user wants to install so it would be
                    // possible to pass in flags to know what needs to be unloaded. Typically
                    // we need to:
                    // - unload dex if platform-tools is going to be updated. There's a vague
                    //   attempt below at removing any references to dex and GCing. Seems
                    //   to do the trick.
                    // - unload any target that is going to be updated since it may have
                    //   resource data used by a current layout editor (e.g. data/*.ttf
                    //   and various data/res/*.xml).
                    //
                    // Most important we need to make sure there isn't a build going on
                    // and if there is one, either abort it or wait for it to complete and
                    // then we want to make sure we don't get any attempt to use the SDK
                    // before the postInstallHook is called.

                    Sdk sdk = Sdk.getCurrent();
                    if (sdk != null) {
                        DexWrapper dx = sdk.getDexWrapper();
                        dx.unload();
                    }
                }

                /**
                 * Nothing to do. We'll reparse the SDK later in onSdkReload.
                 * <p/>
                 * {@inheritDoc}
                 */
                public void postInstallHook() {
                }

                /**
                 * Reparse the SDK in case anything was add/removed.
                 * <p/>
                 * {@inheritDoc}
                 */
                public void onSdkReload() {
                    AdtPlugin.getDefault().reparseSdk();
                }
            };

            window.addListener(listener);
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
