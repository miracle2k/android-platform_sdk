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

package com.android.sdkuilib.internal.repository;

import com.android.prefs.AndroidLocation.AndroidLocationException;
import com.android.sdklib.ISdkLog;
import com.android.sdklib.SdkConstants;
import com.android.sdklib.SdkManager;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.sdklib.internal.repository.AddonPackage;
import com.android.sdklib.internal.repository.Archive;
import com.android.sdklib.internal.repository.DocPackage;
import com.android.sdklib.internal.repository.ExtraPackage;
import com.android.sdklib.internal.repository.ITask;
import com.android.sdklib.internal.repository.ITaskFactory;
import com.android.sdklib.internal.repository.ITaskMonitor;
import com.android.sdklib.internal.repository.LocalSdkParser;
import com.android.sdklib.internal.repository.Package;
import com.android.sdklib.internal.repository.PlatformPackage;
import com.android.sdklib.internal.repository.RepoSource;
import com.android.sdklib.internal.repository.RepoSources;
import com.android.sdklib.internal.repository.SamplePackage;
import com.android.sdklib.internal.repository.ToolPackage;
import com.android.sdklib.repository.SdkRepository;
import com.android.sdkuilib.internal.repository.icons.ImageFactory;
import com.android.sdkuilib.repository.UpdaterWindow.ISdkListener;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

/**
 * Data shared between {@link UpdaterWindowImpl} and its pages.
 */
class UpdaterData {
    private String mOsSdkRoot;

    private final ISdkLog mSdkLog;
    private ITaskFactory mTaskFactory;
    private boolean mUserCanChangeSdkRoot;

    private SdkManager mSdkManager;
    private AvdManager mAvdManager;

    private final LocalSdkParser mLocalSdkParser = new LocalSdkParser();
    private final RepoSources mSources = new RepoSources();

    private final LocalSdkAdapter mLocalSdkAdapter = new LocalSdkAdapter(this);
    private final RepoSourcesAdapter mSourcesAdapter = new RepoSourcesAdapter(this);

    private ImageFactory mImageFactory;

    private final SettingsController mSettingsController;

    private final ArrayList<ISdkListener> mListeners = new ArrayList<ISdkListener>();

    private Shell mWindowShell;

    private AndroidLocationException mAvdManagerInitError;

    /**
     * Creates a new updater data.
     *
     * @param sdkLog Logger. Cannot be null.
     * @param osSdkRoot The OS path to the SDK root.
     */
    public UpdaterData(String osSdkRoot, ISdkLog sdkLog) {
        mOsSdkRoot = osSdkRoot;
        mSdkLog = sdkLog;

        mSettingsController = new SettingsController(this);

        initSdk();
    }

    // ----- getters, setters ----

    public String getOsSdkRoot() {
        return mOsSdkRoot;
    }

    public void setTaskFactory(ITaskFactory taskFactory) {
        mTaskFactory = taskFactory;
    }

    public ITaskFactory getTaskFactory() {
        return mTaskFactory;
    }

    public void setUserCanChangeSdkRoot(boolean userCanChangeSdkRoot) {
        mUserCanChangeSdkRoot = userCanChangeSdkRoot;
    }

    public boolean canUserChangeSdkRoot() {
        return mUserCanChangeSdkRoot;
    }

    public RepoSources getSources() {
        return mSources;
    }

    public RepoSourcesAdapter getSourcesAdapter() {
        return mSourcesAdapter;
    }

    public LocalSdkParser getLocalSdkParser() {
        return mLocalSdkParser;
    }

    public LocalSdkAdapter getLocalSdkAdapter() {
        return mLocalSdkAdapter;
    }

    public ISdkLog getSdkLog() {
        return mSdkLog;
    }

    public void setImageFactory(ImageFactory imageFactory) {
        mImageFactory = imageFactory;
    }

    public ImageFactory getImageFactory() {
        return mImageFactory;
    }

    public SdkManager getSdkManager() {
        return mSdkManager;
    }

    public AvdManager getAvdManager() {
        return mAvdManager;
    }

