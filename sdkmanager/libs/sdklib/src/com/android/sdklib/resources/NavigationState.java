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

package com.android.sdklib.resources;

/**
 * Navigation state enum.
 * <p/>This is used in the resource folder names.
 */
public enum NavigationState {
    EXPOSED("navexposed", "Exposed"), //$NON-NLS-1$
    HIDDEN("navhidden", "Hidden");    //$NON-NLS-1$

    private String mValue;
    private String mDisplayValue;

    private NavigationState(String value, String displayValue) {
        mValue = value;
        mDisplayValue = displayValue;
    }

    /**
     * Returns the enum for matching the provided qualifier value.
     * @param value The qualifier value.
     * @return the enum for the qualifier value or null if no matching was found.
     */
    public static NavigationState getEnum(String value) {
        for (NavigationState state : values()) {
            if (state.mValue.equals(value)) {
                return state;
            }
        }

        return null;
    }

    public String getValue() {
        return mValue;
    }

    public String getDisplayValue() {
        return mDisplayValue;
    }

    public static int getIndex(NavigationState value) {
        int i = 0;
        for (NavigationState input : values()) {
            if (value == input) {
                return i;
            }

            i++;
        }

        return -1;
    }

    public static NavigationState getByIndex(int index) {
        int i = 0;
        for (NavigationState value : values()) {
            if (i == index) {
                return value;
            }
            i++;
        }
        return null;
    }
}
