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

package com.android.ide.common.rendering.api;

/**
 * Enum representing the density class of Android resources.
 */
public enum ResourceDensity {
    XHIGH(320),
    HIGH(240),
    MEDIUM(160),
    LOW(120),
    NODPI(0);

    public final static int DEFAULT_DENSITY = 160;

    private final int mDpi;

    ResourceDensity(int dpi) {
        mDpi = dpi;
    }

    /**
     * Returns the dot-per-inch value associated with the density.
     * @return the dpi value.
     */
    public int getDpi() {
        return mDpi;
    }

    /**
     * Returns the enum matching the given dpi.
     * @param dpi The dpi
     * @return the enum for the dpi or null if no match was found.
     */
    public static ResourceDensity getEnum(int dpi) {
        for (ResourceDensity d : values()) {
            if (d.mDpi == dpi) {
                return d;
            }
        }

        return null;
    }
}
