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

import java.util.Collections;
import java.util.List;

/**
 * Layout information for a specific view object
 */
public class ViewInfo {

    private final Object mCookie;
    private final String mName;
    private final int mLeft;
    private final int mRight;
    private final int mTop;
    private final int mBottom;
    private List<ViewInfo> mChildren = Collections.emptyList();
    private final Object mViewObject;
    private final Object mLayoutParamsObject;

    public ViewInfo(String name, Object cookie, int left, int top, int right, int bottom) {
        this(name, cookie, left, top, right, bottom, null /*viewObject*/,
                null /*layoutParamsObject*/);
    }

    public ViewInfo(String name, Object cookie, int left, int top, int right, int bottom,
            Object viewObject, Object layoutParamsObject) {
        mName = name;
        mCookie = cookie;
        mLeft = left;
        mRight = right;
        mTop = top;
        mBottom = bottom;
        mViewObject = viewObject;
        mLayoutParamsObject = layoutParamsObject;
    }

    /**
     * Sets the list of children {@link ViewInfo}.
     */
    public void setChildren(List<ViewInfo> children) {
        if (children != null) {
            mChildren = Collections.unmodifiableList(children);
        } else {
            mChildren = Collections.emptyList();
        }
    }

    /**
     * Returns the list of children views. This is never null, but can be empty.
     */
    public List<ViewInfo> getChildren() {
        return mChildren;
    }

    /**
     * Returns the cookie associated with the XML node. Can be null.
     *
     * @see ILayoutPullParser#getViewKey()
     */
    public Object getCookie() {
        return mCookie;
    }

    /**
     * Returns the class name of the view object. Can be null.
     */
    public String getClassName() {
        return mName;
    }

    /**
     * Returns the left of the view bounds, relative to the view parent bounds.
     */
    public int getLeft() {
        return mLeft;
    }

    /**
     * Returns the top of the view bounds, relative to the view parent bounds.
     */
    public int getTop() {
        return mTop;
    }

    /**
     * Returns the right of the view bounds, relative to the view parent bounds.
     */
    public int getRight() {
        return mRight;
    }

    /**
     * Returns the bottom of the view bounds, relative to the view parent bounds.
     */
    public int getBottom() {
        return mBottom;
    }

    /**
     * Returns the actual android.view.View (or child class) object. This can be used
     * to query the object properties that are not in the XML and not in the map returned
     * by {@link #getDefaultPropertyValues()}.
     */
    public Object getViewObject() {
        return mViewObject;
    }

    /**
     * Returns the actual  android.view.ViewGroup$LayoutParams (or child class) object.
     * This can be used to query the object properties that are not in the XML and not in
     * the map returned by {@link #getDefaultPropertyValues()}.
     */
    public Object getLayoutParamsObject() {
        return mLayoutParamsObject;
    }
}
