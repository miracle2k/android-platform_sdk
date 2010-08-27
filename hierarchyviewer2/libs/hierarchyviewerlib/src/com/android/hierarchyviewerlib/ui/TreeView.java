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
import com.android.hierarchyviewerlib.ComponentRegistry;
import com.android.hierarchyviewerlib.device.ViewNode.ProfileRating;
import com.android.hierarchyviewerlib.models.TreeViewModel;
import com.android.hierarchyviewerlib.models.TreeViewModel.TreeChangeListener;
import com.android.hierarchyviewerlib.ui.util.DrawableViewNode;
import com.android.hierarchyviewerlib.ui.util.DrawableViewNode.Point;
import com.android.hierarchyviewerlib.ui.util.DrawableViewNode.Rectangle;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.MouseMoveListener;
import org.eclipse.swt.events.MouseWheelListener;
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

public class TreeView extends Canvas implements TreeChangeListener {

    private TreeViewModel model;

    private DrawableViewNode tree;

    private DrawableViewNode selectedNode;

    private Rectangle viewport;

    private Transform transform;

    private Transform inverse;

    private double zoom;

    private Point lastPoint;

    private DrawableViewNode draggedNode;

    public static final int LINE_PADDING = 10;

    public static final float BEZIER_FRACTION = 0.35f;

    private Image redImage;

    private Image yellowImage;

    private Image greenImage;

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
        addKeyListener(keyListener);

        transform = new Transform(Display.getDefault());
        inverse = new Transform(Display.getDefault());

