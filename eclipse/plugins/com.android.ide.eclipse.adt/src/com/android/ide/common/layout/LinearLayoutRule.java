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

import static com.android.ide.common.layout.LayoutConstants.ANDROID_URI;
import static com.android.ide.common.layout.LayoutConstants.ATTR_BASELINE_ALIGNED;
import static com.android.ide.common.layout.LayoutConstants.ATTR_LAYOUT_GRAVITY;
import static com.android.ide.common.layout.LayoutConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.ide.common.layout.LayoutConstants.ATTR_LAYOUT_WEIGHT;
import static com.android.ide.common.layout.LayoutConstants.ATTR_LAYOUT_WIDTH;
import static com.android.ide.common.layout.LayoutConstants.ATTR_ORIENTATION;
import static com.android.ide.common.layout.LayoutConstants.ATTR_WEIGHT_SUM;
import static com.android.ide.common.layout.LayoutConstants.VALUE_HORIZONTAL;
import static com.android.ide.common.layout.LayoutConstants.VALUE_VERTICAL;

import com.android.ide.common.api.DrawingStyle;
import com.android.ide.common.api.DropFeedback;
import com.android.ide.common.api.IDragElement;
import com.android.ide.common.api.IFeedbackPainter;
import com.android.ide.common.api.IGraphics;
import com.android.ide.common.api.IMenuCallback;
import com.android.ide.common.api.INode;
import com.android.ide.common.api.INodeHandler;
import com.android.ide.common.api.IViewMetadata;
import com.android.ide.common.api.IViewRule;
import com.android.ide.common.api.InsertType;
import com.android.ide.common.api.MenuAction;
import com.android.ide.common.api.Point;
import com.android.ide.common.api.Rect;
import com.android.ide.common.api.IViewMetadata.FillPreference;
import com.android.ide.common.api.MenuAction.OrderedChoices;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * An {@link IViewRule} for android.widget.LinearLayout and all its derived
 * classes.
 */
public class LinearLayoutRule extends BaseLayoutRule {
    private static final String ACTION_ORIENTATION = "_orientation"; //$NON-NLS-1$
    private static final String ACTION_WEIGHT = "_weight"; //$NON-NLS-1$
    private static final String ACTION_DISTRIBUTE = "_distribute"; //$NON-NLS-1$
    private static final String ACTION_BASELINE = "_baseline"; //$NON-NLS-1$

    private static final URL ICON_HORIZONTAL =
        LinearLayoutRule.class.getResource("hlinear.png"); //$NON-NLS-1$
    private static final URL ICON_VERTICAL =
        LinearLayoutRule.class.getResource("vlinear.png"); //$NON-NLS-1$
    private static final URL ICON_WEIGHTS =
        LinearLayoutRule.class.getResource("weights.png"); //$NON-NLS-1$
    private static final URL ICON_DISTRIBUTE =
        LinearLayoutRule.class.getResource("distribute.png"); //$NON-NLS-1$
    private static final URL ICON_BASELINE =
        LinearLayoutRule.class.getResource("baseline.png"); //$NON-NLS-1$

    /**
     * Add an explicit Orientation toggle to the context menu.
     */
    @Override
    public List<MenuAction> getContextMenu(final INode selectedNode) {
        if (supportsOrientation()) {
            String current = getCurrentOrientation(selectedNode);
            IMenuCallback onChange = new PropertyCallback(Collections.singletonList(selectedNode),
                    "Change LinearLayout Orientation",
                    ANDROID_URI, ATTR_ORIENTATION);
            return concatenate(super.getContextMenu(selectedNode),
                new MenuAction.Choices(ACTION_ORIENTATION, "Orientation",  //$NON-NLS-1$
                    mapify(
                        "horizontal", "Horizontal",                    //$NON-NLS-1$
                        "vertical", "Vertical"                         //$NON-NLS-1$
                    ),
                    current, onChange));
        } else {
            return super.getContextMenu(selectedNode);
        }
    }

    /**
     * Returns the current orientation, regardless of whether it has been defined in XML
     *
     * @param node The LinearLayout to look up the orientation for
     * @return "horizontal" or "vertical" depending on the current orientation of the
     *         linear layout
     */
    private String getCurrentOrientation(final INode node) {
        String orientation = node.getStringAttr(ANDROID_URI, ATTR_ORIENTATION);
        if (orientation == null || orientation.length() == 0) {
            orientation = VALUE_HORIZONTAL;
        }
        return orientation;
    }

    /**
     * Returns true if the given node represents a vertical linear layout.
     * @param node the node to check layout orientation for
     * @return true if the layout is in vertical mode, otherwise false
     */
    protected boolean isVertical(INode node) {
        // Horizontal is the default, so if no value is specified it is horizontal.
        return VALUE_VERTICAL.equals(node.getStringAttr(ANDROID_URI,
                ATTR_ORIENTATION));
    }

    /**
     * Returns true if this LinearLayout supports switching orientation.
     *
     * @return true if this layout supports orientations
     */
    protected boolean supportsOrientation() {
        return true;
    }

