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
import org.eclipse.swt.events.MouseWheelListener;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Transform;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;

public class TreeView extends Canvas implements TreeChangeListener {

    private TreeViewModel model;

    private DrawableViewNode tree;

    private Rectangle viewport;

    private Transform transform;

    private Transform inverse;

    private double zoom;

    private Point lastPoint;

    private DrawableViewNode draggedNode;

    public TreeView(Composite parent) {
        super(parent, SWT.NONE);

        model = ComponentRegistry.getTreeViewModel();
        model.addTreeChangeListener(this);

        addPaintListener(paintListener);
        addMouseListener(mouseListener);
        addMouseMoveListener(mouseMoveListener);
        addMouseWheelListener(mouseWheelListener);
        addListener(SWT.Resize, resizeListener);
        addDisposeListener(disposeListener);

        transform = new Transform(Display.getDefault());
        inverse = new Transform(Display.getDefault());
    }

    private DisposeListener disposeListener = new DisposeListener() {
        public void widgetDisposed(DisposeEvent e) {
            model.removeTreeChangeListener(TreeView.this);
            transform.dispose();
            inverse.dispose();
        }
    };

    private Listener resizeListener = new Listener() {
        public void handleEvent(Event e) {
            synchronized (this) {
                if (tree != null && viewport != null) {

                    // I don't know what the best behaviour is... This seems
                    // like a good idea.
                    Point viewCenter =
                            new Point(viewport.x + viewport.width / 2, viewport.y + viewport.height
                                    / 2);
                    viewport.width = getBounds().width / zoom;
                    viewport.height = getBounds().height / zoom;
                    viewport.x = viewCenter.x - viewport.width / 2;
                    viewport.y = viewCenter.y - viewport.height / 2;
                }
            }
            if (viewport != null) {
                model.setViewport(viewport);
            }
        }
    };

    private MouseListener mouseListener = new MouseListener() {

        public void mouseDoubleClick(MouseEvent e) {
            // pass
        }

        public void mouseDown(MouseEvent e) {
            synchronized (this) {
                if (tree != null && viewport != null) {
                    Point pt = transformPoint(e.x, e.y);
                    draggedNode = tree.getSelected(pt.x, pt.y);
                    if (draggedNode == tree) {
                        draggedNode = null;
                    }
                    if (draggedNode != null) {
                        lastPoint = pt;
                    } else {
                        lastPoint = new Point(e.x, e.y);
                    }
                }
            }
        }

        public void mouseUp(MouseEvent e) {
            boolean redraw = false;
            boolean viewportChanged = false;
            synchronized (this) {
                if (tree != null && viewport != null && lastPoint != null) {
                    if (draggedNode == null) {
                        handleMouseDrag(new Point(e.x, e.y));
                        viewportChanged = true;
                    } else {
                        handleMouseDrag(transformPoint(e.x, e.y));
                    }
                    lastPoint = null;
                    draggedNode = null;
                    redraw = true;
                }
            }
            if (viewportChanged) {
                model.setViewport(viewport);
            } else if (redraw) {
                model.removeTreeChangeListener(TreeView.this);
                model.notifyTreeChanged();
                model.addTreeChangeListener(TreeView.this);
                doRedraw();
            }
        }

    };

    private MouseMoveListener mouseMoveListener = new MouseMoveListener() {
        public void mouseMove(MouseEvent e) {
            boolean redraw = false;
            boolean viewportChanged = false;
            synchronized (this) {
                if (tree != null && viewport != null && lastPoint != null) {
                    if (draggedNode == null) {
                        handleMouseDrag(new Point(e.x, e.y));
                        viewportChanged = true;
                    } else {
                        handleMouseDrag(transformPoint(e.x, e.y));
                    }
                    redraw = true;
                }
            }
            if (viewportChanged) {
                model.setViewport(viewport);
            } else if (redraw) {
                model.removeTreeChangeListener(TreeView.this);
                model.notifyTreeChanged();
                model.addTreeChangeListener(TreeView.this);
                doRedraw();
            }
        }
    };

