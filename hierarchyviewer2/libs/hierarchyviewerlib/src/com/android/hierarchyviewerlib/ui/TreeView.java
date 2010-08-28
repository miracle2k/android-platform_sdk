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
import com.android.hierarchyviewerlib.models.TreeViewModel.ITreeChangeListener;
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

public class TreeView extends Canvas implements ITreeChangeListener {

    private TreeViewModel mModel;

    private DrawableViewNode mTree;

    private DrawableViewNode mSelectedNode;

    private Rectangle mViewport;

    private Transform mTransform;

    private Transform mInverse;

    private double mZoom;

    private Point mLastPoint;

    private boolean mAlreadySelectedOnMouseDown;

    private boolean mDoubleClicked;

    private boolean mNodeMoved;

    private DrawableViewNode mDraggedNode;

    public static final int LINE_PADDING = 10;

    public static final float BEZIER_FRACTION = 0.35f;

    private static Image sRedImage;

    private static Image sYellowImage;

    private static Image sGreenImage;

    private static Image sNotSelectedImage;

    private static Image sSelectedImage;

    private static Image sFilteredImage;

    private static Image sFilteredSelectedImage;

    private static Font sSystemFont;

    private Color mBoxColor;

    private Color mTextBackgroundColor;

    private Rectangle mSelectedRectangleLocation;

    private Point mButtonCenter;

    private static final int BUTTON_SIZE = 13;

    private Image mScaledSelectedImage;

    private boolean mButtonClicked;

    private DrawableViewNode mLastDrawnSelectedViewNode;

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

        mModel = TreeViewModel.getModel();
        mModel.addTreeChangeListener(this);

        addPaintListener(mPaintListener);
        addMouseListener(mMouseListener);
        addMouseMoveListener(mMouseMoveListener);
        addMouseWheelListener(mMouseWheelListener);
        addListener(SWT.Resize, mResizeListener);
        addDisposeListener(mDisposeListener);
        addKeyListener(mKeyListener);

        loadResources();

        mTransform = new Transform(Display.getDefault());
        mInverse = new Transform(Display.getDefault());

