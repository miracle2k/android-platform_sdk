/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.hierarchyviewer;

import com.android.ddmuilib.ImageLoader;
import com.android.hierarchyviewerlib.ComponentRegistry;
import com.android.hierarchyvieweruilib.DeviceSelector;
import com.android.hierarchyvieweruilib.PixelPerfect;
import com.android.hierarchyvieweruilib.PixelPerfectLoupe;
import com.android.hierarchyvieweruilib.PixelPerfectTree;
import com.android.hierarchyvieweruilib.TreeView;
import com.android.hierarchyvieweruilib.TreeViewOverview;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Slider;

public class UIThread {
    public static void runUI() {
        Display display = new Display();

        // CODE BELOW IS FOR TESTING.
        Shell shell = new Shell(display);
        shell.setLayout(new FillLayout());
        DeviceSelector deviceSelector = new DeviceSelector(shell);
        shell.open();
        Shell shell2 = new Shell(display);
        shell2.setLayout(new FillLayout());
        /*


        
        PixelPerfectTree pixelPerfectTree = new PixelPerfectTree(shell2);
        Composite overview = new Composite(shell2, SWT.NONE);
        overview.setLayout(new GridLayout());
        PixelPerfect pixelPerfect = new PixelPerfect(overview);
        pixelPerfect.setLayoutData(new GridData(GridData.FILL_BOTH));
        final Slider slider = new Slider(overview, SWT.HORIZONTAL);
        slider.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        slider.setMinimum(2);
        slider.setMaximum(25);
        slider.setSelection(8);
        slider.setThumb(1);
        final PixelPerfectLoupe pixelPerfectLoupe = new PixelPerfectLoupe(shell2);
        slider.addSelectionListener(new SelectionListener() {
            private int oldZoom = 8;

            public void widgetDefaultSelected(SelectionEvent arg0) {
                // pass
            }

            public void widgetSelected(SelectionEvent arg0) {
                int newZoom = slider.getSelection();
                if (newZoom != oldZoom) {
                    ComponentRegistry.getPixelPerfectModel().setZoom(newZoom);
                    oldZoom = newZoom;
                }
            }

        });
        shell2.open();
        */
        TreeView treeView = new TreeView(shell2);
        shell2.open();
        Shell shell3 = new Shell(display);
        shell3.setLayout(new FillLayout());
        TreeViewOverview treeViewOverview = new TreeViewOverview(shell3);
        shell3.open();
        // ComponentRegistry.getDirector().loadViewTreeData(null);
        while (!shell.isDisposed() && !shell2.isDisposed()) {
            if (!display.readAndDispatch()) {
                display.sleep();
            }
        }
        if (!shell.isDisposed()) {
            shell.dispose();
        }
        if (!shell2.isDisposed()) {
            shell2.dispose();
        }

        // NO LONGER TESTING STUFF.

        ImageLoader.dispose();
        display.dispose();
    }
}
