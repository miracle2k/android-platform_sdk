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
import static com.android.ide.common.layout.LayoutConstants.ATTR_GRAVITY;
import static com.android.ide.common.layout.LayoutConstants.ATTR_ID;
import static com.android.ide.common.layout.LayoutConstants.ATTR_LAYOUT_PREFIX;
import static com.android.ide.common.layout.LayoutConstants.VALUE_ABOVE;
import static com.android.ide.common.layout.LayoutConstants.VALUE_ALIGN_BASELINE;
import static com.android.ide.common.layout.LayoutConstants.VALUE_ALIGN_BOTTOM;
import static com.android.ide.common.layout.LayoutConstants.VALUE_ALIGN_LEFT;
import static com.android.ide.common.layout.LayoutConstants.VALUE_ALIGN_PARENT_BOTTOM;
import static com.android.ide.common.layout.LayoutConstants.VALUE_ALIGN_PARENT_LEFT;
import static com.android.ide.common.layout.LayoutConstants.VALUE_ALIGN_PARENT_RIGHT;
import static com.android.ide.common.layout.LayoutConstants.VALUE_ALIGN_PARENT_TOP;
import static com.android.ide.common.layout.LayoutConstants.VALUE_ALIGN_RIGHT;
import static com.android.ide.common.layout.LayoutConstants.VALUE_ALIGN_TOP;
import static com.android.ide.common.layout.LayoutConstants.VALUE_ALIGN_WITH_PARENT_MISSING;
import static com.android.ide.common.layout.LayoutConstants.VALUE_BELOW;
import static com.android.ide.common.layout.LayoutConstants.VALUE_CENTER_HORIZONTAL;
import static com.android.ide.common.layout.LayoutConstants.VALUE_CENTER_IN_PARENT;
import static com.android.ide.common.layout.LayoutConstants.VALUE_CENTER_VERTICAL;
import static com.android.ide.common.layout.LayoutConstants.VALUE_TO_LEFT_OF;
import static com.android.ide.common.layout.LayoutConstants.VAUE_TO_RIGHT_OF;

import com.android.ide.common.api.DrawingStyle;
import com.android.ide.common.api.DropFeedback;
import com.android.ide.common.api.IAttributeInfo;
import com.android.ide.common.api.IDragElement;
import com.android.ide.common.api.IFeedbackPainter;
import com.android.ide.common.api.IGraphics;
import com.android.ide.common.api.INode;
import com.android.ide.common.api.INodeHandler;
import com.android.ide.common.api.IViewRule;
import com.android.ide.common.api.MenuAction;
import com.android.ide.common.api.Point;
import com.android.ide.common.api.Rect;
import com.android.ide.common.api.IAttributeInfo.Format;
import com.android.ide.common.api.INode.IAttribute;
import com.android.util.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An {@link IViewRule} for android.widget.RelativeLayout and all its derived
 * classes.
 */
public class RelativeLayoutRule extends BaseLayoutRule {

    // ==== Selection ====

    @Override
    public List<String> getSelectionHint(INode parentNode, INode childNode) {
        List<String> infos = new ArrayList<String>(18);
        addAttr(VALUE_ABOVE, childNode, infos);
        addAttr(VALUE_BELOW, childNode, infos);
        addAttr(VALUE_TO_LEFT_OF, childNode, infos);
        addAttr(VAUE_TO_RIGHT_OF, childNode, infos);
        addAttr(VALUE_ALIGN_BASELINE, childNode, infos);
        addAttr(VALUE_ALIGN_TOP, childNode, infos);
        addAttr(VALUE_ALIGN_BOTTOM, childNode, infos);
        addAttr(VALUE_ALIGN_LEFT, childNode, infos);
        addAttr(VALUE_ALIGN_RIGHT, childNode, infos);
        addAttr(VALUE_ALIGN_PARENT_TOP, childNode, infos);
        addAttr(VALUE_ALIGN_PARENT_BOTTOM, childNode, infos);
        addAttr(VALUE_ALIGN_PARENT_LEFT, childNode, infos);
        addAttr(VALUE_ALIGN_PARENT_RIGHT, childNode, infos);
        addAttr(VALUE_ALIGN_WITH_PARENT_MISSING, childNode, infos);
        addAttr(VALUE_CENTER_HORIZONTAL, childNode, infos);
        addAttr(VALUE_CENTER_IN_PARENT, childNode, infos);
        addAttr(VALUE_CENTER_VERTICAL, childNode, infos);

        return infos;
    }

