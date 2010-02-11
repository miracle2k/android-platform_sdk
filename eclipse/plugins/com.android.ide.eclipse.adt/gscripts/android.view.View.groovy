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
import com.android.ide.eclipse.adt.editors.layout.gscripts.IGraphics;
import com.android.ide.eclipse.adt.editors.layout.gscripts.INode;
import com.android.ide.eclipse.adt.editors.layout.gscripts.Rect;

/**
 * An {@link IViewRule} for android.view.View and all its derived classes.
 * This is the "root" rule, that is used whenever there is not more specific rule to apply.
 */
public class AndroidViewViewRule extends BaseViewRule {

    // TODO if there's nothing to implement here, I might as well remove it.
    // Before that, make sure the engine can deal with the lack of a base class
    // fallback when navigating the hierarchy.

    // TODO move all this to BaseViewRule
    void onSelected(IGraphics gc, INode selectedNode,
            String displayName, boolean isMultipleSelection) {
        Rect r = selectedNode.getBounds();

        if (!r.isValid()) {
            return;
        }

        gc.setLineWidth(1);
        gc.setLineStyle(IGraphics.LineStyle.LINE_SOLID);
        gc.drawRect(r);

        if (displayName == null || isMultipleSelection) {
            return;
        }

        int xs = r.x + 2;
        int ys = r.y - gc.getFontHeight();
        if (ys < 0) {
            ys = r.y + r.h;
        }
        gc.drawString(displayName, xs, ys);
    }

    void onChildSelected(IGraphics gc, INode parentNode, INode childNode) {
        Rect rp = parentNode.getBounds();
        Rect rc = childNode.getBounds();

        if (rp.isValid() && rc.isValid()) {
            gc.setLineWidth(1);
            gc.setLineStyle(IGraphics.LineStyle.LINE_DOT);

            // top line
            int m = rc.x + rc.w / 2;
            gc.drawLine(m, rc.y, m, rp.y);
            // bottom line
            gc.drawLine(m, rc.y + rc.h, m, rp.y + rp.h);
            // left line
            m = rc.y + rc.h / 2;
            gc.drawLine(rc.x, m, rp.x, m);
            // right line
            gc.drawLine(rc.x + rc.w, m, rp.x + rp.w, m);
        }
    }

}
