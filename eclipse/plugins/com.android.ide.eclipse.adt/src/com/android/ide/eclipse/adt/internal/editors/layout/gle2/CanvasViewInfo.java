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

import com.android.ide.common.api.Rect;
import com.android.ide.common.rendering.api.Capability;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.ide.eclipse.adt.internal.editors.descriptors.AttributeDescriptor;
import com.android.ide.eclipse.adt.internal.editors.layout.UiElementPullParser;
import com.android.ide.eclipse.adt.internal.editors.layout.descriptors.LayoutDescriptors;
import com.android.ide.eclipse.adt.internal.editors.layout.uimodel.UiViewElementNode;
import com.android.ide.eclipse.adt.internal.editors.uimodel.UiAttributeNode;
import com.android.ide.eclipse.adt.internal.editors.uimodel.UiElementNode;

import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.ui.views.properties.IPropertyDescriptor;
import org.eclipse.ui.views.properties.IPropertySheetPage;
import org.eclipse.ui.views.properties.IPropertySource;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Maps a {@link ViewInfo} in a structure more adapted to our needs.
 * The only large difference is that we keep both the original bounds of the view info
 * and we pre-compute the selection bounds which are absolute to the rendered image
 * (whereas the original bounds are relative to the parent view.)
 * <p/>
 * Each view also knows its parent and children.
 * <p/>
 * We can't alter {@link ViewInfo} as it is part of the LayoutBridge and needs to
 * have a fixed API.
 * <p/>
 * The view info also implements {@link IPropertySource}, which enables a linked
 * {@link IPropertySheetPage} to display the attributes of the selected element.
 * This class actually delegates handling of {@link IPropertySource} to the underlying
 * {@link UiViewElementNode}, if any.
 */
public class CanvasViewInfo implements IPropertySource {

    /**
     * Minimal size of the selection, in case an empty view or layout is selected.
     */
    private static final int SELECTION_MIN_SIZE = 6;


    private final Rectangle mAbsRect;
    private final Rectangle mSelectionRect;
    private final String mName;
    private final Object mViewObject;
    private final UiViewElementNode mUiViewNode;
    private final CanvasViewInfo mParent;
    private final ArrayList<CanvasViewInfo> mChildren = new ArrayList<CanvasViewInfo>();

    /**
     * Is this view info an individually exploded view? This is the case for views
     * that were specially inflated by the {@link UiElementPullParser} and assigned
     * fixed padding because they were invisible and somebody requested visibility.
     */
    private boolean mExploded;

    /**
     * Constructs a {@link CanvasViewInfo} initialized with the given initial values.
     */
    private CanvasViewInfo(CanvasViewInfo parent, String name,
            Object viewObject, UiViewElementNode node, Rectangle absRect,
            Rectangle selectionRect) {
        mParent = parent;
        mName = name;
        mViewObject = viewObject;
        mUiViewNode  = node;
        mAbsRect = absRect;
        mSelectionRect = selectionRect;
    }

    /**
     * Returns the original {@link ViewInfo} bounds in absolute coordinates
     * over the whole graphic.
     *
     * @return the bounding box in absolute coordinates
     */
    public Rectangle getAbsRect() {
        return mAbsRect;
    }

    /*
    * Returns the absolute selection bounds of the view info as a rectangle.
    * The selection bounds will always have a size greater or equal to
    * {@link #SELECTION_MIN_SIZE}.
    * The width/height is inclusive (i.e. width = right-left-1).
    * This is in absolute "screen" coordinates (relative to the rendered bitmap).
    */
    public Rectangle getSelectionRect() {
        return mSelectionRect;
    }

    /**
     * Returns the view node. Could be null, although unlikely.
     * @return An {@link UiViewElementNode} that uniquely identifies the object in the XML model.
     * @see ViewInfo#getCookie()
     */
    public UiViewElementNode getUiViewNode() {
        return mUiViewNode;
    }

    /**
     * Returns the parent {@link CanvasViewInfo}.
     * It is null for the root and non-null for children.
     *
     * @return the parent {@link CanvasViewInfo}, which can be null
     */
    public CanvasViewInfo getParent() {
        return mParent;
    }

