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
import com.android.hierarchyviewerlib.models.PixelPerfectModel;
import com.android.hierarchyviewerlib.models.PixelPerfectModel.ImageChangeListener;
import com.android.hierarchyviewerlib.models.PixelPerfectModel.Point;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
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
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.graphics.Transform;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;

public class PixelPerfectLoupe extends Canvas implements ImageChangeListener {
    private PixelPerfectModel model;

    private Image image;

    private Image grid;

    private Color crosshairColor;

    private int width;

    private int height;

    private Point crosshairLocation;

    private int zoom;

    private Transform transform;

    private int canvasWidth;

    private int canvasHeight;

    public PixelPerfectLoupe(Composite parent) {
        super(parent, SWT.NONE);
        model = ComponentRegistry.getPixelPerfectModel();
        model.addImageChangeListener(this);

        addPaintListener(paintListener);
        addMouseListener(mouseListener);
        addMouseWheelListener(mouseWheelListener);
        addDisposeListener(disposeListener);

        crosshairColor = new Color(Display.getDefault(), new RGB(255, 94, 254));

        transform = new Transform(Display.getDefault());
    }

    private DisposeListener disposeListener = new DisposeListener() {
        public void widgetDisposed(DisposeEvent e) {
            model.removeImageChangeListener(PixelPerfectLoupe.this);
            if (image != null) {
                image.dispose();
            }
            crosshairColor.dispose();
            transform.dispose();
            if (grid != null) {
                grid.dispose();
            }
        }
    };

    private MouseListener mouseListener = new MouseListener() {

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

    private MouseWheelListener mouseWheelListener = new MouseWheelListener() {
        public void mouseScrolled(MouseEvent e) {
            int newZoom = -1;
            synchronized (this) {
                if (image != null && crosshairLocation != null) {
                    if (e.count > 0) {
                        newZoom = zoom + 1;
                    } else {
                        newZoom = zoom - 1;
                    }
                }
            }
            if (newZoom != -1) {
                model.setZoom(newZoom);
            }
        }
    };

    private void handleMouseEvent(MouseEvent e) {
        int newX = -1;
        int newY = -1;
        synchronized (this) {
            if (image == null) {
                return;
            }
            int zoomedX = -crosshairLocation.x * zoom - zoom / 2 + getBounds().width / 2;
            int zoomedY = -crosshairLocation.y * zoom - zoom / 2 + getBounds().height / 2;
            int x = (e.x - zoomedX) / zoom;
            int y = (e.y - zoomedY) / zoom;
            if (x >= 0 && x < width && y >= 0 && y < height) {
                newX = x;
                newY = y;
            }
        }
        if (newX != -1) {
            model.setCrosshairLocation(newX, newY);
        }
    }

    private PaintListener paintListener = new PaintListener() {
        public void paintControl(PaintEvent e) {
            synchronized (this) {
                e.gc.setBackground(Display.getDefault().getSystemColor(SWT.COLOR_BLACK));
                e.gc.fillRectangle(0, 0, getSize().x, getSize().y);
                if (image != null && crosshairLocation != null) {
                    int zoomedX = -crosshairLocation.x * zoom - zoom / 2 + getBounds().width / 2;
                    int zoomedY = -crosshairLocation.y * zoom - zoom / 2 + getBounds().height / 2;
                    transform.translate(zoomedX, zoomedY);
                    transform.scale(zoom, zoom);
                    e.gc.setInterpolation(SWT.NONE);
                    e.gc.setTransform(transform);
                    e.gc.drawImage(image, 0, 0);
                    transform.identity();
                    e.gc.setTransform(transform);

                    // If the size of the canvas has changed, we need to make
                    // another grid.
                    if (grid != null
                            && (canvasWidth != getBounds().width || canvasHeight != getBounds().height)) {
                        grid.dispose();
                        grid = null;
                    }
                    canvasWidth = getBounds().width;
                    canvasHeight = getBounds().height;
                    if (grid == null) {
                        // Make a transparent image;
                        ImageData imageData =
                                new ImageData(canvasWidth + zoom + 1, canvasHeight + zoom + 1, 1,
                                        new PaletteData(new RGB[] {
                                            new RGB(0, 0, 0)
                                        }));
                        imageData.transparentPixel = 0;

                        // Draw the grid.
                        grid = new Image(Display.getDefault(), imageData);
                        GC gc = new GC(grid);
                        gc.setForeground(Display.getDefault().getSystemColor(SWT.COLOR_WHITE));
                        for (int x = 0; x <= canvasWidth + zoom; x += zoom) {
                            gc.drawLine(x, 0, x, canvasHeight + zoom);
                        }
                        for (int y = 0; y <= canvasHeight + zoom; y += zoom) {
                            gc.drawLine(0, y, canvasWidth + zoom, y);
                        }
                        gc.dispose();
                    }

                    e.gc.setClipping(new Rectangle(zoomedX, zoomedY, width * zoom + 1, height
                            * zoom + 1));
                    e.gc.setAlpha(76);
                    e.gc.drawImage(grid, (canvasWidth / 2 - zoom / 2) % zoom - zoom,
                            (canvasHeight / 2 - zoom / 2) % zoom - zoom);
                    e.gc.setAlpha(255);

                    e.gc.setForeground(crosshairColor);
                    e.gc.drawLine(0, canvasHeight / 2, canvasWidth - 1, canvasHeight / 2);
                    e.gc.drawLine(canvasWidth / 2, 0, canvasWidth / 2, canvasHeight - 1);
                }
            }
        }
    };

    private void doRedraw() {
        Display.getDefault().asyncExec(new Runnable() {
            public void run() {
                redraw();
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
    }

    public void imageLoaded() {
        synchronized (this) {
            loadImage();
            crosshairLocation = model.getCrosshairLocation();
            zoom = model.getZoom();
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
        // pass
    }

    public void focusChanged() {
        imageChanged();
    }

    public void zoomChanged() {
        synchronized (this) {
            if (grid != null) {
                // To notify that the zoom level has changed, we get rid of the
                // grid.
                grid.dispose();
                grid = null;
                zoom = model.getZoom();
            }
        }
        doRedraw();
    }
}
