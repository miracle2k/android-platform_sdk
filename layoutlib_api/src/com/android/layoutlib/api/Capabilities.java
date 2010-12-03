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

import com.android.layoutlib.api.LayoutScene.IAnimationListener;

/**
 * Enum describing the layout bridge capabilities.
 *
 */
public enum Capabilities {
    /** Ability to call {@link LayoutScene#render()} and {@link LayoutScene#render(long)}. */
    RENDER,
    /** Ability to call<br>
     * {@link LayoutScene#insertChild(Object, IXmlPullParser, int, com.android.layoutlib.api.LayoutScene.IAnimationListener)}<br>
     * {@link LayoutScene#moveChild(Object, Object, int, com.android.layoutlib.api.LayoutScene.IAnimationListener)}<br>
     * {@link LayoutScene#removeChild(Object, com.android.layoutlib.api.LayoutScene.IAnimationListener)}<br>
     * {@link LayoutScene#setProperty(Object, String, String)}
     * */
    VIEW_MANIPULATION,
    /** Ability to call<br>
     * {@link LayoutScene#animate(Object, String, boolean, com.android.layoutlib.api.LayoutScene.IAnimationListener)}
     * <p>If the bridge also supports {@link #VIEW_MANIPULATION} then those methods can use
     * an {@link IAnimationListener}, otherwise they won't. */
    ANIMATE;
}