    @Override
    public void addLayoutActions(List<MenuAction> actions, final INode parentNode,
            final List<? extends INode> children) {
        super.addLayoutActions(actions, parentNode, children);
        if (supportsOrientation()) {
            OrderedChoices action = MenuAction.createChoices(
                    ACTION_ORIENTATION, "Orientation",  //$NON-NLS-1$
                    null,
                    new PropertyCallback(Collections.singletonList(parentNode),
                            "Change LinearLayout Orientation",
                            ANDROID_URI, ATTR_ORIENTATION),
                    Arrays.<String>asList("Set Horizontal Orientation", "Set Vertical Orientation"),
                    Arrays.<URL>asList(ICON_HORIZONTAL, ICON_VERTICAL),
                    Arrays.<String>asList("horizontal", "vertical"),
                    getCurrentOrientation(parentNode),
                    null /* icon */,
                    -10
            );
            action.setRadio(true);
            actions.add(action);
        }
        if (!isVertical(parentNode)) {
            String current = parentNode.getStringAttr(ANDROID_URI, ATTR_BASELINE_ALIGNED);
            boolean isAligned =  current == null || Boolean.valueOf(current);
            actions.add(MenuAction.createToggle(null, "Toggle Baseline Alignment",
                    isAligned,
                    new PropertyCallback(Collections.singletonList(parentNode),
                            "Change Baseline Alignment",
                            ANDROID_URI, ATTR_BASELINE_ALIGNED), // TODO: Also set index?
                    ICON_BASELINE, 38));
        }

        // Gravity
        if (children != null && children.size() > 0) {
            actions.add(MenuAction.createSeparator(35));

            // Margins
            actions.add(createMarginAction(parentNode, children));

            // Gravity
            actions.add(createGravityAction(children, ATTR_LAYOUT_GRAVITY));

            // Weights
            IMenuCallback actionCallback = new IMenuCallback() {
                public void action(final MenuAction action, final String valueId,
                        final Boolean newValue) {
                    parentNode.editXml("Change Weight", new INodeHandler() {
                        public void handle(INode n) {
                            if (action.getId().equals(ACTION_WEIGHT)) {
                                String weight =
                                    children.get(0).getStringAttr(ANDROID_URI, ATTR_LAYOUT_WEIGHT);
                                if (weight == null || weight.length() == 0) {
                                    weight = "0.0"; //$NON-NLS-1$
                                }
                                weight = mRulesEngine.displayInput("Enter Weight Value:", weight,
                                        null);
                                if (weight != null) {
                                    for (INode child : children) {
                                        child.setAttribute(ANDROID_URI,
                                                ATTR_LAYOUT_WEIGHT, weight);
                                    }
                                }
                            } else if (action.getId().equals(ACTION_DISTRIBUTE)) {
                                // Any XML to get weight sum?
                                String weightSum = parentNode.getStringAttr(ANDROID_URI,
                                        ATTR_WEIGHT_SUM);
                                double sum = -1.0;
                                if (weightSum != null) {
                                    // Distribute
                                    try {
                                        sum = Double.parseDouble(weightSum);
                                    } catch (NumberFormatException nfe) {
                                        // Just keep using the default
                                    }
                                }
                                INode[] targets = parentNode.getChildren();
                                int numTargets = targets.length;
                                double share;
                                if (sum <= 0.0) {
                                    // The sum will be computed from the children, so just
                                    // use arbitrary amount
                                    share = 1.0;
                                } else {
                                    share = sum / numTargets;
                                }
                                String value;
                                if (share != (int) share) {
                                    value = String.format("%.2f", (float) share); //$NON-NLS-1$
                                } else {
                                    value = Integer.toString((int) share);
                                }
                                for (INode target : targets) {
                                    target.setAttribute(ANDROID_URI, ATTR_LAYOUT_WEIGHT, value);
                                }
                            } else {
                                assert action.getId().equals(ACTION_BASELINE);
                            }
                        }
                    });
                }
            };
            actions.add(MenuAction.createSeparator(50));
            actions.add(MenuAction.createAction(ACTION_DISTRIBUTE, "Distribute Weights Evenly",
                    null, actionCallback, ICON_DISTRIBUTE, 60));
            actions.add(MenuAction.createAction(ACTION_WEIGHT, "Change Layout Weight", null,
                    actionCallback, ICON_WEIGHTS, 70));
        }
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

        boolean isVertical = isVertical(targetNode);

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
                    if (bounds.isValid() && (bounds.w > b.w || bounds.h > b.h) &&
                            node.getChildren().length == 0) {
                        // The bounds of the child does not fully fit inside the target.
                        // Limit the bounds to the layout bounds (but only when there
                        // are no children, since otherwise positioning around the existing
                        // children gets difficult)
                        final int px, py, pw, ph;
                        if (bounds.w > b.w) {
                            px = b.x;
                            pw = b.w;
                        } else {
                            px = bounds.x + offsetX;
                            pw = bounds.w;
                        }
                        if (bounds.h > b.h) {
                            py = b.y;
                            ph = b.h;
                        } else {
                            py = bounds.y + offsetY;
                            ph = bounds.h;
                        }
                        Rect within = new Rect(px, py, pw, ph);
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
        insertAt(targetNode, elements, feedback.isCopy || !feedback.sameCanvas, initialInsertPos);
    }

    @Override
    public void onChildInserted(INode node, INode parent, InsertType insertType) {
        // Attempt to set fill-properties on newly added views such that for example,
        // in a vertical layout, a text field defaults to filling horizontally, but not
        // vertically.
        String fqcn = node.getFqcn();
        IViewMetadata metadata = mRulesEngine.getMetadata(fqcn);
        if (metadata != null) {
            boolean vertical = isVertical(parent);
            FillPreference fill = metadata.getFillPreference();
            String fillParent = getFillParentValueName();
            if (fill.fillHorizontally(vertical)) {
                node.setAttribute(ANDROID_URI, ATTR_LAYOUT_WIDTH, fillParent);
            }
            if (fill.fillVertically(vertical)) {
                node.setAttribute(ANDROID_URI, ATTR_LAYOUT_HEIGHT, fillParent);
            }
        }
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