        loadAllData();
    }

    private void loadResources() {
        ImageLoader loader = ImageLoader.getLoader(this.getClass());
        sRedImage = loader.loadImage("red.png", Display.getDefault()); //$NON-NLS-1$
        sYellowImage = loader.loadImage("yellow.png", Display.getDefault()); //$NON-NLS-1$
        sGreenImage = loader.loadImage("green.png", Display.getDefault()); //$NON-NLS-1$
        sNotSelectedImage = loader.loadImage("not-selected.png", Display.getDefault()); //$NON-NLS-1$
        sSelectedImage = loader.loadImage("selected.png", Display.getDefault()); //$NON-NLS-1$
        sFilteredImage = loader.loadImage("filtered.png", Display.getDefault()); //$NON-NLS-1$
        sFilteredSelectedImage = loader.loadImage("selected-filtered.png", Display.getDefault()); //$NON-NLS-1$
        mBoxColor = new Color(Display.getDefault(), new RGB(225, 225, 225));
        mTextBackgroundColor = new Color(Display.getDefault(), new RGB(82, 82, 82));
        if (mScaledSelectedImage != null) {
            mScaledSelectedImage.dispose();
        }
        sSystemFont = Display.getDefault().getSystemFont();
    }

    private DisposeListener mDisposeListener = new DisposeListener() {
        public void widgetDisposed(DisposeEvent e) {
            mModel.removeTreeChangeListener(TreeView.this);
            mTransform.dispose();
            mInverse.dispose();
            mBoxColor.dispose();
            mTextBackgroundColor.dispose();
            if (mTree != null) {
                mModel.setViewport(null);
            }
        }
    };

    private Listener mResizeListener = new Listener() {
        public void handleEvent(Event e) {
            synchronized (TreeView.this) {
                if (mTree != null && mViewport != null) {

                    // Keep the center in the same place.
                    Point viewCenter =
                            new Point(mViewport.x + mViewport.width / 2, mViewport.y + mViewport.height
                                    / 2);
                    mViewport.width = getBounds().width / mZoom;
                    mViewport.height = getBounds().height / mZoom;
                    mViewport.x = viewCenter.x - mViewport.width / 2;
                    mViewport.y = viewCenter.y - mViewport.height / 2;
                }
            }
            if (mViewport != null) {
                mModel.setViewport(mViewport);
            }
        }
    };

    private KeyListener mKeyListener = new KeyListener() {

        public void keyPressed(KeyEvent e) {
            boolean selectionChanged = false;
            DrawableViewNode clickedNode = null;
            synchronized (TreeView.this) {
                if (mTree != null && mViewport != null && mSelectedNode != null) {
                    switch (e.keyCode) {
                        case SWT.ARROW_LEFT:
                            if (mSelectedNode.parent != null) {
                                mSelectedNode = mSelectedNode.parent;
                                selectionChanged = true;
                            }
                            break;
                        case SWT.ARROW_UP:

                            // On up and down, it is cool to go up and down only
                            // the leaf nodes.
                            // It goes well with the layout viewer
                            DrawableViewNode currentNode = mSelectedNode;
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
                                mSelectedNode = currentNode;
                            }
                            break;
                        case SWT.ARROW_DOWN:
                            currentNode = mSelectedNode;
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
                                mSelectedNode = currentNode;
                            }
                            break;
                        case SWT.ARROW_RIGHT:
                            DrawableViewNode rightNode = null;
                            double mostOverlap = 0;
                            final int N = mSelectedNode.children.size();

                            // We consider all the children and pick the one
                            // who's tree overlaps the most.
                            for (int i = 0; i < N; i++) {
                                DrawableViewNode child = mSelectedNode.children.get(i);
                                DrawableViewNode topMostChild = child;
                                while (topMostChild.children.size() != 0) {
                                    topMostChild = topMostChild.children.get(0);
                                }
                                double overlap =
                                        Math.min(DrawableViewNode.NODE_HEIGHT, Math.min(
                                                mSelectedNode.top + DrawableViewNode.NODE_HEIGHT
                                                        - topMostChild.top, topMostChild.top
                                                        + child.treeHeight - mSelectedNode.top));
                                if (overlap > mostOverlap) {
                                    mostOverlap = overlap;
                                    rightNode = child;
                                }
                            }
                            if (rightNode != null) {
                                mSelectedNode = rightNode;
                                selectionChanged = true;
                            }
                            break;
                        case SWT.CR:
                            clickedNode = mSelectedNode;
                            break;
                    }
                }
            }
            if (selectionChanged) {
                mModel.setSelection(mSelectedNode);
            }
            if (clickedNode != null) {
                HierarchyViewerDirector.getDirector().showCapture(getShell(), clickedNode.viewNode);
            }
        }

        public void keyReleased(KeyEvent e) {
        }
    };

    private MouseListener mMouseListener = new MouseListener() {

        public void mouseDoubleClick(MouseEvent e) {
            DrawableViewNode clickedNode = null;
            synchronized (TreeView.this) {
                if (mTree != null && mViewport != null) {
                    Point pt = transformPoint(e.x, e.y);
                    clickedNode = mTree.getSelected(pt.x, pt.y);
                }
            }
            if (clickedNode != null) {
                HierarchyViewerDirector.getDirector().showCapture(getShell(), clickedNode.viewNode);
                mDoubleClicked = true;
            }
        }

        public void mouseDown(MouseEvent e) {
            boolean selectionChanged = false;
            synchronized (TreeView.this) {
                if (mTree != null && mViewport != null) {
                    Point pt = transformPoint(e.x, e.y);

                    // Ignore profiling rectangle, except for...
                    if (mSelectedRectangleLocation != null
                            && pt.x >= mSelectedRectangleLocation.x
                            && pt.x < mSelectedRectangleLocation.x
                                    + mSelectedRectangleLocation.width
                            && pt.y >= mSelectedRectangleLocation.y
                            && pt.y < mSelectedRectangleLocation.y
                                    + mSelectedRectangleLocation.height) {

                        // the small button!
                        if ((pt.x - mButtonCenter.x) * (pt.x - mButtonCenter.x)
                                + (pt.y - mButtonCenter.y) * (pt.y - mButtonCenter.y) <= (BUTTON_SIZE * BUTTON_SIZE) / 4) {
                            mButtonClicked = true;
                            doRedraw();
                        }
                        return;
                    }
                    mDraggedNode = mTree.getSelected(pt.x, pt.y);

                    // Update the selection.
                    if (mDraggedNode != null && mDraggedNode != mSelectedNode) {
                        mSelectedNode = mDraggedNode;
                        selectionChanged = true;
                        mAlreadySelectedOnMouseDown = false;
                    } else if (mDraggedNode != null) {
                        mAlreadySelectedOnMouseDown = true;
                    }

                    // Can't drag the root.
                    if (mDraggedNode == mTree) {
                        mDraggedNode = null;
                    }

                    if (mDraggedNode != null) {
                        mLastPoint = pt;
                    } else {
                        mLastPoint = new Point(e.x, e.y);
                    }
                    mNodeMoved = false;
                    mDoubleClicked = false;
                }
            }
            if (selectionChanged) {
                mModel.setSelection(mSelectedNode);
            }
        }

        public void mouseUp(MouseEvent e) {
            boolean redraw = false;
            boolean redrawButton = false;
            boolean viewportChanged = false;
            boolean selectionChanged = false;
            synchronized (TreeView.this) {
                if (mTree != null && mViewport != null && mLastPoint != null) {
                    if (mDraggedNode == null) {
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
                    DrawableViewNode mouseUpOn = mTree.getSelected(pt.x, pt.y);
                    if (mouseUpOn != null && mouseUpOn == mSelectedNode
                            && mAlreadySelectedOnMouseDown && !mNodeMoved && !mDoubleClicked) {
                        mSelectedNode = null;
                        selectionChanged = true;
                    }
                    mLastPoint = null;
                    mDraggedNode = null;
                    redraw = true;
                }

                // Just clicked the button here.
                if (mButtonClicked) {
                    HierarchyViewerDirector.getDirector().showCapture(getShell(),
                            mSelectedNode.viewNode);
                    mButtonClicked = false;
                    redrawButton = true;
                }
            }

            // Complicated.
            if (viewportChanged) {
                mModel.setViewport(mViewport);
            } else if (redraw) {
                mModel.removeTreeChangeListener(TreeView.this);
                mModel.notifyViewportChanged();
                if (selectionChanged) {
                    mModel.setSelection(mSelectedNode);
                }
                mModel.addTreeChangeListener(TreeView.this);
                doRedraw();
            } else if (redrawButton) {
                doRedraw();
            }
        }

    };

    private MouseMoveListener mMouseMoveListener = new MouseMoveListener() {
        public void mouseMove(MouseEvent e) {
            boolean redraw = false;
            boolean viewportChanged = false;
            synchronized (TreeView.this) {
                if (mTree != null && mViewport != null && mLastPoint != null) {
                    if (mDraggedNode == null) {
                        handleMouseDrag(new Point(e.x, e.y));
                        viewportChanged = true;
                    } else {
                        handleMouseDrag(transformPoint(e.x, e.y));
                    }
                    redraw = true;
                }
            }
            if (viewportChanged) {
                mModel.setViewport(mViewport);
            } else if (redraw) {
                mModel.removeTreeChangeListener(TreeView.this);
                mModel.notifyViewportChanged();
                mModel.addTreeChangeListener(TreeView.this);
                doRedraw();
            }
        }
    };

    private void handleMouseDrag(Point pt) {

        // Case 1: a node is dragged. DrawableViewNode knows how to handle this.
        if (mDraggedNode != null) {
            if (mLastPoint.y - pt.y != 0) {
                mNodeMoved = true;
            }
            mDraggedNode.move(mLastPoint.y - pt.y);
            mLastPoint = pt;
            return;
        }

        // Case 2: the viewport is dragged. We have to make sure we respect the
        // bounds - don't let the user drag way out... + some leeway for the
        // profiling box.
        double xDif = (mLastPoint.x - pt.x) / mZoom;
        double yDif = (mLastPoint.y - pt.y) / mZoom;

        double treeX = mTree.bounds.x - DRAG_LEEWAY;
        double treeY = mTree.bounds.y - DRAG_LEEWAY;
        double treeWidth = mTree.bounds.width + 2 * DRAG_LEEWAY;
        double treeHeight = mTree.bounds.height + 2 * DRAG_LEEWAY;

        if (mViewport.width > treeWidth) {
            if (xDif < 0 && mViewport.x + mViewport.width > treeX + treeWidth) {
                mViewport.x = Math.max(mViewport.x + xDif, treeX + treeWidth - mViewport.width);
            } else if (xDif > 0 && mViewport.x < treeX) {
                mViewport.x = Math.min(mViewport.x + xDif, treeX);
            }
        } else {
            if (xDif < 0 && mViewport.x > treeX) {
                mViewport.x = Math.max(mViewport.x + xDif, treeX);
            } else if (xDif > 0 && mViewport.x + mViewport.width < treeX + treeWidth) {
                mViewport.x = Math.min(mViewport.x + xDif, treeX + treeWidth - mViewport.width);
            }
        }
        if (mViewport.height > treeHeight) {
            if (yDif < 0 && mViewport.y + mViewport.height > treeY + treeHeight) {
                mViewport.y = Math.max(mViewport.y + yDif, treeY + treeHeight - mViewport.height);
            } else if (yDif > 0 && mViewport.y < treeY) {
                mViewport.y = Math.min(mViewport.y + yDif, treeY);
            }
        } else {
            if (yDif < 0 && mViewport.y > treeY) {
                mViewport.y = Math.max(mViewport.y + yDif, treeY);
            } else if (yDif > 0 && mViewport.y + mViewport.height < treeY + treeHeight) {
                mViewport.y = Math.min(mViewport.y + yDif, treeY + treeHeight - mViewport.height);
            }
        }
        mLastPoint = pt;
    }

    private Point transformPoint(double x, double y) {
        float[] pt = {
                (float) x, (float) y
        };
        mInverse.transform(pt);
        return new Point(pt[0], pt[1]);
    }

    private MouseWheelListener mMouseWheelListener = new MouseWheelListener() {
        public void mouseScrolled(MouseEvent e) {
            Point zoomPoint = null;
            synchronized (TreeView.this) {
                if (mTree != null && mViewport != null) {
                    mZoom += Math.ceil(e.count / 3.0) * 0.1;
                    zoomPoint = transformPoint(e.x, e.y);
                }
            }
            if (zoomPoint != null) {
                mModel.zoomOnPoint(mZoom, zoomPoint);
            }
        }
    };

    private PaintListener mPaintListener = new PaintListener() {
        public void paintControl(PaintEvent e) {
            synchronized (TreeView.this) {
                e.gc.setBackground(Display.getDefault().getSystemColor(SWT.COLOR_BLACK));
                e.gc.fillRectangle(0, 0, getBounds().width, getBounds().height);
                if (mTree != null && mViewport != null) {

                    // Easy stuff!
                    e.gc.setTransform(mTransform);
                    e.gc.setForeground(Display.getDefault().getSystemColor(SWT.COLOR_WHITE));
                    Path connectionPath = new Path(Display.getDefault());
                    paintRecursive(e.gc, mTransform, mTree, mSelectedNode, connectionPath);
                    e.gc.drawPath(connectionPath);
                    connectionPath.dispose();

                    // Draw the profiling box.
                    if (mSelectedNode != null) {

                        e.gc.setAlpha(200);

                        // Draw the little triangle
                        int x = mSelectedNode.left + DrawableViewNode.NODE_WIDTH / 2;
                        int y = (int) mSelectedNode.top + 4;
                        e.gc.setBackground(mBoxColor);
                        e.gc.fillPolygon(new int[] {
                                x, y, x - 11, y - 11, x + 11, y - 11
                        });

                        // Draw the rectangle and update the location.
                        y -= 10 + RECT_HEIGHT;
                        e.gc.fillRoundRectangle(x - RECT_WIDTH / 2, y, RECT_WIDTH, RECT_HEIGHT, 30,
                                30);
                        mSelectedRectangleLocation =
                                new Rectangle(x - RECT_WIDTH / 2, y, RECT_WIDTH, RECT_HEIGHT);

                        e.gc.setAlpha(255);

                        // Draw the button
                        mButtonCenter =
                                new Point(x - BUTTON_RIGHT_OFFSET + (RECT_WIDTH - BUTTON_SIZE) / 2,
                                        y + BUTTON_TOP_OFFSET + BUTTON_SIZE / 2);

                        if (mButtonClicked) {
                            e.gc
                                    .setBackground(Display.getDefault().getSystemColor(
                                            SWT.COLOR_BLACK));
                        } else {
                            e.gc.setBackground(mTextBackgroundColor);

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
                        if (mSelectedNode.viewNode.image != null
                                && mSelectedNode.viewNode.image.getBounds().height != 1
                                && mSelectedNode.viewNode.image.getBounds().width != 1) {

                            // Scaling the image to the right size takes lots of
                            // time, so we want to do it only once.

                            // If the selection changed, get rid of the old
                            // image.
                            if (mLastDrawnSelectedViewNode != mSelectedNode) {
                                if (mScaledSelectedImage != null) {
                                    mScaledSelectedImage.dispose();
                                    mScaledSelectedImage = null;
                                }
                                mLastDrawnSelectedViewNode = mSelectedNode;
                            }

                            if (mScaledSelectedImage == null) {
                                double ratio =
                                        1.0 * mSelectedNode.viewNode.image.getBounds().width
                                                / mSelectedNode.viewNode.image.getBounds().height;
                                int newWidth, newHeight;
                                if (ratio > 1.0 * IMAGE_WIDTH / IMAGE_HEIGHT) {
                                    newWidth =
                                            Math.min(IMAGE_WIDTH, mSelectedNode.viewNode.image
                                                    .getBounds().width);
                                    newHeight = (int) (newWidth / ratio);
                                } else {
                                    newHeight =
                                            Math.min(IMAGE_HEIGHT, mSelectedNode.viewNode.image
                                                    .getBounds().height);
                                    newWidth = (int) (newHeight * ratio);
                                }

                                // Interesting note... We make the image twice
                                // the needed size so that there is better
                                // resolution under zoom.
                                newWidth = Math.max(newWidth * 2, 1);
                                newHeight = Math.max(newHeight * 2, 1);
                                mScaledSelectedImage =
                                        new Image(Display.getDefault(), newWidth, newHeight);
                                GC gc = new GC(mScaledSelectedImage);
                                gc.setBackground(mTextBackgroundColor);
                                gc.fillRectangle(0, 0, newWidth, newHeight);
                                gc.drawImage(mSelectedNode.viewNode.image, 0, 0,
                                        mSelectedNode.viewNode.image.getBounds().width,
                                        mSelectedNode.viewNode.image.getBounds().height, 0, 0,
                                        newWidth, newHeight);
                                gc.dispose();
                            }

                            // Draw the background rectangle
                            e.gc.setBackground(mTextBackgroundColor);
                            e.gc.fillRoundRectangle(x - mScaledSelectedImage.getBounds().width / 4
                                    - IMAGE_OFFSET, y
                                    + (IMAGE_HEIGHT - mScaledSelectedImage.getBounds().height / 2)
                                    / 2 - IMAGE_OFFSET, mScaledSelectedImage.getBounds().width / 2
                                    + 2 * IMAGE_OFFSET, mScaledSelectedImage.getBounds().height / 2
                                    + 2 * IMAGE_OFFSET, IMAGE_ROUNDING, IMAGE_ROUNDING);

                            // Under max zoom, we want the image to be
                            // untransformed. So, get back to the identity
                            // transform.
                            int imageX = x - mScaledSelectedImage.getBounds().width / 4;
                            int imageY =
                                    y
                                            + (IMAGE_HEIGHT - mScaledSelectedImage.getBounds().height / 2)
                                            / 2;

                            Transform untransformedTransform = new Transform(Display.getDefault());
                            e.gc.setTransform(untransformedTransform);
                            float[] pt = new float[] {
                                    imageX, imageY
                            };
                            mTransform.transform(pt);
                            e.gc.drawImage(mScaledSelectedImage, 0, 0, mScaledSelectedImage
                                    .getBounds().width, mScaledSelectedImage.getBounds().height,
                                    (int) pt[0], (int) pt[1], (int) (mScaledSelectedImage
                                            .getBounds().width
                                            * mZoom / 2),
                                    (int) (mScaledSelectedImage.getBounds().height * mZoom / 2));
                            untransformedTransform.dispose();
                            e.gc.setTransform(mTransform);
                        }

                        // Text stuff

                        y += IMAGE_HEIGHT;
                        y += 10;
                        Font font = getFont(8, false);
                        e.gc.setFont(font);

                        String text =
                                mSelectedNode.viewNode.viewCount + " view"
                                        + (mSelectedNode.viewNode.viewCount != 1 ? "s" : "");
                        DecimalFormat formatter = new DecimalFormat("0.000");

                        String measureText =
                                "Measure: "
                                        + (mSelectedNode.viewNode.measureTime != -1 ? formatter
                                                .format(mSelectedNode.viewNode.measureTime)
                                                + " ms" : "n/a");
                        String layoutText =
                                "Layout: "
                                        + (mSelectedNode.viewNode.layoutTime != -1 ? formatter
                                                .format(mSelectedNode.viewNode.layoutTime)
                                                + " ms" : "n/a");
                        String drawText =
                                "Draw: "
                                        + (mSelectedNode.viewNode.drawTime != -1 ? formatter
                                                .format(mSelectedNode.viewNode.drawTime)
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

                        e.gc.setBackground(mTextBackgroundColor);
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
                        mSelectedRectangleLocation = null;
                        mButtonCenter = null;
                    }
                }
            }
        }
    };

    private static void paintRecursive(GC gc, Transform transform, DrawableViewNode node,
            DrawableViewNode selectedNode, Path connectionPath) {
        if (selectedNode == node && node.viewNode.filtered) {
            gc.drawImage(sFilteredSelectedImage, node.left, (int) Math.round(node.top));
        } else if (selectedNode == node) {
            gc.drawImage(sSelectedImage, node.left, (int) Math.round(node.top));
        } else if (node.viewNode.filtered) {
            gc.drawImage(sFilteredImage, node.left, (int) Math.round(node.top));
        } else {
            gc.drawImage(sNotSelectedImage, node.left, (int) Math.round(node.top));
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
                            - sRedImage.getBounds().height;
            x +=
                    (contentWidth - (sRedImage.getBounds().width * 3 + 2 * DrawableViewNode.CONTENT_INTER_PADDING)) / 2;
            switch (node.viewNode.measureRating) {
                case GREEN:
                    gc.drawImage(sGreenImage, (int) x, (int) y);
                    break;
                case YELLOW:
                    gc.drawImage(sYellowImage, (int) x, (int) y);
                    break;
                case RED:
                    gc.drawImage(sRedImage, (int) x, (int) y);
                    break;
            }

            x += sRedImage.getBounds().width + DrawableViewNode.CONTENT_INTER_PADDING;
            switch (node.viewNode.layoutRating) {
                case GREEN:
                    gc.drawImage(sGreenImage, (int) x, (int) y);
                    break;
                case YELLOW:
                    gc.drawImage(sYellowImage, (int) x, (int) y);
                    break;
                case RED:
                    gc.drawImage(sRedImage, (int) x, (int) y);
                    break;
            }

            x += sRedImage.getBounds().width + DrawableViewNode.CONTENT_INTER_PADDING;
            switch (node.viewNode.drawRating) {
                case GREEN:
                    gc.drawImage(sGreenImage, (int) x, (int) y);
                    break;
                case YELLOW:
                    gc.drawImage(sYellowImage, (int) x, (int) y);
                    break;
                case RED:
                    gc.drawImage(sRedImage, (int) x, (int) y);
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
        FontData[] fontData = sSystemFont.getFontData();
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
        boolean newViewport = mViewport == null;
        Display.getDefault().syncExec(new Runnable() {
            public void run() {
                synchronized (this) {
                    mTree = mModel.getTree();
                    mSelectedNode = mModel.getSelection();
                    mViewport = mModel.getViewport();
                    mZoom = mModel.getZoom();
                    if (mTree != null && mViewport == null) {
                        mViewport =
                                new Rectangle(0, mTree.top + DrawableViewNode.NODE_HEIGHT / 2
                                        - getBounds().height / 2, getBounds().width,
                                        getBounds().height);
                    } else {
                        setTransform();
                    }
                }
            }
        });
        if (newViewport) {
            mModel.setViewport(mViewport);
        }
    }

    // Fickle behaviour... When a new tree is loaded, the model doesn't know
    // about the viewport until it passes through here.
    public void treeChanged() {
        Display.getDefault().syncExec(new Runnable() {
            public void run() {
                synchronized (this) {
                    mTree = mModel.getTree();
                    mSelectedNode = mModel.getSelection();
                    if (mTree == null) {
                        mViewport = null;
                    } else {
                        mViewport =
                                new Rectangle(0, mTree.top + DrawableViewNode.NODE_HEIGHT / 2
                                        - getBounds().height / 2, getBounds().width,
                                        getBounds().height);
                    }
                }
            }
        });
        if (mViewport != null) {
            mModel.setViewport(mViewport);
        } else {
            doRedraw();
        }
    }

    private void setTransform() {
        if (mViewport != null && mTree != null) {
            // Set the transform.
            mTransform.identity();
            mInverse.identity();

            mTransform.scale((float) mZoom, (float) mZoom);
            mInverse.scale((float) mZoom, (float) mZoom);
            mTransform.translate((float) -mViewport.x, (float) -mViewport.y);
            mInverse.translate((float) -mViewport.x, (float) -mViewport.y);
            mInverse.invert();
        }
    }

    // Note the syncExec and then synchronized... It avoids deadlock
    public void viewportChanged() {
        Display.getDefault().syncExec(new Runnable() {
            public void run() {
                synchronized (this) {
                    mViewport = mModel.getViewport();
                    mZoom = mModel.getZoom();
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
            mSelectedNode = mModel.getSelection();
            if (mSelectedNode != null && mSelectedNode.viewNode.image == null) {
                HierarchyViewerDirector.getDirector()
                        .loadCaptureInBackground(mSelectedNode.viewNode);
            }
        }
        doRedraw();
    }
}
