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

package com.android.ide.eclipse.adt.internal.editors.layout.gre;

import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.gscripts.INodeProxy;
import com.android.ide.eclipse.adt.gscripts.Rect;
import com.android.ide.eclipse.adt.internal.editors.layout.uimodel.UiViewElementNode;

import org.eclipse.swt.graphics.Rectangle;

/**
 *
 */
public class NodeProxy implements INodeProxy {

    private final UiViewElementNode mNode;
    private final Rect mBounds;

    public NodeProxy(UiViewElementNode node, Rectangle bounds) {
        mNode = node;
        mBounds = new Rect(bounds.x, bounds.y, bounds.width, bounds.height);
    }

    public Rect getBounds() {
        return mBounds;
    }

    /* package */ UiViewElementNode getNode() {
        return mNode;
    }

    public void debugPrint(String msg) {
        AdtPlugin.printToConsole(
                mNode == null ? "Groovy" : mNode.getDescriptor().getXmlLocalName() + ".groovy",
                msg
                );
    }

}
