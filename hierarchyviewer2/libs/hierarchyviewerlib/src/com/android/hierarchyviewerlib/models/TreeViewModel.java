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

import com.android.hierarchyviewerlib.device.ViewNode;
import com.android.hierarchyviewerlib.device.Window;
import com.android.hierarchyviewerlib.scene.DrawableViewNode;
import com.android.hierarchyviewerlib.scene.DrawableViewNode.Point;
import com.android.hierarchyviewerlib.scene.DrawableViewNode.Rectangle;

import java.util.ArrayList;

public class TreeViewModel {
    public static final double MAX_ZOOM = 2;

    public static final double MIN_ZOOM = 0.2;

    private Window window;

    private DrawableViewNode tree;

    private Rectangle viewport;

    private double zoom;

    private final ArrayList<TreeChangeListener> treeChangeListeners =
            new ArrayList<TreeChangeListener>();

    public void setData(Window window, ViewNode viewNode) {
        synchronized (this) {
            this.window = window;
            tree = new DrawableViewNode(viewNode);
            tree.setLeft();
            tree.placeRoot();
            viewport = null;
            zoom = 1;
        }
        notifyTreeChanged();
    }

    public void setViewport(Rectangle viewport) {
        synchronized (this) {
            this.viewport = viewport;
        }
        notifyViewportChanged();
    }

    public void setZoom(double newZoom) {
        Point zoomPoint = null;
        synchronized (this) {
            if (tree != null && viewport != null) {
                zoomPoint =
                        new Point(viewport.x + viewport.width / 2, viewport.y + viewport.height / 2);
            }
        }
        zoomOnPoint(newZoom, zoomPoint);
    }

    public void zoomOnPoint(double newZoom, Point zoomPoint) {
        synchronized (this) {
            if (tree != null && this.viewport != null) {
                if (newZoom < MIN_ZOOM) {
                    newZoom = MIN_ZOOM;
                }
                if (newZoom > MAX_ZOOM) {
                    newZoom = MAX_ZOOM;
                }
                viewport.x = zoomPoint.x - (zoomPoint.x - viewport.x) * zoom / newZoom;
                viewport.y = zoomPoint.y - (zoomPoint.y - viewport.y) * zoom / newZoom;
                viewport.width = viewport.width * zoom / newZoom;
                viewport.height = viewport.height * zoom / newZoom;
                zoom = newZoom;
            }
        }
        notifyZoomChanged();
    }

    public DrawableViewNode getTree() {
        synchronized (this) {
            return tree;
        }
    }

    public Window getWindow() {
        synchronized (this) {
            return window;
        }
    }

    public Rectangle getViewport() {
        synchronized (this) {
            return viewport;
        }
    }

    public double getZoom() {
        synchronized (this) {
            return zoom;
        }
    }

    public static interface TreeChangeListener {
        public void treeChanged();

        public void viewportChanged();

        public void zoomChanged();
    }

    private TreeChangeListener[] getTreeChangeListenerList() {
        TreeChangeListener[] listeners = null;
        synchronized (treeChangeListeners) {
            if (treeChangeListeners.size() == 0) {
                return null;
            }
            listeners =
                    treeChangeListeners.toArray(new TreeChangeListener[treeChangeListeners.size()]);
        }
        return listeners;
    }

    public void notifyTreeChanged() {
        TreeChangeListener[] listeners = getTreeChangeListenerList();
        if (listeners != null) {
            for (int i = 0; i < listeners.length; i++) {
                listeners[i].treeChanged();
            }
        }
    }

    public void notifyViewportChanged() {
        TreeChangeListener[] listeners = getTreeChangeListenerList();
        if (listeners != null) {
            for (int i = 0; i < listeners.length; i++) {
                listeners[i].viewportChanged();
            }
        }
    }

    public void notifyZoomChanged() {
        TreeChangeListener[] listeners = getTreeChangeListenerList();
        if (listeners != null) {
            for (int i = 0; i < listeners.length; i++) {
                listeners[i].zoomChanged();
            }
        }
    }

    public void addTreeChangeListener(TreeChangeListener listener) {
        synchronized (treeChangeListeners) {
            treeChangeListeners.add(listener);
        }
    }

    public void removeTreeChangeListener(TreeChangeListener listener) {
        synchronized (treeChangeListeners) {
            treeChangeListeners.remove(listener);
        }
    }
}
