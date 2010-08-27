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
    private static Shell shell;

    private static Canvas canvas;

    private static Image image;

    private static ViewNode viewNode;

    private static Composite buttonBar;

    private static Button onWhite;

    private static Button onBlack;

    private static Button showExtras;

    public static void show(Shell parentShell, ViewNode viewNode, Image image) {
        if (shell == null) {
            createShell();
        }
        if (shell.isVisible() && CaptureDisplay.viewNode != null) {
            CaptureDisplay.viewNode.dereferenceImage();
        }
        CaptureDisplay.image = image;
        CaptureDisplay.viewNode = viewNode;
        viewNode.referenceImage();
        shell.setText(viewNode.name);

        boolean shellVisible = shell.isVisible();
        if (!shellVisible) {
            shell.setSize(0, 0);
        }
        Rectangle bounds =
                shell.computeTrim(0, 0, Math.max(buttonBar.getBounds().width,
                        image.getBounds().width), buttonBar.getBounds().height
                        + image.getBounds().height + 5);
        shell.setSize(bounds.width, bounds.height);
        if (!shellVisible) {
            shell.setLocation(parentShell.getBounds().x
                    + (parentShell.getBounds().width - bounds.width) / 2, parentShell.getBounds().y
                    + (parentShell.getBounds().height - bounds.height) / 2);
        }
        shell.open();
        if (shellVisible) {
            canvas.redraw();
        }
    }

    private static void createShell() {
        shell = new Shell(Display.getDefault(), SWT.CLOSE | SWT.TITLE);
        GridLayout gridLayout = new GridLayout();
        gridLayout.marginWidth = 0;
        gridLayout.marginHeight = 0;
        shell.setLayout(gridLayout);

        buttonBar = new Composite(shell, SWT.NONE);
        RowLayout rowLayout = new RowLayout(SWT.HORIZONTAL);
        rowLayout.pack = true;
        rowLayout.center = true;
        buttonBar.setLayout(rowLayout);
        Composite buttons = new Composite(buttonBar, SWT.NONE);
        buttons.setLayout(new FillLayout());

        onWhite = new Button(buttons, SWT.TOGGLE);
        onWhite.setText("On White");
        onBlack = new Button(buttons, SWT.TOGGLE);
        onBlack.setText("On Black");
        onBlack.setSelection(true);
        onWhite.addSelectionListener(whiteSelectionListener);
        onBlack.addSelectionListener(blackSelectionListener);

        showExtras = new Button(buttonBar, SWT.CHECK);
        showExtras.setText("Show Extras");
        showExtras.addSelectionListener(extrasSelectionListener);

        canvas = new Canvas(shell, SWT.NONE);
        canvas.setLayoutData(new GridData(GridData.FILL_BOTH));
        canvas.addPaintListener(paintListener);

        shell.addShellListener(shellListener);

        ImageLoader imageLoader = ImageLoader.getLoader(HierarchyViewerDirector.class);
        Image image = imageLoader.loadImage("display.png", Display.getDefault());
        shell.setImage(image);
    }

    private static PaintListener paintListener = new PaintListener() {

        public void paintControl(PaintEvent e) {
            if (onWhite.getSelection()) {
                e.gc.setBackground(Display.getDefault().getSystemColor(SWT.COLOR_WHITE));
            } else {
                e.gc.setBackground(Display.getDefault().getSystemColor(SWT.COLOR_BLACK));
            }
            e.gc.fillRectangle(0, 0, canvas.getBounds().width, canvas.getBounds().height);
            if (image != null) {
                int width = image.getBounds().width;
                int height = image.getBounds().height;
                int x = (canvas.getBounds().width - width) / 2;
                int y = (canvas.getBounds().height - height) / 2;
                e.gc.drawImage(image, x, y);
                if (showExtras.getSelection()) {
                    if ((viewNode.paddingLeft | viewNode.paddingRight | viewNode.paddingTop | viewNode.paddingBottom) != 0) {
                        e.gc.setForeground(Display.getDefault().getSystemColor(SWT.COLOR_BLUE));
                        e.gc.drawRectangle(x + viewNode.paddingLeft, y + viewNode.paddingTop, width
                                - viewNode.paddingLeft - viewNode.paddingRight - 1, height
                                - viewNode.paddingTop - viewNode.paddingBottom - 1);
                    }
                    if (viewNode.hasMargins) {
                        e.gc.setForeground(Display.getDefault().getSystemColor(SWT.COLOR_GREEN));
                        e.gc.drawRectangle(x - viewNode.marginLeft, y - viewNode.marginTop, width
                                + viewNode.marginLeft + viewNode.marginRight - 1, height
                                + viewNode.marginTop + viewNode.marginBottom - 1);
                    }
                    if (viewNode.baseline != -1) {
                        e.gc.setForeground(Display.getDefault().getSystemColor(SWT.COLOR_RED));
                        e.gc.drawLine(x, y + viewNode.baseline, x + width - 1, viewNode.baseline);
                    }
                }
            }
        }
    };

    private static ShellAdapter shellListener = new ShellAdapter() {
        @Override
        public void shellClosed(ShellEvent e) {
            e.doit = false;
            shell.setVisible(false);
            if (viewNode != null) {
                viewNode.dereferenceImage();
            }
        }

    };

    private static SelectionListener whiteSelectionListener = new SelectionListener() {
        public void widgetDefaultSelected(SelectionEvent e) {
            // pass
        }

        public void widgetSelected(SelectionEvent e) {
            onWhite.setSelection(true);
            onBlack.setSelection(false);
            canvas.redraw();
        }
    };

    private static SelectionListener blackSelectionListener = new SelectionListener() {
        public void widgetDefaultSelected(SelectionEvent e) {
            // pass
        }

        public void widgetSelected(SelectionEvent e) {
            onBlack.setSelection(true);
            onWhite.setSelection(false);
            canvas.redraw();
        }
    };

    private static SelectionListener extrasSelectionListener = new SelectionListener() {
        public void widgetDefaultSelected(SelectionEvent e) {
            // pass
        }

        public void widgetSelected(SelectionEvent e) {
            canvas.redraw();
        }
    };
}
