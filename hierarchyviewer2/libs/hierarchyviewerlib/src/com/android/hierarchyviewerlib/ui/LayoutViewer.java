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

package com.android.hierarchyviewerlib.ui;

import com.android.hierarchyviewerlib.HierarchyViewerDirector;
import com.android.hierarchyviewerlib.models.TreeViewModel;
import com.android.hierarchyviewerlib.models.TreeViewModel.TreeChangeListener;
import com.android.hierarchyviewerlib.ui.util.DrawableViewNode;
import com.android.hierarchyviewerlib.ui.util.DrawableViewNode.Point;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.graphics.Transform;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;

import java.util.ArrayList;

public class LayoutViewer extends Canvas implements TreeChangeListener {

    private TreeViewModel model;

    private DrawableViewNode tree;

    private DrawableViewNode selectedNode;

    private Transform transform;

    private Transform inverse;

    private double scale;

    private boolean showExtras = false;

    private boolean onBlack = true;

    public LayoutViewer(Composite parent) {
        super(parent, SWT.NONE);
        model = TreeViewModel.getModel();
        model.addTreeChangeListener(this);

        addDisposeListener(disposeListener);
        addPaintListener(paintListener);
        addListener(SWT.Resize, resizeListener);
        addMouseListener(mouseListener);

        transform = new Transform(Display.getDefault());
        inverse = new Transform(Display.getDefault());
    }

    public void setShowExtras(boolean show) {
        showExtras = show;
        doRedraw();
    }

    public void setOnBlack(boolean value) {
        onBlack = value;
        doRedraw();
    }

    public boolean getOnBlack() {
        return onBlack;
    }

    private DisposeListener disposeListener = new DisposeListener() {
        public void widgetDisposed(DisposeEvent e) {
            transform.dispose();
            inverse.dispose();
        }
    };

    private Listener resizeListener = new Listener() {
        public void handleEvent(Event e) {
            synchronized (this) {
                setTransform();
            }
        }
    };

    private MouseListener mouseListener = new MouseListener() {

        public void mouseDoubleClick(MouseEvent e) {
            if (selectedNode != null) {
                HierarchyViewerDirector.getDirector()
                        .showCapture(getShell(), selectedNode.viewNode);
            }
        }

        public void mouseDown(MouseEvent e) {
            boolean selectionChanged = false;
            DrawableViewNode newSelection = null;
            synchronized (LayoutViewer.this) {
                if (tree != null) {
                    float[] pt = {
                            e.x, e.y
                    };
                    inverse.transform(pt);
                    newSelection =
                            updateSelection(tree, pt[0], pt[1], 0, 0, 0, 0, tree.viewNode.width,
                                    tree.viewNode.height);
                    if (selectedNode != newSelection) {
                        selectionChanged = true;
                    }
                }
            }
            if (selectionChanged) {
                model.setSelection(newSelection);
            }
        }

        public void mouseUp(MouseEvent e) {
            // pass
        }
    };

    private DrawableViewNode updateSelection(DrawableViewNode node, float x, float y, int left,
            int top, int clipX, int clipY, int clipWidth, int clipHeight) {
        if (!node.treeDrawn) {
            return null;
        }
        // Update the clip
        int x1 = Math.max(left, clipX);
        int x2 = Math.min(left + node.viewNode.width, clipX + clipWidth);
        int y1 = Math.max(top, clipY);
        int y2 = Math.min(top + node.viewNode.height, clipY + clipHeight);
        clipX = x1;
        clipY = y1;
        clipWidth = x2 - x1;
        clipHeight = y2 - y1;
        if (x < clipX || x > clipX + clipWidth || y < clipY || y > clipY + clipHeight) {
            return null;
        }
        final int N = node.children.size();
        for (int i = N - 1; i >= 0; i--) {
            DrawableViewNode child = node.children.get(i);
            DrawableViewNode ret =
                    updateSelection(child, x, y,
                            left + child.viewNode.left - node.viewNode.scrollX, top
                                    + child.viewNode.top - node.viewNode.scrollY, clipX, clipY,
                            clipWidth, clipHeight);
            if (ret != null) {
                return ret;
            }
        }
        return node;
    }