    public SettingsController getSettingsController() {
        return mSettingsController;
    }

    /** Adds a listener ({@link ISdkListener}) that is notified when the SDK is reloaded. */
    public void addListeners(ISdkListener listener) {
        if (mListeners.contains(listener) == false) {
            mListeners.add(listener);
        }
    }

    /** Removes a listener ({@link ISdkListener}) that is notified when the SDK is reloaded. */
    public void removeListener(ISdkListener listener) {
        mListeners.remove(listener);
    }

    public void setWindowShell(Shell windowShell) {
        mWindowShell = windowShell;
    }

    public Shell getWindowShell() {
        return mWindowShell;
    }

    /**
     * Check if any error occurred during initialization.
     * If it did, display an error message.
     *
     * @return True if an error occurred, false if we should continue.
     */
    public boolean checkIfInitFailed() {
        if (mAvdManagerInitError != null) {
            String example;
            if (SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS) {
                example = "%USERPROFILE%";     //$NON-NLS-1$
            } else {
                example = "~";                 //$NON-NLS-1$
            }

            String error = String.format(
                "The AVD manager normally uses the user's profile directory to store " +
                "AVD files. However it failed to find the default profile directory. " +
                "\n" +
                "To fix this, please set the environment variable ANDROID_SDK_HOME to " +
                "a valid path such as \"%s\".",
                example);

            // We may not have any UI. Only display a dialog if there's a window shell available.
            if (mWindowShell != null) {
                MessageDialog.openError(mWindowShell,
                    "Android Virtual Devices Manager",
                    error);
            } else {
                mSdkLog.error(null /* Throwable */, "%s", error);  //$NON-NLS-1$
            }

            return true;
        }
        return false;
    }

    // -----

    /**
     * Initializes the {@link SdkManager} and the {@link AvdManager}.
     */
    private void initSdk() {
        mSdkManager = SdkManager.createManager(mOsSdkRoot, mSdkLog);
        try {
            mAvdManager = null; // remove the old one if needed.
            mAvdManager = new AvdManager(mSdkManager, mSdkLog);
        } catch (AndroidLocationException e) {
            mSdkLog.error(e, "Unable to read AVDs: " + e.getMessage());  //$NON-NLS-1$

            // Note: we used to continue here, but the thing is that
            // mAvdManager==null so nothing is really going to work as
            // expected. Let's just display an error later in checkIfInitFailed()
            // and abort right there. This step is just too early in the SWT
            // setup process to display a message box yet.

            mAvdManagerInitError = e;
        }

        // notify listeners.
        notifyListeners(false /*init*/);
    }

    /**
     * Reloads the SDK content (targets).
     * <p/>
     * This also reloads the AVDs in case their status changed.
     * <p/>
     * This does not notify the listeners ({@link ISdkListener}).
     */
    public void reloadSdk() {
        // reload SDK
        mSdkManager.reloadSdk(mSdkLog);

        // reload AVDs
        if (mAvdManager != null) {
            try {
                mAvdManager.reloadAvds(mSdkLog);
            } catch (AndroidLocationException e) {
                // FIXME
            }
        }

        // notify adapters?
        mLocalSdkParser.clearPackages();
        // TODO

        // notify listeners
        notifyListeners(false /*init*/);
    }

    /**
     * Reloads the AVDs.
     * <p/>
     * This does not notify the listeners.
     */
    public void reloadAvds() {
        // reload AVDs
        if (mAvdManager != null) {
            try {
                mAvdManager.reloadAvds(mSdkLog);
            } catch (AndroidLocationException e) {
                mSdkLog.error(e, null);
            }
        }
    }

