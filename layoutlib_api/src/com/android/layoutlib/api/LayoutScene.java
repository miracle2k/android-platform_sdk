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

import static com.android.layoutlib.api.SceneResult.SceneStatus.NOT_IMPLEMENTED;

import com.android.layoutlib.api.SceneResult.SceneStatus;

import java.awt.image.BufferedImage;
import java.util.Map;

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
        void done(SceneResult result);

        /**
         * Returns true if the animation is canceled.
         */
        boolean isCanceled();
    }

    /**
     * Returns the last operation result.
     */
    public SceneResult getResult() {
        return NOT_IMPLEMENTED.getResult();
    }

    /**
     * Returns the {@link ViewInfo} object for the top level view.
     * <p>
     * This is reset to a new instance every time {@link #render()} is called and can be
     * <code>null</code> if the call failed (and the method returned a {@link SceneResult} with
     * {@link SceneStatus#ERROR_UNKNOWN} or {@link SceneStatus#NOT_IMPLEMENTED}.
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
     * {@link SceneStatus#ERROR_UNKNOWN} or {@link SceneStatus#NOT_IMPLEMENTED}.
     * <p/>
     * This can be safely modified by the caller.
     */
    public BufferedImage getImage() {
        return null;
    }


    /**
     * Returns a map of (XML attribute name, attribute value) containing only default attribute
     * values, for the given view Object.
     * @param viewObject the view object.
     * @return a map of the default property values or null.
     */
    public Map<String, String> getDefaultViewPropertyValues(Object viewObject) {
        return null;
    }

    /**
     * Re-renders the layout as-is.
     * In case of success, this should be followed by calls to {@link #getRootView()} and
     * {@link #getImage()} to access the result of the rendering.
     *
     * This is equivalent to calling <code>render(SceneParams.DEFAULT_TIMEOUT)</code>
     *
     * @return a {@link SceneResult} indicating the status of the action.
     */
    public SceneResult render() {
        return render(SceneParams.DEFAULT_TIMEOUT);
    }

    /**
     * Re-renders the layout as-is, with a given timeout in case other renderings are being done.
     * In case of success, this should be followed by calls to {@link #getRootView()} and
     * {@link #getImage()} to access the result of the rendering.
     *
     * The {@link LayoutBridge} is only able to inflate or render one layout at a time. There
     * is an internal lock object whenever such an action occurs. The timeout parameter is used
     * when attempting to acquire the lock. If the timeout expires, the method will return
     * {@link SceneStatus#ERROR_TIMEOUT}.
     *
     * @param timeout timeout for the rendering, in milliseconds.
     *
     * @return a {@link SceneResult} indicating the status of the action.
     */
    public SceneResult render(long timeout) {
        return NOT_IMPLEMENTED.getResult();
    }

    /**
     * Sets the value of a given property on a given object.
     * <p/>
     * This does nothing more than change the property. To render the scene in its new state, a
     * call to {@link #render()} is required.
     * <p/>
     * Any amount of actions can be taken on the scene before {@link #render()} is called.
     *
     * @param objectView
     * @param propertyName
     * @param propertyValue
     *
     * @return a {@link SceneResult} indicating the status of the action.
     */
    public SceneResult setProperty(Object objectView, String propertyName, String propertyValue) {
        return NOT_IMPLEMENTED.getResult();
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
    public SceneResult insertChild(Object parentView, IXmlPullParser childXml, int index) {
        return NOT_IMPLEMENTED.getResult();
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
    public SceneResult moveChild(Object parentView, IXmlPullParser layoutParamsXml, int index) {
        return NOT_IMPLEMENTED.getResult();
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
    public SceneResult removeChild(Object parentView, int index) {
        return NOT_IMPLEMENTED.getResult();
    }

    /**
     * Starts playing an given animation on a given object.
     * <p/>
     * The animation playback is asynchronous and the rendered frame is sent vi the
     * <var>listener</var>.
     *
     * @param targetObject the view object to animate
     * @param animationName the name of the animation (res/anim) to play.
     * @param listener the listener callback.
     *
     * @return a {@link SceneResult} indicating the status of the action.
     */
    public SceneResult animate(Object targetObject, String animationName,
            boolean isFrameworkAnimation, IAnimationListener listener) {
        return NOT_IMPLEMENTED.getResult();
    }

    /**
     * Discards the layout. No more actions can be called on this object.
     */
    public void dispose() {
    }
}
