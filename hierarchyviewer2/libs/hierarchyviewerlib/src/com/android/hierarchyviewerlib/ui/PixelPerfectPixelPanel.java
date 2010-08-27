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
import com.android.hierarchyviewerlib.models.PixelPerfectModel.ImageChangeListener;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;

public class PixelPerfectPixelPanel extends Canvas implements ImageChangeListener {
    private PixelPerfectModel model;

    private Image image;

    private Image overlayImage;

    private Point crosshairLocation;

    public static final int PREFERRED_WIDTH = 180;

    public static final int PREFERRED_HEIGHT = 52;

    public PixelPerfectPixelPanel(Composite parent) {
        super(parent, SWT.NONE);
        model = PixelPerfectModel.getModel();
        model.addImageChangeListener(this);

        addPaintListener(paintListener);
        addDisposeListener(disposeListener);

        imageLoaded();
    }

    @Override
    public Point computeSize(int wHint, int hHint, boolean changed) {
        int height = PREFERRED_HEIGHT;
        int width = (wHint == SWT.DEFAULT) ? PREFERRED_WIDTH : wHint;
        return new Point(width, height);
    }

    private DisposeListener disposeListener = new DisposeListener() {
        public void widgetDisposed(DisposeEvent e) {
            model.removeImageChangeListener(PixelPerfectPixelPanel.this);
        }
    };

    private PaintListener paintListener = new PaintListener() {
        public void paintControl(PaintEvent e) {
            synchronized (PixelPerfectPixelPanel.this) {
                e.gc.setBackground(Display.getDefault().getSystemColor(SWT.COLOR_BLACK));
                e.gc.fillRectangle(0, 0, getBounds().width, getBounds().height);
                if (image != null) {
                    RGB pixel =
                            image.getImageData().palette.getRGB(image.getImageData().getPixel(
                                    crosshairLocation.x, crosshairLocation.y));
                    Color rgbColor = new Color(Display.getDefault(), pixel);
                    e.gc.setForeground(Display.getDefault().getSystemColor(SWT.COLOR_WHITE));
                    e.gc.setBackground(rgbColor);
                    e.gc.drawRectangle(4, 4, 60, 30);
                    e.gc.fillRectangle(5, 5, 59, 29);
                    rgbColor.dispose();
                    e.gc.drawText("#"
                            + Integer
                                    .toHexString(
                                            (1 << 24) + (pixel.red << 16) + (pixel.green << 8)
                                                    + pixel.blue).substring(1), 4, 35, true);
                    e.gc.drawText("R:", 80, 4, true);
                    e.gc.drawText("G:", 80, 20, true);
                    e.gc.drawText("B:", 80, 35, true);
                    e.gc.drawText(Integer.toString(pixel.red), 97, 4, true);
                    e.gc.drawText(Integer.toString(pixel.green), 97, 20, true);
                    e.gc.drawText(Integer.toString(pixel.blue), 97, 35, true);
                    e.gc.drawText("X:", 132, 4, true);
                    e.gc.drawText("Y:", 132, 20, true);
                    e.gc.drawText(Integer.toString(crosshairLocation.x) + " px", 149, 4, true);
                    e.gc.drawText(Integer.toString(crosshairLocation.y) + " px", 149, 20, true);

                    if (overlayImage != null) {
                        int xInOverlay = crosshairLocation.x;
                        int yInOverlay =
                                crosshairLocation.y
                                        - (image.getBounds().height - overlayImage.getBounds().height);
                        if (xInOverlay >= 0 && yInOverlay >= 0
                                && xInOverlay < overlayImage.getBounds().width
                                && yInOverlay < overlayImage.getBounds().height) {
                            pixel =
                                    overlayImage.getImageData().palette.getRGB(overlayImage
                                            .getImageData().getPixel(xInOverlay, yInOverlay));
                            rgbColor = new Color(Display.getDefault(), pixel);
                            e.gc
                                    .setForeground(Display.getDefault().getSystemColor(
                                            SWT.COLOR_WHITE));
                            e.gc.setBackground(rgbColor);
                            e.gc.drawRectangle(204, 4, 60, 30);
                            e.gc.fillRectangle(205, 5, 59, 29);
                            rgbColor.dispose();
                            e.gc.drawText("#"
                                    + Integer.toHexString(
                                            (1 << 24) + (pixel.red << 16) + (pixel.green << 8)
                                                    + pixel.blue).substring(1), 204, 35, true);
                            e.gc.drawText("R:", 280, 4, true);
                            e.gc.drawText("G:", 280, 20, true);
                            e.gc.drawText("B:", 280, 35, true);
                            e.gc.drawText(Integer.toString(pixel.red), 297, 4, true);
                            e.gc.drawText(Integer.toString(pixel.green), 297, 20, true);
                            e.gc.drawText(Integer.toString(pixel.blue), 297, 35, true);
                        }
                    }
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

    public void crosshairMoved() {
        synchronized (this) {
            crosshairLocation = model.getCrosshairLocation();
        }
        doRedraw();
    }

    public void imageChanged() {
        synchronized (this) {
            image = model.getImage();
        }
        doRedraw();
    }

    public void imageLoaded() {
        synchronized (this) {
            image = model.getImage();
            crosshairLocation = model.getCrosshairLocation();
            overlayImage = model.getOverlayImage();
        }
        doRedraw();
    }

    public void overlayChanged() {
        synchronized (this) {
            overlayImage = model.getOverlayImage();
        }
        doRedraw();
    }

    public void overlayTransparencyChanged() {
        // pass
    }

    public void selectionChanged() {
        // pass
    }

    public void treeChanged() {
        // pass
    }

    public void zoomChanged() {
        // pass
    }
}