    /**
     * Sets up the default sources: <br/>
     * - the default google SDK repository, <br/>
     * - the extra repo URLs from the environment, <br/>
     * - the user sources from prefs <br/>
     * - and finally the extra user repo URLs from the environment.
     */
    public void setupDefaultSources() {
        RepoSources sources = getSources();
        sources.add(new RepoSource(SdkRepository.URL_GOOGLE_SDK_REPO_SITE, false /*userSource*/));

        // SDK_UPDATER_URLS is a semicolon-separated list of URLs that can be used to
        // seed the SDK Updater list for full repositories.
        String str = System.getenv("SDK_UPDATER_URLS");
        if (str != null) {
            String[] urls = str.split(";");
            for (String url : urls) {
                if (url != null && url.length() > 0) {
                    RepoSource s = new RepoSource(url, false /*userSource*/);
                    if (!sources.hasSource(s)) {
                        sources.add(s);
                    }
                }
            }
        }

        // Load user sources
        sources.loadUserSources(getSdkLog());

        // SDK_UPDATER_USER_URLS is a semicolon-separated list of URLs that can be used to
        // seed the SDK Updater list for user-only repositories. User sources can only provide
        // add-ons and extra packages.
        str = System.getenv("SDK_UPDATER_USER_URLS");
        if (str != null) {
            String[] urls = str.split(";");
            for (String url : urls) {
                if (url != null && url.length() > 0) {
                    RepoSource s = new RepoSource(url, true /*userSource*/);
                    if (!sources.hasSource(s)) {
                        sources.add(s);
                    }
                }
            }
        }
    }

    /**
     * Returns the list of installed packages, parsing them if this has not yet been done.
     */
    public Package[] getInstalledPackage() {
        LocalSdkParser parser = getLocalSdkParser();

        Package[] packages = parser.getPackages();

        if (packages == null) {
            // load on demand the first time
            packages = parser.parseSdk(getOsSdkRoot(), getSdkManager(), getSdkLog());
        }

        return packages;
    }

    /**
     * Notify the listeners ({@link ISdkListener}) that the SDK was reloaded.
     * <p/>
     * This can be called from any thread.
     *
     * @param init whether the SDK loaded for the first time.
     */
    public void notifyListeners(final boolean init) {
        if (mWindowShell != null && mListeners.size() > 0) {
            mWindowShell.getDisplay().syncExec(new Runnable() {
                public void run() {
                    for (ISdkListener listener : mListeners) {
                        try {
                            listener.onSdkChange(init);
                        } catch (Throwable t) {
                            mSdkLog.error(t, null);
                        }
                    }
                }
            });
        }
    }

