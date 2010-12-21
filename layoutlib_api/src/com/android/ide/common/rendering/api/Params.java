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

import java.util.Map;

public class Params {

    public final static long DEFAULT_TIMEOUT = 250; //ms

    public static enum RenderingMode {
        NORMAL(false, false),
        V_SCROLL(false, true),
        H_SCROLL(true, false),
        FULL_EXPAND(true, true);

        private final boolean mHorizExpand;
        private final boolean mVertExpand;

        private RenderingMode(boolean horizExpand, boolean vertExpand) {
            mHorizExpand = horizExpand;
            mVertExpand = vertExpand;
        }

        public boolean isHorizExpand() {
            return mHorizExpand;
        }

        public boolean isVertExpand() {
            return mVertExpand;
        }
    }

    private ILayoutPullParser mLayoutDescription;
    private Object mProjectKey;
    private int mScreenWidth;
    private int mScreenHeight;
    private RenderingMode mRenderingMode;
    private int mDensity;
    private float mXdpi;
    private float mYdpi;
    private String mThemeName;
    private boolean mIsProjectTheme;
    private Map<String, Map<String, ResourceValue>> mProjectResources;
    private Map<String, Map<String, ResourceValue>> mFrameworkResources;
    private IProjectCallback mProjectCallback;
    private LayoutLog mLog;

    private boolean mCustomBackgroundEnabled;
    private int mCustomBackgroundColor;
    private long mTimeout;

    private IImageFactory mImageFactory = null;

    /**
     *
     * @param layoutDescription the {@link ILayoutPullParser} letting the LayoutLib Bridge visit the
     * layout file.
     * @param projectKey An Object identifying the project. This is used for the cache mechanism.
     * @param screenWidth the screen width
     * @param screenHeight the screen height
     * @param renderingMode The rendering mode.
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
     * @param log the object responsible for displaying warning/errors to the user.
     */
    public Params(ILayoutPullParser layoutDescription,
            Object projectKey,
            int screenWidth, int screenHeight, RenderingMode renderingMode,
            int density, float xdpi, float ydpi,
            String themeName, boolean isProjectTheme,
            Map<String, Map<String, ResourceValue>> projectResources,
            Map<String, Map<String, ResourceValue>> frameworkResources,
            IProjectCallback projectCallback, LayoutLog log) {
        mLayoutDescription = layoutDescription;
        mProjectKey = projectKey;
        mScreenWidth = screenWidth;
        mScreenHeight = screenHeight;
        mRenderingMode = renderingMode;
        mDensity = density;
        mXdpi = xdpi;
        mYdpi = ydpi;
        mThemeName = themeName;
        mIsProjectTheme = isProjectTheme;
        mProjectResources = projectResources;
        mFrameworkResources = frameworkResources;
        mProjectCallback = projectCallback;
        mLog = log;
        mCustomBackgroundEnabled = false;
        mTimeout = DEFAULT_TIMEOUT;
    }

    /**
     * Copy constructor.
     */
    public Params(Params params) {
        mLayoutDescription = params.mLayoutDescription;
        mProjectKey = params.mProjectKey;
        mScreenWidth = params.mScreenWidth;
        mScreenHeight = params.mScreenHeight;
        mRenderingMode = params.mRenderingMode;
        mDensity = params.mDensity;
        mXdpi = params.mXdpi;
        mYdpi = params.mYdpi;
        mThemeName = params.mThemeName;
        mIsProjectTheme = params.mIsProjectTheme;
        mProjectResources = params.mProjectResources;
        mFrameworkResources = params.mFrameworkResources;
        mProjectCallback = params.mProjectCallback;
        mLog = params.mLog;
        mCustomBackgroundEnabled = params.mCustomBackgroundEnabled;
        mCustomBackgroundColor = params.mCustomBackgroundColor;
        mTimeout = params.mTimeout;
        mImageFactory = params.mImageFactory;
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

    public ILayoutPullParser getLayoutDescription() {
        return mLayoutDescription;
    }

    public Object getProjectKey() {
        return mProjectKey;
    }

    public int getScreenWidth() {
        return mScreenWidth;
    }

    public int getScreenHeight() {
        return mScreenHeight;
    }

    public RenderingMode getRenderingMode() {
        return mRenderingMode;
    }

    public int getDensity() {
        return mDensity;
    }

    public float getXdpi() {
        return mXdpi;
    }

    public float getYdpi() {
        return mYdpi;
    }

    public String getThemeName() {
        return mThemeName;
    }

    public boolean isProjectTheme() {
        return mIsProjectTheme;
    }

    public Map<String, Map<String, ResourceValue>> getProjectResources() {
        return mProjectResources;
    }

    public Map<String, Map<String, ResourceValue>> getFrameworkResources() {
        return mFrameworkResources;
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
}
