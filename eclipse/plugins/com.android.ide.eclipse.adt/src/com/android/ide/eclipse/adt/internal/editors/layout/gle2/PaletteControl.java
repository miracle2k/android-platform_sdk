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

import static com.android.ide.common.layout.LayoutConstants.ANDROID_URI;
import static com.android.ide.common.layout.LayoutConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.ide.common.layout.LayoutConstants.ATTR_LAYOUT_WIDTH;
import static com.android.ide.common.layout.LayoutConstants.ATTR_TEXT;
import static com.android.ide.common.layout.LayoutConstants.VALUE_WRAP_CONTENT;

import com.android.ide.common.api.InsertType;
import com.android.ide.common.api.Rect;
import com.android.ide.common.rendering.LayoutLibrary;
import com.android.ide.common.rendering.api.Capability;
import com.android.ide.common.rendering.api.LayoutLog;
import com.android.ide.common.rendering.api.Params.RenderingMode;
import com.android.ide.common.rendering.api.RenderSession;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.ide.eclipse.adt.internal.editors.IconFactory;
import com.android.ide.eclipse.adt.internal.editors.descriptors.DocumentDescriptor;
import com.android.ide.eclipse.adt.internal.editors.descriptors.ElementDescriptor;
import com.android.ide.eclipse.adt.internal.editors.descriptors.XmlnsAttributeDescriptor;
import com.android.ide.eclipse.adt.internal.editors.layout.LayoutEditor;
import com.android.ide.eclipse.adt.internal.editors.layout.configuration.ConfigurationComposite;
import com.android.ide.eclipse.adt.internal.editors.layout.descriptors.ViewElementDescriptor;
import com.android.ide.eclipse.adt.internal.editors.layout.gre.NodeFactory;
import com.android.ide.eclipse.adt.internal.editors.layout.gre.NodeProxy;
import com.android.ide.eclipse.adt.internal.editors.layout.gre.ViewMetadataRepository;
import com.android.ide.eclipse.adt.internal.editors.layout.uimodel.UiViewElementNode;
import com.android.ide.eclipse.adt.internal.editors.ui.DecorComposite;
import com.android.ide.eclipse.adt.internal.editors.ui.IDecorContent;
import com.android.ide.eclipse.adt.internal.editors.uimodel.UiDocumentNode;
import com.android.ide.eclipse.adt.internal.editors.uimodel.UiElementNode;
import com.android.ide.eclipse.adt.internal.preferences.AdtPrefs;
import com.android.ide.eclipse.adt.internal.sdk.AndroidTargetData;
import com.android.ide.eclipse.adt.internal.sdk.Sdk;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkConstants;
import com.android.sdklib.util.Pair;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DragSource;
import org.eclipse.swt.dnd.DragSourceEvent;
import org.eclipse.swt.dnd.DragSourceListener;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.MenuDetectEvent;
import org.eclipse.swt.events.MenuDetectListener;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseTrackListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * A palette control for the {@link GraphicalEditorPart}.
 * <p/>
 * The palette contains several groups, each with a UI name (e.g. layouts and views) and each
 * with a list of element descriptors.
 * <p/>
 *
 * TODO list:
 *   - The available items should depend on the actual GLE2 Canvas selection. Selected android
 *     views should force filtering on what they accept can be dropped on them (e.g. TabHost,
 *     TableLayout). Should enable/disable them, not hide them, to avoid shuffling around.
 *   - Optional: a text filter
 *   - Optional: have context-sensitive tools items, e.g. selection arrow tool,
 *     group selection tool, alignment, etc.
 */
public class PaletteControl extends Composite {

    /**
     * Wrapper to create a {@link PaletteControl} into a {@link DecorComposite}.
     */
    public static class PaletteDecor implements IDecorContent {
        private final GraphicalEditorPart mEditorPart;
        private Control mControl;

        public PaletteDecor(GraphicalEditorPart editor) {
            mEditorPart = editor;
        }

        public String getTitle() {
            return "Palette";
        }

