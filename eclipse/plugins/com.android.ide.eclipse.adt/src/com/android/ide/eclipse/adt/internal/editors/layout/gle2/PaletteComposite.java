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

import com.android.ide.common.api.Rect;
import com.android.ide.common.layoutlib.LayoutLibrary;
import com.android.ide.eclipse.adt.internal.editors.descriptors.DescriptorsUtils;
import com.android.ide.eclipse.adt.internal.editors.descriptors.DocumentDescriptor;
import com.android.ide.eclipse.adt.internal.editors.descriptors.ElementDescriptor;
import com.android.ide.eclipse.adt.internal.editors.layout.IGraphicalLayoutEditor;
import com.android.ide.eclipse.adt.internal.editors.layout.LayoutConstants;
import com.android.ide.eclipse.adt.internal.editors.layout.LayoutEditor;
import com.android.ide.eclipse.adt.internal.editors.layout.descriptors.LayoutDescriptors;
import com.android.ide.eclipse.adt.internal.editors.uimodel.UiDocumentNode;
import com.android.ide.eclipse.adt.internal.editors.uimodel.UiElementNode;
import com.android.ide.eclipse.adt.internal.sdk.AndroidTargetData;
import com.android.layoutlib.api.LayoutBridge;
import com.android.layoutlib.api.LayoutScene;
import com.android.layoutlib.api.SceneResult;
import com.android.layoutlib.api.ViewInfo;
import com.android.sdklib.SdkConstants;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DragSource;
import org.eclipse.swt.dnd.DragSourceEffect;
import org.eclipse.swt.dnd.DragSourceEvent;
import org.eclipse.swt.dnd.DragSourceListener;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.ControlListener;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.MouseTrackListener;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.ScrollBar;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * A palette composite for the {@link GraphicalEditorPart}.
 * <p/>
 * The palette contains several groups, each with a UI name (e.g. layouts and views) and each
 * with a list of element descriptors.
 * <p/>
 *
 * @since GLE2
 *
 * TODO list:
 *   - The available items should depend on the actual GLE2 Canvas selection. Selected android
 *     views should force filtering on what they accept can be dropped on them (e.g. TabHost,
 *     TableLayout). Should enable/disable them, not hide them, to avoid shuffling around.
 *   - Optional: a text filter
 *   - Optional: have icons that depict the element and/or automatically rendered icons
 *     based on a rendering of the widget.
 *   - Optional: have context-sensitive tools items, e.g. selection arrow tool,
 *     group selection tool, alignment, etc.
 *   - Different view strategies: big icon, small icons, text vs no text, compact grid.
 *     - This would only be useful with meaningful icons. Out current 1-letter icons are not enough
 *       to get rid of text labels.
 */
public class PaletteComposite extends Composite {


    /** The parent grid layout that contains all the {@link Toggle} and {@link Item} widgets. */
    private Composite mRoot;
    private ScrollBar mVBar;
    private ControlListener mControlListener;
    private Listener mScrollbarListener;
    private IGraphicalLayoutEditor mEditor;

    /**
     * Create the composite.
     * @param parent The parent composite.
     * @param editor An editor associated with this palette.
     */
    public PaletteComposite(Composite parent, IGraphicalLayoutEditor editor) {
        super(parent, SWT.BORDER | SWT.V_SCROLL);

        mEditor = editor;
        mVBar = getVerticalBar();

        mScrollbarListener = new Listener() {
            public void handleEvent(Event event) {
                scrollScrollbar();
            }
        };

        mVBar.addListener(SWT.Selection, mScrollbarListener);


        mControlListener = new ControlListener() {
            public void controlMoved(ControlEvent e) {
                // Ignore
            }
            public void controlResized(ControlEvent e) {
                if (recomputeScrollbar()) {
                    redraw();
                }
            }
        };

        addControlListener(mControlListener);
    }

    @Override
    protected void checkSubclass() {
        // Disable the check that prevents subclassing of SWT components
    }

    @Override
    public void dispose() {
        if (mControlListener != null) {
            removeControlListener(mControlListener);
            mControlListener = null;
        }

        if (mVBar != null && !mVBar.isDisposed()) {
            if (mScrollbarListener != null) {
                mVBar.removeListener(SWT.Selection, mScrollbarListener);
            }
            mVBar = null;
        }

        super.dispose();
    }

