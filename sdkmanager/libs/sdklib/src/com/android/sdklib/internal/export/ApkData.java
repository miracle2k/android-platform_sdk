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

package com.android.sdklib.internal.export;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;

/**
 * Class representing one apk that needs to be generated. This contains
 * which project it must be created from, and which filters should be used.
 *
 * This class is meant to be sortable in a way that allows generation of the buildInfo
 * value that goes in the composite versionCode.
 */
public class ApkData implements Comparable<ApkData> {

    private final static int INDEX_OUTPUTNAME = 0;
    private final static int INDEX_PROJECT    = 1;
    private final static int INDEX_MINOR      = 2;
    private final static int INDEX_MINSDK     = 3;
    private final static int INDEX_ABI        = 4;
    private final static int INDEX_OPENGL     = 5;
    private final static int INDEX_SCREENSIZE = 6;
    private final static int INDEX_LOCALES    = 7;
    private final static int INDEX_DENSITY    = 8;
    private final static int INDEX_MAX        = 9;

    private String mOutputName;
    private String mRelativePath;
    private File mProject;
    private int mBuildInfo;
    private int mMinor;

    // the following are used to sort the export data and generate buildInfo
    private int mMinSdkVersion;
    private String mAbi;
    private int mGlVersion;
    // screen size?

    public ApkData() {
        // do nothing.
    }

    public ApkData(ApkData data) {
        mRelativePath = data.mRelativePath;
        mProject = data.mProject;
        mBuildInfo = data.mBuildInfo;
        mMinor = data.mBuildInfo;
        mMinSdkVersion = data.mMinSdkVersion;
        mAbi = data.mAbi;
        mGlVersion = data.mGlVersion;
    }

    public String getOutputName() {
        return mOutputName;
    }

    public void setOutputName(String outputName) {
        mOutputName = outputName;
    }

    public String getRelativePath() {
        return mRelativePath;
    }

    public void setRelativePath(String relativePath) {
        mRelativePath = relativePath;
    }

    public File getProject() {
        return mProject;
    }

    public void setProject(File project) {
        mProject = project;
    }

    public int getBuildInfo() {
        return mBuildInfo;
    }

    public void setBuildInfo(int buildInfo) {
        mBuildInfo = buildInfo;
    }

    public int getMinor() {
        return mMinor;
    }

    public void setMinor(int minor) {
        mMinor = minor;
    }

    public int getMinSdkVersion() {
        return mMinSdkVersion;
    }

    public void setMinSdkVersion(int minSdkVersion) {
        mMinSdkVersion = minSdkVersion;
    }

    public String getAbi() {
        return mAbi;
    }

    public void setAbi(String abi) {
        mAbi = abi;
    }

    public int getGlVersion() {
        return mGlVersion;
    }

    public void setGlVersion(int glVersion) {
        mGlVersion = glVersion;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(mOutputName);
        sb.append(" / ").append(mRelativePath);
        sb.append(" / ").append(mBuildInfo);
        sb.append(" / ").append(mMinor);
        sb.append(" / ").append(mMinSdkVersion);
        sb.append(" / ").append(mAbi);

        return sb.toString();
    }

    public int compareTo(ApkData o) {
        int minSdkDiff = mMinSdkVersion - o.mMinSdkVersion;
        if (minSdkDiff != 0) {
            return minSdkDiff;
        }

        if (mAbi != null) {
            if (o.mAbi != null) {
                return mAbi.compareTo(o.mAbi);
            } else {
                return -1;
            }
        } else if (o.mAbi != null) {
            return 1;
        }

        if (mGlVersion != 0) {
            if (o.mGlVersion != 0) {
                return mGlVersion - o.mGlVersion;
            } else {
                return -1;
            }
        } else if (o.mGlVersion != 0) {
            return 1;
        }

        return 0;
    }

    /**
     * Writes the apk description in the given writer. a single line is used to write
     * everything.
     * @param writer The {@link OutputStreamWriter} to write to.
     * @throws IOException
     *
     * @see {@link #read(String)}
     */
    public void write(OutputStreamWriter writer) throws IOException {
        for (int i = 0 ; i < ApkData.INDEX_MAX ; i++) {
            write(i, writer);
        }
    }

    /**
     * reads the apk description from a log line.
     * @param line The fields to read, comma-separated.
     *
     * @see #write(FileWriter)
     */
    public void read(String line) {
        String[] dataStrs = line.split(",");
        for (int i = 0 ; i < ApkData.INDEX_MAX ; i++) {
            read(i, dataStrs);
        }
    }

    private void write(int index, OutputStreamWriter writer) throws IOException {
        switch (index) {
            case INDEX_OUTPUTNAME:
                writeValue(writer, mOutputName);
                break;
            case INDEX_PROJECT:
                writeValue(writer, mRelativePath);
                break;
            case INDEX_MINOR:
                writeValue(writer, mMinor);
                break;
            case INDEX_MINSDK:
                writeValue(writer, mMinSdkVersion);
                break;
            case INDEX_ABI:
                writeValue(writer, mAbi != null ? mAbi : "");
                break;
        }
    }

    private void read(int index, String[] data) {
        switch (index) {
            case INDEX_OUTPUTNAME:
                mOutputName = data[index];
                break;
            case INDEX_PROJECT:
                mRelativePath = data[index];
                break;
            case INDEX_MINOR:
                mMinor = Integer.parseInt(data[index]);
                break;
            case INDEX_MINSDK:
                mMinSdkVersion = Integer.parseInt(data[index]);
                break;
            case INDEX_ABI:
                if (index < data.length && data[index].length() > 0) {
                    mAbi = data[index];
                }
                break;
        }
    }

    private static void writeValue(OutputStreamWriter writer, String value) throws IOException {
        writer.append(value).append(',');
    }

    private static void writeValue(OutputStreamWriter writer, int value) throws IOException {
        writeValue(writer, Integer.toString(value));
    }
}
