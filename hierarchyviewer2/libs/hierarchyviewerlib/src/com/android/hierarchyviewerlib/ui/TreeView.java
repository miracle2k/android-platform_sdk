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

import com.android.ddmlib.Log;
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

    private static Font systemFont;

    private Color boxColor;

    private Color textBackgroundColor;

    private Rectangle selectedRectangleLocation;

    private Point buttonCenter;

    private static final int BUTTON_SIZE = 13;

    private Image scaledSelectedImage;

    private boolean buttonClicked;

    private DrawableViewNode lastDrawnSelectedViewNode;

    // The profile-image box needs to be moved to,
    // so add some dragging leeway.
    private static final int DRAG_LEEWAY = 220;

    // Profile-image box constants
    private static final int RECT_WIDTH = 190;

    private static final int RECT_HEIGHT = 224;

    private static final int BUTTON_RIGHT_OFFSET = 5;

    private static final int BUTTON_TOP_OFFSET = 5;

    private static final int IMAGE_WIDTH = 125;

    private static final int IMAGE_HEIGHT = 120;

    private static final int IMAGE_OFFSET = 6;

    private static final int IMAGE_ROUNDING = 8;

    private static final int RECTANGLE_SIZE = 5;

    private static final int TEXT_SIDE_OFFSET = 8;

    private static final int TEXT_TOP_OFFSET = 4;

    private static final int TEXT_SPACING = 2;

    private static final int TEXT_ROUNDING = 20;

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

        loadAllData();
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
        systemFont = Display.getDefault().getSystemFont();
    }

    private DisposeListener disposeListener = new DisposeListener() {
        public void widgetDisposed(DisposeEvent e) {
            model.removeTreeChangeListener(TreeView.this);
            transform.dispose();
            inverse.dispose();
            boxColor.dispose();
            textBackgroundColor.dispose();
            if (tree != null) {
                model.setViewport(null);
            }
        }
    };

    private Listener resizeListener = new Listener() {
        public void handleEvent(Event e) {
            synchronized (TreeView.this) {
                if (tree != null && viewport != null) {

                    // Keep the center in the same place.
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

                            // On up and down, it is cool to go up and down only
                            // the leaf nodes.
                            // It goes well with the layout viewer
                            DrawableViewNode currentNode = selectedNode;
                            while (currentNode.parent != null && currentNode.viewNode.index == 0) {
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
                                }
                            }
                            if (selectionChanged) {
                                selectedNode = currentNode;
                            }
                            break;
                        case SWT.ARROW_DOWN:
                            currentNode = selectedNode;
                            while (currentNode.parent != null
                                    && currentNode.viewNode.index + 1 == currentNode.parent.children
                                            .size()) {
                                currentNode = currentNode.parent;
                            }
                            if (currentNode.parent != null) {
                                selectionChanged = true;
                                currentNode =
                                        currentNode.parent.children
                                                .get(currentNode.viewNode.index + 1);
                                while (currentNode.children.size() != 0) {
                                    currentNode = currentNode.children.get(0);
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

                            // We consider all the children and pick the one
                            // who's tree overlaps the most.
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

                    // Ignore profiling rectangle, except for...
                    if (selectedRectangleLocation != null
                            && pt.x >= selectedRectangleLocation.x
                            && pt.x < selectedRectangleLocation.x + selectedRectangleLocation.width
                            && pt.y >= selectedRectangleLocation.y
                            && pt.y < selectedRectangleLocation.y
                                    + selectedRectangleLocation.height) {

                        // the small button!
                        if ((pt.x - buttonCenter.x) * (pt.x - buttonCenter.x)
                                + (pt.y - buttonCenter.y) * (pt.y - buttonCenter.y) <= (BUTTON_SIZE * BUTTON_SIZE) / 4) {
                            buttonClicked = true;
                            doRedraw();
                        }
                        return;
                    }
                    draggedNode = tree.getSelected(pt.x, pt.y);

                    // Update the selection.
                    if (draggedNode != null && draggedNode != selectedNode) {
                        selectedNode = draggedNode;
                        selectionChanged = true;
                        alreadySelectedOnMouseDown = false;
                    } else if (draggedNode != null) {
                        alreadySelectedOnMouseDown = true;
                    }

                    // Can't drag the root.
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
                        // The viewport moves.
                        handleMouseDrag(new Point(e.x, e.y));
                        viewportChanged = true;
                    } else {
                        // The nodes move.
                        handleMouseDrag(transformPoint(e.x, e.y));
                    }

                    // Deselect on the second click...
                    // This is in the mouse up, because mouse up happens after a
                    // double click event.
                    // During a double click, we don't want to deselect.
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

                // Just clicked the button here.
                if (buttonClicked) {
                    HierarchyViewerDirector.getDirector().showCapture(getShell(),
                            selectedNode.viewNode);
                    buttonClicked = false;
                    redrawButton = true;
                }
            }

            // Complicated.
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

        // Case 1: a node is dragged. DrawableViewNode knows how to handle this.
        if (draggedNode != null) {
            if (lastPoint.y - pt.y != 0) {
                nodeMoved = true;
            }
            draggedNode.move(lastPoint.y - pt.y);
            lastPoint = pt;
            return;
        }

        // Case 2: the viewport is dragged. We have to make sure we respect the
        // bounds - don't let the user drag way out... + some leeway for the
        // profiling box.
        double xDif = (lastPoint.x - pt.x) / zoom;
        double yDif = (lastPoint.y - pt.y) / zoom;

        double treeX = tree.bounds.x - DRAG_LEEWAY;
        double treeY = tree.bounds.y - DRAG_LEEWAY;
        double treeWidth = tree.bounds.width + 2 * DRAG_LEEWAY;
        double treeHeight = tree.bounds.height + 2 * DRAG_LEEWAY;

        if (viewport.width > treeWidth) {
            if (xDif < 0 && viewport.x + viewport.width > treeX + treeWidth) {
                viewport.x = Math.max(viewport.x + xDif, treeX + treeWidth - viewport.width);
            } else if (xDif > 0 && viewport.x < treeX) {
                viewport.x = Math.min(viewport.x + xDif, treeX);
            }
        } else {
            if (xDif < 0 && viewport.x > treeX) {
                viewport.x = Math.max(viewport.x + xDif, treeX);
            } else if (xDif > 0 && viewport.x + viewport.width < treeX + treeWidth) {
                viewport.x = Math.min(viewport.x + xDif, treeX + treeWidth - viewport.width);
            }
        }
        if (viewport.height > treeHeight) {
            if (yDif < 0 && viewport.y + viewport.height > treeY + treeHeight) {
                viewport.y = Math.max(viewport.y + yDif, treeY + treeHeight - viewport.height);
            } else if (yDif > 0 && viewport.y < treeY) {
                viewport.y = Math.min(viewport.y + yDif, treeY);
            }
        } else {
            if (yDif < 0 && viewport.y > treeY) {
                viewport.y = Math.max(viewport.y + yDif, treeY);
            } else if (yDif > 0 && viewport.y + viewport.height < treeY + treeHeight) {
                viewport.y = Math.min(viewport.y + yDif, treeY + treeHeight - viewport.height);
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

                    // Easy stuff!
                    e.gc.setTransform(transform);
                    e.gc.setForeground(Display.getDefault().getSystemColor(SWT.COLOR_WHITE));
                    Path connectionPath = new Path(Display.getDefault());
                    paintRecursive(e.gc, transform, tree, selectedNode, connectionPath);
                    e.gc.drawPath(connectionPath);
                    connectionPath.dispose();

                    // Draw the profiling box.
                    if (selectedNode != null) {

                        e.gc.setAlpha(200);

                        // Draw the little triangle
                        int x = selectedNode.left + DrawableViewNode.NODE_WIDTH / 2;
                        int y = (int) selectedNode.top + 4;
                        e.gc.setBackground(boxColor);
                        e.gc.fillPolygon(new int[] {
                                x, y, x - 11, y - 11, x + 11, y - 11
                        });

                        // Draw the rectangle and update the location.
                        y -= 10 + RECT_HEIGHT;
                        e.gc.fillRoundRectangle(x - RECT_WIDTH / 2, y, RECT_WIDTH, RECT_HEIGHT, 30,
                                30);
                        selectedRectangleLocation =
                                new Rectangle(x - RECT_WIDTH / 2, y, RECT_WIDTH, RECT_HEIGHT);

                        e.gc.setAlpha(255);

                        // Draw the button
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

                        e.gc.drawRectangle(x - BUTTON_RIGHT_OFFSET
                                + (RECT_WIDTH - BUTTON_SIZE - RECTANGLE_SIZE) / 2 - 1, y
                                + BUTTON_TOP_OFFSET + (BUTTON_SIZE - RECTANGLE_SIZE) / 2,
                                RECTANGLE_SIZE + 1, RECTANGLE_SIZE);

                        y += 15;

                        // If there is an image, draw it.
                        if (selectedNode.viewNode.image != null
                                && selectedNode.viewNode.image.getBounds().height != 1
                                && selectedNode.viewNode.image.getBounds().width != 1) {

                            // Scaling the image to the right size takes lots of
                            // time, so we want to do it only once.

                            // If the selection changed, get rid of the old
                            // image.
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

                                // Interesting note... We make the image twice
                                // the needed size so that there is better
                                // resolution under zoom.
                                newWidth = Math.max(newWidth * 2, 1);
                                newHeight = Math.max(newHeight * 2, 1);
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

                            // Draw the background rectangle
                            e.gc.setBackground(textBackgroundColor);
                            e.gc.fillRoundRectangle(x - scaledSelectedImage.getBounds().width / 4
                                    - IMAGE_OFFSET, y
                                    + (IMAGE_HEIGHT - scaledSelectedImage.getBounds().height / 2)
                                    / 2 - IMAGE_OFFSET, scaledSelectedImage.getBounds().width / 2
                                    + 2 * IMAGE_OFFSET, scaledSelectedImage.getBounds().height / 2
                                    + 2 * IMAGE_OFFSET, IMAGE_ROUNDING, IMAGE_ROUNDING);

                            // Under max zoom, we want the image to be
                            // untransformed. So, get back to the identity
                            // transform.
                            int imageX = x - scaledSelectedImage.getBounds().width / 4;
                            int imageY =
                                    y + (IMAGE_HEIGHT - scaledSelectedImage.getBounds().height / 2)
                                            / 2;

                            Transform untransformedTransform = new Transform(Display.getDefault());
                            e.gc.setTransform(untransformedTransform);
                            float[] pt = new float[] {
                                    imageX, imageY
                            };
                            transform.transform(pt);
                            e.gc.drawImage(scaledSelectedImage, 0, 0, scaledSelectedImage
                                    .getBounds().width, scaledSelectedImage.getBounds().height,
                                    (int) pt[0], (int) pt[1], (int) (scaledSelectedImage
                                            .getBounds().width
                                            * zoom / 2),
                                    (int) (scaledSelectedImage.getBounds().height * zoom / 2));
                            untransformedTransform.dispose();
                            e.gc.setTransform(transform);
                        }

                        // Text stuff

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

        // Can't use Display.getDefault().getSystemColor in a non-UI thread.
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
        Display.getDefault().syncExec(new Runnable() {
            public void run() {
                redraw();
            }
        });
    }
    
    public void loadAllData() {
        boolean newViewport = viewport == null;
        Display.getDefault().syncExec(new Runnable() {
            public void run() {
                synchronized (this) {
                    tree = model.getTree();
                    selectedNode = model.getSelection();
                    viewport = model.getViewport();
                    zoom = model.getZoom();
                    if (tree != null && viewport == null) {
                        viewport =
                                new Rectangle(0, tree.top + DrawableViewNode.NODE_HEIGHT / 2
                                        - getBounds().height / 2, getBounds().width,
                                        getBounds().height);
                    } else {
                        setTransform();
                    }
                }
            }
        });
        if (newViewport) {
            model.setViewport(viewport);
        }
    }

    // Fickle behaviour... When a new tree is loaded, the model doesn't know
    // about the viewport until it passes through here.
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
        } else {
            doRedraw();
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

    // Note the syncExec and then synchronized... It avoids deadlock
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