    /**
     * Loads or reloads the palette elements by using the layout and view descriptors from the
     * given target data.
     *
     * @param targetData The target data that contains the descriptors. If null or empty,
     *   no groups will be created.
     */
    public void reloadPalette(AndroidTargetData targetData) {

        for (Control c : getChildren()) {
            c.dispose();
        }

        setGridLayout(this, 2);

        mRoot = new Composite(this, SWT.NONE);
        setGridLayout(mRoot, 0);

        if (targetData != null) {
            addGroup(mRoot, "Views", targetData.getLayoutDescriptors().getViewDescriptors());
            addGroup(mRoot, "Layouts", targetData.getLayoutDescriptors().getLayoutDescriptors());
        }

        layout(true);

        recomputeScrollbar();
    }

    // ----- private methods ----

    /** Returns true if scrollbar changed. */
    private  boolean recomputeScrollbar() {
        if (mVBar != null && mRoot != null) {

            int sel = mVBar.getSelection();
            int max = mVBar.getMaximum();
            float current = max > 0 ? (float)sel / max : 0;

            int ry = mRoot.getSize().y;

            // The root contains composite groups
            // which in turn contain Toggle/Item CLabel instances
            Control[] children = mRoot.getChildren();
            findVisibleItem: for (int i = children.length - 1; i >= 0; i--) {
                Control ci = children[i];
                if (ci.isVisible() && ci instanceof Composite) {
                    Control[] children2 = ((Composite) ci).getChildren();
                    for (int j = children2.length - 1; j >= 0; j--) {
                        Control cj = children2[j];
                        if (cj.isVisible()) {
                            // This is the bottom-most visible item
                            ry = ci.getLocation().y + cj.getLocation().y + cj.getSize().y;
                            break findVisibleItem;
                        }
                    }
                }
            }


            int vy = getSize().y;
            // Scrollable size is the height of the root view
            // less the current view visible height.
            int y = ry > vy ? ry - vy + 2 : 0;
            // Thumb size is the ratio between root view and visible height.
            float ft = ry > 0 ? (float)vy / ry : 1;
            int thumb = (int) Math.ceil(y * ft);
            y += thumb;


            if (y != max) {
                mVBar.setEnabled(y > 0);
                mVBar.setMaximum(y < 0 ? 1 : y);
                mVBar.setSelection((int) (y * current));
                mVBar.setThumb(thumb);
                scrollScrollbar();
                return true;
            }
        }

        return false;
    }

    private void scrollScrollbar() {
        if (mVBar != null && mRoot != null) {
            Point p = mRoot.getLocation();
            p.y = - mVBar.getSelection();
            mRoot.setLocation(p);
        }
    }

    private void setGridLayout(Composite parent, int spacing) {
        GridLayout gl = new GridLayout(1, false);
        gl.horizontalSpacing = 0;
        gl.verticalSpacing = 0;
        gl.marginHeight = spacing;
        gl.marginBottom = spacing;
        gl.marginLeft = spacing;
        gl.marginRight = spacing;
        gl.marginTop = spacing;
        gl.marginBottom = spacing;
        parent.setLayout(gl);
    }

    private void addGroup(Composite parent,
            String uiName,
            List<ElementDescriptor> descriptors) {

        Composite group = new Composite(parent, SWT.NONE);
        setGridLayout(group, 0);

        Toggle toggle = new Toggle(group, uiName);

        for (ElementDescriptor desc : descriptors) {

            // Exclude the <include> tag from the View palette.
            // We don't have drop support for it right now, although someday we should.
            if (LayoutDescriptors.VIEW_INCLUDE.equals(desc.getXmlName())) {
                continue;
            }

            Item item = new Item(group, this, desc);
            toggle.addItem(item);
            GridData gd = new GridData();
            item.setLayoutData(gd);
        }
    }

    /* package */ IGraphicalLayoutEditor getEditor() {
        return mEditor;
    }

    /**
     * A Toggle widget is a row that is the head of a group.
     * <p/>
     * When clicked, the toggle will show/hide all the {@link Item} widgets that have been
     * added to it using {@link #addItem(Item)}.
     */
    private class Toggle extends CLabel implements MouseTrackListener, MouseListener {
        private boolean mMouseIn;
        private DragSource mSource;
        private ArrayList<Item> mItems = new ArrayList<Item>();

        public Toggle(Composite parent, String groupName) {
            super(parent, SWT.NONE);
            mMouseIn = false;

            setData(null);

            String s = String.format("-= %s =-", groupName);
            setText(s);
            setToolTipText(s);
            //TODO use triangle icon and swap it -- setImage(desc.getIcon());
            addMouseTrackListener(this);
            addMouseListener(this);
        }