        public Image getImage() {
            return IconFactory.getInstance().getIcon("editor_palette");  //$NON-NLS-1$
        }

        public void createControl(Composite parent) {
            mControl = new PaletteControl(parent, mEditorPart);
        }

        public Control getControl() {
            return mControl;
        }
    }

    /**
     * The parent grid layout that contains all the {@link Toggle} and
     * {@link IconTextItem} widgets.
     */
    private GraphicalEditorPart mEditor;
    private Color mBackground;
    private Color mForeground;

    /** The palette modes control various ways to visualize and lay out the views */
    private static enum PaletteMode {
        /** Show rendered previews of the views */
        PREVIEW("Show Previews", true),
        /** Show rendered previews of the views, scaled down to 75% */
        SMALL_PREVIEW("Show Small Previews", true),
        /** Show rendered previews of the views, scaled down to 50% */
        TINY_PREVIEW("Show Tiny Previews", true),
        /** Show an icon + text label */
        ICON_TEXT("Show Icon and Text", false),
        /** Show only icons, packed multiple per row */
        ICON_ONLY("Show Only Icons", true);

        PaletteMode(String actionLabel, boolean wrap) {
            mActionLabel = actionLabel;
            mWrap = wrap;
        }

        public String getActionLabel() {
            return mActionLabel;
        }

        public boolean getWrap() {
            return mWrap;
        }

        public boolean isPreview() {
            return this == PREVIEW || this == SMALL_PREVIEW || this == TINY_PREVIEW;
        }

        public boolean isScaledPreview() {
            return this == SMALL_PREVIEW || this == TINY_PREVIEW;
        }

        private final String mActionLabel;
        private final boolean mWrap;
    };

    /** Token used in preference string to record alphabetical sorting */
    private static final String VALUE_ALPHABETICAL = "alpha";   //$NON-NLS-1$
    /** Token used in preference string to record categories being turned off */
    private static final String VALUE_NO_CATEGORIES = "nocat"; //$NON-NLS-1$
    /** Token used in preference string to record auto close being turned off */
    private static final String VALUE_NO_AUTOCLOSE = "noauto";      //$NON-NLS-1$

    private final PreviewIconFactory mPreviewIconFactory = new PreviewIconFactory(this);
    private PaletteMode mPaletteMode = PaletteMode.SMALL_PREVIEW;
    /** Use alphabetical sorting instead of natural order? */
    private boolean mAlphabetical;
    /** Use categories instead of a single large list of views? */
    private boolean mCategories = true;
    /** Auto-close the previous category when new categories are opened */
    private boolean mAutoClose = true;
    private AccordionControl mAccordion;
    private String mCurrentTheme;
    private String mCurrentDevice;
    private IAndroidTarget mCurrentTarget;

    /**
     * Create the composite.
     * @param parent The parent composite.
     * @param editor An editor associated with this palette.
     */
    public PaletteControl(Composite parent, GraphicalEditorPart editor) {
        super(parent, SWT.NONE);

        mEditor = editor;
        loadPaletteMode();
    }

    /** Reads UI mode from persistent store to preserve palette mode across IDE sessions */
    private void loadPaletteMode() {
        String paletteModes = AdtPrefs.getPrefs().getPaletteModes();
        if (paletteModes.length() > 0) {
            String[] tokens = paletteModes.split(","); //$NON-NLS-1$
            try {
                mPaletteMode = PaletteMode.valueOf(tokens[0]);
            } catch (Throwable t) {
                mPaletteMode = PaletteMode.values()[0];
            }
            mAlphabetical = paletteModes.contains(VALUE_ALPHABETICAL);
            mCategories = !paletteModes.contains(VALUE_NO_CATEGORIES);
            mAutoClose = !paletteModes.contains(VALUE_NO_AUTOCLOSE);
        }
    }

    /**
     * Returns the most recently stored version of auto-close-mode; this is the last
     * user-initiated setting of the auto-close mode (we programmatically switch modes when
     * you enter icons-only mode, and set it back to this when going to any other mode)
     */
    private boolean getSavedAutoCloseMode() {
        return !AdtPrefs.getPrefs().getPaletteModes().contains(VALUE_NO_AUTOCLOSE);
    }

