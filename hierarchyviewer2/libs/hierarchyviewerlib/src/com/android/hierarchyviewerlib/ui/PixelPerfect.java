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

import com.android.hierarchyviewerlib.device.ViewNode;
import com.android.hierarchyviewerlib.models.PixelPerfectModel;
import com.android.hierarchyviewerlib.models.PixelPerfectModel.IImageChangeListener;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.MouseMoveListener;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;

public class PixelPerfect extends ScrolledComposite implements IImageChangeListener {
    private Canvas mCanvas;

    private PixelPerfectModel mModel;

    private Image mImage;

    private Color mCrosshairColor;

    private Color mMarginColor;

    private Color mBorderColor;

    private Color mPaddingColor;

    private int mWidth;

    private int mHeight;

    private Point mCrosshairLocation;

    private ViewNode mSelectedNode;

    private Image mOverlayImage;

    private double mOverlayTransparency;

    public PixelPerfect(Composite parent) {
        super(parent, SWT.H_SCROLL | SWT.V_SCROLL);
        mCanvas = new Canvas(this, SWT.NONE);
        setContent(mCanvas);
        setExpandHorizontal(true);
        setExpandVertical(true);
        mModel = PixelPerfectModel.getModel();
        mModel.addImageChangeListener(this);

        mCanvas.addPaintListener(mPaintListener);
        mCanvas.addMouseListener(mMouseListener);
        mCanvas.addMouseMoveListener(mMouseMoveListener);
        mCanvas.addKeyListener(mKeyListener);

        addDisposeListener(mDisposeListener);

        mCrosshairColor = new Color(Display.getDefault(), new RGB(0, 255, 255));
        mBorderColor = new Color(Display.getDefault(), new RGB(255, 0, 0));
        mMarginColor = new Color(Display.getDefault(), new RGB(0, 255, 0));
        mPaddingColor = new Color(Display.getDefault(), new RGB(0, 0, 255));

        imageLoaded();
    }

    private DisposeListener mDisposeListener = new DisposeListener() {
        public void widgetDisposed(DisposeEvent e) {
            mModel.removeImageChangeListener(PixelPerfect.this);
            mCrosshairColor.dispose();
            mBorderColor.dispose();
            mPaddingColor.dispose();
        }
    };

    @Override
    public boolean setFocus() {
        return mCanvas.setFocus();
    }

    private MouseListener mMouseListener = new MouseListener() {

        public void mouseDoubleClick(MouseEvent e) {
            // pass
        }

        public void mouseDown(MouseEvent e) {
            handleMouseEvent(e);
        }

        public void mouseUp(MouseEvent e) {
            handleMouseEvent(e);
        }

    };

    private MouseMoveListener mMouseMoveListener = new MouseMoveListener() {
        public void mouseMove(MouseEvent e) {
            if (e.stateMask != 0) {
                handleMouseEvent(e);
            }
        }
    };

    private void handleMouseEvent(MouseEvent e) {
        synchronized (PixelPerfect.this) {
            if (mImage == null) {
                return;
            }
            int leftOffset = mCanvas.getSize().x / 2 - mWidth / 2;
            int topOffset = mCanvas.getSize().y / 2 - mHeight / 2;
            e.x -= leftOffset;
            e.y -= topOffset;
            e.x = Math.max(e.x, 0);
            e.x = Math.min(e.x, mWidth - 1);
            e.y = Math.max(e.y, 0);
            e.y = Math.min(e.y, mHeight - 1);
        }
        mModel.setCrosshairLocation(e.x, e.y);
    }

