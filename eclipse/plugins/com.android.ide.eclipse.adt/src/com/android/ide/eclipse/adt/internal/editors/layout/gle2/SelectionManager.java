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

import com.android.ide.eclipse.adt.internal.editors.layout.uimodel.UiViewElementNode;
import com.android.sdklib.SdkConstants;

import org.eclipse.core.runtime.ListenerList;
import org.eclipse.gef.ui.parts.TreeViewer;
import org.eclipse.jface.util.SafeRunnable;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.ITreeSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TreePath;
import org.eclipse.jface.viewers.TreeSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseEvent;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

/**
 * The {@link SelectionManager} manages the selection in the canvas editor.
 * It holds (and can be asked about) the set of selected items, and it also has
 * operations for manipulating the selection - such as toggling items, copying
 * the selection to the clipboard, etc.
 * <p/>
 * This class implements {@link ISelectionProvider} so that it can delegate
 * the selection provider from the {@link LayoutCanvasViewer}.
 * <p/>
 * Note that {@link LayoutCanvasViewer} sets a selection change listener on this
 * manager so that it can invoke its own fireSelectionChanged when the canvas'
 * selection changes.
 */
public class SelectionManager implements ISelectionProvider {

    private LayoutCanvas mCanvas;

    /** The current selection list. The list is never null, however it can be empty. */
    private final LinkedList<CanvasSelection> mSelections = new LinkedList<CanvasSelection>();

    /** An unmodifiable view of {@link #mSelections}. */
    private final List<CanvasSelection> mUnmodifiableSelection =
        Collections.unmodifiableList(mSelections);

    /** Barrier set when updating the selection to prevent from recursively
     * invoking ourselves. */
    private boolean mInsideUpdateSelection;

    /**
     * The <em>current</em> alternate selection, if any, which changes when the Alt key is
     * used during a selection. Can be null.
     */
    private CanvasAlternateSelection mAltSelection;

    /** List of clients listening to selection changes. */
    private final ListenerList mSelectionListeners = new ListenerList();


    /**
     * Constructs a new {@link SelectionManager} associated with the given layout canvas.
     *
     * @param layoutCanvas The layout canvas to create a {@link SelectionManager} for.
     */
    public SelectionManager(LayoutCanvas layoutCanvas) {
        this.mCanvas = layoutCanvas;
    }

    public void addSelectionChangedListener(ISelectionChangedListener listener) {
         mSelectionListeners.add(listener);
     }

     public void removeSelectionChangedListener(ISelectionChangedListener listener) {
         mSelectionListeners.remove(listener);
     }

    /**
     * Returns the native {@link CanvasSelection} list.
     *
     * @return An immutable list of {@link CanvasSelection}. Can be empty but not null.
     * @see #getSelection() {@link #getSelection()} to retrieve a {@link TreeViewer}
     *                      compatible {@link ISelection}.
     */
    /* package */ List<CanvasSelection> getSelections() {
        return mUnmodifiableSelection;
    }

    /**
     * Return a snapshot/copy of the selection. Useful for clipboards etc where we
     * don't want the returned copy to be affected by future edits to the selection.
     *
     * @return A copy of the current selection. Never null.
     */
    /* package */ List<CanvasSelection> getSnapshot() {
        return new ArrayList<CanvasSelection>(mSelections);
    }

    /**
     * Returns a {@link TreeSelection} compatible with a TreeViewer
     * where each {@link TreePath} item is actually a {@link CanvasViewInfo}.
     */
    public ISelection getSelection() {
        if (mSelections.isEmpty()) {
            return TreeSelection.EMPTY;
        }

        ArrayList<TreePath> paths = new ArrayList<TreePath>();

        for (CanvasSelection cs : mSelections) {
            CanvasViewInfo vi = cs.getViewInfo();
            if (vi != null) {
                ArrayList<Object> segments = new ArrayList<Object>();
                while (vi != null) {
                    segments.add(0, vi);
                    vi = vi.getParent();
                }
                paths.add(new TreePath(segments.toArray()));
            }
        }

        return new TreeSelection(paths.toArray(new TreePath[paths.size()]));
    }