    /**
     * Install the list of given {@link Archive}s. This is invoked by the user selecting some
     * packages in the remote page and then clicking "install selected".
     *
     * @param result The archives to install. Incompatible ones will be skipped.
     */
    public void installArchives(final ArrayList<ArchiveInfo> result) {
        if (mTaskFactory == null) {
            throw new IllegalArgumentException("Task Factory is null");
        }

        final boolean forceHttp = getSettingsController().getForceHttp();

        mTaskFactory.start("Installing Archives", new ITask() {
            public void run(ITaskMonitor monitor) {

                final int progressPerArchive = 2 * Archive.NUM_MONITOR_INC;
                monitor.setProgressMax(result.size() * progressPerArchive);
                monitor.setDescription("Preparing to install archives");

                boolean installedAddon = false;
                boolean installedTools = false;

                // Mark all current local archives as already installed.
                HashSet<Archive> installedArchives = new HashSet<Archive>();
                for (Package p : getInstalledPackage()) {
                    for (Archive a : p.getArchives()) {
                        installedArchives.add(a);
                    }
                }

                int numInstalled = 0;
                nextArchive: for (ArchiveInfo ai : result) {
                    Archive archive = ai.getNewArchive();
                    if (archive == null) {
                        // This is not supposed to happen.
                        continue nextArchive;
                    }

                    int nextProgress = monitor.getProgress() + progressPerArchive;
                    try {
                        if (monitor.isCancelRequested()) {
                            break;
                        }

                        ArchiveInfo[] adeps = ai.getDependsOn();
                        if (adeps != null) {
                            for (ArchiveInfo adep : adeps) {
                                Archive na = adep.getNewArchive();
                                if (na == null) {
                                    // This archive depends on a missing archive.
                                    // We shouldn't get here.
                                    // Skip it.
                                    monitor.setResult("Skipping '%1$s'; it depends on a missing package.",
                                            archive.getParentPackage().getShortDescription());
                                    continue nextArchive;
                                } else if (!installedArchives.contains(na)) {
                                    // This archive depends on another one that was not installed.
                                    // We shouldn't get here.
                                    // Skip it.
                                    monitor.setResult("Skipping '%1$s'; it depends on '%2$s' which was not installed.",
                                            archive.getParentPackage().getShortDescription(),
                                            adep.getShortDescription());
                                    continue nextArchive;
                                }
                            }
                        }

                        if (archive.install(mOsSdkRoot, forceHttp, mSdkManager, monitor)) {
                            // We installed this archive.
                            installedArchives.add(archive);
                            numInstalled++;

                            // If this package was replacing an existing one, the old one
                            // is no longer installed.
                            installedArchives.remove(ai.getReplaced());

                            // Check if we successfully installed a tool or add-on package.
                            if (archive.getParentPackage() instanceof AddonPackage) {
                                installedAddon = true;
                            } else if (archive.getParentPackage() instanceof ToolPackage) {
                                installedTools = true;
                            }
                        }

                    } catch (Throwable t) {
                        // Display anything unexpected in the monitor.
                        String msg = t.getMessage();
                        if (msg != null) {
                            msg = String.format("Unexpected Error installing '%1$s': %2$s: %3$s",
                                    archive.getParentPackage().getShortDescription(),
                                    t.getClass().getCanonicalName(), msg);
                        } else {
                            // no error info? get the stack call to display it
                            // At least that'll give us a better bug report.
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            t.printStackTrace(new PrintStream(baos));

                            msg = String.format("Unexpected Error installing '%1$s'\n%2$s",
                                    archive.getParentPackage().getShortDescription(),
                                    baos.toString());
                        }

                        monitor.setResult(msg);
                        mSdkLog.error(t, msg);
                    } finally {

                        // Always move the progress bar to the desired position.
                        // This allows internal methods to not have to care in case
                        // they abort early
                        monitor.incProgress(nextProgress - monitor.getProgress());
                    }
                }

                if (installedAddon) {
                    // Update the USB vendor ids for adb
                    try {
                        mSdkManager.updateAdb();
                        monitor.setResult("Updated ADB to support the USB devices declared in the SDK add-ons.");
                    } catch (Exception e) {
                        mSdkLog.error(e, "Update ADB failed");
                        monitor.setResult("failed to update adb to support the USB devices declared in the SDK add-ons.");
                    }
                }

                if (installedAddon || installedTools) {
                    // We need to restart ADB. Actually since we don't know if it's even
                    // running, maybe we should just kill it and not start it.
                    // Note: it turns out even under Windows we don't need to kill adb
                    // before updating the tools folder, as adb.exe is (surprisingly) not
                    // locked.

                    askForAdbRestart(monitor);
                }

                if (installedTools) {
                    notifyToolsNeedsToBeRestarted();
                }

                if (numInstalled == 0) {
                    monitor.setDescription("Done. Nothing was installed.");
                } else {
                    monitor.setDescription("Done. %1$d %2$s installed.",
                            numInstalled,
                            numInstalled == 1 ? "package" : "packages");

                    //notify listeners something was installed, so that they can refresh
                    reloadSdk();
                }
            }
        });
    }

    /**
     * Attempts to restart ADB.
     * <p/>
     * If the "ask before restart" setting is set (the default), prompt the user whether
     * now is a good time to restart ADB.
     *
     * @param monitor
     */
    private void askForAdbRestart(ITaskMonitor monitor) {
        final boolean[] canRestart = new boolean[] { true };

        if (getWindowShell() != null && getSettingsController().getAskBeforeAdbRestart()) {
            // need to ask for permission first
            Display display = getWindowShell().getDisplay();

            display.syncExec(new Runnable() {
                public void run() {
                    canRestart[0] = MessageDialog.openQuestion(getWindowShell(),
                            "ADB Restart",
                            "A package that depends on ADB has been updated. It is recommended " +
                            "to restart ADB. Is it OK to do it now? If not, you can restart it " +
                            "manually later.");
                }
            });
        }

        if (canRestart[0]) {
            AdbWrapper adb = new AdbWrapper(getOsSdkRoot(), monitor);
            adb.stopAdb();
            adb.startAdb();
        }
    }