    /** Saves UI mode to persistent store to preserve palette mode across IDE sessions */
    private void savePaletteMode() {
        StringBuilder sb = new StringBuilder();
        sb.append(mPaletteMode);
        if (mAlphabetical) {
            sb.append(',').append(VALUE_ALPHABETICAL);
        }
        if (!mCategories) {
            sb.append(',').append(VALUE_NO_CATEGORIES);
        }
        if (!mAutoClose) {
            sb.append(',').append(VALUE_NO_AUTOCLOSE);
        }
        AdtPrefs.getPrefs().setPaletteModes(sb.toString());
    }

    private void refreshPalette() {
        IAndroidTarget oldTarget = mCurrentTarget;
        mCurrentTarget = null;
        mCurrentTheme = null;
        mCurrentDevice = null;
        reloadPalette(oldTarget);
    }

    @Override
    protected void checkSubclass() {
        // Disable the check that prevents subclassing of SWT components
    }

    @Override
    public void dispose() {
        if (mBackground != null) {
            mBackground.dispose();
            mBackground = null;
        }
        if (mForeground != null) {
            mForeground.dispose();
            mForeground = null;
        }

        super.dispose();
    }

    /**
     * Returns the currently displayed target
     *
     * @return the current target, or null
     */
    public IAndroidTarget getCurrentTarget() {
        return mCurrentTarget;
    }

    /**
     * Returns the currently displayed theme (in palette modes that support previewing)
     *
     * @return the current theme, or null
     */
    public String getCurrentTheme() {
        return mCurrentTheme;
    }

    /**
     * Returns the currently displayed device (in palette modes that support previewing)
     *
     * @return the current device, or null
     */
    public String getCurrentDevice() {
        return mCurrentDevice;
    }

    /**
     * Loads or reloads the palette elements by using the layout and view descriptors from the
     * given target data.
     *
     * @param target The target that has just been loaded
     */
    public void reloadPalette(IAndroidTarget target) {
        ConfigurationComposite configuration = mEditor.getConfigurationComposite();
        String theme = configuration.getTheme();
        String device = configuration.getDevice();
        if (target == mCurrentTarget && mCurrentTheme != null && mCurrentTheme.equals(theme) &&
                mCurrentDevice != null && mCurrentDevice.equals(device)) {
            return;
        }
        mCurrentTheme = theme;
        mCurrentTarget = target;
        mCurrentDevice = device;
        mPreviewIconFactory.reset();

        // Erase old content and recreate new
        for (Control c : getChildren()) {
            c.dispose();
        }

        if (mPaletteMode.isPreview()) {
            if (mForeground != null) {
                mForeground.dispose();
                mForeground = null;
            }
            if (mBackground != null) {
                mBackground.dispose();
                mBackground = null;
            }
            RGB background = mPreviewIconFactory.getBackgroundColor();
            mBackground = new Color(getDisplay(), background);
            RGB foreground = mPreviewIconFactory.getForegroundColor();
            if (foreground != null) {
                mForeground = new Color(getDisplay(), foreground);
            }
        }

        AndroidTargetData targetData = Sdk.getCurrent().getTargetData(target);

        List<Object> headers = Collections.emptyList();
        final Map<String, List<ViewElementDescriptor>> categoryToItems;
        if (targetData != null) {
            categoryToItems = new HashMap<String, List<ViewElementDescriptor>>();
            headers = new ArrayList<Object>();
            List<Pair<String,List<ViewElementDescriptor>>> paletteEntries =
                ViewMetadataRepository.get().getPaletteEntries(targetData,
                        mAlphabetical, mCategories);
            for (Pair<String,List<ViewElementDescriptor>> pair : paletteEntries) {
                String category = pair.getFirst();
                List<ViewElementDescriptor> categoryItems = pair.getSecond();
                headers.add(category);
                categoryToItems.put(category, categoryItems);
            }
        } else {
            categoryToItems = null;
        }

        boolean wrap = mPaletteMode.getWrap();

        // Pack icon-only view vertically; others stretch to fill palette region
        boolean fillVertical = mPaletteMode != PaletteMode.ICON_ONLY;

        mAccordion = new AccordionControl(this, SWT.NONE, headers, fillVertical, wrap) {
            @Override
            protected Composite createChildContainer(Composite parent) {
                Composite composite = super.createChildContainer(parent);
                if (mPaletteMode.isPreview()) {
                    composite.setBackground(mBackground);
                }
                addMenu(composite);
                return composite;
            }
            @Override
            protected void createChildren(Composite parent, Object header) {
                assert categoryToItems != null;
                List<ViewElementDescriptor> list = categoryToItems.get(header);
                for (ViewElementDescriptor desc : list) {
                    createItem(parent, desc);
                }
            }
        };
        addMenu(mAccordion);
        for (CLabel headerLabel : mAccordion.getHeaderLabels()) {
            addMenu(headerLabel);
        }
        setLayout(new FillLayout());

        // Expand All for icon-only mode, but don't store it as the persistent auto-close mode;
        // when we enter other modes it will read back whatever persistent mode.
        if (mPaletteMode == PaletteMode.ICON_ONLY) {
            mAccordion.expandAll(true);
            mAccordion.setAutoClose(false);
        } else {
            mAccordion.setAutoClose(getSavedAutoCloseMode());
        }

        layout(true);
    }

