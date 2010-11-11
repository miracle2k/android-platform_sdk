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

package com.android.ide.eclipse.adt.internal.editors.layout.gle2;

import com.android.ide.common.api.INode;
import com.android.ide.common.api.Point;
import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.internal.editors.descriptors.DescriptorsUtils;
import com.android.ide.eclipse.adt.internal.editors.descriptors.XmlnsAttributeDescriptor;
import com.android.ide.eclipse.adt.internal.editors.layout.LayoutEditor;
import com.android.ide.eclipse.adt.internal.editors.layout.descriptors.ViewElementDescriptor;
import com.android.ide.eclipse.adt.internal.editors.layout.gre.NodeFactory;
import com.android.ide.eclipse.adt.internal.editors.layout.gre.RulesEngine;
import com.android.ide.eclipse.adt.internal.editors.layout.uimodel.UiViewElementNode;
import com.android.ide.eclipse.adt.internal.editors.uimodel.UiDocumentNode;
import com.android.ide.eclipse.adt.internal.editors.uimodel.UiElementNode;
import com.android.layoutlib.api.LayoutScene;
import com.android.sdklib.SdkConstants;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IContributionItem;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DragSource;
import org.eclipse.swt.dnd.DragSourceListener;
import org.eclipse.swt.dnd.DropTarget;
import org.eclipse.swt.dnd.DropTargetListener;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.actions.ContributionItemFactory;
import org.eclipse.ui.actions.TextActionHandler;
import org.eclipse.ui.actions.ActionFactory.IWorkbenchAction;
import org.eclipse.ui.internal.ide.IDEWorkbenchMessages;
import org.eclipse.ui.views.contentoutline.IContentOutlinePage;
import org.w3c.dom.Node;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Displays the image rendered by the {@link GraphicalEditorPart} and handles
 * the interaction with the widgets.
 * <p/>
 * {@link LayoutCanvas} implements the "Canvas" control. The editor part
 * actually uses the {@link LayoutCanvasViewer}, which is a JFace viewer wrapper
 * around this control.
 * <p/>
 * The LayoutCanvas contains the painting logic for the canvas. Selection,
 * clipboard, view management etc. is handled in separate helper classes.
 *
 * @since GLE2
 */
@SuppressWarnings("restriction") // For WorkBench "Show In" support
class LayoutCanvas extends Canvas {

    private static final boolean DEBUG = false;

    /* package */ static final String PREFIX_CANVAS_ACTION = "canvas_action_";

    /** The layout editor that uses this layout canvas. */
    private final LayoutEditor mLayoutEditor;

    /** The Rules Engine, associated with the current project. */
    private RulesEngine mRulesEngine;

    /** GC wrapper given to the IViewRule methods. The GC itself is only defined in the
     *  context of {@link #onPaint(PaintEvent)}; otherwise it is null. */
    private GCWrapper mGCWrapper;

    /** Default font used on the canvas. Do not dispose, it's a system font. */
    private Font mFont;

    /** Current hover view info. Null when no mouse hover. */
    private CanvasViewInfo mHoverViewInfo;

    /** When true, always display the outline of all views. */
    private boolean mShowOutline;

    /** When true, display the outline of all empty parent views. */
    private boolean mShowInvisible;

    /** Drop target associated with this composite. */
    private DropTarget mDropTarget;

    /** Factory that can create {@link INode} proxies. */
    private final NodeFactory mNodeFactory = new NodeFactory();

    /** Vertical scaling & scrollbar information. */
    private ScaleInfo mVScale;

    /** Horizontal scaling & scrollbar information. */
    private ScaleInfo mHScale;

    /** Drag source associated with this canvas. */
    private DragSource mDragSource;

    /**
     * The current Outline Page, to set its model.
     * It isn't possible to call OutlinePage2.dispose() in this.dispose().
     * this.dispose() is called from GraphicalEditorPart.dispose(),
     * when page's widget is already disposed.
     * Added the DisposeListener to OutlinePage2 in order to correctly dispose this page.
     **/
    private OutlinePage2 mOutlinePage;

