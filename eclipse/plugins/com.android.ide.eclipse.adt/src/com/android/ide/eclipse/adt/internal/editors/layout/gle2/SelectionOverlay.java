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

import java.util.List;

/**
 * The {@link SelectionOverlay} paints the current selection as an overlay.
 */
public class SelectionOverlay extends Overlay {
    private final LayoutCanvas mCanvas;

    /**
     * Constructs a new {@link SelectionOverlay} tied to the given canvas.
     *
     * @param canvas the associated canvas
     */
    public SelectionOverlay(LayoutCanvas canvas) {
        mCanvas = canvas;
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
                    paintSelection(gcWrapper, s.getViewInfo(), node, isMultipleSelection);
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
                    double scale = mCanvas.getScale();

                    // Compute the location to display the help. This is done in
                    // layout coordinates, so we need to apply the scale in reverse
                    // when making pixel margins
                    // TODO: We could take the Canvas dimensions into account to see
                    // where there is more room.
                    // TODO: The scrollbars should take the presence of hint text
                    // into account.
                    int x, y;
                    if (b.w > b.h) {
                        x = (int) (b.x + 3 / scale);
                        y = (int) (b.y + b.h + 10 / scale);
                    } else {
                        x = (int) (b.x + b.w + 10 / scale);
                        y = (int) (b.y + 3 / scale);
                    }
                    gcWrapper.drawBoxedStrings(x, y, infos);
                }
            }
        }
    }

    /** Called by the canvas when a view is being selected. */
    private void paintSelection(IGraphics gc, CanvasViewInfo view, INode selectedNode,
            boolean isMultipleSelection) {
        Rect r = selectedNode.getBounds();

        if (!r.isValid()) {
            return;
        }

        gc.useStyle(DrawingStyle.SELECTION);
        gc.fillRect(r);
        gc.drawRect(r);

        // Paint sibling rectangles, if applicable
        List<CanvasViewInfo> siblings = view.getNodeSiblings();
        if (siblings != null) {
            for (CanvasViewInfo sibling : siblings) {
                if (sibling != view) {
                    r = SwtUtils.toRect(sibling.getSelectionRect());
                    gc.fillRect(r);
                    gc.drawRect(r);
                }
            }
        }

        /* Label hidden pending selection visual design
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
        */
    }
}
