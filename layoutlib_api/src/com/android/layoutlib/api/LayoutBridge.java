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

/**
 * Entry point of the Layout Lib. Extensions of this class provide a method to compute
 * and render a layout.
 */
public abstract class LayoutBridge {

    public final static int API_CURRENT = 5;

    /**
     * Returns the API level of the layout library.
     * <p/>
     * While no methods will ever be removed, some may become deprecated, and some new ones
     * will appear.
     */
    public abstract int getApiLevel();

    /**
     * Initializes the Bridge object.
     *
     * @param fontOsLocation the location of the fonts.
     * @param enumValueMap map attrName => { map enumFlagName => Integer value }.
     * @return true if success.
     */
    public boolean init(String fontOsLocation, Map<String, Map<String, Integer>> enumValueMap) {
        return false;
    }

    /**
     * Prepares the layoutlib to unloaded.
     */
    public boolean dispose() {
        return false;
    }

    /**
     * Starts a layout session by inflating and rendering it. The method returns a
     * {@link LayoutScene} on which further actions can be taken.
     *
     * @return a new {@link ILayoutScene} object that contains the result of the scene creation and
     * first rendering.
     */
    public LayoutScene createScene(SceneParams params) {
        return null;
    }

    /**
     * Clears the resource cache for a specific project.
     * <p/>This cache contains bitmaps and nine patches that are loaded from the disk and reused
     * until this method is called.
     * <p/>The cache is not configuration dependent and should only be cleared when a
     * resource changes (at this time only bitmaps and 9 patches go into the cache).
     *
     * @param projectKey the key for the project.
     */
    public void clearCaches(Object projectKey) {

    }
}
