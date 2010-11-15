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
import com.android.ide.common.api.IMenuCallback;
import com.android.ide.common.api.INode;
import com.android.ide.common.api.INodeHandler;
import com.android.ide.common.api.IViewRule;
import com.android.ide.common.api.MenuAction;
import com.android.ide.common.api.Point;
import com.android.ide.common.api.Rect;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * An {@link IViewRule} for android.widget.LinearLayout and all its derived
 * classes.
 */
public class LinearLayoutRule extends BaseLayout {
    public static String ATTR_ORIENTATION = "orientation"; //$NON-NLS-1$
    public static String VALUE_HORIZONTAL = "horizontal";  //$NON-NLS-1$
    public static String VALUE_VERTICAL = "vertical";      //$NON-NLS-1$

    /**
     * Add an explicit Orientation toggle to the context menu.
     */
    @Override
    public List<MenuAction> getContextMenu(final INode selectedNode) {

        String curr_orient = selectedNode.getStringAttr(ANDROID_URI, ATTR_ORIENTATION);
        if (curr_orient == null || curr_orient.length() == 0) {
            curr_orient = VALUE_HORIZONTAL;
        }

        IMenuCallback onChange = new IMenuCallback() {
            public void action(MenuAction action, final String valueId, Boolean newValue) {
                String actionId = action.getId();
                final INode node = selectedNode;

                if (actionId.equals("_orientation")) { //$NON-NLS-1$
                    node.editXml("Change LinearLayout " + ATTR_ORIENTATION, new INodeHandler() {
                        public void handle(INode n) {
                            node.setAttribute(ANDROID_URI, ATTR_ORIENTATION, valueId);
                        }
                    });
                }
            }
        };

        return concatenate(super.getContextMenu(selectedNode),
            new MenuAction.Choices("_orientation", "Orientation",  //$NON-NLS-1$
                mapify(
                    "horizontal", "Horizontal",                    //$NON-NLS-1$
                    "vertical", "Vertical"                         //$NON-NLS-1$
                ),
                curr_orient, onChange));
    }

    // ==== Drag'n'drop support ====

    @Override
    public DropFeedback onDropEnter(final INode targetNode, final IDragElement[] elements) {

        if (elements.length == 0) {
            return null;
        }

        Rect bn = targetNode.getBounds();
        if (!bn.isValid()) {
            return null;
        }

        boolean isVertical = VALUE_VERTICAL.equals(targetNode.getStringAttr(ANDROID_URI,
                ATTR_ORIENTATION));

        // Prepare a list of insertion points: X coords for horizontal, Y for
        // vertical.
        List<MatchPos> indexes = new ArrayList<MatchPos>();

        int last = isVertical ? bn.y : bn.x;
        int pos = 0;
        boolean lastDragged = false;
        int selfPos = -1;
        for (INode it : targetNode.getChildren()) {
            Rect bc = it.getBounds();
            if (bc.isValid()) {
                // First see if this node looks like it's the same as one of the
                // *dragged* bounds
                boolean isDragged = false;
                for (IDragElement element : elements) {
                    // This tries to determine if an INode corresponds to an
                    // IDragElement, by comparing their bounds.
                    if (bc.equals(element.getBounds())) {
                        isDragged = true;
                    }
                }

                // We don't want to insert drag positions before or after the
                // element that is itself being dragged. However, we -do- want
                // to insert a match position here, at the center, such that
                // when you drag near its current position we show a match right
                // where it's already positioned.
                if (isDragged) {
                    int v = isVertical ? bc.y + (bc.h / 2) : bc.x + (bc.w / 2);
                    selfPos = pos;
                    indexes.add(new MatchPos(v, pos++));
                } else if (lastDragged) {
                    // Even though we don't want to insert a match below, we
                    // need to increment the index counter such that subsequent
                    // lines know their correct index in the child list.
                    pos++;
                } else {
                    // Add an insertion point between the last point and the
                    // start of this child
                    int v = isVertical ? bc.y : bc.x;
                    v = (last + v) / 2;
                    indexes.add(new MatchPos(v, pos++));
                }

                last = isVertical ? (bc.y + bc.h) : (bc.x + bc.w);
                lastDragged = isDragged;
            } else {
                // We still have to count this position even if it has no bounds, or
                // subsequent children will be inserted at the wrong place
                pos++;
            }
        }

        // Finally add an insert position after all the children - unless of
        // course we happened to be dragging the last element
        if (!lastDragged) {
            int v = last + 1;
            indexes.add(new MatchPos(v, pos));
        }

        int posCount = targetNode.getChildren().length + 1;
        return new DropFeedback(new LinearDropData(indexes, posCount, isVertical, selfPos),
                new IFeedbackPainter() {

                    public void paint(IGraphics gc, INode node, DropFeedback feedback) {
                        // Paint callback for the LinearLayout. This is called
                        // by the canvas when a draw is needed.
                        drawFeedback(gc, node, elements, feedback);
                    }
                });
    }

