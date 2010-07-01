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

import com.android.ide.eclipse.adt.internal.editors.layout.uimodel.UiViewElementNode;
import com.android.layoutlib.api.ILayoutResult;
import com.android.layoutlib.api.ILayoutResult.ILayoutViewInfo;

import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.ui.views.properties.IPropertyDescriptor;
import org.eclipse.ui.views.properties.IPropertySheetPage;
import org.eclipse.ui.views.properties.IPropertySource;

import java.util.ArrayList;

/**
 * Maps a {@link ILayoutViewInfo} in a structure more adapted to our needs.
 * The only large difference is that we keep both the original bounds of the view info
 * and we pre-compute the selection bounds which are absolute to the rendered image
 * (whereas the original bounds are relative to the parent view.)
 * <p/>
 * Each view also knows its parent and children.
 * <p/>
 * We can't alter {@link ILayoutViewInfo} as it is part of the LayoutBridge and needs to
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
    private final UiViewElementNode mUiViewKey;
    private final CanvasViewInfo mParent;
    private final ArrayList<CanvasViewInfo> mChildren = new ArrayList<CanvasViewInfo>();

    /**
     * Constructs a {@link CanvasViewInfo} hierarchy based on a given {@link ILayoutViewInfo}
     * hierarchy. This call is recursive and builds a full tree.
     *
     * @param viewInfo The root of the {@link ILayoutViewInfo} hierarchy.
     */
    public CanvasViewInfo(ILayoutViewInfo viewInfo) {
        this(viewInfo, null /*parent*/, 0 /*parentX*/, 0 /*parentY*/);
    }

    private CanvasViewInfo(ILayoutViewInfo viewInfo, CanvasViewInfo parent, int parentX, int parentY) {
        mParent = parent;
        mName = viewInfo.getName();

        // The ILayoutViewInfo#getViewKey() method returns a key which depends on the
        // IXmlPullParser used to parse the layout files. In this case, the parser is
        // guaranteed to be an UiElementPullParser, which creates keys that are of type
        // UiViewElementNode.
        // We'll simply crash if the type is not right, as this is not supposed to happen
        // and nothing could work if there's a type mismatch.
        mUiViewKey  = (UiViewElementNode) viewInfo.getViewKey();

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
            for (ILayoutViewInfo child : viewInfo.getChildren()) {
                // Only use children which have a ViewKey of the correct type.
                // We can't interact with those when they have a null key or
                // an incompatible type.
                if (child.getViewKey() instanceof UiViewElementNode) {
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
     * Returns the original {@link ILayoutResult} bounds in absolute coordinates
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
     * Returns the view key. Could be null, although unlikely.
     * @return An {@link UiViewElementNode} that uniquely identifies the object in the XML model.
     * @see ILayoutViewInfo#getViewKey()
     */
    public UiViewElementNode getUiViewKey() {
        return mUiViewKey;
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
     * @see ILayoutViewInfo#getName()
     */
    public String getName() {
        return mName;
    }

    // ---- Implementation of IPropertySource

    public Object getEditableValue() {
        UiViewElementNode uiView = getUiViewKey();
        if (uiView != null) {
            return ((IPropertySource) uiView).getEditableValue();
        }
        return null;
    }

    public IPropertyDescriptor[] getPropertyDescriptors() {
        UiViewElementNode uiView = getUiViewKey();
        if (uiView != null) {
            return ((IPropertySource) uiView).getPropertyDescriptors();
        }
        return null;
    }

    public Object getPropertyValue(Object id) {
        UiViewElementNode uiView = getUiViewKey();
        if (uiView != null) {
            return ((IPropertySource) uiView).getPropertyValue(id);
        }
        return null;
    }

    public boolean isPropertySet(Object id) {
        UiViewElementNode uiView = getUiViewKey();
        if (uiView != null) {
            return ((IPropertySource) uiView).isPropertySet(id);
        }
        return false;
    }

    public void resetPropertyValue(Object id) {
        UiViewElementNode uiView = getUiViewKey();
        if (uiView != null) {
            ((IPropertySource) uiView).resetPropertyValue(id);
        }
    }

    public void setPropertyValue(Object id, Object value) {
        UiViewElementNode uiView = getUiViewKey();
        if (uiView != null) {
            ((IPropertySource) uiView).setPropertyValue(id, value);
        }
    }
}