    /**
     * Returns the list of children of this {@link CanvasViewInfo}.
     * The list is never null. It can be empty.
     * By contract, this.getChildren().get(0..n-1).getParent() == this.
     *
     * @return the children, never null
     */
    public List<CanvasViewInfo> getChildren() {
        return mChildren;
    }

    /**
     * Returns true if the specific {@link CanvasViewInfo} is a parent
     * of this {@link CanvasViewInfo}. It can be a direct parent or any
     * grand-parent higher in the hierarchy.
     *
     * @param potentialParent the view info to check
     * @return true if the given info is a parent of this view
     */
    public boolean isParent(CanvasViewInfo potentialParent) {
        if (potentialParent == null) {

        }
        CanvasViewInfo p = mParent;
        while (p != null) {
            if (p == potentialParent) {
                return true;
            }
            p = p.getParent();
        }
        return false;
    }

    /**
     * Returns the name of the {@link CanvasViewInfo}.
     * Could be null, although unlikely.
     * Experience shows this is the full qualified Java name of the View.
     *
     * @return the name of the view info, or null
     *
     * @see ViewInfo#getClassName()
     */
    public String getName() {
        return mName;
    }

    /**
     * Returns the View object associated with the {@link CanvasViewInfo}.
     * @return the view object or null.
     */
    public Object getViewObject() {
        return mViewObject;
    }

    // ---- Implementation of IPropertySource

    public Object getEditableValue() {
        UiViewElementNode uiView = getUiViewNode();
        if (uiView != null) {
            return ((IPropertySource) uiView).getEditableValue();
        }
        return null;
    }

    public IPropertyDescriptor[] getPropertyDescriptors() {
        UiViewElementNode uiView = getUiViewNode();
        if (uiView != null) {
            return ((IPropertySource) uiView).getPropertyDescriptors();
        }
        return null;
    }

    public Object getPropertyValue(Object id) {
        UiViewElementNode uiView = getUiViewNode();
        if (uiView != null) {
            return ((IPropertySource) uiView).getPropertyValue(id);
        }
        return null;
    }

    public boolean isPropertySet(Object id) {
        UiViewElementNode uiView = getUiViewNode();
        if (uiView != null) {
            return ((IPropertySource) uiView).isPropertySet(id);
        }
        return false;
    }

    public void resetPropertyValue(Object id) {
        UiViewElementNode uiView = getUiViewNode();
        if (uiView != null) {
            ((IPropertySource) uiView).resetPropertyValue(id);
        }
    }

    public void setPropertyValue(Object id, Object value) {
        UiViewElementNode uiView = getUiViewNode();
        if (uiView != null) {
            ((IPropertySource) uiView).setPropertyValue(id, value);
        }
    }

    /**
     * Returns the XML node corresponding to this info, or null if there is no
     * such XML node.
     *
     * @return The XML node corresponding to this info object, or null
     */
    public Node getXmlNode() {
        UiViewElementNode uiView = getUiViewNode();
        if (uiView != null) {
            return uiView.getXmlNode();
        }

        return null;
    }

    /**
     * Returns true iff this view info corresponds to a root element.
     *
     * @return True iff this is a root view info.
     */
    public boolean isRoot() {
        // Select the visual element -- unless it's the root.
        // The root element is the one whose GRAND parent
        // is null (because the parent will be a -document-
        // node).
        return mUiViewNode == null || mUiViewNode.getUiParent() == null ||
            mUiViewNode.getUiParent().getUiParent() == null;
    }

    /**
     * Returns true if this {@link CanvasViewInfo} represents an invisible parent - in
     * other words, a view that can have children, and that has zero bounds making it
     * effectively invisible. (We don't actually look for -0- bounds, but
     * bounds smaller than SELECTION_MIN_SIZE.)
     *
     * @return True if this is an invisible parent.
     */
    public boolean isInvisibleParent() {
        if (mAbsRect.width < SELECTION_MIN_SIZE || mAbsRect.height < SELECTION_MIN_SIZE) {
            return mUiViewNode != null && mUiViewNode.getDescriptor().hasChildren();
        }

        return false;
    }

    /**
     * Is this {@link CanvasViewInfo} a view that has had its padding inflated in order to
     * make it visible during selection or dragging? Note that this is NOT considered to
     * be the case in the explode-all-views mode where all nodes have their padding
     * increased; it's only used for views that individually exploded because they were
     * requested visible and they returned true for {@link #isInvisibleParent()}.
     *
     * @return True if this is an exploded node.
     */
    public boolean isExploded() {
        return mExploded;
    }

