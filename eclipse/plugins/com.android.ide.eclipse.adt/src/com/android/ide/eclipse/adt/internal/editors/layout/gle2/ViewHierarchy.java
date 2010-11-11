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

import com.android.ide.common.api.INode;
import com.android.ide.eclipse.adt.internal.editors.layout.gre.NodeProxy;
import com.android.ide.eclipse.adt.internal.editors.layout.uimodel.UiViewElementNode;
import com.android.ide.eclipse.adt.internal.editors.uimodel.UiElementNode;
import com.android.layoutlib.api.LayoutScene;
import com.android.layoutlib.api.SceneResult;
import com.android.layoutlib.api.ViewInfo;

import org.eclipse.swt.graphics.Rectangle;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The view hierarchy class manages a set of view info objects and performs find
 * operations on this set.
 */
public class ViewHierarchy {
    private LayoutCanvas mCanvas;

    /**
     * Constructs a new {@link ViewHierarchy} tied to the given
     * {@link LayoutCanvas}.
     *
     * @param canvas The {@link LayoutCanvas} to create a {@link ViewHierarchy}
     *            for.
     */
    public ViewHierarchy(LayoutCanvas canvas) {
        this.mCanvas = canvas;
    }

    /**
     * The CanvasViewInfo root created by the last call to {@link #setResult}
     * with a valid layout.
     * <p/>
     * This <em>can</em> be null to indicate we're dealing with an empty document with
     * no root node. Null here does not mean the result was invalid, merely that the XML
     * had no content to display -- we need to treat an empty document as valid so that
     * we can drop new items in it.
     */
    private CanvasViewInfo mLastValidViewInfoRoot;

    /**
     * True when the last {@link #setResult} provided a valid {@link LayoutScene}.
     * <p/>
     * When false this means the canvas is displaying an out-dated result image & bounds and some
     * features should be disabled accordingly such a drag'n'drop.
     * <p/>
     * Note that an empty document (with a null {@link #mLastValidViewInfoRoot}) is considered
     * valid since it is an acceptable drop target.
     */
    private boolean mIsResultValid;

    /**
     * A list of invisible parents (see {@link CanvasViewInfo#isInvisibleParent()} for
     * details) in the current view hierarchy.
     */
    private final List<CanvasViewInfo> mInvisibleParents = new ArrayList<CanvasViewInfo>();

    /**
     * A read-only view of {@link #mInvisibleParents}; note that this is NOT a copy so it
     * reflects updates to the underlying {@link #mInvisibleParents} list.
     */
    private final List<CanvasViewInfo> mInvisibleParentsReadOnly =
        Collections.unmodifiableList(mInvisibleParents);

    /**
     * Flag which records whether or not we have any exploded parent nodes in this
     * view hierarchy. This is used to track whether or not we need to recompute the
     * layout when we exit show-all-invisible-parents mode (see
     * {@link LayoutCanvas#showInvisibleViews}).
     */
    private boolean mExplodedParents;

    /**
     * Sets the result of the layout rendering. The result object indicates if the layout
     * rendering succeeded. If it did, it contains a bitmap and the objects rectangles.
     *
     * Implementation detail: the bridge's computeLayout() method already returns a newly
     * allocated ILayourResult. That means we can keep this result and hold on to it
     * when it is valid.
     *
     * @param scene The new result, either valid or not.
     * @param explodedNodes The set of individual nodes the layout computer was asked to
     *            explode. Note that these are independent of the explode-all mode where
     *            all views are exploded; this is used only for the mode (
     *            {@link LayoutCanvas#showInvisibleViews}) where individual invisible
     *            nodes are padded during certain interactions.
     */
    /* package */ void setResult(LayoutScene scene, Set<UiElementNode> explodedNodes) {
        mIsResultValid = (scene != null && scene.getResult() == SceneResult.SUCCESS);
        mExplodedParents = false;

        if (mIsResultValid && scene != null) {
            ViewInfo root = scene.getRootView();
            if (root == null) {
                mLastValidViewInfoRoot = null;
            } else {
                mLastValidViewInfoRoot = new CanvasViewInfo(scene.getRootView());
            }

            updateNodeProxies(mLastValidViewInfoRoot);

            // Update the data structures related to tracking invisible and exploded nodes.
            // We need to find the {@link CanvasViewInfo} objects that correspond to
            // the passed in {@link UiElementNode} keys that were re-rendered, and mark
            // them as exploded and store them in a list for rendering.
            mExplodedParents = false;
            mInvisibleParents.clear();
            addInvisibleParents(mLastValidViewInfoRoot, explodedNodes);

            // Update the selection
            mCanvas.getSelectionManager().sync(mLastValidViewInfoRoot);
        } else {
            mInvisibleParents.clear();
        }
    }

