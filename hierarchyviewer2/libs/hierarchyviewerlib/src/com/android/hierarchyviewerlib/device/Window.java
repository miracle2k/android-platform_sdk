/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.hierarchyviewerlib.device;

import com.android.ddmlib.IDevice;

/**
 * Used for storing a window from the window manager service on the device.
 * These are the windows that the device selector shows.
 */
public class Window {

    private String mTitle;

    private int mHashCode;

    private IDevice mDevice;

    public Window(IDevice device, String title, int hashCode) {
        this.mDevice = device;
        this.mTitle = title;
        this.mHashCode = hashCode;
    }

    public String getTitle() {
        return mTitle;
    }

    public int getHashCode() {
        return mHashCode;
    }

    public String encode() {
        return Integer.toHexString(mHashCode);
    }

    @Override
    public String toString() {
        return mTitle;
    }

    public IDevice getDevice() {
        return mDevice;
    }

    public static Window getFocusedWindow(IDevice device) {
        return new Window(device, "<Focused Window>", -1);
    }

    /*
     * After each refresh of the windows in the device selector, the windows are
     * different instances and automatically reselecting the same window doesn't
     * work in the device selector unless the equals method is defined here.
     */
    @Override
    public boolean equals(Object other) {
        if (other instanceof Window) {
            return mHashCode == ((Window) other).mHashCode
                    && mDevice.getSerialNumber().equals(((Window) other).mDevice.getSerialNumber());
        }
        return false;
    }
}