    private KeyListener mKeyListener = new KeyListener() {

        public void keyPressed(KeyEvent e) {
            boolean crosshairMoved = false;
            synchronized (PixelPerfect.this) {
                if (mImage != null) {
                    switch (e.keyCode) {
                        case SWT.ARROW_UP:
                            if (mCrosshairLocation.y != 0) {
                                mCrosshairLocation.y--;
                                crosshairMoved = true;
                            }
                            break;
                        case SWT.ARROW_DOWN:
                            if (mCrosshairLocation.y != mHeight - 1) {
                                mCrosshairLocation.y++;
                                crosshairMoved = true;
                            }
                            break;
                        case SWT.ARROW_LEFT:
                            if (mCrosshairLocation.x != 0) {
                                mCrosshairLocation.x--;
                                crosshairMoved = true;
                            }
                            break;
                        case SWT.ARROW_RIGHT:
                            if (mCrosshairLocation.x != mWidth - 1) {
                                mCrosshairLocation.x++;
                                crosshairMoved = true;
                            }
                            break;
                    }
                }
            }
            if (crosshairMoved) {
                mModel.setCrosshairLocation(mCrosshairLocation.x, mCrosshairLocation.y);
            }
        }

        public void keyReleased(KeyEvent e) {
            // pass
        }

    };

    private PaintListener mPaintListener = new PaintListener() {
        public void paintControl(PaintEvent e) {
            synchronized (PixelPerfect.this) {
                e.gc.setBackground(Display.getDefault().getSystemColor(SWT.COLOR_BLACK));
                e.gc.fillRectangle(0, 0, mCanvas.getSize().x, mCanvas.getSize().y);
                if (mImage != null) {
                    // Let's be cool and put it in the center...
                    int leftOffset = mCanvas.getSize().x / 2 - mWidth / 2;
                    int topOffset = mCanvas.getSize().y / 2 - mHeight / 2;
                    e.gc.drawImage(mImage, leftOffset, topOffset);
                    if (mOverlayImage != null) {
                        e.gc.setAlpha((int) (mOverlayTransparency * 255));
                        int overlayTopOffset =
                                mCanvas.getSize().y / 2 + mHeight / 2
                                        - mOverlayImage.getBounds().height;
                        e.gc.drawImage(mOverlayImage, leftOffset, overlayTopOffset);
                        e.gc.setAlpha(255);
                    }

                    if (mSelectedNode != null) {
                        // If the screen is in landscape mode, the
                        // coordinates are backwards.
                        int leftShift = 0;
                        int topShift = 0;
                        int nodeLeft = mSelectedNode.left;
                        int nodeTop = mSelectedNode.top;
                        int nodeWidth = mSelectedNode.width;
                        int nodeHeight = mSelectedNode.height;
                        int nodeMarginLeft = mSelectedNode.marginLeft;
                        int nodeMarginTop = mSelectedNode.marginTop;
                        int nodeMarginRight = mSelectedNode.marginRight;
                        int nodeMarginBottom = mSelectedNode.marginBottom;
                        int nodePadLeft = mSelectedNode.paddingLeft;
                        int nodePadTop = mSelectedNode.paddingTop;
                        int nodePadRight = mSelectedNode.paddingRight;
                        int nodePadBottom = mSelectedNode.paddingBottom;
                        ViewNode cur = mSelectedNode;
                        while (cur.parent != null) {
                            leftShift += cur.parent.left - cur.parent.scrollX;
                            topShift += cur.parent.top - cur.parent.scrollY;
                            cur = cur.parent;
                        }

                        // Everything is sideways.
                        if (cur.width > cur.height) {
                            e.gc.setForeground(mPaddingColor);
                            e.gc.drawRectangle(leftOffset + mWidth - nodeTop - topShift - nodeHeight
                                    + nodePadBottom,
                                    topOffset + leftShift + nodeLeft + nodePadLeft, nodeHeight
                                            - nodePadBottom - nodePadTop, nodeWidth - nodePadRight
                                            - nodePadLeft);
                            e.gc.setForeground(mMarginColor);
                            e.gc.drawRectangle(leftOffset + mWidth - nodeTop - topShift - nodeHeight
                                    - nodeMarginBottom, topOffset + leftShift + nodeLeft
                                    - nodeMarginLeft,
                                    nodeHeight + nodeMarginBottom + nodeMarginTop, nodeWidth
                                            + nodeMarginRight + nodeMarginLeft);
                            e.gc.setForeground(mBorderColor);
                            e.gc.drawRectangle(
                                    leftOffset + mWidth - nodeTop - topShift - nodeHeight, topOffset
                                            + leftShift + nodeLeft, nodeHeight, nodeWidth);
                        } else {
                            e.gc.setForeground(mPaddingColor);
                            e.gc.drawRectangle(leftOffset + leftShift + nodeLeft + nodePadLeft,
                                    topOffset + topShift + nodeTop + nodePadTop, nodeWidth
                                            - nodePadRight - nodePadLeft, nodeHeight
                                            - nodePadBottom - nodePadTop);
                            e.gc.setForeground(mMarginColor);
                            e.gc.drawRectangle(leftOffset + leftShift + nodeLeft - nodeMarginLeft,
                                    topOffset + topShift + nodeTop - nodeMarginTop, nodeWidth
                                            + nodeMarginRight + nodeMarginLeft, nodeHeight
                                            + nodeMarginBottom + nodeMarginTop);
                            e.gc.setForeground(mBorderColor);
                            e.gc.drawRectangle(leftOffset + leftShift + nodeLeft, topOffset
                                    + topShift + nodeTop, nodeWidth, nodeHeight);
                        }
                    }
                    if (mCrosshairLocation != null) {
                        e.gc.setForeground(mCrosshairColor);
                        e.gc.drawLine(leftOffset, topOffset + mCrosshairLocation.y, leftOffset
                                + mWidth - 1, topOffset + mCrosshairLocation.y);
                        e.gc.drawLine(leftOffset + mCrosshairLocation.x, topOffset, leftOffset
                                + mCrosshairLocation.x, topOffset + mHeight - 1);
                    }
                }
            }
        }
    };

