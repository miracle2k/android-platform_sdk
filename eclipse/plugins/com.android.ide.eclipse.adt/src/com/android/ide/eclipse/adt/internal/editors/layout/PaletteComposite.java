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

import com.android.ide.eclipse.adt.internal.editors.descriptors.ElementDescriptor;
import com.android.ide.eclipse.adt.internal.sdk.AndroidTargetData;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.dnd.ByteArrayTransfer;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DragSource;
import org.eclipse.swt.dnd.DragSourceEvent;
import org.eclipse.swt.dnd.DragSourceListener;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.dnd.TransferData;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseTrackListener;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.ScrollBar;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

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
 * - *Mandatory* for a first release:
 *   - Currently this displays elements as buttons. Eventually this needs to either be replaced
 *     by custom drawing right in here or we need to use a custom control.
 *   - Needs to be able to originate drag'n'drop from these controls onto the GEP.
 *   - Scroll the list.
 * - For later releases:
 *   - Ability to collapse palettes or dockable palettes.
 *   - Different view strategies: big icon, small icons, text vs no text, compact grid.
 *     - This would only be useful with meaningful icons. Out current 1-letter icons are not enough
 *       to get rid of text labels.
 *   - Would be nice to have context-sensitive tools items, e.g. selection arrow tool,
 *     group selection tool, alignment, etc.
 */
public class PaletteComposite extends Composite {

    /**
     * Create the composite.
     * @param parent The parent composite.
     */
    public PaletteComposite(Composite parent) {
        super(parent, SWT.BORDER | SWT.V_SCROLL);
    }

    @Override
    protected void checkSubclass() {
        // Disable the check that prevents subclassing of SWT components
    }

    /**
     * Load or reload the palette elements by using the layour and view descriptors from the
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

        final Composite parent = new Composite(this, SWT.NONE);
        setGridLayout(parent, 0);

        if (targetData != null) {
            /* TODO: All this is TEMPORARY. */
            Label l = new Label(this, SWT.NONE);
            l.setText("*** PLACEHOLDER ***");  //$NON-NLS-1$
            l.setToolTipText("Temporary mock for the palette. Needs to scroll, needs no buttons, needs to drag'n'drop."); //$NON-NLS-1$

