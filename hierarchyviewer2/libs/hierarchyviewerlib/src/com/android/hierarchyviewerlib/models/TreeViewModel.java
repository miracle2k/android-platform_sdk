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
import com.android.hierarchyviewerlib.ui.util.DrawableViewNode;
import com.android.hierarchyviewerlib.ui.util.DrawableViewNode.Point;
import com.android.hierarchyviewerlib.ui.util.DrawableViewNode.Rectangle;

import java.util.ArrayList;

public class TreeViewModel {
    public static final double MAX_ZOOM = 2;

    public static final double MIN_ZOOM = 0.2;

    private Window mWindow;

    private DrawableViewNode mTree;

    private DrawableViewNode mSelectedNode;

    private Rectangle mViewport;

    private double mZoom;

    private final ArrayList<ITreeChangeListener> mTreeChangeListeners =
            new ArrayList<ITreeChangeListener>();

    private static TreeViewModel sModel;

    public static TreeViewModel getModel() {
        if (sModel == null) {
            sModel = new TreeViewModel();
        }
        return sModel;
    }

    public void setData(Window window, ViewNode viewNode) {
        synchronized (this) {
            if (mTree != null) {
                mTree.viewNode.dispose();
            }
            this.mWindow = window;
            if (viewNode == null) {
                mTree = null;
            } else {
                mTree = new DrawableViewNode(viewNode);
                mTree.setLeft();
                mTree.placeRoot();
            }
            mViewport = null;
            mZoom = 1;
            mSelectedNode = null;
        }
        notifyTreeChanged();
    }

    public void setSelection(DrawableViewNode selectedNode) {
        synchronized (this) {
            this.mSelectedNode = selectedNode;
        }
        notifySelectionChanged();
    }

    public void setViewport(Rectangle viewport) {
        synchronized (this) {
            this.mViewport = viewport;
        }
        notifyViewportChanged();
    }

    public void setZoom(double newZoom) {
        Point zoomPoint = null;
        synchronized (this) {
            if (mTree != null && mViewport != null) {
                zoomPoint =
                        new Point(mViewport.x + mViewport.width / 2, mViewport.y + mViewport.height / 2);
            }
        }
        zoomOnPoint(newZoom, zoomPoint);
    }

    public void zoomOnPoint(double newZoom, Point zoomPoint) {
        synchronized (this) {
            if (mTree != null && this.mViewport != null) {
                if (newZoom < MIN_ZOOM) {
                    newZoom = MIN_ZOOM;
                }
                if (newZoom > MAX_ZOOM) {
                    newZoom = MAX_ZOOM;
                }
                mViewport.x = zoomPoint.x - (zoomPoint.x - mViewport.x) * mZoom / newZoom;
                mViewport.y = zoomPoint.y - (zoomPoint.y - mViewport.y) * mZoom / newZoom;
                mViewport.width = mViewport.width * mZoom / newZoom;
                mViewport.height = mViewport.height * mZoom / newZoom;
                mZoom = newZoom;
            }
        }
        notifyZoomChanged();
    }

    public DrawableViewNode getTree() {
        synchronized (this) {
            return mTree;
        }
    }

    public Window getWindow() {
        synchronized (this) {
            return mWindow;
        }
    }

    public Rectangle getViewport() {
        synchronized (this) {
            return mViewport;
        }
    }

    public double getZoom() {
        synchronized (this) {
            return mZoom;
        }
    }

    public DrawableViewNode getSelection() {
        synchronized (this) {
            return mSelectedNode;
        }
    }

    public static interface ITreeChangeListener {
        public void treeChanged();

        public void selectionChanged();

        public void viewportChanged();

        public void zoomChanged();
    }

    private ITreeChangeListener[] getTreeChangeListenerList() {
        ITreeChangeListener[] listeners = null;
        synchronized (mTreeChangeListeners) {
            if (mTreeChangeListeners.size() == 0) {
                return null;
            }
            listeners =
                    mTreeChangeListeners.toArray(new ITreeChangeListener[mTreeChangeListeners.size()]);
        }
        return listeners;
    }

    public void notifyTreeChanged() {
        ITreeChangeListener[] listeners = getTreeChangeListenerList();
        if (listeners != null) {
            for (int i = 0; i < listeners.length; i++) {
                listeners[i].treeChanged();
            }
        }
    }

    public void notifySelectionChanged() {
        ITreeChangeListener[] listeners = getTreeChangeListenerList();
        if (listeners != null) {
            for (int i = 0; i < listeners.length; i++) {
                listeners[i].selectionChanged();
            }
        }
    }

    public void notifyViewportChanged() {
        ITreeChangeListener[] listeners = getTreeChangeListenerList();
        if (listeners != null) {
            for (int i = 0; i < listeners.length; i++) {
                listeners[i].viewportChanged();
            }
        }
    }

    public void notifyZoomChanged() {
        ITreeChangeListener[] listeners = getTreeChangeListenerList();
        if (listeners != null) {
            for (int i = 0; i < listeners.length; i++) {
                listeners[i].zoomChanged();
            }
        }
    }

    public void addTreeChangeListener(ITreeChangeListener listener) {
        synchronized (mTreeChangeListeners) {
            mTreeChangeListeners.add(listener);
        }
    }

    public void removeTreeChangeListener(ITreeChangeListener listener) {
        synchronized (mTreeChangeListeners) {
            mTreeChangeListeners.remove(listener);
        }
    }
}