    /**
     * Sets the selection. It must be an {@link ITreeSelection} where each segment
     * of the tree path is a {@link CanvasViewInfo}. A null selection is considered
     * as an empty selection.
     * <p/>
     * This method is invoked by {@link LayoutCanvasViewer#setSelection(ISelection)}
     * in response to an <em>outside</em> selection (compatible with ours) that has
     * changed. Typically it means the outline selection has changed and we're
     * synchronizing ours to match.
     */
    public void setSelection(ISelection selection) {
        if (mInsideUpdateSelection) {
            return;
        }

        try {
            mInsideUpdateSelection = true;

            if (selection == null) {
                selection = TreeSelection.EMPTY;
            }

            if (selection instanceof ITreeSelection) {
                ITreeSelection treeSel = (ITreeSelection) selection;

                if (treeSel.isEmpty()) {
                    // Clear existing selection, if any
                    if (!mSelections.isEmpty()) {
                        mSelections.clear();
                        mAltSelection = null;
                        redraw();
                    }
                    return;
                }

                boolean changed = false;
                boolean redoLayout = false;

                // Create a list of all currently selected view infos
                Set<CanvasViewInfo> oldSelected = new HashSet<CanvasViewInfo>();
                for (CanvasSelection cs : mSelections) {
                    oldSelected.add(cs.getViewInfo());
                }

                // Go thru new selection and take care of selecting new items
                // or marking those which are the same as in the current selection
                for (TreePath path : treeSel.getPaths()) {
                    Object seg = path.getLastSegment();
                    if (seg instanceof CanvasViewInfo) {
                        CanvasViewInfo newVi = (CanvasViewInfo) seg;
                        if (oldSelected.contains(newVi)) {
                            // This view info is already selected. Remove it from the
                            // oldSelected list so that we don't deselect it later.
                            oldSelected.remove(newVi);
                        } else {
                            // This view info is not already selected. Select it now.

                            // reset alternate selection if any
                            mAltSelection = null;
                            // otherwise add it.
                            mSelections.add(createSelection(newVi));
                            changed = true;
                        }
                        if (newVi.isInvisibleParent()) {
                            redoLayout = true;
                        }
                    }
                }

                // Deselect old selected items that are not in the new one
                for (CanvasViewInfo vi : oldSelected) {
                    if (vi.isExploded()) {
                        redoLayout = true;
                    }
                    deselect(vi);
                    changed = true;
                }

                if (redoLayout) {
                    mCanvas.getLayoutEditor().recomputeLayout();
                }
                if (changed) {
                    redraw();
                    updateMenuActions();
                }

            }
        } finally {
            mInsideUpdateSelection = false;
        }
    }

