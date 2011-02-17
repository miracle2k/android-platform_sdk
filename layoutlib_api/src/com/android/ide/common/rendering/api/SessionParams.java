/*
 * Copyright (C) 2011 The Android Open Source Project
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

/**
 * Rendering parameters for a {@link RenderSession}.
 */
public class SessionParams extends RenderParams {

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


    private final ILayoutPullParser mLayoutDescription;
    private final RenderingMode mRenderingMode;
    private boolean mLayoutOnly = false;

    /**
     *
     * @param layoutDescription the {@link ILayoutPullParser} letting the LayoutLib Bridge visit the
     * layout file.
     * @param renderingMode The rendering mode.
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
    public SessionParams(
            ILayoutPullParser layoutDescription,
            RenderingMode renderingMode,
            Object projectKey,
            int screenWidth, int screenHeight,
            Density density, float xdpi, float ydpi,
            RenderResources renderResources,
            IProjectCallback projectCallback,
            int minSdkVersion, int targetSdkVersion,
            LayoutLog log) {
        super(projectKey, screenWidth, screenHeight, density, xdpi, ydpi,
                renderResources, projectCallback, minSdkVersion, targetSdkVersion, log);

        mLayoutDescription = layoutDescription;
        mRenderingMode = renderingMode;
    }

    public SessionParams(SessionParams params) {
        super(params);
        mLayoutDescription = params.mLayoutDescription;
        mRenderingMode = params.mRenderingMode;
    }

    public ILayoutPullParser getLayoutDescription() {
        return mLayoutDescription;
    }

    public RenderingMode getRenderingMode() {
        return mRenderingMode;
    }

    public void setLayoutOnly() {
        mLayoutOnly = true;
    }

    public boolean isLayoutOnly() {
        return mLayoutOnly;
    }
}
