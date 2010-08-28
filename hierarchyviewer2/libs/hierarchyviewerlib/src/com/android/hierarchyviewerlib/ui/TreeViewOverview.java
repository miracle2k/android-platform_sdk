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
import com.android.hierarchyviewerlib.models.TreeViewModel.ITreeChangeListener;
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

public class TreeViewOverview extends Canvas implements ITreeChangeListener {

    private TreeViewModel mModel;

    private DrawableViewNode mTree;

    private Rectangle mViewport;

    private Transform mTransform;

    private Transform mInverse;

    private Rectangle mBounds = new Rectangle();

    private double mScale;

    private boolean mDragging = false;

    private DrawableViewNode mSelectedNode;

    private static Image sNotSelectedImage;

    private static Image sSelectedImage;

    private static Image sFilteredImage;

    private static Image sFilteredSelectedImage;

    public TreeViewOverview(Composite parent) {
        super(parent, SWT.NONE);

        mModel = TreeViewModel.getModel();
        mModel.addTreeChangeListener(this);

        loadResources();

        addPaintListener(mPaintListener);
        addMouseListener(mMouseListener);
        addMouseMoveListener(mMouseMoveListener);
        addListener(SWT.Resize, mResizeListener);
        addDisposeListener(mDisposeListener);

        mTransform = new Transform(Display.getDefault());
        mInverse = new Transform(Display.getDefault());

        loadAllData();
    }

    private void loadResources() {
        ImageLoader loader = ImageLoader.getLoader(this.getClass());
        sNotSelectedImage = loader.loadImage("not-selected.png", Display.getDefault()); //$NON-NLS-1$
        sSelectedImage = loader.loadImage("selected-small.png", Display.getDefault()); //$NON-NLS-1$
        sFilteredImage = loader.loadImage("filtered.png", Display.getDefault()); //$NON-NLS-1$
        sFilteredSelectedImage =
                loader.loadImage("selected-filtered-small.png", Display.getDefault()); //$NON-NLS-1$
    }

    private DisposeListener mDisposeListener = new DisposeListener() {
        public void widgetDisposed(DisposeEvent e) {
            mModel.removeTreeChangeListener(TreeViewOverview.this);
            mTransform.dispose();
            mInverse.dispose();
        }
    };

    private MouseListener mMouseListener = new MouseListener() {

        public void mouseDoubleClick(MouseEvent e) {
            // pass
        }

        public void mouseDown(MouseEvent e) {
            boolean redraw = false;
            synchronized (TreeViewOverview.this) {
                if (mTree != null && mViewport != null) {
                    mDragging = true;
                    redraw = true;
                    handleMouseEvent(transformPoint(e.x, e.y));
                }
            }
            if (redraw) {
                mModel.removeTreeChangeListener(TreeViewOverview.this);
                mModel.setViewport(mViewport);
                mModel.addTreeChangeListener(TreeViewOverview.this);
                doRedraw();
            }
        }

        public void mouseUp(MouseEvent e) {
            boolean redraw = false;
            synchronized (TreeViewOverview.this) {
                if (mTree != null && mViewport != null) {
                    mDragging = false;
                    redraw = true;
                    handleMouseEvent(transformPoint(e.x, e.y));

                    // Update bounds and transform only on mouse up. That way,
                    // you don't get confusing behaviour during mouse drag and
                    // it snaps neatly at the end
                    setBounds();
                    setTransform();
                }
            }
            if (redraw) {
                mModel.removeTreeChangeListener(TreeViewOverview.this);
                mModel.setViewport(mViewport);
                mModel.addTreeChangeListener(TreeViewOverview.this);
                doRedraw();
            }
        }

    };

    private MouseMoveListener mMouseMoveListener = new MouseMoveListener() {
        public void mouseMove(MouseEvent e) {
            boolean moved = false;
            synchronized (TreeViewOverview.this) {
                if (mDragging) {
                    moved = true;
                    handleMouseEvent(transformPoint(e.x, e.y));
                }
            }
            if (moved) {
                mModel.removeTreeChangeListener(TreeViewOverview.this);
                mModel.setViewport(mViewport);
                mModel.addTreeChangeListener(TreeViewOverview.this);
                doRedraw();
            }
        }
    };

    private void handleMouseEvent(Point pt) {
        mViewport.x = pt.x - mViewport.width / 2;
        mViewport.y = pt.y - mViewport.height / 2;
        if (mViewport.x < mBounds.x) {
            mViewport.x = mBounds.x;
        }
        if (mViewport.y < mBounds.y) {
            mViewport.y = mBounds.y;
        }
        if (mViewport.x + mViewport.width > mBounds.x + mBounds.width) {
            mViewport.x = mBounds.x + mBounds.width - mViewport.width;
        }
        if (mViewport.y + mViewport.height > mBounds.y + mBounds.height) {
            mViewport.y = mBounds.y + mBounds.height - mViewport.height;
        }
    }

    private Point transformPoint(double x, double y) {
        float[] pt = {
                (float) x, (float) y
        };
        mInverse.transform(pt);
        return new Point(pt[0], pt[1]);
    }

    private Listener mResizeListener = new Listener() {
        public void handleEvent(Event arg0) {
            synchronized (TreeViewOverview.this) {
                setTransform();
            }
            doRedraw();
        }
    };