    /**
     * Performs selection for a mouse event.
     * <p/>
     * Shift key (or Command on the Mac) is used to toggle in multi-selection.
     * Alt key is used to cycle selection through objects at the same level than
     * the one pointed at (i.e. click on an object then alt-click to cycle).
     *
     * @param e The mouse event which triggered the selection. Cannot be null.
     *            The modifier key mask will be used to determine whether this
     *            is a plain select or a toggle, etc.
     */
    public void select(MouseEvent e) {
        boolean isMultiClick = (e.stateMask & SWT.SHIFT) != 0 ||
            // On Mac, the Command key is the normal toggle accelerator
            ((SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_DARWIN) &&
                    (e.stateMask & SWT.COMMAND) != 0);
        boolean isCycleClick   = (e.stateMask & SWT.ALT)   != 0;

        LayoutPoint p = ControlPoint.create(mCanvas, e).toLayout();

        if (e.button == 3) {
            // Right click button is used to display a context menu.
            // If there's an existing selection and the click is anywhere in this selection
            // and there are no modifiers being used, we don't want to change the selection.
            // Otherwise we select the item under the cursor.

            if (!isCycleClick && !isMultiClick) {
                for (CanvasSelection cs : mSelections) {
                    if (cs.getRect().contains(p.x, p.y)) {
                        // The cursor is inside the selection. Don't change anything.
                        return;
                    }
                }
            }

        } else if (e.button != 1) {
            // Click was done with something else than the left button for normal selection
            // or the right button for context menu.
            // We don't use mouse button 2 yet (middle mouse, or scroll wheel?) for
            // anything, so let's not change the selection.
            return;
        }

        CanvasViewInfo vi = mCanvas.getViewHierarchy().findViewInfoAt(p);

        if (isMultiClick && !isCycleClick) {
            // Case where shift is pressed: pointed object is toggled.

            // reset alternate selection if any
            mAltSelection = null;

            // If nothing has been found at the cursor, assume it might be a user error
            // and avoid clearing the existing selection.

            if (vi != null) {
                // toggle this selection on-off: remove it if already selected
                if (deselect(vi)) {
                    if (vi.isExploded()) {
                        mCanvas.getLayoutEditor().recomputeLayout();
                    }

                    redraw();
                    return;
                }

                // otherwise add it.
                mSelections.add(createSelection(vi));
                fireSelectionChanged();
                redraw();
            }

        } else if (isCycleClick) {
            // Case where alt is pressed: select or cycle the object pointed at.

            // Note: if shift and alt are pressed, shift is ignored. The alternate selection
            // mechanism does not reset the current multiple selection unless they intersect.

            // We need to remember the "origin" of the alternate selection, to be
            // able to continue cycling through it later. If there's no alternate selection,
            // create one. If there's one but not for the same origin object, create a new
            // one too.
            if (mAltSelection == null || mAltSelection.getOriginatingView() != vi) {
                mAltSelection = new CanvasAlternateSelection(
                        vi, mCanvas.getViewHierarchy().findAltViewInfoAt(p));

                // deselect them all, in case they were partially selected
                deselectAll(mAltSelection.getAltViews());

                // select the current one
                CanvasViewInfo vi2 = mAltSelection.getCurrent();
                if (vi2 != null) {
                    mSelections.addFirst(createSelection(vi2));
                    fireSelectionChanged();
                }
            } else {
                // We're trying to cycle through the current alternate selection.
                // First remove the current object.
                CanvasViewInfo vi2 = mAltSelection.getCurrent();
                deselect(vi2);

                // Now select the next one.
                vi2 = mAltSelection.getNext();
                if (vi2 != null) {
                    mSelections.addFirst(createSelection(vi2));
                    fireSelectionChanged();
                }
            }
            redraw();

        } else {
            // Case where no modifier is pressed: either select or reset the selection.
            selectSingle(vi);
        }
    }

    /**
     * Removes all the currently selected item and only select the given item.
     * Issues a {@link #redraw()} if the selection changes.
     *
     * @param vi The new selected item if non-null. Selection becomes empty if null.
     */
    /* package */ void selectSingle(CanvasViewInfo vi) {
        // reset alternate selection if any
        mAltSelection = null;

        boolean redoLayout = hasExplodedItems();

        // reset (multi)selection if any
        if (!mSelections.isEmpty()) {
            if (mSelections.size() == 1 && mSelections.getFirst().getViewInfo() == vi) {
                // CanvasSelection remains the same, don't touch it.
                return;
            }
            mSelections.clear();
        }

        if (vi != null) {
            mSelections.add(createSelection(vi));
            if (vi.isInvisibleParent()) {
                redoLayout = true;
            }
        }
        fireSelectionChanged();

        if (redoLayout) {
            mCanvas.getLayoutEditor().recomputeLayout();
        }

        redraw();
    }

    /** Returns true if the view hierarchy is showing exploded items. */
    private boolean hasExplodedItems() {
        for (CanvasSelection item : mSelections) {
            if (item.getViewInfo().isExploded()) {
                return true;
            }
        }

        return false;
    }

