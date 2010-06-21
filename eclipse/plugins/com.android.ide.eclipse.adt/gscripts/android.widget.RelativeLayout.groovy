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
            def a = childNode.getStringAttr(ANDROID_URI, "layout_${it}");
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

    DropFeedback onDropEnter(INode targetNode, IDragElement[] elements) {

        if (elements.length == 0) {
            return null;
        }

        def bn = targetNode.getBounds();
        if (!bn.isValid()) {
            return;
        }

        // Collect the ids of the elements being dragged
        def movedIds = collectIds([:], elements).keySet().asList();

        // Prepare the drop feedback
        return new DropFeedback(
                [ "child": null,    // INode: Current child under cursor
                  "index": 0,       // int: Index of child in the parent children list
                  "zones": null,    // Valid "anchor" zones for the current child
                                    // of type list(map(rect:Rect, attr:[String]))
                  "curr":  null,    // map: Current zone
                  "movedIds": movedIds,
                  "cachedLinkIds": [:]
                ],
                { gc, node, feedback ->
                    // Paint closure for the RelativeLayout just defers to the method below
                    drawRelativeDropFeedback(gc, node, elements, feedback);
                });
    }

    DropFeedback onDropMove(INode targetNode,
                            IDragElement[] elements,
                            DropFeedback feedback,
                            Point p) {

        def  data = feedback.userData;
        Rect area = feedback.captureArea;

        // Only look for a new child if cursor is no longer under the current rect
        if (area == null || !area.contains(p)) {

            // We're not capturing anymore since we got outside of the capture bounds
            feedback.captureArea = null;

            // Find the current direct children under the cursor
            def childNode = null;
            def childIndex = -1;
            nextChild: for(child in targetNode.getChildren()) {
                childIndex++;
                def bc = child.getBounds();
                if (bc.contains(p)) {

                    // TODO visually indicate this target node has been rejected,
                    // e.g. by drawing a semi-transp rect on it or drawing a red cross at
                    // the cursor point.

                    // If we're doing a move operation within the same canvas, we can't
                    // attach the moved object to one belonging to the selection since
                    // it will disappear after the move.
                    if (feedback.sameCanvas && !feedback.isCopy) {
                        for (element in elements) {
                            if (bc == element.getBounds()) {
                                continue nextChild;
                            }
                        }
                    }

                    // One more limitation: if we're moving one or more elements, we can't
                    // drop them on a child which relative position is expressed directly or
                    // indirectly based on the element being moved.
                    if (!feedback.isCopy) {
                        if (searchRelativeIds(child, data.movedIds, data.cachedLinkIds)) {
                            continue nextChild;
                        }
                    }

                    childNode = child;
                    break;
                }
            }

            // If there is a selected child and it changed, recompute child drop zones
            if (childNode != null && childNode != data.child) {
                data.child = childNode;
                data.index = childIndex;
                data.curr  = null;
                data.zones = null;

                def result = computeChildDropZones(childNode);
                data.zones = result[1];

                // capture this rect, to prevent the engine from switching the layout node.
                feedback.captureArea = result[0];
                feedback.requestPaint = true;

            } else if (childNode == null) {
                // If there is no selected child, compute the border drop zone
                data.child = null;
                data.index = -1;
                data.curr  = null;

                def zone = computeBorderDropZone(targetNode.getBounds(), p);

                if (zone == null) {
                    data.zones = null;
                } else {
                    data.zones = [ zone ];
                    feedback.captureArea = zone.rect;
                }

                feedback.requestPaint = (area != feedback.captureArea);
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

    /**
     * Returns true if the child has any attribute of Format.REFERENCE which
     * value matches one of the ids in movedIds.
     */
    def searchRelativeIds(INode node, List movedIds, Map cachedLinkIds) {
        def ids = getLinkedIds(node, cachedLinkIds);

        for (id in ids) {
            if (id in movedIds) {
                return true;
            }
        }

        return false;
    }

    def getLinkedIds(INode node, Map cachedLinkIds) {
        def ids = cachedLinkIds[node];

        if (ids != null) {
            return ids;
        }

        // we don't have cached data on this child, so create a list of
        // all the linked id it is referencing.
        ids = [];
        cachedLinkIds[node] = ids;
        for (attr in node.getAttributes()) {
            def attrInfo = node.getAttributeInfo(attr.getUri(), attr.getName());
            if (attrInfo == null) {
                continue;
            }
            def formats = attrInfo.getFormats();
            if (formats == null || !(IAttributeInfo.Format.REFERENCE in formats)) {
                continue;
            }
            def id = attr.getValue();
            id = normalizeId(id);
            if (id in ids) {
                continue;
            }
            ids.add(id);

            // Find the sibling with that id
            def p = node.getParent();
            if (p == null) {
                continue;
            }
            for (child in p.getChildren()) {
                if (child == node) {
                    continue;
                }
                def childId = child.getStringAttr(ANDROID_URI, ATTR_ID);
                childId = normalizeId(childId);
                if (id == childId) {
                    def linkedIds = getLinkedIds(child, cachedLinkIds);
                    ids.addAll(linkedIds);
                    break;
                }
            }
        }

        return ids;
    }

    def computeBorderDropZone(Rect bounds, Point p) {

        int x = p.x;
        int y = p.y;

        int x1 = bounds.x;
        int y1 = bounds.y;
        int w  = bounds.w;
        int h  = bounds.h;
        int x2 = x1 + w;
        int y2 = y1 + h;

        int n  = 10;
        int n2 = n*2;

        Rect r = null;
        String attr = null;

        if (x <= x1 + n && y >= y1 && y <= y2) {
            r = new Rect(x1 - n, y1, n2, h);
            attr = "alignParentLeft";

        } else if (x >= x2 - n && y >= y1 && y <= y2) {
            r = new Rect(x2 - n, y1, n2, h);
            attr = "alignParentRight";

        } else if (y <= y1 + n && x >= x1 && x <= x2) {
            r = new Rect(x1, y1 - n, w, n2);
            attr = "alignParentTop";

        } else if (y >= y2 - n && x >= x1 && x <= x2) {
            r = new Rect(x1, y2 - n, w, n2);
            attr = "alignParentBottom";

        } else {
            // we're nowhere near a border
            return null;
        }

        return [ "rect": r, "attr": [ attr ], "mark": r.center() ];
    }


    def computeChildDropZones(INode childNode) {

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

            zones << [ "rect": new Rect(x, y, wn, h1),
                       "attr": [ a ] +  a2 ];
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
            zones << [ "rect": new Rect(x, y, w1, hn),
                       "attr": [ a ] +  a2 ];
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

    void drawRelativeDropFeedback(IGraphics gc,
                                  INode targetNode,
                                  IDragElement[] elements,
                                  DropFeedback feedback) {
        Rect b = targetNode.getBounds();
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

            def r = feedback.captureArea;
            int x = r.x + 5;
            int y = r.y + r.h + 5;
            int h = gc.getFontHeight();

            String id = null;
            if (data.child) {
                id = data.child.getStringAttr(ANDROID_URI, ATTR_ID);
            }

            for (s in data.curr.attr) {
                if (id) s = "$s=$id";
                gc.drawString(s, x, y);
                y += h;
            }

            gc.setLineStyle(IGraphics.LineStyle.LINE_SOLID);
            gc.setLineWidth(2);

            def mark = data.curr.get("mark");
            if (mark) {
                def black = gc.registerColor(0);
                gc.setForeground(black);

                x = mark.x;
                y = mark.y;
                gc.drawLine(x - 10, y - 10, x + 10, y + 10);
                gc.drawLine(x + 10, y - 10, x - 10, y + 10);
                gc.drawOval(x - 10, y - 10, x + 10, y + 10);

            } else {

                r = data.curr.rect;
                x = r.x + r.w / 2;
                y = r.y + r.h / 2;
            }

            Rect be = elements[0].getBounds();

            if (be.isValid()) {
                // At least the first element has a bound. Draw rectangles
                // for all dropped elements with valid bounds, offset at
                // the drop point.

                int offsetX = x - be.x;
                int offsetY = y - be.y;

                if ("alignTop" in data.curr.attr && "alignBottom" in data.curr.attr) {
                    offsetY -= be.h / 2;
                } else if ("above" in data.curr.attr || "alignTop" in data.curr.attr) {
                    offsetY -= be.h;
                }
                if ("alignRight" in data.curr.attr && "alignLeft" in data.curr.attr) {
                    offsetX -= be.w / 2;
                } else if ("toLeftOf" in data.curr.attr || "alignLeft" in data.curr.attr) {
                    offsetX -= be.w;
                }

                gc.setForeground(gc.registerColor(0x00FFFF00));

                for (element in elements) {
                    drawElement(gc, element, offsetX, offsetY);
                }
            }
        }
    }

    void onDropLeave(INode targetNode, IDragElement[] elements, DropFeedback feedback) {
        // Free the last captured rect, if any
        feedback.captureArea = null;
    }

    void onDropped(INode targetNode,
                   IDragElement[] elements,
                   DropFeedback feedback,
                   Point p) {
        def data = feedback.userData;
        if (!data.curr) {
            return;
        }

        def index = data.index;
        def insertPos = data.insertPos;

        // Collect IDs from dropped elements and remap them to new IDs
        // if this is a copy or from a different canvas.
        def idMap = getDropIdMap(targetNode, elements, feedback.isCopy || !feedback.sameCanvas);

        targetNode.editXml("Add elements to RelativeLayout") {

            // Now write the new elements.
            for (element in elements) {
                String fqcn = element.getFqcn();
                Rect be = element.getBounds();

                // index==-1 means to insert at the end.
                // Otherwise increment the insertion position.
                if (index >= 0) {
                    index++;
                }

                INode newChild = targetNode.insertChildAt(fqcn, index);

                // Copy all the attributes, modifying them as needed.
                def attrFilter = getLayoutAttrFilter();
                addAttributes(newChild, element, idMap) {
                    uri, name, value ->
                    // TODO need a better way to exclude other layout attributes dynamically
                    if (uri == ANDROID_URI && name in attrFilter) {
                        return false; // don't set these attributes
                    } else {
                        return value;
                    }
                };

// TODO... seems totally wrong. REVISIT or EXPLAIN
                String id = null;
                if (data.child) {
                    id = data.child.getStringAttr(ANDROID_URI, ATTR_ID);
                }

                data.curr.attr.each {
                    newChild.setAttribute(ANDROID_URI, "layout_${it}", id ? id : "true");
                }

                addInnerElements(newChild, element, idMap);
            }
        }
    }


}
