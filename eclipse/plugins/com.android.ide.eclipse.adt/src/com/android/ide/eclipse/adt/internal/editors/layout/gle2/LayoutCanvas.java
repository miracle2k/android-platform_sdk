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

import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.editors.layout.gscripts.IDragElement;
import com.android.ide.eclipse.adt.editors.layout.gscripts.INode;
import com.android.ide.eclipse.adt.editors.layout.gscripts.Point;
import com.android.ide.eclipse.adt.editors.layout.gscripts.Rect;
import com.android.ide.eclipse.adt.editors.layout.gscripts.IDragElement.IDragAttribute;
import com.android.ide.eclipse.adt.internal.editors.AndroidXmlEditor;
import com.android.ide.eclipse.adt.internal.editors.descriptors.AttributeDescriptor;
import com.android.ide.eclipse.adt.internal.editors.descriptors.DescriptorsUtils;
import com.android.ide.eclipse.adt.internal.editors.descriptors.XmlnsAttributeDescriptor;
import com.android.ide.eclipse.adt.internal.editors.layout.LayoutEditor;
import com.android.ide.eclipse.adt.internal.editors.layout.descriptors.ViewElementDescriptor;
import com.android.ide.eclipse.adt.internal.editors.layout.gre.NodeFactory;
import com.android.ide.eclipse.adt.internal.editors.layout.gre.NodeProxy;
import com.android.ide.eclipse.adt.internal.editors.layout.gre.RulesEngine;
import com.android.ide.eclipse.adt.internal.editors.layout.uimodel.UiViewElementNode;
import com.android.ide.eclipse.adt.internal.editors.uimodel.UiAttributeNode;
import com.android.ide.eclipse.adt.internal.editors.uimodel.UiDocumentNode;
import com.android.ide.eclipse.adt.internal.editors.uimodel.UiElementNode;
import com.android.layoutlib.api.ILayoutResult;
import com.android.layoutlib.api.ILayoutResult.ILayoutViewInfo;
import com.android.sdklib.SdkConstants;

import org.eclipse.core.runtime.ListenerList;
import org.eclipse.gef.ui.parts.TreeViewer;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IContributionItem;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.util.SafeRunnable;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.ITreeSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TreePath;
import org.eclipse.jface.viewers.TreeSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DragSource;
import org.eclipse.swt.dnd.DragSourceEvent;
import org.eclipse.swt.dnd.DragSourceListener;
import org.eclipse.swt.dnd.DropTarget;
import org.eclipse.swt.dnd.DropTargetListener;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.dnd.TransferData;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.MouseMoveListener;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.ScrollBar;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.actions.ContributionItemFactory;
import org.eclipse.ui.actions.TextActionHandler;
import org.eclipse.ui.actions.ActionFactory.IWorkbenchAction;
import org.eclipse.ui.internal.ide.IDEWorkbenchMessages;
import org.eclipse.ui.internal.registry.ViewDescriptor;
import org.eclipse.ui.views.contentoutline.IContentOutlinePage;
import org.eclipse.wst.sse.core.internal.provisional.IStructuredModel;
import org.eclipse.wst.sse.core.internal.provisional.IndexedRegion;
import org.eclipse.wst.sse.core.internal.provisional.text.IStructuredDocument;
import org.eclipse.wst.xml.core.internal.document.NodeContainer;
import org.w3c.dom.Node;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.Raster;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

/**
 * Displays the image rendered by the {@link GraphicalEditorPart} and handles
 * the interaction with the widgets.
 * <p/>
 * {@link LayoutCanvas} implements the "Canvas" control. The editor part
 * actually uses the {@link LayoutCanvasViewer}, which is a JFace viewer wrapper
 * around this control.
 * <p/>
 * This class implements {@link ISelectionProvider} so that it can delegate
 * the selection provider from the {@link LayoutCanvasViewer}.
 * <p/>
 * Note that {@link LayoutCanvasViewer} sets a selection change listener on this
 * control so that it can invoke its own fireSelectionChanged when the control's
 * selection changes.
 *
 * @since GLE2
 */
/*
 * TODO list:
 * - gray on error, keep select but disable d'n'd.
 * - context menu: enum clear, flag values, toggles as tri-states
 * - context menu: impl custom layout width/height
 * - properly handle custom views
 */
class LayoutCanvas extends Canvas implements ISelectionProvider {

    private final static boolean DEBUG = false;

    /* package */ static final String PREFIX_CANVAS_ACTION = "canvas_action_";

    /** The layout editor that uses this layout canvas. */
    private final LayoutEditor mLayoutEditor;

    /** The Groovy Rules Engine, associated with the current project. */
    private RulesEngine mRulesEngine;

    /** SWT clipboard instance. */
    private Clipboard mClipboard;

    /**
     * The CanvasViewInfo root created by the last call to {@link #setResult(ILayoutResult)}
     * with a valid layout.
     * <p/>
     * This <em>can</em> be null to indicate we're dealing with an empty document with
     * no root node. Null here does not mean the result was invalid, merely that the XML
     * had no content to display -- we need to treat an empty document as valid so that
     * we can drop new items in it.
     */
    private CanvasViewInfo mLastValidViewInfoRoot;

    /**
     * True when the last {@link #setResult(ILayoutResult)} provided a valid {@link ILayoutResult}.
     * <p/>
     * When false this means the canvas is displaying an out-dated result image & bounds and some
     * features should be disabled accordingly such a drag'n'drop.
     * <p/>
     * Note that an empty document (with a null {@link #mLastValidViewInfoRoot}) is considered
     * valid since it is an acceptable drop target.
     */
    private boolean mIsResultValid;

    /** Current background image. Null when there's no image. */
    private Image mImage;

    /** The current selection list. The list is never null, however it can be empty. */
    private final LinkedList<CanvasSelection> mSelections = new LinkedList<CanvasSelection>();

    /** An unmodifiable view of {@link #mSelections}. */
    private List<CanvasSelection> mUnmodifiableSelection;

    /** CanvasSelection border color. Do not dispose, it's a system color. */
    private Color mSelectionFgColor;

    /** GC wrapper given to the IViewRule methods. The GC itself is only defined in the
     *  context of {@link #onPaint(PaintEvent)}; otherwise it is null. */
    private final GCWrapper mGCWrapper;

    /** Default font used on the canvas. Do not dispose, it's a system font. */
    private Font mFont;

    /** Current hover view info. Null when no mouse hover. */
    private CanvasViewInfo mHoverViewInfo;

    /** Current mouse hover border rectangle. Null when there's no mouse hover.
     * The rectangle coordinates do not take account of the translation, which must
     * be applied to the rectangle when drawing.
     */
    private Rectangle mHoverRect;

    /** Hover border color. Must be disposed, it's NOT a system color. */
    private Color mHoverFgColor;

    /** Outline color. Do not dispose, it's a system color. */
    private Color mOutlineColor;

    /**
     * The <em>current</em> alternate selection, if any, which changes when the Alt key is
     * used during a selection. Can be null.
     */
    private CanvasAlternateSelection mAltSelection;

    /** When true, always display the outline of all views. */
    private boolean mShowOutline;

    /** Drop target associated with this composite. */
    private DropTarget mDropTarget;

    /** Drop listener, with feedback from current drop */
    private CanvasDropListener mDropListener;

    /** Factory that can create {@link INode} proxies. */
    private final NodeFactory mNodeFactory = new NodeFactory();

    /** Vertical scaling & scrollbar information. */
    private ScaleInfo mVScale;

    /** Horizontal scaling & scrollbar information. */
    private ScaleInfo mHScale;

    /** Drag source associated with this canvas. */
    private DragSource mDragSource;

