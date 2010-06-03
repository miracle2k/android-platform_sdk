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
        for (elem in elements) {
            if (elem.getNode().is(targetNode)) {
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

        def id_map = [:];

        // Need to remap ids if necessary
        if (isCopy || !sameCanvas) {
            collectIds(id_map, elements);
            id_map = remapIds(targetNode, id_map);
        }

        targetNode.editXml("Add elements to AbsoluteLayout") {
            // Now write the new elements.
            elements.each { element ->
                String fqcn = element.getFqcn();
                def srcNode = element.getNode();

                Rect be = srcNode == null ? null : srcNode.getBounds();

                INode newChild = targetNode.appendChild(fqcn);

                // Copy all the attributes, modifying them as needed.
                addAttributes(newChild, element.getAttributes(), id_map) {
                    uri, name, value ->
                    if (name == "layout_x" || name == "layout_y") {
                        return false; // don't set these attributes
                    } else {
                        return value;
                    }
                };

                // TODO for the 2..n elements see if the they have a x/y expressed
                // in dp or dip and use it to recompute a relative position to the
                // first dragged element. If they don't use dp/dip, we can still use
                // the canvas bounds if available.
                newChild.setAttribute(ANDROID_URI, "layout_x", "${x}dip");
                x += 10;

                newChild.setAttribute(ANDROID_URI, "layout_y", "${y}dip");
                if (be != null && be.isValid()) {
                    y += be.h;
                } else {
                    y += 10;
                }

                def children = element.getInnerElements();
                addInnerElements(newChild, element.getInnerElements(), id_map);
            }
        }
    }

    void addAttributes(INode newNode, oldAttributes, id_map, Closure filter) {
        for (attr in oldAttributes) {
                    String uri = attr.getUri();
                    String name = attr.getName();
                    String value = attr.getValue();

            if (uri == ANDROID_URI && name == "id") {
                if (id_map.containsKey(value)) {
                    value = id_map[value][0];
                }
            }

            if (filter != null) {
                value = filter(uri, name, value);
            }
            if (value != null && value != false && value != "") {
                newNode.setAttribute(uri, name, value);
            }
        }
    }

    void addInnerElements(INode node, IDragElement[] elements, id_map) {

        elements.each { element ->
            String fqcn = element.getFqcn();
            INode newNode = node.appendChild(fqcn);

            addAttributes(newNode, element.getAttributes(), id_map, null /* closure */);
            addInnerElements(newNode, element.getInnerElements(), id_map);
        }
                        }

    /**
     * Fills id_map with a map String id => tuple (String id, String fqcn)
     * where fqcn is the FQCN of the element (in case we want to generate
     * new IDs based on the element type.)
     */
    void collectIds(id_map, IDragElement[] elements) {
        elements.each { element ->
            def attr = element.getAttribute(ANDROID_URI, "id");
            if (attr != null) {
                String id = attr.getValue();
                if (id != null && id != "") {
                    id_map.put(id, [id, element.getFqcn()]);
                    }
                }

            collectIds(id_map, element.getInnerElements());
                    }
                }

    Object remapIds(INode node, id_map) {
        // Visit the document to get a list of existing ids
        def existing_ids = [:];
        collectExistingIds(node.getRoot(), existing_ids);

        def new_map = [:];
        id_map.each() { key, value ->
            def id = normalizeId(key);

            if (!existing_ids.containsKey(id)) {
                // Not a conflict. Use as-is.
                new_map.put(key, value);
                if (key != id) {
                    new_map.put(id, value);
            }
            } else {
                // There is a conflict. Get a new id.
                def new_id = findNewId(value[1], existing_ids);
                value[0] = new_id;
                new_map.put(id, value);
                new_map.put(id.replaceFirst("@\\+", "@"), value);
        }
    }

        return new_map;
    }

    String findNewId(String fqcn, existing_ids) {
        // Get the last component of the FQCN (e.g. "android.view.Button" => "Button")
        String name = fqcn[fqcn.lastIndexOf(".")+1 .. fqcn.length()-1];

        for (int i = 1; i < 1000000; i++) {
            String id = String.format("@+id/%s%02d", name, i);
            if (!existing_ids.containsKey(id)) {
                existing_ids.put(id, id);
                return id;
            }
                    }

        // We'll never reach here.
        return null;
                }

    void collectExistingIds(INode root, existing_ids) {
        if (root == null) {
            return;
            }

        def id = root.getStringAttr(ANDROID_URI, "id");
        if (id != null) {
            id = normalizeId(id);

            if (!existing_ids.containsKey(id)) {
                existing_ids.put(id, id);
        }
    }

        root.getChildren().each {
            collectExistingIds(it, existing_ids);
        }
            }

    /** Transform @id/name into @+id/name to treat both forms the same way. */
    String normalizeId(String id) {
        if (id.indexOf("@+") == -1) {
            id = id.replaceFirst("@", "@+");
        }
        return id;
    }
}