    void drawFeedback(IGraphics gc, INode node, IDragElement[] elements, DropFeedback feedback) {
        Rect b = node.getBounds();
        if (!b.isValid()) {
            return;
        }

        // Highlight the receiver
        gc.useStyle(DrawingStyle.DROP_RECIPIENT);
        gc.drawRect(b);

        gc.useStyle(DrawingStyle.DROP_ZONE);

        LinearDropData data = (LinearDropData) feedback.userData;
        boolean isVertical = data.isVertical();
        int selfPos = data.getSelfPos();

        for (MatchPos it : data.getIndexes()) {
            int i = it.getDistance();
            int pos = it.getPosition();
            // Don't show insert drop zones for "self"-index since that one goes
            // right through the center of the widget rather than in a sibling
            // position
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

        Integer currX = data.getCurrX();
        Integer currY = data.getCurrY();

        if (currX != null && currY != null) {
            gc.useStyle(DrawingStyle.DROP_ZONE_ACTIVE);

            int x = currX;
            int y = currY;

            Rect be = elements[0].getBounds();

            // Draw a clear line at the closest drop zone (unless we're over the
            // dragged element itself)
            if (data.getInsertPos() != selfPos || selfPos == -1) {
                gc.useStyle(DrawingStyle.DROP_PREVIEW);
                if (data.getWidth() != null) {
                    int width = data.getWidth();
                    int fromX = x - width / 2;
                    int toX = x + width / 2;
                    gc.drawLine(fromX, y, toX, y);
                } else if (data.getHeight() != null) {
                    int height = data.getHeight();
                    int fromY = y - height / 2;
                    int toY = y + height / 2;
                    gc.drawLine(x, fromY, x, toY);
                }
            }

            if (be.isValid()) {
                boolean isLast = data.isLastPosition();

                // At least the first element has a bound. Draw rectangles for
                // all dropped elements with valid bounds, offset at the drop
                // point.
                int offsetX;
                int offsetY;
                if (isVertical) {
                    offsetX = b.x - be.x;
                    offsetY = currY - be.y - (isLast ? 0 : (be.h / 2));

                } else {
                    offsetX = currX - be.x - (isLast ? 0 : (be.w / 2));
                    offsetY = b.y - be.y;
                }

                gc.useStyle(DrawingStyle.DROP_PREVIEW);
                for (IDragElement element : elements) {
                    Rect bounds = element.getBounds();
                    if (bounds.isValid() && (bounds.w > b.w || bounds.h > b.h)) {
                        // The bounds of the child does not fully fit inside the target.
                        // Limit the bounds to the layout bounds.
                        Rect within = new Rect(b.x, b.y,
                                Math.min(bounds.w, b.w), Math.min(bounds.h, b.h));
                        gc.drawRect(within);
                    } else {
                        drawElement(gc, element, offsetX, offsetY);
                    }
                }
            }
        }
    }

    @Override
    public DropFeedback onDropMove(INode targetNode, IDragElement[] elements,
            DropFeedback feedback, Point p) {
        Rect b = targetNode.getBounds();
        if (!b.isValid()) {
            return feedback;
        }

        LinearDropData data = (LinearDropData) feedback.userData;
        boolean isVertical = data.isVertical();

        int bestDist = Integer.MAX_VALUE;
        int bestIndex = Integer.MIN_VALUE;
        Integer bestPos = null;

        for (MatchPos index : data.getIndexes()) {
            int i = index.getDistance();
            int pos = index.getPosition();
            int dist = (isVertical ? p.y : p.x) - i;
            if (dist < 0)
                dist = -dist;
            if (dist < bestDist) {
                bestDist = dist;
                bestIndex = i;
                bestPos = pos;
                if (bestDist <= 0)
                    break;
            }
        }

        if (bestIndex != Integer.MIN_VALUE) {
            Integer oldX = data.getCurrX();
            Integer oldY = data.getCurrY();

            if (isVertical) {
                data.setCurrX(b.x + b.w / 2);
                data.setCurrY(bestIndex);
                data.setWidth(b.w);
                data.setHeight(null);
            } else {
                data.setCurrX(bestIndex);
                data.setCurrY(b.y + b.h / 2);
                data.setWidth(null);
                data.setHeight(b.h);
            }

            data.setInsertPos(bestPos);

            feedback.requestPaint = !equals(oldX, data.getCurrX())
                    || !equals(oldY, data.getCurrY());
        }

        return feedback;
    }

    private static boolean equals(Integer i1, Integer i2) {
        if (i1 == i2) {
            return true;
        } else if (i1 != null) {
            return i1.equals(i2);
        } else {
            // We know i2 != null
            return i2.equals(i1);
        }
    }

    @Override
    public void onDropLeave(INode targetNode, IDragElement[] elements, DropFeedback feedback) {
        // ignore
    }

    @Override
    public void onDropped(final INode targetNode, final IDragElement[] elements,
            final DropFeedback feedback, final Point p) {

        LinearDropData data = (LinearDropData) feedback.userData;
        final int initialInsertPos = data.getInsertPos();

        // Collect IDs from dropped elements and remap them to new IDs
        // if this is a copy or from a different canvas.
        final Map<String, Pair<String, String>> idMap = getDropIdMap(targetNode, elements,
                feedback.isCopy || !feedback.sameCanvas);

        targetNode.editXml("Add elements to LinearLayout", new INodeHandler() {

            public void handle(INode node) {
                // Now write the new elements.
                int insertPos = initialInsertPos;
                for (IDragElement element : elements) {
                    String fqcn = element.getFqcn();

                    INode newChild = targetNode.insertChildAt(fqcn, insertPos);

                    // insertPos==-1 means to insert at the end. Otherwise
                    // increment the insertion position.
                    if (insertPos >= 0) {
                        insertPos++;
                    }

                    // Copy all the attributes, modifying them as needed.
                    addAttributes(newChild, element, idMap, DEFAULT_ATTR_FILTER);

                    addInnerElements(newChild, element, idMap);
                }
            }
        });
    }

    /** A possible match position */
    private class MatchPos {
        /** The pixel distance */
        private int mDistance;
        /** The position among siblings */
        private int mPosition;

        public MatchPos(int distance, int position) {
            this.mDistance = distance;
            this.mPosition = position;
        }

        @Override
        public String toString() {
            return "MatchPos [distance=" + mDistance //$NON-NLS-1$
                    + ", position=" + mPosition      //$NON-NLS-1$
                    + "]";                           //$NON-NLS-1$
        }

        private int getDistance() {
            return mDistance;
        }

        private int getPosition() {
            return mPosition;
        }
    }

    private class LinearDropData {
        /** Vertical layout? */
        private final boolean mVertical;

        /** Insert points (pixels + index) */
        private final List<MatchPos> mIndexes;

        /** Number of insert positions in the target node */
        private final int mNumPositions;

        /** Current marker X position */
        private Integer mCurrX;

        /** Current marker Y position */
        private Integer mCurrY;

        /** Position of the dragged element in this layout (or
            -1 if the dragged element is from elsewhere) */
        private final int mSelfPos;

        /** Current drop insert index (-1 for "at the end") */
        private int mInsertPos = -1;

        /** width of match line if it's a horizontal one */
        private Integer mWidth;

        /** height of match line if it's a vertical one */
        private Integer mHeight;

        public LinearDropData(List<MatchPos> indexes, int numPositions,
                boolean isVertical, int selfPos) {
            this.mIndexes = indexes;
            this.mNumPositions = numPositions;
            this.mVertical = isVertical;
            this.mSelfPos = selfPos;
        }

        @Override
        public String toString() {
            return "LinearDropData [currX=" + mCurrX //$NON-NLS-1$
                    + ", currY=" + mCurrY //$NON-NLS-1$
                    + ", height=" + mHeight //$NON-NLS-1$
                    + ", indexes=" + mIndexes //$NON-NLS-1$
                    + ", insertPos=" + mInsertPos //$NON-NLS-1$
                    + ", isVertical=" + mVertical //$NON-NLS-1$
                    + ", selfPos=" + mSelfPos //$NON-NLS-1$
                    + ", width=" + mWidth //$NON-NLS-1$
                    + "]"; //$NON-NLS-1$
        }

        private boolean isVertical() {
            return mVertical;
        }

        private void setCurrX(Integer currX) {
            this.mCurrX = currX;
        }

        private Integer getCurrX() {
            return mCurrX;
        }

        private void setCurrY(Integer currY) {
            this.mCurrY = currY;
        }

        private Integer getCurrY() {
            return mCurrY;
        }

        private int getSelfPos() {
            return mSelfPos;
        }

        private void setInsertPos(int insertPos) {
            this.mInsertPos = insertPos;
        }

        private int getInsertPos() {
            return mInsertPos;
        }

        private List<MatchPos> getIndexes() {
            return mIndexes;
        }

        private void setWidth(Integer width) {
            this.mWidth = width;
        }

        private Integer getWidth() {
            return mWidth;
        }

        private void setHeight(Integer height) {
            this.mHeight = height;
        }

        private Integer getHeight() {
            return mHeight;
        }

        /**
         * Returns true if we are inserting into the last position
         *
         * @return true if we are inserting into the last position
         */
        public boolean isLastPosition() {
            return mInsertPos == mNumPositions - 1;
        }
    }
}