    /**
     * Mark this {@link CanvasViewInfo} as having been exploded or not. See the
     * {@link #isExploded()} method for details on what this property means.
     *
     * @param exploded New value of the exploded property to mark this info with.
     */
    /* package */ void setExploded(boolean exploded) {
        this.mExploded = exploded;
    }

    /**
     * Returns the info represented as a {@link SimpleElement}.
     *
     * @return A {@link SimpleElement} wrapping this info.
     */
    /* package */ SimpleElement toSimpleElement() {

        UiViewElementNode uiNode = getUiViewNode();

        String fqcn = SimpleXmlTransfer.getFqcn(uiNode.getDescriptor());
        String parentFqcn = null;
        Rect bounds = SwtUtils.toRect(getAbsRect());
        Rect parentBounds = null;

        UiElementNode uiParent = uiNode.getUiParent();
        if (uiParent != null) {
            parentFqcn = SimpleXmlTransfer.getFqcn(uiParent.getDescriptor());
        }
        if (getParent() != null) {
            parentBounds = SwtUtils.toRect(getParent().getAbsRect());
        }

        SimpleElement e = new SimpleElement(fqcn, parentFqcn, bounds, parentBounds);

        for (UiAttributeNode attr : uiNode.getUiAttributes()) {
            String value = attr.getCurrentValue();
            if (value != null && value.length() > 0) {
                AttributeDescriptor attrDesc = attr.getDescriptor();
                SimpleAttribute a = new SimpleAttribute(
                        attrDesc.getNamespaceUri(),
                        attrDesc.getXmlLocalName(),
                        value);
                e.addAttribute(a);
            }
        }

        for (CanvasViewInfo childVi : getChildren()) {
            SimpleElement e2 = childVi.toSimpleElement();
            if (e2 != null) {
                e.addInnerElement(e2);
            }
        }

        return e;
    }

    /**
     * Returns the layout url attribute value for the closest surrounding include element
     * parent, or null if this {@link CanvasViewInfo} is not rendered as part of an
     * include tag.
     *
     * @return the layout url attribute value for the surrounding include tag, or null if
     *         not applicable
     */
    public String getIncludeUrl() {
        CanvasViewInfo curr = this;
        while (curr != null) {
            if (curr.mUiViewNode != null) {
                Node node = curr.mUiViewNode.getXmlNode();
                if (node != null && node.getNamespaceURI() == null
                        && node.getNodeType() == Node.ELEMENT_NODE
                        && LayoutDescriptors.VIEW_INCLUDE.equals(node.getNodeName())) {
                    // Note: the layout attribute is NOT in the Android namespace
                    Element element = (Element) node;
                    String url = element.getAttribute(LayoutDescriptors.ATTR_LAYOUT);
                    if (url.length() > 0) {
                        return url;
                    }
                }
            }
            curr = curr.mParent;
        }

        return null;
    }

    // ---- Factory functionality ----

    /**
     * Creates a new {@link CanvasViewInfo} hierarchy based on the given {@link ViewInfo}
     * hierarchy. Note that this will not necessarily create one {@link CanvasViewInfo}
     * for each {@link ViewInfo}. It will generally only create {@link CanvasViewInfo}
     * objects for {@link ViewInfo} objects that contain a reference to an
     * {@link UiViewElementNode}, meaning that it corresponds to an element in the XML
     * file for this layout file. This is not always the case, such as in the following
     * scenarios:
     * <ul>
     * <li>we link to other layouts with {@code <include>}
     * <li>the current view is rendered within another view ("Show Included In") such that
     * the outer file does not correspond to elements in the current included XML layout
     * <li>on older platforms that don't support {@link Capability#EMBEDDED_LAYOUT} there
     * is no reference to the {@code <include>} tag
     * <li>with the {@code <merge>} tag we don't get a reference to the corresponding
     * element
     * <ul>
     * <p>
     * This method will build up a set of {@link CanvasViewInfo} that corresponds to the
     * actual <b>selectable</b> views (which are also shown in the Outline).
     *
     * @param root the root {@link ViewInfo} to build from
     * @return a {@link CanvasViewInfo} hierarchy
     */
    public static CanvasViewInfo create(ViewInfo root) {
        if (root.getCookie() == null) {
            // Special case: If the root-most view does not have a view cookie,
            // then we are rendering some outer layout surrounding this layout, and in
            // that case we must search down the hierarchy for the (possibly multiple)
            // sub-roots that correspond to elements in this layout, and place them inside
            // an outer view that has no node. In the outline this item will be used to
            // show the inclusion-context.
            CanvasViewInfo rootView = createView(null, root, 0, 0);
            addKeyedSubtrees(rootView, root, 0, 0);
            return rootView;
        } else {
            // We have a view key at the top, so just go and create {@link CanvasViewInfo}
            // objects for each {@link ViewInfo} until we run into a null key.
            return addKeyedSubtrees(null, root, 0, 0);
        }
    }