    private PaintListener mPaintListener = new PaintListener() {
        public void paintControl(PaintEvent e) {
            synchronized (TreeViewOverview.this) {
                if (mTree != null) {
                    e.gc.setBackground(Display.getDefault().getSystemColor(SWT.COLOR_BLACK));
                    e.gc.setForeground(Display.getDefault().getSystemColor(SWT.COLOR_WHITE));
                    e.gc.fillRectangle(0, 0, getBounds().width, getBounds().height);
                    e.gc.setTransform(mTransform);
                    e.gc.setLineWidth((int) Math.ceil(0.7 / mScale));
                    Path connectionPath = new Path(Display.getDefault());
                    paintRecursive(e.gc, mTree, connectionPath);
                    e.gc.drawPath(connectionPath);
                    connectionPath.dispose();

                    if (mViewport != null) {
                        e.gc.setAlpha(50);
                        e.gc.setBackground(Display.getDefault().getSystemColor(SWT.COLOR_WHITE));
                        e.gc.fillRectangle((int) mViewport.x, (int) mViewport.y, (int) Math
                                .ceil(mViewport.width), (int) Math.ceil(mViewport.height));

                        e.gc.setAlpha(255);
                        e.gc
                                .setForeground(Display.getDefault().getSystemColor(
                                        SWT.COLOR_DARK_GRAY));
                        e.gc.setLineWidth((int) Math.ceil(2 / mScale));
                        e.gc.drawRectangle((int) mViewport.x, (int) mViewport.y, (int) Math
                                .ceil(mViewport.width), (int) Math.ceil(mViewport.height));
                    }
                }
            }
        }
    };

    private void paintRecursive(GC gc, DrawableViewNode node, Path connectionPath) {
        if (mSelectedNode == node && node.viewNode.filtered) {
            gc.drawImage(sFilteredSelectedImage, node.left, (int) Math.round(node.top));
        } else if (mSelectedNode == node) {
            gc.drawImage(sSelectedImage, node.left, (int) Math.round(node.top));
        } else if (node.viewNode.filtered) {
            gc.drawImage(sFilteredImage, node.left, (int) Math.round(node.top));
        } else {
            gc.drawImage(sNotSelectedImage, node.left, (int) Math.round(node.top));
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
        Display.getDefault().syncExec(new Runnable() {
            public void run() {
                redraw();
            }
        });
    }

    public void loadAllData() {
        Display.getDefault().syncExec(new Runnable() {
            public void run() {
                synchronized (this) {
                    mTree = mModel.getTree();
                    mSelectedNode = mModel.getSelection();
                    mViewport = mModel.getViewport();
                    setBounds();
                    setTransform();
                }
            }
        });
    }

    // Note the syncExec and then synchronized... It avoids deadlock
    public void treeChanged() {
        Display.getDefault().syncExec(new Runnable() {
            public void run() {
                synchronized (this) {
                    mTree = mModel.getTree();
                    mSelectedNode = mModel.getSelection();
                    mViewport = mModel.getViewport();
                    setBounds();
                    setTransform();
                }
            }
        });
        doRedraw();
    }

    private void setBounds() {
        if (mViewport != null && mTree != null) {
            mBounds.x = Math.min(mViewport.x, mTree.bounds.x);
            mBounds.y = Math.min(mViewport.y, mTree.bounds.y);
            mBounds.width =
                    Math.max(mViewport.x + mViewport.width, mTree.bounds.x + mTree.bounds.width)
                            - mBounds.x;
            mBounds.height =
                    Math.max(mViewport.y + mViewport.height, mTree.bounds.y + mTree.bounds.height)
                            - mBounds.y;
        } else if (mTree != null) {
            mBounds.x = mTree.bounds.x;
            mBounds.y = mTree.bounds.y;
            mBounds.width = mTree.bounds.x + mTree.bounds.width - mBounds.x;
            mBounds.height = mTree.bounds.y + mTree.bounds.height - mBounds.y;
        }
    }

    private void setTransform() {
        if (mTree != null) {

            mTransform.identity();
            mInverse.identity();
            final Point size = new Point();
            size.x = getBounds().width;
            size.y = getBounds().height;
            if (mBounds.width == 0 || mBounds.height == 0 || size.x == 0 || size.y == 0) {
                mScale = 1;
            } else {
                mScale = Math.min(size.x / mBounds.width, size.y / mBounds.height);
            }
            mTransform.scale((float) mScale, (float) mScale);
            mInverse.scale((float) mScale, (float) mScale);
            mTransform.translate((float) -mBounds.x, (float) -mBounds.y);
            mInverse.translate((float) -mBounds.x, (float) -mBounds.y);
            if (size.x / mBounds.width < size.y / mBounds.height) {
                mTransform.translate(0, (float) (size.y / mScale - mBounds.height) / 2);
                mInverse.translate(0, (float) (size.y / mScale - mBounds.height) / 2);
            } else {
                mTransform.translate((float) (size.x / mScale - mBounds.width) / 2, 0);
                mInverse.translate((float) (size.x / mScale - mBounds.width) / 2, 0);
            }
            mInverse.invert();
        }
    }

    public void viewportChanged() {
        Display.getDefault().syncExec(new Runnable() {
            public void run() {
                synchronized (this) {
                    mViewport = mModel.getViewport();
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
            mSelectedNode = mModel.getSelection();
        }
        doRedraw();
    }
}
