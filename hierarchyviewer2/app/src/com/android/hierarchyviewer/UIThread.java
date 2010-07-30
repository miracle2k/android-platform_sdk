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
import com.android.hierarchyvieweruilib.DeviceSelector;
import com.android.hierarchyvieweruilib.PixelPerfect;
import com.android.hierarchyvieweruilib.PixelPerfectLoupe;
import com.android.hierarchyvieweruilib.PixelPerfectTree;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.FillLayout;
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
        PixelPerfect pixelPerfect = new PixelPerfect(shell2);
        shell2.open();
        Shell shell3 = new Shell(display);
        shell3.setLayout(new FillLayout());
        final PixelPerfectLoupe pixelPerfectLoupe = new PixelPerfectLoupe(shell3);
        shell3.open();
        Shell shell4 = new Shell(display);
        shell4.setLayout(new FillLayout());
        PixelPerfectTree pixelPerfectTree = new PixelPerfectTree(shell4);
        shell4.open();
        Shell shell5 = new Shell(display);
        shell5.setLayout(new FillLayout());
        final Slider slider = new Slider(shell5, SWT.HORIZONTAL);
        slider.setMinimum(2);
        slider.setMaximum(25);
        slider.setSelection(8);
        slider.setThumb(1);
        slider.addSelectionListener(new SelectionListener() {
            private int oldZoom = 8;

            public void widgetDefaultSelected(SelectionEvent arg0) {
                // pass
            }

            public void widgetSelected(SelectionEvent arg0) {
                int newZoom = slider.getSelection();
                if (newZoom != oldZoom) {
                    pixelPerfectLoupe.setZoom(newZoom);
                    oldZoom = newZoom;
                }
            }

        });
        shell5.open();
        while (!shell.isDisposed() && !shell2.isDisposed() && !shell3.isDisposed()
                && !shell4.isDisposed()) {
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
        if (!shell3.isDisposed()) {
            shell3.dispose();
        }
        if (!shell4.isDisposed()) {
            shell4.dispose();
        }

        // NO LONGER TESTING STUFF.

        deviceSelector.terminate();
        pixelPerfect.terminate();
        pixelPerfectLoupe.terminate();
        pixelPerfectTree.terminate();
        ImageLoader.dispose();
        display.dispose();
    }
}
