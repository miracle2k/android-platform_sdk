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

package com.android.hierarchyvieweruilib;

import com.android.hierarchyviewerlib.ComponentRegistry;
import com.android.hierarchyviewerlib.models.TreeViewModel;
import com.android.hierarchyviewerlib.models.TreeViewModel.TreeChangeListener;
import com.android.hierarchyviewerlib.scene.DrawableViewNode;
import com.android.hierarchyviewerlib.scene.DrawableViewNode.Point;
import com.android.hierarchyviewerlib.scene.DrawableViewNode.Rectangle;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.MouseMoveListener;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
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

    public TreeViewOverview(Composite parent) {
        super(parent, SWT.NONE);

        model = ComponentRegistry.getTreeViewModel();
        model.addTreeChangeListener(this);

        addPaintListener(paintListener);
        addMouseListener(mouseListener);
        addMouseMoveListener(mouseMoveListener);
        addListener(SWT.Resize, resizeListener);
        addDisposeListener(disposeListener);

        transform = new Transform(Display.getDefault());
        inverse = new Transform(Display.getDefault());
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
            synchronized (this) {
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
            synchronized (this) {
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
            synchronized (this) {
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
            synchronized (this) {
                setTransform();
            }
            doRedraw();
        }
    };

    private PaintListener paintListener = new PaintListener() {
        public void paintControl(PaintEvent e) {
            if (tree != null && viewport != null) {
                e.gc.setTransform(transform);
                e.gc.setBackground(Display.getDefault().getSystemColor(SWT.COLOR_WHITE));
                e.gc.fillRectangle((int) bounds.x, (int) bounds.y, (int) Math.ceil(bounds.width),
                        (int) Math.ceil(bounds.height));
                TreeView.paintRecursive(e.gc, tree);

                e.gc.setAlpha(100);
                e.gc.setBackground(Display.getDefault().getSystemColor(SWT.COLOR_DARK_GRAY));
                e.gc.fillRectangle((int) viewport.x, (int) viewport.y, (int) Math
                        .ceil(viewport.width), (int) Math.ceil(viewport.height));

                e.gc.setAlpha(255);
                e.gc.setForeground(Display.getDefault().getSystemColor(SWT.COLOR_BLACK));
                e.gc.setLineWidth((int) Math.ceil(2 / scale));
                e.gc.drawRectangle((int) viewport.x, (int) viewport.y, (int) Math
                        .ceil(viewport.width), (int) Math.ceil(viewport.height));
            }
        }
    };

    private void doRedraw() {
        Display.getDefault().syncExec(new Runnable() {
            public void run() {
                redraw();
            }
        });
    }

    public void treeChanged() {
        synchronized (this) {
            tree = model.getTree();
            setBounds();
            setTransform();
        }
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
            Display.getDefault().syncExec(new Runnable() {
                public void run() {
                    size.x = getBounds().width;
                    size.y = getBounds().height;
                }
            });
            scale = Math.min(size.x / bounds.width, size.y / bounds.height);
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
        synchronized (this) {
            viewport = model.getViewport();
            setBounds();
            setTransform();
        }
        doRedraw();
    }

    public void zoomChanged() {
        viewportChanged();
    }

}