    /** Creates a {@link CanvasViewInfo} for a given {@link ViewInfo} but does not recurse */
    private static CanvasViewInfo createView(CanvasViewInfo parent, ViewInfo root, int parentX,
            int parentY) {
        Object cookie = root.getCookie();
        UiViewElementNode node = null;
        if (cookie instanceof UiViewElementNode) {
            node = (UiViewElementNode) cookie;
        }

        return createView(parent, root, parentX, parentY, node);
    }

    /**
     * Creates a {@link CanvasViewInfo} for a given {@link ViewInfo} but does not recurse.
     * This method specifies an explicit {@link UiViewElementNode} to use rather than
     * relying on the view cookie in the info object.
     */
    private static CanvasViewInfo createView(CanvasViewInfo parent, ViewInfo root, int parentX,
            int parentY, UiViewElementNode node) {

        int x = root.getLeft();
        int y = root.getTop();
        int w = root.getRight() - x;
        int h = root.getBottom() - y;

        x += parentX;
        y += parentY;

        Rectangle absRect = new Rectangle(x, y, w - 1, h - 1);

        if (w < SELECTION_MIN_SIZE) {
            int d = (SELECTION_MIN_SIZE - w) / 2;
            x -= d;
            w += SELECTION_MIN_SIZE - w;
        }

        if (h < SELECTION_MIN_SIZE) {
            int d = (SELECTION_MIN_SIZE - h) / 2;
            y -= d;
            h += SELECTION_MIN_SIZE - h;
        }

        Rectangle selectionRect = new Rectangle(x, y, w - 1, h - 1);

        return new CanvasViewInfo(parent, root.getClassName(), root.getViewObject(), node, absRect,
                selectionRect);
    }

