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

package com.android.ide.eclipse.adt.internal.editors.layout.gre;

import com.android.ide.eclipse.adt.editors.layout.gscripts.INode;
import com.android.ide.eclipse.adt.internal.editors.layout.gle2.CanvasViewInfo;
import com.android.ide.eclipse.adt.internal.editors.layout.uimodel.UiViewElementNode;

import org.eclipse.swt.graphics.Rectangle;

import java.util.HashMap;

/**
 * An object that can create {@link INode} proxies.
 * This also keeps references to objects already created and tries to reuse them.
 */
public class NodeFactory {

    private final HashMap<UiViewElementNode, NodeProxy> mNodeMap =
        new HashMap<UiViewElementNode, NodeProxy>();

    public NodeFactory() {
    }

    /**
     * Returns an {@link INode} proxy based on the view key of the given
     * {@link CanvasViewInfo}. The bounds of the node are set to the canvas view bounds.
     */
    public NodeProxy create(CanvasViewInfo canvasViewInfo) {
        return create(canvasViewInfo.getUiViewKey(), canvasViewInfo.getAbsRect());
    }

    /**
     * Returns an {@link INode} proxy based on a given {@link UiViewElementNode} that
     * is not yet part of the canvas, typically those created
     */
    public NodeProxy create(UiViewElementNode uiNode) {
        return create(uiNode, null /*bounds*/);
    }

    public void clear() {
        mNodeMap.clear();
    }

    //----

    private NodeProxy create(UiViewElementNode uiNode, Rectangle bounds) {
        NodeProxy proxy = mNodeMap.get(uiNode);

        if (proxy == null) {
            // Create a new proxy if the key doesn't exist
            proxy = new NodeProxy(uiNode, bounds, this);
            mNodeMap.put(uiNode, proxy);

        } else if (bounds != null && !proxy.getBounds().equals(bounds)) {
            // Update the bounds if necessary
            proxy.setBounds(bounds);
        }

        return proxy;
    }
}