    /**
     * Creates or updates the node proxy for this canvas view info.
     * <p/>
     * Since proxies are reused, this will update the bounds of an existing proxy when the
     * canvas is refreshed and a view changes position or size.
     * <p/>
     * This is a recursive call that updates the whole hierarchy starting at the given
     * view info.
     */
    private void updateNodeProxies(CanvasViewInfo vi) {
        if (vi == null) {
            return;
        }

        UiViewElementNode key = vi.getUiViewKey();

        if (key != null) {
            mCanvas.getNodeFactory().create(vi);
        }

        for (CanvasViewInfo child : vi.getChildren()) {
            updateNodeProxies(child);
        }
    }

    /**
     * Make a pass over the view hierarchy and look for two things:
     * <ol>
     * <li>Invisible parents. These are nodes that can hold children and have empty
     * bounds. These are then added to the {@link #mInvisibleParents} list.
     * <li>Exploded nodes. These are nodes that were previously marked as invisible, and
     * subsequently rendered by a recomputed layout. They now no longer have empty bounds,
     * but should be specially marked via {@link CanvasViewInfo#setExploded} such that we
     * for example in selection operations can determine if we need to recompute the
     * layout.
     * </ol>
     *
     * @param vi
     * @param invisibleNodes
     */
    private void addInvisibleParents(CanvasViewInfo vi, Set<UiElementNode> invisibleNodes) {
        if (vi == null) {
            return;
        }

        if (vi.isInvisibleParent()) {
            mInvisibleParents.add(vi);
        } else if (invisibleNodes != null) {
            UiViewElementNode key = vi.getUiViewKey();

            if (key != null && invisibleNodes.contains(key)) {
                vi.setExploded(true);
                mExplodedParents = true;
                mInvisibleParents.add(vi);
            }
        }

        for (CanvasViewInfo child : vi.getChildren()) {
            addInvisibleParents(child, invisibleNodes);
        }
    }

    /**
     * Returns true when the last {@link #setResult} provided a valid
     * {@link LayoutScene}.
     * <p/>
     * When false this means the canvas is displaying an out-dated result image & bounds and some
     * features should be disabled accordingly such a drag'n'drop.
     * <p/>
     * Note that an empty document (with a null {@link #getRoot()}) is considered
     * valid since it is an acceptable drop target.
     * @return True when this {@link ViewHierarchy} contains a valid hierarchy of views.
    */
    public boolean isValid() {
        return mIsResultValid;
    }

    /**
     * Returns true if the last valid content of the canvas represents an empty document.
     * @return True if the last valid content of the canvas represents an empty document.
     */
    public boolean isEmpty() {
        return mLastValidViewInfoRoot == null;
    }

    /**
     * Returns true if we have parents in this hierarchy that are invisible (e.g. because
     * they have no children and zero layout bounds).
     *
     * @return True if we have invisible parents.
     */
    public boolean hasInvisibleParents() {
        return mInvisibleParents.size() > 0;
    }

    /**
     * Returns true if we have views that were exploded during rendering
     * @return True if we have exploded parents
     */
    public boolean hasExplodedParents() {
        return mExplodedParents;
    }

    /** Locates and return any views that overlap the given selection rectangle.
     * @param topLeft The top left corner of the selection rectangle.
     * @param bottomRight The bottom right corner of the selection rectangle.
     * @return A collection of {@link CanvasViewInfo} objects that overlap the
     *   rectangle.
     */
    public Collection<CanvasViewInfo> findWithin(
            LayoutPoint topLeft,
            LayoutPoint bottomRight) {
        Rectangle selectionRectangle = new Rectangle(topLeft.x, topLeft.y, bottomRight.x
                - topLeft.x, bottomRight.y - topLeft.y);
        List<CanvasViewInfo> infos = new ArrayList<CanvasViewInfo>();
        addWithin(mLastValidViewInfoRoot, selectionRectangle, infos);
        return infos;
    }

    /**
     * Recursive internal version of {@link #findViewInfoAt(int, int)}. Please don't use directly.
     * <p/>
     * Tries to find the inner most child matching the given x,y coordinates in the view
     * info sub-tree. This uses the potentially-expanded selection bounds.
     *
     * Returns null if not found.
     */
    private void addWithin(
            CanvasViewInfo canvasViewInfo,
            Rectangle canvasRectangle,
            List<CanvasViewInfo> infos) {
        if (canvasViewInfo == null) {
            return;
        }
        Rectangle r = canvasViewInfo.getSelectionRect();
        if (canvasRectangle.intersects(r)) {

            // try to find a matching child first
            for (CanvasViewInfo child : canvasViewInfo.getChildren()) {
                addWithin(child, canvasRectangle, infos);
            }

            if (canvasViewInfo != mLastValidViewInfoRoot) {
                infos.add(canvasViewInfo);
            }
        }
    }