    /* package */ GraphicalEditorPart getEditor() {
        return mEditor;
    }

    private Control createItem(Composite parent, ViewElementDescriptor desc) {
        Control item = null;
        switch (mPaletteMode) {
            case SMALL_PREVIEW:
            case TINY_PREVIEW:
            case PREVIEW: {
                ImageDescriptor descriptor = mPreviewIconFactory.getImageDescriptor(desc);
                if (descriptor != null) {
                    Image image = descriptor.createImage();
                    ImageControl imageControl = new ImageControl(parent, SWT.None, image);
                    if (mPaletteMode.isScaledPreview()) {
                        // Try to preserve the overall size since rendering sizes typically
                        // vary with the dpi - so while the scaling factor for a 160 dpi
                        // rendering the scaling factor should be 0.5, for a 320 dpi one the
                        // scaling factor should be half that, 0.25.
                        float scale = 1.0f;
                        if (mPaletteMode == PaletteMode.SMALL_PREVIEW) {
                            scale = 0.75f;
                        } else if (mPaletteMode == PaletteMode.TINY_PREVIEW) {
                            scale = 0.5f;
                        }
                        int dpi = mEditor.getConfigurationComposite().getDensity().getDpiValue();
                        while (dpi > 160) {
                            scale = scale / 2;
                            dpi = dpi / 2;
                        }
                        imageControl.setScale(scale);
                    }
                    imageControl.setHoverColor(getDisplay().getSystemColor(SWT.COLOR_WHITE));
                    imageControl.setBackground(mBackground);
                    String toolTip = desc.getUiName();
                    // It appears pretty much none of the descriptors have tooltips
                    //String descToolTip = desc.getTooltip();
                    //if (descToolTip != null && descToolTip.length() > 0) {
                    //    toolTip = toolTip + "\n" + descToolTip;
                    //}
                    imageControl.setToolTipText(toolTip);

                    item = imageControl;
                } else {
                    // Just use an Icon+Text item for these for now
                    item = new IconTextItem(parent, desc);
                    if (mForeground != null) {
                        item.setForeground(mForeground);
                        item.setBackground(mBackground);
                    }
                }
                break;
            }
            case ICON_TEXT: {
                item = new IconTextItem(parent, desc);
                break;
            }
            case ICON_ONLY: {
                item = new ImageControl(parent, SWT.None, desc.getIcon());
                item.setToolTipText(desc.getUiName());
                break;
            }
            default:
                throw new IllegalArgumentException("Not yet implemented");
        }

        final DragSource source = new DragSource(item, DND.DROP_COPY);
        source.setTransfer(new Transfer[] { SimpleXmlTransfer.getInstance() });
        source.addDragListener(new DescDragSourceListener(desc));
        item.addDisposeListener(new DisposeListener() {
            public void widgetDisposed(DisposeEvent e) {
                source.dispose();
            }
        });
        addMenu(item);

        return item;
    }

