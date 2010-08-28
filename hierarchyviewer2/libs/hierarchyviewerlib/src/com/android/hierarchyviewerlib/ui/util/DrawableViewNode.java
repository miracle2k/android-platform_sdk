/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.hierarchyviewerlib.ui.util;

import com.android.hierarchyviewerlib.device.ViewNode;

import java.util.ArrayList;

public class DrawableViewNode {
    public ViewNode viewNode;

    public final ArrayList<DrawableViewNode> children = new ArrayList<DrawableViewNode>();

    public final static int NODE_HEIGHT = 100;

    public final static int NODE_WIDTH = 180;

    public final static int CONTENT_LEFT_RIGHT_PADDING = 9;

    public final static int CONTENT_TOP_BOTTOM_PADDING = 8;

    public final static int CONTENT_INTER_PADDING = 3;

    public final static int INDEX_PADDING = 7;

    public final static int LEAF_NODE_SPACING = 9;

    public final static int NON_LEAF_NODE_SPACING = 15;

    public final static int PARENT_CHILD_SPACING = 50;

    public final static int PADDING = 30;

    public int treeHeight;

    public int treeWidth;

    public boolean leaf;

    public DrawableViewNode parent;

    public int left;

    public double top;

    public int topSpacing;

    public int bottomSpacing;

    public boolean treeDrawn;

    public static class Rectangle {
        public double x, y, width, height;

        public Rectangle() {

        }

        public Rectangle(Rectangle other) {
            this.x = other.x;
            this.y = other.y;
            this.width = other.width;
            this.height = other.height;
        }

        public Rectangle(double x, double y, double width, double height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        @Override
        public String toString() {
            return "{" + x + ", " + y + ", " + width + ", " + height + "}"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
        }

    }

    public static class Point {
        public double x, y;

        public Point() {
        }

        public Point(double x, double y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public String toString() {
            return "(" + x + ", " + y + ")"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        }
    }

    public Rectangle bounds = new Rectangle();

    public DrawableViewNode(ViewNode viewNode) {
        this.viewNode = viewNode;
        treeDrawn = !viewNode.willNotDraw;
        if (viewNode.children.size() == 0) {
            treeHeight = NODE_HEIGHT;
            treeWidth = NODE_WIDTH;
            leaf = true;
        } else {
            leaf = false;
            int N = viewNode.children.size();
            treeHeight = 0;
            treeWidth = 0;
            for (int i = 0; i < N; i++) {
                DrawableViewNode child = new DrawableViewNode(viewNode.children.get(i));
                children.add(child);
                child.parent = this;
                treeHeight += child.treeHeight;
                treeWidth = Math.max(treeWidth, child.treeWidth);
                if (i != 0) {
                    DrawableViewNode prevChild = children.get(i - 1);
                    if (prevChild.leaf && child.leaf) {
                        treeHeight += LEAF_NODE_SPACING;
                        prevChild.bottomSpacing = LEAF_NODE_SPACING;
                        child.topSpacing = LEAF_NODE_SPACING;
                    } else {
                        treeHeight += NON_LEAF_NODE_SPACING;
                        prevChild.bottomSpacing = NON_LEAF_NODE_SPACING;
                        child.topSpacing = NON_LEAF_NODE_SPACING;
                    }
                }
                treeDrawn |= child.treeDrawn;
            }
            treeWidth += NODE_WIDTH + PARENT_CHILD_SPACING;
        }
    }

    public void setLeft() {
        if (parent == null) {
            left = PADDING;
            bounds.x = 0;
            bounds.width = treeWidth + 2 * PADDING;
        } else {
            left = parent.left + NODE_WIDTH + PARENT_CHILD_SPACING;
        }
        int N = children.size();
        for (int i = 0; i < N; i++) {
            children.get(i).setLeft();
        }
    }

    public void placeRoot() {
        top = PADDING + (treeHeight - NODE_HEIGHT) / 2.0;
        double currentTop = PADDING;
        int N = children.size();
        for (int i = 0; i < N; i++) {
            DrawableViewNode child = children.get(i);
            child.place(currentTop, top - currentTop);
            currentTop += child.treeHeight + child.bottomSpacing;
        }
        bounds.y = 0;
        bounds.height = treeHeight + 2 * PADDING;
    }

    private void place(double treeTop, double rootDistance) {
        if (treeHeight <= rootDistance) {
            top = treeTop + treeHeight - NODE_HEIGHT;
        } else if (rootDistance <= -NODE_HEIGHT) {
            top = treeTop;
        } else {
            if (children.size() == 0) {
                top = treeTop;
            } else {
                top =
                        rootDistance + treeTop - NODE_HEIGHT + (2.0 * NODE_HEIGHT)
                                / (treeHeight + NODE_HEIGHT) * (treeHeight - rootDistance);
            }
        }
        int N = children.size();
        double currentTop = treeTop;
        for (int i = 0; i < N; i++) {
            DrawableViewNode child = children.get(i);
            child.place(currentTop, rootDistance);
            currentTop += child.treeHeight + child.bottomSpacing;
            rootDistance -= child.treeHeight + child.bottomSpacing;
        }
    }

    public DrawableViewNode getSelected(double x, double y) {
        if (x >= left && x < left + NODE_WIDTH && y >= top && y <= top + NODE_HEIGHT) {
            return this;
        }
        int N = children.size();
        for (int i = 0; i < N; i++) {
            DrawableViewNode selected = children.get(i).getSelected(x, y);
            if (selected != null) {
                return selected;
            }
        }
        return null;
    }

    /*
     * Moves the node the specified distance up.
     */
    public void move(double distance) {
        top -= distance;

        // Get the root
        DrawableViewNode root = this;
        while (root.parent != null) {
            root = root.parent;
        }

        // Figure out the new tree top.
        double treeTop;
        if (top + NODE_HEIGHT <= root.top) {
            treeTop = top + NODE_HEIGHT - treeHeight;
        } else if (top >= root.top + NODE_HEIGHT) {
            treeTop = top;
        } else {
            if (leaf) {
                treeTop = top;
            } else {
                double distanceRatio = 1 - (root.top + NODE_HEIGHT - top) / (2.0 * NODE_HEIGHT);
                treeTop = root.top - treeHeight + distanceRatio * (treeHeight + NODE_HEIGHT);
            }
        }
        // Go up the tree and figure out the tree top.
        DrawableViewNode node = this;
        while (node.parent != null) {
            int index = node.viewNode.index;
            for (int i = 0; i < index; i++) {
                DrawableViewNode sibling = node.parent.children.get(i);
                treeTop -= sibling.treeHeight + sibling.bottomSpacing;
            }
            node = node.parent;
        }

        // Update the bounds.
        root.bounds.y = Math.min(root.top - PADDING, treeTop - PADDING);
        root.bounds.height =
                Math.max(treeTop + root.treeHeight + PADDING, root.top + NODE_HEIGHT + PADDING)
                        - root.bounds.y;
        // Place all the children of the root
        double currentTop = treeTop;
        int N = root.children.size();
        for (int i = 0; i < N; i++) {
            DrawableViewNode child = root.children.get(i);
            child.place(currentTop, root.top - currentTop);
            currentTop += child.treeHeight + child.bottomSpacing;
        }

    }
}
