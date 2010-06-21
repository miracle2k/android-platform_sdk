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

package com.android.adt.gscripts;

public class BaseView implements IViewRule {

    private String mFqcn;

    // Some common Android layout attribute names used by the view rules.
    // All these belong to the attribute namespace ANDROID_URI.
    public static String ATTR_ID = "id";
    public static String ATTR_TEXT = "text";
    public static String ATTR_LAYOUT_WIDTH = "layout_width";
    public static String ATTR_LAYOUT_HEIGHT = "layout_height";

    // Some common Android layout attribute values used by the view rules.
    public static String VALUE_FILL_PARENT = "fill_parent";
    public static String VALUE_MATCH_PARENT = "match_parent";
    public static String VALUE_MATCH_CONTENT = "match_content";


    /**
     * Namespace for the Android resource XML,
     * i.e. "http://schemas.android.com/apk/res/android"
     */
    public static String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    public boolean onInitialize(String fqcn) {
        // This base rule can handle any class.
        mFqcn = fqcn
        return true;
    }

    public void onDispose() {
        // Nothing to dispose.
    }

    public String getDisplayName() {
        // Default is to not override the selection display name.
        return null;
    }

    public Map<?, ?> getDefaultAttributes() {
        // The base rule does not have any custom default attributes.
        return null;
    }

    public String getFqcn() {
        return mFqcn;
    }

    // ==== Selection ====

    void onSelected(IGraphics gc, INode selectedNode,
                String displayName, boolean isMultipleSelection) {
        Rect r = selectedNode.getBounds();

        if (!r.isValid()) {
            return;
        }

        gc.setLineWidth(1);
        gc.setLineStyle(IGraphics.LineStyle.LINE_SOLID);
        gc.drawRect(r);

        if (displayName == null || isMultipleSelection) {
            return;
        }

        int xs = r.x + 2;
        int ys = r.y - gc.getFontHeight();
        if (ys < 0) {
            ys = r.y + r.h;
        }
        gc.drawString(displayName, xs, ys);
    }

    void onChildSelected(IGraphics gc, INode parentNode, INode childNode) {
        Rect rp = parentNode.getBounds();
        Rect rc = childNode.getBounds();

        if (rp.isValid() && rc.isValid()) {
            gc.setLineWidth(1);
            gc.setLineStyle(IGraphics.LineStyle.LINE_DOT);

            // top line
            int m = rc.x + rc.w / 2;
            gc.drawLine(m, rc.y, m, rp.y);
            // bottom line
            gc.drawLine(m, rc.y + rc.h, m, rp.y + rp.h);
            // left line
            m = rc.y + rc.h / 2;
            gc.drawLine(rc.x, m, rp.x, m);
            // right line
            gc.drawLine(rc.x + rc.w, m, rp.x + rp.w, m);
        }
    }


    // ==== Drag'n'drop support ====

    // By default Views do not accept drag'n'drop.
    DropFeedback onDropEnter(INode targetNode, IDragElement[] elements) {
        return null;
    }

    DropFeedback onDropMove(INode targetNode, IDragElement[] elements,
                            DropFeedback feedback, Point p) {
        return null;
    }

    void onDropLeave(INode targetNode, IDragElement[] elements, DropFeedback feedback) {
        // ignore
    }

    void onDropped(INode targetNode, IDragElement[] elements, DropFeedback feedback, Point p) {
        // ignore
    }

}
