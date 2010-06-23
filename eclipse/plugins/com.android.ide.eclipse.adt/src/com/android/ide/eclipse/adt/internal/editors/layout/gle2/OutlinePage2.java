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

import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.internal.editors.descriptors.ElementDescriptor;
import com.android.ide.eclipse.adt.internal.editors.layout.LayoutEditor;
import com.android.ide.eclipse.adt.internal.editors.ui.ErrorImageComposite;
import com.android.ide.eclipse.adt.internal.editors.uimodel.UiDocumentNode;
import com.android.ide.eclipse.adt.internal.editors.uimodel.UiElementNode;

import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.TreePath;
import org.eclipse.jface.viewers.TreeSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.views.contentoutline.IContentOutlinePage;

import java.util.ArrayList;

/**
 * An outline page for the GLE2 canvas view.
 * <p/>
 * The page is created by {@link LayoutEditor}.
 * Selection is synchronized by {@link LayoutCanvas}.
 */
public class OutlinePage2 implements IContentOutlinePage {

    private TreeViewer mTreeViewer;
    private UiDocumentNode mUiDocumentNode;

    public OutlinePage2() {
    }

    public void createControl(Composite parent) {
        Tree tree = new Tree(parent, SWT.MULTI /*style*/);
        mTreeViewer = new TreeViewer(tree);

        mTreeViewer.setContentProvider(new ContentProvider());
        mTreeViewer.setLabelProvider(new LabelProvider());
    }

    public void dispose() {
        Control c = getControl();
        if (c != null && !c.isDisposed()) {
            mTreeViewer = null;
            c.dispose();
        }
    }

    public void setModel(CanvasViewInfo rootViewInfo) {
        mTreeViewer.setInput(rootViewInfo);
    }

    public Control getControl() {
        return mTreeViewer == null ? null : mTreeViewer.getControl();
    }

    public ISelection getSelection() {
        return mTreeViewer == null ? null : mTreeViewer.getSelection();
    }

    /**
     * Selects the given {@link CanvasViewInfo} elements and reveals them.
     *
     * @param selectedInfos The {@link CanvasViewInfo} elements to selected.
     *   This can be null or empty to remove any selection.
     */
    public void selectAndReveal(CanvasViewInfo[] selectedInfos) {
        if (mTreeViewer == null) {
            return;
        }

        if (selectedInfos == null || selectedInfos.length == 0) {
            mTreeViewer.setSelection(TreeSelection.EMPTY);
            return;
        }

        int n = selectedInfos.length;
        TreePath[] paths = new TreePath[n];
        for (int i = 0; i < n; i++) {
            ArrayList<Object> segments = new ArrayList<Object>();
            CanvasViewInfo vi = selectedInfos[i];
            while (vi != null) {
                segments.add(0, vi);
                vi = vi.getParent();
            }
            paths[i] = new TreePath(segments.toArray());
        }

        mTreeViewer.setSelection(new TreeSelection(paths), true /*reveal*/);
    }

    public void setSelection(ISelection selection) {
        if (mTreeViewer != null) {
            mTreeViewer.setSelection(selection);
        }
    }

    public void setFocus() {
        Control c = getControl();
        if (c != null) {
            c.setFocus();
        }
    }

    public void addSelectionChangedListener(ISelectionChangedListener listener) {
        if (mTreeViewer != null) {
            mTreeViewer.addSelectionChangedListener(listener);
        }
    }

    public void removeSelectionChangedListener(ISelectionChangedListener listener) {
        if (mTreeViewer != null) {
            mTreeViewer.removeSelectionChangedListener(listener);
        }
    }

    public void setActionBars(IActionBars barts) {
        // TODO Auto-generated method stub
    }

    // ----

    /**
     * Content provider for the Outline model.
     * Objects are going to be {@link CanvasViewInfo}.
     */
    private static class ContentProvider implements ITreeContentProvider {

        public Object[] getChildren(Object parentElement) {
            if (parentElement instanceof CanvasViewInfo) {
                return ((CanvasViewInfo) parentElement).getChildren().toArray();
            }
            return new Object[0];
        }

        public Object getParent(Object element) {
            if (element instanceof CanvasViewInfo) {
                return ((CanvasViewInfo) element).getParent();
            }
            return null;
        }

        public boolean hasChildren(Object element) {
            if (element instanceof CanvasViewInfo) {
                return ((CanvasViewInfo) element).getChildren().size() > 0;
            }
            return false;
        }

        /**
         * Returns the root elements for the given input.
         * Here the root elements are all the children of the input model.
         */
        public Object[] getElements(Object inputElement) {
            return getChildren(inputElement);
        }

        public void dispose() {
            // pass
        }

        public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
            // pass
        }

    }

    /**
     * Label provider for the Outline model.
     * Objects are going to be {@link CanvasViewInfo}.
     */
    private static class LabelProvider implements ILabelProvider {

        /**
         * Returns the element's logo with a fallback on the android logo.
         */
        public Image getImage(Object element) {
            if (element instanceof CanvasViewInfo) {
                element = ((CanvasViewInfo) element).getUiViewKey();
            }

            if (element instanceof UiElementNode) {
                UiElementNode node = (UiElementNode) element;
                ElementDescriptor desc = node.getDescriptor();
                if (desc != null) {
                    Image img = desc.getIcon();
                    if (img != null) {
                        if (node.hasError()) {
                            return new ErrorImageComposite(img).createImage();
                        } else {
                            return img;
                        }
                    }
                }
            }

            return AdtPlugin.getAndroidLogo();
        }

        /**
         * Uses UiElementNode.shortDescription for the label for this tree item.
         */
        public String getText(Object element) {
            if (element instanceof CanvasViewInfo) {
                element = ((CanvasViewInfo) element).getUiViewKey();
            }

            if (element instanceof UiElementNode) {
                UiElementNode node = (UiElementNode) element;
                return node.getShortDescription();
            }

            return element == null ? "(null)" : element.toString();  //$NON-NLS-1$
        }

        public void addListener(ILabelProviderListener arg0) {
            // TODO Auto-generated method stub

        }

        public void dispose() {
            // TODO Auto-generated method stub

        }

        public boolean isLabelProperty(Object arg0, String arg1) {
            // TODO Auto-generated method stub
            return false;
        }

        public void removeListener(ILabelProviderListener arg0) {
            // TODO Auto-generated method stub

        }


    }

}