    /** Delete action for the Edit or context menu. */
    private Action mDeleteAction;

    /** Select-All action for the Edit or context menu. */
    private Action mSelectAllAction;

    /** Paste action for the Edit or context menu. */
    private Action mPasteAction;

    /** Cut action for the Edit or context menu. */
    private Action mCutAction;

    /** Copy action for the Edit or context menu. */
    private Action mCopyAction;

    /** Root of the context menu. */
    private MenuManager mMenuManager;

    /** The view hierarchy associated with this canvas. */
    private final ViewHierarchy mViewHierarchy = new ViewHierarchy(this);

    /** The selection in the canvas. */
    private final SelectionManager mSelectionManager = new SelectionManager(this);

    /** The overlay which paints the optional outline. */
    private OutlineOverlay mOutlineOverlay;

    /** The overlay which paints outlines around empty children */
    private EmptyViewsOverlay mEmptyOverlay;

    /** The overlay which paints the mouse hover. */
    private HoverOverlay mHoverOverlay;

    /** The overlay which paints the selection. */
    private SelectionOverlay mSelectionOverlay;

    /** The overlay which paints the rendered layout image. */
    private ImageOverlay mImageOverlay;

    /**
     * Gesture Manager responsible for identifying mouse, keyboard and drag and
     * drop events.
     */
    private final GestureManager mGestureManager = new GestureManager(this);

    /**
     * Native clipboard support.
     */
    private ClipboardSupport mClipboardSupport;

    public LayoutCanvas(LayoutEditor layoutEditor,
            RulesEngine rulesEngine,
            Composite parent,
            int style) {
        super(parent, style | SWT.DOUBLE_BUFFERED | SWT.V_SCROLL | SWT.H_SCROLL);
        mLayoutEditor = layoutEditor;
        mRulesEngine = rulesEngine;

        mClipboardSupport = new ClipboardSupport(this, parent);
        mHScale = new ScaleInfo(this, getHorizontalBar());
        mVScale = new ScaleInfo(this, getVerticalBar());

        mGCWrapper = new GCWrapper(mHScale, mVScale);

        Display display = getDisplay();
        mFont = display.getSystemFont();

        // --- Set up graphic overlays
        // mOutlineOverlay and mEmptyOverlay are initialized lazily
        mHoverOverlay = new HoverOverlay(mHScale, mVScale);
        mHoverOverlay.create(display);
        mSelectionOverlay = new SelectionOverlay();
        mSelectionOverlay.create(display);
        mImageOverlay = new ImageOverlay(this, mHScale, mVScale);
        mImageOverlay.create(display);

        // --- Set up listeners
        addPaintListener(new PaintListener() {
            public void paintControl(PaintEvent e) {
                onPaint(e);
            }
        });

        addControlListener(new ControlAdapter() {
            @Override
            public void controlResized(ControlEvent e) {
                super.controlResized(e);
                mHScale.setClientSize(getClientArea().width);
                mVScale.setClientSize(getClientArea().height);
            }
        });

        addKeyListener(new KeyListener() {

            public void keyPressed(KeyEvent e) {
                // Set up backspace as an alias for the delete action within the canvas.
                // On most Macs there is no delete key - though there IS a key labeled
                // "Delete" and it sends a backspace key code! In short, for Macs we should
                // treat backspace as delete, and it's harmless (and probably useful) to
                // handle backspace for other platforms as well.
                if (e.keyCode == SWT.BS) {
                    mDeleteAction.run();
                }
            }

            public void keyReleased(KeyEvent e) {
            }
        });

        // --- setup drag'n'drop ---
        // DND Reference: http://www.eclipse.org/articles/Article-SWT-DND/DND-in-SWT.html

        mDropTarget = createDropTarget(this);
        mDragSource = createDragSource(this);
        mGestureManager.registerListeners(mDragSource, mDropTarget);

        if (mLayoutEditor == null) {
            // TODO: In another CL we should use EasyMock/objgen to provide an editor.
            return; // Unit test
        }

        // --- setup context menu ---
        setupGlobalActionHandlers();
        createContextMenu();

        // --- setup outline ---
        // Get the outline associated with this editor, if any and of the right type.
        Object outline = layoutEditor.getAdapter(IContentOutlinePage.class);
        if (outline instanceof OutlinePage2) {
            mOutlinePage = (OutlinePage2) outline;
        }
    }

