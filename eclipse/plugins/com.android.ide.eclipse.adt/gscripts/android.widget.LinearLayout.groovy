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

    public static String ATTR_ORIENTATION = "orientation";
    public static String VALUE_HORIZONTAL = "horizontal";
    public static String VALUE_VERTICAL = "vertical";

    /**
     * Add an explicit Orientation toggle to the context menu.
     */
    public List<MenuAction> getContextMenu(INode selectedNode) {

        def curr_orient = selectedNode.getStringAttr(ANDROID_URI, ATTR_ORIENTATION);
        if (!curr_orient) {
            curr_orient = VALUE_HORIZONTAL;
        }

        def onChange = { MenuAction.Action action, String valueId, Boolean newValue ->
            def actionId = action.getId();
            def node = selectedNode;

            if (actionId == "_orientation") {
                node.editXml("Change LinearLayout " + ATTR_ORIENTATION, {
                    node.setAttribute(ANDROID_URI, ATTR_ORIENTATION, valueId);
                } as INodeHandler)
            }
        } as IMenuCallback

        return super.getContextMenu(selectedNode) +
               [ new MenuAction.Choices("_orientation", "Orientation",
                        [ horizontal : "Horizontal",
                          vertical : "Vertical" ],
                        curr_orient,
                        onChange ),
               ];
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

        boolean isVertical =
            targetNode.getStringAttr(ANDROID_URI, ATTR_ORIENTATION) == VALUE_VERTICAL;

        // Prepare a list of insertion points: X coords for horizontal, Y for vertical.
        // Each list is a tuple: 0=pixel coordinate, 1=index of children or -1 for "at end".
        def indexes = [ ];

        int last = isVertical ? bn.y : bn.x;
        int pos = 0;
        boolean lastDragged = false;
        int selfPos = -1;
        targetNode.getChildren().each {
            def bc = it.getBounds();
            if (bc.isValid()) {
                // First see if this node looks like it's the same as one of the *dragged* bounds
                boolean isDragged = false;
                for (element in elements) {
                    // This tries to determine if an INode corresponds to an IDragElement, by
                    // comparing their bounds.
                    if (bc == element.getBounds()) {
                        isDragged = true;
                    }
                }

                // We don't want to insert drag positions before or after the element
                // that is itself being dragged. However, we -do- want to insert a match
                // position here, at the center, such that when you drag near its current
                // position we show a match right where it's already positioned.
                if (isDragged) {
                    int v = isVertical ? bc.y + (bc.h / 2) : bc.x + (bc.w / 2);
                    selfPos = pos;
                    indexes.add( [v, pos++] );
                } else if (lastDragged) {
                    // Even though we don't want to insert a match below, we need to increment
                    // the index counter such that subsequent lines know their correct
                    // index in the child list.
                    pos++;
                } else {
                    // Add an insertion point between the last point and the start of this child
                    int v = isVertical ? bc.y : bc.x;
                    v = (last + v) / 2;
                    indexes.add( [v, pos++] );
                }

                last = isVertical ? (bc.y + bc.h) : (bc.x + bc.w);
                lastDragged = isDragged;
            }
        }

        // Finally add an insert position after all the children - unless of course we happened
        // to be dragging the last element
        if (!lastDragged) {
            int v = last + 1;
            indexes.add( [v, -1] );
        }

        return new DropFeedback(
          [ "isVertical": isVertical,   // boolean: True if vertical linear layout
            "indexes": indexes,         // list(tuple(0:int, 1:int)): insert points (pixels + index)
            "currX": null,              // int: Current marker X position
            "currY": null,              // int: Current marker Y position
            "selfPos": selfPos,         // int: Position of the dragged element in this layout (or
                                        //      -1 if the dragged element is from elsewhere)
            "insertPos": -1             // int: Current drop insert index (-1 for "at the end")
          ],
          {
            gc, node, feedback ->
            // Paint closure for the LinearLayout.
            // This is called by the canvas when a draw is needed.

            drawFeedback(gc, node, elements, feedback);
        } as IFeedbackPainter);
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
        gc.useStyle(DrawingStyle.DROP_RECIPIENT);
        gc.drawRect(b);

        gc.useStyle(DrawingStyle.DROP_ZONE);

        def indexes = feedback.userData.indexes;
        boolean isVertical = feedback.userData.isVertical;
        def selfPos = feedback.userData.selfPos;

        indexes.each {
            int i = it[0];
            int pos = it[1];
            // Don't show insert drop zones for "self"-index since that one goes
            // right through the center of the widget rather than in a sibling position
            if (pos != selfPos) {
                if (isVertical) {
                    // draw horizontal lines
                    gc.drawLine(b.x, i, b.x + b.w, i);
                } else {
                    // draw vertical lines
                    gc.drawLine(i, b.y, i, b.y + b.h);
                }
            }
        }

        def currX = feedback.userData.currX;
        def currY = feedback.userData.currY;

        if (currX != null && currY != null) {
            gc.useStyle(DrawingStyle.DROP_ZONE_ACTIVE);

            int x = currX;
            int y = currY;

            Rect be = elements[0].getBounds();

            // Draw a clear line at the closest drop zone (unless we're over the dragged
            // element itself)
            if (feedback.userData.insertPos != selfPos || selfPos == -1) {
                gc.useStyle(DrawingStyle.DROP_PREVIEW);
                if (feedback.userData.width != null) {
                    int width = feedback.userData.width;
                    int fromX = x - width / 2;
                    int toX = x + width / 2;
                    gc.drawLine(fromX, y, toX, y);
                } else if (feedback.userData.height != null) {
                    int height = (int)feedback.userData.height;
                    int fromY = y - height / 2;
                    int toY = y + height / 2;
                    gc.drawLine(x, fromY, x, toY);
                }
            }

            if (be.isValid()) {
                // At least the first element has a bound. Draw rectangles
                // for all dropped elements with valid bounds, offset at
                // the drop point.
                int offsetX;
                int offsetY;
                if (isVertical) {
                    offsetX = b.x - be.x;
                    offsetY = currY - be.y - (be.h / 2);
                } else {
                    offsetX = currX - be.x - (be.w / 2);
                    offsetY = b.y - be.y;
                }

                gc.useStyle(DrawingStyle.DROP_PREVIEW);
                for (element in elements) {
                    drawElement(gc, element, offsetX, offsetY);
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
            def old_x = data.currX;
            def old_y = data.currY;

            if (isVertical) {
                data.currX = b.x + b.w / 2;
                data.currY = bestIndex;
                data.width = b.w;
            } else {
                data.currX = bestIndex;
                data.currY = b.y + b.h / 2;
                data.height = b.h;
            }

            data.insertPos = bestPos;

            feedback.requestPaint = (old_x != data.currX) || (old_y != data.currY);
        }

        return feedback;
    }

    void onDropLeave(INode targetNode, IDragElement[] elements, DropFeedback feedback) {
        // ignore
    }

    void onDropped(INode targetNode,
                   IDragElement[] elements,
                   DropFeedback feedback,
                   Point p) {

        int insertPos = feedback.userData.insertPos;

        // Collect IDs from dropped elements and remap them to new IDs
        // if this is a copy or from a different canvas.
        def idMap = getDropIdMap(targetNode, elements, feedback.isCopy || !feedback.sameCanvas);

        targetNode.editXml("Add elements to LinearLayout", {

            // Now write the new elements.
            for (element in elements) {
                String fqcn = element.getFqcn();
                Rect be = element.getBounds();

                INode newChild = targetNode.insertChildAt(fqcn, insertPos);

                // insertPos==-1 means to insert at the end. Otherwise
                // increment the insertion position.
                if (insertPos >= 0) {
                    insertPos++;
                }

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

                addInnerElements(newChild, element, idMap);
            }
        } as INodeHandler)
    }
}
