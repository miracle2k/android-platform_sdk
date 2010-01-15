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

import com.android.ide.eclipse.adt.internal.editors.descriptors.ElementDescriptor;
import com.android.ide.eclipse.adt.internal.sdk.AndroidTargetData;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DragSource;
import org.eclipse.swt.dnd.DragSourceEvent;
import org.eclipse.swt.dnd.DragSourceListener;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.MouseTrackListener;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.ScrollBar;

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

        final ScrollBar vbar = getVerticalBar();

        vbar.setMaximum(getSize().y);

        for (Listener listener : vbar.getListeners(SWT.Selection)) {
            vbar.removeListener(SWT.Selection, listener);
        }

        vbar.addListener(SWT.Selection, new Listener() {
            public void handleEvent(Event event) {
                Point p = mRoot.getLocation();
                p.y = - vbar.getSelection();
                mRoot.setLocation(p);
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

        for (ElementDescriptor desc : descriptors) {
            Item item = new Item(group, desc);
            toggle.addItem(item);
            GridData gd = new GridData();
            item.setLayoutData(gd);
        }
    }

    /**
     * A Toggle widget is a row that is the head of a group.
     * <p/>
     * When clicked, the toggle will show/hide all the {@link Item} widgets that have been
     * added to it using {@link #addItem(Item)}.
     */
    private static class Toggle extends CLabel implements MouseTrackListener, MouseListener {
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

            getParent().getParent().layout(true /*changed*/);
        }
    }

    /**
     * An Item widget represents one {@link ElementDescriptor} that can be dropped on the
     * GLE2 canvas using drag'n'drop.
     */
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

}