    @Override
    public void dispose() {
        super.dispose();

        mGestureManager.unregisterListeners(mDragSource, mDropTarget);

        if (mDropTarget != null) {
            mDropTarget.dispose();
            mDropTarget = null;
        }

        if (mRulesEngine != null) {
            mRulesEngine.dispose();
            mRulesEngine = null;
        }

        if (mDragSource != null) {
            mDragSource.dispose();
            mDragSource = null;
        }

        if (mClipboardSupport != null) {
            mClipboardSupport.dispose();
            mClipboardSupport = null;
        }

        if (mGCWrapper != null) {
            mGCWrapper.dispose();
            mGCWrapper = null;
        }

        if (mOutlineOverlay != null) {
            mOutlineOverlay.dispose();
            mOutlineOverlay = null;
        }

        if (mEmptyOverlay != null) {
            mEmptyOverlay.dispose();
            mEmptyOverlay = null;
        }

        if (mHoverOverlay != null) {
            mHoverOverlay.dispose();
            mHoverOverlay = null;
        }

        if (mSelectionOverlay != null) {
            mSelectionOverlay.dispose();
            mSelectionOverlay = null;
        }

        if (mImageOverlay != null) {
            mImageOverlay.dispose();
            mImageOverlay = null;
        }
    }

    /** Returns the Rules Engine, associated with the current project. */
    /* package */ RulesEngine getRulesEngine() {
        return mRulesEngine;
    }

    /** Sets the Rules Engine, associated with the current project. */
    /* package */ void setRulesEngine(RulesEngine rulesEngine) {
        mRulesEngine = rulesEngine;
    }

    /**
     * Returns the factory to use to convert from {@link CanvasViewInfo} or from
     * {@link UiViewElementNode} to {@link INode} proxies.
     */
    /* package */ NodeFactory getNodeFactory() {
        return mNodeFactory;
    }

    /**
     * Returns our {@link DragSourceListener}.
     * This is used by {@link OutlinePage2} to delegate drag source events.
     */
    /* package */ DragSourceListener getDragListener() {
        return mGestureManager.getDragSourceListener();
    }

    /**
     * Returns our {@link DropTargetListener}.
     * This is used by {@link OutlinePage2} to delegate drop target events.
     */
    /* package */ DropTargetListener getDropListener() {
        return mGestureManager.getDropTargetListener();
    }

    /**
     * Returns the GCWrapper used to paint view rules.
     *
     * @return The GCWrapper used to paint view rules
     */
    /* package */ GCWrapper getGcWrapper() {
        return mGCWrapper;
    }

    /**
     * Returns the {@link LayoutEditor} associated with this canvas.
     */
    /* package */ LayoutEditor getLayoutEditor() {
        return mLayoutEditor;
    }

    /**
     * Returns the horizontal {@link ScaleInfo} transform object, which can map
     * a layout point into a control point.
     *
     * @return A {@link ScaleInfo} for mapping between layout and control
     *         coordinates in the horizontal dimension.
     */
    /* package */ ScaleInfo getHorizontalTransform() {
        return mHScale;
    }

