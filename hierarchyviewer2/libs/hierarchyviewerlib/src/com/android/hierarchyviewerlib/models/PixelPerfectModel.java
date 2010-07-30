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
import com.android.ddmlib.RawImage;
import com.android.hierarchyviewerlib.device.ViewNode;

import java.util.ArrayList;

public class PixelPerfectModel {

    public static class Point {
        public int x;

        public int y;

        Point(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    private IDevice device;

    private RawImage image;

    private Point crosshairLocation;

    private ViewNode viewNode;

    private ViewNode selected;

    private final ArrayList<ImageChangeListener> imageChangeListeners =
            new ArrayList<ImageChangeListener>();

    public void setData(IDevice device, RawImage image, ViewNode viewNode) {
        synchronized (this) {
            this.device = device;
            this.image = image;
            this.viewNode = viewNode;
            this.crosshairLocation = new Point(image.width / 2, image.height / 2);
            this.selected = null;
        }
        notifyImageLoaded();
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

    public void setFocusData(RawImage image, ViewNode viewNode) {
        synchronized (this) {
            this.image = image;
            this.viewNode = viewNode;
            this.selected = null;
        }
        notifyFocusChanged();
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

    public RawImage getImage() {
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

    public static interface ImageChangeListener {
        public void imageLoaded();

        public void imageChanged();

        public void crosshairMoved();

        public void selectionChanged();

        public void focusChanged();
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

    public void notifyFocusChanged() {
        ImageChangeListener[] listeners = getImageChangeListenerList();
        if (listeners != null) {
            for (int i = 0; i < listeners.length; i++) {
                listeners[i].focusChanged();
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