    private PaintListener paintListener = new PaintListener() {
        public void paintControl(PaintEvent e) {
            synchronized (LayoutViewer.this) {
                if (onBlack) {
                    e.gc.setBackground(Display.getDefault().getSystemColor(SWT.COLOR_BLACK));
                } else {
                    e.gc.setBackground(Display.getDefault().getSystemColor(SWT.COLOR_WHITE));
                }
                e.gc.fillRectangle(0, 0, getBounds().width, getBounds().height);
                if (tree != null) {
                    e.gc.setLineWidth((int) Math.ceil(0.3 / scale));
                    e.gc.setTransform(transform);
                    if (onBlack) {
                        e.gc.setForeground(Display.getDefault().getSystemColor(SWT.COLOR_WHITE));
                    } else {
                        e.gc.setForeground(Display.getDefault().getSystemColor(SWT.COLOR_BLACK));
                    }
                    Rectangle parentClipping = e.gc.getClipping();
                    e.gc.setClipping(0, 0, tree.viewNode.width + (int) Math.ceil(0.3 / scale),
                            tree.viewNode.height + (int) Math.ceil(0.3 / scale));
                    paintRecursive(e.gc, tree, 0, 0, true);

                    if (selectedNode != null) {
                        e.gc.setClipping(parentClipping);

                        // w00t, let's be nice and display the whole path in
                        // light red and the selected node in dark red.
                        ArrayList<Point> rightLeftDistances = new ArrayList<Point>();
                        int left = 0;
                        int top = 0;
                        DrawableViewNode currentNode = selectedNode;
                        while (currentNode != tree) {
                            left += currentNode.viewNode.left;
                            top += currentNode.viewNode.top;
                            currentNode = currentNode.parent;
                            left -= currentNode.viewNode.scrollX;
                            top -= currentNode.viewNode.scrollY;
                            rightLeftDistances.add(new Point(left, top));
                        }
                        e.gc.setForeground(Display.getDefault().getSystemColor(SWT.COLOR_DARK_RED));
                        currentNode = selectedNode.parent;
                        final int N = rightLeftDistances.size();
                        for (int i = 0; i < N; i++) {
                            e.gc.drawRectangle((int) (left - rightLeftDistances.get(i).x),
                                    (int) (top - rightLeftDistances.get(i).y),
                                    currentNode.viewNode.width, currentNode.viewNode.height);
                            currentNode = currentNode.parent;
                        }

                        if (showExtras && selectedNode.viewNode.image != null) {
                            e.gc.drawImage(selectedNode.viewNode.image, left, top);
                            if (onBlack) {
                                e.gc.setForeground(Display.getDefault().getSystemColor(
                                        SWT.COLOR_WHITE));
                            } else {
                                e.gc.setForeground(Display.getDefault().getSystemColor(
                                        SWT.COLOR_BLACK));
                            }
                            paintRecursive(e.gc, selectedNode, left, top, true);

                        }

                        e.gc.setForeground(Display.getDefault().getSystemColor(SWT.COLOR_RED));
                        e.gc.setLineWidth((int) Math.ceil(2 / scale));
                        e.gc.drawRectangle(left, top, selectedNode.viewNode.width,
                                selectedNode.viewNode.height);
                    }
                }
            }
        }
    };

    private void paintRecursive(GC gc, DrawableViewNode node, int left, int top, boolean root) {
        if (!node.treeDrawn) {
            return;
        }
        // Don't shift the root
        if (!root) {
            left += node.viewNode.left;
            top += node.viewNode.top;
        }
        Rectangle parentClipping = gc.getClipping();
        int x1 = Math.max(parentClipping.x, left);
        int x2 =
                Math.min(parentClipping.x + parentClipping.width, left + node.viewNode.width
                        + (int) Math.ceil(0.3 / scale));
        int y1 = Math.max(parentClipping.y, top);
        int y2 =
                Math.min(parentClipping.y + parentClipping.height, top + node.viewNode.height
                        + (int) Math.ceil(0.3 / scale));

        // Clipping is weird... You set it to -5 and it comes out 17 or
        // something.
        if (x2 <= x1 || y2 <= y1) {
            return;
        }
        gc.setClipping(x1, y1, x2 - x1, y2 - y1);
        final int N = node.children.size();
        for (int i = 0; i < N; i++) {
            paintRecursive(gc, node.children.get(i), left - node.viewNode.scrollX, top
                    - node.viewNode.scrollY, false);
        }
        gc.setClipping(parentClipping);
        if (!node.viewNode.willNotDraw) {
            gc.drawRectangle(left, top, node.viewNode.width, node.viewNode.height);
        }

    }

    private void doRedraw() {
        Display.getDefault().asyncExec(new Runnable() {
            public void run() {
                redraw();
            }
        });
    }

    private void setTransform() {
        if (tree != null) {
            Rectangle bounds = getBounds();
            int leftRightPadding = bounds.width <= 30 ? 0 : 5;
            int topBottomPadding = bounds.height <= 30 ? 0 : 5;
            scale =
                    Math.min(1.0 * (bounds.width - leftRightPadding * 2) / tree.viewNode.width, 1.0
                            * (bounds.height - topBottomPadding * 2) / tree.viewNode.height);
            int scaledWidth = (int) Math.ceil(tree.viewNode.width * scale);
            int scaledHeight = (int) Math.ceil(tree.viewNode.height * scale);

            transform.identity();
            inverse.identity();
            transform.translate((bounds.width - scaledWidth) / 2.0f,
                    (bounds.height - scaledHeight) / 2.0f);
            inverse.translate((bounds.width - scaledWidth) / 2.0f,
                    (bounds.height - scaledHeight) / 2.0f);
            transform.scale((float) scale, (float) scale);
            inverse.scale((float) scale, (float) scale);
            if (bounds.width != 0 && bounds.height != 0) {
                inverse.invert();
            }
        }
    }

    public void selectionChanged() {
        synchronized (this) {
            if (selectedNode != null) {
                selectedNode.viewNode.dereferenceImage();
            }
            selectedNode = model.getSelection();
            if (selectedNode != null) {
                selectedNode.viewNode.referenceImage();
            }
        }
        doRedraw();
    }

    // Note the syncExec and then synchronized... It avoids deadlock
    public void treeChanged() {
        Display.getDefault().syncExec(new Runnable() {
            public void run() {
                synchronized (this) {
                    if (selectedNode != null) {
                        selectedNode.viewNode.dereferenceImage();
                    }
                    tree = model.getTree();
                    selectedNode = model.getSelection();
                    if (selectedNode != null) {
                        selectedNode.viewNode.referenceImage();
                    }
                    setTransform();
                }
            }
        });
        doRedraw();
    }

    public void viewportChanged() {
        // pass
    }

    public void zoomChanged() {
        // pass
    }
}