    /**
     * Returns the vertical {@link ScaleInfo} transform object, which can map a
     * layout point into a control point.
     *
     * @return A {@link ScaleInfo} for mapping between layout and control
     *         coordinates in the vertical dimension.
     */
    /* package */ ScaleInfo getVerticalTransform() {
        return mVScale;
    }

    /**
     * Returns the {@link SelectionManager} associated with this canvas.
     *
     * @return The {@link SelectionManager} holding the selection for this
     *         canvas. Never null.
     */
    public SelectionManager getSelectionManager() {
        return mSelectionManager;
    }

    /**
     * Returns the {@link ViewHierarchy} object associated with this canvas,
     * holding the most recent rendered view of the scene, if valid.
     *
     * @return The {@link ViewHierarchy} object associated with this canvas.
     *         Never null.
     */
    public ViewHierarchy getViewHierarchy() {
        return mViewHierarchy;
    }

    /**
     * Returns the {@link ClipboardSupport} object associated with this canvas.
     *
     * @return The {@link ClipboardSupport} object for this canvas. Null only after dispose.
     */
    public ClipboardSupport getClipboardSupport() {
        return mClipboardSupport;
    }

    /**
     * Sets the result of the layout rendering. The result object indicates if the layout
     * rendering succeeded. If it did, it contains a bitmap and the objects rectangles.
     *
     * Implementation detail: the bridge's computeLayout() method already returns a newly
     * allocated ILayourResult. That means we can keep this result and hold on to it
     * when it is valid.
     *
     * @param scene The new scene, either valid or not.
     * @param explodedNodes The set of individual nodes the layout computer was asked to
     *            explode. Note that these are independent of the explode-all mode where
     *            all views are exploded; this is used only for the mode (
     *            {@link #showInvisibleViews(boolean)}) where individual invisible nodes
     *            are padded during certain interactions.
     */
    /* package */ void setResult(LayoutScene scene, Set<UiElementNode> explodedNodes) {
        // disable any hover
        clearHover();

        mViewHierarchy.setResult(scene, explodedNodes);
        if (mViewHierarchy.isValid() && scene != null) {
            Image image = mImageOverlay.setImage(scene.getImage());

            mOutlinePage.setModel(mViewHierarchy.getRoot());

            if (image != null) {
                mHScale.setSize(image.getImageData().width, getClientArea().width);
                mVScale.setSize(image.getImageData().height, getClientArea().height);
            }

            // Pre-load the android.view.View rule in the Rules Engine. Doing it here means
            // it will be done after the first rendering is finished. Successive calls are
            // superfluous but harmless since the rule will be cached.
            mRulesEngine.preloadAndroidView();
        }

        redraw();
    }

    /* package */ void setShowOutline(boolean newState) {
        mShowOutline = newState;
        redraw();
    }

    /* package */ double getScale() {
        return mHScale.getScale();
    }

    /* package */ void setScale(double scale, boolean redraw) {
        mHScale.setScale(scale);
        mVScale.setScale(scale);
        if (redraw) {
            redraw();
        }
    }

    /**
     * Transforms a point, expressed in layout coordinates, into "client" coordinates
     * relative to the control (and not relative to the display).
     *
     * @param canvasX X in the canvas coordinates
     * @param canvasY Y in the canvas coordinates
     * @return A new {@link Point} in control client coordinates (not display coordinates)
     */
    /* package */ Point layoutToControlPoint(int canvasX, int canvasY) {
        int x = mHScale.translate(canvasX);
        int y = mVScale.translate(canvasY);
        return new Point(x, y);
    }

    /**
     * Returns the action for the context menu corresponding to the given action id.
     * <p/>
     * For global actions such as copy or paste, the action id must be composed of
     * the {@link #PREFIX_CANVAS_ACTION} followed by one of {@link ActionFactory}'s
     * action ids.
     * <p/>
     * Returns null if there's no action for the given id.
     */
    /* package */ IAction getAction(String actionId) {
        String prefix = PREFIX_CANVAS_ACTION;
        if (mMenuManager == null ||
                actionId == null ||
                !actionId.startsWith(prefix)) {
            return null;
        }

        actionId = actionId.substring(prefix.length());

        for (IContributionItem contrib : mMenuManager.getItems()) {
            if (contrib instanceof ActionContributionItem &&
                    actionId.equals(contrib.getId())) {
                return ((ActionContributionItem) contrib).getAction();
            }
        }

        return null;
    }

