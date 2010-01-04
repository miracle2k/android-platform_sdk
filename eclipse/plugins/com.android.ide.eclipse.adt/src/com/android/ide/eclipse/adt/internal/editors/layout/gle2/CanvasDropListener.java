/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.eclipse.org/org/documents/epl-v10.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.ide.eclipse.adt.internal.editors.layout.gle2;

import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.gscripts.DropZone;
import com.android.ide.eclipse.adt.internal.editors.layout.gre.NodeProxy;

import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.dnd.DropTargetListener;
import org.eclipse.swt.dnd.TransferData;
import org.eclipse.swt.graphics.Point;

import java.util.ArrayList;

/**
 * Handles drop operations on top of the canvas.
 * <p/>
 * Reference for d'n'd: http://www.eclipse.org/articles/Article-SWT-DND/DND-in-SWT.html
 */
/* package */ class CanvasDropListener implements DropTargetListener {

    private final LayoutCanvas mCanvas;
    private CanvasViewInfo mCurrentView;
    private DropZone mCurrentZone;
    private ArrayList<DropZone> mZones;

    public CanvasDropListener(LayoutCanvas canvas) {
        mCanvas = canvas;
    }

    public CanvasViewInfo getCurrentView() {
        return mCurrentView;
    }

    public ArrayList<DropZone> getZones() {
        return mZones;
    }

    public DropZone getCurrentZone() {
        return mCurrentZone;
    }

    /*
     * The cursor has entered the drop target boundaries.
     * {@inheritDoc}
     */
    public void dragEnter(DropTargetEvent event) {
        AdtPlugin.printErrorToConsole("DEBUG", "drag enter", event);

        checkDataType(event);

        if (event.detail == DND.DROP_DEFAULT) {
            if ((event.operations & DND.DROP_COPY) != 0) {
                event.detail = DND.DROP_COPY;
            }
        }

        if (event.detail == DND.DROP_COPY) {
            processDropEvent(event);
        } else {
            event.detail = DND.DROP_NONE;
            clearDropInfo();
        }
    }

    /*
     * The operation being performed has changed (e.g. modifier key).
     * {@inheritDoc}
     */
    public void dragOperationChanged(DropTargetEvent event) {
        AdtPlugin.printErrorToConsole("DEBUG", "drag changed", event);

        checkDataType(event);

        if (event.detail == DND.DROP_DEFAULT) {
            if ((event.operations & DND.DROP_COPY) != 0) {
                event.detail = DND.DROP_COPY;
            }
        }

        if (event.detail == DND.DROP_COPY) {
            processDropEvent(event);
        } else {
            event.detail = DND.DROP_NONE;
            clearDropInfo();
        }
    }

    /*
     * The cursor has left the drop target boundaries.
     * {@inheritDoc}
     */
    public void dragLeave(DropTargetEvent event) {
        AdtPlugin.printErrorToConsole("DEBUG", "drag leave");
        clearDropInfo();
    }

    /*
     * The cursor is moving over the drop target.
     * {@inheritDoc}
     */
    public void dragOver(DropTargetEvent event) {
        processDropEvent(event);
    }

    /*
     * The drop is about to be performed.
     * The drop target is given a last chance to change the nature of the drop.
     * {@inheritDoc}
     */
    public void dropAccept(DropTargetEvent event) {
        AdtPlugin.printErrorToConsole("DEBUG", "drop accept");

        checkDataType(event);

        if (event.detail != DND.DROP_NONE) {
            processDropEvent(event);
        }
    }

    /*
     * The data is being dropped.
     * {@inheritDoc}
     */
    public void drop(DropTargetEvent event) {
        // TODO Auto-generated method stub
        AdtPlugin.printErrorToConsole("DEBUG", "drop");
    }

    /**
     * Verifies that event.currentDataType is of type {@link ElementDescTransfer}.
     * If not, try to find a valid data type.
     * Otherwise set the drop to {@link DND#DROP_NONE} to cancel it.
     *
     * @return True if the data type is accepted.
     */
    private boolean checkDataType(DropTargetEvent event) {

        ElementDescTransfer edt = ElementDescTransfer.getInstance();

        TransferData current = event.currentDataType;

        if (edt.isSupportedType(current)) {
            return true;
        }

        // We only support ElementDescTransfer and the current data type is not right.
        // Let's see if we can find another one.

        for (TransferData td : event.dataTypes) {
            if (td != current && edt.isSupportedType(td)) {
                // We like this type better.
                event.currentDataType = td;
                return true;
            }
        }

        // We failed to find any good transfer type.
        event.detail = DND.DROP_NONE;
        return false;
    }

    private void processDropEvent(DropTargetEvent event) {
        if (!mCanvas.isResultValid()) {
            // We don't allow drop on an invalid layout, even if we have some obsolete
            // layout info for it.
            event.detail = DND.DROP_NONE;
            clearDropInfo();
            return;
        }

        int x = event.x;
        int y = event.y;
        Point p = mCanvas.toControl(x, y);
        x = p.x - LayoutCanvas.IMAGE_MARGIN;
        y = p.y - LayoutCanvas.IMAGE_MARGIN;

        CanvasViewInfo vi = mCanvas.findViewInfoAt(x, y);

        boolean needRedraw = false;

        if (vi != mCurrentView) {
            setCurrentView(vi);
            needRedraw = true;
        }

        if (mZones != null) {
            if (mCurrentZone == null || !mCurrentZone.bounds.contains(x, y)) {
                // If there is no current zone or it doesn't contain the current point,
                // try to find one.
                for (DropZone z : mZones) {
                    if (z.bounds.contains(x, y)) {
                        mCurrentZone = z;
                        needRedraw = true;
                        break;
                    }
                }
            }
        }

        if (needRedraw) {
            mCanvas.redraw();
        }
    }

    private void setCurrentView(CanvasViewInfo vi) {
        // We switched to a new view.
        mCurrentView = vi;
        mCurrentZone = null;
        mZones = null;

        if (vi != null) {
            // Query GRE for drop zones
            NodeProxy target = new NodeProxy(vi.getUiViewKey(), vi.getAbsRect());
            mZones = mCanvas.getRulesEngine().dropStart(target);
            AdtPlugin.printErrorToConsole("ZONES", mZones);
        }

        mCanvas.redraw();
    }

    private void clearDropInfo() {
        if (mCurrentView != null) {
            mCurrentView = null;
            mCurrentZone = null;
            mZones = null;
            mCanvas.redraw();
        }
    }


}