    /**
     * Locates and returns the {@link CanvasViewInfo} corresponding to the given
     * node, or null if it cannot be found.
     *
     * @param node The node we want to find a corresponding
     *            {@link CanvasViewInfo} for.
     * @return The {@link CanvasViewInfo} corresponding to the given node, or
     *         null if no match was found.
     */
    public CanvasViewInfo findViewInfoFor(Node node) {
        if (mLastValidViewInfoRoot != null) {
            return findViewInfoForNode(node, mLastValidViewInfoRoot);
        }
        return null;
    }

    /**
     * Tries to find a child with the same view XML node in the view info sub-tree.
     * Returns null if not found.
     */
    private CanvasViewInfo findViewInfoForNode(Node xmlNode, CanvasViewInfo canvasViewInfo) {
        if (canvasViewInfo == null) {
            return null;
        }
        if (canvasViewInfo.getXmlNode() == xmlNode) {
            return canvasViewInfo;
        }

        // Try to find a matching child
        for (CanvasViewInfo child : canvasViewInfo.getChildren()) {
            CanvasViewInfo v = findViewInfoForNode(xmlNode, child);
            if (v != null) {
                return v;
            }
        }

        return null;
    }


    /**
     * Tries to find the inner most child matching the given x,y coordinates in
     * the view info sub-tree, starting at the last know view info root. This
     * uses the potentially-expanded selection bounds.
     * <p/>
     * Returns null if not found or if there's no view info root.
     *
     * @param p The point at which to look for the deepest match in the view
     *            hierarchy
     * @return A {@link CanvasViewInfo} that intersects the given point, or null
     *         if nothing was found.
     */
    public CanvasViewInfo findViewInfoAt(LayoutPoint p) {
        if (mLastValidViewInfoRoot == null) {
            return null;
        }

        return findViewInfoAt_Recursive(p, mLastValidViewInfoRoot);
    }

    /**
     * Recursive internal version of {@link #findViewInfoAt(int, int)}. Please don't use directly.
     * <p/>
     * Tries to find the inner most child matching the given x,y coordinates in the view
     * info sub-tree. This uses the potentially-expanded selection bounds.
     *
     * Returns null if not found.
     */
    private CanvasViewInfo findViewInfoAt_Recursive(LayoutPoint p, CanvasViewInfo canvasViewInfo) {
        if (canvasViewInfo == null) {
            return null;
        }
        Rectangle r = canvasViewInfo.getSelectionRect();
        if (r.contains(p.x, p.y)) {

            // try to find a matching child first
            for (CanvasViewInfo child : canvasViewInfo.getChildren()) {
                CanvasViewInfo v = findViewInfoAt_Recursive(p, child);
                if (v != null) {
                    return v;
                }
            }

            // if no children matched, this is the view that we're looking for
            return canvasViewInfo;
        }

        return null;
    }

    /**
     * Returns a list of all the possible alternatives for a given view at the given
     * position. This is used to build and manage the "alternate" selection that cycles
     * around the parents or children of the currently selected element.
     */
    /* package */ List<CanvasViewInfo> findAltViewInfoAt(LayoutPoint p) {
        if (mLastValidViewInfoRoot != null) {
            return findAltViewInfoAt_Recursive(p, mLastValidViewInfoRoot, null);
        }

        return null;
    }

    /**
     * Internal recursive version of {@link #findAltViewInfoAt(int, int, CanvasViewInfo)}.
     * Please don't use directly.
     */
    private List<CanvasViewInfo> findAltViewInfoAt_Recursive(
            LayoutPoint p, CanvasViewInfo parent, List<CanvasViewInfo> outList) {
        Rectangle r;

        if (outList == null) {
            outList = new ArrayList<CanvasViewInfo>();

            if (parent != null) {
                // add the parent root only once
                r = parent.getSelectionRect();
                if (r.contains(p.x, p.y)) {
                    outList.add(parent);
                }
            }
        }

        if (parent != null && !parent.getChildren().isEmpty()) {
            // then add all children that match the position
            for (CanvasViewInfo child : parent.getChildren()) {
                r = child.getSelectionRect();
                if (r.contains(p.x, p.y)) {
                    outList.add(child);
                }
            }

            // finally recurse in the children
            for (CanvasViewInfo child : parent.getChildren()) {
                r = child.getSelectionRect();
                if (r.contains(p.x, p.y)) {
                    findAltViewInfoAt_Recursive(p, child, outList);
                }
            }
        }

        return outList;
    }