    private void handleMouseDrag(Point pt) {
        if (draggedNode != null) {
            draggedNode.move(lastPoint.y - pt.y);
            lastPoint = pt;
            return;
        }
        double xDif = (lastPoint.x - pt.x) / zoom;
        double yDif = (lastPoint.y - pt.y) / zoom;

        if (viewport.width > tree.bounds.width) {
            if (xDif < 0 && viewport.x + viewport.width > tree.bounds.x + tree.bounds.width) {
                viewport.x =
                        Math.max(viewport.x + xDif, tree.bounds.x + tree.bounds.width
                                - viewport.width);
            } else if (xDif > 0 && viewport.x < tree.bounds.x) {
                viewport.x = Math.min(viewport.x + xDif, tree.bounds.x);
            }
        } else {
            if (xDif < 0 && viewport.x > tree.bounds.x) {
                viewport.x = Math.max(viewport.x + xDif, tree.bounds.x);
            } else if (xDif > 0 && viewport.x + viewport.width < tree.bounds.x + tree.bounds.width) {
                viewport.x =
                        Math.min(viewport.x + xDif, tree.bounds.x + tree.bounds.width
                                - viewport.width);
            }
        }
        if (viewport.height > tree.bounds.height) {
            if (yDif < 0 && viewport.y + viewport.height > tree.bounds.y + tree.bounds.height) {
                viewport.y =
                        Math.max(viewport.y + yDif, tree.bounds.y + tree.bounds.height
                                - viewport.height);
            } else if (yDif > 0 && viewport.y < tree.bounds.y) {
                viewport.y = Math.min(viewport.y + yDif, tree.bounds.y);
            }
        } else {
            if (yDif < 0 && viewport.y > tree.bounds.y) {
                viewport.y = Math.max(viewport.y + yDif, tree.bounds.y);
            } else if (yDif > 0
                    && viewport.y + viewport.height < tree.bounds.y + tree.bounds.height) {
                viewport.y =
                        Math.min(viewport.y + yDif, tree.bounds.y + tree.bounds.height
                                - viewport.height);
            }
        }
        lastPoint = pt;
    }

    private Point transformPoint(double x, double y) {
        float[] pt = {
                (float) x, (float) y
        };
        inverse.transform(pt);
        return new Point(pt[0], pt[1]);
    }

    private MouseWheelListener mouseWheelListener = new MouseWheelListener() {

        public void mouseScrolled(MouseEvent e) {
            Point zoomPoint = null;
            synchronized (this) {
                if (tree != null && viewport != null) {
                    zoom += Math.ceil(e.count / 3.0) * 0.1;
                    zoomPoint = transformPoint(e.x, e.y);
                }
            }
            if (zoomPoint != null) {
                model.zoomOnPoint(zoom, zoomPoint);
            }
        }
    };

    private PaintListener paintListener = new PaintListener() {
        public void paintControl(PaintEvent e) {
            synchronized (this) {
                e.gc.setBackground(Display.getDefault().getSystemColor(SWT.COLOR_WHITE));
                e.gc.fillRectangle(0, 0, getBounds().width, getBounds().height);
                if (tree != null && viewport != null) {
                    e.gc.setTransform(transform);
                    paintRecursive(e.gc, tree);
                }
            }
        }
    };

    static void paintRecursive(GC gc, DrawableViewNode node) {
        gc.drawRectangle(node.left, (int) Math.round(node.top), DrawableViewNode.NODE_WIDTH,
                DrawableViewNode.NODE_HEIGHT);
        int N = node.children.size();
        for (int i = 0; i < N; i++) {
            DrawableViewNode child = node.children.get(i);
            paintRecursive(gc, child);
            gc.drawLine(node.left + DrawableViewNode.NODE_WIDTH, (int) Math.round(node.top)
                    + DrawableViewNode.NODE_HEIGHT / 2, child.left, (int) Math.round(child.top)
                    + DrawableViewNode.NODE_HEIGHT / 2);
        }
    }

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
            if (tree == null) {
                viewport = null;
            } else {
                Display.getDefault().syncExec(new Runnable() {
                    public void run() {
                        viewport =
                                new Rectangle((tree.bounds.width - getBounds().width) / 2,
                                        (tree.bounds.height - getBounds().height) / 2,
                                        getBounds().width, getBounds().height);
                    }
                });
            }
        }
        if (viewport != null) {
            model.setViewport(viewport);
        }
    }

    private void setTransform() {
        if (viewport != null && tree != null) {
            // Set the transform.
            transform.identity();
            inverse.identity();

            transform.scale((float) zoom, (float) zoom);
            inverse.scale((float) zoom, (float) zoom);
            transform.translate((float) -viewport.x, (float) -viewport.y);
            inverse.translate((float) -viewport.x, (float) -viewport.y);
            inverse.invert();
        }
    }

    public void viewportChanged() {
        synchronized (this) {
            viewport = model.getViewport();
            zoom = model.getZoom();
            setTransform();
        }
        doRedraw();
    }

    public void zoomChanged() {
        viewportChanged();
    }
}
