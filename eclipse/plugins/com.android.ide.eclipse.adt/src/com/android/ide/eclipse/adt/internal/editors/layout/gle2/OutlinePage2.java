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

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IContributionItem;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.viewers.IElementComparer;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.ITreeSelection;
import org.eclipse.jface.viewers.TreePath;
import org.eclipse.jface.viewers.TreeSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.INullSelectionListener;
import org.eclipse.ui.IPartListener;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.views.contentoutline.ContentOutlinePage;

import java.util.ArrayList;

/*
 * TODO -- missing features:
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
 * The page is created by {@link LayoutEditor#getAdapter(Class)}.
 * It sets itself as a listener on the site's selection service in order to be
 * notified of the canvas' selection changes.
 * The underlying page is also a selection provider (via IContentOutlinePage)
 * and as such it will broadcast selection changes to the site's selection service
 * (on which both the layout editor part and the property sheet page listen.)
 *
 * @since GLE2
 */
public class OutlinePage2 extends ContentOutlinePage
    implements ISelectionListener, INullSelectionListener {

    /**
     * RootWrapper is a workaround: we can't set the input of the treeview to its root
     * element, so we introduce a fake parent.
     */
    private final RootWrapper mRootWrapper = new RootWrapper();
	/** Part listener, to update the context menu associated with GraphicalEditorPart. */
    private PartListener mPartListener;

    public OutlinePage2() {
        super();
    }

    @Override
    public void createControl(Composite parent) {
        super.createControl(parent);

        TreeViewer tv = getTreeViewer();
        tv.setAutoExpandLevel(2);
        tv.setContentProvider(new ContentProvider());
        tv.setLabelProvider(new LabelProvider());
        tv.setInput(mRootWrapper);

        // The tree viewer will hold CanvasViewInfo instances, however these
        // change each time the canvas is reloaded. OTOH liblayout gives us
        // constant UiView keys which we can use to perform tree item comparisons.
        tv.setComparer(new IElementComparer() {
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

        // Listen to selection changes from the layout editor
        getSite().getPage().addSelectionListener(this);

        setupContextMenu();
    }

    @Override
    public void dispose() {
        mRootWrapper.setRoot(null);

        IWorkbenchWindow win = getSite().getWorkbenchWindow();
        win.getPartService().removePartListener(mPartListener);

        getSite().getPage().removeSelectionListener(this);
        super.dispose();
    }

    /**
     * Invoked by {@link LayoutCanvas} to set the model (aka the root view info).
     *
     * @param rootViewInfo The root of the view info hierarchy. Can be null.
     */
    public void setModel(CanvasViewInfo rootViewInfo) {
        mRootWrapper.setRoot(rootViewInfo);

        TreeViewer tv = getTreeViewer();
        if (tv != null) {
            Object[] expanded = tv.getExpandedElements();
            tv.refresh();
            tv.setExpandedElements(expanded);
        }
    }

    /**
     * Returns the current tree viewer selection. Shouldn't be null,
     * although it can be {@link TreeSelection#EMPTY}.
     */
    @Override
    public ISelection getSelection() {
        return super.getSelection();
    }

    /**
     * Sets the outline selection.
     *
     * @param selection Only {@link ITreeSelection} will be used, otherwise the
     *   selection will be cleared (including a null selection).
     */
    @Override
    public void setSelection(ISelection selection) {
        // TreeViewer should be able to deal with a null selection, but let's make it safe
        if (selection == null) {
            selection = TreeSelection.EMPTY;
        }

        super.setSelection(selection);

        TreeViewer tv = getTreeViewer();
        if (tv == null || !(selection instanceof ITreeSelection) || selection.isEmpty()) {
            return;
        }

        // auto-reveal the selection
        ITreeSelection treeSel = (ITreeSelection) selection;
        for (TreePath p : treeSel.getPaths()) {
            tv.expandToLevel(p, 1);
        }
    }

    /**
     * Listens to a workbench selection.
     * Only listen on selection coming from {@link LayoutEditor}, which avoid
     * picking up our own selections.
     */
    public void selectionChanged(IWorkbenchPart part, ISelection selection) {
        if (part instanceof LayoutEditor) {
            setSelection(selection);
        }
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

    // --- Content and Label Providers ---

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

    // --- Context Menu ---

    /**
     * This viewer uses its own actions that delegate to the ones given
     * by the {@link LayoutCanvas}. All the processing is actually handled
     * directly by the canvas and this viewer only gets refreshed as a
     * consequence of the canvas changing the XML model.
     * <p/>
     * To do that, we create actions that currently listen to the active
     * part and only defer to an active layout canvas.
     */
    private void setupContextMenu() {

        MenuManager mm = new MenuManager();
        mm.removeAll();

        final String prefix = LayoutCanvas.PREFIX_CANVAS_ACTION;
        mm.add(new DelegateAction(prefix + ActionFactory.CUT.getId()));
        mm.add(new DelegateAction(prefix + ActionFactory.COPY.getId()));
        mm.add(new DelegateAction(prefix + ActionFactory.PASTE.getId()));

        mm.add(new Separator());

        mm.add(new DelegateAction(prefix + ActionFactory.DELETE.getId()));
        mm.add(new DelegateAction(prefix + ActionFactory.SELECT_ALL.getId()));

        getControl().setMenu(mm.createContextMenu(getControl()));

        mPartListener = new PartListener(mm);
        IWorkbenchWindow win = getSite().getWorkbenchWindow();
        win.getPartService().addPartListener(mPartListener);
    }

    /**
     * Listen to part changes.
     * <p/>
     * This listener only cares specifically about GLE2's {@link GraphicalEditorPart} changing.
     * When the part changes, the delegate menu actions are refreshed to make sure that the
     * ouline's context menu always points to the current active layout canvas.
     */
    private static class PartListener implements IPartListener {
        private final MenuManager mMenuManager;

        public PartListener(MenuManager menuManager) {
            mMenuManager = menuManager;
        }

        public void partOpened(IWorkbenchPart part) {
            // pass
        }

        public void partActivated(IWorkbenchPart part) {
            GraphicalEditorPart gep = getGraphicalEditorPart(part);
            if (gep != null) {
                updateMenuActions(gep);
            }
        }

        public void partBroughtToTop(IWorkbenchPart part) {
            GraphicalEditorPart gep = getGraphicalEditorPart(part);
            if (gep != null) {
                updateMenuActions(gep);
            }
        }

        public void partDeactivated(IWorkbenchPart part) {
            GraphicalEditorPart gep = getGraphicalEditorPart(part);
            if (gep != null) {
                updateMenuActions(gep);
            }
        }

        public void partClosed(IWorkbenchPart part) {
            GraphicalEditorPart gep = getGraphicalEditorPart(part);
            if (gep != null) {
                updateMenuActions(gep);
            }
        }

        /**
         * Returns a non-null reference on the {@link GraphicalEditorPart} if this
         * is the part that changed, or null.
         */
        private GraphicalEditorPart getGraphicalEditorPart(IWorkbenchPart part) {
            if (part instanceof LayoutEditor) {
                part = ((LayoutEditor) part).getActiveEditor();
            }
            if (part instanceof GraphicalEditorPart) {
                return (GraphicalEditorPart) part;
            }
            return null;
        }

        /**
         * For every action contributed to our menu manager, ask the action to
         * update itself. The action will refresh its target action to match the
         * one from the given {@link GraphicalEditorPart}. If the editor part is
         * null, the delegate action will unlink from its target.
         */
        private void updateMenuActions(GraphicalEditorPart editorPart) {
            for (IContributionItem contrib : mMenuManager.getItems()) {
                if (contrib instanceof ActionContributionItem) {
                    IAction action = ((ActionContributionItem) contrib).getAction();
                    if (action instanceof DelegateAction) {
                        ((DelegateAction) action).updateFromEditorPart(editorPart);
                    }
                }
            }
        }
    }

    /**
     * An action that delegates its properties and behavior to a target action.
     * The target action can be null or it can change overtime, typically as the
     * layout canvas' editor part is activated or closed.
     */
    private static class DelegateAction extends Action {
        private IAction mTargetAction;
        private final String mCanvasActionId;

        public DelegateAction(String canvasActionId) {
            super(canvasActionId);
            mCanvasActionId = canvasActionId;
        }

        // --- Methods form IAction ---

        /** Returns the target action's {@link #isEnabled()} if defined, or false. */
        @Override
        public boolean isEnabled() {
            return mTargetAction == null ? false : mTargetAction.isEnabled();
        }

        /** Returns the target action's {@link #isChecked()} if defined, or false. */
        @Override
        public boolean isChecked() {
            return mTargetAction == null ? false : mTargetAction.isChecked();
        }

        /** Returns the target action's {@link #isHandled()} if defined, or false. */
        @Override
        public boolean isHandled() {
            return mTargetAction == null ? false : mTargetAction.isHandled();
        }

        /** Runs the target action if defined. */
        @Override
        public void run() {
            if (mTargetAction != null) {
                mTargetAction.run();
            }
            super.run();
        }

        /** Updates this action to delegate to its counterpart in the given editor part */
        public void updateFromEditorPart(GraphicalEditorPart editorPart) {
            if (editorPart == null) {
                mTargetAction = null;
            } else {
                mTargetAction = editorPart.getCanvasAction(mCanvasActionId);
            }

            if (mTargetAction != null) {
                setText(mTargetAction.getText());
                setId(mTargetAction.getId());
                setDescription(mTargetAction.getDescription());
                setImageDescriptor(mTargetAction.getImageDescriptor());
                setHoverImageDescriptor(mTargetAction.getHoverImageDescriptor());
                setDisabledImageDescriptor(mTargetAction.getDisabledImageDescriptor());
                setToolTipText(mTargetAction.getToolTipText());
                setActionDefinitionId(mTargetAction.getActionDefinitionId());
                setHelpListener(mTargetAction.getHelpListener());
                setAccelerator(mTargetAction.getAccelerator());
                setChecked(mTargetAction.isChecked());
                setEnabled(mTargetAction.isEnabled());
            } else {
                setEnabled(false);
            }
        }
    }
}
