/*
 * Copyright (C) 2010 The Android Open Source Project
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

import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DragSource;
import org.eclipse.swt.dnd.DragSourceEvent;
import org.eclipse.swt.dnd.DragSourceListener;
import org.eclipse.swt.dnd.DropTarget;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.dnd.DropTargetListener;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.MouseMoveListener;
import org.eclipse.swt.events.TypedEvent;
import org.eclipse.swt.graphics.Device;
import org.eclipse.swt.graphics.GC;

import java.util.ArrayList;
import java.util.List;

/**
 * The {@link GestureManager} is is the central manager of gestures; it is responsible
 * for recognizing when particular gestures should begin and terminate. It
 * listens to the drag, mouse and keyboard systems to find out when to start
 * gestures and in order to update the gestures along the way.
 */
public class GestureManager {
    /** The canvas which owns this GestureManager. */
    private final LayoutCanvas mCanvas;

    /** The currently executing gesture, or null. */
    private Gesture mCurrentGesture;

    /** A listener for drop target events. */
    private final DropTargetListener mDropListener = new CanvasDropListener();

    /** A listener for drag source events. */
    private final DragSourceListener mDragSourceListener = new CanvasDragSourceListener();

    /**
     * The list of overlays associated with {@link #mCurrentGesture}. Will be
     * null before it has been initialized lazily by the paint routine (the
     * initialized value can never be null, but it can be an empty collection).
     */
    private List<Overlay> mOverlays;

    /**
     * Most recently seen mouse position (x coordinate). We keep a copy of this
     * value since we sometimes need to know it when we aren't told about the
     * mouse position (such as when a keystroke is received, such as an arrow
     * key in order to tweak the current drop position)
     */
    protected int mLastMouseX;

    /**
     * Most recently seen mouse position (y coordinate). We keep a copy of this
     * value since we sometimes need to know it when we aren't told about the
     * mouse position (such as when a keystroke is received, such as an arrow
     * key in order to tweak the current drop position)
     */
    protected int mLastMouseY;

    /**
     * Most recently seen mouse mask. We keep a copy of this since in some
     * scenarios (such as on a drag gesture) we don't get access to it.
     */
    protected int mLastStateMask;

    /**
     * Listener for mouse motion, click and keyboard events.
     */
    private Listener mListener;

    /**
     * When we the drag leaves, we don't know if that's the last we'll see of
     * this drag or if it's just temporarily outside the canvas and it will
     * return. We want to restore it if it comes back. This is also necessary
     * because even on a drop we'll receive a
     * {@link DropTargetListener#dragLeave} right before the drop, and we need
     * to restore it in the drop. Therefore, when we lose a {@link DropGesture}
     * to a {@link DropTargetListener#dragLeave}, we store a reference to the
     * current gesture as a {@link #mZombieGesture}, since the gesture is dead
     * but might be brought back to life if we see a subsequent
     * {@link DropTargetListener#dragEnter} before another gesture begins.
     */
    private DropGesture mZombieGesture;

    /**
     * Constructs a new {@link GestureManager} for the given
     * {@link LayoutCanvas}.
     *
     * @param canvas The canvas which controls this {@link GestureManager}
     */
    public GestureManager(LayoutCanvas canvas) {
        this.mCanvas = canvas;
    }

    /**
     * Returns the canvas associated with this GestureManager.
     *
     * @return The {@link LayoutCanvas} associated with this GestureManager.
     *         Never null.
     */
    public LayoutCanvas getCanvas() {
        return mCanvas;
    }

    /**
     * Returns the current gesture, if one is in progress, and otherwise returns
     * null.
     *
     * @return The current gesture or null.
     */
    public Gesture getCurrentGesture() {
        return mCurrentGesture;
    }

    /**
     * Paints the overlays associated with the current gesture, if any.
     *
     * @param gc The graphics object to paint into.
     */
    public void paint(GC gc) {
        if (mCurrentGesture == null) {
            return;
        }

        if (mOverlays == null) {
            mOverlays = mCurrentGesture.createOverlays();
            Device device = gc.getDevice();
            for (Overlay overlay : mOverlays) {
                overlay.create(device);
            }
        }
        for (Overlay overlay : mOverlays) {
            overlay.paint(gc);
        }
    }

    /**
     * Returns the {@link DropTargetListener} used by the GestureManager. This
     * is a bit leaky, but the Outline is reusing all this code... This should
     * be separated out.
     */
    /* package */DropTargetListener getDropTargetListener() {
        return mDropListener;
    }

    /**
     * Returns the {@link DragSourceListener} used by the GestureManager. This
     * is a bit leaky, but the Outline is reusing all this code... This should
     * be separated out.
     */
    /* package */DragSourceListener getDragSourceListener() {
        return mDragSourceListener;
    }

