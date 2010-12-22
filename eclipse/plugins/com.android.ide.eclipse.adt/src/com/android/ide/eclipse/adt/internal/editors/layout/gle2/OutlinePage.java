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

import static org.eclipse.jface.action.IAction.AS_PUSH_BUTTON;

import com.android.ide.common.api.INode;
import com.android.ide.common.api.InsertType;
import com.android.ide.common.layout.BaseLayoutRule;
import com.android.ide.common.layout.Pair;
import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.internal.editors.descriptors.ElementDescriptor;
import com.android.ide.eclipse.adt.internal.editors.layout.LayoutEditor;
import com.android.ide.eclipse.adt.internal.editors.layout.gle2.IncludeFinder.Reference;
import com.android.ide.eclipse.adt.internal.editors.layout.gre.NodeProxy;
import com.android.ide.eclipse.adt.internal.editors.layout.uimodel.UiViewElementNode;
import com.android.ide.eclipse.adt.internal.editors.ui.ErrorImageComposite;
import com.android.ide.eclipse.adt.internal.editors.uimodel.UiElementNode;
import com.android.sdklib.annotations.VisibleForTesting;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IContributionItem;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
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
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.MenuDetectEvent;
import org.eclipse.swt.events.MenuDetectListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.INullSelectionListener;
import org.eclipse.ui.IPageLayout;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.views.contentoutline.ContentOutlinePage;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * An outline page for the layout canvas view.
 * <p/>
 * The page is created by {@link LayoutEditor#getAdapter(Class)}. This means
 * we have *one* instance of the outline page per open canvas editor.
 * <p/>
 * It sets itself as a listener on the site's selection service in order to be
 * notified of the canvas' selection changes.
 * The underlying page is also a selection provider (via IContentOutlinePage)
 * and as such it will broadcast selection changes to the site's selection service
 * (on which both the layout editor part and the property sheet page listen.)
 */
public class OutlinePage extends ContentOutlinePage
    implements ISelectionListener, INullSelectionListener {

    /**
     * The graphical editor that created this outline.
     */
    private final GraphicalEditorPart mGraphicalEditorPart;

    /**
     * RootWrapper is a workaround: we can't set the input of the TreeView to its root
     * element, so we introduce a fake parent.
     */
    private final RootWrapper mRootWrapper = new RootWrapper();

    /**
     * Menu manager for the context menu actions.
     * The actions delegate to the current GraphicalEditorPart.
     */
    private MenuManager mMenuManager;

    /** Action to Select All in the tree */
    private final Action mTreeSelectAllAction = new Action() {
        @Override
        public void run() {
            getTreeViewer().getTree().selectAll();
            OutlinePage.this.fireSelectionChanged(getSelection());
        }

        @Override
        public String getId() {
            return ActionFactory.SELECT_ALL.getId();
        }
    };

    /** Action for moving items up in the tree */
    private Action mMoveUpAction = new Action("Move Up\t-", AS_PUSH_BUTTON) {

        @Override
        public String getId() {
            return "adt.outline.moveup"; //$NON-NLS-1$
        }

        @Override
        public boolean isEnabled() {
            return canMove(false);
        }

        @Override
        public void run() {
            move(false);
        }
    };

    /** Action for moving items down in the tree */
    private Action mMoveDownAction = new Action("Move Down\t+", AS_PUSH_BUTTON) {

        @Override
        public String getId() {
            return "adt.outline.movedown"; //$NON-NLS-1$
        }

        @Override
        public boolean isEnabled() {
            return canMove(true);
        }

        @Override
        public void run() {
            move(true);
        }
    };

    public OutlinePage(GraphicalEditorPart graphicalEditorPart) {
        super();
        mGraphicalEditorPart = graphicalEditorPart;
    }

    @Override
    public void createControl(Composite parent) {
        super.createControl(parent);

        TreeViewer tv = getTreeViewer();
        tv.setAutoExpandLevel(2);
        tv.setContentProvider(new ContentProvider());
        tv.setLabelProvider(new LabelProvider());
        tv.setInput(mRootWrapper);

        int supportedOperations = DND.DROP_COPY | DND.DROP_MOVE;
        Transfer[] transfers = new Transfer[] {
            SimpleXmlTransfer.getInstance()
        };

        tv.addDropSupport(supportedOperations, transfers, new OutlineDropListener(this, tv));
        tv.addDragSupport(supportedOperations, transfers, new OutlineDragListener(this, tv));

        // The tree viewer will hold CanvasViewInfo instances, however these
        // change each time the canvas is reloaded. OTOH layoutlib gives us
        // constant UiView keys which we can use to perform tree item comparisons.
        tv.setComparer(new IElementComparer() {
            public int hashCode(Object element) {
                if (element instanceof CanvasViewInfo) {
                    UiViewElementNode key = ((CanvasViewInfo) element).getUiViewNode();
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
                    UiViewElementNode keyA = ((CanvasViewInfo) a).getUiViewNode();
                    UiViewElementNode keyB = ((CanvasViewInfo) b).getUiViewNode();
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
        tv.addDoubleClickListener(new IDoubleClickListener() {
            public void doubleClick(DoubleClickEvent event) {
                // Front properties panel; its selection is already linked
                IWorkbenchPage page = getSite().getPage();
                try {
                    page.showView(IPageLayout.ID_PROP_SHEET, null, IWorkbenchPage.VIEW_ACTIVATE);
                } catch (PartInitException e) {
                    AdtPlugin.log(e, "Could not activate property sheet");
                }
            }
        });

        setupContextMenu();

        // Listen to selection changes from the layout editor
        getSite().getPage().addSelectionListener(this);
        getControl().addDisposeListener(new DisposeListener() {

            public void widgetDisposed(DisposeEvent e) {
                dispose();
            }
        });

        Tree tree = tv.getTree();
        tree.addKeyListener(new KeyListener() {

            public void keyPressed(KeyEvent e) {
                if (e.character == '-') {
                    if (mMoveUpAction.isEnabled()) {
                        mMoveUpAction.run();
                    }
                } else if (e.character == '+') {
                    if (mMoveDownAction.isEnabled()) {
                        mMoveDownAction.run();
                    }
                }
            }

            public void keyReleased(KeyEvent e) {
            }
        });
    }

    @Override
    public void dispose() {
        mRootWrapper.setRoot(null);

        getSite().getPage().removeSelectionListener(this);
        super.dispose();
    }

    /**
     * Invoked by {@link LayoutCanvas} to set the model (a.k.a. the root view info).
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
            // Ensure that the root is expanded
            tv.expandToLevel(rootViewInfo, 2);
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

    /** Return the {@link CanvasViewInfo} associated with the given TreeItem's data field */
    /* package */ static CanvasViewInfo getViewInfo(Object viewData) {
        if (viewData instanceof RootWrapper) {
            return ((RootWrapper) viewData).getRoot();
        }
        if (viewData instanceof CanvasViewInfo) {
            return (CanvasViewInfo) viewData;
        }
        return null;
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
    private class LabelProvider implements ILabelProvider {

        /**
         * Returns the element's logo with a fallback on the android logo.
         */
        public Image getImage(Object element) {
            if (element instanceof CanvasViewInfo) {
                element = ((CanvasViewInfo) element).getUiViewNode();
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
            CanvasViewInfo vi = null;
            if (element instanceof CanvasViewInfo) {
                vi = (CanvasViewInfo) element;
                element = vi.getUiViewNode();
            }

            if (element instanceof UiElementNode) {
                UiElementNode node = (UiElementNode) element;
                return node.getShortDescription();
            } else if (element == null && vi != null) {
                // It's an inclusion-context
                Reference includedWithin = mGraphicalEditorPart.getIncludedWithin();
                if (includedWithin != null) {
                    return includedWithin.getDisplayName();
                }
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
     */
    private void setupContextMenu() {

        mMenuManager = new MenuManager();
        mMenuManager.removeAll();

        mMenuManager.add(mMoveUpAction);
        mMenuManager.add(mMoveDownAction);
        mMenuManager.add(new Separator());

        final String prefix = LayoutCanvas.PREFIX_CANVAS_ACTION;
        mMenuManager.add(new DelegateAction(prefix + ActionFactory.CUT.getId()));
        mMenuManager.add(new DelegateAction(prefix + ActionFactory.COPY.getId()));
        mMenuManager.add(new DelegateAction(prefix + ActionFactory.PASTE.getId()));

        mMenuManager.add(new Separator());

        mMenuManager.add(new DelegateAction(prefix + ActionFactory.DELETE.getId()));
        mMenuManager.add(new DelegateAction(prefix + ActionFactory.SELECT_ALL.getId()));

        mMenuManager.addMenuListener(new IMenuListener() {
            public void menuAboutToShow(IMenuManager manager) {
                // Update all actions to match their LayoutCanvas counterparts
                for (IContributionItem contrib : manager.getItems()) {
                    if (contrib instanceof ActionContributionItem) {
                        IAction action = ((ActionContributionItem) contrib).getAction();
                        if (action instanceof DelegateAction) {
                            ((DelegateAction) action).updateFromEditorPart(mGraphicalEditorPart);
                        }
                    }
                }
            }
        });

        new DynamicContextMenu(
                mGraphicalEditorPart.getLayoutEditor(),
                mGraphicalEditorPart.getCanvasControl(),
                mMenuManager);

        getControl().setMenu(mMenuManager.createContextMenu(getControl()));

        // Update Move Up/Move Down state only when the menu is opened
        getControl().addMenuDetectListener(new MenuDetectListener() {
            public void menuDetected(MenuDetectEvent e) {
                mMenuManager.update(IAction.ENABLED);
            }
        });
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
            setId(canvasActionId);
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

        /**
         * Updates this action to delegate to its counterpart in the given editor part
         *
         * @param editorPart The editor being updated
         */
        public void updateFromEditorPart(GraphicalEditorPart editorPart) {
            LayoutCanvas canvas = editorPart == null ? null : editorPart.getCanvasControl();
            if (canvas == null) {
                mTargetAction = null;
            } else {
                mTargetAction = canvas.getAction(mCanvasActionId);
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

    /** Returns the associated editor with this outline */
    /* package */GraphicalEditorPart getEditor() {
        return mGraphicalEditorPart;
    }

    @Override
    public void setActionBars(IActionBars actionBars) {
        super.setActionBars(actionBars);

        // Map Outline actions to canvas actions such that they share Undo context etc
        LayoutCanvas canvas = mGraphicalEditorPart.getCanvasControl();
        canvas.updateGlobalActions(actionBars);

        // Special handling for Select All since it's different than the canvas (will
        // include selecting the root etc)
        actionBars.setGlobalActionHandler(mTreeSelectAllAction.getId(), mTreeSelectAllAction);
        actionBars.updateActionBars();
    }

    // ---- Move Up/Down Support ----

    /** Returns true if the current selected item can be moved */
    private boolean canMove(boolean forward) {
        CanvasViewInfo viewInfo = getSingleSelectedItem();
        if (viewInfo != null) {
            UiViewElementNode node = viewInfo.getUiViewNode();
            if (forward) {
                return findNext(node) != null;
            } else {
                return findPrevious(node) != null;
            }
        }

        return false;
    }

    /** Moves the current selected item down (forward) or up (not forward) */
    private void move(boolean forward) {
        CanvasViewInfo viewInfo = getSingleSelectedItem();
        if (viewInfo != null) {
            final Pair<UiViewElementNode, Integer> target;
            UiViewElementNode selected = viewInfo.getUiViewNode();
            if (forward) {
                target = findNext(selected);
            } else {
                target = findPrevious(selected);
            }
            if (target != null) {
                final LayoutCanvas canvas = mGraphicalEditorPart.getCanvasControl();
                final SelectionManager selectionManager = canvas.getSelectionManager();
                final ArrayList<SelectionItem> dragSelection = new ArrayList<SelectionItem>();
                dragSelection.add(selectionManager.createSelection(viewInfo));
                SelectionManager.sanitize(dragSelection);

                if (!dragSelection.isEmpty()) {
                    final SimpleElement[] elements = SelectionItem.getAsElements(dragSelection);
                    UiViewElementNode parentNode = target.getFirst();
                    final NodeProxy targetNode = canvas.getNodeFactory().create(parentNode);

                    // Record children of the target right before the drop (such that we
                    // can find out after the drop which exact children were inserted)
                    Set<INode> children = new HashSet<INode>();
                    for (INode node : targetNode.getChildren()) {
                        children.add(node);
                    }

                    String label = MoveGesture.computeUndoLabel(targetNode,
                            elements, DND.DROP_MOVE);
                    canvas.getLayoutEditor().wrapUndoEditXmlModel(label, new Runnable() {
                        public void run() {
                            canvas.getRulesEngine().setInsertType(InsertType.MOVE);
                            int index = target.getSecond();
                            BaseLayoutRule.insertAt(targetNode, elements, false, index);
                            canvas.getClipboardSupport().deleteSelection("Remove", dragSelection);
                        }
                    });

                    // Now find out which nodes were added, and look up their
                    // corresponding CanvasViewInfos
                    final List<INode> added = new ArrayList<INode>();
                    for (INode node : targetNode.getChildren()) {
                        if (!children.contains(node)) {
                            added.add(node);
                        }
                    }

                    selectionManager.updateOutlineSelection(added);
                }
            }
        }
    }

    /**
     * Returns the {@link CanvasViewInfo} for the currently selected item, or null if
     * there are no or multiple selected items
     *
     * @return the current selected item if there is exactly one item selected
     */
    private CanvasViewInfo getSingleSelectedItem() {
        TreeItem[] selection = getTreeViewer().getTree().getSelection();
        if (selection.length == 1) {
            return getViewInfo(selection[0].getData());
        }

        return null;
    }


    /** Returns the pair [parent,index] of the next node (when iterating forward) */
    @VisibleForTesting
    /* package */ static Pair<UiViewElementNode, Integer> findNext(UiViewElementNode node) {
        UiElementNode parent = node.getUiParent();
        if (parent == null) {
            return null;
        }

        UiElementNode next = node.getUiNextSibling();
        if (next != null) {
            if (next.getDescriptor().hasChildren()) {
                return getFirstPosition(next);
            } else {
                return getPositionAfter(next);
            }
        }

        next = parent.getUiNextSibling();
        if (next != null) {
            return getPositionBefore(next);
        } else {
            UiElementNode grandParent = parent.getUiParent();
            if (grandParent != null) {
                return getLastPosition(grandParent);
            }
        }

        return null;
    }

    /** Returns the pair [parent,index] of the previous node (when iterating backward) */
    @VisibleForTesting
    /* package */ static Pair<UiViewElementNode, Integer> findPrevious(UiViewElementNode node) {
        UiElementNode prev = node.getUiPreviousSibling();
        if (prev != null) {
            UiElementNode curr = prev;
            while (true) {
                List<UiElementNode> children = curr.getUiChildren();
                if (children.size() > 0) {
                    curr = children.get(children.size() - 1);
                    continue;
                }
                if (curr.getDescriptor().hasChildren()) {
                    return getFirstPosition(curr);
                } else {
                    if (curr == prev) {
                        return getPositionBefore(curr);
                    } else {
                        return getPositionAfter(curr);
                    }
                }
            }
        }

        return getPositionBefore(node.getUiParent());
    }

    /** Returns the pair [parent,index] of the position immediately before the given node  */
    private static Pair<UiViewElementNode, Integer> getPositionBefore(UiElementNode node) {
        if (node != null) {
            UiElementNode parent = node.getUiParent();
            if (parent != null && parent instanceof UiViewElementNode) {
                return Pair.of((UiViewElementNode) parent, node.getUiSiblingIndex());
            }
        }

        return null;
    }

    /** Returns the pair [parent,index] of the position immediately following the given node  */
    private static Pair<UiViewElementNode, Integer> getPositionAfter(UiElementNode node) {
        if (node != null) {
            UiElementNode parent = node.getUiParent();
            if (parent != null && parent instanceof UiViewElementNode) {
                return Pair.of((UiViewElementNode) parent, node.getUiSiblingIndex() + 1);
            }
        }

        return null;
    }

    /** Returns the pair [parent,index] of the first position inside the given parent */
    private static Pair<UiViewElementNode, Integer> getFirstPosition(UiElementNode parent) {
        if (parent != null && parent instanceof UiViewElementNode) {
            return Pair.of((UiViewElementNode) parent, 0);
        }

        return null;
    }

    /**
     * Returns the pair [parent,index] of the last position after the given node's
     * children
     */
    private static Pair<UiViewElementNode, Integer> getLastPosition(UiElementNode parent) {
        if (parent != null && parent instanceof UiViewElementNode) {
            return Pair.of((UiViewElementNode) parent, parent.getUiChildren().size());
        }

        return null;
    }
}