    private void notifyToolsNeedsToBeRestarted() {
        if (getWindowShell() == null) {
            // We don't need to print anything if this is a standalone console update.
            return;
        }

        Display display = getWindowShell().getDisplay();

        display.syncExec(new Runnable() {
            public void run() {
                MessageDialog.openInformation(getWindowShell(),
                        "Android Tools Updated",
                        "The Android SDK and AVD Manager that you are currently using has been updated. " +
                        "It is recommended that you now close the manager window and re-open it. " +
                        "If you started this window from Eclipse, please check if the Android " +
                        "plug-in needs to be updated.");
            }
        });
    }


    /**
     * Tries to update all the *existing* local packages.
     * This version *requires* to be run with a GUI.
     * <p/>
     * There are two modes of operation:
     * <ul>
     * <li>If selectedArchives is null, refreshes all sources, compares the available remote
     * packages with the current local ones and suggest updates to be done to the user (including
     * new platforms that the users doesn't have yet).
     * <li>If selectedArchives is not null, this represents a list of archives/packages that
     * the user wants to install or update, so just process these.
     * </ul>
     *
     * @param selectedArchives The list of remote archives to consider for the update.
     *  This can be null, in which case a list of remote archive is fetched from all
     *  available sources.
     */
    public void updateOrInstallAll_WithGUI(Collection<Archive> selectedArchives) {
        if (selectedArchives == null) {
            refreshSources(true);
        }

        UpdaterLogic ul = new UpdaterLogic();
        ArrayList<ArchiveInfo> archives = ul.computeUpdates(
                selectedArchives,
                getSources(),
                getLocalSdkParser().getPackages());

        if (selectedArchives == null) {
            ul.addNewPlatforms(archives, getSources(), getLocalSdkParser().getPackages());
        }

        // TODO if selectedArchives is null and archives.len==0, find if there are
        // any new platform we can suggest to install instead.

        UpdateChooserDialog dialog = new UpdateChooserDialog(getWindowShell(), this, archives);
        dialog.open();

        ArrayList<ArchiveInfo> result = dialog.getResult();
        if (result != null && result.size() > 0) {
            installArchives(result);
        }
    }

