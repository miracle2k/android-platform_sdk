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

import com.android.hierarchyviewerlib.models.PixelPerfectModel;
import com.android.hierarchyviewerlib.models.PixelPerfectModel.IImageChangeListener;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.MouseWheelListener;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.graphics.Transform;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;

public class PixelPerfectLoupe extends Canvas implements IImageChangeListener {
    private PixelPerfectModel mModel;

    private Image mImage;

    private Image mGrid;

    private Color mCrosshairColor;

    private int mWidth;

    private int mHeight;

    private Point mCrosshairLocation;

    private int mZoom;

    private Transform mTransform;

    private int mCanvasWidth;

    private int mCanvasHeight;

    private Image mOverlayImage;

    private double mOverlayTransparency;

    private boolean mShowOverlay = false;

    public PixelPerfectLoupe(Composite parent) {
        super(parent, SWT.NONE);
        mModel = PixelPerfectModel.getModel();
        mModel.addImageChangeListener(this);

        addPaintListener(mPaintListener);
        addMouseListener(mMouseListener);
        addMouseWheelListener(mMouseWheelListener);
        addDisposeListener(mDisposeListener);
        addKeyListener(mKeyListener);

        mCrosshairColor = new Color(Display.getDefault(), new RGB(255, 94, 254));

        mTransform = new Transform(Display.getDefault());

        imageLoaded();
    }

    public void setShowOverlay(boolean value) {
        synchronized (this) {
            mShowOverlay = value;
        }
        doRedraw();
    }

    private DisposeListener mDisposeListener = new DisposeListener() {
        public void widgetDisposed(DisposeEvent e) {
            mModel.removeImageChangeListener(PixelPerfectLoupe.this);
            mCrosshairColor.dispose();
            mTransform.dispose();
            if (mGrid != null) {
                mGrid.dispose();
            }
        }
    };

    private MouseListener mMouseListener = new MouseListener() {

        public void mouseDoubleClick(MouseEvent e) {
            // pass
        }

        public void mouseDown(MouseEvent e) {
            handleMouseEvent(e);
        }

        public void mouseUp(MouseEvent e) {
            //
        }

    };

    private MouseWheelListener mMouseWheelListener = new MouseWheelListener() {
        public void mouseScrolled(MouseEvent e) {
            int newZoom = -1;
            synchronized (PixelPerfectLoupe.this) {
                if (mImage != null && mCrosshairLocation != null) {
                    if (e.count > 0) {
                        newZoom = mZoom + 1;
                    } else {
                        newZoom = mZoom - 1;
                    }
                }
            }
            if (newZoom != -1) {
                mModel.setZoom(newZoom);
            }
        }
    };

    private void handleMouseEvent(MouseEvent e) {
        int newX = -1;
        int newY = -1;
        synchronized (PixelPerfectLoupe.this) {
            if (mImage == null) {
                return;
            }
            int zoomedX = -mCrosshairLocation.x * mZoom - mZoom / 2 + getBounds().width / 2;
            int zoomedY = -mCrosshairLocation.y * mZoom - mZoom / 2 + getBounds().height / 2;
            int x = (e.x - zoomedX) / mZoom;
            int y = (e.y - zoomedY) / mZoom;
            if (x >= 0 && x < mWidth && y >= 0 && y < mHeight) {
                newX = x;
                newY = y;
            }
        }
        if (newX != -1) {
            mModel.setCrosshairLocation(newX, newY);
        }
    }

