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
import com.android.hierarchyviewerlib.HierarchyViewerDirector;
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
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Path;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Transform;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;

import java.text.DecimalFormat;

public class TreeView extends Canvas implements TreeChangeListener {

    private TreeViewModel model;

    private DrawableViewNode tree;

    private DrawableViewNode selectedNode;

    private Rectangle viewport;

    private Transform transform;

    private Transform inverse;

    private double zoom;

    private Point lastPoint;

    private boolean alreadySelectedOnMouseDown;

    private boolean doubleClicked;

    private boolean nodeMoved;

    private DrawableViewNode draggedNode;

    public static final int LINE_PADDING = 10;

    public static final float BEZIER_FRACTION = 0.35f;

    private static Image redImage;

    private static Image yellowImage;

    private static Image greenImage;

    private static Image notSelectedImage;

    private static Image selectedImage;

    private static Image filteredImage;

    private static Image filteredSelectedImage;

    private Color boxColor;

    private Color textBackgroundColor;

    private Rectangle selectedRectangleLocation;

    private Point buttonCenter;

    private static final int BUTTON_SIZE = 13;

    private Image scaledSelectedImage;

    private boolean buttonClicked;

    private DrawableViewNode lastDrawnSelectedViewNode;

    public TreeView(Composite parent) {
        super(parent, SWT.NONE);

        model = TreeViewModel.getModel();
        model.addTreeChangeListener(this);

        addPaintListener(paintListener);
        addMouseListener(mouseListener);
        addMouseMoveListener(mouseMoveListener);
        addMouseWheelListener(mouseWheelListener);
        addListener(SWT.Resize, resizeListener);
        addDisposeListener(disposeListener);
        addKeyListener(keyListener);

        loadResources();

        transform = new Transform(Display.getDefault());
        inverse = new Transform(Display.getDefault());

    }

    private void loadResources() {
        ImageLoader loader = ImageLoader.getLoader(this.getClass());
        redImage = loader.loadImage("red.png", Display.getDefault());
        yellowImage = loader.loadImage("yellow.png", Display.getDefault());
        greenImage = loader.loadImage("green.png", Display.getDefault());
        notSelectedImage = loader.loadImage("not-selected.png", Display.getDefault());
        selectedImage = loader.loadImage("selected.png", Display.getDefault());
        filteredImage = loader.loadImage("filtered.png", Display.getDefault());
        filteredSelectedImage = loader.loadImage("selected-filtered.png", Display.getDefault());
        boxColor = new Color(Display.getDefault(), new RGB(225, 225, 225));
        textBackgroundColor = new Color(Display.getDefault(), new RGB(82, 82, 82));
        if (scaledSelectedImage != null) {
            scaledSelectedImage.dispose();
        }
    }

