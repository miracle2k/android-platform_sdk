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
        // This base rule can handle any class so we don't need to filter on FQCN.
        // Derived classes should do so if they can handle some subclasses.

        // For debugging and as an example of how to use the injected _rules_engine property.
        _rules_engine.debugPrintf("Initialize() of %s", _rules_engine.getFqcn());

        // If onInitialize returns false, it means it can't handle the given FQCN and
        // will be unloaded.
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

    // ==== Selection ====

    public void onSelected(IGraphics gc, INode selectedNode,
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

    public void onChildSelected(IGraphics gc, INode parentNode, INode childNode) {
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
    public DropFeedback onDropEnter(INode targetNode, IDragElement[] elements) {
        return null;
    }

    public DropFeedback onDropMove(INode targetNode, IDragElement[] elements,
                            DropFeedback feedback, Point p) {
        return null;
    }

    public void onDropLeave(INode targetNode, IDragElement[] elements, DropFeedback feedback) {
        // ignore
    }

    public void onDropped(INode targetNode, IDragElement[] elements, DropFeedback feedback, Point p) {
        // ignore
    }

    // ==== Paste support ====

    /**
     * Most views can't accept children so there's nothing to paste on them.
     * In this case, defer the call to the parent layout and use the target node as
     * an indication of where to paste.
     */
    public void onPaste(INode targetNode, IDragElement[] elements) {
        //
        def parent = targetNode.getParent();
        def parentFqcn = parent?.getFqcn();
        def parentRule = _rules_engine.loadRule(parentFqcn);

        if (parentRule instanceof BaseLayout) {
            parentRule.onPasteBeforeChild(parent, targetNode, elements);
        }
    }

}