    /**
     * Registers all the listeners needed by the {@link GestureManager}.
     *
     * @param dragSource The drag source in the {@link LayoutCanvas} to listen
     *            to.
     * @param dropTarget The drop target in the {@link LayoutCanvas} to listen
     *            to.
     */
    public void registerListeners(DragSource dragSource, DropTarget dropTarget) {
        assert mListener == null;
        mListener = new Listener();
        mCanvas.addMouseMoveListener(mListener);
        mCanvas.addMouseListener(mListener);
        mCanvas.addKeyListener(mListener);

        if (dragSource != null) {
            dragSource.addDragListener(mDragSourceListener);
        }
        if (dropTarget != null) {
            dropTarget.addDropListener(mDropListener);
        }
    }

    /**
     * Unregisters all the listeners previously registered by
     * {@link #registerListeners}.
     *
     * @param dragSource The drag source in the {@link LayoutCanvas} to stop
     *            listening to.
     * @param dropTarget The drop target in the {@link LayoutCanvas} to stop
     *            listening to.
     */
    public void unregisterListeners(DragSource dragSource, DropTarget dropTarget) {
        if (mCanvas.isDisposed()) {
            // If the LayoutCanvas is already disposed, we shouldn't try to unregister
            // the listeners; they are already not active and an attempt to remove the
            // listener will throw a widget-is-disposed exception.
            mListener = null;
            return;
        }

        if (mListener != null) {
            mCanvas.removeMouseMoveListener(mListener);
            mCanvas.removeMouseListener(mListener);
            mCanvas.removeKeyListener(mListener);
            mListener = null;
        }

        if (dragSource != null) {
            dragSource.removeDragListener(mDragSourceListener);
        }
        if (dropTarget != null) {
            dropTarget.removeDropListener(mDropListener);
        }
    }

    /**
     * Starts the given gesture.
     *
     * @param mousePos The most recent mouse coordinate applicable to the new
     *            gesture, in control coordinates.
     * @param gesture The gesture to initiate
     */
    private void startGesture(ControlPoint mousePos, Gesture gesture, int mask) {
        if (mCurrentGesture != null) {
            finishGesture(mousePos, true);
            assert mCurrentGesture == null;
        }

        if (gesture != null) {
            mCurrentGesture = gesture;
            mCurrentGesture.begin(mousePos, mask);
        }
    }

    /**
     * Updates the current gesture, if any, for the given event.
     *
     * @param mousePos The most recent mouse coordinate applicable to the new
     *            gesture, in control coordinates.
     * @param event The event corresponding to this update. May be null. Don't
     *            make any assumptions about the type of this event - for
     *            example, it may not always be a MouseEvent, it could be a
     *            DragSourceEvent, etc.
     */
    private void updateMouse(ControlPoint mousePos, TypedEvent event) {
        if (mCurrentGesture != null) {
            mCurrentGesture.update(mousePos);
        }
    }

    /**
     * Finish the given gesture, either from successful completion or from
     * cancellation.
     *
     * @param mousePos The most recent mouse coordinate applicable to the new
     *            gesture, in control coordinates.
     * @param canceled True if and only if the gesture was canceled.
     */
    private void finishGesture(ControlPoint mousePos, boolean canceled) {
        if (mCurrentGesture != null) {
            mCurrentGesture.end(mousePos, canceled);
            if (mOverlays != null) {
                for (Overlay overlay : mOverlays) {
                    overlay.dispose();
                }
                mOverlays = null;
            }
            mCurrentGesture = null;
            mZombieGesture = null;
            mLastStateMask = 0;
        }
    }

    /**
     * Helper class which implements the {@link MouseMoveListener},
     * {@link MouseListener} and {@link KeyListener} interfaces.
     */
    private class Listener implements MouseMoveListener, MouseListener, KeyListener {

        // --- MouseMoveListener ---

        public void mouseMove(MouseEvent e) {
            mLastMouseX = e.x;
            mLastMouseY = e.y;
            mLastStateMask = e.stateMask;

            if ((e.stateMask & SWT.BUTTON_MASK) != 0) {
                if (mCurrentGesture != null) {
                    ControlPoint controlPoint = ControlPoint.create(mCanvas, e);
                    updateMouse(controlPoint, e);
                    mCanvas.redraw();
                }
            } else {
                mCanvas.hover(e);
            }
        }

        // --- MouseListener ---

        public void mouseUp(MouseEvent e) {
            if (mCurrentGesture == null) {
                // Just a click, select
                mCanvas.getSelectionManager().select(e);
            }
            finishGesture(ControlPoint.create(mCanvas, e), false);
            mCanvas.redraw();
        }

