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

package com.android.ide.eclipse.adt.internal.project;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.MultiLineReceiver;
import com.android.ddmlib.AndroidDebugBridge.IDebugBridgeChangeListener;
import com.android.ddmlib.AndroidDebugBridge.IDeviceChangeListener;
import com.android.ide.eclipse.adt.internal.resources.manager.GlobalProjectMonitor;
import com.android.ide.eclipse.adt.internal.resources.manager.GlobalProjectMonitor.IProjectListener;

import org.eclipse.core.resources.IProject;

import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;

/**
 * Registers which apk was installed on which device.
 * <p/>
 * The goal of this class is to remember the installation of APKs on devices, and provide
 * information about whether a new APK should be installed on a device prior to running the
 * application from a launch configuration.
 * <p/>
 * The manager uses {@link IProject} and {@link IDevice} to identify the target device and the
 * (project generating the) APK. This ensures that disconnected and reconnected devices will
 * always receive new APKs (since the APK could be uninstalled manually).
 * <p/>
 * Manually uninstalling an APK from a connected device will still be a problem, but this should
 * be a limited use case.
 * <p/>
 * This is a singleton. To get the instance, use {@link #getInstance()}
 */
public class ApkInstallManager implements IDeviceChangeListener, IDebugBridgeChangeListener,
        IProjectListener {

    private final static ApkInstallManager sThis = new ApkInstallManager();

    /**
     * Internal struct to associate a project and a device.
     */
    private final static class ApkInstall {
        public ApkInstall(IProject project, String packageName, IDevice device) {
            this.project = project;
            this.packageName = packageName;
            this.device = device;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof ApkInstall) {
                ApkInstall apkObj = (ApkInstall)obj;

                return (device == apkObj.device && project.equals(apkObj.project) &&
                        packageName.equals(apkObj.packageName));
            }

            return false;
        }

        @Override
        public int hashCode() {
            return (device.getSerialNumber() + project.getName() + packageName).hashCode();
        }

        final IProject project;
        final String packageName;
        final IDevice device;
    }

    private final static class PmReceiver extends MultiLineReceiver {
        boolean foundPackage = false;
        @Override
        public void processNewLines(String[] lines) {
            // if the package if found, then pm will show a line starting with "package:/"
            if (foundPackage == false) { // just in case this is called several times for multilines
                for (String line : lines) {
                    if (line.startsWith("package:/")) {
                        foundPackage = true;
                        break;
                    }
                }
            }
        }

        public boolean isCancelled() {
            return false;
        }
    }

    /**
     * Hashset of the list of installed package. Hashset used to ensure we don't re-add new
     * objects for the same app.
     */
    private final HashSet<ApkInstall> mInstallList = new HashSet<ApkInstall>();

    public static ApkInstallManager getInstance() {
        return sThis;
    }

    /**
     * Registers an installation of <var>project</var> onto <var>device</var>
     * @param project The project that was installed.
     * @param packageName the package name of the apk
     * @param device The device that received the installation.
     */
    public void registerInstallation(IProject project, String packageName, IDevice device) {
        synchronized (mInstallList) {
            mInstallList.add(new ApkInstall(project, packageName, device));
        }
    }

    /**
     * Returns whether a <var>project</var> was installed on the <var>device</var>.
     * @param project the project that may have been installed.
     * @param device the device that may have received the installation.
     * @return
     */
    public boolean isApplicationInstalled(IProject project, String packageName, IDevice device) {
        synchronized (mInstallList) {
            ApkInstall found = null;
            for (ApkInstall install : mInstallList) {
                if (project.equals(install.project) && packageName.equals(install.packageName) &&
                        device == install.device) {
                    found = install;
                    break;
                }
            }

            // check the app is still installed.
            if (found != null) {
                try {
                    PmReceiver receiver = new PmReceiver();
                    found.device.executeShellCommand("pm path " + packageName, receiver);
                    if (receiver.foundPackage == false) {
                        mInstallList.remove(found);
                    }

                    return receiver.foundPackage;
                } catch (IOException e) {
                    // failed to query pm? force reinstall.
                    return false;
                }
            }
        }
        return false;
    }

    /**
     * Resets registered installations for a specific {@link IProject}.
     * <p/>This ensures that {@link #isApplicationInstalled(IProject, IDevice)} will always return
     * <code>null</code> for this specified project, for any device.
     * @param project the project for which to reset all installations.
     */
    public void resetInstallationFor(IProject project) {
        synchronized (mInstallList) {
            Iterator<ApkInstall> iterator = mInstallList.iterator();
            while (iterator.hasNext()) {
                ApkInstall install = iterator.next();
                if (install.project.equals(project)) {
                    iterator.remove();
                }
            }
        }
    }

    private ApkInstallManager() {
        AndroidDebugBridge.addDeviceChangeListener(this);
        AndroidDebugBridge.addDebugBridgeChangeListener(this);
        GlobalProjectMonitor.getMonitor().addProjectListener(this);
    }

    /*
     * Responds to a bridge change by clearing the full installation list.
     * (non-Javadoc)
     * @see com.android.ddmlib.AndroidDebugBridge.IDebugBridgeChangeListener#bridgeChanged(com.android.ddmlib.AndroidDebugBridge)
     */
    public void bridgeChanged(AndroidDebugBridge bridge) {
        // the bridge changed, there is no way to know which IDevice will be which.
        // We reset everything
        synchronized (mInstallList) {
            mInstallList.clear();
        }
    }

    /*
     * Responds to a device being disconnected by removing all installations related to this device.
     * (non-Javadoc)
     * @see com.android.ddmlib.AndroidDebugBridge.IDeviceChangeListener#deviceDisconnected(com.android.ddmlib.Device)
     */
    public void deviceDisconnected(IDevice device) {
        synchronized (mInstallList) {
            Iterator<ApkInstall> iterator = mInstallList.iterator();
            while (iterator.hasNext()) {
                ApkInstall install = iterator.next();
                if (install.device == device) {
                    iterator.remove();
                }
            }
        }
    }

    /*
     * Responds to a close project by resetting all its installation.
     * (non-Javadoc)
     * @see com.android.ide.eclipse.editors.resources.manager.ResourceMonitor.IProjectListener#projectClosed(org.eclipse.core.resources.IProject)
     */
    public void projectClosed(IProject project) {
        resetInstallationFor(project);
    }

    /*
     * Responds to a close project by resetting all its installation.
     * (non-Javadoc)
     * @see com.android.ide.eclipse.editors.resources.manager.ResourceMonitor.IProjectListener#projectDeleted(org.eclipse.core.resources.IProject)
     */
    public void projectDeleted(IProject project) {
        resetInstallationFor(project);
    }

    /*
     * Does nothing
     * (non-Javadoc)
     * @see com.android.ddmlib.AndroidDebugBridge.IDeviceChangeListener#deviceChanged(com.android.ddmlib.Device, int)
     */
    public void deviceChanged(IDevice device, int changeMask) {
        // nothing to do.
    }

    /*
     * Does nothing
     * (non-Javadoc)
     * @see com.android.ddmlib.AndroidDebugBridge.IDeviceChangeListener#deviceConnected(com.android.ddmlib.Device)
     */
    public void deviceConnected(IDevice device) {
        // nothing to do.
    }

    /*
     * Does nothing
     * (non-Javadoc)
     * @see com.android.ide.eclipse.editors.resources.manager.ResourceMonitor.IProjectListener#projectOpened(org.eclipse.core.resources.IProject)
     */
    public void projectOpened(IProject project) {
        // nothing to do.
    }

    /*
     * Does nothing
     * (non-Javadoc)
     * @see com.android.ide.eclipse.editors.resources.manager.ResourceMonitor.IProjectListener#projectOpenedWithWorkspace(org.eclipse.core.resources.IProject)
     */
    public void projectOpenedWithWorkspace(IProject project) {
        // nothing to do.
    }
}
