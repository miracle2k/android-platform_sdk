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
package com.android.ide.eclipse.adt.internal.editors.layout;

import com.android.ide.eclipse.adt.internal.editors.layout.gle2.LayoutCanvas;

import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.part.EditorActionBarContributor;

/**
 * Action contributor responsible for updating the global action registrations in the
 * shared action bar for the editor instances
 */
public class LayoutEditorActionContributor extends EditorActionBarContributor {

    public LayoutEditorActionContributor() {
        super();
    }

    @Override
    public void setActiveEditor(IEditorPart part) {
        IActionBars bars = getActionBars();
        if (part instanceof LayoutEditor) {
            LayoutCanvas canvas = ((LayoutEditor)part).getGraphicalEditor().getCanvasControl();
            if (canvas != null) {
                canvas.updateGlobalActions(bars);
            }
        }
    }
}