    private void addAttr(String propertyName, INode childNode, List<String> infos) {
        String a = childNode.getStringAttr(ANDROID_URI, ATTR_LAYOUT_PREFIX + propertyName);
        if (a != null && a.length() > 0) {
            String s = propertyName + ": " + a;
            infos.add(s);
        }
    }

    // ==== Drag'n'drop support ====

    @Override
    public DropFeedback onDropEnter(INode targetNode, final IDragElement[] elements) {

        if (elements.length == 0) {
            return null;
        }

        Rect bn = targetNode.getBounds();
        if (!bn.isValid()) {
            return null;
        }

        // Collect the ids of the elements being dragged
        List<String> movedIds = new ArrayList<String>(collectIds(
                new HashMap<String, Pair<String, String>>(), elements).keySet());

        // Prepare the drop feedback
        return new DropFeedback(new RelativeDropData(movedIds), new IFeedbackPainter() {
            public void paint(IGraphics gc, INode node, DropFeedback feedback) {
                drawRelativeDropFeedback(gc, node, elements, feedback);
            }
        });
    }

    @Override
    public DropFeedback onDropMove(INode targetNode, IDragElement[] elements,
            DropFeedback feedback, Point p) {

        RelativeDropData data = (RelativeDropData) feedback.userData;
        Rect area = feedback.captureArea;

        // Only look for a new child if cursor is no longer under the current
        // rect
        if (area == null || !area.contains(p)) {

            // We're not capturing anymore since we got outside of the capture
            // bounds
            feedback.captureArea = null;
            feedback.requestPaint = false;
            data.setRejected(null);

            // Find the current direct children under the cursor
            INode childNode = null;
            int childIndex = -1;
            nextChild: for (INode child : targetNode.getChildren()) {
                childIndex++;
                Rect bc = child.getBounds();
                if (bc.contains(p)) {

                    // If we're doing a move operation within the same canvas,
                    // we can't attach the moved object to one belonging to the
                    // selection since it will disappear after the move.
                    if (feedback.sameCanvas && !feedback.isCopy) {
                        for (IDragElement element : elements) {
                            if (bc.equals(element.getBounds())) {
                                data.setRejected(bc);
                                feedback.requestPaint = true;
                                continue nextChild;
                            }
                        }
                    }

                    // One more limitation: if we're moving one or more
                    // elements, we can't drop them on a child which relative
                    // position is expressed directly or indirectly based on the
                    // element being moved.
                    if (!feedback.isCopy) {
                        if (searchRelativeIds(child, data.getMovedIds(),
                                data.getCachedLinkIds())) {
                            data.setRejected(bc);
                            feedback.requestPaint = true;
                            continue nextChild;
                        }
                    }

                    childNode = child;
                    break;
                }
            }

            // If there is a selected child and it changed, recompute child drop
            // zones
            if (childNode != null && childNode != data.getChild()) {
                data.setChild(childNode);
                data.setIndex(childIndex);
                data.setCurr(null);
                data.setZones(null);

                Pair<Rect, List<DropZone>> result = computeChildDropZones(childNode);
                data.setZones(result.getSecond());

                // Capture this rect, to prevent the engine from switching the
                // layout node.
                feedback.captureArea = result.getFirst();
                feedback.requestPaint = true;

            } else if (childNode == null) {
                // If there is no selected child, compute the border drop zone
                data.setChild(null);
                data.setIndex(-1);
                data.setCurr(null);

                DropZone zone = computeBorderDropZone(targetNode, p, feedback);
                if (zone == null) {
                    data.setZones(null);
                } else {
                    data.setZones(Collections.singletonList(zone));
                    feedback.captureArea = zone.getRect();
                }

                feedback.requestPaint |= (area == null || !area.equals(feedback.captureArea));
            }
        }

        // Find the current zone
        DropZone currZone = null;
        if (data.getZones() != null) {
            for (DropZone zone : data.getZones()) {
                if (zone.getRect().contains(p)) {
                    currZone = zone;
                    break;
                }
            }

            // Look to see if there's a border match if we didn't find anything better;
            // a border match isn't required to have the mouse cursor within it since we
            // do edge matching in the code which -adds- the border zones.
            if (currZone == null && feedback.dragBounds != null) {
                for (DropZone zone : data.getZones()) {
                    if (zone.isBorderZone()) {
                        currZone = zone;
                        break;
                    }
                }
            }
        }

        // Look for border match when there are no children: always offer one in this case
        if (currZone == null && targetNode.getChildren().length == 0 && data.getZones() != null
                && data.getZones().size() > 0) {
            currZone = data.getZones().get(0);
        }

        if (currZone != data.getCurr()) {
            data.setCurr(currZone);
            feedback.requestPaint = true;
        }

        feedback.invalidTarget = (currZone == null);

        return feedback;
    }

