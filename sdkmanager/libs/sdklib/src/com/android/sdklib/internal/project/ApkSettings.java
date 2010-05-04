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

package com.android.sdklib.internal.project;


/**
 * Settings for multiple APK generation.
 */
public class ApkSettings {
    private boolean mSplitByDensity = false;
    private boolean mSplitByAbi = false;

    public ApkSettings() {
    }

    /**
     * Creates an ApkSettings and fills it from the project settings read from a
     * {@link ProjectProperties} file.
     */
    public ApkSettings(ProjectProperties properties) {
        mSplitByDensity = Boolean.parseBoolean(properties.getProperty(
                ProjectProperties.PROPERTY_SPLIT_BY_DENSITY));
        mSplitByAbi =  Boolean.parseBoolean(properties.getProperty(
                ProjectProperties.PROPERTY_SPLIT_BY_ABI));
    }

    /**
     * Indicates whether APKs should be generate for each dpi level.
     */
    public boolean isSplitByDensity() {
        return mSplitByDensity;
    }

    public void setSplitByDensity(boolean split) {
        mSplitByDensity = split;
    }

    public boolean isSplitByAbi() {
    	return mSplitByAbi;
    }

    public void setSplitByAbi(boolean split) {
    	mSplitByAbi = split;
    }

    /**
     * Writes the receiver into a {@link ProjectProperties}.
     * @param properties the {@link ProjectProperties} in which to store the settings.
     */
    public void write(ProjectProperties properties) {
        properties.setProperty(ProjectProperties.PROPERTY_SPLIT_BY_DENSITY,
                Boolean.toString(mSplitByDensity));
        properties.setProperty(ProjectProperties.PROPERTY_SPLIT_BY_ABI,
                Boolean.toString(mSplitByAbi));
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ApkSettings) {
            ApkSettings objSettings = (ApkSettings) obj;
            return mSplitByDensity == objSettings.mSplitByDensity &&
                    mSplitByAbi == objSettings.mSplitByAbi;
        }

        return false;
    }

    @Override
    public int hashCode() {
        return Integer.valueOf(
                (mSplitByDensity ? 1 : 0) +
                (mSplitByAbi ? 2 : 0)).hashCode();
    }
}
