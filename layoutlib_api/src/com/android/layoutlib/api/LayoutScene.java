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

import com.android.layoutlib.api.SceneResult.LayoutStatus;

import java.awt.image.BufferedImage;

/**
 * An object allowing interaction with an Android layout.
 *
 * This is returned by {@link LayoutBridge#createScene(SceneParams)}.
 * and can then be used for subsequent actions on the layout.
 *
 * @since 5
 *
 */
public class LayoutScene {

    public interface IAnimationListener {
        /**
         * Called when a new animation frame is available for display.
         */
        void onNewFrame(BufferedImage image);

        /**
         * Called when the animation is done playing.
         */
        void done();

        /**
         * Returns true if the animation is canceled.
         */
        void isCanceled();
    }

    /**
     * Returns the last operation result.
     */
    public SceneResult getResult() {
        return new SceneResult(LayoutStatus.NOT_IMPLEMENTED);
    }

    /**
     * Returns the {@link ViewInfo} object for the top level view.
     * <p>
     * This is reset to a new instance every time {@link #render()} is called and can be
     * <code>null</code> if the call failed (and the method returned a {@link SceneResult} with
     * {@link LayoutStatus#ERROR} or {@link LayoutStatus#NOT_IMPLEMENTED}.
     * <p/>
     * This can be safely modified by the caller.
     */
    public ViewInfo getRootView() {
        return null;
    }

    /**
     * Returns the rendering of the full layout.
     * <p>
     * This is reset to a new instance every time {@link #render()} is called and can be
     * <code>null</code> if the call failed (and the method returned a {@link SceneResult} with
     * {@link LayoutStatus#ERROR} or {@link LayoutStatus#NOT_IMPLEMENTED}.
     * <p/>
     * This can be safely modified by the caller.
     */
    public BufferedImage getImage() {
        return null;
    }

    /**
     * Re-renders the layout as-is.
     * In case of success, this should be followed by calls to {@link #getRootView()} and
     * {@link #getImage()} to access the result of the rendering.
     *
     * @return a {@link SceneResult} indicating the status of the action.
     */
    public SceneResult render() {
        return new SceneResult(LayoutStatus.NOT_IMPLEMENTED);
    }

    /**
     * Sets the value of a given property on a given object.
     * <p/>
     * This does nothing more than change the property. To render the scene in its new state, a
     * call to {@link #render()} is required.
     * <p/>
     * Any amount of actions can be taken on the scene before {@link #render()} is called.
     *
     * @param object
     * @param propertyName
     * @param propertyValue
     *
     * @return a {@link SceneResult} indicating the status of the action.
     */
    public SceneResult setProperty(int object, String propertyName, String propertyValue) {
        return new SceneResult(LayoutStatus.NOT_IMPLEMENTED);
    }

    /**
     * Inserts a new child in a ViewGroup object.
     * <p/>
     * This does nothing more than change the layouy. To render the scene in its new state, a
     * call to {@link #render()} is required.
     * <p/>
     * Any amount of actions can be taken on the scene before {@link #render()} is called.
     *
     * @return a {@link SceneResult} indicating the status of the action.
     */
    public SceneResult insertChild() {
        return new SceneResult(LayoutStatus.NOT_IMPLEMENTED);
    }

    /**
     * Removes a child from a ViewGroup object.
     * <p/>
     * This does nothing more than change the layouy. To render the scene in its new state, a
     * call to {@link #render()} is required.
     * <p/>
     * Any amount of actions can be taken on the scene before {@link #render()} is called.
     *
     * @return a {@link SceneResult} indicating the status of the action.
     */
    public SceneResult removeChild() {
        return new SceneResult(LayoutStatus.NOT_IMPLEMENTED);
    }

    /**
     * Starts playing an given animation on a given object.
     * <p/>
     * The animation playback is asynchronous and the rendered frame is sent vi the
     * <var>listener</var>.
     *
     * @return a {@link SceneResult} indicating the status of the action.
     */
    public SceneResult animate(int object, int animation, IAnimationListener listener) {
        return new SceneResult(LayoutStatus.NOT_IMPLEMENTED);
    }

    /**
     * Discards the layout. No more actions can be called on this object.
     */
    public void dispose() {
    }
}