    //---------------

    /**
     * Paints the canvas in response to paint events.
     */
    private void onPaint(PaintEvent e) {
        GC gc = e.gc;
        gc.setFont(mFont);
        mGCWrapper.setGC(gc);
        try {
            mImageOverlay.paint(gc);

            if (mShowOutline) {
                if (mOutlineOverlay == null) {
                    mOutlineOverlay = new OutlineOverlay(mViewHierarchy, mHScale, mVScale);
                    mOutlineOverlay.create(getDisplay());
                }
                mOutlineOverlay.paint(gc);
            }

            if (mShowInvisible) {
                if (mEmptyOverlay == null) {
                    mEmptyOverlay = new EmptyViewsOverlay(mViewHierarchy, mHScale, mVScale);
                    mEmptyOverlay.create(getDisplay());
                }
                mEmptyOverlay.paint(gc);
            }

            mHoverOverlay.paint(gc);
            mSelectionOverlay.paint(mSelectionManager, gc, mGCWrapper, mRulesEngine);
            mGestureManager.paint(gc);

        } finally {
            mGCWrapper.setGC(null);
        }
    }

    /**
     * Shows or hides invisible parent views, which are views which have empty bounds and
     * no children. The nodes which will be shown are provided by
     * {@link #getNodesToExplode()}.
     *
     * @param show When true, any invisible parent nodes are padded and highlighted
     *            ("exploded"), and when false any formerly exploded nodes are hidden.
     */
    /* package */ void showInvisibleViews(boolean show) {
        if (mShowInvisible == show) {
            return;
        }

        // Optimization: Avoid doing work when we don't have invisible parents (on show)
        // or formerly exploded nodes (on hide).
        if (show && !mViewHierarchy.hasInvisibleParents()) {
            return;
        } else if (!show && !mViewHierarchy.hasExplodedParents()) {
            return;
        }

        mShowInvisible = show;
        mLayoutEditor.recomputeLayout();
    }

    /**
     * Returns a set of nodes that should be exploded (forced non-zero padding during render),
     * or null if no nodes should be exploded. (Note that this is independent of the
     * explode-all mode, where all nodes are padded -- that facility does not use this
     * mechanism, which is only intended to be used to expose invisible parent nodes.
     *
     * @return The set of invisible parents, or null if no views should be expanded.
     */
    public Set<UiElementNode> getNodesToExplode() {
        if (mShowInvisible) {
            return mViewHierarchy.getInvisibleNodes();
        }

        // IF we have selection, and IF we have invisible nodes in the view,
        // see if any of the selected items are among the invisible nodes, and if so
        // add them to a lazily constructed set which we pass back for rendering.
        Set<UiElementNode> result = null;
        List<CanvasSelection> selections = mSelectionManager.getSelections();
        if (selections.size() > 0) {
            List<CanvasViewInfo> invisibleParents = mViewHierarchy.getInvisibleViews();
            if (invisibleParents.size() > 0) {
                for (CanvasSelection item : selections) {
                    CanvasViewInfo viewInfo = item.getViewInfo();
                    // O(n^2) here, but both the selection size and especially the
                    // invisibleParents size are expected to be small
                    if (invisibleParents.contains(viewInfo)) {
                        UiViewElementNode node = viewInfo.getUiViewKey();
                        if (node != null) {
                            if (result == null) {
                                result = new HashSet<UiElementNode>();
                            }
                            result.add(node);
                        }
                    }
                }
            }
        }

        return result;
    }