        public void addItem(Item item) {
            mItems.add(item);
        }

        @Override
        public void dispose() {
            if (mSource != null) {
                mSource.dispose();
                mSource = null;
            }
            super.dispose();
        }

        @Override
        public int getStyle() {
            int style = super.getStyle();
            if (mMouseIn) {
                style |= SWT.SHADOW_IN;
            }
            return style;
        }

        // -- MouseTrackListener callbacks

        public void mouseEnter(MouseEvent e) {
            if (!mMouseIn) {
                mMouseIn = true;
                redraw();
            }
        }

        public void mouseExit(MouseEvent e) {
            if (mMouseIn) {
                mMouseIn = false;
                redraw();
            }
        }

        public void mouseHover(MouseEvent e) {
            // pass
        }

        // -- MouseListener callbacks

        public void mouseDoubleClick(MouseEvent arg0) {
            // pass
        }

        public void mouseDown(MouseEvent arg0) {
            // pass
        }

        public void mouseUp(MouseEvent arg0) {
            for (Item i : mItems) {
                if (i.isVisible()) {
                    Object ld = i.getLayoutData();
                    if (ld instanceof GridData) {
                        GridData gd = (GridData) ld;

                        i.setData(gd.heightHint != SWT.DEFAULT ?
                                    Integer.valueOf(gd.heightHint) :
                                        null);
                        gd.heightHint = 0;
                    }
                } else {
                    Object ld = i.getLayoutData();
                    if (ld instanceof GridData) {
                        GridData gd = (GridData) ld;

                        Object d = i.getData();
                        if (d instanceof Integer) {
                            gd.heightHint = ((Integer) d).intValue();
                        } else {
                            gd.heightHint = SWT.DEFAULT;
                        }
                    }
                }
                i.setVisible(!i.isVisible());
            }

            // Tell the root composite that its content changed.
            mRoot.layout(true /*changed*/);
            // Force the top composite to recompute the scrollbar and refresh it.
            mControlListener.controlResized(null /*event*/);
        }
    }

    /**
     * An Item widget represents one {@link ElementDescriptor} that can be dropped on the
     * GLE2 canvas using drag'n'drop.
     */
    private static class Item extends CLabel implements MouseTrackListener {

        private boolean mMouseIn;
        private DragSource mSource;
        private final ElementDescriptor mDesc;
        public PaletteComposite mPalette;

        public Item(Composite parent, PaletteComposite palette, ElementDescriptor desc) {
            super(parent, SWT.NONE);
            mPalette = palette;
            mDesc = desc;
            mMouseIn = false;

            setText(desc.getUiName());
            setImage(desc.getIcon());
            setToolTipText(desc.getTooltip());
            addMouseTrackListener(this);

            // DND Reference: http://www.eclipse.org/articles/Article-SWT-DND/DND-in-SWT.html
            mSource = new DragSource(this, DND.DROP_COPY);
            mSource.setTransfer(new Transfer[] { SimpleXmlTransfer.getInstance() });
            mSource.addDragListener(new DescDragSourceListener(this));
            mSource.setDragSourceEffect(new PreviewDragSourceEffect(this));
        }

        @Override
        public void dispose() {
            if (mSource != null) {
                mSource.dispose();
                mSource = null;
            }
            super.dispose();
        }

        @Override
        public int getStyle() {
            int style = super.getStyle();
            if (mMouseIn) {
                style |= SWT.SHADOW_IN;
            }
            return style;
        }

        public void mouseEnter(MouseEvent e) {
            if (!mMouseIn) {
                mMouseIn = true;
                redraw();
            }
        }

        public void mouseExit(MouseEvent e) {
            if (mMouseIn) {
                mMouseIn = false;
                redraw();
            }
        }

        public void mouseHover(MouseEvent e) {
            // pass
        }

        /* package */ ElementDescriptor getDescriptor() {
            return mDesc;
        }

        /* package */ IGraphicalLayoutEditor getEditor() {
            return mPalette.getEditor();
        }

        /* package */ DragSource getDragSource() {
            return mSource;
        }
    }

    /**
     * A {@link DragSourceListener} that deals with drag'n'drop of
     * {@link ElementDescriptor}s.
     */
    private static class DescDragSourceListener implements DragSourceListener {
        private final Item mItem;
        private SimpleElement[] mElements;

        public DescDragSourceListener(Item item) {
            mItem = item;
        }

