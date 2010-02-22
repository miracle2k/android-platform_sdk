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

/**
 * An {@link IViewRule} for android.widget.RelativeLayout and all its derived classes.
 */
public class AndroidWidgetRelativeLayoutRule extends BaseLayout {

    // ==== Selection ====

    /**
     * Display some relation layout information on a selected child.
     */
    void onChildSelected(IGraphics gc, INode parentNode, INode childNode) {
        super.onChildSelected(gc, parentNode, childNode);

        // Get the top parent, to display data under it
        INode topParent = parentNode;
        while (true) {
            INode p = topParent.getParent();
            if (p == null) {
                break;
            } else {
                topParent = p;
            }
        }

        Rect b = topParent.getBounds();
        if (!b.isValid()) {
            return;
        }

        def infos = [];

        def addAttr = {
            def a = childNode.getStringAttr("layout_${it}");
            if (a) {
                infos += "${it}: ${a}";
            }
        }

        addAttr("above");
        addAttr("below");
        addAttr("toLeftOf");
        addAttr("toRightOf");
        addAttr("alignBaseline");
        addAttr("alignTop");
        addAttr("alignBottom");
        addAttr("alignLeft");
        addAttr("alignRight");
        addAttr("alignParentTop");
        addAttr("alignParentBottom");
        addAttr("alignParentLeft");
        addAttr("alignParentRight");
        addAttr("alignWithParentMissing");
        addAttr("centerHorizontal");
        addAttr("centerInParent");
        addAttr("centerVertical");

        if (infos) {
            gc.setForeground(gc.registerColor(0x00222222));
            int x = b.x + 10;
            int y = b.y + b.h + 10;
            int h = gc.getFontHeight();
            infos.each {
                y += h;
                gc.drawString(it, x, y);
            }
        }
    }


    // ==== Drag'n'drop support ====

    DropFeedback onDropEnter(INode targetNode) {

        def bn = targetNode.getBounds();
        if (!bn.isValid()) {
            return;
        }

        // Prepare the drop feedback
        return new DropFeedback(
                [ "child": null,    // INode: Current child under cursor
                  "zones": null,    // Valid "anchor" zones for the current child
                                    // of type list(map(rect:Rect, attr:[String]))
                  "rect": null,     // Surrounding rect of the current zones
                  "curr": null,     // map: Current zone
                ],
                { gc, node, feedback ->
                    // Paint closure for the RelativeLayout just defers to the method below
                    drawRelativeDropFeedback(gc, node, feedback);
                });
    }

    DropFeedback onDropMove(INode layoutNode, DropFeedback feedback, Point p) {

        def data = feedback.userData;

        Rect r = data.rect;

        // If cursor is still in the current rect, don't change it.
        if (r == null || !r.contains(p)) {
            // Find the current direct children under the cursor
            def childNode = null;
            for(child in layoutNode.getChildren()) {
                if (child.getBounds().contains(p)) {
                    childNode = child;
                    break;
                }
            }

            // Only recompute drop zones if the child changed
            if (childNode != data.child) {
                data.child = childNode;
                def result = computeDropZones(childNode);
                data.rect = r = result[0];
                data.zones = result[1];
                data.curr  = null;
                feedback.requestPaint = true;
            }
        }

        // Find the current zone
        def currZone = null;
        if (data.zones) {
            for(zone in data.zones) {
                if (zone.rect.contains(p)) {
                    currZone = zone;
                    break;
                }
            }
        }

        if (currZone != data.curr) {
            data.curr = currZone;
            feedback.requestPaint = true;
        }

        return feedback;
    }