    /**
     * Locates and returns the {@link CanvasViewInfo} corresponding to the given
     * node, or null if it cannot be found.
     *
     * @param node The node we want to find a corresponding
     *            {@link CanvasViewInfo} for.
     * @return The {@link CanvasViewInfo} corresponding to the given node, or
     *         null if no match was found.
     */
    public CanvasViewInfo findViewInfoFor(INode node) {
        if (mLastValidViewInfoRoot != null && node instanceof NodeProxy) {
            return findViewInfoKey(((NodeProxy) node).getNode(), mLastValidViewInfoRoot);
        }
        return null;
    }

    /**
     * Tries to find a child with the same view key in the view info sub-tree.
     * Returns null if not found.
     *
     * @param viewKey The view key that a matching {@link CanvasViewInfo} should
     *            have as its key.
     * @param canvasViewInfo A root {@link CanvasViewInfo} to search from.
     * @return A {@link CanvasViewInfo} matching the given key, or null if not
     *         found.
     */
    public CanvasViewInfo findViewInfoKey(Object viewKey, CanvasViewInfo canvasViewInfo) {
        if (canvasViewInfo == null) {
            return null;
        }
        if (canvasViewInfo.getUiViewKey() == viewKey) {
            return canvasViewInfo;
        }

        // try to find a matching child
        for (CanvasViewInfo child : canvasViewInfo.getChildren()) {
            CanvasViewInfo v = findViewInfoKey(viewKey, child);
            if (v != null) {
                return v;
            }
        }

        return null;
    }

    /**
     * Returns a list of ALL ViewInfos (possibly excluding the root, depending
     * on the parameter for that).
     *
     * @param includeRoot If true, include the root in the list, otherwise
     *            exclude it (but include all its children)
     * @return A list of canvas view infos.
     */
    public List<CanvasViewInfo> findAllViewInfos(boolean includeRoot) {
        List<CanvasViewInfo> infos = new ArrayList<CanvasViewInfo>();
        if (mIsResultValid && mLastValidViewInfoRoot != null) {
            findAllViewInfos(infos, mLastValidViewInfoRoot, includeRoot);
        }

        return infos;
    }

    private void findAllViewInfos(List<CanvasViewInfo> result, CanvasViewInfo canvasViewInfo,
            boolean includeRoot) {
        if (canvasViewInfo != null) {
            if (includeRoot || !canvasViewInfo.isRoot()) {
                result.add(canvasViewInfo);
            }
            for (CanvasViewInfo child : canvasViewInfo.getChildren()) {
                findAllViewInfos(result, child, true);
            }
        }
    }

    /**
     * Returns the root of the view hierarchy, if any (could be null, for example
     * on rendering failure).
     *
     * @return The current view hierarchy, or null
     */
    public CanvasViewInfo getRoot() {
        return mLastValidViewInfoRoot;
    }

    /**
     * Returns a collection of views that have zero bounds and that correspond to empty
     * parents. Note that the views may not actually have zero bounds; in particular, if
     * they are exploded ({@link CanvasViewInfo#isExploded()}, then they will have the
     * bounds of a shown invisible node. Therefore, this method returns the views that
     * would be invisible in a real rendering of the scene.
     *
     * @return A collection of empty parent views.
     */
    public List<CanvasViewInfo> getInvisibleViews() {
        return mInvisibleParentsReadOnly;
    }

    /**
     * Returns the invisible nodes (the {@link UiElementNode} objects corresponding
     * to the {@link CanvasViewInfo} objects returned from {@link #getInvisibleViews()}.
     * We are pulling out the nodes since they preserve their identity across layout
     * rendering, and in particular we return it as a set such that the layout renderer
     * can perform quick identity checks when looking up attribute values during the
     * rendering process.
     *
     * @return A set of the invisible nodes.
     */
    public Set<UiElementNode> getInvisibleNodes() {
        if (mInvisibleParents.size() == 0) {
            return Collections.emptySet();
        }

        Set<UiElementNode> nodes = new HashSet<UiElementNode>(mInvisibleParents.size());
        for (CanvasViewInfo info : mInvisibleParents) {
            UiViewElementNode node = info.getUiViewKey();
            if (node != null) {
                nodes.add(node);
            }
        }

        return nodes;
    }
}