    /**
     * Returns true if the child has any attribute of Format.REFERENCE which
     * value matches one of the ids in movedIds.
     */
    private boolean searchRelativeIds(INode node, List<String> movedIds,
            Map<INode, Set<String>> cachedLinkIds) {
        Set<String> ids = getLinkedIds(node, cachedLinkIds);

        for (String id : ids) {
            if (movedIds.contains(id)) {
                return true;
            }
        }

        return false;
    }

    private Set<String> getLinkedIds(INode node, Map<INode, Set<String>> cachedLinkIds) {
        Set<String> ids = cachedLinkIds.get(node);

        if (ids != null) {
            return ids;
        }

        // We don't have cached data on this child, so create a list of
        // all the linked id it is referencing.
        ids = new HashSet<String>();
        cachedLinkIds.put(node, ids);
        for (IAttribute attr : node.getLiveAttributes()) {
            IAttributeInfo attrInfo = node.getAttributeInfo(attr.getUri(), attr.getName());
            if (attrInfo == null) {
                continue;
            }
            Format[] formats = attrInfo.getFormats();
            if (!IAttributeInfo.Format.REFERENCE.in(formats)) {
                continue;
            }

            String id = attr.getValue();
            id = normalizeId(id);
            if (ids.contains(id)) {
                continue;
            }
            ids.add(id);

            // Find the sibling with that id
            INode p = node.getParent();
            if (p == null) {
                continue;
            }
            for (INode child : p.getChildren()) {
                if (child == node) {
                    continue;
                }
                String childId = child.getStringAttr(ANDROID_URI, ATTR_ID);
                if (childId == null) {
                    continue;
                }
                childId = normalizeId(childId);
                if (id.equals(childId)) {
                    Set<String> linkedIds = getLinkedIds(child, cachedLinkIds);
                    ids.addAll(linkedIds);
                    break;
                }
            }
        }

        return ids;
    }

    private DropZone computeBorderDropZone(INode targetNode, Point p, DropFeedback feedback) {
        Rect bounds = targetNode.getBounds();
        int x = p.x;
        int y = p.y;

        int x1 = bounds.x;
        int y1 = bounds.y;
        int w = bounds.w;
        int h = bounds.h;
        int x2 = x1 + w;
        int y2 = y1 + h;

        // Default border zone size
        int n = 10;
        int n2 = 2*n;

        // Size of -matched- border zone (not painted, but we detect edge overlaps here)
        int hn = 0;
        int vn = 0;
        if (feedback.dragBounds != null) {
            hn = feedback.dragBounds.w / 2;
            vn = feedback.dragBounds.h / 2;
        }
        boolean vertical = false;

        Rect r = null;
        String attr = null;

        if (x <= x1 + n + hn && y >= y1 && y <= y2) {
            r = new Rect(x1 - n, y1, n2, h);
            attr = VALUE_ALIGN_PARENT_LEFT;
            vertical = true;

        } else if (x >= x2 - hn - n && y >= y1 && y <= y2) {
            r = new Rect(x2 - n, y1, n2, h);
            attr = VALUE_ALIGN_PARENT_RIGHT;
            vertical = true;

        } else if (y <= y1 + n + vn && x >= x1 && x <= x2) {
            r = new Rect(x1, y1 - n, w, n2);
            attr = VALUE_ALIGN_PARENT_TOP;

        } else if (y >= y2 - vn - n && x >= x1 && x <= x2) {
            r = new Rect(x1, y2 - n, w, n2);
            attr = VALUE_ALIGN_PARENT_BOTTOM;

        } else {
            // We're nowhere near a border.
            // If there are no children, we will offer one anyway:
            if (targetNode.getChildren().length == 0) {
                r = new Rect(x1 - n, y1, n2, h);
                attr = VALUE_ALIGN_PARENT_LEFT;
                vertical = true;
            } else {
                return null;
            }
        }

        return new DropZone(r, Collections.singletonList(attr), r.getCenter(), vertical);
    }

