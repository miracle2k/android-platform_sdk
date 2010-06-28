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
import com.android.ide.eclipse.adt.internal.editors.layout.uimodel.UiViewElementNode;
import com.android.ide.eclipse.adt.internal.editors.ui.ErrorImageComposite;
import com.android.ide.eclipse.adt.internal.editors.uimodel.UiElementNode;

import org.eclipse.jface.viewers.IElementComparer;
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

/*
 * TODO -- missing features:
 * - synchronize selection tree=>canvas
 * - right-click context menu *shared* with the one from canvas (simply delegate action)
 * - drag'n'drop initiated from Palette to Outline
 * - drag'n'drop from Outline to Outline
 * - drag'n'drop from Canvas to Outline
 * - drag'n'drop from Outline to Canvas
 * - => Check if we can handle all the d'n'd cases a simply delegating the action to the canvas.
 *      There's a *lot* of logic in the CanvasDropListener we don't want to replicate.
 *      That should be fairly trivial, except that we can't provide X/Y coordinates in the drop
 *      move. We could just fake them by using the topleft/middle point of the tree item's bounds
 *      or something like that.
 */

/**
 * An outline page for the GLE2 canvas view.
 * <p/>
 * The page is created by {@link LayoutEditor}.
 * Selection is synchronized by {@link LayoutCanvas}.
 */
public class OutlinePage2 implements IContentOutlinePage {

    /**
     * The current TreeViewer. This is created in {@link #createControl(Composite)}.
     * It is entirely possible for callbacks to be invoked *before* the tree viewer
     * is created, for example if a non-yet shown canvas is modified and it refreshes
     * the model of a non-yet shown outline.
     */
    private TreeViewer mTreeViewer;

    /**
     * RootWrapper is a workaround: we can't set the input of the treeview to its root
     * element, so we introduce a fake parent.
     */
    private final RootWrapper mRootWrapper = new RootWrapper();

    public OutlinePage2() {
    }

    public void createControl(Composite parent) {
        Tree tree = new Tree(parent, SWT.MULTI /*style*/);
        mTreeViewer = new TreeViewer(tree);

        mTreeViewer.setAutoExpandLevel(2);
        mTreeViewer.setContentProvider(new ContentProvider());
        mTreeViewer.setLabelProvider(new LabelProvider());
        mTreeViewer.setInput(mRootWrapper);

        // The tree viewer will hold CanvasViewInfo instances, however these
        // change each time the canvas is reloaded. OTOH liblayout gives us
        // constant UiView keys which we can use to perform tree item comparisons.
        mTreeViewer.setComparer(new IElementComparer() {
            public int hashCode(Object element) {
                if (element instanceof CanvasViewInfo) {
                    UiViewElementNode key = ((CanvasViewInfo) element).getUiViewKey();
                    if (key != null) {
                        return key.hashCode();
                    }
                }
                if (element != null) {
                    return element.hashCode();
                }
                return 0;
            }

            public boolean equals(Object a, Object b) {
                if (a instanceof CanvasViewInfo && b instanceof CanvasViewInfo) {
                    UiViewElementNode keyA = ((CanvasViewInfo) a).getUiViewKey();
                    UiViewElementNode keyB = ((CanvasViewInfo) b).getUiViewKey();
                    if (keyA != null) {
                        return keyA.equals(keyB);
                    }
                }
                if (a != null) {
                    return a.equals(b);
                }
                return false;
            }
        });
    }

    public void dispose() {
        Control c = getControl();
        if (c != null && !c.isDisposed()) {
            mTreeViewer = null;
            c.dispose();
        }
        mRootWrapper.setRoot(null);
    }

    public void setModel(CanvasViewInfo rootViewInfo) {
        mRootWrapper.setRoot(rootViewInfo);

        if (mTreeViewer != null) {
            Object[] expanded = mTreeViewer.getExpandedElements();
            mTreeViewer.refresh();
            mTreeViewer.setExpandedElements(expanded);
        }
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
            mTreeViewer.expandToLevel(paths[i], 1);
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
     * In theory, the root of the model should be the input of the {@link TreeViewer},
     * which would be the root {@link CanvasViewInfo}.
     * That means in theory {@link ContentProvider#getElements(Object)} should return
     * its own input as the single root node.
     * <p/>
     * However as described in JFace Bug 9262, this case is not properly handled by
     * a {@link TreeViewer} and leads to an infinite recursion in the tree viewer.
     * See https://bugs.eclipse.org/bugs/show_bug.cgi?id=9262
     * <p/>
     * The solution is to wrap the tree viewer input in a dummy root node that acts
     * as a parent. This class does just that.
     */
    private static class RootWrapper {
        private CanvasViewInfo mRoot;

        public void setRoot(CanvasViewInfo root) {
            mRoot = root;
        }

        public CanvasViewInfo getRoot() {
            return mRoot;
        }
    }

    /**
     * Content provider for the Outline model.
     * Objects are going to be {@link CanvasViewInfo}.
     */
    private static class ContentProvider implements ITreeContentProvider {

        public Object[] getChildren(Object element) {
            if (element instanceof RootWrapper) {
                CanvasViewInfo root = ((RootWrapper)element).getRoot();
                if (root != null) {
                    return new Object[] { root };
                }
            }
            if (element instanceof CanvasViewInfo) {
                ArrayList<CanvasViewInfo> children = ((CanvasViewInfo) element).getChildren();
                if (children != null) {
                    return children.toArray();
                }
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
                ArrayList<CanvasViewInfo> children = ((CanvasViewInfo) element).getChildren();
                if (children != null) {
                    return children.size() > 0;
                }
            }
            return false;
        }

        /**
         * Returns the root element.
         * Semantically, the root element is the single top-level XML element of the XML layout.
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

        public void addListener(ILabelProviderListener listener) {
            // pass
        }

        public void dispose() {
            // pass
        }

        public boolean isLabelProperty(Object element, String property) {
            // pass
            return false;
        }

        public void removeListener(ILabelProviderListener listener) {
            // pass
        }
    }

}
