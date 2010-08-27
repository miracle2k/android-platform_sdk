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

import com.android.ddmuilib.ImageLoader;
import com.android.hierarchyviewerlib.models.TreeViewModel;
import com.android.hierarchyviewerlib.models.TreeViewModel.TreeChangeListener;
import com.android.hierarchyviewerlib.ui.util.DrawableViewNode;
import com.android.hierarchyviewerlib.ui.util.DrawableViewNode.Point;
import com.android.hierarchyviewerlib.ui.util.DrawableViewNode.Rectangle;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.MouseMoveListener;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Path;
import org.eclipse.swt.graphics.Transform;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;

public class TreeViewOverview extends Canvas implements TreeChangeListener {

    private TreeViewModel model;

    private DrawableViewNode tree;

    private Rectangle viewport;

    private Transform transform;

    private Transform inverse;

    private Rectangle bounds = new Rectangle();

    private double scale;

    private boolean dragging = false;

    private DrawableViewNode selectedNode;

    private static Image notSelectedImage;

    private static Image selectedImage;

    private static Image filteredImage;

    private static Image filteredSelectedImage;

    public TreeViewOverview(Composite parent) {
        super(parent, SWT.NONE);

        model = TreeViewModel.getModel();
        model.addTreeChangeListener(this);

        loadResources();

        addPaintListener(paintListener);
        addMouseListener(mouseListener);
        addMouseMoveListener(mouseMoveListener);
        addListener(SWT.Resize, resizeListener);
        addDisposeListener(disposeListener);

        transform = new Transform(Display.getDefault());
        inverse = new Transform(Display.getDefault());
    }

    private void loadResources() {
        ImageLoader loader = ImageLoader.getLoader(this.getClass());
        notSelectedImage = loader.loadImage("not-selected.png", Display.getDefault());
        selectedImage = loader.loadImage("selected-small.png", Display.getDefault());
        filteredImage = loader.loadImage("filtered.png", Display.getDefault());
        filteredSelectedImage =
                loader.loadImage("selected-filtered-small.png", Display.getDefault());
    }

    private DisposeListener disposeListener = new DisposeListener() {
        public void widgetDisposed(DisposeEvent e) {
            model.removeTreeChangeListener(TreeViewOverview.this);
            transform.dispose();
            inverse.dispose();
        }
    };

    private MouseListener mouseListener = new MouseListener() {

        public void mouseDoubleClick(MouseEvent e) {
            // pass
        }

        public void mouseDown(MouseEvent e) {
            boolean redraw = false;
            synchronized (TreeViewOverview.this) {
                if (tree != null && viewport != null) {
                    dragging = true;
                    redraw = true;
                    handleMouseEvent(transformPoint(e.x, e.y));
                }
            }
            if (redraw) {
                model.removeTreeChangeListener(TreeViewOverview.this);
                model.setViewport(viewport);
                model.addTreeChangeListener(TreeViewOverview.this);
                doRedraw();
            }
        }

        public void mouseUp(MouseEvent e) {
            boolean redraw = false;
            synchronized (TreeViewOverview.this) {
                if (tree != null && viewport != null) {
                    dragging = false;
                    redraw = true;
                    handleMouseEvent(transformPoint(e.x, e.y));
                    setBounds();
                    setTransform();
                }
            }
            if (redraw) {
                model.removeTreeChangeListener(TreeViewOverview.this);
                model.setViewport(viewport);
                model.addTreeChangeListener(TreeViewOverview.this);
                doRedraw();
            }
        }

    };

    private MouseMoveListener mouseMoveListener = new MouseMoveListener() {
        public void mouseMove(MouseEvent e) {
            boolean moved = false;
            synchronized (TreeViewOverview.this) {
                if (dragging) {
                    moved = true;
                    handleMouseEvent(transformPoint(e.x, e.y));
                }
            }
            if (moved) {
                model.removeTreeChangeListener(TreeViewOverview.this);
                model.setViewport(viewport);
                model.addTreeChangeListener(TreeViewOverview.this);
                doRedraw();
            }
        }
    };

    private void handleMouseEvent(Point pt) {
        viewport.x = pt.x - viewport.width / 2;
        viewport.y = pt.y - viewport.height / 2;
        if (viewport.x < bounds.x) {
            viewport.x = bounds.x;
        }
        if (viewport.y < bounds.y) {
            viewport.y = bounds.y;
        }
        if (viewport.x + viewport.width > bounds.x + bounds.width) {
            viewport.x = bounds.x + bounds.width - viewport.width;
        }
        if (viewport.y + viewport.height > bounds.y + bounds.height) {
            viewport.y = bounds.y + bounds.height - viewport.height;
        }
    }

    private Point transformPoint(double x, double y) {
        float[] pt = {
                (float) x, (float) y
        };
        inverse.transform(pt);
        return new Point(pt[0], pt[1]);
    }

    private Listener resizeListener = new Listener() {
        public void handleEvent(Event arg0) {
            synchronized (TreeViewOverview.this) {
                setTransform();
            }
            doRedraw();
        }
    };

