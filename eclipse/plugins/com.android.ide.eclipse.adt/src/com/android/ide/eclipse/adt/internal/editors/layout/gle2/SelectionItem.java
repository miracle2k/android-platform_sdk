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

import com.android.ide.eclipse.adt.internal.editors.layout.LayoutEditor;
import com.android.ide.eclipse.adt.internal.editors.layout.gre.NodeFactory;
import com.android.ide.eclipse.adt.internal.editors.layout.gre.NodeProxy;
import com.android.ide.eclipse.adt.internal.editors.layout.gre.RulesEngine;
import com.android.ide.eclipse.adt.internal.editors.layout.uimodel.UiViewElementNode;

import org.eclipse.swt.graphics.Rectangle;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents one selection in {@link LayoutCanvas}.
 */
/* package */ class SelectionItem {

    /** Current selected view info. Can be null. */
    private final CanvasViewInfo mCanvasViewInfo;

    /** Current selection border rectangle. Null when mCanvasViewInfo is null . */
    private final Rectangle mRect;

    /** The node proxy for drawing the selection. Null when mCanvasViewInfo is null. */
    private final NodeProxy mNodeProxy;

    /**
     * Creates a new {@link SelectionItem} object.
     * @param canvasViewInfo The view info being selected. Must not be null.
     * @param gre the rules engine
     * @param nodeFactory the node factory
     */
    public SelectionItem(CanvasViewInfo canvasViewInfo,
            RulesEngine gre,
            NodeFactory nodeFactory) {

        assert canvasViewInfo != null;

        mCanvasViewInfo = canvasViewInfo;

        if (canvasViewInfo == null) {
            mRect = null;
            mNodeProxy = null;
        } else {
            Rectangle r = canvasViewInfo.getSelectionRect();
            mRect = new Rectangle(r.x, r.y, r.width, r.height);
            mNodeProxy = nodeFactory.create(canvasViewInfo);
        }
    }

    /**
     * Returns true when this selection item represents the root, the top level
     * layout element in the editor.
     * @return True if and only if this element is at the root of the hierarchy
     */
    public boolean isRoot() {
        return mCanvasViewInfo.isRoot();
    }

    /**
     * Returns the selected view info. Cannot be null.
     */
    public CanvasViewInfo getViewInfo() {
        return mCanvasViewInfo;
    }

    /**
     * Returns the selection border rectangle.
     * Cannot be null.
     */
    public Rectangle getRect() {
        return mRect;
    }

    /** Returns the node associated with this selection (may be null) */
    /* package */ NodeProxy getNode() {
        return mNodeProxy;
    }

    //----

    /**
     * Gets the XML text from the given selection for a text transfer.
     * The returned string can be empty but not null.
     */
    /* package */ static String getAsText(LayoutCanvas canvas, List<SelectionItem> selection) {
        StringBuilder sb = new StringBuilder();

        LayoutEditor layoutEditor = canvas.getLayoutEditor();
        for (SelectionItem cs : selection) {
            CanvasViewInfo vi = cs.getViewInfo();
            UiViewElementNode key = vi.getUiViewNode();
            Node node = key.getXmlNode();
            String t = layoutEditor.getXmlText(node);
            if (t != null) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(t);
            }
        }

        return sb.toString();
    }

    /**
     * Returns elements representing the given selection of canvas items.
     *
     * @param items Items to wrap in elements
     * @return An array of wrapper elements. Never null.
     */
    /* package */ static SimpleElement[] getAsElements(List<SelectionItem> items) {
        ArrayList<SimpleElement> elements = new ArrayList<SimpleElement>();

        for (SelectionItem cs : items) {
            CanvasViewInfo vi = cs.getViewInfo();

            SimpleElement e = vi.toSimpleElement();
            elements.add(e);
        }

        return elements.toArray(new SimpleElement[elements.size()]);
    }
}
