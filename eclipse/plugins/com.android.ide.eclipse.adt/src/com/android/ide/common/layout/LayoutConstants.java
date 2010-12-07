/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.eclipse.org/org/documents/epl-v10.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.ide.common.layout;

import com.android.sdklib.SdkConstants;

/**
 * A bunch of constants that map to either:
 * <ul>
 * <li>Android Layouts XML element names (Linear, Relative, Absolute, etc.)
 * <li>Attributes for layout XML elements.
 * <li>Values for attributes.
 * </ul>
 */
public class LayoutConstants {
    /** The element name in a <code>&lt;view class="..."&gt;</code> element. */
    public static final String VIEW = "view";                           //$NON-NLS-1$

    /** The attribute name in a <code>&lt;view class="..."&gt;</code> element. */
    public static final String ATTR_CLASS = "class";                    //$NON-NLS-1$

    // Some common layout element names
    public static final String RELATIVE_LAYOUT = "RelativeLayout";      //$NON-NLS-1$
    public static final String LINEAR_LAYOUT   = "LinearLayout";        //$NON-NLS-1$
    public static final String ABSOLUTE_LAYOUT = "AbsoluteLayout";      //$NON-NLS-1$

    public static final String ATTR_TEXT = "text";                      //$NON-NLS-1$
    public static final String ATTR_ID = "id";                          //$NON-NLS-1$

    public static final String ATTR_LAYOUT_HEIGHT = "layout_height";    //$NON-NLS-1$
    public static final String ATTR_LAYOUT_WIDTH = "layout_width";      //$NON-NLS-1$

    public static final String ATTR_LAYOUT_ALIGN_PARENT_TOP = "layout_alignParentTop"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_ALIGN_PARENT_BOTTOM = "layout_alignParentBottom"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_ALIGN_PARENT_LEFT = "layout_alignParentLeft";//$NON-NLS-1$
    public static final String ATTR_LAYOUT_ALIGN_PARENT_RIGHT = "layout_alignParentRight";   //$NON-NLS-1$

    public static final String ATTR_LAYOUT_ALIGN_BASELINE = "layout_alignBaseline"; //$NON-NLS-1$

    public static final String ATTR_LAYOUT_CENTER_VERTICAL = "layout_centerVertical"; //$NON-NLS-1$
    public static final String ATTR_LAYOUT_CENTER_HORIZONTAL = "layout_centerHorizontal"; //$NON-NLS-1$

    public static final String ATTR_LAYOUT_TO_RIGHT_OF = "layout_toRightOf";    //$NON-NLS-1$
    public static final String ATTR_LAYOUT_TO_LEFT_OF = "layout_toLeftOf";      //$NON-NLS-1$

    public static final String ATTR_LAYOUT_BELOW = "layout_below";              //$NON-NLS-1$
    public static final String ATTR_LAYOUT_ABOVE = "layout_above";              //$NON-NLS-1$

    public static final String ATTR_LAYOUT_Y = "layout_y";                      //$NON-NLS-1$
    public static final String ATTR_LAYOUT_X = "layout_x";                      //$NON-NLS-1$

    public static final String VALUE_WRAP_CONTENT = "wrap_content";             //$NON-NLS-1$
    public static final String VALUE_FILL_PARENT = "fill_parent";               //$NON-NLS-1$
    public static final String VALUE_TRUE = "true";                             //$NON-NLS-1$
    public static final String VALUE_N_DIP = "%ddip";                           //$NON-NLS-1$

    /**
     * Namespace for the Android resource XML, i.e.
     * "http://schemas.android.com/apk/res/android"
     */
    public static final String ANDROID_URI = SdkConstants.NS_RESOURCES;

    /** The fully qualified class name of an EditText view */
    public static final String FQCN_EDIT_TEXT = "android.widget.EditText"; //$NON-NLS-1$

    /** The fully qualified class name of a LinearLayout view */
    public static final String FQCN_LINEAR_LAYOUT = "android.widget.LinearLayout"; //$NON-NLS-1$

    /** The fully qualified class name of a FrameLayout view */
    public static final String FQCN_FRAME_LAYOUT = "android.widget.FrameLayout"; //$NON-NLS-1$

    /** The fully qualified class name of a TableRow view */
    public static final String FQCN_TABLE_ROW = "android.widget.TableRow"; //$NON-NLS-1$

    /** The fully qualified class name of a TabWidget view */
    public static final String FQCN_TAB_WIDGET = "android.widget.TabWidget"; //$NON-NLS-1$

    public static final String ATTR_SRC = "src"; //$NON-NLS-1$

    // like fill_parent for API 8
    public static final String VALUE_MATCH_PARENT = "match_parent"; //$NON-NLS-1$

    public static String ATTR_ORIENTATION = "orientation"; //$NON-NLS-1$

    public static String VALUE_HORIZONTAL = "horizontal"; //$NON-NLS-1$

    public static String VALUE_VERTICAL = "vertical"; //$NON-NLS-1$

}
