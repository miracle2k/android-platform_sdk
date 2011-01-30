/*
 * Copyright (C) 2011 The Android Open Source Project
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
package com.android.ide.eclipse.traceview.views;

import com.android.traceview.ColorController;
import com.android.traceview.DmTraceReader;
import com.android.traceview.ProfileView;
import com.android.traceview.SelectionController;
import com.android.traceview.TimeLineView;
import com.android.traceview.TraceReader;
import com.android.traceview.TraceUnits;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.part.ViewPart;

public class TraceviewView extends ViewPart {

    private Action mOpenTraceFileAction;
    private Composite mParent;
    private String mFilename;
    private Composite mContents;

    @Override
    public void createPartControl(Composite parent) {
        mParent = parent;
        mContents = null;

        mOpenTraceFileAction = new Action("Open trace file") {
            @Override
            public void run() {
                FileDialog fd = new FileDialog(mParent.getShell(), SWT.OPEN);
                mFilename = fd.open();
                if (mFilename != null) {
                    display();
                }
            }
        };

        IActionBars actionBars = getViewSite().getActionBars();

        // toolbar
        IToolBarManager toolBarManager = actionBars.getToolBarManager();
        toolBarManager.add(mOpenTraceFileAction);
    }

    public void display() {
        TraceReader reader = new DmTraceReader(mFilename, false);
        reader.getTraceUnits().setTimeScale(TraceUnits.TimeScale.MilliSeconds);

        if (mContents != null) {
            mContents.dispose();
        }
        mContents = new Composite(mParent, SWT.NONE);

        Display display = mContents.getDisplay();
        ColorController.assignMethodColors(display, reader.getMethods());
        SelectionController selectionController = new SelectionController();

        GridLayout gridLayout = new GridLayout(1, false);
        gridLayout.marginWidth = 0;
        gridLayout.marginHeight = 0;
        gridLayout.horizontalSpacing = 0;
        gridLayout.verticalSpacing = 0;
        mContents.setLayout(gridLayout);

        Color darkGray = display.getSystemColor(SWT.COLOR_DARK_GRAY);

        // Create a sash form to separate the timeline view (on top)
        // and the profile view (on bottom)
        SashForm sashForm1 = new SashForm(mContents, SWT.VERTICAL);
        sashForm1.setBackground(darkGray);
        sashForm1.SASH_WIDTH = 3;
        GridData data = new GridData(GridData.FILL_BOTH);
        sashForm1.setLayoutData(data);

        // Create the timeline view
        new TimeLineView(sashForm1, reader, selectionController);

        // Create the profile view
        new ProfileView(sashForm1, reader, selectionController);

        mParent.layout();
    }

    @Override
    public void setFocus() {
        mParent.setFocus();
    }

}