    private KeyListener mKeyListener = new KeyListener() {

        public void keyPressed(KeyEvent e) {
            boolean crosshairMoved = false;
            synchronized (PixelPerfectLoupe.this) {
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
            synchronized (PixelPerfectLoupe.this) {
                e.gc.setBackground(Display.getDefault().getSystemColor(SWT.COLOR_BLACK));
                e.gc.fillRectangle(0, 0, getSize().x, getSize().y);
                if (mImage != null && mCrosshairLocation != null) {
                    int zoomedX = -mCrosshairLocation.x * mZoom - mZoom / 2 + getBounds().width / 2;
                    int zoomedY = -mCrosshairLocation.y * mZoom - mZoom / 2 + getBounds().height / 2;
                    mTransform.translate(zoomedX, zoomedY);
                    mTransform.scale(mZoom, mZoom);
                    e.gc.setInterpolation(SWT.NONE);
                    e.gc.setTransform(mTransform);
                    e.gc.drawImage(mImage, 0, 0);
                    if (mShowOverlay && mOverlayImage != null) {
                        e.gc.setAlpha((int) (mOverlayTransparency * 255));
                        e.gc.drawImage(mOverlayImage, 0, mHeight - mOverlayImage.getBounds().height);
                        e.gc.setAlpha(255);
                    }

                    mTransform.identity();
                    e.gc.setTransform(mTransform);

                    // If the size of the canvas has changed, we need to make
                    // another grid.
                    if (mGrid != null
                            && (mCanvasWidth != getBounds().width || mCanvasHeight != getBounds().height)) {
                        mGrid.dispose();
                        mGrid = null;
                    }
                    mCanvasWidth = getBounds().width;
                    mCanvasHeight = getBounds().height;
                    if (mGrid == null) {
                        // Make a transparent image;
                        ImageData imageData =
                                new ImageData(mCanvasWidth + mZoom + 1, mCanvasHeight + mZoom + 1, 1,
                                        new PaletteData(new RGB[] {
                                            new RGB(0, 0, 0)
                                        }));
                        imageData.transparentPixel = 0;

                        // Draw the grid.
                        mGrid = new Image(Display.getDefault(), imageData);
                        GC gc = new GC(mGrid);
                        gc.setForeground(Display.getDefault().getSystemColor(SWT.COLOR_WHITE));
                        for (int x = 0; x <= mCanvasWidth + mZoom; x += mZoom) {
                            gc.drawLine(x, 0, x, mCanvasHeight + mZoom);
                        }
                        for (int y = 0; y <= mCanvasHeight + mZoom; y += mZoom) {
                            gc.drawLine(0, y, mCanvasWidth + mZoom, y);
                        }
                        gc.dispose();
                    }

                    e.gc.setClipping(new Rectangle(zoomedX, zoomedY, mWidth * mZoom + 1, mHeight
                            * mZoom + 1));
                    e.gc.setAlpha(76);
                    e.gc.drawImage(mGrid, (mCanvasWidth / 2 - mZoom / 2) % mZoom - mZoom,
                            (mCanvasHeight / 2 - mZoom / 2) % mZoom - mZoom);
                    e.gc.setAlpha(255);

                    e.gc.setForeground(mCrosshairColor);
                    e.gc.drawLine(0, mCanvasHeight / 2, mCanvasWidth - 1, mCanvasHeight / 2);
                    e.gc.drawLine(mCanvasWidth / 2, 0, mCanvasWidth / 2, mCanvasHeight - 1);
                }
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

    private void loadImage() {
        mImage = mModel.getImage();
        if (mImage != null) {
            mWidth = mImage.getBounds().width;
            mHeight = mImage.getBounds().height;
        } else {
            mWidth = 0;
            mHeight = 0;
        }
    }

    // Note the syncExec and then synchronized... It avoids deadlock
    public void imageLoaded() {
        Display.getDefault().syncExec(new Runnable() {
            public void run() {
                synchronized (this) {
                    loadImage();
                    mCrosshairLocation = mModel.getCrosshairLocation();
                    mZoom = mModel.getZoom();
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
        // pass
    }

    public void treeChanged() {
        // pass
    }

    public void zoomChanged() {
        Display.getDefault().syncExec(new Runnable() {
            public void run() {
                synchronized (this) {
                    if (mGrid != null) {
                        // To notify that the zoom level has changed, we get rid
                        // of the
                        // grid.
                        mGrid.dispose();
                        mGrid = null;
                    }
                    mZoom = mModel.getZoom();
                }
            }
        });
        doRedraw();
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
