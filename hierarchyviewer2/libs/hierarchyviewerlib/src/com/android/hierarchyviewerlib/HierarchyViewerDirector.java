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

package com.android.hierarchyviewerlib;

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.Log;
import com.android.ddmlib.RawImage;
import com.android.ddmlib.TimeoutException;
import com.android.ddmlib.AndroidDebugBridge.IDeviceChangeListener;
import com.android.hierarchyviewerlib.device.DeviceBridge;
import com.android.hierarchyviewerlib.device.ViewNode;
import com.android.hierarchyviewerlib.device.Window;
import com.android.hierarchyviewerlib.device.WindowUpdater;
import com.android.hierarchyviewerlib.device.DeviceBridge.ViewServerInfo;
import com.android.hierarchyviewerlib.device.WindowUpdater.IWindowChangeListener;

import java.io.IOException;

/**
 * This is the class where most of the logic resides.
 */
public abstract class HierarchyViewerDirector implements IDeviceChangeListener,
        IWindowChangeListener {

    public static final String TAG = "hierarchyviewer";

    private int pixelPerfectRefreshesInProgress = 0;

    public void terminate() {
        WindowUpdater.terminate();
    }

    public abstract String getAdbLocation();

    public void initDebugBridge() {
        DeviceBridge.initDebugBridge(getAdbLocation());
    }

    public void stopDebugBridge() {
        DeviceBridge.terminate();
    }

    public void populateDeviceSelectionModel() {
        IDevice[] devices = DeviceBridge.getDevices();
        for (IDevice device : devices) {
            deviceConnected(device);
        }
    }

    public void startListenForDevices() {
        DeviceBridge.startListenForDevices(this);
    }

    public void stopListenForDevices() {
        DeviceBridge.stopListenForDevices(this);
    }

    public abstract void executeInBackground(Runnable task);

    public void deviceConnected(final IDevice device) {
        if (device.isOnline()) {
            DeviceBridge.setupDeviceForward(device);
            if (!DeviceBridge.isViewServerRunning(device)) {
                if (!DeviceBridge.startViewServer(device)) {
                    // Let's do something interesting here... Try again in 2
                    // seconds.
                    executeInBackground(new Runnable() {
                        public void run() {
                            try {
                                Thread.sleep(2000);
                            } catch (InterruptedException e) {
                                Log.e(TAG, "Unable to debug device " + device);
                                DeviceBridge.removeDeviceForward(device);
                                return;
                            }
                            if (!DeviceBridge.startViewServer(device)) {
                                Log.e(TAG, "Unable to debug device " + device);
                                DeviceBridge.removeDeviceForward(device);
                            } else {
                                loadViewServerInfoAndWindows(device);
                            }
                        }
                    });
                    return;
                }
            }
            loadViewServerInfoAndWindows(device);
        }
    }

    private void loadViewServerInfoAndWindows(final IDevice device) {
        executeInBackground(new Runnable() {
            public void run() {
                ViewServerInfo viewServerInfo = DeviceBridge.loadViewServerInfo(device);
                if (viewServerInfo == null) {
                    return;
                }
                Window[] windows = DeviceBridge.loadWindows(device);
                ComponentRegistry.getDeviceSelectionModel().addDevice(device, windows);
                if (viewServerInfo.protocolVersion >= 3) {
                    WindowUpdater.startListenForWindowChanges(HierarchyViewerDirector.this, device);
                    focusChanged(device);
                }
            }
        });
    }

    public void deviceDisconnected(IDevice device) {
        ViewServerInfo viewServerInfo = DeviceBridge.getViewServerInfo(device);
        if (viewServerInfo == null) {
            return;
        }
        if (viewServerInfo.protocolVersion >= 3) {
            WindowUpdater.stopListenForWindowChanges(this, device);
        }
        DeviceBridge.removeDeviceForward(device);
        DeviceBridge.removeViewServerInfo(device);
        ComponentRegistry.getDeviceSelectionModel().removeDevice(device);
        if (ComponentRegistry.getPixelPerfectModel().getDevice() == device) {
            ComponentRegistry.getPixelPerfectModel().setData(null, null, null);
        }
    }

    public void deviceChanged(IDevice device, int changeMask) {
        if ((changeMask & IDevice.CHANGE_STATE) != 0 && device.isOnline()) {
            deviceConnected(device);
        }
    }

    public void windowsChanged(final IDevice device) {
        executeInBackground(new Runnable() {
            public void run() {
                Window[] windows = DeviceBridge.loadWindows(device);
                ComponentRegistry.getDeviceSelectionModel().updateDevice(device, windows);
            }
        });
    }

    public void focusChanged(final IDevice device) {
        executeInBackground(new Runnable() {
            public void run() {
                int focusedWindow = DeviceBridge.getFocusedWindow(device);
                ComponentRegistry.getDeviceSelectionModel().updateFocusedWindow(device,
                        focusedWindow);
            }
        });

        // Some interesting logic here. We don't want to refresh the pixel
        // perfect view 1000 times in a row if the focus keeps changing. We just
        // want it to refresh following the last focus change.
        boolean proceed = false;
        synchronized (this) {
            if (pixelPerfectRefreshesInProgress <= 1) {
                proceed = true;
                pixelPerfectRefreshesInProgress++;
            }
        }
        if (proceed) {
            executeInBackground(new Runnable() {
                public void run() {
                    if (ComponentRegistry.getDeviceSelectionModel().getFocusedWindow(device) != -1
                            && device == ComponentRegistry.getPixelPerfectModel().getDevice()) {
                        try {
                            ViewNode viewNode =
                                    DeviceBridge.loadWindowData(Window.getFocusedWindow(device));
                            RawImage screenshot = device.getScreenshot();
                            ComponentRegistry.getPixelPerfectModel().setFocusData(screenshot,
                                    viewNode);
                        } catch (IOException e) {
                            Log.e(TAG, "Unable to load screenshot from device " + device);
                        } catch (TimeoutException e) {
                            Log.e(TAG, "Timeout loading screenshot from device " + device);
                        } catch (AdbCommandRejectedException e) {
                            Log.e(TAG, "Adb rejected command to load screenshot from device "
                                    + device);
                        }
                    }
                    synchronized (HierarchyViewerDirector.this) {
                        pixelPerfectRefreshesInProgress--;
                    }
                }

            });
        }
    }

    public void loadPixelPerfectData(final IDevice device) {
        executeInBackground(new Runnable() {
            public void run() {
                try {
                    RawImage screenshot = device.getScreenshot();
                    ViewNode viewNode =
                            DeviceBridge.loadWindowData(Window.getFocusedWindow(device));
                    ComponentRegistry.getPixelPerfectModel().setData(device, screenshot, viewNode);
                } catch (IOException e) {
                    Log.e(TAG, "Unable to load screenshot from device " + device);
                } catch (TimeoutException e) {
                    Log.e(TAG, "Timeout loading screenshot from device " + device);
                } catch (AdbCommandRejectedException e) {
                    Log.e(TAG, "Adb rejected command to load screenshot from device " + device);
                }
            }
        });
    }

    public void loadViewTreeData(final Window window) {
        executeInBackground(new Runnable() {
            public void run() {

                ViewNode viewNode = DeviceBridge.loadWindowData(window);
                if (viewNode != null) {
                    DeviceBridge.loadProfileData(window, viewNode);
                    ComponentRegistry.getTreeViewModel().setData(window, viewNode);
                }
            }
        });
    }
}