    /** List of clients listening to selection changes. */
    private final ListenerList mSelectionListeners = new ListenerList();

    /** The current Outline Page, to set its model. */
    private OutlinePage2 mOutlinePage;

    /** Barrier set when updating the selection to prevent from recursively
     * invoking ourselves. */
    private boolean mInsideUpdateSelection;

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

    private CanvasDragSourceListener mDragSourceListener;


    public LayoutCanvas(LayoutEditor layoutEditor,
            RulesEngine rulesEngine,
            Composite parent,
            int style) {
        super(parent, style | SWT.DOUBLE_BUFFERED | SWT.V_SCROLL | SWT.H_SCROLL);
        mLayoutEditor = layoutEditor;
        mRulesEngine = rulesEngine;

        mClipboard = new Clipboard(parent.getDisplay());

        mHScale = new ScaleInfo(getHorizontalBar());
        mVScale = new ScaleInfo(getVerticalBar());

        mGCWrapper = new GCWrapper(mHScale, mVScale);

        Display d = getDisplay();
        mSelectionFgColor = d.getSystemColor(SWT.COLOR_RED);
        mHoverFgColor     = new Color(d, 0xFF, 0x99, 0x00); // orange
        mOutlineColor     = d.getSystemColor(SWT.COLOR_GREEN);

        mFont = d.getSystemFont();

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

        addMouseMoveListener(new MouseMoveListener() {
            public void mouseMove(MouseEvent e) {
                onMouseMove(e);
            }
        });

        addMouseListener(new MouseListener() {
            public void mouseUp(MouseEvent e) {
                onMouseUp(e);
            }

            public void mouseDown(MouseEvent e) {
                onMouseDown(e);
            }

            public void mouseDoubleClick(MouseEvent e) {
                onDoubleClick(e);
            }
        });

        // --- setup drag'n'drop ---
        // DND Reference: http://www.eclipse.org/articles/Article-SWT-DND/DND-in-SWT.html

        mDropListener = new CanvasDropListener(this);
        mDropTarget = createDropTarget(this, mDropListener);

        mDragSourceListener = new CanvasDragSourceListener();
        mDragSource = createDragSource(this, mDragSourceListener);

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

        if (mOutlinePage != null) {
            mOutlinePage.setModel(null);
            mOutlinePage = null;
        }

        if (mHoverFgColor != null) {
            mHoverFgColor.dispose();
            mHoverFgColor = null;
        }

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

        if (mClipboard != null) {
            mClipboard.dispose();
            mClipboard = null;
        }
    }

    /**
     * Returns true when the last {@link #setResult(ILayoutResult)} provided a valid
     * {@link ILayoutResult}.
     * <p/>
     * When false this means the canvas is displaying an out-dated result image & bounds and some
     * features should be disabled accordingly such a drag'n'drop.
     * <p/>
     * Note that an empty document (with a null {@link #mLastValidViewInfoRoot}) is considered
     * valid since it is an acceptable drop target.
    */
    /* package */ boolean isResultValid() {
        return mIsResultValid;
    }

    /**
     * Returns true if the last valid content of the canvas represents an empty document.
     */
    /* package */ boolean isEmptyDocument() {
        return mLastValidViewInfoRoot == null;
    }

    /** Returns the Groovy Rules Engine, associated with the current project. */
    /* package */ RulesEngine getRulesEngine() {
        return mRulesEngine;
    }

