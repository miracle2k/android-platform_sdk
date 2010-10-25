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

import com.android.ide.common.api.DrawingStyle;
import com.android.ide.common.api.DropFeedback;
import com.android.ide.common.api.IDragElement;
import com.android.ide.common.api.IFeedbackPainter;
import com.android.ide.common.api.IGraphics;
import com.android.ide.common.api.INode;
import com.android.ide.common.api.INodeHandler;
import com.android.ide.common.api.IViewRule;
import com.android.ide.common.api.Point;
import com.android.ide.common.api.Rect;

import java.util.Map;

/**
 * An {@link IViewRule} for android.widget.FrameLayout and all its derived
 * classes.
 */
public class FrameLayoutRule extends BaseLayout {

    // ==== Drag'n'drop support ====
    // The FrameLayout accepts any drag'n'drop anywhere on its surface.

    @Override
    public DropFeedback onDropEnter(INode targetNode, final IDragElement[] elements) {
        if (elements.length == 0) {
            return null;
        }

        return new DropFeedback(null, new IFeedbackPainter() {
            public void paint(IGraphics gc, INode node, DropFeedback feedback) {
                drawFeedback(gc, node, elements, feedback);
            }
        });
    }

    void drawFeedback(IGraphics gc, INode targetNode, IDragElement[] elements, DropFeedback feedback) {
        Rect b = targetNode.getBounds();
        if (!b.isValid()) {
            return;
        }

        gc.useStyle(DrawingStyle.DROP_RECIPIENT);
        gc.drawRect(b);

        // Get the drop point
        Point p = (Point) feedback.userData;

        if (p == null) {
            return;
        }

        Rect be = elements[0].getBounds();

        gc.useStyle(DrawingStyle.DROP_PREVIEW);
        if (be.isValid()) {
            // At least the first element has a bound. Draw rectangles
            // for all dropped elements with valid bounds, offset at
            // (0,0)
            for (IDragElement it : elements) {
                Rect currBounds = it.getBounds();
                if (currBounds.isValid()) {
                    int offsetX = b.x - currBounds.x;
                    int offsetY = b.y - currBounds.y;
                    drawElement(gc, it, offsetX, offsetY);
                }
            }
        } else {
            // We don't have bounds for new elements. In this case
            // just draw insert lines indicating the top left corner where
            // the item will be placed

            // +1: Place lines fully within the view (the stroke width is 2) to
            // make
            // it even more visually obvious
            gc.drawLine(b.x + 1, b.y, b.x + 1, b.y + b.h);
            gc.drawLine(b.x, b.y + 1, b.x + b.w, b.y + 1);
        }
    }

    @Override
    public DropFeedback onDropMove(INode targetNode, IDragElement[] elements,
            DropFeedback feedback, Point p) {
        feedback.userData = p;
        feedback.requestPaint = true;
        return feedback;
    }

    @Override
    public void onDropLeave(INode targetNode, IDragElement[] elements, DropFeedback feedback) {
        // ignore
    }

    @Override
    public void onDropped(final INode targetNode, final IDragElement[] elements,
            final DropFeedback feedback, final Point p) {
        Rect b = targetNode.getBounds();
        if (!b.isValid()) {
            return;
        }

        // Collect IDs from dropped elements and remap them to new IDs
        // if this is a copy or from a different canvas.
        final Map<String, Pair<String, String>> idMap = getDropIdMap(targetNode, elements,
                feedback.isCopy || !feedback.sameCanvas);

        targetNode.editXml("Add elements to FrameLayout", new INodeHandler() {

            public void handle(INode node) {

                // Now write the new elements.
                for (IDragElement element : elements) {
                    String fqcn = element.getFqcn();

                    INode newChild = targetNode.appendChild(fqcn);

                    // Copy all the attributes, modifying them as needed.
                    addAttributes(newChild, element, idMap, DEFAULT_ATTR_FILTER);

                    addInnerElements(newChild, element, idMap);
                }
            }
        });
    }
}