    private Pair<Rect, List<DropZone>> computeChildDropZones(INode childNode) {

        Rect b = childNode.getBounds();

        // Compute drop zone borders as follow:
        //
        // +---+-----+-----+-----+---+
        // | 1 \ 2 \ 3 / 4 / 5 |
        // +----+-----+---+-----+----+
        //
        // For the top and bottom borders, zones 1 and 5 have the same width,
        // which is
        // size1 = min(10, w/5)
        // and zones 2, 3 and 4 have a width of
        // size2 = (w - 2*size) / 3
        //
        // Same works for left and right borders vertically.
        //
        // Attributes generated:
        // Horizontally:
        // 1- toLeftOf / 2- alignLeft / 3- 2+4 / 4- alignRight / 5- toRightOf
        // Vertically:
        // 1- above / 2-alignTop / 3- 2+4 / 4- alignBottom / 5- below

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

        Rect bounds = new Rect(x1, y1, wt, ht);

        List<DropZone> zones = new ArrayList<DropZone>(16);
        String a = VALUE_ABOVE;
        int x = x1;
        int y = y1;

        x = addx(w1, a, x, y, h1, zones, VALUE_TO_LEFT_OF);
        x = addx(w2, a, x, y, h1, zones, VALUE_ALIGN_LEFT);
        x = addx(w2, a, x, y, h1, zones, VALUE_ALIGN_LEFT, VALUE_ALIGN_RIGHT);
        x = addx(w2, a, x, y, h1, zones, VALUE_ALIGN_RIGHT);
        x = addx(w1, a, x, y, h1, zones, VAUE_TO_RIGHT_OF);

        a = VALUE_BELOW;
        x = x1;
        y = y1 + ht - h1;

        x = addx(w1, a, x, y, h1, zones, VALUE_TO_LEFT_OF);
        x = addx(w2, a, x, y, h1, zones, VALUE_ALIGN_LEFT);
        x = addx(w2, a, x, y, h1, zones, VALUE_ALIGN_LEFT, VALUE_ALIGN_RIGHT);
        x = addx(w2, a, x, y, h1, zones, VALUE_ALIGN_RIGHT);
        x = addx(w1, a, x, y, h1, zones, VAUE_TO_RIGHT_OF);

        a = VALUE_TO_LEFT_OF;
        x = x1;
        y = y1 + h1;

        y = addy(h2, a, x, y, w1, zones, VALUE_ALIGN_TOP);
        y = addy(h2, a, x, y, w1, zones, VALUE_ALIGN_TOP, VALUE_ALIGN_BOTTOM);
        y = addy(h2, a, x, y, w1, zones, VALUE_ALIGN_BOTTOM);

        a = VAUE_TO_RIGHT_OF;
        x = x1 + wt - w1;
        y = y1 + h1;

        y = addy(h2, a, x, y, w1, zones, VALUE_ALIGN_TOP);
        y = addy(h2, a, x, y, w1, zones, VALUE_ALIGN_TOP, VALUE_ALIGN_BOTTOM);
        y = addy(h2, a, x, y, w1, zones, VALUE_ALIGN_BOTTOM);

        return Pair.of(bounds, zones);
    }

    private int addx(int wn, String a, int x, int y, int h1, List<DropZone> zones, String... a2) {
        Rect rect = new Rect(x, y, wn, h1);
        List<String> attrs = new ArrayList<String>(a2.length + 1);
        attrs.add(a);
        for (String attribute : a2) {
            attrs.add(attribute);
        }
        zones.add(new DropZone(rect, attrs));
        return x + wn;
    }

    private int addy(int hn, String a, int x, int y, int w1, List<DropZone> zones, String... a2) {
        Rect rect = new Rect(x, y, w1, hn);
        List<String> attrs = new ArrayList<String>(a2.length + 1);
        attrs.add(a);
        for (String attribute : a2) {
            attrs.add(attribute);
        }

        zones.add(new DropZone(rect, attrs));
        return y + hn;
    }