    def computeDropZones(INode childNode) {

        if (childNode == null) {
            return [];
        }

        Rect b = childNode.getBounds();

        // Compute drop zone borders as follow:
        //
        // +---+-----+-----+-----+---+
        // | 1 \  2  \  3  /  4  / 5 |
        // +----+-----+---+-----+----+
        //
        // For the top and bottom borders, zones 1 and 5 have the same width, which is
        //  size1 = min(10, w/5)
        // and zones 2, 3 and 4 have a width of
        //  size2 = (w - 2*size) / 3
        //
        // Same works for left and right borders vertically.
        //
        // Attributes generated:
        // Horizontally:
        //   1- toLeftOf / 2- alignLeft / 3- 2+4 / 4- alignRight  / 5- toRightOf
        // Vertically:
        //   1- above    / 2-alignTop   / 3- 2+4 / 4- alignBottom / 5- below

        int w1 = 20;
        int w3 = b.w / 3;
        int w2 = Math.max(20, w3);

        int h1 = 20;
        int h3 = b.h / 3;
        int h2 = Math.max(20, h3);

        int wt = w1 * 2 + w2 * 3;
        int ht = h1 * 2 + h2 * 3;

        int x1 = b.x + ((b.w - wt) / 2);
        int y1 = b.y + ((b.h - ht) / 2);

        def bounds = new Rect(x1, y1, wt, ht);

        def zones = [];
        def a = "above";
        int x = x1;
        int y = y1;

        def addx = {
            int wn, ArrayList a2 ->

            zones << [ "rect" : new Rect(x, y, wn, h1),
                       "attr" : [ a ] +  a2,
                       "child": childNode ];
            x += wn;
        }

        addx(w1, [ "toLeftOf"  ]);
        addx(w2, [ "alignLeft" ]);
        addx(w2, [ "alignLeft", "alignRight" ]);
        addx(w2, [ "alignRight" ]);
        addx(w1, [ "toRightOf" ]);

        a = "below";
        x = x1;
        y = y1 + ht - h1;

        addx(w1, [ "toLeftOf"  ]);
        addx(w2, [ "alignLeft" ]);
        addx(w2, [ "alignLeft", "alignRight" ]);
        addx(w2, [ "alignRight" ]);
        addx(w1, [ "toRightOf" ]);

        def addy = {
            int hn, ArrayList a2 ->
            zones << [ "rect" : new Rect(x, y, w1, hn),
                       "attr" : [ a ] +  a2,
                       "child": childNode ];
            y += hn;
        }

        a = "toLeftOf";
        x = x1;
        y = y1 + h1;

        addy(h2, [ "alignTop" ]);
        addy(h2, [ "alignTop", "alignBottom" ]);
        addy(h2, [ "alignBottom" ]);

        a = "toRightOf";
        x = x1 + wt - w1;
        y = y1 + h1;

        addy(h2, [ "alignTop" ]);
        addy(h2, [ "alignTop", "alignBottom" ]);
        addy(h2, [ "alignBottom" ]);

        return [ bounds, zones ];
    }

    void drawRelativeDropFeedback(IGraphics gc, INode layoutNode, DropFeedback feedback) {
        Rect b = layoutNode.getBounds();
        if (!b.isValid()) {
            return;
        }

        def color = gc.registerColor(0x00FF9900);
        gc.setForeground(color);

        gc.setLineStyle(IGraphics.LineStyle.LINE_SOLID);
        gc.setLineWidth(2);
        gc.drawRect(b);

        gc.setLineStyle(IGraphics.LineStyle.LINE_DOT);
        gc.setLineWidth(1);

        def data = feedback.userData;

        if (data.zones) {
            data.zones.each {
                gc.drawRect(it.rect);
            }
        }

        if (data.curr) {
            gc.setAlpha(200);
            gc.setBackground(color);
            gc.fillRect(data.curr.rect);
            gc.setAlpha(255);

            int x = data.rect.x + 5;
            int y = data.rect.y + data.rect.h + 5;
            int h = gc.getFontHeight();
            String id = data.curr.child.getStringAttr("id");
            data.curr.attr.each {
                String s = it;
                if (id) s = "$s=$id";
                gc.drawString(s, x, y);
                y += h;
            }

        }
    }

    void onDropLeave(INode targetNode, DropFeedback feedback) {
        // ignore
    }

    void onDropped(String fqcn, INode targetNode, DropFeedback feedback, Point p) {
        // ignore
    }


}
