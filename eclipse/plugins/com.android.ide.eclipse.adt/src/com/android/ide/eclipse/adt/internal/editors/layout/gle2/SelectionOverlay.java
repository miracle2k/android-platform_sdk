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

package com.android.ide.eclipse.adt.internal.editors.layout.gle2;

import com.android.ide.common.api.DrawingStyle;
import com.android.ide.common.api.IGraphics;
import com.android.ide.common.api.INode;
import com.android.ide.common.api.Rect;
import com.android.ide.eclipse.adt.internal.editors.layout.gre.NodeProxy;
import com.android.ide.eclipse.adt.internal.editors.layout.gre.RulesEngine;

import java.util.Collections;
import java.util.List;

/**
 * The {@link SelectionOverlay} paints the current selection as an overlay.
 */
public class SelectionOverlay extends Overlay {
    /**
     * Constructs a new {@link SelectionOverlay} tied to the given canvas.
     */
    public SelectionOverlay() {
    }

    /**
     * Paints the selection.
     *
     * @param selectionManager The {@link SelectionManager} holding the
     *            selection.
     * @param gcWrapper The graphics context wrapper for the layout rules to use.
     * @param rulesEngine The {@link RulesEngine} holding the rules.
     */
    public void paint(SelectionManager selectionManager, GCWrapper gcWrapper,
            RulesEngine rulesEngine) {
        List<SelectionItem> selections = selectionManager.getSelections();
        int n = selections.size();
        if (n > 0) {
            boolean isMultipleSelection = n > 1;
            for (SelectionItem s : selections) {
                if (s.isRoot()) {
                    // The root selection is never painted
                    continue;
                }

                NodeProxy node = s.getNode();
                if (node != null) {
                    String name = s.getName();
                    paintSelection(gcWrapper, node, name, isMultipleSelection);
                }
            }

            if (n == 1) {
                NodeProxy node = selections.get(0).getNode();
                if (node != null) {
                    paintHints(gcWrapper, node, rulesEngine);
                }
            }
        }
    }

    /** Paint hint for current selection */
    private void paintHints(GCWrapper gcWrapper, NodeProxy node, RulesEngine rulesEngine) {
        INode parent = node.getParent();
        if (parent instanceof NodeProxy) {
            NodeProxy parentNode = (NodeProxy) parent;

            // Get the top parent, to display data under it
            INode topParent = parentNode;
            while (true) {
                INode p = topParent.getParent();
                if (p == null) {
                    break;
                }
                topParent = p;
            }

            Rect b = topParent.getBounds();
            if (b.isValid()) {
                List<String> infos = rulesEngine.callGetSelectionHint(parentNode, node);
                if (infos != null && infos.size() > 0) {
                    gcWrapper.useStyle(DrawingStyle.HELP);
                    int x = b.x + 10;
                    int y = b.y + b.h + 10;
                    gcWrapper.drawBoxedStrings(x, y, infos);
                }
            }
        }
    }

    /** Called by the canvas when a view is being selected. */
    private void paintSelection(IGraphics gc, INode selectedNode, String displayName,
            boolean isMultipleSelection) {
        Rect r = selectedNode.getBounds();

        if (!r.isValid()) {
            return;
        }

        gc.useStyle(DrawingStyle.SELECTION);
        gc.fillRect(r);
        gc.drawRect(r);

        if (displayName == null || isMultipleSelection) {
            return;
        }

        int xs = r.x + 2;
        int ys = r.y - gc.getFontHeight() - 4;
        if (ys < 0) {
            ys = r.y + r.h + 3;
        }
        gc.useStyle(DrawingStyle.HELP);
        gc.drawBoxedStrings(xs, ys, Collections.singletonList(displayName));
    }
}