    /** Sets the Groovy Rules Engine, associated with the current project. */
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
        return mDragSourceListener;
    }

    /**
     * Returns our {@link DropTargetListener}.
     * This is used by {@link OutlinePage2} to delegate drop target events.
     */
    /* package */ DropTargetListener getDropListener() {
        return mDropListener;
    }

    /**
     * Returns the native {@link CanvasSelection} list.
     *
     * @return An immutable list of {@link CanvasSelection}
     * @see #getSelection() {@link #getSelection()} to retrieve a {@link TreeViewer}
     *                      compatible {@link ISelection}.
     */
    /* package */ List<CanvasSelection> getCanvasSelection() {
        if (mUnmodifiableSelection == null) {
            mUnmodifiableSelection = Collections.unmodifiableList(mSelections);
        }
        return mUnmodifiableSelection;
    }

    /**
     * Sets the result of the layout rendering. The result object indicates if the layout
     * rendering succeeded. If it did, it contains a bitmap and the objects rectangles.
     *
     * Implementation detail: the bridge's computeLayout() method already returns a newly
     * allocated ILayourResult. That means we can keep this result and hold on to it
     * when it is valid.
     *
     * @param result The new rendering result, either valid or not.
     */
    /* package */ void setResult(ILayoutResult result) {
        // disable any hover
        mHoverRect = null;

        mIsResultValid = (result != null && result.getSuccess() == ILayoutResult.SUCCESS);

        if (mIsResultValid && result != null) {
            ILayoutViewInfo root = result.getRootView();
            if (root == null) {
                mLastValidViewInfoRoot = null;
            } else {
                mLastValidViewInfoRoot = new CanvasViewInfo(result.getRootView());
            }
            setImage(result.getImage());

            updateNodeProxies(mLastValidViewInfoRoot);
            mOutlinePage.setModel(mLastValidViewInfoRoot);

            // Check if the selection is still the same (based on the object keys)
            // and eventually recompute their bounds.
            for (ListIterator<CanvasSelection> it = mSelections.listIterator(); it.hasNext(); ) {
                CanvasSelection s = it.next();

                // Check if the selected object still exists
                Object key = s.getViewInfo().getUiViewKey();
                CanvasViewInfo vi = findViewInfoKey(key, mLastValidViewInfoRoot);

                // Remove the previous selection -- if the selected object still exists
                // we need to recompute its bounds in case it moved so we'll insert a new one
                // at the same place.
                it.remove();
                if (vi != null) {
                    it.add(new CanvasSelection(vi, mRulesEngine, mNodeFactory));
                }
            }
            fireSelectionChanged();

            // remove the current alternate selection views
            mAltSelection = null;

            if (mImage != null) {
                mHScale.setSize(mImage.getImageData().width, getClientArea().width);
                mVScale.setSize(mImage.getImageData().height, getClientArea().height);
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

    /* package */ void setScale(double scale) {
        mHScale.setScale(scale);
        mVScale.setScale(scale);
        redraw();
    }

    /**
     * Transforms a point, expressed in SWT display coordinates
     * (e.g. from a Drag'n'Drop {@link Event}, not local {@link Control} coordinates)
     * into the canvas' image coordinates according to the current zoom and scroll.
     *
     * @param displayX X in SWT display coordinates
     * @param displayY Y in SWT display coordinates
     * @return A new {@link Point} in canvas coordinates
     */
    /* package */ Point displayToCanvasPoint(int displayX, int displayY) {
        // convert screen coordinates to local SWT control coordinates
        org.eclipse.swt.graphics.Point p = this.toControl(displayX, displayY);

        int x = mHScale.inverseTranslate(p.x);
        int y = mVScale.inverseTranslate(p.y);
        return new Point(x, y);
    }

    /**
     * Transforms a point, expressed in canvas coordinates, into "client" coordinates
     * relative to the control (and not relative to the display.)
     *
     * @param canvasX X in the canvas coordinates
     * @param canvasY Y in the canvas coordinates
     * @return A new {@link Point} in control client coordinates (not display coordinates)
     */
    /* package */ Point canvasToControlPoint(int canvasX, int canvasY) {
        int x = mHScale.translate(canvasX);
        int y = mVScale.translate(canvasY);
        return new Point(x, y);
    }

    //----
    // Implementation of ISelectionProvider

    /**
     * Returns a {@link TreeSelection} compatible with a TreeViewer
     * where each {@link TreePath} item is actually a {@link CanvasViewInfo}.
     */
    public ISelection getSelection() {
        if (mSelections.isEmpty()) {
            return TreeSelection.EMPTY;
        }

        ArrayList<TreePath> paths = new ArrayList<TreePath>();

        for (CanvasSelection cs : mSelections) {
            CanvasViewInfo vi = cs.getViewInfo();
            if (vi != null) {
                ArrayList<Object> segments = new ArrayList<Object>();
                while (vi != null) {
                    segments.add(0, vi);
                    vi = vi.getParent();
                }
                paths.add(new TreePath(segments.toArray()));
            }
        }

        return new TreeSelection(paths.toArray(new TreePath[paths.size()]));
    }

    /**
     * Sets the selection. It must be an {@link ITreeSelection} where each segment
     * of the tree path is a {@link CanvasViewInfo}. A null selection is considered
     * as an empty selection.
     * <p/>
     * This method is invoked by {@link LayoutCanvasViewer#setSelection(ISelection)}
     * in response to an <em>outside</em> selection (compatible with ours) that has
     * changed. Typically it means the outline selection has changed and we're
     * synchronizing ours to match.
     */
    public void setSelection(ISelection selection) {
        if (mInsideUpdateSelection) {
            return;
        }

        try {
            mInsideUpdateSelection = true;

            if (selection == null) {
                selection = TreeSelection.EMPTY;
            }

            if (selection instanceof ITreeSelection) {
                ITreeSelection treeSel = (ITreeSelection) selection;

                if (treeSel.isEmpty()) {
                    // Clear existing selection, if any
                    if (!mSelections.isEmpty()) {
                        mSelections.clear();
                        mAltSelection = null;
                        redraw();
                    }
                    return;
                }

                boolean changed = false;

                // Create a list of all currently selected view infos
                Set<CanvasViewInfo> oldSelected = new HashSet<CanvasViewInfo>();
                for (CanvasSelection cs : mSelections) {
                    oldSelected.add(cs.getViewInfo());
                }

                // Go thru new selection and take care of selecting new items
                // or marking those which are the same as in the current selection
                for (TreePath path : treeSel.getPaths()) {
                    Object seg = path.getLastSegment();
                    if (seg instanceof CanvasViewInfo) {
                        CanvasViewInfo newVi = (CanvasViewInfo) seg;
                        if (oldSelected.contains(newVi)) {
                            // This view info is already selected. Remove it from the
                            // oldSelected list so that we don't de-select it later.
                            oldSelected.remove(newVi);
                        } else {
                            // This view info is not already selected. Select it now.

                            // reset alternate selection if any
                            mAltSelection = null;
                            // otherwise add it.
                            mSelections.add(
                                    new CanvasSelection(newVi, mRulesEngine, mNodeFactory));
                            changed = true;
                        }
                    }
                }

                // De-select old selected items that are not in the new one
                for (CanvasViewInfo vi : oldSelected) {
                    deselect(vi);
                    changed = true;
                }

                if (changed) {
                    redraw();
                    updateMenuActions();
                }

            }
        } finally {
            mInsideUpdateSelection = false;
        }
    }

    public void addSelectionChangedListener(ISelectionChangedListener listener) {
        mSelectionListeners.add(listener);
    }

    public void removeSelectionChangedListener(ISelectionChangedListener listener) {
        mSelectionListeners.remove(listener);
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

    //---

    /**
     * Helper class to convert between control pixel coordinates and canvas coordinates.
     * Takes care of the zooming and offset of the canvas.
     */
    private class ScaleInfo implements ICanvasTransform {
        /** Canvas image size (original, before zoom), in pixels */
        private int mImgSize;

        /** Client size, in pixels */
        private int mClientSize;

        /** Left-top offset in client pixel coordinates */
        private int mTranslate;

        /** Scaling factor, > 0 */
        private double mScale;

        /** Scrollbar widget */
        ScrollBar mScrollbar;

        public ScaleInfo(ScrollBar scrollbar) {
            mScrollbar = scrollbar;
            mScale = 1.0;
            mTranslate = 0;

            mScrollbar.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e) {
                    // User requested scrolling. Changes translation and redraw canvas.
                    mTranslate = mScrollbar.getSelection();
                    redraw();
                }
            });
        }

        /**
         * Sets the new scaling factor. Recomputes scrollbars.
         * @param scale Scaling factor, > 0.
         */
        public void setScale(double scale) {
            if (mScale != scale) {
                mScale = scale;
                resizeScrollbar();
            }
        }

        /** Returns current scaling factor. */
        public double getScale() {
            return mScale;
        }

        /** Returns Canvas image size (original, before zoom), in pixels. */
        public int getImgSize() {
            return mImgSize;
        }

        /** Returns the scaled image size in pixels. */
        public int getScalledImgSize() {
            return (int) (mImgSize * mScale);
        }

        /** Changes the size of the canvas image and the client size. Recomputes scrollbars. */
        public void setSize(int imgSize, int clientSize) {
            mImgSize = imgSize;
            setClientSize(clientSize);
        }

        /** Changes the size of the client size. Recomputes scrollbars. */
        public void setClientSize(int clientSize) {
            mClientSize = clientSize;
            resizeScrollbar();
        }

        private void resizeScrollbar() {
            // scaled image size
            int sx = (int) (mImgSize * mScale);

            // actual client area is always reduced by the margins
            int cx = mClientSize - 2 * IMAGE_MARGIN;

            if (sx < cx) {
                mScrollbar.setEnabled(false);
            } else {
                mScrollbar.setEnabled(true);

                // max scroll value is the scaled image size
                // thumb value is the actual viewable area out of the scaled img size
                mScrollbar.setMaximum(sx);
                mScrollbar.setThumb(cx);
            }
        }

        public int translate(int canvasX) {
            return IMAGE_MARGIN - mTranslate + (int)(mScale * canvasX);
        }

        public int scale(int canwasW) {
            return (int)(mScale * canwasW);
        }

        public int inverseTranslate(int screenX) {
            return (int) ((screenX - IMAGE_MARGIN + mTranslate) / mScale);
        }
    }

    /**
     * Creates or updates the node proxy for this canvas view info.
     * <p/>
     * Since proxies are reused, this will update the bounds of an existing proxy when the
     * canvas is refreshed and a view changes position or size.
     * <p/>
     * This is a recursive call that updates the whole hierarchy starting at the given
     * view info.
     */
    private void updateNodeProxies(CanvasViewInfo vi) {
        if (vi == null) {
            return;
        }

        UiViewElementNode key = vi.getUiViewKey();

        if (key != null) {
            mNodeFactory.create(vi);
        }

        for (CanvasViewInfo child : vi.getChildren()) {
            updateNodeProxies(child);
        }
    }

    /**
     * Sets the image of the last *successful* rendering.
     * Converts the AWT image into an SWT image.
     * <p/>
     * The image *can* be null, which is the case when we are dealing with an empty document.
     */
    private void setImage(BufferedImage awtImage) {
        if (awtImage == null) {
            mImage = null;

        } else {
            int width = awtImage.getWidth();
            int height = awtImage.getHeight();

            Raster raster = awtImage.getData(new java.awt.Rectangle(width, height));
            int[] imageDataBuffer = ((DataBufferInt)raster.getDataBuffer()).getData();

            ImageData imageData = new ImageData(width, height, 32,
                    new PaletteData(0x00FF0000, 0x0000FF00, 0x000000FF));

            imageData.setPixels(0, 0, imageDataBuffer.length, imageDataBuffer, 0);

            mImage = new Image(getDisplay(), imageData);
        }
    }

    /**
     * Sets the alpha for the given GC.
     * <p/>
     * Alpha may not work on all platforms and may fail with an exception, which is
     * hidden here (false is returned in that case).
     *
     * @param gc the GC to change
     * @param alpha the new alpha, 0 for transparent, 255 for opaque.
     * @return True if the operation worked, false if it failed with an exception.
     *
     * @see GC#setAlpha(int)
     */
    private boolean gc_setAlpha(GC gc, int alpha) {
        try {
            gc.setAlpha(alpha);
            return true;
        } catch (SWTException e) {
            return false;
        }
    }

    /**
     * Sets the non-text antialias flag for the given GC.
     * <p/>
     * Antialias may not work on all platforms and may fail with an exception, which is
     * hidden here (-2 is returned in that case).
     *
     * @param gc the GC to change
     * @param alias One of {@link SWT#DEFAULT}, {@link SWT#ON}, {@link SWT#OFF}.
     * @return The previous aliasing mode if the operation worked,
     *         or -2 if it failed with an exception.
     *
     * @see GC#setAntialias(int)
     */
    private int gc_setAntialias(GC gc, int alias) {
        try {
            int old = gc.getAntialias();
            gc.setAntialias(alias);
            return old;
        } catch (SWTException e) {
            return -2;
        }
    }

    /**
     * Paints the canvas in response to paint events.
     */
    private void onPaint(PaintEvent e) {
        GC gc = e.gc;
        gc.setFont(mFont);
        mGCWrapper.setGC(gc);
        try {

            if (mImage != null) {
                if (!mIsResultValid) {
                    gc_setAlpha(gc, 128);  // half-transparent
                }

                ScaleInfo hi = mHScale;
                ScaleInfo vi = mVScale;

                // we only anti-alias when reducing the image size.
                int oldAlias = -2;
                if (hi.getScale() < 1.0) {
                    oldAlias = gc_setAntialias(gc, SWT.ON);
                }

                gc.drawImage(mImage,
                        0,                          // srcX
                        0,                          // srcY
                        hi.getImgSize(),            // srcWidth
                        vi.getImgSize(),            // srcHeight
                        hi.translate(0),            // destX
                        vi.translate(0),            // destY
                        hi.getScalledImgSize(),     // destWidth
                        vi.getScalledImgSize()      // destHeight
                        );

                if (oldAlias != -2) {
                    gc_setAntialias(gc, oldAlias);
                }

                if (!mIsResultValid) {
                    gc_setAlpha(gc, 255);  // opaque
                }
            }

            if (mShowOutline && mLastValidViewInfoRoot != null) {
                gc.setForeground(mOutlineColor);
                gc.setLineStyle(SWT.LINE_DOT);
                drawOutline(gc, mLastValidViewInfoRoot);
            }

            if (mHoverRect != null) {
                gc.setForeground(mHoverFgColor);
                gc.setLineStyle(SWT.LINE_DOT);

                int x = mHScale.translate(mHoverRect.x);
                int y = mVScale.translate(mHoverRect.y);
                int w = mHScale.scale(mHoverRect.width);
                int h = mVScale.scale(mHoverRect.height);

                gc.drawRectangle(x, y, w, h);
            }

            int n = mSelections.size();
            if (n > 0) {
                boolean isMultipleSelection = n > 1;

                if (n == 1) {
                    gc.setForeground(mSelectionFgColor);
                    mSelections.get(0).paintParentSelection(mRulesEngine, mGCWrapper);
                }

                for (CanvasSelection s : mSelections) {
                    gc.setForeground(mSelectionFgColor);
                    s.paintSelection(mRulesEngine, mGCWrapper, isMultipleSelection);
                }
            }

            if (mDropListener != null) {
                mDropListener.paintFeedback(mGCWrapper);
            }

        } finally {
            mGCWrapper.setGC(null);
        }
    }

    private void drawOutline(GC gc, CanvasViewInfo info) {

        Rectangle r = info.getAbsRect();

        int x = mHScale.translate(r.x);
        int y = mVScale.translate(r.y);
        int w = mHScale.scale(r.width);
        int h = mVScale.scale(r.height);

        gc.drawRectangle(x, y, w, h);

        for (CanvasViewInfo vi : info.getChildren()) {
            drawOutline(gc, vi);
        }
    }

    /**
     * Hover on top of a known child.
     */
    private void onMouseMove(MouseEvent e) {
        CanvasViewInfo root = mLastValidViewInfoRoot;

        int x = mHScale.inverseTranslate(e.x);
        int y = mVScale.inverseTranslate(e.y);

        CanvasViewInfo vi = findViewInfoAt(x, y);

        // We don't hover on the root since it's not a widget per see and it is always there.
        if (vi == root) {
            vi = null;
        }

        boolean needsUpdate = vi != mHoverViewInfo;
        mHoverViewInfo = vi;

        if (vi == null) {
            mHoverRect = null;
        } else {
            Rectangle r = vi.getSelectionRect();
            mHoverRect = new Rectangle(r.x, r.y, r.width, r.height);
        }

        if (needsUpdate) {
            redraw();
        }
    }

    private void onMouseDown(MouseEvent e) {
        // Pass, not used yet. We do everything on mouse up.
    }

    /**
     * Performs selection on mouse up (not mouse down).
     * <p/>
     * Shift key is used to toggle in multi-selection.
     * Alt key is used to cycle selection through objects at the same level than the one
     * pointed at (i.e. click on an object then alt-click to cycle).
     */
    private void onMouseUp(MouseEvent e) {

        // Only perform selection when mouse button 1 is used.
        // This prevents right-click from also changing the selection, since it's
        // used to display a context menu that depends on the current selection.
        if (e.button != 1) {
            return;
        }

        boolean isShift = (e.stateMask & SWT.SHIFT) != 0;
        boolean isAlt   = (e.stateMask & SWT.ALT)   != 0;

        int x = mHScale.inverseTranslate(e.x);
        int y = mVScale.inverseTranslate(e.y);

        CanvasViewInfo vi = findViewInfoAt(x, y);

        if (isShift && !isAlt) {
            // Case where shift is pressed: pointed object is toggled.

            // reset alternate selection if any
            mAltSelection = null;

            // If nothing has been found at the cursor, assume it might be a user error
            // and avoid clearing the existing selection.

            if (vi != null) {
                // toggle this selection on-off: remove it if already selected
                if (deselect(vi)) {
                    redraw();
                    return;
                }

                // otherwise add it.
                mSelections.add(new CanvasSelection(vi, mRulesEngine, mNodeFactory));
                fireSelectionChanged();
                redraw();
            }

        } else if (isAlt) {
            // Case where alt is pressed: select or cycle the object pointed at.

            // Note: if shift and alt are pressed, shift is ignored. The alternate selection
            // mechanism does not reset the current multiple selection unless they intersect.

            // We need to remember the "origin" of the alternate selection, to be
            // able to continue cycling through it later. If there's no alternate selection,
            // create one. If there's one but not for the same origin object, create a new
            // one too.
            if (mAltSelection == null || mAltSelection.getOriginatingView() != vi) {
                mAltSelection = new CanvasAlternateSelection(
                        vi, findAltViewInfoAt(x, y, mLastValidViewInfoRoot));

                // deselect them all, in case they were partially selected
                deselectAll(mAltSelection.getAltViews());

                // select the current one
                CanvasViewInfo vi2 = mAltSelection.getCurrent();
                if (vi2 != null) {
                    mSelections.addFirst(new CanvasSelection(vi2, mRulesEngine, mNodeFactory));
                    fireSelectionChanged();
                }
            } else {
                // We're trying to cycle through the current alternate selection.
                // First remove the current object.
                CanvasViewInfo vi2 = mAltSelection.getCurrent();
                deselect(vi2);

                // Now select the next one.
                vi2 = mAltSelection.getNext();
                if (vi2 != null) {
                    mSelections.addFirst(new CanvasSelection(vi2, mRulesEngine, mNodeFactory));
                    fireSelectionChanged();
                }
            }
            redraw();

        } else {
            // Case where no modifier is pressed: either select or reset the selection.

            selectSingle(vi);
        }
    }

    /**
     * Removes all the currently selected item and only select the given item.
     * Issues a {@link #redraw()} if the selection changes.
     *
     * @param vi The new selected item if non-null. Selection becomes empty if null.
     */
    private void selectSingle(CanvasViewInfo vi) {
        // reset alternate selection if any
        mAltSelection = null;

        // reset (multi)selection if any
        if (!mSelections.isEmpty()) {
            if (mSelections.size() == 1 && mSelections.getFirst().getViewInfo() == vi) {
                // CanvasSelection remains the same, don't touch it.
                return;
            }
            mSelections.clear();
        }

        if (vi != null) {
            mSelections.add(new CanvasSelection(vi, mRulesEngine, mNodeFactory));
        }
        fireSelectionChanged();
        redraw();
    }

    /**
     * Deselects a view info.
     * Returns true if the object was actually selected.
     * Callers are responsible for calling redraw() and updateOulineSelection() after.
     */
    private boolean deselect(CanvasViewInfo canvasViewInfo) {
        if (canvasViewInfo == null) {
            return false;
        }

        for (ListIterator<CanvasSelection> it = mSelections.listIterator(); it.hasNext(); ) {
            CanvasSelection s = it.next();
            if (canvasViewInfo == s.getViewInfo()) {
                it.remove();
                return true;
            }
        }

        return false;
    }

    /**
     * Deselects multiple view infos.
     * Callers are responsible for calling redraw() and updateOulineSelection() after.
     */
    private void deselectAll(List<CanvasViewInfo> canvasViewInfos) {
        for (ListIterator<CanvasSelection> it = mSelections.listIterator(); it.hasNext(); ) {
            CanvasSelection s = it.next();
            if (canvasViewInfos.contains(s.getViewInfo())) {
                it.remove();
            }
        }
    }

    private void onDoubleClick(MouseEvent e) {
        // pass, not used yet.
    }

    /**
     * Tries to find a child with the same view key in the view info sub-tree.
     * Returns null if not found.
     */
    private CanvasViewInfo findViewInfoKey(Object viewKey, CanvasViewInfo canvasViewInfo) {
        if (canvasViewInfo == null) {
            return null;
        }
        if (canvasViewInfo.getUiViewKey() == viewKey) {
            return canvasViewInfo;
        }

        // try to find a matching child
        for (CanvasViewInfo child : canvasViewInfo.getChildren()) {
            CanvasViewInfo v = findViewInfoKey(viewKey, child);
            if (v != null) {
                return v;
            }
        }

        return null;
    }


    /**
     * Tries to find the inner most child matching the given x,y coordinates
     * in the view info sub-tree, starting at the last know view info root.
     * This uses the potentially-expanded selection bounds.
     * <p/>
     * Returns null if not found or if there's no view info root.
     */
    /* package */ CanvasViewInfo findViewInfoAt(int x, int y) {
        if (mLastValidViewInfoRoot == null) {
            return null;
        } else {
            return findViewInfoAt_Recursive(x, y, mLastValidViewInfoRoot);
        }
    }

    /**
     * Recursive internal version of {@link #findViewInfoAt(int, int)}. Please don't use directly.
     * <p/>
     * Tries to find the inner most child matching the given x,y coordinates in the view
     * info sub-tree. This uses the potentially-expanded selection bounds.
     *
     * Returns null if not found.
     */
    private CanvasViewInfo findViewInfoAt_Recursive(int x, int y, CanvasViewInfo canvasViewInfo) {
        if (canvasViewInfo == null) {
            return null;
        }
        Rectangle r = canvasViewInfo.getSelectionRect();
        if (r.contains(x, y)) {

            // try to find a matching child first
            for (CanvasViewInfo child : canvasViewInfo.getChildren()) {
                CanvasViewInfo v = findViewInfoAt_Recursive(x, y, child);
                if (v != null) {
                    return v;
                }
            }

            // if no children matched, this is the view that we're looking for
            return canvasViewInfo;
        }

        return null;
    }

    /**
     * Returns a list of all the possible alternatives for a given view at the given
     * position. This is used to build and manage the "alternate" selection that cycles
     * around the parents or children of the currently selected element.
     */
    private List<CanvasViewInfo> findAltViewInfoAt(int x, int y, CanvasViewInfo parent) {
        return findAltViewInfoAt_Recursive(x, y, parent, null);
    }

    /**
     * Internal recursive version of {@link #findAltViewInfoAt(int, int, CanvasViewInfo)}.
     * Please don't use directly.
     */
    private List<CanvasViewInfo> findAltViewInfoAt_Recursive(
            int x, int y, CanvasViewInfo parent, List<CanvasViewInfo> outList) {
        Rectangle r;

        if (outList == null) {
            outList = new ArrayList<CanvasViewInfo>();

            if (parent != null) {
                // add the parent root only once
                r = parent.getSelectionRect();
                if (r.contains(x, y)) {
                    outList.add(parent);
                }
            }
        }

        if (parent != null && !parent.getChildren().isEmpty()) {
            // then add all children that match the position
            for (CanvasViewInfo child : parent.getChildren()) {
                r = child.getSelectionRect();
                if (r.contains(x, y)) {
                    outList.add(child);
                }
            }

            // finally recurse in the children
            for (CanvasViewInfo child : parent.getChildren()) {
                r = child.getSelectionRect();
                if (r.contains(x, y)) {
                    findAltViewInfoAt_Recursive(x, y, child, outList);
                }
            }
        }

        return outList;
    }

    /**
     * Used by {@link #onSelectAll()} to add all current view infos to the selection list.
     *
     * @param canvasViewInfo The root to add. This info and all its children will be added to the
     *                 selection list.
     */
    private void selectAllViewInfos(CanvasViewInfo canvasViewInfo) {
        if (canvasViewInfo != null) {
            mSelections.add(new CanvasSelection(canvasViewInfo, mRulesEngine, mNodeFactory));
            for (CanvasViewInfo vi : canvasViewInfo.getChildren()) {
                selectAllViewInfos(vi);
            }
        }
    }

    /**
     * Notifies listeners that the selection has changed.
     */
    private void fireSelectionChanged() {
        if (mInsideUpdateSelection) {
            return;
        }
        try {
            mInsideUpdateSelection = true;

            final SelectionChangedEvent event = new SelectionChangedEvent(this, getSelection());

            SafeRunnable.run(new SafeRunnable() {
                public void run() {
                    for (Object listener : mSelectionListeners.getListeners()) {
                        ((ISelectionChangedListener)listener).selectionChanged(event);
                    }
                }
            });

            // Update menu actions that depend on the selection
            updateMenuActions();

        } finally {
            mInsideUpdateSelection = false;
        }
    }


    //---------------

    /**
     * Helper to create the drag source for the given control.
     * <p/>
     * This is static with package-access so that {@link OutlinePage2} can also
     * create an exact copy of the source with the same attributes.
     */
    /* package */ static DragSource createDragSource(
            Control control,
            DragSourceListener dragSourceListener) {
        DragSource source = new DragSource(control, DND.DROP_COPY | DND.DROP_MOVE);
        source.setTransfer(new Transfer[] {
                TextTransfer.getInstance(),
                SimpleXmlTransfer.getInstance()
            } );
        source.addDragListener(dragSourceListener);
        return source;
    }

    /**
     * Helper to create the drop target for the given control.
     * <p/>
     * This is static with package-access so that {@link OutlinePage2} can also
     * create an exact copy of the drop target with the same attributes.
     */
    /* package */ static DropTarget createDropTarget(
            Control control,
            DropTargetListener dropListener) {
        DropTarget dropTarget = new DropTarget(
                control, DND.DROP_COPY | DND.DROP_MOVE | DND.DROP_DEFAULT);
        dropTarget.setTransfer(new Transfer[] { SimpleXmlTransfer.getInstance() } );
        dropTarget.addDropListener(dropListener);
        return dropTarget;
    }

    /**
     * Our canvas {@link DragSourceListener}. Handles drag being started and finished
     * and generating the drag data.
     */
    private class CanvasDragSourceListener implements DragSourceListener {

        /**
         * The current selection being dragged.
         * This may be a subset of the canvas selection.
         * Can be empty but never null.
         */
        final ArrayList<CanvasSelection> mDragSelection = new ArrayList<CanvasSelection>();
        private SimpleElement[] mDragElements;

        /**
         * The user has begun the actions required to drag the widget.
         * <p/>
         * Initiate a drag only if there is one or more item selected.
         * If there's none, try to auto-select the one under the cursor.
         *
         * {@inheritDoc}
         */
        public void dragStart(DragSourceEvent e) {
            // We need a selection (simple or multiple) to do any transfer.
            // If there's a selection *and* the cursor is over this selection, use all the
            // currently selected elements.
            // If there is no selection or the cursor is not over a selected element, drag
            // the element under the cursor.
            // If nothing can be selected, abort the drag operation.

            mDragSelection.clear();

            if (!mSelections.isEmpty()) {
                // Is the cursor on top of a selected element?
                int x = mHScale.inverseTranslate(e.x);
                int y = mVScale.inverseTranslate(e.y);

                for (CanvasSelection cs : mSelections) {
                    if (cs.getRect().contains(x, y)) {
                        mDragSelection.addAll(mSelections);
                        break;
                    }
                }

                if (mDragSelection.isEmpty()) {
                    // There is no selected element under the cursor.
                    // We'll now try to find another element.

                    CanvasViewInfo vi = findViewInfoAt(x, y);
                    if (vi != null) {
                        mDragSelection.add(new CanvasSelection(vi, mRulesEngine, mNodeFactory));
                    }
                }
            }

            sanitizeSelection(mDragSelection);

            e.doit = !mDragSelection.isEmpty();
            if (e.doit) {
                mDragElements = getSelectionAsElements(mDragSelection);
                GlobalCanvasDragInfo.getInstance().startDrag(
                        mDragElements,
                        mDragSelection.toArray(new CanvasSelection[mDragSelection.size()]),
                        LayoutCanvas.this);
            }
        }

        /**
         * Callback invoked when data is needed for the event, typically right before drop.
         * The drop side decides what type of transfer to use and this side must now provide
         * the adequate data.
         *
         * {@inheritDoc}
         */
        public void dragSetData(DragSourceEvent e) {
            if (TextTransfer.getInstance().isSupportedType(e.dataType)) {
                e.data = getSelectionAsText(mDragSelection);
                return;
            }

            if (SimpleXmlTransfer.getInstance().isSupportedType(e.dataType)) {
                e.data = mDragElements;
                return;
            }

            // otherwise we failed
            e.detail = DND.DROP_NONE;
            e.doit = false;
        }

        /**
         * Callback invoked when the drop has been finished either way.
         * On a successful move, remove the originating elements.
         */
        public void dragFinished(DragSourceEvent e) {
            if (e.detail == DND.DROP_MOVE) {
                // Remove from source. Since we know the selection, we'll simply
                // create a cut operation on the existing drag selection.

                // Create an undo wrapper, which takes a runnable
                mLayoutEditor.wrapUndoRecording(
                        "Remove drag'n'drop source elements",
                        new Runnable() {
                            public void run() {
                                // Create an edit-XML wrapper, which takes a runnable
                                mLayoutEditor.editXmlModel(new Runnable() {
                                    public void run() {
                                        deleteSelection("Remove", mDragSelection);
                                    }
                                });
                            }
                        });
            }

            // Clear the selection
            mDragSelection.clear();
            mDragElements = null;
            GlobalCanvasDragInfo.getInstance().stopDrag();
        }
    }

    /**
     * Sanitizes the selection for a copy/cut or drag operation.
     * <p/>
     * Sanitizes the list to make sure all elements have a valid XML attached to it,
     * that is remove element that have no XML to avoid having to make repeated such
     * checks in various places after.
     * <p/>
     * In case of multiple selection, we also need to remove all children when their
     * parent is already selected since parents will always be added with all their
     * children.
     * <p/>
     *
     * @param selection The selection list to be sanitized <b>in-place</b>.
     *      The <code>selection</code> argument should not be {@link #mSelections} -- the
     *      given list is going to be altered and we should never alter the user-made selection.
     *      Instead the caller should provide its own copy.
     */
    private void sanitizeSelection(List<CanvasSelection> selection) {
        if (selection.isEmpty()) {
            return;
        }

        for (Iterator<CanvasSelection> it = selection.iterator(); it.hasNext(); ) {
            CanvasSelection cs = it.next();
            CanvasViewInfo vi = cs.getViewInfo();
            UiViewElementNode key = vi == null ? null : vi.getUiViewKey();
            Node node = key == null ? null : key.getXmlNode();
            if (node == null) {
                // Missing ViewInfo or view key or XML, discard this.
                it.remove();
                continue;
            }

            if (vi != null) {
                for (Iterator<CanvasSelection> it2 = selection.iterator();
                     it2.hasNext(); ) {
                    CanvasSelection cs2 = it2.next();
                    if (cs != cs2) {
                        CanvasViewInfo vi2 = cs2.getViewInfo();
                        if (vi.isParent(vi2)) {
                            // vi2 is a parent for vi. Remove vi.
                            it.remove();
                            break;
                        }
                    }
                }
            }
        }
    }

    /**
     * Get the XML text from the given selection for a text transfer.
     * The returned string can be empty but not null.
     */
    private String getSelectionAsText(List<CanvasSelection> selection) {
        StringBuilder sb = new StringBuilder();

        for (CanvasSelection cs : selection) {
            CanvasViewInfo vi = cs.getViewInfo();
            UiViewElementNode key = vi.getUiViewKey();
            Node node = key.getXmlNode();
            String t = getXmlTextFromEditor(mLayoutEditor, node);
            if (t != null) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(t);
            }
        }

        return sb.toString();
    }

    /**
     * Get the XML text directly from the editor.
     */
    private String getXmlTextFromEditor(AndroidXmlEditor editor, Node xml_node) {
        String data = null;
        IStructuredModel model = editor.getModelForRead();
        try {
            IStructuredDocument sse_doc = editor.getStructuredDocument();
            if (xml_node instanceof NodeContainer) {
                // The easy way to get the source of an SSE XML node.
                data = ((NodeContainer) xml_node).getSource();
            } else  if (xml_node instanceof IndexedRegion && sse_doc != null) {
                // Try harder.
                IndexedRegion region = (IndexedRegion) xml_node;
                int start = region.getStartOffset();
                int end = region.getEndOffset();

                if (end > start) {
                    data = sse_doc.get(start, end - start);
                }
            }
        } catch (BadLocationException e) {
            // the region offset was invalid. ignore.
        } finally {
            model.releaseFromRead();
        }
        return data;
    }

    private SimpleElement[] getSelectionAsElements(List<CanvasSelection> mDragSelection) {
        ArrayList<SimpleElement> elements = new ArrayList<SimpleElement>();

        for (CanvasSelection cs : mDragSelection) {
            CanvasViewInfo vi = cs.getViewInfo();

            SimpleElement e = transformToSimpleElement(vi);
            elements.add(e);
        }

        return elements.toArray(new SimpleElement[elements.size()]);
    }

    private SimpleElement transformToSimpleElement(CanvasViewInfo vi) {

        UiViewElementNode uiNode = vi.getUiViewKey();

        String fqcn = SimpleXmlTransfer.getFqcn(uiNode.getDescriptor());
        String parentFqcn = null;
        Rect bounds = new Rect(vi.getAbsRect());
        Rect parentBounds = null;

        UiElementNode uiParent = uiNode.getUiParent();
        if (uiParent != null) {
            parentFqcn = SimpleXmlTransfer.getFqcn(uiParent.getDescriptor());
        }
        if (vi.getParent() != null) {
            parentBounds = new Rect(vi.getParent().getAbsRect());
        }

        SimpleElement e = new SimpleElement(fqcn, parentFqcn, bounds, parentBounds);

        for (UiAttributeNode attr : uiNode.getUiAttributes()) {
            String value = attr.getCurrentValue();
            if (value != null && value.length() > 0) {
                AttributeDescriptor attrDesc = attr.getDescriptor();
                SimpleAttribute a = new SimpleAttribute(
                        attrDesc.getNamespaceUri(),
                        attrDesc.getXmlLocalName(),
                        value);
                e.addAttribute(a);
            }
        }

        for (CanvasViewInfo childVi : vi.getChildren()) {
            SimpleElement e2 = transformToSimpleElement(childVi);
            if (e2 != null) {
                e.addInnerElement(e2);
            }
        }

        return e;
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
                cutSelectionToClipboard(new ArrayList<CanvasSelection>(mSelections));
            }
        };

        tah.setCutAction(mCutAction);
        copyActionAttributes(mCutAction, ActionFactory.CUT);

        mCopyAction = new Action() {
            @Override
            public void run() {
                copySelectionToClipboard(new ArrayList<CanvasSelection>(mSelections));
            }
        };

        tah.setCopyAction(mCopyAction);
        copyActionAttributes(mCopyAction, ActionFactory.COPY);

        mPasteAction = new Action() {
            @Override
            public void run() {
                pasteSelection(new ArrayList<CanvasSelection>(mSelections));
            }
        };

        tah.setPasteAction(mPasteAction);
        copyActionAttributes(mPasteAction, ActionFactory.PASTE);

        mDeleteAction = new Action() {
            @Override
            public void run() {
                deleteSelection(
                        mDeleteAction.getText(), // verb "Delete" from the DELETE action's title
                        new ArrayList<CanvasSelection>(mSelections));
            }
        };

        tah.setDeleteAction(mDeleteAction);
        copyActionAttributes(mDeleteAction, ActionFactory.DELETE);

        mSelectAllAction = new Action() {
            @Override
            public void run() {
                onSelectAll();
            }
        };

        tah.setSelectAllAction(mSelectAllAction);
        copyActionAttributes(mSelectAllAction, ActionFactory.SELECT_ALL);
    }

    /** Update menu actions that depends on the selection. */
    private void updateMenuActions() {

        boolean hasSelection = !mSelections.isEmpty();

        mCutAction.setEnabled(hasSelection);
        mCopyAction.setEnabled(hasSelection);
        mDeleteAction.setEnabled(hasSelection);
        mSelectAllAction.setEnabled(hasSelection);

        // The paste operation is only available if we can paste our custom type.
        // We do not currently support pasting random text (e.g. XML). Maybe later.
        SimpleXmlTransfer sxt = SimpleXmlTransfer.getInstance();
        boolean hasSxt = false;
        for (TransferData td : mClipboard.getAvailableTypes()) {
            if (sxt.isSupportedType(td)) {
                hasSxt = true;
                break;
            }
        }
        mPasteAction.setEnabled(hasSxt);
    }

    /**
     * Helper for {@link #setupGlobalActionHandlers()}.
     * Copies the action attributes form the given {@link ActionFactory}'s action to
     * our action.
     * <p/>
     * {@link ActionFactory} provides access to the standard global actions in Ecipse.
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
     * There's also a dynamic part that is populated by the groovy rules of the
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
        new DynamicContextMenu(this, mMenuManager);
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
        MenuManager showInSubMenu= new MenuManager(showInLabel);
        showInSubMenu.add(
                ContributionItemFactory.VIEWS_SHOW_IN.create(
                        mLayoutEditor.getSite().getWorkbenchWindow()));
        manager.add(showInSubMenu);
    }

    /**
     * Invoked by {@link #mSelectAllAction}. It clears the selection and then
     * selects everything (all views and all their children).
     */
    private void onSelectAll() {
        // First clear the current selection, if any.
        mSelections.clear();
        mAltSelection = null;

        // Now select everything if there's a valid layout
        if (mIsResultValid && mLastValidViewInfoRoot != null) {
            selectAllViewInfos(mLastValidViewInfoRoot);
            redraw();
        }

        fireSelectionChanged();
    }

    /**
     * Perform the "Copy" action, either from the Edit menu or from the context menu.
     * Invoked by {@link #mCopyAction}.
     * <p/>
     * This sanitizes the selection, so it must be a copy. It then inserts the selection
     * both as text and as {@link SimpleElement}s in the clipboard.
     */
    private void copySelectionToClipboard(List<CanvasSelection> selection) {
        sanitizeSelection(selection);

        if (selection.isEmpty()) {
            return;
        }

        Object[] data = new Object[] {
                getSelectionAsElements(selection),
                getSelectionAsText(selection)
        };

        Transfer[] types = new Transfer[] {
                SimpleXmlTransfer.getInstance(),
                TextTransfer.getInstance()
        };

        mClipboard.setContents(data, types);
    }

    /**
     * Perform the "Cut" action, either from the Edit menu or from the context menu.
     * Invoked by {@link #mCutAction}.
     * <p/>
     * This sanitizes the selection, so it must be a copy.
     * It uses the {@link #copySelectionToClipboard(List)} method to copy the selection
     * to the clipboard.
     * Finally it uses {@link #deleteSelection(String, List)} to delete the selection
     * with a "Cut" verb for the title.
     */
    private void cutSelectionToClipboard(List<CanvasSelection> selection) {
        copySelectionToClipboard(selection);
        deleteSelection(
                mCutAction.getText(), // verb "Cut" from the CUT action's title
                selection);
    }

    /**
     * Deletes the given selection.
     * <p/>
     * This can either be invoked directly by {@link #mDeleteAction}, or as
     * an implementation detail as part of {@link #mCutAction} or also when removing
     * the elements after a successful "MOVE" drag'n'drop.
     *
     * @param verb A translated verb for the action. Will be used for the undo/redo title.
     *   Typically this should be {@link Action#getText()} for either
     *   {@link #mCutAction} or {@link #mDeleteAction}.
     * @param selection The selection. Must not be null. Can be empty, in which case nothing
     *   happens. The selection list will be sanitized so the caller should give a copy of
     *   {@link #mSelections}, directly or indirectly.
     */
    private void deleteSelection(String verb, final List<CanvasSelection> selection) {
        sanitizeSelection(selection);

        if (selection.isEmpty()) {
            return;
        }

        // If all selected items have the same *kind* of parent, display that in the undo title.
        String title = null;
        for (CanvasSelection cs : selection) {
            CanvasViewInfo vi = cs.getViewInfo();
            if (vi != null && vi.getParent() != null) {
                if (title == null) {
                    title = vi.getParent().getName();
                } else if (!title.equals(vi.getParent().getName())) {
                    // More than one kind of parent selected.
                    title = null;
                    break;
                }
            }
        }

        if (title != null) {
            // Typically the name is an FQCN. Just get the last segment.
            int pos = title.lastIndexOf('.');
            if (pos > 0 && pos < title.length() - 1) {
                title = title.substring(pos + 1);
            }
        }
        boolean multiple = mSelections.size() > 1;
        if (title == null) {
            title = String.format(
                        multiple ? "%1$s elements" : "%1$s element",
                        verb);
        } else {
            title = String.format(
                        multiple ? "%1$s elements from %2$s" : "%1$s element from %2$s",
                        verb, title);
        }

        // Implementation note: we don't clear the internal selection after removing
        // the elements. An update XML model event should happen when the model gets released
        // which will trigger a recompute of the layout, thus reloading the model thus
        // resetting the selection.
        mLayoutEditor.wrapUndoRecording(title, new Runnable() {
            public void run() {
                mLayoutEditor.editXmlModel(new Runnable() {
                    public void run() {
                        for (CanvasSelection cs : selection) {
                            CanvasViewInfo vi = cs.getViewInfo();
                            if (vi != null) {
                                UiViewElementNode ui = vi.getUiViewKey();
                                if (ui != null) {
                                    ui.deleteXmlNode();
                                }
                            }
                        }
                    }
                });
            }
        });
    }

    /**
     * Perform the "Paste" action, either from the Edit menu or from the context menu.
     */
    private void pasteSelection(List<CanvasSelection> selection) {

        SimpleXmlTransfer sxt = SimpleXmlTransfer.getInstance();
        SimpleElement[] pasted = (SimpleElement[]) mClipboard.getContents(sxt);

        if (pasted == null || pasted.length == 0) {
            return;
        }

        if (mLastValidViewInfoRoot == null) {
            // Pasting in an empty document. Only paste the first element.
            pasteInEmptyDocument(pasted[0]);
            return;
        }

        // Otherwise use the current selection, if any, as a guide where to paste
        // using the first selected element only. If there's no selection use
        // the root as the insertion point.
        sanitizeSelection(selection);
        CanvasViewInfo target = mLastValidViewInfoRoot;
        if (selection.size() > 0) {
            CanvasSelection cs = selection.get(0);
            target = cs.getViewInfo();
        }

        NodeProxy targetNode = mNodeFactory.create(target);

        getRulesEngine().callOnPaste(targetNode, pasted);
    }

    /**
     * Paste a new root into an empty XML layout.
     * <p/>
     * In case of error (unknown FQCN, document not empty), silently do nothing.
     * In case of success, the new element will have some default attributes set (xmlns:android,
     * layout_width and height). The edit is wrapped in a proper undo.
     * <p/>
     * Implementation is similar to {@link #createDocumentRoot(String)} except we also
     * copy all the attributes and inner elements recursively.
     */
    private void pasteInEmptyDocument(final IDragElement pastedElement) {
        String rootFqcn = pastedElement.getFqcn();

        // Need a valid empty document to create the new root
        final UiDocumentNode uiDoc = mLayoutEditor.getUiRootNode();
        if (uiDoc == null || uiDoc.getUiChildren().size() > 0) {
            debugPrintf("Failed to paste document root for %1$s: document is not empty", rootFqcn);
            return;
        }

        // Find the view descriptor matching our FQCN
        final ViewElementDescriptor viewDesc = mLayoutEditor.getFqcnViewDescritor(rootFqcn);
        if (viewDesc == null) {
            // TODO this could happen if pasting a custom view not known in this project
            debugPrintf("Failed to paste document root, unknown FQCN %1$s", rootFqcn);
            return;
        }

        // Get the last segment of the FQCN for the undo title
        String title = rootFqcn;
        int pos = title.lastIndexOf('.');
        if (pos > 0 && pos < title.length() - 1) {
            title = title.substring(pos + 1);
        }
        title = String.format("Paste root %1$s in document", title);

        mLayoutEditor.wrapUndoRecording(title, new Runnable() {
            public void run() {
                mLayoutEditor.editXmlModel(new Runnable() {
                    public void run() {
                        UiElementNode uiNew = uiDoc.appendNewUiChild(viewDesc);

                        // A root node requires the Android XMLNS
                        uiNew.setAttributeValue(
                                "android",
                                XmlnsAttributeDescriptor.XMLNS_URI,
                                SdkConstants.NS_RESOURCES,
                                true /*override*/);

                        // Copy all the attributes from the pasted element
                        for (IDragAttribute attr : pastedElement.getAttributes()) {
                            uiNew.setAttributeValue(
                                    attr.getName(),
                                    attr.getUri(),
                                    attr.getValue(),
                                    true /*override*/);
                        }

                        // Adjust the attributes, adding the default layout_width/height
                        // only if they are not present (the original element should have
                        // them though.)
                        DescriptorsUtils.setDefaultLayoutAttributes(uiNew, false /*updateLayout*/);

                        uiNew.createXmlNode();

                        // Now process all children
                        for (IDragElement childElement : pastedElement.getInnerElements()) {
                            addChild(uiNew, childElement);
                        }
                    }

                    private void addChild(UiElementNode uiParent, IDragElement childElement) {
                        String childFqcn = childElement.getFqcn();
                        final ViewElementDescriptor childDesc =
                            mLayoutEditor.getFqcnViewDescritor(childFqcn);
                        if (childDesc == null) {
                            // TODO this could happen if pasting a custom view
                            debugPrintf("Failed to paste element, unknown FQCN %1$s", childFqcn);
                            return;
                        }

                        UiElementNode uiChild = uiParent.appendNewUiChild(childDesc);

                        // Copy all the attributes from the pasted element
                        for (IDragAttribute attr : childElement.getAttributes()) {
                            uiChild.setAttributeValue(
                                    attr.getName(),
                                    attr.getUri(),
                                    attr.getValue(),
                                    true /*override*/);
                        }

                        // Adjust the attributes, adding the default layout_width/height
                        // only if they are not present (the original element should have
                        // them though.)
                        DescriptorsUtils.setDefaultLayoutAttributes(
                                uiChild, false /*updateLayout*/);

                        uiChild.createXmlNode();

                        // Now process all grand children
                        for (IDragElement grandChildElement : childElement.getInnerElements()) {
                            addChild(uiChild, grandChildElement);
                        }
                    }
                });
            }
        });
    }

    /**
     * Add new root in an existing empty XML layout.
     * <p/>
     * In case of error (unknown FQCN, document not empty), silently do nothing.
     * In case of success, the new element will have some default attributes set (xmlns:android,
     * layout_width and height). The edit is wrapped in a proper undo.
     * <p/>
     * This is invoked by {@link CanvasDropListener#drop(org.eclipse.swt.dnd.DropTargetEvent)}.
     *
     * @param rootFqcn A non-null non-empty FQCN that must match an existing {@link ViewDescriptor}
     *   to add as root to the current empty XML document.
     */
    /* package */ void createDocumentRoot(String rootFqcn) {

        // Need a valid empty document to create the new root
        final UiDocumentNode uiDoc = mLayoutEditor.getUiRootNode();
        if (uiDoc == null || uiDoc.getUiChildren().size() > 0) {
            debugPrintf("Failed to create document root for %1$s: document is not empty", rootFqcn);
            return;
        }

        // Find the view descriptor matching our FQCN
        final ViewElementDescriptor viewDesc = mLayoutEditor.getFqcnViewDescritor(rootFqcn);
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

        mLayoutEditor.wrapUndoRecording(title, new Runnable() {
            public void run() {
                mLayoutEditor.editXmlModel(new Runnable() {
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
        });
    }

    private void debugPrintf(String message, Object... params) {
        if (DEBUG) AdtPlugin.printToConsole("Canvas", String.format(message, params));
    }

}
