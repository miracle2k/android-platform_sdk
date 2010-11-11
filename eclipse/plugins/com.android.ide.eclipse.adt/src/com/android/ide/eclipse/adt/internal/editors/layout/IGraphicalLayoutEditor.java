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

package com.android.ide.eclipse.adt.internal.editors.layout;

import com.android.ide.eclipse.adt.internal.editors.uimodel.UiDocumentNode;
import com.android.ide.eclipse.adt.internal.editors.uimodel.UiElementNode;
import com.android.layoutlib.api.ILayoutResult;

import org.eclipse.core.resources.IFile;
import org.eclipse.ui.IEditorPart;

import java.util.Set;

/**
 * Interface defining what {@link LayoutEditor} expects from a GraphicalLayoutEditor part.
 *
 * @since GLE2
 */
public interface IGraphicalLayoutEditor extends IEditorPart {

    /**
     * Opens and initialize the editor with a new file.
     * @param file the file being edited.
     */
    abstract void openFile(IFile file);

    /**
     * Resets the editor with a replacement file.
     * @param file the replacement file.
     */
    abstract void replaceFile(IFile file);

    /**
     * Resets the editor with a replacement file coming from a config change in the config
     * selector.
     * @param file the replacement file.
     */
    abstract void changeFileOnNewConfig(IFile file);

    /**
     * Responds to a target change for the project of the edited file
     */
    abstract void onTargetChange();

    /**
     * Responds to an SDK reload/change.
     */
    abstract void onSdkChange();

    /**
     * Callback for XML model changed. Only update/recompute the layout if the editor is visible
     */
    abstract void onXmlModelChanged();

    /**
     * Responds to a page change that made the Graphical editor page the activated page.
     */
    abstract void activated();

    /**
     * Responds to a page change that made the Graphical editor page the deactivated page
     */
    abstract void deactivated();

    abstract void reloadPalette();

    abstract void recomputeLayout();

    abstract UiDocumentNode getModel();

    abstract LayoutEditor getLayoutEditor();

    /**
     * Renders the given model, using this editor's theme and screen settings, and returns
     * the result as an {@link ILayoutResult}. Any error messages will be written to the
     * editor's error area.
     *
     * @param model the model to be rendered, which can be different than the editor's own
     *            {@link #getModel()}.
     * @param width the width to use for the layout
     * @param height the height to use for the layout
     * @param explodeNodes a set of nodes to explode, or null for none
     * @return the resulting rendered image wrapped in an {@link ILayoutResult}
     */
    abstract ILayoutResult render(UiDocumentNode model,
            int width, int height, Set<UiElementNode> explodeNodes);

}