    /**
     * Tries to update all the *existing* local packages.
     * This version is intended to run without a GUI and
     * only outputs to the current {@link ISdkLog}.
     *
     * @param pkgFilter A list of {@link SdkRepository#NODES} to limit the type of packages
     *   we can update. A null or empty list means to update everything possible.
     * @param includeObsoletes True to also list and install obsolete packages.
     * @param dryMode True to check what would be updated/installed but do not actually
     *   download or install anything.
     */
    public void updateOrInstallAll_NoGUI(
            Collection<String> pkgFilter,
            boolean includeObsoletes,
            boolean dryMode) {

        refreshSources(true);

        UpdaterLogic ul = new UpdaterLogic();
        ArrayList<ArchiveInfo> archives = ul.computeUpdates(
                null /*selectedArchives*/,
                getSources(),
                getLocalSdkParser().getPackages());

        ul.addNewPlatforms(archives, getSources(), getLocalSdkParser().getPackages());

        // Filter the selected archives to only keep the ones matching the filter
        if (pkgFilter != null && pkgFilter.size() > 0 && archives != null && archives.size() > 0) {
            // Map filter types to an SdkRepository Package type.
            HashMap<String, Class<? extends Package>> pkgMap =
                new HashMap<String, Class<? extends Package>>();
            pkgMap.put(SdkRepository.NODE_PLATFORM, PlatformPackage.class);
            pkgMap.put(SdkRepository.NODE_ADD_ON,   AddonPackage.class);
            pkgMap.put(SdkRepository.NODE_TOOL,     ToolPackage.class);
            pkgMap.put(SdkRepository.NODE_DOC,      DocPackage.class);
            pkgMap.put(SdkRepository.NODE_SAMPLE,   SamplePackage.class);
            pkgMap.put(SdkRepository.NODE_EXTRA,    ExtraPackage.class);

            if (SdkRepository.NODES.length != pkgMap.size()) {
                // Sanity check in case we forget to update this package map.
                // We don't cancel the operation though.
                mSdkLog.error(null,
                    "Filter Mismatch!\nThe package filter list has changed. Please report this.");
            }

            // Now make a set of the types that are allowed by the filter.
            HashSet<Class<? extends Package>> allowedPkgSet =
                new HashSet<Class<? extends Package>>();
            for (String type : pkgFilter) {
                if (pkgMap.containsKey(type)) {
                    allowedPkgSet.add(pkgMap.get(type));
                } else {
                    // This should not happen unless there's a mismatch in the package map.
                    mSdkLog.error(null, "Ignoring unknown package filter '%1$s'", type);
                }
            }

            // we don't need the map anymore
            pkgMap = null;

            Iterator<ArchiveInfo> it = archives.iterator();
            while (it.hasNext()) {
                boolean keep = false;
                ArchiveInfo ai = it.next();
                Archive a = ai.getNewArchive();
                if (a != null) {
                    Package p = a.getParentPackage();
                    if (p != null && allowedPkgSet.contains(p.getClass())) {
                        keep = true;
                    }
                }

                if (!keep) {
                    it.remove();
                }
            }

            if (archives.size() == 0) {
                mSdkLog.warning("The package filter removed all packages. There is nothing to install.\n" +
                        "Please consider trying updating again without a package filter.");
                return;
            }
        }

        if (!includeObsoletes && archives != null && archives.size() > 0) {
            // Filter obsolete packages out
            Iterator<ArchiveInfo> it = archives.iterator();
            while (it.hasNext()) {
                boolean keep = false;
                ArchiveInfo ai = it.next();
                Archive a = ai.getNewArchive();
                if (a != null) {
                    Package p = a.getParentPackage();
                    if (p != null && !p.isObsolete()) {
                        keep = true;
                    }
                }

                if (!keep) {
                    it.remove();
                }
            }

            if (archives.size() == 0) {
                mSdkLog.warning("All candidate packages were obsolete. Nothing to install.");
                return;
            }
        }

        // TODO if selectedArchives is null and archives.len==0, find if there are
        // any new platform we can suggest to install instead.

        if (archives != null && archives.size() > 0) {
            if (dryMode) {
                mSdkLog.printf("Packages selected for install:\n");
                for (ArchiveInfo ai : archives) {
                    Archive a = ai.getNewArchive();
                    if (a != null) {
                        Package p = a.getParentPackage();
                        if (p != null) {
                            mSdkLog.printf("- %1$s\n", p.getShortDescription());
                        }
                    }
                }
                mSdkLog.printf("\nDry mode is on so nothing will actually be installed.\n");
            } else {
                installArchives(archives);
            }
        } else {
            mSdkLog.printf("There is nothing to install or update.\n");
        }
    }

    /**
     * Refresh all sources. This is invoked either internally (reusing an existing monitor)
     * or as a UI callback on the remote page "Refresh" button (in which case the monitor is
     * null and a new task should be created.)
     *
     * @param forceFetching When true, load sources that haven't been loaded yet.
     *                      When false, only refresh sources that have been loaded yet.
     */
    public void refreshSources(final boolean forceFetching) {
        assert mTaskFactory != null;

        final boolean forceHttp = getSettingsController().getForceHttp();

        mTaskFactory.start("Refresh Sources", new ITask() {
            public void run(ITaskMonitor monitor) {
                RepoSource[] sources = mSources.getSources();
                monitor.setProgressMax(sources.length);
                for (RepoSource source : sources) {
                    if (forceFetching ||
                            source.getPackages() != null ||
                            source.getFetchError() != null) {
                        source.load(monitor.createSubMonitor(1), forceHttp);
                    }
                    monitor.incProgress(1);
                }
            }
        });
    }
}