    /**
     * An Item widget represents one {@link ElementDescriptor} that can be dropped on the
     * GLE2 canvas using drag'n'drop.
     */
    private class IconTextItem extends CLabel implements MouseTrackListener {

        private boolean mMouseIn;

        public IconTextItem(Composite parent, ViewElementDescriptor desc) {
            super(parent, SWT.NONE);
            mMouseIn = false;

            setText(desc.getUiName());
            setImage(desc.getIcon());
            setToolTipText(desc.getTooltip());
            addMouseTrackListener(this);
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
    }

    /**
     * A {@link DragSourceListener} that deals with drag'n'drop of
     * {@link ElementDescriptor}s.
     */
    private class DescDragSourceListener implements DragSourceListener {
        private final ViewElementDescriptor mDesc;
        private SimpleElement[] mElements;

        public DescDragSourceListener(ViewElementDescriptor desc) {
            mDesc = desc;
        }

        public void dragStart(DragSourceEvent e) {
            // See if we can find out the bounds of this element from a preview image.
            // Preview images are created before the drag source listener is notified
            // of the started drag.
            Rect bounds = null;
            Rect dragBounds = null;

            createDragImage(e);
            if (mImage != null && !mIsPlaceholder) {
                ImageData data = mImage.getImageData();
                int width = data.width;
                int height = data.height;
                bounds = new Rect(0, 0, width, height);
                dragBounds = new Rect(-width / 2, -height / 2, width, height);
            }

            SimpleElement se = new SimpleElement(
                    SimpleXmlTransfer.getFqcn(mDesc),
                    null   /* parentFqcn */,
                    bounds /* bounds */,
                    null   /* parentBounds */);
            mElements = new SimpleElement[] { se };

            // Register this as the current dragged data
            GlobalCanvasDragInfo dragInfo = GlobalCanvasDragInfo.getInstance();
            dragInfo.startDrag(
                    mElements,
                    null /* selection */,
                    null /* canvas */,
                    null /* removeSource */);
            dragInfo.setDragBounds(dragBounds);

            e.doit = true;
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
            if (mImage != null) {
                mImage.dispose();
                mImage = null;
            }
        }

        // TODO: Figure out the right dimensions to use for rendering.
        // We WILL crop this after rendering, but for performance reasons it would be good
        // not to make it much larger than necessary since to crop this we rely on
        // actually scanning pixels.

        /**
         * Width of the rendered preview image (before it is cropped), although the actual
         * width may be smaller (since we also take the device screen's size into account)
         */
        private static final int MAX_RENDER_HEIGHT = 400;

        /**
         * Height of the rendered preview image (before it is cropped), although the
         * actual width may be smaller (since we also take the device screen's size into
         * account)
         */
        private static final int MAX_RENDER_WIDTH = 500;

        /** Amount of alpha to multiply into the image (divided by 256) */
        private static final int IMG_ALPHA = 216;

        /** The image shown by the drag source effect */
        private Image mImage;

        /**
         * If true, the image is a preview of the view, and if not it is a "fallback"
         * image of some sort, such as a rendering of the palette item itself
         */
        private boolean mIsPlaceholder;

