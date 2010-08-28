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

    private final HashMap<IDevice, Window[]> mDeviceMap = new HashMap<IDevice, Window[]>();

    private final HashMap<IDevice, Integer> mFocusedWindowHashes = new HashMap<IDevice, Integer>();

    private final ArrayList<IDevice> mDeviceList = new ArrayList<IDevice>();

    private final ArrayList<IWindowChangeListener> mWindowChangeListeners =
            new ArrayList<IWindowChangeListener>();

    private IDevice mSelectedDevice;

    private Window mSelectedWindow;

    private static DeviceSelectionModel sModel;

    public static DeviceSelectionModel getModel() {
        if (sModel == null) {
            sModel = new DeviceSelectionModel();
        }
        return sModel;
    }

    public boolean containsDevice(IDevice device) {
        synchronized (mDeviceMap) {
            return mDeviceMap.containsKey(device);
        }
    }

    public void addDevice(IDevice device, Window[] windows) {
        synchronized (mDeviceMap) {
            mDeviceMap.put(device, windows);
            mDeviceList.add(device);
        }
        notifyDeviceConnected(device);
    }

    public void removeDevice(IDevice device) {
        boolean selectionChanged = false;
        synchronized (mDeviceMap) {
            mDeviceList.remove(device);
            if (!mDeviceList.contains(device)) {
                mDeviceMap.remove(device);
                mFocusedWindowHashes.remove(device);
                if (mSelectedDevice == device) {
                    mSelectedDevice = null;
                    mSelectedWindow = null;
                    selectionChanged = true;
                }
            }
        }
        notifyDeviceDisconnected(device);
        if (selectionChanged) {
            notifySelectionChanged(mSelectedDevice, mSelectedWindow);
        }
    }

    public void updateDevice(IDevice device, Window[] windows) {
        boolean selectionChanged = false;
        synchronized (mDeviceMap) {
            mDeviceMap.put(device, windows);
            // If the selected window no longer exists, we clear the selection.
            if (mSelectedDevice == device && mSelectedWindow != null) {
                boolean windowStillExists = false;
                for (int i = 0; i < windows.length && !windowStillExists; i++) {
                    if (windows[i].equals(mSelectedWindow)) {
                        windowStillExists = true;
                    }
                }
                if (!windowStillExists) {
                    mSelectedDevice = null;
                    mSelectedWindow = null;
                    selectionChanged = true;
                }
            }
        }
        notifyDeviceChanged(device);
        if (selectionChanged) {
            notifySelectionChanged(mSelectedDevice, mSelectedWindow);
        }
    }

    /*
     * Change which window has focus and notify the listeners.
     */
    public void updateFocusedWindow(IDevice device, int focusedWindow) {
        Integer oldValue = null;
        synchronized (mDeviceMap) {
            oldValue = mFocusedWindowHashes.put(device, new Integer(focusedWindow));
        }
        // Only notify if the values are different. It would be cool if Java
        // containers accepted basic types like int.
        if (oldValue == null || (oldValue != null && oldValue.intValue() != focusedWindow)) {
            notifyFocusChanged(device);
        }
    }

    public static interface IWindowChangeListener {
        public void deviceConnected(IDevice device);

        public void deviceChanged(IDevice device);

        public void deviceDisconnected(IDevice device);

        public void focusChanged(IDevice device);

        public void selectionChanged(IDevice device, Window window);
    }

    private IWindowChangeListener[] getWindowChangeListenerList() {
        IWindowChangeListener[] listeners = null;
        synchronized (mWindowChangeListeners) {
            if (mWindowChangeListeners.size() == 0) {
                return null;
            }
            listeners =
                    mWindowChangeListeners.toArray(new IWindowChangeListener[mWindowChangeListeners
                            .size()]);
        }
        return listeners;
    }

    private void notifyDeviceConnected(IDevice device) {
        IWindowChangeListener[] listeners = getWindowChangeListenerList();
        if (listeners != null) {
            for (int i = 0; i < listeners.length; i++) {
                listeners[i].deviceConnected(device);
            }
        }
    }

    private void notifyDeviceChanged(IDevice device) {
        IWindowChangeListener[] listeners = getWindowChangeListenerList();
        if (listeners != null) {
            for (int i = 0; i < listeners.length; i++) {
                listeners[i].deviceChanged(device);
            }
        }
    }

    private void notifyDeviceDisconnected(IDevice device) {
        IWindowChangeListener[] listeners = getWindowChangeListenerList();
        if (listeners != null) {
            for (int i = 0; i < listeners.length; i++) {
                listeners[i].deviceDisconnected(device);
            }
        }
    }

    private void notifyFocusChanged(IDevice device) {
        IWindowChangeListener[] listeners = getWindowChangeListenerList();
        if (listeners != null) {
            for (int i = 0; i < listeners.length; i++) {
                listeners[i].focusChanged(device);
            }
        }
    }

    private void notifySelectionChanged(IDevice device, Window window) {
        IWindowChangeListener[] listeners = getWindowChangeListenerList();
        if (listeners != null) {
            for (int i = 0; i < listeners.length; i++) {
                listeners[i].selectionChanged(device, window);
            }
        }
    }

    public void addWindowChangeListener(IWindowChangeListener listener) {
        synchronized (mWindowChangeListeners) {
            mWindowChangeListeners.add(listener);
        }
    }

    public void removeWindowChangeListener(IWindowChangeListener listener) {
        synchronized (mWindowChangeListeners) {
            mWindowChangeListeners.remove(listener);
        }
    }

    public IDevice[] getDevices() {
        synchronized (mDeviceMap) {
            return mDeviceList.toArray(new IDevice[mDeviceList.size()]);
        }
    }

    public Window[] getWindows(IDevice device) {
        Window[] windows;
        synchronized (mDeviceMap) {
            windows = mDeviceMap.get(device);
        }
        return windows;
    }

    // Returns the window that currently has focus or -1. Note that this means
    // that a window with hashcode -1 gets highlighted. If you remember, this is
    // the infamous <Focused Window>
    public int getFocusedWindow(IDevice device) {
        synchronized (mDeviceMap) {
            Integer focusedWindow = mFocusedWindowHashes.get(device);
            if (focusedWindow == null) {
                return -1;
            }
            return focusedWindow.intValue();
        }
    }

    public void setSelection(IDevice device, Window window) {
        synchronized (mDeviceMap) {
            mSelectedDevice = device;
            mSelectedWindow = window;
        }
        notifySelectionChanged(device, window);
    }

    public IDevice getSelectedDevice() {
        synchronized (mDeviceMap) {
            return mSelectedDevice;
        }
    }

    public Window getSelectedWindow() {
        synchronized (mDeviceMap) {
            return mSelectedWindow;
        }
    }
}