    /**
     * Clears the hover.
     */
    /* package */ void clearHover() {
        mHoverOverlay.clearHover();
    }

    /**
     * Hover on top of a known child.
     */
    /* package */ void hover(MouseEvent e) {
        // Check if a button is pressed; no hovers during drags
        if ((e.stateMask & SWT.BUTTON_MASK) != 0) {
            clearHover();
            return;
        }

        LayoutPoint p = ControlPoint.create(this, e).toLayout();
        CanvasViewInfo vi = mViewHierarchy.findViewInfoAt(p);

        // We don't hover on the root since it's not a widget per see and it is always there.
        if (vi != null && vi.isRoot()) {
            vi = null;
        }

        boolean needsUpdate = vi != mHoverViewInfo;
        mHoverViewInfo = vi;

        if (vi == null) {
            clearHover();
        } else {
            Rectangle r = vi.getSelectionRect();
            mHoverOverlay.setHover(r.x, r.y, r.width, r.height);
        }

        if (needsUpdate) {
            redraw();
        }
    }

    /**
     * Show the XML element corresponding to the point under the mouse event
     * (unless it's a root).
     *
     * @param e A mouse event pointing on the screen whose underlying XML
     *            element we want to view
     */
    public void showXml(MouseEvent e) {
        // Warp to the text editor and show the corresponding XML for the
        // double-clicked widget
        LayoutPoint p = ControlPoint.create(this, e).toLayout();
        CanvasViewInfo vi = mViewHierarchy.findViewInfoAt(p);
        if (vi == null || vi.isRoot()) {
            return;
        }

        Node xmlNode = vi.getXmlNode();
        if (xmlNode != null) {
            boolean found = mLayoutEditor.show(xmlNode);
            if (!found) {
                getDisplay().beep();
            }
        }
    }

    //---------------

    /**
     * Helper to create the drag source for the given control.
     * <p/>
     * This is static with package-access so that {@link OutlinePage2} can also
     * create an exact copy of the source with the same attributes.
     */
    /* package */static DragSource createDragSource(Control control) {
        DragSource source = new DragSource(control, DND.DROP_COPY | DND.DROP_MOVE);
        source.setTransfer(new Transfer[] {
                TextTransfer.getInstance(),
                SimpleXmlTransfer.getInstance()
        });
        return source;
    }

    /**
     * Helper to create the drop target for the given control.
     * <p/>
     * This is static with package-access so that {@link OutlinePage2} can also
     * create an exact copy of the drop target with the same attributes.
     */
    /* package */static DropTarget createDropTarget(Control control) {
        DropTarget dropTarget = new DropTarget(
                control, DND.DROP_COPY | DND.DROP_MOVE | DND.DROP_DEFAULT);
        dropTarget.setTransfer(new Transfer[] {
            SimpleXmlTransfer.getInstance()
        });
        return dropTarget;
    }

    //---------------