        private void createDragImage(DragSourceEvent event) {
            mImage = renderPreview();

            mIsPlaceholder = mImage == null;
            if (mIsPlaceholder) {
                // Couldn't render preview (or the preview is a blank image, such as for
                // example the preview of an empty layout), so instead create a placeholder
                // image
                // Render the palette item itself as an image
                Control control = ((DragSource) event.widget).getControl();
                GC gc = new GC(control);
                Point size = control.getSize();
                Display display = getDisplay();
                final Image image = new Image(display, size.x, size.y);
                gc.copyArea(image, 0, 0);
                gc.dispose();

                BufferedImage awtImage = SwtUtils.convertToAwt(image);
                if (awtImage != null) {
                    awtImage = ImageUtils.createDropShadow(awtImage, 3 /* shadowSize */,
                            0.7f /* shadowAlpha */, 0x000000 /* shadowRgb */);
                    mImage = SwtUtils.convertToSwt(display, awtImage, true, IMG_ALPHA);
                } else {
                    ImageData data = image.getImageData();
                    data.alpha = IMG_ALPHA;

                    // Changing the ImageData -after- constructing an image on it
                    // has no effect, so we have to construct a new image. Luckily these
                    // are tiny images.
                    mImage = new Image(display, data);
                }
                image.dispose();
            }

            event.image = mImage;

            if (!mIsPlaceholder) {
                // Shift the drag feedback image up such that it's centered under the
                // mouse pointer

                Rectangle imageBounds = mImage.getBounds();
                event.offsetX = imageBounds.width / 2;
                event.offsetY = imageBounds.height / 2;

            }
        }

