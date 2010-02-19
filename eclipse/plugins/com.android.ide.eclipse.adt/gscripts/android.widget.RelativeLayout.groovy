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

package com.android.adt.gscripts;

/**
 * An {@link IViewRule} for android.widget.RelativeLayout and all its derived classes.
 */
public class AndroidWidgetRelativeLayoutRule extends BaseLayout {

    // ==== Drag'n'drop support ====

    // TODO

    // ==== Selection ====

    /**
     * Display some relation layout information on a selected child.
     */
    void onChildSelected(IGraphics gc, INode parentNode, INode childNode) {
        super.onChildSelected(gc, parentNode, childNode);

        // Get the top parent, to display data under it
        // (TODO later we could make an API to do that officially)
        INode topParent = parentNode;
        while (true) {
            INode p = topParent.getParent();
            if (p == null) {
                break;
            } else {
                topParent = p;
            }
        }

        Rect b = topParent.getBounds();
        if (!b.isValid()) {
            return;
        }

        def infos = [];

        def addAttr = {
            def a = childNode.getStringAttr("layout_${it}");
            if (a) {
                infos += "${it}: ${a}";
            }
        }

        addAttr("above");
        addAttr("below");
        addAttr("toLeftOf");
        addAttr("toRightOf");
        addAttr("alignBaseline");
        addAttr("alignTop");
        addAttr("alignBottom");
        addAttr("alignLeft");
        addAttr("alignRight");
        addAttr("alignParentTop");
        addAttr("alignParentBottom");
        addAttr("alignParentLeft");
        addAttr("alignParentRight");
        addAttr("alignWithParentMissing");
        addAttr("centerHorizontal");
        addAttr("centerInParent");
        addAttr("centerVertical");

        if (infos) {
            gc.setForeground(gc.registerColor(0x00222222));
            int x = b.x + 10;
            int y = b.y + b.h + 10;
            int h = gc.getFontHeight();
            infos.each {
                y += h;
                gc.drawString(it, x, y);
            }
        }
    }

}
