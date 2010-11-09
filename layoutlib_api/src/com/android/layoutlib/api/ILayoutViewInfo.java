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

import java.util.List;
import java.util.Map;

/**
 * Layout information for a specific view object
 */
public interface ILayoutViewInfo {

    /**
     * Returns the list of children views.
     */
    List<ILayoutViewInfo> getChildren();

    /**
     * Returns the key associated with the node.
     * @see IXmlPullParser#getViewKey()
     */
    Object getViewKey();

    /**
     * Returns the class name of the view object.
     */
    String getClassName();

    /**
     * Returns the left of the view bounds, relative to the view parent bounds.
     */
    int getLeft();

    /**
     * Returns the top of the view bounds, relative to the view parent bounds.
     */
    int getTop();

    /**
     * Returns the right of the view bounds, relative to the view parent bounds.
     */
    int getRight();

    /**
     * Returns the bottom of the view bounds, relative to the view parent bounds.
     */
    int getBottom();

    /**
     * Returns a map of default values for some properties. The map key is the property name,
     * as found in the XML.
     */
    Map<String, String> getDefaultPropertyValues();

    /**
     * Returns the actual android.view.View (or child class) object. This can be used
     * to query the object properties that are not in the XML and not in the map returned
     * by {@link #getDefaultPropertyValues()}.
     */
    Object getViewObject();
}
