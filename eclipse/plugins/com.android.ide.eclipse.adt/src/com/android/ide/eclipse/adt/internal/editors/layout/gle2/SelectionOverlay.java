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

import com.android.ide.eclipse.adt.internal.editors.layout.gre.RulesEngine;

import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Device;
import org.eclipse.swt.graphics.GC;

import java.util.List;

/**
 * The {@link SelectionOverlay} paints the current selection as an overlay.
 */
public class SelectionOverlay extends Overlay {
    /** CanvasSelection border color. */
    private Color mSelectionFgColor;

    /**
     * Constructs a new {@link SelectionOverlay} tied to the given canvas.
     */
    public SelectionOverlay() {
    }

    @Override
    public void create(Device device) {
        mSelectionFgColor = new Color(device, SwtDrawingStyle.SELECTION.getStrokeColor());
    }

    @Override
    public void dispose() {
        if (mSelectionFgColor != null) {
            mSelectionFgColor.dispose();
            mSelectionFgColor = null;
        }
    }

    /**
     * Paints the selection.
     *
     * @param selectionManager The {@link SelectionManager} holding the
     *            selection.
     * @param gc The graphics context to draw into.
     * @param gcWrapper The graphics context wrapper for the layout rules to use.
     * @param rulesEngine The {@link RulesEngine} holding the rules.
     */
    public void paint(SelectionManager selectionManager, GC gc, GCWrapper gcWrapper,
            RulesEngine rulesEngine) {
        List<CanvasSelection> selections = selectionManager.getSelections();
        int n = selections.size();
        if (n > 0) {
            boolean isMultipleSelection = n > 1;

            if (n == 1) {
                gc.setForeground(mSelectionFgColor);
                selections.get(0).paintParentSelection(rulesEngine, gcWrapper);
            }

            for (CanvasSelection s : selections) {
                if (s.isRoot()) {
                    // The root selection is never painted
                    continue;
                }
                gc.setForeground(mSelectionFgColor);
                s.paintSelection(rulesEngine, gcWrapper, isMultipleSelection);
            }
        }
    }

}