    /** Create a subtree recursively until you run out of keys */
    private static CanvasViewInfo createSubtree(CanvasViewInfo parent, ViewInfo viewInfo,
            int parentX, int parentY) {
        assert viewInfo.getCookie() != null;

        CanvasViewInfo view = createView(parent, viewInfo, parentX, parentY);

        // Process children:
        parentX += viewInfo.getLeft();
        parentY += viewInfo.getTop();

        // See if we have any missing keys at this level
        int missingNodes = 0;
        List<ViewInfo> children = viewInfo.getChildren();
        for (ViewInfo child : children) {
            // Only use children which have a ViewKey of the correct type.
            // We can't interact with those when they have a null key or
            // an incompatible type.
            Object cookie = child.getCookie();
            if (!(cookie instanceof UiViewElementNode)) {
                missingNodes++;
            }
        }

        if (missingNodes == 0) {
            // No missing nodes; this is the normal case, and we can just continue to
            // recursively add our children
            for (ViewInfo child : children) {
                CanvasViewInfo childView = createSubtree(view, child, parentX, parentY);
                view.addChild(childView);
            }
        } else {
            // We don't have keys for one or more of the ViewInfos. There are many
            // possible causes: we are on an SDK platform that does not support
            // embedded_layout rendering, or we are including a view with a <merge>
            // as the root element.

            String containerName = view.getUiViewNode().getDescriptor().getXmlLocalName();
            if (containerName.equals(LayoutDescriptors.VIEW_INCLUDE)) {
                // This is expected -- we don't WANT to get node keys for the content
                // of an include since it's in a different file and should be treated
                // as a single unit that cannot be edited (hence, no CanvasViewInfo
                // children)
            } else {
                // We are getting children with null keys where we don't expect it;
                // this usually means that we are dealing with an Android platform
                // that does not support {@link Capability#EMBEDDED_LAYOUT}, or
                // that there are <merge> tags which are doing surprising things
                // to the view hierarchy
                LinkedList<UiViewElementNode> unused = new LinkedList<UiViewElementNode>();
                for (UiElementNode child : view.getUiViewNode().getUiChildren()) {
                    if (child instanceof UiViewElementNode) {
                        unused.addLast((UiViewElementNode) child);
                    }
                }
                for (ViewInfo child : children) {
                    Object cookie = child.getCookie();
                    if (cookie != null) {
                        unused.remove(cookie);
                    }
                }
                if (unused.size() > 0) {
                    if (unused.size() == missingNodes) {
                        // The number of unmatched elements and ViewInfos are identical;
                        // it's very likely that they match one to one, so just use these
                        for (ViewInfo child : children) {
                            if (child.getCookie() == null) {
                                // Only create a flat (non-recursive) view
                                CanvasViewInfo childView = createView(view, child, parentX,
                                        parentY, unused.removeFirst());
                                view.addChild(childView);
                            } else {
                                CanvasViewInfo childView = createSubtree(view, child, parentX,
                                        parentY);
                                view.addChild(childView);
                            }
                        }
                    } else {
                        // We have an uneven match. In this case we might be dealing
                        // with <merge> etc.
                        // We have no way to associate elements back with the
                        // corresponding <include> tags if there are more than one of
                        // them. That's not a huge tragedy since visually you are not
                        // allowed to edit these anyway; we just need to make a visual
                        // block for these for selection and outline purposes.
                        UiViewElementNode reference = unused.get(0);
                        addBoundingView(view, children, reference, parentX, parentY);
                    }
                }
            }
        }

        return view;
    }

    /**
     * Add a single bounding view for all the non-keyed children with dimensions that span
     * the bounding rectangle of all these children, and associate it with the given node
     * reference. Keyed children are added in the normal way.
     */
    private static void addBoundingView(CanvasViewInfo parentView, List<ViewInfo> children,
            UiViewElementNode reference, int parentX, int parentY) {
        Rectangle absRect = null;
        int insertIndex = -1;
        for (int index = 0, size = children.size(); index < size; index++) {
            ViewInfo child = children.get(index);
            if (child.getCookie() == null) {
                int x = child.getLeft();
                int y = child.getTop();
                int width = child.getRight() - x;
                int height = child.getBottom() - y;
                Rectangle rect = new Rectangle(x, y, width, height);
                if (absRect == null) {
                    absRect = rect;
                    insertIndex = index;
                } else {
                    absRect = absRect.union(rect);
                }
            } else {
                CanvasViewInfo childView = createSubtree(parentView, child, parentX, parentY);
                parentView.addChild(childView);
            }
        }
        if (absRect != null) {
            absRect.x += parentX;
            absRect.y += parentY;
            String name = reference.getDescriptor().getXmlLocalName();
            CanvasViewInfo childView = new CanvasViewInfo(parentView, name, null, reference,
                    absRect, absRect);
            parentView.addChild(childView, insertIndex);
        }
    }

    /** Search for a subtree with valid keys and add those subtrees */
    private static CanvasViewInfo addKeyedSubtrees(CanvasViewInfo parent, ViewInfo viewInfo,
            int parentX, int parentY) {
        if (viewInfo.getCookie() != null) {
            CanvasViewInfo subtree = createSubtree(parent, viewInfo, parentX, parentY);
            if (parent != null) {
                parent.mChildren.add(subtree);
            }
            return subtree;
        } else {
            for (ViewInfo child : viewInfo.getChildren()) {
                addKeyedSubtrees(parent, child, parentX + viewInfo.getLeft(), parentY
                        + viewInfo.getTop());
            }

            return null;
        }
    }

    /** Adds the given {@link CanvasViewInfo} as a new last child of this view */
    private void addChild(CanvasViewInfo child) {
        mChildren.add(child);
    }

    /** Adds the given {@link CanvasViewInfo} as a new child at the given index */
    private void addChild(CanvasViewInfo child, int index) {
        if (index < 0) {
            index = mChildren.size();
        }
        mChildren.add(index, child);
    }
}