    /**
     * Invoked by the constructor to add our cut/copy/paste/delete/select-all
     * handlers in the global action handlers of this editor's site.
     * <p/>
     * This will enable the menu items under the global Edit menu and make them
     * invoke our actions as needed. As a benefit, the corresponding shortcut
     * accelerators will do what one would expect.
     */
    private void setupGlobalActionHandlers() {
        // Get the global action bar for this editor (i.e. the menu bar)
        IActionBars actionBars = mLayoutEditor.getEditorSite().getActionBars();

        TextActionHandler tah = new TextActionHandler(actionBars);

        mCutAction = new Action() {
            @Override
            public void run() {
                mClipboardSupport.cutSelectionToClipboard(mSelectionManager.getSnapshot());
            }
        };

        tah.setCutAction(mCutAction);
        copyActionAttributes(mCutAction, ActionFactory.CUT);

        mCopyAction = new Action() {
            @Override
            public void run() {
                mClipboardSupport.copySelectionToClipboard(mSelectionManager.getSnapshot());
            }
        };

        tah.setCopyAction(mCopyAction);
        copyActionAttributes(mCopyAction, ActionFactory.COPY);

        mPasteAction = new Action() {
            @Override
            public void run() {
                mClipboardSupport.pasteSelection(mSelectionManager.getSnapshot());
            }
        };

        tah.setPasteAction(mPasteAction);
        copyActionAttributes(mPasteAction, ActionFactory.PASTE);

        mDeleteAction = new Action() {
            @Override
            public void run() {
                mClipboardSupport.deleteSelection(
                        getDeleteLabel(),
                        mSelectionManager.getSnapshot());
            }
        };

        tah.setDeleteAction(mDeleteAction);
        copyActionAttributes(mDeleteAction, ActionFactory.DELETE);

        mSelectAllAction = new Action() {
            @Override
            public void run() {
                mSelectionManager.selectAll();
            }
        };

        tah.setSelectAllAction(mSelectAllAction);
        copyActionAttributes(mSelectAllAction, ActionFactory.SELECT_ALL);
    }

    /* package */ String getCutLabel() {
        return mCutAction.getText();
    }

    /* package */ String getDeleteLabel() {
        // verb "Delete" from the DELETE action's title
        return mDeleteAction.getText();
    }

    /**
     * Updates menu actions that depends on the selection.
     *
     * @param hasSelection True iff we have a non-empty selection
     */
    /* package */ void updateMenuActions(boolean hasSelection) {
        mCutAction.setEnabled(hasSelection);
        mCopyAction.setEnabled(hasSelection);
        mDeleteAction.setEnabled(hasSelection);
        mSelectAllAction.setEnabled(hasSelection);

        // The paste operation is only available if we can paste our custom type.
        // We do not currently support pasting random text (e.g. XML). Maybe later.
        boolean hasSxt = mClipboardSupport.hasSxtOnClipboard();
        mPasteAction.setEnabled(hasSxt);
    }

    /**
     * Helper for {@link #setupGlobalActionHandlers()}.
     * Copies the action attributes form the given {@link ActionFactory}'s action to
     * our action.
     * <p/>
     * {@link ActionFactory} provides access to the standard global actions in Eclipse.
     * <p/>
     * This allows us to grab the standard labels and icons for the
     * global actions such as copy, cut, paste, delete and select-all.
     */
    private void copyActionAttributes(Action action, ActionFactory factory) {
        IWorkbenchAction wa = factory.create(mLayoutEditor.getEditorSite().getWorkbenchWindow());
        action.setId(wa.getId());
        action.setText(wa.getText());
        action.setEnabled(wa.isEnabled());
        action.setDescription(wa.getDescription());
        action.setToolTipText(wa.getToolTipText());
        action.setAccelerator(wa.getAccelerator());
        action.setActionDefinitionId(wa.getActionDefinitionId());
        action.setImageDescriptor(wa.getImageDescriptor());
        action.setHoverImageDescriptor(wa.getHoverImageDescriptor());
        action.setDisabledImageDescriptor(wa.getDisabledImageDescriptor());
    }

    /**
     * Creates the context menu for the canvas. This is called once from the canvas' constructor.
     * <p/>
     * The menu has a static part with actions that are always available such as
     * copy, cut, paste and show in > explorer. This is created by
     * {@link #setupStaticMenuActions(IMenuManager)}.
     * <p/>
     * There's also a dynamic part that is populated by the rules of the
     * selected elements, created by {@link DynamicContextMenu}.
     */
    private void createContextMenu() {

        // This manager is the root of the context menu.
        mMenuManager = new MenuManager() {
            @Override
            public boolean isDynamic() {
                return true;
            }
        };

        // Fill the menu manager with the static & dynamic actions
        setupStaticMenuActions(mMenuManager);
        new DynamicContextMenu(mLayoutEditor, this, mMenuManager);
        Menu menu = mMenuManager.createContextMenu(this);
        setMenu(menu);
    }

