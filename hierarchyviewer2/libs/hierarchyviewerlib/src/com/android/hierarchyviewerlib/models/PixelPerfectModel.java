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

package com.android.hierarchyviewerlib.models;

import com.android.ddmlib.IDevice;
import com.android.hierarchyviewerlib.device.ViewNode;

import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Display;

import java.util.ArrayList;

public class PixelPerfectModel {

    public static final int MIN_ZOOM = 2;

    public static final int MAX_ZOOM = 24;

    public static final int DEFAULT_ZOOM = 8;

    private IDevice device;

    private Image image;

    private Point crosshairLocation;

    private ViewNode viewNode;

    private ViewNode selected;

    private int zoom;

    private final ArrayList<ImageChangeListener> imageChangeListeners =
            new ArrayList<ImageChangeListener>();

    private Image overlayImage;

    private double overlayTransparency = 0.5;

    private static PixelPerfectModel model;

    public static PixelPerfectModel getModel() {
        if (model == null) {
            model = new PixelPerfectModel();
        }
        return model;
    }

    public void setData(final IDevice device, final Image image, final ViewNode viewNode) {
        final Image toDispose = this.image;
        final Image toDispose2 = this.overlayImage;
        Display.getDefault().syncExec(new Runnable() {
            public void run() {
                synchronized (PixelPerfectModel.this) {
                    PixelPerfectModel.this.device = device;
                    PixelPerfectModel.this.image = image;
                    PixelPerfectModel.this.viewNode = viewNode;
                    if (image != null) {
                        PixelPerfectModel.this.crosshairLocation =
                                new Point(image.getBounds().width / 2, image.getBounds().height / 2);
                    } else {
                        PixelPerfectModel.this.crosshairLocation = null;
                    }
                    overlayImage = null;
                    PixelPerfectModel.this.selected = null;
                    zoom = DEFAULT_ZOOM;
                }
            }
        });
        notifyImageLoaded();
        if (toDispose != null) {
            Display.getDefault().syncExec(new Runnable() {
                public void run() {
                    toDispose.dispose();
                }
            });
        }
        if (toDispose2 != null) {
            Display.getDefault().syncExec(new Runnable() {
                public void run() {
                    toDispose2.dispose();
                }
            });
        }

    }

    public void setCrosshairLocation(int x, int y) {
        synchronized (this) {
            crosshairLocation = new Point(x, y);
        }
        notifyCrosshairMoved();
    }

    public void setSelected(ViewNode selected) {
        synchronized (this) {
            this.selected = selected;
        }
        notifySelectionChanged();
    }

    public void setTree(final ViewNode viewNode) {
        Display.getDefault().syncExec(new Runnable() {
            public void run() {
                synchronized (PixelPerfectModel.this) {
                    PixelPerfectModel.this.viewNode = viewNode;
                    PixelPerfectModel.this.selected = null;
                }
            }
        });
        notifyTreeChanged();
    }

    public void setImage(final Image image) {
        final Image toDispose = this.image;
        Display.getDefault().syncExec(new Runnable() {
            public void run() {
                synchronized (PixelPerfectModel.this) {
                    PixelPerfectModel.this.image = image;
                }
            }
        });
        notifyImageChanged();
        if (toDispose != null) {
            Display.getDefault().syncExec(new Runnable() {
                public void run() {
                    toDispose.dispose();
                }
            });
        }
    }

    public void setZoom(int newZoom) {
        synchronized (this) {
            if (newZoom < MIN_ZOOM) {
                newZoom = MIN_ZOOM;
            }
            if (newZoom > MAX_ZOOM) {
                newZoom = MAX_ZOOM;
            }
            zoom = newZoom;
        }
        notifyZoomChanged();
    }

    public void setOverlayImage(final Image overlayImage) {
        final Image toDispose = this.overlayImage;
        Display.getDefault().syncExec(new Runnable() {
            public void run() {
                synchronized (PixelPerfectModel.this) {
                    PixelPerfectModel.this.overlayImage = overlayImage;
                }
            }
        });
        notifyOverlayChanged();
        if (toDispose != null) {
            Display.getDefault().syncExec(new Runnable() {
                public void run() {
                    toDispose.dispose();
                }
            });
        }
    }