    private void drawRelativeDropFeedback(IGraphics gc, INode targetNode, IDragElement[] elements,
            DropFeedback feedback) {
        Rect b = targetNode.getBounds();
        if (!b.isValid()) {
            return;
        }

        gc.useStyle(DrawingStyle.DROP_RECIPIENT);
        gc.drawRect(b);

        gc.useStyle(DrawingStyle.DROP_ZONE);

        RelativeDropData data = (RelativeDropData) feedback.userData;

        if (data.getZones() != null) {
            for (DropZone it : data.getZones()) {
                gc.drawRect(it.getRect());
            }
        }

        if (data.getCurr() != null) {
            gc.useStyle(DrawingStyle.DROP_ZONE_ACTIVE);
            gc.fillRect(data.getCurr().getRect());

            Rect r = feedback.captureArea;
            int x = r.x + 5;
            int y = r.y + r.h + 5;

            String id = null;
            if (data.getChild() != null) {
                id = data.getChild().getStringAttr(ANDROID_URI, ATTR_ID);
            }

            // Print constraints (with id appended if applicable)
            gc.useStyle(DrawingStyle.HELP);
            List<String> strings = new ArrayList<String>();
            for (String it : data.getCurr().getAttr()) {
                strings.add(id != null && id.length() > 0 ? it + "=" + id : it);
            }
            gc.drawBoxedStrings(x, y, strings);

            Point mark = data.getCurr().getMark();
            if (mark != null) {
                gc.useStyle(DrawingStyle.DROP_PREVIEW);
                Rect nr = data.getCurr().getRect();
                int nx = nr.x + nr.w / 2;
                int ny = nr.y + nr.h / 2;
                boolean vertical = data.getCurr().isVertical();
                if (vertical) {
                    gc.drawLine(nx, nr.y, nx, nr.y + nr.h);
                    x = nx;
                    y = b.y;
                } else {
                    gc.drawLine(nr.x, ny, nr.x + nr.w, ny);
                    x = b.x;
                    y = ny;
                }
            } else {
                r = data.getCurr().getRect();
                x = r.x + r.w / 2;
                y = r.y + r.h / 2;
            }

            Rect be = elements[0].getBounds();

            // Draw bound rectangles for all selected items
            gc.useStyle(DrawingStyle.DROP_PREVIEW);
            for (IDragElement element : elements) {
                be = element.getBounds();
                if (!be.isValid()) {
                    // We don't always have bounds - for example when dragging
                    // from the palette.
                    continue;
                }

                int offsetX = x - be.x;
                int offsetY = y - be.y;

                if (data.getCurr().getAttr().contains(VALUE_ALIGN_TOP)
                        && data.getCurr().getAttr().contains(VALUE_ALIGN_BOTTOM)) {
                    offsetY -= be.h / 2;
                } else if (data.getCurr().getAttr().contains(VALUE_ABOVE)
                        || data.getCurr().getAttr().contains(VALUE_ALIGN_TOP)
                        || data.getCurr().getAttr().contains(VALUE_ALIGN_PARENT_BOTTOM)) {
                    offsetY -= be.h;
                }
                if (data.getCurr().getAttr().contains(VALUE_ALIGN_RIGHT)
                        && data.getCurr().getAttr().contains(VALUE_ALIGN_LEFT)) {
                    offsetX -= be.w / 2;
                } else if (data.getCurr().getAttr().contains(VALUE_TO_LEFT_OF)
                        || data.getCurr().getAttr().contains(VALUE_ALIGN_LEFT)
                        || data.getCurr().getAttr().contains(VALUE_ALIGN_PARENT_RIGHT)) {
                    offsetX -= be.w;
                }

                drawElement(gc, element, offsetX, offsetY);
            }
        }

        if (data.getRejected() != null) {
            Rect br = data.getRejected();
            gc.useStyle(DrawingStyle.INVALID);
            gc.fillRect(br);
            gc.drawLine(br.x, br.y, br.x + br.w, br.y + br.h);
            gc.drawLine(br.x, br.y + br.h, br.x + br.w, br.y);
        }
    }

    @Override
    public void onDropLeave(INode targetNode, IDragElement[] elements, DropFeedback feedback) {
        // Free the last captured rect, if any
        feedback.captureArea = null;
    }