        public void dragStart(DragSourceEvent e) {
            // See if we can find out the bounds of this element from a preview image.
            // Preview images are created before the drag source listener is notified
            // of the started drag.
            Rect bounds = null;
            DragSource dragSource = mItem.getDragSource();
            DragSourceEffect dragSourceEffect = dragSource.getDragSourceEffect();
            if (dragSourceEffect instanceof PreviewDragSourceEffect) {
                PreviewDragSourceEffect preview = (PreviewDragSourceEffect) dragSourceEffect;
                Image previewImage = preview.getPreviewImage();
                if (previewImage != null && !preview.isPlaceholder()) {
                    ImageData data = previewImage.getImageData();
                    bounds = new Rect(0, 0, data.width, data.height);
                }
            }

            SimpleElement se = new SimpleElement(
                    SimpleXmlTransfer.getFqcn(mItem.getDescriptor()),
                    null   /* parentFqcn */,
                    bounds /* bounds */,
                    null   /* parentBounds */);
            mElements = new SimpleElement[] { se };

            // Register this as the current dragged data
            GlobalCanvasDragInfo.getInstance().startDrag(
                    mElements,
                    null /* selection */,
                    null /* canvas */,
                    null /* removeSource */);
        }

        public void dragSetData(DragSourceEvent e) {
            // Provide the data for the drop when requested by the other side.
            if (SimpleXmlTransfer.getInstance().isSupportedType(e.dataType)) {
                e.data = mElements;
            }
        }

        public void dragFinished(DragSourceEvent e) {
            // Unregister the dragged data.
            GlobalCanvasDragInfo.getInstance().stopDrag();
            mElements = null;
        }
    }

    /**
     * A {@link DragSourceEffect} (an image shown under the drag cursor) which renders a
     * preview of the given item.
     */
    private static class PreviewDragSourceEffect extends DragSourceEffect {
        // TODO: Figure out the right dimensions to use for rendering.
        // We WILL crop this after rendering, but for performance reasons it would be good
        // not to make it much larger than necessary since to crop this we rely on
        // actually scanning pixels.

        /** Width of the rendered preview image (before it is cropped) */
        private static final int RENDER_HEIGHT = 400;

        /** Height of the rendered preview image (before it is cropped) */
        private static final int RENDER_WIDTH = 500;

        /** Amount of alpha to multiply into the image (divided by 256) */
        private static final int IMG_ALPHA = 192;

        /** The item this preview is rendering a preview for */
        private final Item mItem;

        /** The image shown by the drag source effect */
        private Image mImage;

        /**
         * If true, the image is a preview of the view, and if not it is a "fallback"
         * image of some sort, such as a rendering of the palette item itself
         */
        private boolean mIsPlaceholder;

        private PreviewDragSourceEffect(Item item) {
            super(item);
            mItem = item;
        }

        @Override
        public void dragStart(DragSourceEvent event) {
            mImage = renderPreview();

            mIsPlaceholder = mImage == null;
            if (mIsPlaceholder) {
                // Couldn't render preview (or the preview is a blank image, such as for
                // example the preview of an empty layout), so instead create a placeholder
                // image
                // Render the palette item itself as an image
                Control control = getControl();
                GC gc = new GC(control);
                Point size = control.getSize();
                final Image image = new Image(mItem.getDisplay(), size.x, size.y);
                gc.copyArea(image, 0, 0);
                gc.dispose();
                ImageData data = image.getImageData();

                data.alpha = IMG_ALPHA;

                // Changing the ImageData -after- constructing an image on it
                // has no effect, so we have to construct a new image. Luckily these
                // are tiny images.
                mImage = new Image(mItem.getDisplay(), data);
                image.dispose();
            }

            event.image = mImage;

            if (!mIsPlaceholder) {
                // Shift the drag feedback image up such that it's centered under the
                // mouse pointer

                // Eclipse 3.4 does not support drag image offsets
                // TODO: Replace by direct field access when we drop Eclipse 3.4 support.
                try {
                    Field xField = event.getClass().getDeclaredField("offsetX"); //$NON-NLS-1$
                    Field yField = event.getClass().getDeclaredField("offsetY"); //$NON-NLS-1$

                    Rectangle imageBounds = mImage.getBounds();
                    int offsetX = imageBounds.width / 2;
                    int offsetY = imageBounds.height / 2;
                    xField.set(event, Integer.valueOf(offsetX));
                    yField.set(event, Integer.valueOf(offsetY));
                } catch (SecurityException e) {
                } catch (NoSuchFieldException e) {
                } catch (IllegalArgumentException e) {
                } catch (IllegalAccessException e) {
                }
            }
        }

