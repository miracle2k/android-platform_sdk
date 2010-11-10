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

import java.awt.image.BufferedImage;

/**
 * An object allowing interaction with an Android layout.
 *
 * This is returned by {@link ILayoutBridge#startLayout(IXmlPullParser, Object, int, int, boolean, int, float, float, String, boolean, java.util.Map, java.util.Map, IProjectCallback, ILayoutLog)}
 * and can then be used for subsequent actions on the layout.
 *
 * @since 5
 *
 */
public interface ILayoutScene {

    enum LayoutStatus { SUCCESS, ERROR };

    public interface ILayoutResult {
        LayoutStatus getStatus();
        String getErrorMessage();
        Throwable getException();
    }

    public interface IAnimationListener {
        /**
         * Called when a new animation frame is available for display.
         */
        void onNewFrame(BufferedImage image);

        /**
         * Called when the animation is done playing.
         */
        void done();
    }

    /**
     * Returns the result for the original call to {@link ILayoutBridge#startLayout(IXmlPullParser, Object, int, int, boolean, int, float, float, String, boolean, java.util.Map, java.util.Map, IProjectCallback, ILayoutLog)}
     */
    ILayoutResult getStatus();

    /**
     * Returns the {@link ILayoutViewInfo} object for the top level view.
     */
    ILayoutViewInfo getRootView();

    /**
     * Returns the rendering of the full layout.
     */
    BufferedImage getImage();

    /**
     * Re-renders the layout as-is.
     * In case of success, this should be followed by calls to {@link #getRootView()} and
     * {@link #getImage()}
     */
    ILayoutResult render();

    /**
     * Sets the value of a given property on a given object.
     * In case of success, this should be followed by a call to {@link #render()}
     * @param object
     * @param propertyName
     * @param propertyValue
     * @return
     */
    ILayoutResult setProperty(int object, String propertyName, String propertyValue);

    /**
     * TBD
     */
    ILayoutResult insertChild();

    /**
     * TBD
     */
    ILayoutResult removeChild();

    /**
     * TBD
     */
    ILayoutResult animate(int object, int animation, IAnimationListener listener);

    /**
     * Discards the layout. No more actions can be called on this object.
     */
    void dispose();
}
