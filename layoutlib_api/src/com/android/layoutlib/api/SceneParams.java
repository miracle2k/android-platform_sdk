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

package com.android.layoutlib.api;

import java.util.Map;

public class SceneParams {

    private IXmlPullParser mLayoutDescription;
    private Object mProjectKey;
    private int mScreenWidth;
    private int mScreenHeight;
    private boolean mRenderFullSize;
    private int mDensity;
    private float mXdpi;
    private float mYdpi;
    private String mThemeName;
    private boolean mIsProjectTheme;
    private Map<String, Map<String, IResourceValue>> mProjectResources;
    private Map<String, Map<String, IResourceValue>> mFrameworkResources;
    private IProjectCallback mProjectCallback;
    private ILayoutLog mLogger;
    private boolean mCustomBackgroundEnabled;
    private int mCustomBackgroundColor;

    /**
     *
     * @param layoutDescription the {@link IXmlPullParser} letting the LayoutLib Bridge visit the
     * layout file.
     * @param projectKey An Object identifying the project. This is used for the cache mechanism.
     * @param screenWidth the screen width
     * @param screenHeight the screen height
     * @param renderFullSize if true, the rendering will render the full size needed by the
     * layout. This size is never smaller than <var>screenWidth</var> x <var>screenHeight</var>.
     * @param density the density factor for the screen.
     * @param xdpi the screen actual dpi in X
     * @param ydpi the screen actual dpi in Y
     * @param themeName The name of the theme to use.
     * @param isProjectTheme true if the theme is a project theme, false if it is a framework theme.
     * @param projectResources the resources of the project. The map contains (String, map) pairs
     * where the string is the type of the resource reference used in the layout file, and the
     * map contains (String, {@link IResourceValue}) pairs where the key is the resource name,
     * and the value is the resource value.
     * @param frameworkResources the framework resources. The map contains (String, map) pairs
     * where the string is the type of the resource reference used in the layout file, and the map
     * contains (String, {@link IResourceValue}) pairs where the key is the resource name, and the
     * value is the resource value.
     * @param projectCallback The {@link IProjectCallback} object to get information from
     * the project.
     * @param logger the object responsible for displaying warning/errors to the user.
     */
    public SceneParams(IXmlPullParser layoutDescription,
            Object projectKey,
            int screenWidth, int screenHeight, boolean renderFullSize,
            int density, float xdpi, float ydpi,
            String themeName, boolean isProjectTheme,
            Map<String, Map<String, IResourceValue>> projectResources,
            Map<String, Map<String, IResourceValue>> frameworkResources,
            IProjectCallback projectCallback, ILayoutLog logger) {
        mLayoutDescription = layoutDescription;
        mProjectKey = projectKey;
        mScreenWidth = screenWidth;
        mScreenHeight = screenHeight;
        mRenderFullSize = renderFullSize;
        mDensity = density;
        mXdpi = xdpi;
        mYdpi = ydpi;
        mThemeName = themeName;
        mIsProjectTheme = isProjectTheme;
        mProjectResources = projectResources;
        mFrameworkResources = frameworkResources;
        mProjectCallback = projectCallback;
        mLogger = logger;
        mCustomBackgroundEnabled = false;
    }

    /**
     * Copy constructor.
     */
    public SceneParams(SceneParams params) {
        mLayoutDescription = params.mLayoutDescription;
        mProjectKey = params.mProjectKey;
        mScreenWidth = params.mScreenWidth;
        mScreenHeight = params.mScreenHeight;
        mRenderFullSize = params.mRenderFullSize;
        mDensity = params.mDensity;
        mXdpi = params.mXdpi;
        mYdpi = params.mYdpi;
        mThemeName = params.mThemeName;
        mIsProjectTheme = params.mIsProjectTheme;
        mProjectResources = params.mProjectResources;
        mFrameworkResources = params.mFrameworkResources;
        mProjectCallback = params.mProjectCallback;
        mLogger = params.mLogger;
        mCustomBackgroundEnabled = params.mCustomBackgroundEnabled;
        mCustomBackgroundColor = params.mCustomBackgroundColor;
    }

    public void setCustomBackgroundColor(int color) {
        mCustomBackgroundEnabled = true;
        mCustomBackgroundColor = color;
    }

    public IXmlPullParser getLayoutDescription() {
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

    public boolean getRenderFullSize() {
        return mRenderFullSize;
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

    public boolean getIsProjectTheme() {
        return mIsProjectTheme;
    }

    public Map<String, Map<String, IResourceValue>> getProjectResources() {
        return mProjectResources;
    }

    public Map<String, Map<String, IResourceValue>> getFrameworkResources() {
        return mFrameworkResources;
    }

    public IProjectCallback getProjectCallback() {
        return mProjectCallback;
    }

    public ILayoutLog getLogger() {
        return mLogger;
    }

    public boolean isCustomBackgroundEnabled() {
        return mCustomBackgroundEnabled;
    }

    public int getCustomBackgroundColor() {
        return mCustomBackgroundColor;
    }
}
