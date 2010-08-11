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

import com.android.ddmlib.RawImage;
import com.android.hierarchyviewerlib.ComponentRegistry;
import com.android.hierarchyviewerlib.device.ViewNode;
import com.android.hierarchyviewerlib.models.PixelPerfectModel;
import com.android.hierarchyviewerlib.models.PixelPerfectModel.ImageChangeListener;
import com.android.hierarchyviewerlib.models.PixelPerfectModel.Point;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.MouseMoveListener;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;

public class PixelPerfect extends ScrolledComposite implements ImageChangeListener {
    private Canvas canvas;

    private PixelPerfectModel model;

    private Image image;

    private Color crosshairColor;

    private Color marginColor;

    private Color borderColor;

    private Color paddingColor;

    private int width;

    private int height;

    private Point crosshairLocation;

    private ViewNode selectedNode;

    public PixelPerfect(Composite parent) {
        super(parent, SWT.H_SCROLL | SWT.V_SCROLL);
        canvas = new Canvas(this, SWT.NONE);
        setContent(canvas);
        setExpandHorizontal(true);
        setExpandVertical(true);
        model = ComponentRegistry.getPixelPerfectModel();
        model.addImageChangeListener(this);

        canvas.addPaintListener(paintListener);
        canvas.addMouseListener(mouseListener);
        canvas.addMouseMoveListener(mouseMoveListener);

        addDisposeListener(disposeListener);

        crosshairColor = new Color(Display.getDefault(), new RGB(0, 255, 255));
        borderColor = new Color(Display.getDefault(), new RGB(255, 0, 0));
        marginColor = new Color(Display.getDefault(), new RGB(0, 255, 0));
        paddingColor = new Color(Display.getDefault(), new RGB(0, 0, 255));
    }

    private DisposeListener disposeListener = new DisposeListener() {
        public void widgetDisposed(DisposeEvent e) {
            model.removeImageChangeListener(PixelPerfect.this);
            if (image != null) {
                image.dispose();
            }
            crosshairColor.dispose();
            borderColor.dispose();
            paddingColor.dispose();
        }
    };

    @Override
    public boolean setFocus() {
        return canvas.setFocus();
    }

