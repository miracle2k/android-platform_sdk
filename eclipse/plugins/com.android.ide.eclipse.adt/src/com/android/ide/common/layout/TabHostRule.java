/*
 * Copyright (C) 2010 The Android Open Source Project
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

import static com.android.ide.common.layout.LayoutConstants.ANDROID_URI;
import static com.android.ide.common.layout.LayoutConstants.ATTR_ID;
import static com.android.ide.common.layout.LayoutConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.ide.common.layout.LayoutConstants.ATTR_LAYOUT_WIDTH;
import static com.android.ide.common.layout.LayoutConstants.ATTR_ORIENTATION;
import static com.android.ide.common.layout.LayoutConstants.FQCN_FRAME_LAYOUT;
import static com.android.ide.common.layout.LayoutConstants.FQCN_LINEAR_LAYOUT;
import static com.android.ide.common.layout.LayoutConstants.FQCN_TAB_WIDGET;
import static com.android.ide.common.layout.LayoutConstants.VALUE_VERTICAL;
import static com.android.ide.common.layout.LayoutConstants.VALUE_WRAP_CONTENT;

import com.android.ide.common.api.INode;
import com.android.ide.common.api.IViewRule;
import com.android.ide.common.api.InsertType;

/**
 * An {@link IViewRule} for android.widget.TabHost.
 */
public class TabHostRule extends IgnoredLayoutRule {
    // The TabHost layout states in its documentation that you typically
    // manipulate its children via the TabHost rather than directly manipulating
    // the child elements yourself, e.g. via addTab() etc.

    @Override
    public void onCreate(INode node, INode parent, InsertType insertType) {
        super.onCreate(node, parent, insertType);

        if (insertType == InsertType.CREATE) {
            String fillParent = getFillParentValueName();

            // Configure default Table setup as described in the Table tutorial
            node.setAttribute(ANDROID_URI, ATTR_ID, "@android:id/tabhost"); //$NON-NLS-1$
            node.setAttribute(ANDROID_URI, ATTR_LAYOUT_WIDTH, fillParent);
            node.setAttribute(ANDROID_URI, ATTR_LAYOUT_HEIGHT, fillParent);

            INode linear = node.appendChild(FQCN_LINEAR_LAYOUT);
            linear.setAttribute(ANDROID_URI, ATTR_LAYOUT_WIDTH, fillParent);
            linear.setAttribute(ANDROID_URI, ATTR_LAYOUT_HEIGHT, fillParent);
            linear.setAttribute(ANDROID_URI, ATTR_ORIENTATION,
                    VALUE_VERTICAL);

            INode tab = linear.appendChild(FQCN_TAB_WIDGET);
            tab.setAttribute(ANDROID_URI, ATTR_LAYOUT_WIDTH, fillParent);
            tab.setAttribute(ANDROID_URI, ATTR_LAYOUT_HEIGHT, VALUE_WRAP_CONTENT);
            tab.setAttribute(ANDROID_URI, ATTR_ID, "@android:id/tabs"); //$NON-NLS-1$

            INode frame = linear.appendChild(FQCN_FRAME_LAYOUT);
            frame.setAttribute(ANDROID_URI, ATTR_LAYOUT_WIDTH, fillParent);
            frame.setAttribute(ANDROID_URI, ATTR_LAYOUT_HEIGHT, fillParent);
            frame.setAttribute(ANDROID_URI, ATTR_ID, "@android:id/tabcontent"); //$NON-NLS-1$

            INode child = frame.appendChild(FQCN_LINEAR_LAYOUT);
            child.setAttribute(ANDROID_URI, ATTR_LAYOUT_WIDTH, fillParent);
            child.setAttribute(ANDROID_URI, ATTR_LAYOUT_HEIGHT, fillParent);
            child.setAttribute(ANDROID_URI, ATTR_ID, "@+id/contentlayout"); //$NON-NLS-1$
        }
    }

}