        /** Performs the actual rendering of the descriptor into an image */
        private Image renderPreview() {
            // Create blank XML document
            Document document = null;
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            try {
                factory.setNamespaceAware(true);
                factory.setValidating(false);
                DocumentBuilder builder = factory.newDocumentBuilder();
                document = builder.newDocument();
            } catch (ParserConfigurationException e) {
                return null;
            }

            // Insert our target view's XML into it as a node
            GraphicalEditorPart editor = getEditor();
            LayoutEditor layoutEditor = editor.getLayoutEditor();

            String viewName = mDesc.getXmlLocalName();
            Element element = document.createElement(viewName);

            // Set up a proper name space
            Attr attr = document.createAttributeNS(XmlnsAttributeDescriptor.XMLNS_URI,
                    "xmlns:android"); //$NON-NLS-1$
            attr.setValue(ANDROID_URI);
            element.getAttributes().setNamedItemNS(attr);

            element.setAttributeNS(SdkConstants.NS_RESOURCES,
                    ATTR_LAYOUT_WIDTH, VALUE_WRAP_CONTENT);
            element.setAttributeNS(SdkConstants.NS_RESOURCES,
                    ATTR_LAYOUT_HEIGHT, VALUE_WRAP_CONTENT);

            // This doesn't apply to all, but doesn't seem to cause harm and makes for a
            // better experience with text-oriented views like buttons and texts
            element.setAttributeNS(SdkConstants.NS_RESOURCES, ATTR_TEXT, mDesc.getUiName());

            document.appendChild(element);

            // Construct UI model from XML
            AndroidTargetData data = layoutEditor.getTargetData();
            DocumentDescriptor documentDescriptor;
            if (data == null) {
                documentDescriptor = new DocumentDescriptor("temp", null/*children*/);//$NON-NLS-1$
            } else {
                documentDescriptor = data.getLayoutDescriptors().getDescriptor();
            }
            UiDocumentNode model = (UiDocumentNode) documentDescriptor.createUiNode();
            model.setEditor(layoutEditor);
            model.setUnknownDescriptorProvider(editor.getModel().getUnknownDescriptorProvider());
            model.loadFromXmlNode(document);

            // Call the create-hooks such that we for example insert mandatory
            // children into views like the DialerFilter, apply image source attributes
            // to ImageButtons, etc.
            LayoutCanvas canvas = editor.getCanvasControl();
            NodeFactory nodeFactory = canvas.getNodeFactory();
            UiElementNode parent = model.getUiRoot();
            UiElementNode child = parent.getUiChildren().get(0);
            if (child instanceof UiViewElementNode) {
                UiViewElementNode childUiNode = (UiViewElementNode) child;
                NodeProxy childNode = nodeFactory.create(childUiNode);
                canvas.getRulesEngine().callCreateHooks(layoutEditor,
                        null, childNode, InsertType.CREATE);
            }

            boolean hasTransparency = false;
            LayoutLibrary layoutLibrary = editor.getLayoutLibrary();
            if (layoutLibrary != null) {
                hasTransparency = layoutLibrary.supports(Capability.TRANSPARENCY);
            }

            RenderSession session = null;
            try {
                // Use at most the size of the screen for the preview render.
                // This is important since when we fill the size of certain views (like
                // a SeekBar), we want it to at most be the width of the screen, and for small
                // screens the RENDER_WIDTH was wider.
                org.eclipse.draw2d.geometry.Rectangle screenBounds = editor.getScreenBounds();
                int renderWidth = Math.min(screenBounds.width, MAX_RENDER_WIDTH);
                int renderHeight = Math.min(screenBounds.height, MAX_RENDER_HEIGHT);
                LayoutLog silentLogger = new LayoutLog();
                session = editor.render(model, renderWidth, renderHeight,
                    null /* explodeNodes */, hasTransparency, silentLogger, RenderingMode.NORMAL);
            } catch (Throwable t) {
                // Previews can fail for a variety of reasons -- let's not bug
                // the user with it
                return null;
            }

            if (session != null) {
                if (session.getResult().isSuccess()) {
                    BufferedImage image = session.getImage();
                    if (image != null) {
                        BufferedImage cropped;
                        Rect initialCrop = null;
                        ViewInfo viewInfo = null;

                        List<ViewInfo> viewInfoList = session.getRootViews();

                        if (viewInfoList != null && viewInfoList.size() > 0) {
                            viewInfo = viewInfoList.get(0);
                        }

                        if (viewInfo != null) {
                            int x1 = viewInfo.getLeft();
                            int x2 = viewInfo.getRight();
                            int y2 = viewInfo.getBottom();
                            int y1 = viewInfo.getTop();
                            initialCrop = new Rect(x1, y1, x2 - x1, y2 - y1);
                        }

                        if (hasTransparency) {
                            cropped = ImageUtils.cropBlank(image, initialCrop);
                        } else {
                            // Find out what the "background" color is such that we can properly
                            // crop it out of the image. To do this we pick out a pixel in the
                            // bottom right unpainted area. Rather than pick the one in the far
                            // bottom corner, we pick one as close to the bounds of the view as
                            // possible (but still outside of the bounds), such that we can
                            // deal with themes like the dialog theme.
                            int edgeX = image.getWidth() -1;
                            int edgeY = image.getHeight() -1;
                            if (viewInfo != null) {
                                if (viewInfo.getRight() < image.getWidth()-1) {
                                    edgeX = viewInfo.getRight()+1;
                                }
                                if (viewInfo.getBottom() < image.getHeight()-1) {
                                    edgeY = viewInfo.getBottom()+1;
                                }
                            }
                            int edgeColor = image.getRGB(edgeX, edgeY);
                            cropped = ImageUtils.cropColor(image, edgeColor, initialCrop);
                        }

                        if (cropped != null) {
                            boolean needsContrast = hasTransparency
                                    && !ImageUtils.containsDarkPixels(cropped);
                            cropped = ImageUtils.createDropShadow(cropped,
                                    hasTransparency ? 3 : 5 /* shadowSize */,
                                    !hasTransparency ? 0.6f : needsContrast ? 0.8f : 0.7f/*alpha*/,
                                    0x000000 /* shadowRgb */);

                            Display display = getDisplay();
                            int alpha = (!hasTransparency || !needsContrast) ? IMG_ALPHA : -1;
                            Image swtImage = SwtUtils.convertToSwt(display, cropped, true, alpha);
                            return swtImage;
                        }
                    }
                }

                session.dispose();
            }

            return null;
        }

