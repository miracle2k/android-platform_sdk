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
 * An {@link IViewRule} for android.widget.LinearLayout and all its derived classes.
 */
public class AndroidWidgetLinearLayoutRule extends BaseLayout {

    // ==== Drag'n'drop support ====

    DropFeedback onDropEnter(INode targetNode, IDragElement[] elements) {

        if (elements.length == 0) {
            return null;
        }

        def bn = targetNode.getBounds();
        if (!bn.isValid()) {
            return;
        }

        boolean isVertical = targetNode.getStringAttr(ANDROID_URI, "orientation") == "vertical";

        // Prepare a list of insertion points: X coords for horizontal, Y for vertical.
        // Each list is a tuple: 0=pixel coordinate, 1=index of children or -1 for "at end".
        def indexes = [ ];

        int last = isVertical ? bn.y : bn.x;
        int pos = 0;
        targetNode.getChildren().each {
            def bc = it.getBounds();
            if (bc.isValid()) {
                // add an insertion point between the last point and the start of this child
                int v = isVertical ? bc.y : bc.x;
                v = (last + v) / 2;
                indexes.add( [v, pos++] );

                last = isVertical ? (bc.y + bc.h) : (bc.x + bc.w);
            }
        }

        int v = isVertical ? (bn.y + bn.h) : (bn.x + bn.w);
        v = (last + v) / 2;
        indexes.add( [v, -1] );

        return new DropFeedback(
          [ "isVertical": isVertical,   // boolean: True if vertical linear layout
            "indexes": indexes,         // list(tuple(0:int, 1:int)): insert points (pixels + index)
            "curr_x": null,             // int: Current marker X position
            "curr_y": null,             // int: Current marker Y position
            "insert_pos": -1            // int: Current drop insert index (-1 for "at the end")
          ],
          {
            gc, node, feedback ->
            // Paint closure for the LinearLayout.
            // This is called by the canvas when a draw is needed.

            drawFeedback(gc, node, elements, feedback);
        });
    }

    void drawFeedback(IGraphics gc,
                      INode node,
                      IDragElement[] elements,
                      DropFeedback feedback) {
        Rect b = node.getBounds();
        if (!b.isValid()) {
            return;
        }

        // Highlight the receiver
        gc.setForeground(gc.registerColor(0x00FFFF00));
        gc.setLineStyle(IGraphics.LineStyle.LINE_SOLID);
        gc.setLineWidth(2);
        gc.drawRect(b);

        gc.setLineStyle(IGraphics.LineStyle.LINE_DOT);
        gc.setLineWidth(1);

        def indexes = feedback.userData.indexes;
        boolean isVertical = feedback.userData.isVertical;

        indexes.each {
            int i = it[0];
            if (isVertical) {
                // draw horizontal lines
                gc.drawLine(b.x, i, b.x + b.w, i);
            } else {
                // draw vertical lines
                gc.drawLine(i, b.y, i, b.y + b.h);
            }
        }

        def curr_x = feedback.userData.curr_x;
        def curr_y = feedback.userData.curr_y;

        if (curr_x != null && curr_y != null) {
            int x = curr_x;
            int y = curr_y;

            // Draw a mark at the drop point.
            gc.setLineStyle(IGraphics.LineStyle.LINE_SOLID);
            gc.setLineWidth(2);

            gc.drawLine(x - 10, y - 10, x + 10, y + 10);
            gc.drawLine(x + 10, y - 10, x - 10, y + 10);
            gc.drawOval(x - 10, y - 10, x + 10, y + 10);

            gc.setLineWidth(1);

            Rect be = elements[0].getBounds();

            if (be.isValid()) {
                // At least the first element has a bound. Draw rectangles
                // for all dropped elements with valid bounds, offset at
                // the drop point.

                int offsetX = x - be.x;
                int offsetY = y - be.y;

                // If there's a parent, keep the X/Y coordinate the same relative to the parent.
                Rect pb = elements[0].getParentBounds();
                if (pb.isValid()) {
                    if (isVertical) {
                        offsetX = b.x - pb.x;
                    } else {
                        offsetY = b.y - pb.y;
                    }
                }

                elements.each {
                    drawElement(gc, it, offsetX, offsetY);
                }
            }
        }
    }

    DropFeedback onDropMove(INode targetNode,
                            IDragElement[] elements,
                            DropFeedback feedback,
                            Point p) {
        def data = feedback.userData;

        Rect b = targetNode.getBounds();
        if (!b.isValid()) {
            return feedback;
        }

        boolean isVertical = data.isVertical;

        int bestDist = Integer.MAX_VALUE;
        int bestIndex = Integer.MIN_VALUE;
        int bestPos = null;

        for(index in data.indexes) {
            int i   = index[0];
            int pos = index[1];
            int dist = (isVertical ? p.y : p.x) - i;
            if (dist < 0) dist = - dist;
            if (dist < bestDist) {
                bestDist = dist;
                bestIndex = i;
                bestPos = pos;
                if (bestDist <= 0) break;
            }
        }

        if (bestIndex != Integer.MIN_VALUE) {
            def old_x = data.curr_x;
            def old_y = data.curr_y;

            if (isVertical) {
                data.curr_x = b.x + b.w / 2;
                data.curr_y = bestIndex;
            } else {
                data.curr_x = bestIndex;
                data.curr_y = b.y + b.h / 2;
            }

            data.insert_pos = bestPos;

            feedback.requestPaint = (old_x != data.curr_x) || (old_y != data.curr_y);
        }

        return feedback;
    }

    void onDropLeave(INode targetNode, IDragElement[] elements, DropFeedback feedback) {
        // ignore
    }

    void onDropped(INode targetNode,
                   IDragElement[] elements,
                   DropFeedback feedback,
                   Point p,
                   boolean isCopy,
                   boolean sameCanvas) {

        int insert_pos = feedback.userData.insert_pos;

        // Collect IDs from dropped elements and remap them to new IDs
        // if this is a copy or from a different canvas.
        def id_map = getDropIdMap(targetNode, elements, isCopy || !sameCanvas);

        targetNode.editXml("Add elements to LinearLayout") {

            // Now write the new elements.
            elements.each { element ->
                String fqcn = element.getFqcn();
                Rect be = element.getBounds();

                INode newChild = targetNode.insertChildAt(fqcn, insert_pos);

                // insert_pos==-1 means to insert at the end. Otherwise
                // increment the insertion position.
                if (insert_pos >= 0) {
                    insert_pos++;
                }

                // Copy all the attributes, modifying them as needed.
                addAttributes(newChild, element, id_map) {
                    uri, name, value ->
                    // TODO exclude original parent attributes
                    if (name == "layout_x" || name == "layout_y") {
                        return false; // don't set these attributes
                    } else {
                        return value;
                    }
                };

                addInnerElements(newChild, element, id_map);
            }
        }


    }
}
