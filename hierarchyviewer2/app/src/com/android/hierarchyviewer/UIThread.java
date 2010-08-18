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
import com.android.hierarchyviewerlib.ui.DeviceSelector;
import com.android.hierarchyviewerlib.ui.LayoutViewer;
import com.android.hierarchyviewerlib.ui.PixelPerfect;
import com.android.hierarchyviewerlib.ui.PixelPerfectLoupe;
import com.android.hierarchyviewerlib.ui.PixelPerfectTree;
import com.android.hierarchyviewerlib.ui.ProfileViewer;
import com.android.hierarchyviewerlib.ui.PropertyViewer;
import com.android.hierarchyviewerlib.ui.TreeView;
import com.android.hierarchyviewerlib.ui.TreeViewOverview;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.events.ShellListener;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
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
        
        while (!shell.isDisposed()) {
            if (!display.readAndDispatch()) {
                display.sleep();
            }
        }

        // NO LONGER TESTING STUFF.

        ImageLoader.dispose();
        display.dispose();
    }
}