        /**
         * Utility method to print out the contents of the given XML document. This is
         * really useful when working on the preview code above. I'm including all the
         * code inside a constant false, which means the compiler will omit all the code,
         * but I'd like to leave it in the code base and by doing it this way rather than
         * as commented out code the code won't be accidentally broken.
         */
        @SuppressWarnings("all")
        private void dumpDocument(Document document) {
            // Diagnostics: print out the XML that we're about to render
            if (false) { // Will be omitted by the compiler
                org.apache.xml.serialize.OutputFormat outputFormat =
                    new org.apache.xml.serialize.OutputFormat(
                            "XML", "ISO-8859-1", true); //$NON-NLS-1$ //$NON-NLS-2$
                outputFormat.setIndent(2);
                outputFormat.setLineWidth(100);
                outputFormat.setIndenting(true);
                outputFormat.setOmitXMLDeclaration(true);
                outputFormat.setOmitDocumentType(true);
                StringWriter stringWriter = new StringWriter();
                // Using FQN here to avoid having an import above, which will result
                // in a deprecation warning, and there isn't a way to annotate a single
                // import element with a SuppressWarnings.
                org.apache.xml.serialize.XMLSerializer serializer =
                    new org.apache.xml.serialize.XMLSerializer(stringWriter, outputFormat);
                serializer.setNamespaces(true);
                try {
                    serializer.serialize(document.getDocumentElement());
                    System.out.println(stringWriter.toString());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /** Action for switching view modes via radio buttons */
    private class PaletteModeAction extends Action {
        private final PaletteMode mMode;

        PaletteModeAction(PaletteMode mode) {
            super(mode.getActionLabel(), IAction.AS_RADIO_BUTTON);
            mMode = mode;
            boolean selected = mMode == mPaletteMode;
            setChecked(selected);
            setEnabled(!selected);
        }

        @Override
        public void run() {
            if (isEnabled()) {
                mPaletteMode = mMode;
                refreshPalette();
                savePaletteMode();
            }
        }
    }

    /** Action for toggling various checkbox view modes - categories, sorting, etc */
    private class ToggleViewOptionAction extends Action {
        private final int mAction;
        final static int TOGGLE_CATEGORY = 1;
        final static int TOGGLE_ALPHABETICAL = 2;
        final static int TOGGLE_AUTO_CLOSE = 3;

        ToggleViewOptionAction(String title, int action, boolean checked) {
            super(title, IAction.AS_CHECK_BOX);
            mAction = action;
            setChecked(checked);
        }

        @Override
        public void run() {
            switch (mAction) {
                case TOGGLE_CATEGORY:
                    mCategories = !mCategories;
                    refreshPalette();
                    break;
                case TOGGLE_ALPHABETICAL:
                    mAlphabetical = !mAlphabetical;
                    refreshPalette();
                    break;
                case TOGGLE_AUTO_CLOSE:
                    mAutoClose = !mAutoClose;
                    mAccordion.setAutoClose(mAutoClose);
                    break;
            }
            savePaletteMode();
        }
    }

    private void addMenu(Control control) {
        control.addMenuDetectListener(new MenuDetectListener() {
            public void menuDetected(MenuDetectEvent e) {
                MenuManager manager = new MenuManager() {
                    @Override
                    public boolean isDynamic() {
                        return true;
                    }
                };
                for (PaletteMode mode : PaletteMode.values()) {
                        manager.add(new PaletteModeAction(mode));
                }
                manager.add(new Separator());
                manager.add(new ToggleViewOptionAction("Show Categories",
                        ToggleViewOptionAction.TOGGLE_CATEGORY,
                        mCategories));
                manager.add(new ToggleViewOptionAction("Sort Alphabetically",
                        ToggleViewOptionAction.TOGGLE_ALPHABETICAL,
                        mAlphabetical));
                manager.add(new Separator());
                manager.add(new ToggleViewOptionAction("Auto Close Previous",
                        ToggleViewOptionAction.TOGGLE_AUTO_CLOSE,
                        mAutoClose));
                Menu menu = manager.createContextMenu(PaletteControl.this);
                Point point = new Point(e.x, e.y);
                menu.setLocation(point.x, point.y);
                menu.setVisible(true);
            }
        });
    }
}
