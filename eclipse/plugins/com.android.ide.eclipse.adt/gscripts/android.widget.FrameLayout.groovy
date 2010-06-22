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
 * An {@link IViewRule} for android.widget.FrameLayout and all its derived classes.
 */
public class AndroidWidgetFrameLayoutRule extends BaseLayout {

    // ==== Drag'n'drop support ====
    // The FrameLayout accepts any drag'n'drop anywhere on its surface.

    DropFeedback onDropEnter(INode targetNode, IDragElement[] elements) {
        if (elements.length == 0) {
            return null;
        }

        return new DropFeedback(
            [ "p": null ],      // Point: last cursor position
            {
                gc, node, feedback ->
                // Paint closure for the FrameLayout.

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

        Rect be = elements[0].getBounds();

        if (be.isValid()) {
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

    DropFeedback onDropMove(INode targetNode,
                            IDragElement[] elements,
                            DropFeedback feedback,
                            Point p) {
        feedback.userData.p = p;
        feedback.requestPaint = true;
        return feedback;
    }

    void onDropLeave(INode targetNode, IDragElement[] elements, DropFeedback feedback) {
                // ignore
    }

    void onDropped(INode targetNode,
                   IDragElement[] elements,
                   DropFeedback feedback,
                   Point p) {
        Rect b = targetNode.getBounds();
        if (!b.isValid()) {
            return;
        }

        // Collect IDs from dropped elements and remap them to new IDs
        // if this is a copy or from a different canvas.
        def idMap = getDropIdMap(targetNode, elements, feedback.isCopy || !feedback.sameCanvas);

        targetNode.editXml("Add elements to FrameLayout") {

            // Now write the new elements.
            for (element in elements) {
                String fqcn = element.getFqcn();
                Rect be = element.getBounds();

                INode newChild = targetNode.appendChild(fqcn);

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
        }
    }
}