    /**
     * Invoked by {@link #createContextMenu()} to create our *static* context menu once.
     * <p/>
     * The content of the menu itself does not change. However the state of the
     * various items is controlled by their associated actions.
     * <p/>
     * For cut/copy/paste/delete/select-all, we explicitly reuse the actions
     * created by {@link #setupGlobalActionHandlers()}, so this method must be
     * invoked after that one.
     */
    private void setupStaticMenuActions(IMenuManager manager) {
        manager.removeAll();

        manager.add(mCutAction);
        manager.add(mCopyAction);
        manager.add(mPasteAction);

        manager.add(new Separator());

        manager.add(mDeleteAction);
        manager.add(mSelectAllAction);

        manager.add(new Separator());

        // Create a "Show In" sub-menu and automatically populate it using standard
        // actions contributed by the workbench.
        String showInLabel = IDEWorkbenchMessages.Workbench_showIn;
        MenuManager showInSubMenu = new MenuManager(showInLabel);
        showInSubMenu.add(
                ContributionItemFactory.VIEWS_SHOW_IN.create(
                        mLayoutEditor.getSite().getWorkbenchWindow()));
        manager.add(showInSubMenu);
    }

    /**
     * Deletes the selection. Equivalent to pressing the Delete key.
     */
    /* package */ void delete() {
        mDeleteAction.run();
    }

    /**
     * Add new root in an existing empty XML layout.
     * <p/>
     * In case of error (unknown FQCN, document not empty), silently do nothing.
     * In case of success, the new element will have some default attributes set
     * (xmlns:android, layout_width and height). The edit is wrapped in a proper
     * undo.
     * <p/>
     * This is invoked by
     * {@link MoveGesture#drop(org.eclipse.swt.dnd.DropTargetEvent)}.
     *
     * @param rootFqcn A non-null non-empty FQCN that must match an existing
     *            {@link ViewElementDescriptor} to add as root to the current
     *            empty XML document.
     */
    /* package */ void createDocumentRoot(String rootFqcn) {

        // Need a valid empty document to create the new root
        final UiDocumentNode uiDoc = mLayoutEditor.getUiRootNode();
        if (uiDoc == null || uiDoc.getUiChildren().size() > 0) {
            debugPrintf("Failed to create document root for %1$s: document is not empty", rootFqcn);
            return;
        }

        // Find the view descriptor matching our FQCN
        final ViewElementDescriptor viewDesc = mLayoutEditor.getFqcnViewDescriptor(rootFqcn);
        if (viewDesc == null) {
            // TODO this could happen if dropping a custom view not known in this project
            debugPrintf("Failed to add document root, unknown FQCN %1$s", rootFqcn);
            return;
        }

        // Get the last segment of the FQCN for the undo title
        String title = rootFqcn;
        int pos = title.lastIndexOf('.');
        if (pos > 0 && pos < title.length() - 1) {
            title = title.substring(pos + 1);
        }
        title = String.format("Create root %1$s in document", title);

        mLayoutEditor.wrapUndoEditXmlModel(title, new Runnable() {
            public void run() {
                UiElementNode uiNew = uiDoc.appendNewUiChild(viewDesc);

                // A root node requires the Android XMLNS
                uiNew.setAttributeValue(
                        "android",
                        XmlnsAttributeDescriptor.XMLNS_URI,
                        SdkConstants.NS_RESOURCES,
                        true /*override*/);

                // Adjust the attributes
                DescriptorsUtils.setDefaultLayoutAttributes(uiNew, false /*updateLayout*/);

                uiNew.createXmlNode();
            }
        });
    }

    private void debugPrintf(String message, Object... params) {
        if (DEBUG) {
            AdtPlugin.printToConsole("Canvas", String.format(message, params));
        }
    }
}