            addGroup(parent, "Layouts", targetData.getLayoutDescriptors().getLayoutDescriptors());
            addGroup(parent, "Views", targetData.getLayoutDescriptors().getViewDescriptors());
        }

        layout(true);

        final ScrollBar vbar = getVerticalBar();
        vbar.addListener(SWT.Selection, new Listener() {
            public void handleEvent(Event event) {
                Point p = parent.getLocation();
                p.y = - vbar.getSelection();
                parent.setLocation(p);
            }
        });
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
        //label.setText(String.format("-= %s =-", uiName));
        //label.setToolTipText();

        for (ElementDescriptor desc : descriptors) {
            toggle.addItem(new Item(group, desc));
        }
    }

    private static class Toggle extends CLabel implements MouseTrackListener, Listener {
        private boolean mMouseIn;
        private DragSource mSource;
        private ArrayList<Item> mItems = new ArrayList<Item>();

        public Toggle(Composite parent, String groupName) {
            super(parent, SWT.NONE);
            mMouseIn = false;

            String s = String.format("-= %s =-", groupName);
            setText(s);
            setToolTipText(s);
            //TODO use triangle icon and swap it -- setImage(desc.getIcon());
            addMouseTrackListener(this);
            addListener(SWT.Selection, this);
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

        /** Selection event */
        public void handleEvent(Event event) {
            for (Item i : mItems) {
                i.setVisible(!i.isVisible());
                i.setEnabled(!i.isEnabled());
            }
        }
    }

    private static class Item extends CLabel implements MouseTrackListener {

        private boolean mMouseIn;
        private DragSource mSource;

        public Item(Composite parent, ElementDescriptor desc) {
            super(parent, SWT.NONE);
            mMouseIn = false;

            setText(desc.getUiName());
            setImage(desc.getIcon());
            setToolTipText(desc.getTooltip());
            addMouseTrackListener(this);

            // DND Reference: http://www.eclipse.org/articles/Article-SWT-DND/DND-in-SWT.html
            mSource = new DragSource(this, DND.DROP_COPY);
            mSource.setTransfer(new Transfer[] { ElementDescTransfer.getInstance() });
            mSource.addDragListener(new DescDragSourceListener(desc));
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
    }

    /**
     * A {@link DragSourceListener} that deals with drag'n'drop of
     * {@link ElementDescriptor}s.
     */
    private static class DescDragSourceListener implements DragSourceListener {

        private final ElementDescriptor mDesc;

        public DescDragSourceListener(ElementDescriptor desc) {
            mDesc = desc;
        }

        public void dragStart(DragSourceEvent e) {
            if (mDesc == null) {
                e.doit = false;
            }
        }


        public void dragSetData(DragSourceEvent e) {
            // Provide the data for the drop when requested by the other side.
            if (ElementDescTransfer.getInstance().isSupportedType(e.dataType)) {
                e.data = mDesc;
            }
        }

        public void dragFinished(DragSourceEvent e) {
            // Nothing to do here.
        }
    }

    // TODO move out of this scope once we need it on the other side.
    /**
     * A d'n'd {@link Transfer} class that can transfer {@link ElementDescriptor}s.
     * <p/>
     * The implementation is based on the {@link ByteArrayTransfer} and what we transfer
     * is actually only the inner XML name of the element, which is unique enough.
     * <p/>
     * Drag source provides an {@link ElementDescriptor} object.
     * Drog receivers get back a {@link String} object representing the
     * {@link ElementDescriptor#getXmlName()}.
     * <p/>
     * Drop receivers can find the corresponding element by using
     * {@link ElementDescriptor#findChildrenDescriptor(String, boolean)} with the
     * XML name returned by this transfer operation and their root descriptor.
     * <p/>
     * Drop receivers must deal with the fact that this XML name may not exist in their
     * own {@link ElementDescriptor} hierarchy -- e.g. if the drag came from a different
     * GLE based on a different SDK platform or using custom widgets. In this case they
     * must refuse the drop.
     */
    public static class ElementDescTransfer extends ByteArrayTransfer {

        // Reference: http://www.eclipse.org/articles/Article-SWT-DND/DND-in-SWT.html


        private static final String TYPE_NAME = "android.ADT.element.desc.transfer.1";
        private static final int TYPE_ID = registerType(TYPE_NAME);
        private static ElementDescTransfer sInstance = new ElementDescTransfer();

        private ElementDescTransfer() {
            // pass
        }

        public static ElementDescTransfer getInstance() {
            return sInstance;
        }

        @Override
        protected int[] getTypeIds() {
            return new int[] { TYPE_ID };
        }

        @Override
        protected String[] getTypeNames() {
            return new String[] { TYPE_NAME };
        }

        @Override
        protected void javaToNative(Object object, TransferData transferData) {
            if (object == null || !(object instanceof ElementDescriptor[])) {
                return;
            }

            if (isSupportedType(transferData)) {
                StringBuilder sb = new StringBuilder();
                boolean needSeparator = false;
                for (ElementDescriptor desc : (ElementDescriptor[]) object) {
                    if (needSeparator) {
                        sb.append(';');
                    }
                    sb.append(desc.getXmlName());
                    needSeparator = true;
                }
                try {
                    byte[] buf = sb.toString().getBytes("UTF-8");  //$NON-NLS-1$
                    super.javaToNative(buf, transferData);
                } catch (UnsupportedEncodingException e) {
                    // unlikely; ignore
                }
            }
        }

        @Override
        protected Object nativeToJava(TransferData transferData) {
            if (isSupportedType(transferData)) {
                byte[] buf = (byte[]) super.nativeToJava(transferData);
                if (buf != null && buf.length > 0) {
                    try {
                        String s = new String(buf, "UTF-8"); //$NON-NLS-1$
                        String[] names = s.split(";");  //$NON-NLS-1$
                        return names;
                    } catch (UnsupportedEncodingException e) {
                        // unlikely to happen, but still possible
                    }
                }
            }

            return null;
        }
    }
}
