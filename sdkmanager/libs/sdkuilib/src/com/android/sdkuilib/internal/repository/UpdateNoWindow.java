/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.sdkuilib.internal.repository;

import com.android.sdklib.ISdkLog;
import com.android.sdklib.SdkManager;
import com.android.sdklib.internal.repository.ITask;
import com.android.sdklib.internal.repository.ITaskFactory;
import com.android.sdklib.internal.repository.ITaskMonitor;
import com.android.sdklib.repository.SdkRepository;

import java.util.ArrayList;

/**
 * Performs an update using only a non-interactive console output with no GUI.
 * <p/>
 * TODO: It may be useful in the future to let the filter specify packages names
 * rather than package types, typically to let the user upgrade to a new platform.
 * This can be achieved easily by simply allowing package names in the pkgFilter
 * argument.
 */
public class UpdateNoWindow {

    /** The {@link UpdaterData} to use. */
    private final UpdaterData mUpdaterData;
    /** The {@link ISdkLog} logger to use. */
    private final ISdkLog mSdkLog;
    /** The reply to any question asked by the update process. Currently this will
     *   be yes/no for ability to replace modified samples or restart ADB. */
    private final boolean mForce;

    /**
     * Creates an UpdateNoWindow object that will update using the given SDK root
     * and outputs to the given SDK logger.
     *
     * @param osSdkRoot The OS path of the SDK folder to update.
     * @param sdkManager An existing SDK manager to list current platforms and addons.
     * @param sdkLog A logger object, that should ideally output to a write-only console.
     * @param force The reply to any question asked by the update process. Currently this will
     *   be yes/no for ability to replace modified samples or restart ADB.
     * @param useHttp True to force using HTTP instead of HTTPS for downloads.
     */
    public UpdateNoWindow(String osSdkRoot,
            SdkManager sdkManager,
            ISdkLog sdkLog,
            boolean force,
            boolean useHttp) {
        mSdkLog = sdkLog;
        mForce = force;
        mUpdaterData = new UpdaterData(osSdkRoot, sdkLog);

        // Change the in-memory settings to force the http/https mode
        mUpdaterData.getSettingsController().setSetting(ISettingsPage.KEY_FORCE_HTTP, useHttp);

        // Use a factory that only outputs to the given ISdkLog.
        mUpdaterData.setTaskFactory(new ConsoleTaskFactory());

        // Check that the AVD Manager has been correctly initialized. This is done separately
        // from the constructor in the GUI-based UpdaterWindowImpl to give time to the UI to
        // initialize before displaying a message box. Since we don't have any GUI here
        // we can call it whenever we want.
        if (mUpdaterData.checkIfInitFailed()) {
            return;
        }

        // Setup the default sources including the getenv overrides.
        mUpdaterData.setupDefaultSources();

        mUpdaterData.getLocalSdkParser().parseSdk(osSdkRoot, sdkManager, sdkLog);

    }

    /**
     * Performs the actual update.
     *
     * @param pkgFilter A list of {@link SdkRepository#NODES} to limit the type of packages
     *   we can update. A null or empty list means to update everything possible.
     * @param includeObsoletes True to also list and install obsolete packages.
     * @param dryMode True to check what would be updated/installed but do not actually
     *   download or install anything.
     */
    public void updateAll(
            ArrayList<String> pkgFilter,
            boolean includeObsoletes,
            boolean dryMode) {
        mUpdaterData.updateOrInstallAll_NoGUI(pkgFilter, includeObsoletes, dryMode);
    }

    /**
     * A custom implementation of {@link ITaskFactory} that provides {@link ConsoleTask} objects.
     */
    private class ConsoleTaskFactory implements ITaskFactory {
        public void start(String title, ITask task) {
            new ConsoleTask(title, task);
        }
    }

    /**
     * A custom implementation of {@link ITaskMonitor} that defers all output to the
     * super {@link UpdateNoWindow#mSdkLog}.
     */
    private class ConsoleTask implements ITaskMonitor {

        private static final double MAX_COUNT = 10000.0;
        private double mIncCoef = 0;
        private double mValue = 0;
        private String mLastDesc = null;
        private String mLastProgressBase = null;

        /**
         * Creates a new {@link ConsoleTask} with the given title.
         */
        public ConsoleTask(String title, ITask task) {
            mSdkLog.printf("%s:\n", title);
            task.run(this);
        }

        /**
         * Sets the description in the current task dialog.
         */
        public void setDescription(String descriptionFormat, Object...args) {

            String last = mLastDesc;
            String line = String.format("  " + descriptionFormat, args);

            // If the description contains a %, it generally indicates a recurring
            // progress so we want a \r at the end.
            if (line.indexOf('%') > -1) {
                if (mLastProgressBase != null && line.startsWith(mLastProgressBase)) {
                    line = "    " + line.substring(mLastProgressBase.length());
                }
                line += "\r";
            } else {
                mLastProgressBase = line;
                line += "\n";
            }

            // Skip line if it's the same as the last one.
            if (last != null && last.equals(line)) {
                return;
            }
            mLastDesc = line;

            // If the last line terminated with a \r but the new one doesn't, we need to
            // insert a \n to avoid erasing the previous line.
            if (last != null &&
                    last.endsWith("\r") &&
                    !line.endsWith("\r")) {
                line = "\n" + line;
            }

            mSdkLog.printf("%s", line);
        }

