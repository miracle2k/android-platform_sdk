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
 * An {@link IViewRule} for android.widget.AbsoluteLayout and all its derived
 * classes.
 */
public class AbsoluteLayoutRule extends BaseLayout {

    // ==== Drag'n'drop support ====
    // The AbsoluteLayout accepts any drag'n'drop anywhere on its surface.

    @Override
    public DropFeedback onDropEnter(final INode targetNode, final IDragElement[] elements) {

        if (elements.length == 0) {
            return null;
        }

        return new DropFeedback(null, new IFeedbackPainter() {
            public void paint(IGraphics gc, INode node, DropFeedback feedback) {
                // Paint callback for the AbsoluteLayout.
                // This is called by the canvas when a draw is needed.
                drawFeedback(gc, node, elements, feedback);
            }
        });
    }

    void drawFeedback(
            IGraphics gc,
            INode targetNode,
            IDragElement[] elements,
            DropFeedback feedback) {
        Rect b = targetNode.getBounds();
        if (!b.isValid()) {
            return;
        }

        // Highlight the receiver
        gc.useStyle(DrawingStyle.DROP_RECIPIENT);
        gc.drawRect(b);

        // Get the drop point
        Point p = (Point) feedback.userData;

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
            gc.useStyle(DrawingStyle.DROP_PREVIEW);
            for (IDragElement element : elements) {
                drawElement(gc, element, offsetX, offsetY);
            }
        } else {
            // We don't have bounds for new elements. In this case
            // just draw cross hairs to the drop point.
            gc.useStyle(DrawingStyle.GUIDELINE);
            gc.drawLine(x, b.y, x, b.y + b.h);
            gc.drawLine(b.x, y, b.x + b.w, y);

            // Use preview lines to indicate the bottom quadrant as well (to
            // indicate that you are looking at the top left position of the
            // drop, not the center for example)
            gc.useStyle(DrawingStyle.DROP_PREVIEW);
            gc.drawLine(x, y, b.x + b.w, y);
            gc.drawLine(x, y, x, b.y + b.h);
        }
    }

    @Override
    public DropFeedback onDropMove(INode targetNode, IDragElement[] elements,
            DropFeedback feedback, Point p) {
        // Update the data used by the DropFeedback.paintCallback above.
        feedback.userData = p;
        feedback.requestPaint = true;

        return feedback;
    }

    @Override
    public void onDropLeave(INode targetNode, IDragElement[] elements, DropFeedback feedback) {
        // Nothing to do.
    }

    @Override
    public void onDropped(final INode targetNode, final IDragElement[] elements,
            final DropFeedback feedback, final Point p) {

        final Rect b = targetNode.getBounds();
        if (!b.isValid()) {
            return;
        }

        // Collect IDs from dropped elements and remap them to new IDs
        // if this is a copy or from a different canvas.
        final Map<String, Pair<String, String>> idMap = getDropIdMap(targetNode, elements,
                feedback.isCopy || !feedback.sameCanvas);

        targetNode.editXml("Add elements to AbsoluteLayout", new INodeHandler() {
            public void handle(INode node) {
                boolean first = true;
                Point offset = null;

                // Now write the new elements.
                for (IDragElement element : elements) {
                    String fqcn = element.getFqcn();
                    Rect be = element.getBounds();

                    INode newChild = targetNode.appendChild(fqcn);

                    // Copy all the attributes, modifying them as needed.
                    addAttributes(newChild, element, idMap, DEFAULT_ATTR_FILTER);

                    int x = p.x - b.x;
                    int y = p.y - b.y;

                    if (first) {
                        first = false;
                        if (be.isValid()) {
                            offset = new Point(x - be.x, y - be.y);
                        }
                    } else if (offset != null && be.isValid()) {
                        x = offset.x + be.x;
                        y = offset.y + be.y;
                    } else {
                        x += 10;
                        y += be.isValid() ? be.h : 10;
                    }

                    newChild.setAttribute(ANDROID_URI, "layout_x", //$NON-NLS-1$
                            x + "dip"); //$NON-NLS-1$
                    newChild.setAttribute(ANDROID_URI, "layout_y", //$NON-NLS-1$
                            y + "dip"); //$NON-NLS-1$

                    addInnerElements(newChild, element, idMap);
                }
            }
        });
    }
}