    private void doRedraw() {
        Display.getDefault().syncExec(new Runnable() {
            public void run() {
                mCanvas.redraw();
            }
        });
    }

    private void loadImage() {
        mImage = mModel.getImage();
        if (mImage != null) {
            mWidth = mImage.getBounds().width;
            mHeight = mImage.getBounds().height;
        } else {
            mWidth = 0;
            mHeight = 0;
        }
        setMinSize(mWidth, mHeight);
    }

    public void imageLoaded() {
        Display.getDefault().syncExec(new Runnable() {
            public void run() {
                synchronized (this) {
                    loadImage();
                    mCrosshairLocation = mModel.getCrosshairLocation();
                    mSelectedNode = mModel.getSelected();
                    mOverlayImage = mModel.getOverlayImage();
                    mOverlayTransparency = mModel.getOverlayTransparency();
                }
            }
        });
        doRedraw();
    }

    public void imageChanged() {
        Display.getDefault().syncExec(new Runnable() {
            public void run() {
                synchronized (this) {
                    loadImage();
                }
            }
        });
        doRedraw();
    }

    public void crosshairMoved() {
        synchronized (this) {
            mCrosshairLocation = mModel.getCrosshairLocation();
        }
        doRedraw();
    }

    public void selectionChanged() {
        synchronized (this) {
            mSelectedNode = mModel.getSelected();
        }
        doRedraw();
    }

    // Note the syncExec and then synchronized... It avoids deadlock
    public void treeChanged() {
        Display.getDefault().syncExec(new Runnable() {
            public void run() {
                synchronized (this) {
                    mSelectedNode = mModel.getSelected();
                }
            }
        });
        doRedraw();
    }

    public void zoomChanged() {
        // pass
    }

    public void overlayChanged() {
        synchronized (this) {
            mOverlayImage = mModel.getOverlayImage();
            mOverlayTransparency = mModel.getOverlayTransparency();
        }
        doRedraw();
    }

    public void overlayTransparencyChanged() {
        synchronized (this) {
            mOverlayTransparency = mModel.getOverlayTransparency();
        }
        doRedraw();
    }
}