    private PaintListener paintListener = new PaintListener() {
        public void paintControl(PaintEvent e) {
            synchronized (TreeViewOverview.this) {
                if (tree != null && viewport != null) {
                    e.gc.setBackground(Display.getDefault().getSystemColor(SWT.COLOR_BLACK));
                    e.gc.setForeground(Display.getDefault().getSystemColor(SWT.COLOR_WHITE));
                    e.gc.fillRectangle(0, 0, getBounds().width, getBounds().height);
                    e.gc.setTransform(transform);
                    e.gc.setLineWidth((int) Math.ceil(0.7 / scale));
                    Path connectionPath = new Path(Display.getDefault());
                    paintRecursive(e.gc, tree, connectionPath);
                    e.gc.drawPath(connectionPath);
                    connectionPath.dispose();

                    e.gc.setAlpha(50);
                    e.gc.setBackground(Display.getDefault().getSystemColor(SWT.COLOR_WHITE));
                    e.gc.fillRectangle((int) viewport.x, (int) viewport.y, (int) Math
                            .ceil(viewport.width), (int) Math.ceil(viewport.height));

                    e.gc.setAlpha(255);
                    e.gc.setForeground(Display.getDefault().getSystemColor(SWT.COLOR_DARK_GRAY));
                    e.gc.setLineWidth((int) Math.ceil(2 / scale));
                    e.gc.drawRectangle((int) viewport.x, (int) viewport.y, (int) Math
                            .ceil(viewport.width), (int) Math.ceil(viewport.height));
                }
            }
        }
    };

    private void paintRecursive(GC gc, DrawableViewNode node, Path connectionPath) {
        if (selectedNode == node && node.viewNode.filtered) {
            gc.drawImage(filteredSelectedImage, node.left, (int) Math.round(node.top));
        } else if (selectedNode == node) {
            gc.drawImage(selectedImage, node.left, (int) Math.round(node.top));
        } else if (node.viewNode.filtered) {
            gc.drawImage(filteredImage, node.left, (int) Math.round(node.top));
        } else {
            gc.drawImage(notSelectedImage, node.left, (int) Math.round(node.top));
        }
        int N = node.children.size();
        if (N == 0) {
            return;
        }
        float childSpacing =
                (1.0f * (DrawableViewNode.NODE_HEIGHT - 2 * TreeView.LINE_PADDING)) / N;
        for (int i = 0; i < N; i++) {
            DrawableViewNode child = node.children.get(i);
            paintRecursive(gc, child, connectionPath);
            float x1 = node.left + DrawableViewNode.NODE_WIDTH;
            float y1 =
                    (float) node.top + TreeView.LINE_PADDING + childSpacing * i + childSpacing / 2;
            float x2 = child.left;
            float y2 = (float) child.top + DrawableViewNode.NODE_HEIGHT / 2.0f;
            float cx1 = x1 + TreeView.BEZIER_FRACTION * DrawableViewNode.PARENT_CHILD_SPACING;
            float cy1 = y1;
            float cx2 = x2 - TreeView.BEZIER_FRACTION * DrawableViewNode.PARENT_CHILD_SPACING;
            float cy2 = y2;
            connectionPath.moveTo(x1, y1);
            connectionPath.cubicTo(cx1, cy1, cx2, cy2, x2, y2);
        }
    }

    private void doRedraw() {
        Display.getDefault().asyncExec(new Runnable() {
            public void run() {
                redraw();
            }
        });
    }

    public void treeChanged() {
        Display.getDefault().syncExec(new Runnable() {
            public void run() {
                synchronized (this) {
                    tree = model.getTree();
                    selectedNode = model.getSelection();
                    setBounds();
                    setTransform();
                }
            }
        });
        doRedraw();
    }

    private void setBounds() {
        if (viewport != null && tree != null) {
            bounds.x = Math.min(viewport.x, tree.bounds.x);
            bounds.y = Math.min(viewport.y, tree.bounds.y);
            bounds.width =
                    Math.max(viewport.x + viewport.width, tree.bounds.x + tree.bounds.width)
                            - bounds.x;
            bounds.height =
                    Math.max(viewport.y + viewport.height, tree.bounds.y + tree.bounds.height)
                            - bounds.y;
        }
    }

    private void setTransform() {
        if (viewport != null && tree != null) {

            transform.identity();
            inverse.identity();
            final Point size = new Point();
            size.x = getBounds().width;
            size.y = getBounds().height;
            if (bounds.width == 0 || bounds.height == 0 || size.x == 0 || size.y == 0) {
                scale = 1;
            } else {
                scale = Math.min(size.x / bounds.width, size.y / bounds.height);
            }
            transform.scale((float) scale, (float) scale);
            inverse.scale((float) scale, (float) scale);
            transform.translate((float) -bounds.x, (float) -bounds.y);
            inverse.translate((float) -bounds.x, (float) -bounds.y);
            if (size.x / bounds.width < size.y / bounds.height) {
                transform.translate(0, (float) (size.y / scale - bounds.height) / 2);
                inverse.translate(0, (float) (size.y / scale - bounds.height) / 2);
            } else {
                transform.translate((float) (size.x / scale - bounds.width) / 2, 0);
                inverse.translate((float) (size.x / scale - bounds.width) / 2, 0);
            }
            inverse.invert();
        }
    }

    public void viewportChanged() {
        Display.getDefault().syncExec(new Runnable() {
            public void run() {
                synchronized (this) {
                    viewport = model.getViewport();
                    setBounds();
                    setTransform();
                }
            }
        });
        doRedraw();
    }

    public void zoomChanged() {
        viewportChanged();
    }

    public void selectionChanged() {
        synchronized (this) {
            selectedNode = model.getSelection();
        }
        doRedraw();
    }
}
