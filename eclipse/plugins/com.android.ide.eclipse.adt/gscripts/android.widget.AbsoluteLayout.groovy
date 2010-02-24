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

import com.android.ide.eclipse.adt.editors.layout.gscripts.BaseViewRule;
import com.android.ide.eclipse.adt.editors.layout.gscripts.INodeProxy;
import com.android.ide.eclipse.adt.editors.layout.gscripts.DropZone;
import com.android.ide.eclipse.adt.editors.layout.gscripts.Rect;
import com.android.ide.eclipse.adt.editors.layout.gscripts.Point;

import java.util.Map;
import java.util.ArrayList;

/**
 * An {@link IViewRule} for android.widget.AbsoluteLayout and all its derived classes.
 */
public class AndroidWidgetAbsoluteLayourRule extends BaseViewRule {

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
}