    public void setOverlayTransparency(double value) {
        synchronized (this) {
            value = Math.max(value, 0);
            value = Math.min(value, 1);
            overlayTransparency = value;
        }
        notifyOverlayTransparencyChanged();
    }

    public ViewNode getViewNode() {
        synchronized (this) {
            return viewNode;
        }
    }

    public Point getCrosshairLocation() {
        synchronized (this) {
            return crosshairLocation;
        }
    }

    public Image getImage() {
        synchronized (this) {
            return image;
        }
    }

    public ViewNode getSelected() {
        synchronized (this) {
            return selected;
        }
    }

    public IDevice getDevice() {
        synchronized (this) {
            return device;
        }
    }

    public int getZoom() {
        synchronized (this) {
            return zoom;
        }
    }

    public Image getOverlayImage() {
        synchronized (this) {
            return overlayImage;
        }
    }

    public double getOverlayTransparency() {
        synchronized (this) {
            return overlayTransparency;
        }
    }

    public static interface ImageChangeListener {
        public void imageLoaded();

        public void imageChanged();

        public void crosshairMoved();

        public void selectionChanged();

        public void treeChanged();

        public void zoomChanged();

        public void overlayChanged();

        public void overlayTransparencyChanged();
    }

    private ImageChangeListener[] getImageChangeListenerList() {
        ImageChangeListener[] listeners = null;
        synchronized (imageChangeListeners) {
            if (imageChangeListeners.size() == 0) {
                return null;
            }
            listeners =
                    imageChangeListeners.toArray(new ImageChangeListener[imageChangeListeners
                            .size()]);
        }
        return listeners;
    }

    public void notifyImageLoaded() {
        ImageChangeListener[] listeners = getImageChangeListenerList();
        if (listeners != null) {
            for (int i = 0; i < listeners.length; i++) {
                listeners[i].imageLoaded();
            }
        }
    }

    public void notifyImageChanged() {
        ImageChangeListener[] listeners = getImageChangeListenerList();
        if (listeners != null) {
            for (int i = 0; i < listeners.length; i++) {
                listeners[i].imageChanged();
            }
        }
    }

    public void notifyCrosshairMoved() {
        ImageChangeListener[] listeners = getImageChangeListenerList();
        if (listeners != null) {
            for (int i = 0; i < listeners.length; i++) {
                listeners[i].crosshairMoved();
            }
        }
    }

    public void notifySelectionChanged() {
        ImageChangeListener[] listeners = getImageChangeListenerList();
        if (listeners != null) {
            for (int i = 0; i < listeners.length; i++) {
                listeners[i].selectionChanged();
            }
        }
    }

    public void notifyTreeChanged() {
        ImageChangeListener[] listeners = getImageChangeListenerList();
        if (listeners != null) {
            for (int i = 0; i < listeners.length; i++) {
                listeners[i].treeChanged();
            }
        }
    }

    public void notifyZoomChanged() {
        ImageChangeListener[] listeners = getImageChangeListenerList();
        if (listeners != null) {
            for (int i = 0; i < listeners.length; i++) {
                listeners[i].zoomChanged();
            }
        }
    }

    public void notifyOverlayChanged() {
        ImageChangeListener[] listeners = getImageChangeListenerList();
        if (listeners != null) {
            for (int i = 0; i < listeners.length; i++) {
                listeners[i].overlayChanged();
            }
        }
    }

    public void notifyOverlayTransparencyChanged() {
        ImageChangeListener[] listeners = getImageChangeListenerList();
        if (listeners != null) {
            for (int i = 0; i < listeners.length; i++) {
                listeners[i].overlayTransparencyChanged();
            }
        }
    }

    public void addImageChangeListener(ImageChangeListener listener) {
        synchronized (imageChangeListeners) {
            imageChangeListeners.add(listener);
        }
    }

    public void removeImageChangeListener(ImageChangeListener listener) {
        synchronized (imageChangeListeners) {
            imageChangeListeners.remove(listener);
        }
    }
}
