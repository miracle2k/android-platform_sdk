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

import com.android.resources.Density;
import com.android.resources.ScreenSize;

/**
 * Base class for rendering parameters. This include the generic parameters but not what needs
 * to be rendered or additional parameters.
 *
 */
public abstract class RenderParams {

    public final static long DEFAULT_TIMEOUT = 250; //ms

    private final Object mProjectKey;
    private final int mScreenWidth;
    private final int mScreenHeight;
    private final Density mDensity;
    private final float mXdpi;
    private final float mYdpi;
    private final RenderResources mRenderResources;
    private final IProjectCallback mProjectCallback;
    private final int mMinSdkVersion;
    private final int mTargetSdkVersion;
    private final LayoutLog mLog;

    private boolean mCustomBackgroundEnabled;
    private int mCustomBackgroundColor;
    private long mTimeout;

    private IImageFactory mImageFactory = null;

    private ScreenSize mConfigScreenSize = null;
    private String mAppIcon = null;
    private String mAppLabel = null;
    private String mLocale = null;
    private boolean mForceNoDecor;

    /**
     *
     * @param projectKey An Object identifying the project. This is used for the cache mechanism.
     * @param screenWidth the screen width
     * @param screenHeight the screen height
     * @param density the density factor for the screen.
     * @param xdpi the screen actual dpi in X
     * @param ydpi the screen actual dpi in Y
     * @param themeName The name of the theme to use.
     * @param isProjectTheme true if the theme is a project theme, false if it is a framework theme.
     * @param projectResources the resources of the project. The map contains (String, map) pairs
     * where the string is the type of the resource reference used in the layout file, and the
     * map contains (String, {@link ResourceValue}) pairs where the key is the resource name,
     * and the value is the resource value.
     * @param frameworkResources the framework resources. The map contains (String, map) pairs
     * where the string is the type of the resource reference used in the layout file, and the map
     * contains (String, {@link ResourceValue}) pairs where the key is the resource name, and the
     * value is the resource value.
     * @param projectCallback The {@link IProjectCallback} object to get information from
     * the project.
     * @param minSdkVersion the minSdkVersion of the project
     * @param targetSdkVersion the targetSdkVersion of the project
     * @param log the object responsible for displaying warning/errors to the user.
     */
    public RenderParams(
            Object projectKey,
            int screenWidth, int screenHeight,
            Density density, float xdpi, float ydpi,
            RenderResources renderResources,
            IProjectCallback projectCallback,
            int minSdkVersion, int targetSdkVersion,
            LayoutLog log) {
        mProjectKey = projectKey;
        mScreenWidth = screenWidth;
        mScreenHeight = screenHeight;
        mDensity = density;
        mXdpi = xdpi;
        mYdpi = ydpi;
        mRenderResources = renderResources;
        mProjectCallback = projectCallback;
        mMinSdkVersion = minSdkVersion;
        mTargetSdkVersion = targetSdkVersion;
        mLog = log;
        mCustomBackgroundEnabled = false;
        mTimeout = DEFAULT_TIMEOUT;
    }

    /**
     * Copy constructor.
     */
    public RenderParams(RenderParams params) {
        mProjectKey = params.mProjectKey;
        mScreenWidth = params.mScreenWidth;
        mScreenHeight = params.mScreenHeight;
        mDensity = params.mDensity;
        mXdpi = params.mXdpi;
        mYdpi = params.mYdpi;
        mRenderResources = params.mRenderResources;
        mProjectCallback = params.mProjectCallback;
        mMinSdkVersion = params.mMinSdkVersion;
        mTargetSdkVersion = params.mTargetSdkVersion;
        mLog = params.mLog;
        mCustomBackgroundEnabled = params.mCustomBackgroundEnabled;
        mCustomBackgroundColor = params.mCustomBackgroundColor;
        mTimeout = params.mTimeout;
        mImageFactory = params.mImageFactory;
        mConfigScreenSize = params.mConfigScreenSize;
        mAppIcon = params.mAppIcon;
        mAppLabel = params.mAppLabel;
        mLocale = params.mLocale;
        mForceNoDecor = params.mForceNoDecor;
    }

    public void setOverrideBgColor(int color) {
        mCustomBackgroundEnabled = true;
        mCustomBackgroundColor = color;
    }

    public void setTimeout(long timeout) {
        mTimeout = timeout;
    }

    public void setImageFactory(IImageFactory imageFactory) {
        mImageFactory = imageFactory;
    }

    public void setConfigScreenSize(ScreenSize size) {
        mConfigScreenSize  = size;
    }

    public void setAppIcon(String appIcon) {
        mAppIcon = appIcon;
    }

    public void setAppLabel(String appLabel) {
        mAppLabel = appLabel;
    }

    public void setLocale(String locale) {
        mLocale = locale;
    }

    public void setForceNoDecor() {
        mForceNoDecor = true;
    }

    public Object getProjectKey() {
        return mProjectKey;
    }

    public int getMinSdkVersion() {
        return mMinSdkVersion;
    }

    public int getTargetSdkVersion() {
        return mTargetSdkVersion;
    }

    public int getScreenWidth() {
        return mScreenWidth;
    }

    public int getScreenHeight() {
        return mScreenHeight;
    }

    public Density getDensity() {
        return mDensity;
    }

    public float getXdpi() {
        return mXdpi;
    }

    public float getYdpi() {
        return mYdpi;
    }

    public RenderResources getResources() {
        return mRenderResources;
    }

    public IProjectCallback getProjectCallback() {
        return mProjectCallback;
    }

    public LayoutLog getLog() {
        return mLog;
    }

    public boolean isBgColorOverridden() {
        return mCustomBackgroundEnabled;
    }

    public int getOverrideBgColor() {
        return mCustomBackgroundColor;
    }

    public long getTimeout() {
        return mTimeout;
    }

    public IImageFactory getImageFactory() {
        return mImageFactory;
    }

    public ScreenSize getConfigScreenSize() {
        return mConfigScreenSize;
    }

    public String getAppIcon() {
        return mAppIcon;
    }

    public String getAppLabel() {
        return mAppLabel;
    }

    public String getLocale() {
        return mLocale;
    }

    public boolean isForceNoDecor() {
        return mForceNoDecor;
    }
}
