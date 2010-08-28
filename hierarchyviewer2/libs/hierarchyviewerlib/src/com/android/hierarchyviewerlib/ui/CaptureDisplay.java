/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.hierarchyviewerlib.ui;

import com.android.ddmuilib.ImageLoader;
import com.android.hierarchyviewerlib.HierarchyViewerDirector;
import com.android.hierarchyviewerlib.device.ViewNode;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.events.ShellAdapter;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

public class CaptureDisplay {
    private static Shell sShell;

    private static Canvas sCanvas;

    private static Image sImage;

    private static ViewNode sViewNode;

    private static Composite sButtonBar;

    private static Button sOnWhite;

    private static Button sOnBlack;

    private static Button sShowExtras;

    public static void show(Shell parentShell, ViewNode viewNode, Image image) {
        if (sShell == null) {
            createShell();
        }
        if (sShell.isVisible() && CaptureDisplay.sViewNode != null) {
            CaptureDisplay.sViewNode.dereferenceImage();
        }
        CaptureDisplay.sImage = image;
        CaptureDisplay.sViewNode = viewNode;
        viewNode.referenceImage();
        sShell.setText(viewNode.name);

        boolean shellVisible = sShell.isVisible();
        if (!shellVisible) {
            sShell.setSize(0, 0);
        }
        Rectangle bounds =
                sShell.computeTrim(0, 0, Math.max(sButtonBar.getBounds().width,
                        image.getBounds().width), sButtonBar.getBounds().height
                        + image.getBounds().height + 5);
        sShell.setSize(bounds.width, bounds.height);
        if (!shellVisible) {
            sShell.setLocation(parentShell.getBounds().x
                    + (parentShell.getBounds().width - bounds.width) / 2, parentShell.getBounds().y
                    + (parentShell.getBounds().height - bounds.height) / 2);
        }
        sShell.open();
        if (shellVisible) {
            sCanvas.redraw();
        }
    }

    private static void createShell() {
        sShell = new Shell(Display.getDefault(), SWT.CLOSE | SWT.TITLE);
        GridLayout gridLayout = new GridLayout();
        gridLayout.marginWidth = 0;
        gridLayout.marginHeight = 0;
        sShell.setLayout(gridLayout);

        sButtonBar = new Composite(sShell, SWT.NONE);
        RowLayout rowLayout = new RowLayout(SWT.HORIZONTAL);
        rowLayout.pack = true;
        rowLayout.center = true;
        sButtonBar.setLayout(rowLayout);
        Composite buttons = new Composite(sButtonBar, SWT.NONE);
        buttons.setLayout(new FillLayout());

        sOnWhite = new Button(buttons, SWT.TOGGLE);
        sOnWhite.setText("On White");
        sOnBlack = new Button(buttons, SWT.TOGGLE);
        sOnBlack.setText("On Black");
        sOnBlack.setSelection(true);
        sOnWhite.addSelectionListener(sWhiteSelectionListener);
        sOnBlack.addSelectionListener(sBlackSelectionListener);

        sShowExtras = new Button(sButtonBar, SWT.CHECK);
        sShowExtras.setText("Show Extras");
        sShowExtras.addSelectionListener(sExtrasSelectionListener);

        sCanvas = new Canvas(sShell, SWT.NONE);
        sCanvas.setLayoutData(new GridData(GridData.FILL_BOTH));
        sCanvas.addPaintListener(sPaintListener);

        sShell.addShellListener(sShellListener);

        ImageLoader imageLoader = ImageLoader.getLoader(HierarchyViewerDirector.class);
        Image image = imageLoader.loadImage("display.png", Display.getDefault()); //$NON-NLS-1$
        sShell.setImage(image);
    }

    private static PaintListener sPaintListener = new PaintListener() {

        public void paintControl(PaintEvent e) {
            if (sOnWhite.getSelection()) {
                e.gc.setBackground(Display.getDefault().getSystemColor(SWT.COLOR_WHITE));
            } else {
                e.gc.setBackground(Display.getDefault().getSystemColor(SWT.COLOR_BLACK));
            }
            e.gc.fillRectangle(0, 0, sCanvas.getBounds().width, sCanvas.getBounds().height);
            if (sImage != null) {
                int width = sImage.getBounds().width;
                int height = sImage.getBounds().height;
                int x = (sCanvas.getBounds().width - width) / 2;
                int y = (sCanvas.getBounds().height - height) / 2;
                e.gc.drawImage(sImage, x, y);
                if (sShowExtras.getSelection()) {
                    if ((sViewNode.paddingLeft | sViewNode.paddingRight | sViewNode.paddingTop | sViewNode.paddingBottom) != 0) {
                        e.gc.setForeground(Display.getDefault().getSystemColor(SWT.COLOR_BLUE));
                        e.gc.drawRectangle(x + sViewNode.paddingLeft, y + sViewNode.paddingTop, width
                                - sViewNode.paddingLeft - sViewNode.paddingRight - 1, height
                                - sViewNode.paddingTop - sViewNode.paddingBottom - 1);
                    }
                    if (sViewNode.hasMargins) {
                        e.gc.setForeground(Display.getDefault().getSystemColor(SWT.COLOR_GREEN));
                        e.gc.drawRectangle(x - sViewNode.marginLeft, y - sViewNode.marginTop, width
                                + sViewNode.marginLeft + sViewNode.marginRight - 1, height
                                + sViewNode.marginTop + sViewNode.marginBottom - 1);
                    }
                    if (sViewNode.baseline != -1) {
                        e.gc.setForeground(Display.getDefault().getSystemColor(SWT.COLOR_RED));
                        e.gc.drawLine(x, y + sViewNode.baseline, x + width - 1, sViewNode.baseline);
                    }
                }
            }
        }
    };

    private static ShellAdapter sShellListener = new ShellAdapter() {
        @Override
        public void shellClosed(ShellEvent e) {
            e.doit = false;
            sShell.setVisible(false);
            if (sViewNode != null) {
                sViewNode.dereferenceImage();
            }
        }

    };

    private static SelectionListener sWhiteSelectionListener = new SelectionListener() {
        public void widgetDefaultSelected(SelectionEvent e) {
            // pass
        }

        public void widgetSelected(SelectionEvent e) {
            sOnWhite.setSelection(true);
            sOnBlack.setSelection(false);
            sCanvas.redraw();
        }
    };

    private static SelectionListener sBlackSelectionListener = new SelectionListener() {
        public void widgetDefaultSelected(SelectionEvent e) {
            // pass
        }

        public void widgetSelected(SelectionEvent e) {
            sOnBlack.setSelection(true);
            sOnWhite.setSelection(false);
            sCanvas.redraw();
        }
    };

    private static SelectionListener sExtrasSelectionListener = new SelectionListener() {
        public void widgetDefaultSelected(SelectionEvent e) {
            // pass
        }

        public void widgetSelected(SelectionEvent e) {
            sCanvas.redraw();
        }
    };
}