        public void mouseDown(MouseEvent e) {
            mLastMouseX = e.x;
            mLastMouseY = e.y;
            mLastStateMask = e.stateMask;

            // Not yet used. Should be, for Mac and Linux.
        }

        public void mouseDoubleClick(MouseEvent e) {
            mCanvas.showXml(e);
        }

        // --- KeyListener ---

        public void keyPressed(KeyEvent e) {
            if (mCurrentGesture != null) {
                mCurrentGesture.keyPressed(e);
            } else {
                if (e.keyCode == SWT.ESC) {
                    // It appears that SWT does NOT (on the Mac) pass any
                    // key strokes other than modifier keys when the mouse
                    // button is pressed!!
                    ControlPoint controlPoint = ControlPoint.create(mCanvas,
                            mLastMouseX, mLastMouseY);
                    finishGesture(controlPoint, true);
                    return;
                }
            }
        }

        public void keyReleased(KeyEvent e) {
            if (mCurrentGesture != null) {
                mCurrentGesture.keyReleased(e);
            }
        }

    }

    /** Listener for Drag &amp; Drop events. */
    private class CanvasDropListener implements DropTargetListener {
        public CanvasDropListener() {
        }

        /**
         * The cursor has entered the drop target boundaries. {@inheritDoc}
         */
        public void dragEnter(DropTargetEvent event) {
            if (mCurrentGesture == null) {
                Gesture newGesture = mZombieGesture;
                if (newGesture == null) {
                    newGesture = new MoveGesture(mCanvas);
                } else {
                    mZombieGesture = null;
                }
                startGesture(ControlPoint.create(mCanvas, event),
                        newGesture, 0);
            }

            if (mCurrentGesture instanceof DropGesture) {
                ((DropGesture) mCurrentGesture).dragEnter(event);
            }
        }

        /**
         * The cursor is moving over the drop target. {@inheritDoc}
         */
        public void dragOver(DropTargetEvent event) {
            if (mCurrentGesture instanceof DropGesture) {
                ((DropGesture) mCurrentGesture).dragOver(event);
            }
        }

        /**
         * The cursor has left the drop target boundaries OR data is about to be
         * dropped. {@inheritDoc}
         */
        public void dragLeave(DropTargetEvent event) {
            if (mCurrentGesture instanceof DropGesture) {
                DropGesture dropGesture = (DropGesture) mCurrentGesture;
                dropGesture.dragLeave(event);
                finishGesture(ControlPoint.create(mCanvas, event), true);
                mZombieGesture = dropGesture;
            }
        }

        /**
         * The drop is about to be performed. The drop target is given a last
         * chance to change the nature of the drop. {@inheritDoc}
         */
        public void dropAccept(DropTargetEvent event) {
            Gesture gesture = mCurrentGesture != null ? mCurrentGesture : mZombieGesture;
            if (gesture instanceof DropGesture) {
                ((DropGesture) gesture).dropAccept(event);
            }
        }

        /**
         * The data is being dropped. {@inheritDoc}
         */
        public void drop(final DropTargetEvent event) {
            // See if we had a gesture just prior to the drop (we receive a dragLeave
            // right before the drop which we don't know whether means the cursor has
            // left the canvas for good or just before a drop)
            Gesture gesture = mCurrentGesture != null ? mCurrentGesture : mZombieGesture;
            mZombieGesture = null;

            if (gesture instanceof DropGesture) {
                ((DropGesture) gesture).drop(event);

                finishGesture(ControlPoint.create(mCanvas, event), true);
            }
        }

        /**
         * The operation being performed has changed (e.g. modifier key).
         * {@inheritDoc}
         */
        public void dragOperationChanged(DropTargetEvent event) {
            if (mCurrentGesture instanceof DropGesture) {
                ((DropGesture) mCurrentGesture).dragOperationChanged(event);
            }
        }
    }

    /**
     * Our canvas {@link DragSourceListener}. Handles drag being started and
     * finished and generating the drag data.
     */
    private class CanvasDragSourceListener implements DragSourceListener {

        /**
         * The current selection being dragged. This may be a subset of the
         * canvas selection due to the "sanitize" pass. Can be empty but never
         * null.
         */
        private final ArrayList<CanvasSelection> mDragSelection = new ArrayList<CanvasSelection>();

        private SimpleElement[] mDragElements;

