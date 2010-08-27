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

package com.android.hierarchyviewerlib.models;

import com.android.ddmlib.IDevice;
import com.android.hierarchyviewerlib.device.Window;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * This class stores the list of windows for each connected device. It notifies
 * listeners of any changes as well as knows which window is currently selected
 * in the device selector.
 */
public class DeviceSelectionModel {

    private final HashMap<IDevice, Window[]> deviceMap = new HashMap<IDevice, Window[]>();

    private final HashMap<IDevice, Integer> focusedWindowHashes = new HashMap<IDevice, Integer>();

    private final ArrayList<IDevice> deviceList = new ArrayList<IDevice>();

    private final ArrayList<WindowChangeListener> windowChangeListeners =
            new ArrayList<WindowChangeListener>();

    private IDevice selectedDevice;

    private Window selectedWindow;

    private static DeviceSelectionModel model;

    public static DeviceSelectionModel getModel() {
        if (model == null) {
            model = new DeviceSelectionModel();
        }
        return model;
    }

    public void addDevice(IDevice device, Window[] windows) {
        synchronized (deviceMap) {
            deviceMap.put(device, windows);
            deviceList.add(device);
        }
        notifyDeviceConnected(device);
    }

    public void removeDevice(IDevice device) {
        boolean selectionChanged = false;
        synchronized (deviceMap) {
            deviceList.remove(device);
            if (!deviceList.contains(device)) {
                deviceMap.remove(device);
                focusedWindowHashes.remove(device);
                if (selectedDevice == device) {
                    selectedDevice = null;
                    selectedWindow = null;
                    selectionChanged = true;
                }
            }
        }
        notifyDeviceDisconnected(device);
        if (selectionChanged) {
            notifySelectionChanged(selectedDevice, selectedWindow);
        }
    }

    public void updateDevice(IDevice device, Window[] windows) {
        boolean selectionChanged = false;
        synchronized (deviceMap) {
            deviceMap.put(device, windows);
            // If the selected window no longer exists, we clear the selection.
            if (selectedDevice == device && selectedWindow != null) {
                boolean windowStillExists = false;
                for (int i = 0; i < windows.length && !windowStillExists; i++) {
                    if (windows[i].equals(selectedWindow)) {
                        windowStillExists = true;
                    }
                }
                if (!windowStillExists) {
                    selectedDevice = null;
                    selectedWindow = null;
                    selectionChanged = true;
                }
            }
        }
        notifyDeviceChanged(device);
        if (selectionChanged) {
            notifySelectionChanged(selectedDevice, selectedWindow);
        }
    }

    /*
     * Change which window has focus and notify the listeners.
     */
    public void updateFocusedWindow(IDevice device, int focusedWindow) {
        Integer oldValue = null;
        synchronized (deviceMap) {
            oldValue = focusedWindowHashes.put(device, new Integer(focusedWindow));
        }
        // Only notify if the values are different. It would be cool if Java
        // containers accepted basic types like int.
        if (oldValue == null || (oldValue != null && oldValue.intValue() != focusedWindow)) {
            notifyFocusChanged(device);
        }
    }

    public static interface WindowChangeListener {
        public void deviceConnected(IDevice device);

        public void deviceChanged(IDevice device);

        public void deviceDisconnected(IDevice device);

        public void focusChanged(IDevice device);

        public void selectionChanged(IDevice device, Window window);
    }

    private WindowChangeListener[] getWindowChangeListenerList() {
        WindowChangeListener[] listeners = null;
        synchronized (windowChangeListeners) {
            if (windowChangeListeners.size() == 0) {
                return null;
            }
            listeners =
                    windowChangeListeners.toArray(new WindowChangeListener[windowChangeListeners
                            .size()]);
        }
        return listeners;
    }

    private void notifyDeviceConnected(IDevice device) {
        WindowChangeListener[] listeners = getWindowChangeListenerList();
        if (listeners != null) {
            for (int i = 0; i < listeners.length; i++) {
                listeners[i].deviceConnected(device);
            }
        }
    }

    private void notifyDeviceChanged(IDevice device) {
        WindowChangeListener[] listeners = getWindowChangeListenerList();
        if (listeners != null) {
            for (int i = 0; i < listeners.length; i++) {
                listeners[i].deviceChanged(device);
            }
        }
    }

    private void notifyDeviceDisconnected(IDevice device) {
        WindowChangeListener[] listeners = getWindowChangeListenerList();
        if (listeners != null) {
            for (int i = 0; i < listeners.length; i++) {
                listeners[i].deviceDisconnected(device);
            }
        }
    }

    private void notifyFocusChanged(IDevice device) {
        WindowChangeListener[] listeners = getWindowChangeListenerList();
        if (listeners != null) {
            for (int i = 0; i < listeners.length; i++) {
                listeners[i].focusChanged(device);
            }
        }
    }

    private void notifySelectionChanged(IDevice device, Window window) {
        WindowChangeListener[] listeners = getWindowChangeListenerList();
        if (listeners != null) {
            for (int i = 0; i < listeners.length; i++) {
                listeners[i].selectionChanged(device, window);
            }
        }
    }

    public void addWindowChangeListener(WindowChangeListener listener) {
        synchronized (windowChangeListeners) {
            windowChangeListeners.add(listener);
        }
    }

    public void removeWindowChangeListener(WindowChangeListener listener) {
        synchronized (windowChangeListeners) {
            windowChangeListeners.remove(listener);
        }
    }

    public IDevice[] getDevices() {
        synchronized (deviceMap) {
            return deviceList.toArray(new IDevice[deviceList.size()]);
        }
    }

    public Window[] getWindows(IDevice device) {
        Window[] windows;
        synchronized (deviceMap) {
            windows = deviceMap.get(device);
        }
        return windows;
    }

    // Returns the window that currently has focus or -1. Note that this means
    // that a window with hashcode -1 gets highlighted. If you remember, this is
    // the infamous <Focused Window>
    public int getFocusedWindow(IDevice device) {
        synchronized (deviceMap) {
            Integer focusedWindow = focusedWindowHashes.get(device);
            if (focusedWindow == null) {
                return -1;
            }
            return focusedWindow.intValue();
        }
    }

    public void setSelection(IDevice device, Window window) {
        synchronized (deviceMap) {
            selectedDevice = device;
            selectedWindow = window;
        }
        notifySelectionChanged(device, window);
    }

    public IDevice getSelectedDevice() {
        synchronized (deviceMap) {
            return selectedDevice;
        }
    }

    public Window getSelectedWindow() {
        synchronized (deviceMap) {
            return selectedWindow;
        }
    }
}
