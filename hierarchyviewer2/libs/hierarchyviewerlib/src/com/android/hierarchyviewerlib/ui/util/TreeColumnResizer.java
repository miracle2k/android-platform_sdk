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

package com.android.hierarchyviewerlib.ui.util;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.TreeColumn;

public class TreeColumnResizer {

    private TreeColumn column1;
    private TreeColumn column2;

    private Composite control;
    private int column1Width;
    private int column2Width;

    private final static int MIN_COLUMN1_WIDTH = 18;

    private final static int MIN_COLUMN2_WIDTH = 3;

    public TreeColumnResizer(Composite control, TreeColumn column1, TreeColumn column2) {
        this.control = control;
        this.column1 = column1;
        this.column2 = column2;
        control.addListener(SWT.Resize, resizeListener);
        column1.addListener(SWT.Resize, column1ResizeListener);
        column2.setResizable(false);
    }

    private Listener resizeListener = new Listener() {
        public void handleEvent(Event e) {
            if (column1Width == 0 && column2Width == 0) {
                column1Width = (control.getBounds().width - 18) / 2;
                column2Width = (control.getBounds().width - 18) / 2;
            } else {
                int dif = control.getBounds().width - 18 - (column1Width + column2Width);
                int columnDif = Math.abs(column1Width - column2Width);
                int mainColumnChange = Math.min(Math.abs(dif), columnDif);
                int left = Math.max(0, Math.abs(dif) - columnDif);
                if (dif < 0) {
                    if (column1Width > column2Width) {
                        column1Width -= mainColumnChange;
                    } else {
                        column2Width -= mainColumnChange;
                    }
                    column1Width -= left / 2;
                    column2Width -= left - left / 2;
                } else {
                    if (column1Width > column2Width) {
                        column2Width += mainColumnChange;
                    } else {
                        column1Width += mainColumnChange;
                    }
                    column1Width += left / 2;
                    column2Width += left - left / 2;
                }
            }
            column1.removeListener(SWT.Resize, column1ResizeListener);
            column1.setWidth(column1Width);
            column2.setWidth(column2Width);
            column1.addListener(SWT.Resize, column1ResizeListener);
        }
    };

    private Listener column1ResizeListener = new Listener() {
        public void handleEvent(Event e) {
            int widthDif = column1Width - column1.getWidth();
            column1Width -= widthDif;
            column2Width += widthDif;
            boolean column1Changed = false;

            // Strange, but these constants make the columns look the same.

            if (column1Width < MIN_COLUMN1_WIDTH) {
                column2Width -= MIN_COLUMN1_WIDTH - column1Width;
                column1Width += MIN_COLUMN1_WIDTH - column1Width;
                column1Changed = true;
            }
            if (column2Width < MIN_COLUMN2_WIDTH) {
                column1Width += column2Width - MIN_COLUMN2_WIDTH;
                column2Width = MIN_COLUMN2_WIDTH;
                column1Changed = true;
            }
            if (column1Changed) {
                column1.removeListener(SWT.Resize, this);
                column1.setWidth(column1Width);
                column1.addListener(SWT.Resize, this);
            }
            column2.setWidth(column2Width);
        }
    };
}