    /**
     * Selects the given set of {@link CanvasViewInfo}s. This is similar to
     * {@link #selectSingle} but allows you to make a multi-selection. Issues a
     * {@link #redraw()}.
     *
     * @param viewInfos A collection of {@link CanvasViewInfo} objects to be
     *            selected, or null or empty to clear the selection.
     */
    /* package */ void selectMultiple(Collection<CanvasViewInfo> viewInfos) {
        // reset alternate selection if any
        mAltSelection = null;

        boolean redoLayout = hasExplodedItems();

        mSelections.clear();
        if (viewInfos != null) {
            for (CanvasViewInfo viewInfo : viewInfos) {
                mSelections.add(createSelection(viewInfo));
                if (viewInfo.isInvisibleParent()) {
                    redoLayout = true;
                }
            }
        }

        fireSelectionChanged();

        if (redoLayout) {
            mCanvas.getLayoutEditor().recomputeLayout();
        }

        redraw();
    }

    /**
     * Selects the visual element corresponding to the given XML node
     * @param xmlNode The Node whose element we want to select.
     */
    /* package */ void select(Node xmlNode) {
        CanvasViewInfo vi = mCanvas.getViewHierarchy().findViewInfoFor(xmlNode);
        if (vi != null && !vi.isRoot()) {
            selectSingle(vi);
        }
    }

    /**
     * Selects any views that overlap the given selection rectangle.
     *
     * @param topLeft The top left corner defining the selection rectangle.
     * @param bottomRight The bottom right corner defining the selection
     *            rectangle.
     * @param toggled A set of {@link CanvasViewInfo}s that should be toggled
     *            rather than just added.
     */
    public void selectWithin(LayoutPoint topLeft, LayoutPoint bottomRight,
            Collection<CanvasViewInfo> toggled) {
        // reset alternate selection if any
        mAltSelection = null;

        ViewHierarchy viewHierarchy = mCanvas.getViewHierarchy();
        Collection<CanvasViewInfo> viewInfos = viewHierarchy.findWithin(topLeft, bottomRight);

        if (toggled.size() > 0) {
            // Copy; we're not allowed to touch the passed in collection
            Set<CanvasViewInfo> result = new HashSet<CanvasViewInfo>(toggled);
            for (CanvasViewInfo viewInfo : viewInfos) {
                if (toggled.contains(viewInfo)) {
                    result.remove(viewInfo);
                } else {
                    result.add(viewInfo);
                }
            }
            viewInfos = result;
        }

        mSelections.clear();
        for (CanvasViewInfo viewInfo : viewInfos) {
            mSelections.add(createSelection(viewInfo));
        }

        fireSelectionChanged();
        redraw();
    }

    /**
     * Clears the selection and then selects everything (all views and all their
     * children).
     */
    public void selectAll() {
        // First clear the current selection, if any.
        mSelections.clear();
        mAltSelection = null;

        // Now select everything if there's a valid layout
        for (CanvasViewInfo vi : mCanvas.getViewHierarchy().findAllViewInfos(false)) {
            mSelections.add(createSelection(vi));
        }


        fireSelectionChanged();
        redraw();
    }

    /**
     * Returns true if and only if there is currently more than one selected
     * item.
     *
     * @return True if more than one item is selected
     */
    public boolean hasMultiSelection() {
        return mSelections.size() > 1;
    }

    /**
     * Deselects a view info. Returns true if the object was actually selected.
     * Callers are responsible for calling redraw() and updateOulineSelection()
     * after.
     * @param canvasViewInfo The item to deselect.
     * @return  True if the object was successfully removed from the selection.
     */
    public boolean deselect(CanvasViewInfo canvasViewInfo) {
        if (canvasViewInfo == null) {
            return false;
        }

        for (ListIterator<CanvasSelection> it = mSelections.listIterator(); it.hasNext(); ) {
            CanvasSelection s = it.next();
            if (canvasViewInfo == s.getViewInfo()) {
                it.remove();
                return true;
            }
        }

        return false;
    }