    @Override
    public void onDropped(final INode targetNode, final IDragElement[] elements,
            final DropFeedback feedback, final Point p) {
        final RelativeDropData data = (RelativeDropData) feedback.userData;
        if (data.getCurr() == null) {
            return;
        }

        // Collect IDs from dropped elements and remap them to new IDs
        // if this is a copy or from a different canvas.
        final Map<String, Pair<String, String>> idMap = getDropIdMap(targetNode, elements,
                feedback.isCopy || !feedback.sameCanvas);

        targetNode.editXml("Add elements to RelativeLayout", new INodeHandler() {

            public void handle(INode node) {
                int index = data.getIndex();

                // Now write the new elements.
                for (IDragElement element : elements) {
                    String fqcn = element.getFqcn();

                    // index==-1 means to insert at the end.
                    // Otherwise increment the insertion position.
                    if (index >= 0) {
                        index++;
                    }

                    INode newChild = targetNode.insertChildAt(fqcn, index);

                    // Copy all the attributes, modifying them as needed.
                    addAttributes(newChild, element, idMap, DEFAULT_ATTR_FILTER);

                    // TODO... seems totally wrong. REVISIT or EXPLAIN
                    String id = null;
                    if (data.getChild() != null) {
                        id = data.getChild().getStringAttr(ANDROID_URI, ATTR_ID);
                    }

                    for (String it : data.getCurr().getAttr()) {
                        newChild.setAttribute(ANDROID_URI,
                             ATTR_LAYOUT_PREFIX + it, id != null ? id : "true"); //$NON-NLS-1$
                    }

                    addInnerElements(newChild, element, idMap);
                }
            }
        });
    }

    @Override
    public void addLayoutActions(List<MenuAction> actions, final INode parentNode,
            final List<? extends INode> children) {
        super.addLayoutActions(actions, parentNode, children);

        actions.add(createGravityAction(Collections.<INode>singletonList(parentNode),
                ATTR_GRAVITY));
        actions.add(MenuAction.createSeparator(25));
        actions.add(createMarginAction(parentNode, children));
    }

    /**
     * Internal state used by the RelativeLayoutRule, stored as userData in the
     * {@link DropFeedback}.
     */
    private static class RelativeDropData {
        /** Current child under cursor */
        private INode mChild;

        /** Index of child in the parent children list */
        private int mIndex;

        /**
         * Valid "anchor" zones for the current child of type
         */
        private List<DropZone> mZones;

        /** Current zone */
        private DropZone mCurr;

        /** rejected target (Rect bounds) */
        private Rect mRejected;

        private List<String> mMovedIds;

        private Map<INode, Set<String>> mCachedLinkIds = new HashMap<INode, Set<String>>();

        public RelativeDropData(List<String> movedIds) {
            this.mMovedIds = movedIds;
        }

        private void setChild(INode child) {
            this.mChild = child;
        }

        private INode getChild() {
            return mChild;
        }

        private void setIndex(int index) {
            this.mIndex = index;
        }

        private int getIndex() {
            return mIndex;
        }

        private void setZones(List<DropZone> zones) {
            this.mZones = zones;
        }

        private List<DropZone> getZones() {
            return mZones;
        }

        private void setCurr(DropZone curr) {
            this.mCurr = curr;
        }

        private DropZone getCurr() {
            return mCurr;
        }

        private void setRejected(Rect rejected) {
            this.mRejected = rejected;
        }

        private Rect getRejected() {
            return mRejected;
        }

        private List<String> getMovedIds() {
            return mMovedIds;
        }

        private Map<INode, Set<String>> getCachedLinkIds() {
            return mCachedLinkIds;
        }
    }

    private static class DropZone {
        /** The rectangular bounds of the drop zone */
        private final Rect mRect;

        /**
         * Attributes that correspond to this drop zone, e.g. ["alignLeft",
         * "alignBottom"]
         */
        private final List<String> mAttr;

        /** Non-null iff this is a border */
        private final Point mMark;

        /** Defined iff this is a border match */
        private final boolean mVertical;

        public DropZone(Rect rect, List<String> attr, Point mark, boolean vertical) {
            super();
            this.mRect = rect;
            this.mAttr = attr;
            this.mMark = mark;
            this.mVertical = vertical;
        }

        public DropZone(Rect rect, List<String> attr) {
            this(rect, attr, null, false);
        }

        private Rect getRect() {
            return mRect;
        }

        private List<String> getAttr() {
            return mAttr;
        }

        private Point getMark() {
            return mMark;
        }

        private boolean isVertical() {
            return mVertical;
        }

        private boolean isBorderZone() {
            return mMark != null;
        }
    }
}