    private DisposeListener disposeListener = new DisposeListener() {
        public void widgetDisposed(DisposeEvent e) {
            model.removeTreeChangeListener(TreeView.this);
            transform.dispose();
            inverse.dispose();
            boxColor.dispose();
            textBackgroundColor.dispose();
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
                            if (selectedNode.parent != null) {
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
                            for (int i = 0; i < N; i++) {
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
                HierarchyViewerDirector.getDirector().showCapture(getShell(), clickedNode.viewNode);
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
                HierarchyViewerDirector.getDirector().showCapture(getShell(), clickedNode.viewNode);
                doubleClicked = true;
            }
        }

        public void mouseDown(MouseEvent e) {
            boolean selectionChanged = false;
            synchronized (TreeView.this) {
                if (tree != null && viewport != null) {
                    Point pt = transformPoint(e.x, e.y);
                    if (selectedRectangleLocation != null
                            && pt.x >= selectedRectangleLocation.x
                            && pt.x < selectedRectangleLocation.x + selectedRectangleLocation.width
                            && pt.y >= selectedRectangleLocation.y
                            && pt.y < selectedRectangleLocation.y
                                    + selectedRectangleLocation.height) {
                        if ((pt.x - buttonCenter.x) * (pt.x - buttonCenter.x)
                                + (pt.y - buttonCenter.y) * (pt.y - buttonCenter.y) <= (BUTTON_SIZE * BUTTON_SIZE) / 4) {
                            buttonClicked = true;
                            doRedraw();
                        }
                        return;
                    }
                    draggedNode = tree.getSelected(pt.x, pt.y);
                    if (draggedNode != null && draggedNode != selectedNode) {
                        selectedNode = draggedNode;
                        selectionChanged = true;
                        alreadySelectedOnMouseDown = false;
                    } else if (draggedNode != null) {
                        alreadySelectedOnMouseDown = true;
                    }
                    if (draggedNode == tree) {
                        draggedNode = null;
                    }
                    if (draggedNode != null) {
                        lastPoint = pt;
                    } else {
                        lastPoint = new Point(e.x, e.y);
                    }
                    nodeMoved = false;
                    doubleClicked = false;
                }
            }
            if (selectionChanged) {
                model.setSelection(selectedNode);
            }
        }

        public void mouseUp(MouseEvent e) {
            boolean redraw = false;
            boolean redrawButton = false;
            boolean viewportChanged = false;
            boolean selectionChanged = false;
            synchronized (TreeView.this) {
                if (tree != null && viewport != null && lastPoint != null) {
                    if (draggedNode == null) {
                        handleMouseDrag(new Point(e.x, e.y));
                        viewportChanged = true;
                    } else {
                        handleMouseDrag(transformPoint(e.x, e.y));
                    }
                    Point pt = transformPoint(e.x, e.y);
                    DrawableViewNode mouseUpOn = tree.getSelected(pt.x, pt.y);
                    if (mouseUpOn != null && mouseUpOn == selectedNode
                            && alreadySelectedOnMouseDown && !nodeMoved && !doubleClicked) {
                        selectedNode = null;
                        selectionChanged = true;
                    }
                    lastPoint = null;
                    draggedNode = null;
                    redraw = true;
                }
                if (buttonClicked) {
                    HierarchyViewerDirector.getDirector().showCapture(getShell(),
                            selectedNode.viewNode);
                    buttonClicked = false;
                    redrawButton = true;
                }
            }
            if (viewportChanged) {
                model.setViewport(viewport);
            } else if (redraw) {
                model.removeTreeChangeListener(TreeView.this);
                model.notifyViewportChanged();
                if (selectionChanged) {
                    model.setSelection(selectedNode);
                }
                model.addTreeChangeListener(TreeView.this);
                doRedraw();
            } else if (redrawButton) {
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
            if (lastPoint.y - pt.y != 0) {
                nodeMoved = true;
            }
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
                e.gc.setBackground(Display.getDefault().getSystemColor(SWT.COLOR_BLACK));
                e.gc.fillRectangle(0, 0, getBounds().width, getBounds().height);
                if (tree != null && viewport != null) {
                    e.gc.setTransform(transform);
                    e.gc.setForeground(Display.getDefault().getSystemColor(SWT.COLOR_WHITE));
                    Path connectionPath = new Path(Display.getDefault());
                    paintRecursive(e.gc, transform, tree, selectedNode, connectionPath);
                    e.gc.drawPath(connectionPath);
                    connectionPath.dispose();

                    if (selectedNode != null) {
                        int RECT_WIDTH = 155;
                        int RECT_HEIGHT = 224;

                        int x = selectedNode.left + DrawableViewNode.NODE_WIDTH / 2;
                        int y = (int) selectedNode.top + 4;
                        e.gc.setBackground(boxColor);
                        e.gc.fillPolygon(new int[] {
                                x, y, x - 15, y - 15, x + 15, y - 15
                        });
                        y -= 10 + RECT_HEIGHT;
                        e.gc.fillRoundRectangle(x - RECT_WIDTH / 2, y, RECT_WIDTH, RECT_HEIGHT, 15,
                                15);
                        selectedRectangleLocation =
                                new Rectangle(x - RECT_WIDTH / 2, y, RECT_WIDTH, RECT_HEIGHT);
                        int BUTTON_RIGHT_OFFSET = 1;
                        int BUTTON_TOP_OFFSET = 2;

                        buttonCenter =
                                new Point(x - BUTTON_RIGHT_OFFSET + (RECT_WIDTH - BUTTON_SIZE) / 2,
                                        y + BUTTON_TOP_OFFSET + BUTTON_SIZE / 2);

                        if (buttonClicked) {
                            e.gc
                                    .setBackground(Display.getDefault().getSystemColor(
                                            SWT.COLOR_BLACK));
                        } else {
                            e.gc.setBackground(textBackgroundColor);

                        }
                        e.gc.setForeground(Display.getDefault().getSystemColor(SWT.COLOR_WHITE));

                        e.gc.fillOval(x + RECT_WIDTH / 2 - BUTTON_RIGHT_OFFSET - BUTTON_SIZE, y
                                + BUTTON_TOP_OFFSET, BUTTON_SIZE, BUTTON_SIZE);

                        int RECTANGLE_SIZE = 5;

                        e.gc.drawRectangle(x - BUTTON_RIGHT_OFFSET
                                + (RECT_WIDTH - BUTTON_SIZE - RECTANGLE_SIZE) / 2 - 1, y
                                + BUTTON_TOP_OFFSET + (BUTTON_SIZE - RECTANGLE_SIZE) / 2,
                                RECTANGLE_SIZE + 1, RECTANGLE_SIZE);

                        y += 15;
                        int IMAGE_WIDTH = 125;
                        int IMAGE_HEIGHT = 120;

                        int IMAGE_OFFSET = 6;
                        int IMAGE_ROUNDING = 8;

                        if (selectedNode.viewNode.image != null
                                && selectedNode.viewNode.image.getBounds().height != 0
                                && selectedNode.viewNode.image.getBounds().width != 0) {
                            if (lastDrawnSelectedViewNode != selectedNode) {
                                if (scaledSelectedImage != null) {
                                    scaledSelectedImage.dispose();
                                    scaledSelectedImage = null;
                                }
                                lastDrawnSelectedViewNode = selectedNode;
                            }
                            if (scaledSelectedImage == null) {
                                double ratio =
                                        1.0 * selectedNode.viewNode.image.getBounds().width
                                                / selectedNode.viewNode.image.getBounds().height;
                                int newWidth, newHeight;
                                if (ratio > 1.0 * IMAGE_WIDTH / IMAGE_HEIGHT) {
                                    newWidth =
                                            Math.min(IMAGE_WIDTH, selectedNode.viewNode.image
                                                    .getBounds().width);
                                    newHeight = (int) (newWidth / ratio);
                                } else {
                                    newHeight =
                                            Math.min(IMAGE_HEIGHT, selectedNode.viewNode.image
                                                    .getBounds().height);
                                    newWidth = (int) (newHeight * ratio);
                                }
                                newWidth = Math.max(newWidth, 1);
                                newHeight = Math.max(newHeight, 1);
                                scaledSelectedImage =
                                        new Image(Display.getDefault(), newWidth, newHeight);
                                GC gc = new GC(scaledSelectedImage);
                                gc.setBackground(textBackgroundColor);
                                gc.fillRectangle(0, 0, newWidth, newHeight);
                                gc.drawImage(selectedNode.viewNode.image, 0, 0,
                                        selectedNode.viewNode.image.getBounds().width,
                                        selectedNode.viewNode.image.getBounds().height, 0, 0,
                                        newWidth, newHeight);
                                gc.dispose();
                            }
                            e.gc.setBackground(textBackgroundColor);
                            e.gc.fillRoundRectangle(x - scaledSelectedImage.getBounds().width / 2
                                    - IMAGE_OFFSET, y
                                    + (IMAGE_HEIGHT - scaledSelectedImage.getBounds().height) / 2
                                    - IMAGE_OFFSET, scaledSelectedImage.getBounds().width + 2
                                    * IMAGE_OFFSET, scaledSelectedImage.getBounds().height + 2
                                    * IMAGE_OFFSET, IMAGE_ROUNDING, IMAGE_ROUNDING);
                            e.gc.drawImage(scaledSelectedImage, x
                                    - scaledSelectedImage.getBounds().width / 2, y
                                    + (IMAGE_HEIGHT - scaledSelectedImage.getBounds().height) / 2);
                        }

                        y += IMAGE_HEIGHT;
                        y += 10;
                        Font font = getFont(8, false);
                        e.gc.setFont(font);

                        String text =
                                selectedNode.viewNode.viewCount + " view"
                                        + (selectedNode.viewNode.viewCount != 1 ? "s" : "");
                        DecimalFormat formatter = new DecimalFormat("0.000");

                        String measureText =
                                "Measure: "
                                        + (selectedNode.viewNode.measureTime != -1 ? formatter
                                                .format(selectedNode.viewNode.measureTime)
                                                + " ms" : "n/a");
                        String layoutText =
                                "Layout: "
                                        + (selectedNode.viewNode.layoutTime != -1 ? formatter
                                                .format(selectedNode.viewNode.layoutTime)
                                                + " ms" : "n/a");
                        String drawText =
                                "Draw: "
                                        + (selectedNode.viewNode.drawTime != -1 ? formatter
                                                .format(selectedNode.viewNode.drawTime)
                                                + " ms" : "n/a");

                        int TEXT_SIDE_OFFSET = 8;
                        int TEXT_TOP_OFFSET = 4;
                        int TEXT_SPACING = 2;
                        int TEXT_ROUNDING = 20;

                        org.eclipse.swt.graphics.Point titleExtent = e.gc.stringExtent(text);
                        org.eclipse.swt.graphics.Point measureExtent =
                                e.gc.stringExtent(measureText);
                        org.eclipse.swt.graphics.Point layoutExtent = e.gc.stringExtent(layoutText);
                        org.eclipse.swt.graphics.Point drawExtent = e.gc.stringExtent(drawText);
                        int boxWidth =
                                Math.max(titleExtent.x, Math.max(measureExtent.x, Math.max(
                                        layoutExtent.x, drawExtent.x)))
                                        + 2 * TEXT_SIDE_OFFSET;
                        int boxHeight =
                                titleExtent.y + TEXT_SPACING + measureExtent.y + TEXT_SPACING
                                        + layoutExtent.y + TEXT_SPACING + drawExtent.y + 2
                                        * TEXT_TOP_OFFSET;

                        e.gc.setBackground(textBackgroundColor);
                        e.gc.fillRoundRectangle(x - boxWidth / 2, y, boxWidth, boxHeight,
                                TEXT_ROUNDING, TEXT_ROUNDING);

                        e.gc.setForeground(Display.getDefault().getSystemColor(SWT.COLOR_WHITE));

                        y += TEXT_TOP_OFFSET;

                        e.gc.drawText(text, x - titleExtent.x / 2, y, true);

                        x -= boxWidth / 2;
                        x += TEXT_SIDE_OFFSET;

                        y += titleExtent.y + TEXT_SPACING;

                        e.gc.drawText(measureText, x, y, true);

                        y += measureExtent.y + TEXT_SPACING;

                        e.gc.drawText(layoutText, x, y, true);

                        y += layoutExtent.y + TEXT_SPACING;

                        e.gc.drawText(drawText, x, y, true);

                        font.dispose();
                    } else {
                        selectedRectangleLocation = null;
                        buttonCenter = null;
                    }

                    /*
                     * Transform tempTransform = new
                     * Transform(Display.getDefault());
                     * e.gc.setTransform(tempTransform);
                     * e.gc.setBackground(Display
                     * .getDefault().getSystemColor(SWT.COLOR_GRAY));
                     * e.gc.setForeground
                     * (Display.getDefault().getSystemColor(SWT.COLOR_RED)); //
                     * Draw the number of views. String viewsString =
                     * Integer.toString(tree.viewNode.viewCount) + " view"; if
                     * (tree.viewNode.viewCount != 1) { viewsString += 's'; }
                     * org.eclipse.swt.graphics.Point stringExtent =
                     * e.gc.stringExtent(viewsString); e.gc.fillRectangle(0,
                     * getBounds().height - stringExtent.y - 4, stringExtent.x +
                     * 4, stringExtent.y + 4); e.gc.drawText(viewsString, 2,
                     * getBounds().height - stringExtent.y - 2);
                     * DrawableViewNode profiledNode =
                     * (tree.viewNode.protocolVersion < 3) ? tree :
                     * selectedNode; // Draw the profiling stuff if
                     * (profiledNode != null &&
                     * profiledNode.viewNode.measureTime != -1) { DecimalFormat
                     * formatter = new DecimalFormat("0.000"); String
                     * measureString = "Measure:"; String measureTimeString =
                     * formatter.format(profiledNode.viewNode.measureTime) +
                     * " ms"; String layoutString = "Layout:"; String
                     * layoutTimeString =
                     * formatter.format(profiledNode.viewNode.layoutTime) +
                     * " ms"; String drawString = "Draw:"; String drawTimeString
                     * = formatter.format(profiledNode.viewNode.drawTime) +
                     * " ms"; org.eclipse.swt.graphics.Point measureExtent =
                     * e.gc.stringExtent(measureString);
                     * org.eclipse.swt.graphics.Point measureTimeExtent =
                     * e.gc.stringExtent(measureTimeString);
                     * org.eclipse.swt.graphics.Point layoutExtent =
                     * e.gc.stringExtent(layoutString);
                     * org.eclipse.swt.graphics.Point layoutTimeExtent =
                     * e.gc.stringExtent(layoutTimeString);
                     * org.eclipse.swt.graphics.Point drawExtent =
                     * e.gc.stringExtent(drawString);
                     * org.eclipse.swt.graphics.Point drawTimeExtent =
                     * e.gc.stringExtent(drawTimeString); int letterHeight =
                     * e.gc.getFontMetrics().getHeight(); int width =
                     * Math.max(measureExtent.x, Math.max(layoutExtent.x,
                     * drawExtent.x)) + Math.max(measureTimeExtent.x, Math.max(
                     * layoutTimeExtent.x, drawTimeExtent.x)) + 8; int height =
                     * 3 * letterHeight + 8; int x = getBounds().width - width;
                     * int y = getBounds().height - height;
                     * e.gc.fillRectangle(x, y, width, height); x += 2; y += 2;
                     * e.gc.drawText(measureString, x, y); y += letterHeight +
                     * 2; e.gc.drawText(layoutString, x, y); y += letterHeight +
                     * 2; e.gc.drawText(drawString, x, y); x = getBounds().width
                     * - measureTimeExtent.x - 2; y = getBounds().height -
                     * height + 2; e.gc.drawText(measureTimeString, x, y); x =
                     * getBounds().width - layoutTimeExtent.x - 2; y +=
                     * letterHeight + 2; e.gc.drawText(layoutTimeString, x, y);
                     * x = getBounds().width - drawTimeExtent.x - 2; y +=
                     * letterHeight + 2; e.gc.drawText(drawTimeString, x, y); }
                     * tempTransform.dispose();
                     */

                }
            }
        }
    };

    private static void paintRecursive(GC gc, Transform transform, DrawableViewNode node,
            DrawableViewNode selectedNode, Path connectionPath) {
        if (selectedNode == node && node.viewNode.filtered) {
            gc.drawImage(filteredSelectedImage, node.left, (int) Math.round(node.top));
        } else if (selectedNode == node) {
            gc.drawImage(selectedImage, node.left, (int) Math.round(node.top));
        } else if (node.viewNode.filtered) {
            gc.drawImage(filteredImage, node.left, (int) Math.round(node.top));
        } else {
            gc.drawImage(notSelectedImage, node.left, (int) Math.round(node.top));
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
        drawTextInArea(gc, transform, name, x, y, contentWidth, fontHeight, 10, true);

        y += fontHeight + DrawableViewNode.CONTENT_INTER_PADDING;

        drawTextInArea(gc, transform, "@" + node.viewNode.hashCode, x, y, contentWidth, fontHeight,
                8, false);

        y += fontHeight + DrawableViewNode.CONTENT_INTER_PADDING;
        if (!node.viewNode.id.equals("NO_ID")) {
            drawTextInArea(gc, transform, node.viewNode.id, x, y, contentWidth, fontHeight, 8,
                    false);
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
        x =
                node.left + DrawableViewNode.NODE_WIDTH - DrawableViewNode.INDEX_PADDING
                        - indexExtent.x;
        y =
                node.top + DrawableViewNode.NODE_HEIGHT - DrawableViewNode.INDEX_PADDING
                        - indexExtent.y;
        gc.drawText(Integer.toString(node.viewNode.index), (int) x, (int) y, SWT.DRAW_TRANSPARENT);

        int N = node.children.size();
        if (N == 0) {
            return;
        }
        float childSpacing = (1.0f * (DrawableViewNode.NODE_HEIGHT - 2 * LINE_PADDING)) / N;
        for (int i = 0; i < N; i++) {
            DrawableViewNode child = node.children.get(i);
            paintRecursive(gc, transform, child, selectedNode, connectionPath);
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

    private static void drawTextInArea(GC gc, Transform transform, String text, double x, double y,
            double width, double height, int fontSize, boolean bold) {

        Font oldFont = gc.getFont();

        Font newFont = getFont(fontSize, bold);
        gc.setFont(newFont);

        org.eclipse.swt.graphics.Point extent = gc.stringExtent(text);

        if (extent.x > width) {
            // Oh no... we need to scale it.
            double scale = width / extent.x;
            float[] transformElements = new float[6];
            transform.getElements(transformElements);
            transform.scale((float) scale, (float) scale);
            gc.setTransform(transform);

            x /= scale;
            y /= scale;
            y += (extent.y / scale - extent.y) / 2;

            gc.drawText(text, (int) x, (int) y, SWT.DRAW_TRANSPARENT);

            transform.setElements(transformElements[0], transformElements[1], transformElements[2],
                    transformElements[3], transformElements[4], transformElements[5]);
            gc.setTransform(transform);
        } else {
            gc.drawText(text, (int) (x + (width - extent.x) / 2),
                    (int) (y + (height - extent.y) / 2), SWT.DRAW_TRANSPARENT);
        }
        gc.setFont(oldFont);
        newFont.dispose();

    }

    public static Image paintToImage(DrawableViewNode tree) {
        Image image =
                new Image(Display.getDefault(), (int) Math.ceil(tree.bounds.width), (int) Math
                        .ceil(tree.bounds.height));

        Transform transform = new Transform(Display.getDefault());
        transform.identity();
        transform.translate((float) -tree.bounds.x, (float) -tree.bounds.y);
        Path connectionPath = new Path(Display.getDefault());
        GC gc = new GC(image);
        Color white = new Color(Display.getDefault(), 255, 255, 255);
        Color black = new Color(Display.getDefault(), 0, 0, 0);
        gc.setForeground(white);
        gc.setBackground(black);
        gc.fillRectangle(0, 0, image.getBounds().width, image.getBounds().height);
        gc.setTransform(transform);
        paintRecursive(gc, transform, tree, null, connectionPath);
        gc.drawPath(connectionPath);
        gc.dispose();
        connectionPath.dispose();
        white.dispose();
        black.dispose();
        return image;
    }

    private static Font getFont(int size, boolean bold) {
        Font systemFont = Display.getDefault().getSystemFont();
        FontData[] fontData = systemFont.getFontData();
        for (int i = 0; i < fontData.length; i++) {
            fontData[i].setHeight(size);
            if (bold) {
                fontData[i].setStyle(SWT.BOLD);
            }
        }
        return new Font(Display.getDefault(), fontData);
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
                                new Rectangle(0, tree.top + DrawableViewNode.NODE_HEIGHT / 2
                                        - getBounds().height / 2, getBounds().width,
                                        getBounds().height);
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
            if (selectedNode != null && selectedNode.viewNode.image == null) {
                HierarchyViewerDirector.getDirector()
                        .loadCaptureInBackground(selectedNode.viewNode);
            }
        }
        doRedraw();
    }
}
