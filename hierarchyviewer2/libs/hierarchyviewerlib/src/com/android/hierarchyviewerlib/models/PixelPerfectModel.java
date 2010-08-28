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

    public static final int DEFAULT_OVERLAY_TRANSPARENCY_PERCENTAGE = 50;

    private IDevice mDevice;

    private Image mImage;

    private Point mCrosshairLocation;

    private ViewNode mViewNode;

    private ViewNode mSelectedNode;

    private int mZoom;

    private final ArrayList<IImageChangeListener> mImageChangeListeners =
            new ArrayList<IImageChangeListener>();

    private Image mOverlayImage;

    private double mOverlayTransparency = DEFAULT_OVERLAY_TRANSPARENCY_PERCENTAGE / 100.0;

    private static PixelPerfectModel sModel;

    public static PixelPerfectModel getModel() {
        if (sModel == null) {
            sModel = new PixelPerfectModel();
        }
        return sModel;
    }

    public void setData(final IDevice device, final Image image, final ViewNode viewNode) {
        final Image toDispose = this.mImage;
        final Image toDispose2 = this.mOverlayImage;
        Display.getDefault().syncExec(new Runnable() {
            public void run() {
                synchronized (PixelPerfectModel.this) {
                    PixelPerfectModel.this.mDevice = device;
                    PixelPerfectModel.this.mImage = image;
                    PixelPerfectModel.this.mViewNode = viewNode;
                    if (image != null) {
                        PixelPerfectModel.this.mCrosshairLocation =
                                new Point(image.getBounds().width / 2, image.getBounds().height / 2);
                    } else {
                        PixelPerfectModel.this.mCrosshairLocation = null;
                    }
                    mOverlayImage = null;
                    PixelPerfectModel.this.mSelectedNode = null;
                    mZoom = DEFAULT_ZOOM;
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
            mCrosshairLocation = new Point(x, y);
        }
        notifyCrosshairMoved();
    }

    public void setSelected(ViewNode selected) {
        synchronized (this) {
            this.mSelectedNode = selected;
        }
        notifySelectionChanged();
    }

    public void setTree(final ViewNode viewNode) {
        Display.getDefault().syncExec(new Runnable() {
            public void run() {
                synchronized (PixelPerfectModel.this) {
                    PixelPerfectModel.this.mViewNode = viewNode;
                    PixelPerfectModel.this.mSelectedNode = null;
                }
            }
        });
        notifyTreeChanged();
    }

    public void setImage(final Image image) {
        final Image toDispose = this.mImage;
        Display.getDefault().syncExec(new Runnable() {
            public void run() {
                synchronized (PixelPerfectModel.this) {
                    PixelPerfectModel.this.mImage = image;
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
            mZoom = newZoom;
        }
        notifyZoomChanged();
    }

    public void setOverlayImage(final Image overlayImage) {
        final Image toDispose = this.mOverlayImage;
        Display.getDefault().syncExec(new Runnable() {
            public void run() {
                synchronized (PixelPerfectModel.this) {
                    PixelPerfectModel.this.mOverlayImage = overlayImage;
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
            mOverlayTransparency = value;
        }
        notifyOverlayTransparencyChanged();
    }

    public ViewNode getViewNode() {
        synchronized (this) {
            return mViewNode;
        }
    }

    public Point getCrosshairLocation() {
        synchronized (this) {
            return mCrosshairLocation;
        }
    }

    public Image getImage() {
        synchronized (this) {
            return mImage;
        }
    }

    public ViewNode getSelected() {
        synchronized (this) {
            return mSelectedNode;
        }
    }

    public IDevice getDevice() {
        synchronized (this) {
            return mDevice;
        }
    }

    public int getZoom() {
        synchronized (this) {
            return mZoom;
        }
    }

    public Image getOverlayImage() {
        synchronized (this) {
            return mOverlayImage;
        }
    }

    public double getOverlayTransparency() {
        synchronized (this) {
            return mOverlayTransparency;
        }
    }

    public static interface IImageChangeListener {
        public void imageLoaded();

        public void imageChanged();

        public void crosshairMoved();

        public void selectionChanged();

        public void treeChanged();

        public void zoomChanged();

        public void overlayChanged();

        public void overlayTransparencyChanged();
    }

    private IImageChangeListener[] getImageChangeListenerList() {
        IImageChangeListener[] listeners = null;
        synchronized (mImageChangeListeners) {
            if (mImageChangeListeners.size() == 0) {
                return null;
            }
            listeners =
                    mImageChangeListeners.toArray(new IImageChangeListener[mImageChangeListeners
                            .size()]);
        }
        return listeners;
    }

    public void notifyImageLoaded() {
        IImageChangeListener[] listeners = getImageChangeListenerList();
        if (listeners != null) {
            for (int i = 0; i < listeners.length; i++) {
                listeners[i].imageLoaded();
            }
        }
    }

    public void notifyImageChanged() {
        IImageChangeListener[] listeners = getImageChangeListenerList();
        if (listeners != null) {
            for (int i = 0; i < listeners.length; i++) {
                listeners[i].imageChanged();
            }
        }
    }

    public void notifyCrosshairMoved() {
        IImageChangeListener[] listeners = getImageChangeListenerList();
        if (listeners != null) {
            for (int i = 0; i < listeners.length; i++) {
                listeners[i].crosshairMoved();
            }
        }
    }

    public void notifySelectionChanged() {
        IImageChangeListener[] listeners = getImageChangeListenerList();
        if (listeners != null) {
            for (int i = 0; i < listeners.length; i++) {
                listeners[i].selectionChanged();
            }
        }
    }

    public void notifyTreeChanged() {
        IImageChangeListener[] listeners = getImageChangeListenerList();
        if (listeners != null) {
            for (int i = 0; i < listeners.length; i++) {
                listeners[i].treeChanged();
            }
        }
    }

    public void notifyZoomChanged() {
        IImageChangeListener[] listeners = getImageChangeListenerList();
        if (listeners != null) {
            for (int i = 0; i < listeners.length; i++) {
                listeners[i].zoomChanged();
            }
        }
    }

    public void notifyOverlayChanged() {
        IImageChangeListener[] listeners = getImageChangeListenerList();
        if (listeners != null) {
            for (int i = 0; i < listeners.length; i++) {
                listeners[i].overlayChanged();
            }
        }
    }

    public void notifyOverlayTransparencyChanged() {
        IImageChangeListener[] listeners = getImageChangeListenerList();
        if (listeners != null) {
            for (int i = 0; i < listeners.length; i++) {
                listeners[i].overlayTransparencyChanged();
            }
        }
    }

    public void addImageChangeListener(IImageChangeListener listener) {
        synchronized (mImageChangeListeners) {
            mImageChangeListeners.add(listener);
        }
    }

    public void removeImageChangeListener(IImageChangeListener listener) {
        synchronized (mImageChangeListeners) {
            mImageChangeListeners.remove(listener);
        }
    }
}
