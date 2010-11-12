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
import com.android.ide.eclipse.adt.internal.editors.descriptors.AttributeDescriptor;
import com.android.ide.eclipse.adt.internal.editors.layout.UiElementPullParser;
import com.android.ide.eclipse.adt.internal.editors.layout.uimodel.UiViewElementNode;
import com.android.ide.eclipse.adt.internal.editors.uimodel.UiAttributeNode;
import com.android.ide.eclipse.adt.internal.editors.uimodel.UiElementNode;
import com.android.layoutlib.api.ViewInfo;

import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.ui.views.properties.IPropertyDescriptor;
import org.eclipse.ui.views.properties.IPropertySheetPage;
import org.eclipse.ui.views.properties.IPropertySource;
import org.w3c.dom.Node;

import java.util.ArrayList;

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
     * Constructs a {@link CanvasViewInfo} hierarchy based on a given {@link ViewInfo}
     * hierarchy. This call is recursive and builds a full tree.
     *
     * @param viewInfo The root of the {@link ViewInfo} hierarchy.
     */
    public CanvasViewInfo(ViewInfo viewInfo) {
        this(viewInfo, null /*parent*/, 0 /*parentX*/, 0 /*parentY*/);
    }

    private CanvasViewInfo(ViewInfo viewInfo, CanvasViewInfo parent,
            int parentX, int parentY) {
        mParent = parent;
        mName = viewInfo.getClassName();

        // The ViewInfo#getViewKey() method returns a cookie uniquely identifying the object
        // they represent on this side of the API.
        // In this case, the parser is guaranteed to be an UiElementPullParser, which creates
        // cookies that are of type UiViewElementNode.
        // We'll simply crash if the type is not right, as this is not supposed to happen
        // and nothing could work if there's a type mismatch.
        mUiViewNode  = (UiViewElementNode) viewInfo.getCookie();

        int x = viewInfo.getLeft();
        int y = viewInfo.getTop();
        int w = viewInfo.getRight() - x;
        int h = viewInfo.getBottom() - y;

        if (parent != null) {
            x += parentX;
            y += parentY;
        }

        mAbsRect = new Rectangle(x, y, w - 1, h - 1);

        if (viewInfo.getChildren() != null) {
            for (ViewInfo child : viewInfo.getChildren()) {
                // Only use children which have a ViewKey of the correct type.
                // We can't interact with those when they have a null key or
                // an incompatible type.
                if (child.getCookie() instanceof UiViewElementNode) {
                    mChildren.add(new CanvasViewInfo(child, this, x, y));
                }
            }
        }

        // adjust selection bounds for views which are too small to select

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

        mSelectionRect = new Rectangle(x, y, w - 1, h - 1);
    }

    /**
     * Returns the original {@link ViewInfo} bounds in absolute coordinates
     * over the whole graphic.
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
     */
    public CanvasViewInfo getParent() {
        return mParent;
    }

    /**
     * Returns the list of children of this {@link CanvasViewInfo}.
     * The list is never null. It can be empty.
     * By contract, this.getChildren().get(0..n-1).getParent() == this.
     */
    public ArrayList<CanvasViewInfo> getChildren() {
        return mChildren;
    }

    /**
     * Returns true if the specific {@link CanvasViewInfo} is a parent
     * of this {@link CanvasViewInfo}. It can be a direct parent or any
     * grand-parent higher in the hierarchy.
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
     * @see ViewInfo#getClassName()
     */
    public String getName() {
        return mName;
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
        Rect bounds = new Rect(getAbsRect());
        Rect parentBounds = null;

        UiElementNode uiParent = uiNode.getUiParent();
        if (uiParent != null) {
            parentFqcn = SimpleXmlTransfer.getFqcn(uiParent.getDescriptor());
        }
        if (getParent() != null) {
            parentBounds = new Rect(getParent().getAbsRect());
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

}