    /**
     * Deselects multiple view infos.
     * Callers are responsible for calling redraw() and updateOulineSelection() after.
     */
    private void deselectAll(List<CanvasViewInfo> canvasViewInfos) {
        for (ListIterator<CanvasSelection> it = mSelections.listIterator(); it.hasNext(); ) {
            CanvasSelection s = it.next();
            if (canvasViewInfos.contains(s.getViewInfo())) {
                it.remove();
            }
        }
    }

    /** Sync the selection with an updated view info tree */
    /* package */ void sync(CanvasViewInfo lastValidViewInfoRoot) {
        // Check if the selection is still the same (based on the object keys)
        // and eventually recompute their bounds.
        for (ListIterator<CanvasSelection> it = mSelections.listIterator(); it.hasNext(); ) {
            CanvasSelection s = it.next();

            // Check if the selected object still exists
            ViewHierarchy viewHierarchy = mCanvas.getViewHierarchy();
            Object key = s.getViewInfo().getUiViewNode();
            CanvasViewInfo vi = viewHierarchy.findViewInfoKey(key, lastValidViewInfoRoot);

            // Remove the previous selection -- if the selected object still exists
            // we need to recompute its bounds in case it moved so we'll insert a new one
            // at the same place.
            it.remove();
            if (vi != null) {
                it.add(createSelection(vi));
            }
        }
        fireSelectionChanged();

        // remove the current alternate selection views
        mAltSelection = null;
    }

    /**
     * Notifies listeners that the selection has changed.
     */
    private void fireSelectionChanged() {
        if (mInsideUpdateSelection) {
            return;
        }
        try {
            mInsideUpdateSelection = true;

            final SelectionChangedEvent event = new SelectionChangedEvent(this, getSelection());

            SafeRunnable.run(new SafeRunnable() {
                public void run() {
                    for (Object listener : mSelectionListeners.getListeners()) {
                        ((ISelectionChangedListener) listener).selectionChanged(event);
                    }
                }
            });

            // Update menu actions that depend on the selection
            updateMenuActions();

        } finally {
            mInsideUpdateSelection = false;
        }
    }

    /**
     * Sanitizes the selection for a copy/cut or drag operation.
     * <p/>
     * Sanitizes the list to make sure all elements have a valid XML attached to it,
     * that is remove element that have no XML to avoid having to make repeated such
     * checks in various places after.
     * <p/>
     * In case of multiple selection, we also need to remove all children when their
     * parent is already selected since parents will always be added with all their
     * children.
     * <p/>
     *
     * @param selection The selection list to be sanitized <b>in-place</b>.
     *      The <code>selection</code> argument should not be {@link #mSelections} -- the
     *      given list is going to be altered and we should never alter the user-made selection.
     *      Instead the caller should provide its own copy.
     */
    /* package */ static void sanitize(List<CanvasSelection> selection) {
        if (selection.isEmpty()) {
            return;
        }

        for (Iterator<CanvasSelection> it = selection.iterator(); it.hasNext(); ) {
            CanvasSelection cs = it.next();
            CanvasViewInfo vi = cs.getViewInfo();
            UiViewElementNode key = vi == null ? null : vi.getUiViewNode();
            Node node = key == null ? null : key.getXmlNode();
            if (node == null) {
                // Missing ViewInfo or view key or XML, discard this.
                it.remove();
                continue;
            }

            if (vi != null) {
                for (Iterator<CanvasSelection> it2 = selection.iterator();
                     it2.hasNext(); ) {
                    CanvasSelection cs2 = it2.next();
                    if (cs != cs2) {
                        CanvasViewInfo vi2 = cs2.getViewInfo();
                        if (vi.isParent(vi2)) {
                            // vi2 is a parent for vi. Remove vi.
                            it.remove();
                            break;
                        }
                    }
                }
            }
        }
    }

    private void updateMenuActions() {
        boolean hasSelection = !mSelections.isEmpty();
        mCanvas.updateMenuActions(hasSelection);
    }

    private void redraw() {
        mCanvas.redraw();
    }

    private CanvasSelection createSelection(CanvasViewInfo vi) {
        return new CanvasSelection(vi, mCanvas.getRulesEngine(),
                mCanvas.getNodeFactory());
    }
}