    private MouseListener mouseListener = new MouseListener() {

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

    private MouseMoveListener mouseMoveListener = new MouseMoveListener() {
        public void mouseMove(MouseEvent e) {
            if (e.stateMask != 0) {
                handleMouseEvent(e);
            }
        }
    };

    private void handleMouseEvent(MouseEvent e) {
        synchronized (this) {
            if (image == null) {
                return;
            }
            int leftOffset = canvas.getSize().x / 2 - width / 2;
            int topOffset = canvas.getSize().y / 2 - height / 2;
            e.x -= leftOffset;
            e.y -= topOffset;
            e.x = Math.max(e.x, 0);
            e.x = Math.min(e.x, width - 1);
            e.y = Math.max(e.y, 0);
            e.y = Math.min(e.y, height - 1);
        }
        model.setCrosshairLocation(e.x, e.y);
    }

    private PaintListener paintListener = new PaintListener() {
        public void paintControl(PaintEvent e) {
            synchronized (this) {
                e.gc.setBackground(Display.getDefault().getSystemColor(SWT.COLOR_BLACK));
                e.gc.fillRectangle(0, 0, canvas.getSize().x, canvas.getSize().y);
                if (image != null) {
                    // Let's be cool and put it in the center...
                    int leftOffset = canvas.getSize().x / 2 - width / 2;
                    int topOffset = canvas.getSize().y / 2 - height / 2;
                    e.gc.drawImage(image, leftOffset, topOffset);
                    if (selectedNode != null) {
                        // There are a few quirks here. First of all, margins
                        // are sometimes negative or positive numbers... Yet,
                        // they are always treated as positive.
                        // Secondly, if the screen is in landscape mode, the
                        // coordinates are backwards.
                        int leftShift = 0;
                        int topShift = 0;
                        int nodeLeft = selectedNode.left;
                        int nodeTop = selectedNode.top;
                        int nodeWidth = selectedNode.width;
                        int nodeHeight = selectedNode.height;
                        int nodeMarginLeft = selectedNode.marginLeft;
                        int nodeMarginTop = selectedNode.marginTop;
                        int nodeMarginRight = selectedNode.marginRight;
                        int nodeMarginBottom = selectedNode.marginBottom;
                        int nodePadLeft = selectedNode.paddingLeft;
                        int nodePadTop = selectedNode.paddingTop;
                        int nodePadRight = selectedNode.paddingRight;
                        int nodePadBottom = selectedNode.paddingBottom;
                        ViewNode cur = selectedNode;
                        while (cur.parent != null) {
                            leftShift += cur.parent.left;
                            topShift += cur.parent.top;
                            cur = cur.parent;
                        }

                        // Everything is sideways.
                        if (cur.width > cur.height) {
                            e.gc.setForeground(paddingColor);
                            e.gc.drawRectangle(leftOffset + width - nodeTop - topShift - nodeHeight
                                    + nodePadBottom,
                                    topOffset + leftShift + nodeLeft + nodePadLeft, nodeHeight
                                            - nodePadBottom - nodePadTop, nodeWidth - nodePadRight
                                            - nodePadLeft);
                            e.gc.setForeground(marginColor);
                            e.gc.drawRectangle(leftOffset + width - nodeTop - topShift - nodeHeight
                                    - nodeMarginBottom, topOffset + leftShift + nodeLeft
                                    - nodeMarginLeft,
                                    nodeHeight + nodeMarginBottom + nodeMarginTop, nodeWidth
                                            + nodeMarginRight + nodeMarginLeft);
                            e.gc.setForeground(borderColor);
                            e.gc.drawRectangle(
                                    leftOffset + width - nodeTop - topShift - nodeHeight, topOffset
                                            + leftShift + nodeLeft, nodeHeight, nodeWidth);
                        } else {
                            e.gc.setForeground(paddingColor);
                            e.gc.drawRectangle(leftOffset + leftShift + nodeLeft + nodePadLeft,
                                    topOffset + topShift + nodeTop + nodePadTop, nodeWidth
                                            - nodePadRight - nodePadLeft, nodeHeight
                                            - nodePadBottom - nodePadTop);
                            e.gc.setForeground(marginColor);
                            e.gc.drawRectangle(leftOffset + leftShift + nodeLeft - nodeMarginLeft,
                                    topOffset + topShift + nodeTop - nodeMarginTop, nodeWidth
                                            + nodeMarginRight + nodeMarginLeft, nodeHeight
                                            + nodeMarginBottom + nodeMarginTop);
                            e.gc.setForeground(borderColor);
                            e.gc.drawRectangle(leftOffset + leftShift + nodeLeft, topOffset
                                    + topShift + nodeTop, nodeWidth, nodeHeight);
                        }
                    }
                    if (crosshairLocation != null) {
                        e.gc.setForeground(crosshairColor);
                        e.gc.drawLine(leftOffset, topOffset + crosshairLocation.y, leftOffset
                                + width - 1, topOffset + crosshairLocation.y);
                        e.gc.drawLine(leftOffset + crosshairLocation.x, topOffset, leftOffset
                                + crosshairLocation.x, topOffset + height - 1);
                    }
                }
            }
        }
    };

    private void doRedraw() {
        Display.getDefault().asyncExec(new Runnable() {
            public void run() {
                canvas.redraw();
            }
        });
    }

    private void loadImage() {
        final RawImage rawImage = model.getImage();
        if (rawImage != null) {
            ImageData imageData =
                    new ImageData(rawImage.width, rawImage.height, rawImage.bpp,
                            new PaletteData(rawImage.getRedMask(), rawImage.getGreenMask(),
                                    rawImage.getBlueMask()), 1, rawImage.data);
            if (image != null) {
                image.dispose();
            }
            image = new Image(Display.getDefault(), imageData);
            width = rawImage.width;
            height = rawImage.height;

        } else {
            if (image != null) {
                image.dispose();
                image = null;
            }
            width = 0;
            height = 0;
        }
        Display.getDefault().asyncExec(new Runnable() {
            public void run() {
                setMinSize(width, height);
            }
        });
    }

    public void imageLoaded() {
        synchronized (this) {
            loadImage();
            crosshairLocation = model.getCrosshairLocation();
            selectedNode = model.getSelected();
        }
        doRedraw();
    }

    public void imageChanged() {
        synchronized (this) {
            loadImage();
        }
        doRedraw();
    }

    public void crosshairMoved() {
        synchronized (this) {
            crosshairLocation = model.getCrosshairLocation();
        }
        doRedraw();
    }

    public void selectionChanged() {
        synchronized (this) {
            selectedNode = model.getSelected();
        }
        doRedraw();
    }

    public void focusChanged() {
        synchronized (this) {
            loadImage();
            selectedNode = model.getSelected();
        }
        doRedraw();
    }

    public void zoomChanged() {
        // pass
    }
}