        @Override
        public void dragFinished(DragSourceEvent event) {
            super.dragFinished(event);

            if (mImage != null) {
                mImage.dispose();
                mImage = null;
            }
        }

        @Override
        public void dragSetData(DragSourceEvent event) {
            super.dragSetData(event);
        }

        /** Performs the actual rendering of the descriptor into an image */
        private Image renderPreview() {
            // Create blank XML document
            Document document = null;
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            try {
                DocumentBuilder builder = factory.newDocumentBuilder();
                document = builder.newDocument();
            } catch (ParserConfigurationException e) {
                return null;
            }

            // Insert our target view's XML into it as a node
            ElementDescriptor desc = mItem.getDescriptor();
            IGraphicalLayoutEditor editor = mItem.getEditor();
            LayoutEditor layoutEditor = editor.getLayoutEditor();

            String viewName = desc.getXmlLocalName();
            Element element = document.createElement(viewName);
            element.setAttributeNS(SdkConstants.NS_RESOURCES,
                    LayoutConstants.ATTR_LAYOUT_WIDTH, LayoutConstants.VALUE_WRAP_CONTENT);
            element.setAttributeNS(SdkConstants.NS_RESOURCES,
                    LayoutConstants.ATTR_LAYOUT_HEIGHT, LayoutConstants.VALUE_WRAP_CONTENT);

            // This doesn't apply to all, but doesn't seem to cause harm and makes for a
            // better experience with text-oriented views like buttons and texts
            UiElementNode uiRoot = layoutEditor.getUiRootNode();
            String text = DescriptorsUtils.getFreeWidgetId(uiRoot, viewName);
            element.setAttributeNS(SdkConstants.NS_RESOURCES, LayoutConstants.ATTR_TEXT, text);

            document.insertBefore(element, null);

            // Construct UI model from XML
            DocumentDescriptor documentDescriptor =
                new DocumentDescriptor("layout_doc", null); //$NON-NLS-1$
            UiDocumentNode model = new UiDocumentNode(documentDescriptor);
            model.setEditor(layoutEditor);
            model.loadFromXmlNode(document);

            boolean hasTransparency = false;
            LayoutLibrary layoutLibrary = editor.getLayoutLibrary();
            if (layoutLibrary != null) {
                LayoutBridge bridge = layoutLibrary.getBridge();
                if (bridge != null) {
                    hasTransparency = bridge.getApiLevel() >= 5;
                }
            }

            LayoutScene result = editor.render(model, RENDER_WIDTH, RENDER_HEIGHT,
                    null /* explodeNodes */, hasTransparency);

            if (result != null && result.getResult() == SceneResult.SUCCESS) {
                BufferedImage image = result.getImage();
                if (image != null) {
                    BufferedImage cropped;
                    Rect initialCrop = null;
                    ViewInfo viewInfo = result.getRootView();
                    if (viewInfo != null) {
                        int x1 = viewInfo.getLeft();
                        int x2 = viewInfo.getRight();
                        int y2 = viewInfo.getBottom();
                        int y1 = viewInfo.getTop();
                        initialCrop = new Rect(x1, y1, x2 - x1, y2 - y1);
                    }

                    if (hasTransparency) {
                        cropped = SwtUtils.cropBlank(image, initialCrop);
                    } else {
                        int edgeColor = image.getRGB(image.getWidth() - 1, image.getHeight() - 1);
                        cropped = SwtUtils.cropColor(image, edgeColor, initialCrop);
                    }

                    if (cropped != null) {
                        Display display = getControl().getDisplay();
                        Image swtImage = SwtUtils.convertImage(display, cropped, true, IMG_ALPHA);
                        return swtImage;
                    }
                }
            }

            return null;
        }

        /**
         * Returns the image shown as the drag source effect. The image may be a preview
         * of the palette item, or just a placeholder image; {@link #isPlaceholder()} can
         * tell the difference.
         *
         * @return the image shown as preview. May be null (between drags).
         */
        /* package */ Image getPreviewImage() {
            return mImage;
        }

        /**
         * Returns true if the image returned by {@link #getPreviewImage()} is just a
         * placeholder for a real preview, and false if the image is an actual preview.
         *
         * @return true if the preview image is just a placeholder
         */
        /* package */ boolean isPlaceholder() {
            return mIsPlaceholder;
        }
    }
}
