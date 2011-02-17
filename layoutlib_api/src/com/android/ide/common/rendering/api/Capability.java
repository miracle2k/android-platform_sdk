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

/**
 * Enum describing the layout bridge capabilities.
 *
 */
public enum Capability {
    /** Ability to render at full size, as required by the layout, and unbound by the screen */
    UNBOUND_RENDERING,
    /** Ability to override the background of the rendering with transparency using
     * {@link SceneParams#setCustomBackgroundColor(int)} */
    CUSTOM_BACKGROUND_COLOR,
    /** Ability to call {@link LayoutScene#render()} and {@link LayoutScene#render(long)}. */
    RENDER,
    /** Ability to ask for a layout only with no rendering through
     * {@link SessionParams#setLayoutOnly()}
     */
    LAYOUT_ONLY,
    /**
     * Ability to control embedded layout parsers through {@link IXmlPullParser#getParser(String)}
     */
    EMBEDDED_LAYOUT,
    /** Ability to call<br>
     * {@link LayoutScene#insertChild(Object, IXmlPullParser, int, com.android.layoutlib.api.LayoutScene.IAnimationListener)}<br>
     * {@link LayoutScene#moveChild(Object, Object, int, java.util.Map, com.android.layoutlib.api.LayoutScene.IAnimationListener)}<br>
     * {@link LayoutScene#removeChild(Object, com.android.layoutlib.api.LayoutScene.IAnimationListener)}<br>
     * {@link LayoutScene#setProperty(Object, String, String)}<br>
     * The method that receives an animation listener can only use it if the
     * ANIMATED_VIEW_MANIPULATION, or FULL_ANIMATED_VIEW_MANIPULATION is also supported.
     * */
    VIEW_MANIPULATION,
    /** Ability to play animations with<br>
     * {@link LayoutScene#animate(Object, String, boolean, com.android.layoutlib.api.LayoutScene.IAnimationListener)}
     */
    PLAY_ANIMATION,
    /**
     * Ability to manipulate views with animation, as long as the view does not change parent.
     * {@link LayoutScene#insertChild(Object, IXmlPullParser, int, com.android.layoutlib.api.LayoutScene.IAnimationListener)}<br>
     * {@link LayoutScene#moveChild(Object, Object, int, java.util.Map, com.android.layoutlib.api.LayoutScene.IAnimationListener)}<br>
     * {@link LayoutScene#removeChild(Object, com.android.layoutlib.api.LayoutScene.IAnimationListener)}<br>
     */
    ANIMATED_VIEW_MANIPULATION,
    /**
     * Ability to move views (even into a different ViewGroup) with animation.
     * see {@link LayoutScene#moveChild(Object, Object, int, java.util.Map, com.android.layoutlib.api.LayoutScene.IAnimationListener)}
     */
    FULL_ANIMATED_VIEW_MANIPULATION;
}
