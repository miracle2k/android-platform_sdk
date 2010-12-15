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

import org.eclipse.jface.action.IStatusLineManager;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.graphics.Region;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.views.properties.IPropertySheetEntry;
import org.eclipse.ui.views.properties.PropertySheetEntry;

/**
 * A customized property sheet page for the graphical layout editor v2.
 * <p/>
 * Currently it just provides a custom tooltip to display attributes javadocs.
 * <p/>
 * The property sheet is linked to the current site's selection service.
 * <p/>
 * Note: this is an exact copy of GLE1's UiPropertySheetPage implementation.
 * The idea is that eventually GLE1 will go away and we'll upgrade this to be
 * a more robust property editor (it currently lacks on so many levels, it's not
 * even worth listing the flaws.)
 *
 * @since GLE2
 */
public class PropertySheetPage extends org.eclipse.ui.views.properties.PropertySheetPage {

    public PropertySheetPage() {
        super();
    }

    @Override
    public void createControl(Composite parent) {
        super.createControl(parent);

        setupTooltip();

        // Override parent class' "set status message" behavior. The parent will set
        // the status message to the property's "getDescription()" field. That field
        // may contain newlines, which means the text gets cut off. We want to instead
        // show ALL the text, fit on a single line, and since we don't get to subclass
        // the viewer we will just replace the status message with our own, which works
        // since our mouse listener is registered later so runs later.
        final Tree tree = (Tree) getControl();
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseDown(MouseEvent event) {
                Point pt = new Point(event.x, event.y);
                TreeItem item = tree.getItem(pt);
                if (item != null) {
                    Object object = item.getData();
                    if (object instanceof IPropertySheetEntry) {
                        IPropertySheetEntry entry = (IPropertySheetEntry) object;
                        String help = entry.getDescription();
                        if (help != null) {
                            // Strip out newlines to make this a single line entry
                            help = help.replace('\n', ' ');
                            // Remove repeated spaces in case there were trailing spaces
                            help = help.replaceAll("  ", " "); //NON-NLS-1$ //NON-NLS-2$
                            IActionBars actionBars = getSite().getActionBars();
                            IStatusLineManager status = actionBars.getStatusLineManager();
                            status.setMessage(help);
                        }
                    }
                }
            }
        });

        // Fix the selection background. In Eclipse 3.5 and 3.6, the selection color
        // is white, painted on top of a white or light blue background (table striping),
        // which is practically unreadable.  This is fixed in 3.7M3, but we need a workaround
        // for earlier releases. The following code performs custom painting of this region,
        // and is based on the snippet "draw a custom gradient selection for tree" (snippet 226)
        // from http://www.eclipse.org/swt/snippets/ .
        tree.addListener(SWT.EraseItem, new Listener() {
            public void handleEvent(Event event) {
                if ((event.detail & SWT.SELECTED) != 0) {
                    GC gc = event.gc;
                    Rectangle area = tree.getClientArea();
                    int columnCount = tree.getColumnCount();
                    if (event.index == columnCount - 1 || columnCount == 0) {
                        int width = area.x + area.width - event.x;
                        if (width > 0) {
                            Region region = new Region();
                            gc.getClipping(region);
                            region.add(event.x, event.y, width, event.height);
                            gc.setClipping(region);
                            region.dispose();
                        }
                    }
                    gc.setAdvanced(true);
                    if (gc.getAdvanced()) {
                        gc.setAlpha(127);
                    }
                    Rectangle rect = event.getBounds();
                    Color foreground = gc.getForeground();
                    Color background = gc.getBackground();
                    Display display = tree.getDisplay();
                    gc.setForeground(display.getSystemColor(SWT.COLOR_LIST_SELECTION));
                    gc.setBackground(display.getSystemColor(SWT.COLOR_LIST_BACKGROUND));
                    gc.fillGradientRectangle(0, rect.y, 500, rect.height, false);
                    gc.setForeground(foreground);
                    gc.setBackground(background);
                    event.detail &= ~SWT.SELECTED;
                }
            }
        });
    }

    /**
     * Sets up a custom tooltip when hovering over tree items.
     * <p/>
     * The tooltip will display the element's javadoc, if any, or the item's getText otherwise.
     */
    private void setupTooltip() {
        final Tree tree = (Tree) getControl();

        /*
         * Reference:
         * http://dev.eclipse.org/viewcvs/index.cgi/org.eclipse.swt.snippets/src/org/eclipse/swt/snippets/Snippet125.java?view=markup
         */

        final Listener listener = new Listener() {
            Shell tip = null;
            Label label  = null;

            public void handleEvent(Event event) {
                switch(event.type) {
                case SWT.Dispose:
                case SWT.KeyDown:
                case SWT.MouseExit:
                case SWT.MouseDown:
                case SWT.MouseMove:
                    if (tip != null) {
                        tip.dispose();
                        tip = null;
                        label = null;
                    }
                    break;
                case SWT.MouseHover:
                    if (tip != null) {
                        tip.dispose();
                        tip = null;
                        label = null;
                    }

                    String tooltip = null;

                    TreeItem item = tree.getItem(new Point(event.x, event.y));
                    if (item != null) {
                        Object data = item.getData();
                        if (data instanceof PropertySheetEntry) {
                            tooltip = ((PropertySheetEntry) data).getDescription();
                        }

                        if (tooltip == null) {
                            tooltip = item.getText();
                        } else {
                            tooltip = item.getText() + ":\r" + tooltip;
                        }

                        if (tooltip != null) {
                            Shell shell = tree.getShell();
                            Display display = tree.getDisplay();

                            tip = new Shell(shell, SWT.ON_TOP | SWT.NO_FOCUS | SWT.TOOL);
                            tip.setBackground(display .getSystemColor(SWT.COLOR_INFO_BACKGROUND));
                            FillLayout layout = new FillLayout();
                            layout.marginWidth = 2;
                            tip.setLayout(layout);
                            label = new Label(tip, SWT.NONE);
                            label.setForeground(display.getSystemColor(SWT.COLOR_INFO_FOREGROUND));
                            label.setBackground(display.getSystemColor(SWT.COLOR_INFO_BACKGROUND));
                            label.setData("_TABLEITEM", item);
                            label.setText(tooltip);
                            label.addListener(SWT.MouseExit, this);
                            label.addListener(SWT.MouseDown, this);
                            Point size = tip.computeSize(SWT.DEFAULT, SWT.DEFAULT);
                            Rectangle rect = item.getBounds(0);
                            // Display the tooltip on the same line as the property,
                            // but offset to the right of wherever the mouse cursor was,
                            // such that it does not obscure the list of properties.
                            Point pt = tree.toDisplay(event.x + 15, rect.y);
                            tip.setBounds(pt.x, pt.y, size.x, size.y);
                            tip.setVisible(true);
                        }
                    }
                }
            }
        };

        tree.addListener(SWT.Dispose, listener);
        tree.addListener(SWT.KeyDown, listener);
        tree.addListener(SWT.MouseMove, listener);
        tree.addListener(SWT.MouseHover, listener);

    }

}
