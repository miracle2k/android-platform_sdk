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
import com.android.ide.eclipse.adt.editors.layout.gscripts.DropFeedback;
import com.android.ide.eclipse.adt.editors.layout.gscripts.Point;
import com.android.ide.eclipse.adt.editors.layout.gscripts.Rect;
import com.android.ide.eclipse.adt.internal.editors.layout.gre.NodeProxy;

import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.dnd.DropTargetListener;
import org.eclipse.swt.dnd.TransferData;

/**
 * Handles drop operations on top of the canvas.
 * <p/>
 * Reference for d'n'd: http://www.eclipse.org/articles/Article-SWT-DND/DND-in-SWT.html
 */
/* package */ class CanvasDropListener implements DropTargetListener {

    private final LayoutCanvas mCanvas;

    /**
     * The top view right under the drag'n'drop cursor.
     * This can only be null during a drag'n'drop when there is no view under the cursor
     * or after the state was all cleared.
     */
    private CanvasViewInfo mCurrentView;

    /**
     * The FQCN currently being dragged. This will always be non-null for a valid
     * drag'n'drop that happens within the same instance of Eclipse.
     * <p/>
     * In the event that the drag and drop happens between different instances of Eclipse
     * this will remain null.
     */
    private String mCurrentDragFqcn;

    /**
     * The first view under the cursor that responded to onDropEnter is called the "target view".
     * It can differ from mCurrentView, typically because a terminal View doesn't
     * accept drag'n'drop so its parent layout became the target drag'n'drop receiver.
     * <p/>
     * The target node is the proxy node associated with the target view.
     * This can be null if no view under the cursor accepted the drag'n'drop or if the node
     * factory couldn't create a proxy for it.
     */
    private NodeProxy mTargetNode;

    /**
     * The latest drop feedback returned by IViewRule.onDropEnter/Move.
     */
    private DropFeedback mFeedback;

    /**
     * {@link #dragLeave(DropTargetEvent)} is unfortunately called right before data is
     * about to be dropped (between the last {@link #dragOver(DropTargetEvent)} and the
     * next {@link #dropAccept(DropTargetEvent)}). That means we can't just
     * trash the current DropFeedback from the current view rule in dragLeave().
     * Instead we preserve it in mLeaveTargetNode and mLeaveFeedback in case a dropAccept
     * happens next.
     */
    private NodeProxy mLeaveTargetNode;
    /**
     * @see #mLeaveTargetNode
     */
    private DropFeedback mLeaveFeedback;

    public CanvasDropListener(LayoutCanvas canvas) {
        mCanvas = canvas;
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

            // Get the dragged FQCN.
            // The current transfered type can be extracted from the event.
            // As described in dragOver(), this works basically works on Windows but
            // not on Linux or Mac, in which case we can't get the type until we
            // receive dropAccept/drop().
            // For consistency we try to use the GlobalCanvasDragInfo instance first,
            // and if it fails we use the event transfer type as a backup (but as said
            // before it will most likely work only on Windows.)
            // In any case this can be null even for a valid transfer.

            mCurrentDragFqcn = GlobalCanvasDragInfo.getInstance().getCurrentFqcn();

            ElementDescTransfer edt = ElementDescTransfer.getInstance();
            if (edt.isSupportedType(event.currentDataType)) {
                String data = (String) edt.nativeToJava(event.currentDataType);
                if (data != null) {
                    if (mCurrentDragFqcn == null) {
                        mCurrentDragFqcn = data;

                    } else if (!mCurrentDragFqcn.equals(data)) {
                        // Mostly for debugging purposes we compare them. They should always match.
                        // TODO use an error for right now. Later move this to a log warning only.
                        AdtPlugin.logAndPrintError(null /*exception*/, "CanvasDrop",
                                "TransferType mismatch: Global=%s, drag.event=%s",
                                mCurrentDragFqcn, data);
                    }
                }
            }

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
     * The cursor has left the drop target boundaries OR data is about to be dropped.
     * {@inheritDoc}
     */
    public void dragLeave(DropTargetEvent event) {
        AdtPlugin.printErrorToConsole("DEBUG", "drag leave");

        // dragLeave is unfortunately called right before data is about to be dropped
        // (between the last dropMove and the next dropAccept). That means we can't just
        // trash the current DropFeedback from the current view rule, we need to preserve
        // it in case a dropAccept happens next.
        // See the corresponding kludge in drop().
        mLeaveTargetNode = mTargetNode;
        mLeaveFeedback = mFeedback;

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
        AdtPlugin.printErrorToConsole("DEBUG", "dropped");

        if (mTargetNode == null) {
            // DEBUG
            AdtPlugin.printErrorToConsole("DEBUG", "dropped on null targetNode");
            return;
        }

        // If we have a valid target node and it matches the one we saved in
        // dragLeave then we restore the DropFeedback that we saved in dragLeave.
        if (mTargetNode == mLeaveTargetNode) {
            mFeedback = mLeaveFeedback;
        }
        mLeaveTargetNode = null;
        mLeaveFeedback = null;

        String viewFqcn = null;

        ElementDescTransfer edt = ElementDescTransfer.getInstance();

        if (edt.isSupportedType(event.currentDataType)) {
            // DropTarget already invoked Tranfer#nativeToJava() and stored the result
            // in event.data
            if (event.data instanceof String) {
                viewFqcn = (String) event.data;
            }
        }

        if (viewFqcn == null) {
            AdtPlugin.printErrorToConsole("DEBUG", "drop missing drop data");
            return;
        }

        Point p = mCanvas.displayToCanvasPoint(event.x, event.y);
        mCanvas.getRulesEngine().callOnDropped(mTargetNode, viewFqcn, mFeedback, p);

        clearDropInfo();
    }

    /**
     * Invoked by the canvas to refresh the display.
     * @param gCWrapper The GC wrapper, never null.
     */
    public void paintFeedback(GCWrapper gCWrapper) {
        if (mTargetNode != null && mFeedback != null && mFeedback.requestPaint) {
            mFeedback.requestPaint = false;
            mCanvas.getRulesEngine().callDropFeedbackPaint(gCWrapper, mTargetNode, mFeedback);
        }
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

    /**
     * Called on both dragEnter and dragMove.
     * Generates the onDropEnter/Move/Leave events depending on the currently
     * selected target node.
     */
    private void processDropEvent(DropTargetEvent event) {
        if (!mCanvas.isResultValid()) {
            // We don't allow drop on an invalid layout, even if we have some obsolete
            // layout info for it.
            event.detail = DND.DROP_NONE;
            clearDropInfo();
            return;
        }

        Point p = mCanvas.displayToCanvasPoint(event.x, event.y);
        int x = p.x;
        int y = p.y;

        // Is the mouse currently captured by a DropFeedback.captureArea?
        boolean isCaptured = false;
        if (mFeedback != null) {
            Rect r = mFeedback.captureArea;
            isCaptured = r != null && r.contains(x, y);
        }

        // We can't switch views/nodes when the mouse is captured
        CanvasViewInfo vi;
        if (isCaptured) {
            vi = mCurrentView;
        } else {
            vi = mCanvas.findViewInfoAt(x, y);
        }

        boolean isMove = true;
        boolean needRedraw = false;

        if (vi != mCurrentView) {
            // Current view has changed. Does that also change the target node?
            // Note that either mCurrentView or vi can be null.

            if (vi == null) {
                // vi is null but mCurrentView is not, no view is a target anymore
                callDropLeave();

                // We don't need onDropMove in this case
                isMove = false;
                needRedraw = true;

            } else {
                // vi is a new current view.
                // Query GRE for onDropEnter on the view till we find one that returns a non-null
                // object.

                DropFeedback df = null;
                NodeProxy targetNode = null;

                for (CanvasViewInfo targetVi = vi;
                        targetVi != null && df == null;
                        targetVi = targetVi.getParent()) {
                    targetNode = mCanvas.getNodeFactory().create(targetVi);
                    df = mCanvas.getRulesEngine().callOnDropEnter(targetNode, mCurrentDragFqcn);
                }

                if (df != null && targetNode != mTargetNode) {
                    // We found a new target node for the drag'n'drop.
                    // Release the previous one, if any.
                    callDropLeave();

                    // And assign the new one
                    mTargetNode = targetNode;
                    mFeedback = df;

                    // We don't need onDropMove in this case
                    isMove = false;
                }
            }

            mCurrentView = vi;
        }

        if (isMove && mTargetNode != null && mFeedback != null) {
            // this is a move inside the same view
            com.android.ide.eclipse.adt.editors.layout.gscripts.Point p2 =
                new com.android.ide.eclipse.adt.editors.layout.gscripts.Point(x, y);
            DropFeedback df = mCanvas.getRulesEngine().callOnDropMove(
                    mTargetNode, mCurrentDragFqcn, mFeedback, p2);
            if (df == null) {
                // The target is no longer interested in the drop move.
                callDropLeave();
            }
        }

        if (needRedraw || (mFeedback != null && mFeedback.requestPaint)) {
            mCanvas.redraw();
        }
    }

    /**
     * Calls onDropLeave on mTargetNode with the current mFeedback. <br/>
     * Then clears mTargetNode and mFeedback.
     */
    private void callDropLeave() {
        if (mTargetNode != null && mFeedback != null) {
            mCanvas.getRulesEngine().callOnDropLeave(mTargetNode, mCurrentDragFqcn, mFeedback);
        }

        mTargetNode = null;
        mFeedback = null;
    }

    private void clearDropInfo() {
        callDropLeave();
        mCurrentView = null;
        mCanvas.redraw();
    }

}
