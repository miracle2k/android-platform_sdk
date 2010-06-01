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
 * An {@link IViewRule} for android.widget.AbsoluteLayout and all its derived classes.
 */
public class AndroidWidgetAbsoluteLayoutRule extends BaseLayout {

    // ==== Drag'n'drop support ====
    // The AbsoluteLayout accepts any drag'n'drop anywhere on its surface.

    DropFeedback onDropEnter(INode targetNode, IDragElement[] elements) {

        if (elements.length == 0) {
            return null;
        }

        // If one of the top elements is ourselve, refuse the drop.
        // TODO actually that is OK for a DROP_COPY but not for DROP_MOVE.
        targetNode.debugPrintf("TARGET: ${targetNode}, FIRST: ${elements[0].getNode()}");

        for (elem in elements) {
            if (elem.getNode() == targetNode) {
                return null;
            }
        }

        return new DropFeedback(
            [ "p": null ],      // Point: last cursor position
            {
                gc, node, feedback ->
                // Paint closure for the AbsoluteLayout.
                // This is called by the canvas when a draw is needed.

                drawFeedback(gc, node, elements, feedback);
            });
    }

    void drawFeedback(IGraphics gc,
                      INode targetNode,
                      IDragElement[] elements,
                      DropFeedback feedback) {
        Rect b = targetNode.getBounds();
        if (!b.isValid()) {
            return;
        }

        // Highlight the receiver
        gc.setForeground(gc.registerColor(0x00FFFF00));
        gc.setLineStyle(IGraphics.LineStyle.LINE_SOLID);
        gc.setLineWidth(2);
        gc.drawRect(b);

        // Get the drop point
        Point p = feedback.userData.p;

        if (p == null) {
            return;
        }

        int x = p.x;
        int y = p.y;

        def firstNode = elements[0].getNode();
        Rect be = firstNode == null ? null : firstNode.getBounds();

        if (be != null && be.isValid()) {
            // At least the first element has a bound. Draw rectangles
            // for all dropped elements with valid bounds, offset at
            // the drop point.
            int offsetX = x - be.x;
            int offsetY = y - be.y;
            elements.each {
                drawElement(gc, it, offsetX, offsetY);
            }
        } else {
            // We don't have bounds for new elements. In this case
            // just draw a mark at the drop point.
            gc.drawLine(x - 10, y - 10, x + 10, y + 10);
            gc.drawLine(x + 10, y - 10, x - 10, y + 10);
            gc.drawOval(x - 10, y - 10, x + 10, y + 10);
        }
    }

    void drawElement(IGraphics gc, IDragElement element, int offsetX, int offsetY) {
        INode node = element.getNode();
        if (node != null) {
            Rect b = node.getBounds();

            if (b.isValid()) {
                b = b.copy().offsetBy(offsetX, offsetY);
                gc.drawRect(b);
            }
        }

        element.getInnerElements().each {
            drawElement(gc, it, offsetX, offsetY);
        }
    }

    DropFeedback onDropMove(INode targetNode,
                            IDragElement[] elements,
                            DropFeedback feedback,
                            Point p) {
        // Update the data used by the DropFeedback.paintClosure above.
        feedback.userData.p = p;
        feedback.requestPaint = true;
        return feedback;
    }

    void onDropLeave(INode targetNode, IDragElement[] elements, DropFeedback feedback) {
        // Nothing to do.
    }

    void onDropped(INode targetNode,
                   IDragElement[] elements,
                   DropFeedback feedback,
                   Point p,
                   boolean isCopy,
                   boolean sameCanvas) {

        Rect b = targetNode.getBounds();
        if (!b.isValid()) {
            return;
        }

        int x = p.x - b.x;
        int y = p.y - b.y;

        // TODO DEBUG remove later
        targetNode.debugPrintf("AbsL.drop at coord ${x}x${y}");

        def id_map = [:];

        // Need to remap ids if necessary
        if (isCopy || !sameCanvas) {
            collecIds(id_map, elements);
            // TODO remap
        }

        targetNode.editXml("Add elements to AbsoluteLayout") {
            // Now write the new elements.
            elements.each { element ->
                String fqcn = element.getFqcn();
                def srcNode = element.getNode();
                Rect be = srcNode == null ? null : srcNode.getBounds();

                INode n = targetNode.appendChild(fqcn);

                // Copy all the attributes, modifying them as needed.
                boolean hasX = false;
                boolean hasY = false;
                element.getAttributes().each { attr ->
                    String uri = attr.getUri();
                    String name = attr.getName();
                    String value = attr.getValue();

                    if (uri == ANDROID_URI) {
                        if (name == "id") {
                            value = id_map.get(value, value);

                        } else if (name == "layout_x") {
                            hasX = true;

                        } else if (name == "layout_y") {
                            hasY = true;
                        }

                        n.setAttribute(uri, name, value);
                    }
                }

                if (!hasX) {
                    n.setAttribute("layout_x", "${x}dip");
                    x += 10;
                }
                if (!hasY) {
                    n.setAttribute("layout_y", "${y}dip");
                    if (be != null && be.isValid()) {
                        y += be.h;
                    } else {
                        y += 10;
                    }
                }

                addInnerElements(n, element.getInnerElements(), id_map);
            }
        }
    }

    void addInnerElements(INode node, elements, id_map) {
        elements.each { element ->
            INode n = node.appendChild(element.getFqcn());

            element.getAttributes().each { attr ->
                String uri = attr.getUri();
                String name = attr.getName();
                String value = attr.getValue();

                if (uri == ANDROID_URI) {
                    if (name == "id") {
                        value = id_map.get(value, value);
                    }

                    n.setAttribute(uri, name, value);
                }
            }

            addInnerElements(n, element.getInnerElements(), id_map);
        }
    }

    void collectIds(id_map, elements) {
        elements.each { element ->
            String id = element.getAttribute(ANDROID_URI, "id");
            if (id != null) {
                id_map.put(id, id);
            }

            collectIds(id_map, element.getInnerElements());
        }
    }
}
