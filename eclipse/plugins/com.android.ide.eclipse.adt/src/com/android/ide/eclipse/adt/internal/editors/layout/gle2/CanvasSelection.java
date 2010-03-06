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

import com.android.ide.eclipse.adt.editors.layout.gscripts.INode;
import com.android.ide.eclipse.adt.internal.editors.layout.gre.NodeFactory;
import com.android.ide.eclipse.adt.internal.editors.layout.gre.NodeProxy;
import com.android.ide.eclipse.adt.internal.editors.layout.gre.RulesEngine;

import org.eclipse.swt.graphics.Rectangle;

/**
 * Represents one selection in {@link LayoutCanvas}.
 */
/* package */ class CanvasSelection {

    /** Current selected view info. Can be null. */
    private final CanvasViewInfo mCanvasViewInfo;

    /** Current selection border rectangle. Null when mCanvasViewInfo is null . */
    private final Rectangle mRect;

    /** The node proxy for drawing the selection. Null when mCanvasViewInfo is null. */
    private final NodeProxy mNodeProxy;

    /** The name displayed over the selection, typically the widget class name. Can be null. */
    private final String mName;


    /**
     * Creates a new {@link CanvasSelection} object.
     * @param canvasViewInfo The view info being selected. Must not be null.
     * @param nodeFactory
     */
    public CanvasSelection(CanvasViewInfo canvasViewInfo,
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

        mName = initDisplayName(canvasViewInfo, gre);
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

    /**
     * The name displayed over the selection, typically the widget class name.
     * Can be null.
     */
    public String getName() {
        return mName;
    }

    /**
     * Calls IViewRule.onSelected on the selected view.
     *
     * @param gre The rules engines.
     * @param gcWrapper The GC to use for drawing.
     * @param isMultipleSelection True if more than one view is selected.
     */
    /*package*/ void paintSelection(RulesEngine gre,
            GCWrapper gcWrapper,
            boolean isMultipleSelection) {
        if (mNodeProxy != null) {
            gre.callOnSelected(gcWrapper, mNodeProxy, mName, isMultipleSelection);
        }
    }

    /**
     * Calls IViewRule.onChildSelected on the parent of the selected view, if it has one.
     *
     * @param gre The rules engines.
     * @param gcWrapper The GC to use for drawing.
     */
    public void paintParentSelection(RulesEngine gre, GCWrapper gcWrapper) {
        if (mNodeProxy != null) {
            INode parent = mNodeProxy.getParent();
            if (parent instanceof NodeProxy) {
                gre.callOnChildSelected(gcWrapper, (NodeProxy)parent, mNodeProxy);
            }
        }
    }

    //----

    private String initDisplayName(CanvasViewInfo canvasViewInfo, RulesEngine gre) {
        if (canvasViewInfo == null) {
            return null;
        }

        String fqcn = canvasViewInfo.getName();
        if (fqcn == null) {
            return null;
        }

        String name = gre.callGetDisplayName(canvasViewInfo.getUiViewKey());

        if (name == null) {
            // The name is typically a fully-qualified class name. Let's make it a tad shorter.

            if (fqcn.startsWith("android.")) {                                      // $NON-NLS-1$
                // For android classes, convert android.foo.Name to android...Name
                int first = fqcn.indexOf('.');
                int last = fqcn.lastIndexOf('.');
                if (last > first) {
                    name = fqcn.substring(0, first) + ".." + fqcn.substring(last);   // $NON-NLS-1$
                }
            } else {
                // For custom non-android classes, it's best to keep the 2 first segments of
                // the namespace, e.g. we want to get something like com.example...MyClass
                int first = fqcn.indexOf('.');
                first = fqcn.indexOf('.', first + 1);
                int last = fqcn.lastIndexOf('.');
                if (last > first) {
                    name = fqcn.substring(0, first) + ".." + fqcn.substring(last);   // $NON-NLS-1$
                }
            }
        }

        return name;
    }
}