        /**
         * Sets the description in the current task dialog.
         */
        public void setResult(String resultFormat, Object...args) {
            setDescription(resultFormat, args);
        }

        /**
         * Sets the max value of the progress bar.
         *
         * Weird things will happen if setProgressMax is called multiple times
         * *after* {@link #incProgress(int)}: we don't try to adjust it on the
         * fly.
         */
        public void setProgressMax(int max) {
            assert max > 0;
            // Always set the dialog's progress max to 10k since it only handles
            // integers and we want to have a better inner granularity. Instead
            // we use the max to compute a coefficient for inc deltas.
            mIncCoef = max > 0 ? MAX_COUNT / max : 0;
            assert mIncCoef > 0;
        }

        /**
         * Increments the current value of the progress bar.
         */
        public void incProgress(int delta) {
            assert mIncCoef > 0;
            assert delta > 0;
            internalIncProgress(delta * mIncCoef);
        }

        private void internalIncProgress(double realDelta) {
            mValue += realDelta;
            // max value is 10k, so 10k/100 == 100%.
            // Experimentation shows that it is not really useful to display this
            // progression since during download the description line will change.
            // mSdkLog.printf("    [%3d%%]\r", ((int)mValue) / 100);
        }

        /**
         * Returns the current value of the progress bar,
         * between 0 and up to {@link #setProgressMax(int)} - 1.
         */
        public int getProgress() {
            assert mIncCoef > 0;
            return mIncCoef > 0 ? (int)(mValue / mIncCoef) : 0;
        }

        /**
         * Returns true if the "Cancel" button was selected.
         */
        public boolean isCancelRequested() {
            return false;
        }

        /**
         * Display a yes/no question dialog box.
         *
         * This implementation allow this to be called from any thread, it
         * makes sure the dialog is opened synchronously in the ui thread.
         *
         * @param title The title of the dialog box
         * @param message The error message
         * @return true if YES was clicked.
         */
        public boolean displayPrompt(final String title, final String message) {
            // TODO Make it interactive if mForce==false
            mSdkLog.printf("\n%s\n%s\n[y/n] => %s\n",
                    title,
                    message,
                    mForce ? "yes" : "no (use --force to override)");
            return mForce;
        }

        /**
         * Creates a sub-monitor that will use up to tickCount on the progress bar.
         * tickCount must be 1 or more.
         */
        public ITaskMonitor createSubMonitor(int tickCount) {
            assert mIncCoef > 0;
            assert tickCount > 0;
            return new ConsoleSubTaskMonitor(this, null, mValue, tickCount * mIncCoef);
        }
    }

    private interface IConsoleSubTaskMonitor extends ITaskMonitor {
        public void subIncProgress(double realDelta);
    }

    private static class ConsoleSubTaskMonitor implements IConsoleSubTaskMonitor {

        private final ConsoleTask mRoot;
        private final IConsoleSubTaskMonitor mParent;
        private final double mStart;
        private final double mSpan;
        private double mSubValue;
        private double mSubCoef;

        /**
         * Creates a new sub task monitor which will work for the given range [start, start+span]
         * in its parent.
         *
         * @param root The ProgressTask root
         * @param parent The immediate parent. Can be the null or another sub task monitor.
         * @param start The start value in the root's coordinates
         * @param span The span value in the root's coordinates
         */
        public ConsoleSubTaskMonitor(ConsoleTask root,
                IConsoleSubTaskMonitor parent,
                double start,
                double span) {
            mRoot = root;
            mParent = parent;
            mStart = start;
            mSpan = span;
            mSubValue = start;
        }

        public boolean isCancelRequested() {
            return mRoot.isCancelRequested();
        }

        public void setDescription(String descriptionFormat, Object... args) {
            mRoot.setDescription(descriptionFormat, args);
        }

        public void setResult(String resultFormat, Object... args) {
            mRoot.setResult(resultFormat, args);
        }

        public void setProgressMax(int max) {
            assert max > 0;
            mSubCoef = max > 0 ? mSpan / max : 0;
            assert mSubCoef > 0;
        }

        public int getProgress() {
            assert mSubCoef > 0;
            return mSubCoef > 0 ? (int)((mSubValue - mStart) / mSubCoef) : 0;
        }

        public void incProgress(int delta) {
            assert mSubCoef > 0;
            subIncProgress(delta * mSubCoef);
        }

        public void subIncProgress(double realDelta) {
            mSubValue += realDelta;
            if (mParent != null) {
                mParent.subIncProgress(realDelta);
            } else {
                mRoot.internalIncProgress(realDelta);
            }
        }

        public boolean displayPrompt(String title, String message) {
            return mRoot.displayPrompt(title, message);
        }

        public ITaskMonitor createSubMonitor(int tickCount) {
            assert mSubCoef > 0;
            assert tickCount > 0;
            return new ConsoleSubTaskMonitor(mRoot,
                    this,
                    mSubValue,
                    tickCount * mSubCoef);
        }
    }
}