        ImageLoader loader = ImageLoader.getLoader(this.getClass());
        redImage = loader.loadImage("red.png", Display.getDefault());
        yellowImage = loader.loadImage("yellow.png", Display.getDefault());
        greenImage = loader.loadImage("green.png", Display.getDefault());
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
            synchronized (TreeView.this) {
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

    private KeyListener keyListener = new KeyListener() {

        public void keyPressed(KeyEvent e) {
            boolean selectionChanged = false;
            DrawableViewNode clickedNode = null;
            synchronized (TreeView.this) {
                if (tree != null && viewport != null && selectedNode != null) {
                    switch (e.keyCode) {
                        case SWT.ARROW_LEFT:
                            if(selectedNode.parent != null) {
                                selectedNode = selectedNode.parent;
                                selectionChanged = true;
                            }
                            break;
                        case SWT.ARROW_UP:
                            int levelsOut = 0;
                            DrawableViewNode currentNode = selectedNode;
                            while (currentNode.parent != null && currentNode.viewNode.index == 0) {
                                levelsOut++;
                                currentNode = currentNode.parent;
                            }
                            if (currentNode.parent != null) {
                                selectionChanged = true;
                                currentNode =
                                        currentNode.parent.children
                                                .get(currentNode.viewNode.index - 1);
                                while (currentNode.children.size() != 0) {
                                    currentNode =
                                            currentNode.children
                                                    .get(currentNode.children.size() - 1);
                                    levelsOut--;
                                }
                            }
                            if (selectionChanged) {
                                selectedNode = currentNode;
                            }
                            break;
                        case SWT.ARROW_DOWN:
                            levelsOut = 0;
                            currentNode = selectedNode;
                            while (currentNode.parent != null
                                    && currentNode.viewNode.index + 1 == currentNode.parent.children
                                    .size()) {
                                levelsOut++;
                                currentNode = currentNode.parent;
                            }
                            if (currentNode.parent != null) {
                                selectionChanged = true;
                                currentNode =
                                        currentNode.parent.children
                                                .get(currentNode.viewNode.index + 1);
                                while (currentNode.children.size() != 0) {
                                    currentNode = currentNode.children.get(0);
                                    levelsOut--;
                                }
                            }
                            if (selectionChanged) {
                                selectedNode = currentNode;
                            }
                            break;
                        case SWT.ARROW_RIGHT:
                            DrawableViewNode rightNode = null;
                            double mostOverlap = 0;
                            final int N = selectedNode.children.size();
                            for(int i = 0; i<N; i++) {
                                DrawableViewNode child = selectedNode.children.get(i);
                                DrawableViewNode topMostChild = child;
                                while (topMostChild.children.size() != 0) {
                                    topMostChild = topMostChild.children.get(0);
                                }
                                double overlap =
                                        Math.min(DrawableViewNode.NODE_HEIGHT, Math.min(
                                                selectedNode.top + DrawableViewNode.NODE_HEIGHT
                                                        - topMostChild.top, topMostChild.top
                                                        + child.treeHeight - selectedNode.top));
                                if (overlap > mostOverlap) {
                                    mostOverlap = overlap;
                                    rightNode = child;
                                }
                            }
                            if (rightNode != null) {
                                selectedNode = rightNode;
                                selectionChanged = true;
                            }
                            break;
                        case SWT.CR:
                            clickedNode = selectedNode;
                            break;
                    }
                }
            }
            if (selectionChanged) {
                model.setSelection(selectedNode);
            }
            if (clickedNode != null) {
                ComponentRegistry.getDirector().showCapture(getShell(), clickedNode.viewNode);
            }
        }

        public void keyReleased(KeyEvent e) {
        }
    };

    private MouseListener mouseListener = new MouseListener() {

        public void mouseDoubleClick(MouseEvent e) {
            DrawableViewNode clickedNode = null;
            synchronized (TreeView.this) {
                if (tree != null && viewport != null) {
                    Point pt = transformPoint(e.x, e.y);
                    clickedNode = tree.getSelected(pt.x, pt.y);
                }
            }
            if (clickedNode != null) {
                ComponentRegistry.getDirector().showCapture(getShell(), clickedNode.viewNode);
            }
        }

        public void mouseDown(MouseEvent e) {
            boolean selectionChanged = false;
            synchronized (TreeView.this) {
                if (tree != null && viewport != null) {
                    Point pt = transformPoint(e.x, e.y);
                    draggedNode = tree.getSelected(pt.x, pt.y);
                    if (draggedNode != null && draggedNode != selectedNode) {
                        selectedNode = draggedNode;
                        selectionChanged = true;
                    }
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
            if (selectionChanged) {
                model.setSelection(selectedNode);
            }
        }

        public void mouseUp(MouseEvent e) {
            boolean redraw = false;
            boolean viewportChanged = false;
            synchronized (TreeView.this) {
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
                model.notifyViewportChanged();
                model.addTreeChangeListener(TreeView.this);
                doRedraw();
            }
        }

    };

    private MouseMoveListener mouseMoveListener = new MouseMoveListener() {
        public void mouseMove(MouseEvent e) {
            boolean redraw = false;
            boolean viewportChanged = false;
            synchronized (TreeView.this) {
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
                model.notifyViewportChanged();
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
            synchronized (TreeView.this) {
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
            synchronized (TreeView.this) {
                e.gc.setBackground(Display.getDefault().getSystemColor(SWT.COLOR_WHITE));
                e.gc.fillRectangle(0, 0, getBounds().width, getBounds().height);
                if (tree != null && viewport != null) {
                    e.gc.setTransform(transform);
                    e.gc.setBackground(Display.getDefault().getSystemColor(SWT.COLOR_GRAY));
                    Path connectionPath = new Path(Display.getDefault());
                    paintRecursive(e.gc, tree, connectionPath);
                    e.gc.drawPath(connectionPath);
                    connectionPath.dispose();
                }
            }
        }
    };

    private void paintRecursive(GC gc, DrawableViewNode node, Path connectionPath) {
        if (selectedNode == node) {
            gc.fillRectangle(node.left, (int) Math.round(node.top), DrawableViewNode.NODE_WIDTH,
                    DrawableViewNode.NODE_HEIGHT);
        } else {
            gc.drawRectangle(node.left, (int) Math.round(node.top), DrawableViewNode.NODE_WIDTH,
                    DrawableViewNode.NODE_HEIGHT);
        }

        int fontHeight = gc.getFontMetrics().getHeight();

        // Draw the text...
        int contentWidth =
                DrawableViewNode.NODE_WIDTH - 2 * DrawableViewNode.CONTENT_LEFT_RIGHT_PADDING;
        String name = node.viewNode.name;
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex != -1) {
            name = name.substring(dotIndex + 1);
        }
        double x = node.left + DrawableViewNode.CONTENT_LEFT_RIGHT_PADDING;
        double y = node.top + DrawableViewNode.CONTENT_TOP_BOTTOM_PADDING;
        drawTextInArea(gc, name, x, y, contentWidth, fontHeight);

        y += fontHeight + DrawableViewNode.CONTENT_INTER_PADDING;

        gc.drawText("@" + node.viewNode.hashCode, (int) x, (int) y, SWT.DRAW_TRANSPARENT);

        y += fontHeight + DrawableViewNode.CONTENT_INTER_PADDING;
        if (!node.viewNode.id.equals("NO_ID")) {
            drawTextInArea(gc, node.viewNode.id, x, y, contentWidth, fontHeight);
        }

        if (node.viewNode.measureRating != ProfileRating.NONE) {
            y =
                    node.top + DrawableViewNode.NODE_HEIGHT
                            - DrawableViewNode.CONTENT_TOP_BOTTOM_PADDING
                            - redImage.getBounds().height;
            x +=
                    (contentWidth - (redImage.getBounds().width * 3 + 2 * DrawableViewNode.CONTENT_INTER_PADDING)) / 2;
            switch (node.viewNode.measureRating) {
                case GREEN:
                    gc.drawImage(greenImage, (int) x, (int) y);
                    break;
                case YELLOW:
                    gc.drawImage(yellowImage, (int) x, (int) y);
                    break;
                case RED:
                    gc.drawImage(redImage, (int) x, (int) y);
                    break;
            }

            x += redImage.getBounds().width + DrawableViewNode.CONTENT_INTER_PADDING;
            switch (node.viewNode.layoutRating) {
                case GREEN:
                    gc.drawImage(greenImage, (int) x, (int) y);
                    break;
                case YELLOW:
                    gc.drawImage(yellowImage, (int) x, (int) y);
                    break;
                case RED:
                    gc.drawImage(redImage, (int) x, (int) y);
                    break;
            }

            x += redImage.getBounds().width + DrawableViewNode.CONTENT_INTER_PADDING;
            switch (node.viewNode.drawRating) {
                case GREEN:
                    gc.drawImage(greenImage, (int) x, (int) y);
                    break;
                case YELLOW:
                    gc.drawImage(yellowImage, (int) x, (int) y);
                    break;
                case RED:
                    gc.drawImage(redImage, (int) x, (int) y);
                    break;
            }
        }


        org.eclipse.swt.graphics.Point indexExtent =
                gc.stringExtent(Integer.toString(node.viewNode.index));
        x = node.left+DrawableViewNode.NODE_WIDTH-DrawableViewNode.INDEX_PADDING-indexExtent.x;
        y = node.top+DrawableViewNode.NODE_HEIGHT-DrawableViewNode.INDEX_PADDING-indexExtent.y;
        gc.drawText(Integer.toString(node.viewNode.index), (int) x, (int) y, SWT.DRAW_TRANSPARENT);



        int N = node.children.size();
        if (N == 0) {
            return;
        }
        float childSpacing = (1.0f * (DrawableViewNode.NODE_HEIGHT - 2 * LINE_PADDING)) / N;
        for (int i = 0; i < N; i++) {
            DrawableViewNode child = node.children.get(i);
            paintRecursive(gc, child, connectionPath);
            float x1 = node.left + DrawableViewNode.NODE_WIDTH;
            float y1 = (float) node.top + LINE_PADDING + childSpacing * i + childSpacing / 2;
            float x2 = child.left;
            float y2 = (float) child.top + DrawableViewNode.NODE_HEIGHT / 2.0f;
            float cx1 = x1 + BEZIER_FRACTION * DrawableViewNode.PARENT_CHILD_SPACING;
            float cy1 = y1;
            float cx2 = x2 - BEZIER_FRACTION * DrawableViewNode.PARENT_CHILD_SPACING;
            float cy2 = y2;
            connectionPath.moveTo(x1, y1);
            connectionPath.cubicTo(cx1, cy1, cx2, cy2, x2, y2);
        }
    }

    private void drawTextInArea(GC gc, String text, double x, double y, double width, double height) {
        org.eclipse.swt.graphics.Point extent = gc.stringExtent(text);

        if (extent.x > width) {
            // Oh no... we need to scale it.
            double scale = width / extent.x;
            float[] transformElements = new float[6];
            transform.getElements(transformElements);
            transform.scale((float) scale, (float) scale);
            gc.setTransform(transform);

            x/=scale;
            y/=scale;
            y += (extent.y / scale - extent.y) / 2;

            gc.drawText(text, (int) x, (int) y, SWT.DRAW_TRANSPARENT);

            transform.setElements(transformElements[0], transformElements[1], transformElements[2],
                    transformElements[3], transformElements[4], transformElements[5]);
            gc.setTransform(transform);
        } else {
            gc.drawText(text, (int) x, (int) y, SWT.DRAW_TRANSPARENT);
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
                    if (tree == null) {
                        viewport = null;
                    } else {
                        viewport =
                                new Rectangle((tree.bounds.width - getBounds().width) / 2,
                                        (tree.bounds.height - getBounds().height) / 2,
                                        getBounds().width, getBounds().height);
                    }
                }
            }
        });
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
        Display.getDefault().syncExec(new Runnable() {
            public void run() {
                synchronized (this) {
                    viewport = model.getViewport();
                    zoom = model.getZoom();
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
