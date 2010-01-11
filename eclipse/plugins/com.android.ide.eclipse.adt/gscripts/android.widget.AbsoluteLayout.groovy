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

package com.android.adt.gscripts;

import com.android.ide.eclipse.adt.gscripts.IViewRule;
import com.android.ide.eclipse.adt.gscripts.INodeProxy;
import com.android.ide.eclipse.adt.gscripts.DropZone;
import com.android.ide.eclipse.adt.gscripts.Rect;
import com.android.ide.eclipse.adt.gscripts.Point;

import java.util.Map;
import java.util.ArrayList;

/**
 * An {@link IViewRule} for android.widget.AbsoluteLayout and all its derived classes.
 */
public class AndroidWidgetAbsoluteLayourRule implements IViewRule {

    /**
     * This method is called by the rule engine when the script is first loaded.
     * Returns true, we can handle any AbsoluteLayout or derived class.
     */
    public boolean onInitialize(String fqcn) {
        return true;
    }

    /**
     * This method is called by the rules engine just before the script is unloaded.
     */
    public void onDispose() {
    }

    /**
     * Returns the class name to display when an element is selected in the GLE.
     * Returns null to use the default display behavior.
     */
    public String getDisplayName() {
        return null;
    }


    // ==== XML Creation ====

    /**
     * Returns the default attributes that a new XML element of this type should have
     * when added to an XML layout file. Note that these defaults can be overridden by the
     * specific code performing the insertion.
     *
     * @return A map of attribute:values for a new element of this type. Can be null or empty.
     */
    public Map<?, ?> getDefaultAttributes() {
        return null;
    }


    // ==== Drag'n'drop support ====

    /**
     * Called when a drop operation starts, whilst the d'n'd is dragging the cursor over the
     * views. The purpose of the drop operation will be to create a new element.
     * <p/>
     * For the AbsoluteLayout, the whole bounds of the view is a suitable drop zone.
     */
    public ArrayList<DropZone> dropStart(INodeProxy targetNode) {
        DropZone d = new DropZone(targetNode.getBounds(), null /*data*/ );
        return [ d ];
    }

    /**
     * Called after the user selects to drop the given source into one of the drop zones.
     */
    public void dropFinish(
            String sourceFqcn,
            INodeProxy targetNode,
            DropZone selectedZone,
            Point where) {
        int x = where.x - targetNode.getBounds().x;
        int y = where.y - targetNode.getBounds().y;

        debugPrintf("AbsL.drop: add ${sourceFqcn} at coord ${x}x${y}");

        INodeProxy e = targetNode.createChild(sourceFqcn);
        e.setAttribute("layout_x", "${x}dip");
        e.setAttribute("layout_y", "${y}dip");
    }


    public ArrayList<DropZone> moveStart(INodeProxy sourceNode, INodeProxy targetNode, boolean copy) {
        // TODO
    }

    public void moveFinish(INodeProxy sourceNode, INodeProxy targetNode, boolean copy, DropZone selectedZone) {
        // TODO
    }
}
