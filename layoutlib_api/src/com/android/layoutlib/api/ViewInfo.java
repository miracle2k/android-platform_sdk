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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Layout information for a specific view object
 */
public class ViewInfo {

    protected final Object mKey;
    protected final String mName;
    protected final int mLeft;
    protected final int mRight;
    protected final int mTop;
    protected final int mBottom;
    protected List<ViewInfo> mChildren;;

    public ViewInfo(String name, Object key, int left, int top, int right, int bottom) {
        mName = name;
        mKey = key;
        mLeft = left;
        mRight = right;
        mTop = top;
        mBottom = bottom;
    }

    /**
     * Sets the list of children {@link ViewInfo}.
     */
    public void setChildren(List<ViewInfo> children) {
        mChildren = new ArrayList<ViewInfo>();
        mChildren.addAll(children);
        mChildren = Collections.unmodifiableList(mChildren);
    }

    /**
     * Returns the list of children views. This is never null, but can be empty.
     */
    public List<ViewInfo> getChildren() {
        return mChildren;
    }

    /**
     * Returns the key associated with the node. Can be null.
     *
     * @see IXmlPullParser#getViewKey()
     */
    public Object getViewKey() {
        return null;
    }

    /**
     * Returns the class name of the view object. Can be null.
     */
    public String getClassName() {
        return null;
    }

    /**
     * Returns the left of the view bounds, relative to the view parent bounds.
     */
    public int getLeft() {
        return 0;
    }

    /**
     * Returns the top of the view bounds, relative to the view parent bounds.
     */
    public int getTop() {
        return 0;
    }

    /**
     * Returns the right of the view bounds, relative to the view parent bounds.
     */
    public int getRight() {
        return 0;
    }

    /**
     * Returns the bottom of the view bounds, relative to the view parent bounds.
     */
    public int getBottom() {
        return 0;
    }

    /**
     * Returns a map of default values for some properties. The map key is the property name,
     * as found in the XML.
     */
    public Map<String, String> getDefaultPropertyValues() {
        return null;
    }

    /**
     * Returns the actual android.view.View (or child class) object. This can be used
     * to query the object properties that are not in the XML and not in the map returned
     * by {@link #getDefaultPropertyValues()}.
     */
    public Object getViewObject() {
        return null;
    }
}