        /**
         * The user has begun the actions required to drag the widget.
         * <p/>
         * Initiate a drag only if there is one or more item selected. If
         * there's none, try to auto-select the one under the cursor.
         * {@inheritDoc}
         */
        public void dragStart(DragSourceEvent e) {
            // We need a selection (simple or multiple) to do any transfer.
            // If there's a selection *and* the cursor is over this selection,
            // use all the currently selected elements.
            // If there is no selection or the cursor is not over a selected
            // element, *change* the selection to match the element under the
            // cursor and use that. If nothing can be selected, abort the drag
            // operation.

            List<CanvasSelection> selections = mCanvas.getSelectionManager().getSelections();
            mDragSelection.clear();

            if (!selections.isEmpty()) {
                // Is the cursor on top of a selected element?
                LayoutPoint p = LayoutPoint.create(mCanvas, e);

                boolean insideSelection = false;

                for (CanvasSelection cs : selections) {
                    if (!cs.isRoot() && cs.getRect().contains(p.x, p.y)) {
                        insideSelection = true;
                        break;
                    }
                }

                if (!insideSelection) {
                    CanvasViewInfo vi = mCanvas.getViewHierarchy().findViewInfoAt(p);
                    if (vi != null && !vi.isRoot()) {
                        mCanvas.getSelectionManager().selectSingle(vi);
                        insideSelection = true;
                    }
                }

                if (insideSelection) {
                    // We should now have a proper selection that matches the
                    // cursor. Let's use this one. We make a copy of it since
                    // the "sanitize" pass below might remove some of the
                    // selected objects.
                    if (selections.size() == 1) {
                        // You are dragging just one element - this might or
                        // might not be the root, but if it's the root that is
                        // fine since we will let you drag the root if it is the
                        // only thing you are dragging.
                        mDragSelection.addAll(selections);
                    } else {
                        // Only drag non-root items.
                        for (CanvasSelection cs : selections) {
                            if (!cs.isRoot()) {
                                mDragSelection.add(cs);
                            }
                        }
                    }
                }
            }

            // If you are dragging a non-selected item, select it
            if (mDragSelection.isEmpty()) {
                LayoutPoint p = ControlPoint.create(mCanvas, e).toLayout();
                CanvasViewInfo vi = mCanvas.getViewHierarchy().findViewInfoAt(p);
                if (vi != null && !vi.isRoot()) {
                    mCanvas.getSelectionManager().selectSingle(vi);
                    mDragSelection.addAll(selections);
                }
            }

            SelectionManager.sanitize(mDragSelection);

            e.doit = !mDragSelection.isEmpty();
            if (e.doit) {
                mDragElements = CanvasSelection.getAsElements(mDragSelection);
                GlobalCanvasDragInfo.getInstance().startDrag(mDragElements,
                        mDragSelection.toArray(new CanvasSelection[mDragSelection.size()]),
                        mCanvas, new Runnable() {
                            public void run() {
                                mCanvas.getClipboardSupport().deleteSelection("Remove",
                                        mDragSelection);
                            }
                        });
            }

            // If you drag on the -background-, we make that into a marquee
            // selection
            if (!e.doit || (mDragSelection.size() == 1 && mDragSelection.get(0).isRoot())) {
                boolean toggle = (mLastStateMask & (SWT.CTRL | SWT.SHIFT | SWT.COMMAND)) != 0;
                startGesture(ControlPoint.create(mCanvas, e),
                        new MarqueeGesture(mCanvas, toggle), mLastStateMask);
                e.detail = DND.DROP_NONE;
                e.doit = false;
            } else {
                // Otherwise, the drag means you are moving something
                startGesture(ControlPoint.create(mCanvas, e), new MoveGesture(mCanvas), 0);
            }

            // No hover during drag (since no mouse over events are delivered
            // during a drag to keep the hovers up to date anyway)
            mCanvas.clearHover();

            mCanvas.redraw();
        }

        /**
         * Callback invoked when data is needed for the event, typically right
         * before drop. The drop side decides what type of transfer to use and
         * this side must now provide the adequate data. {@inheritDoc}
         */
        public void dragSetData(DragSourceEvent e) {
            if (TextTransfer.getInstance().isSupportedType(e.dataType)) {
                e.data = CanvasSelection.getAsText(mCanvas, mDragSelection);
                return;
            }

            if (SimpleXmlTransfer.getInstance().isSupportedType(e.dataType)) {
                e.data = mDragElements;
                return;
            }

            // otherwise we failed
            e.detail = DND.DROP_NONE;
            e.doit = false;
        }

        /**
         * Callback invoked when the drop has been finished either way. On a
         * successful move, remove the originating elements.
         */
        public void dragFinished(DragSourceEvent e) {
            // Clear the selection
            mDragSelection.clear();
            mDragElements = null;
            GlobalCanvasDragInfo.getInstance().stopDrag();

            finishGesture(ControlPoint.create(mCanvas, e), e.detail == DND.DROP_NONE);
            mCanvas.redraw();
        }
    }
}
