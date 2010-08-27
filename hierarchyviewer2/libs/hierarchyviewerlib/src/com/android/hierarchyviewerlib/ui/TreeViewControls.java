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

package com.android.hierarchyviewerlib.ui;

import com.android.hierarchyviewerlib.HierarchyViewerDirector;
import com.android.hierarchyviewerlib.models.TreeViewModel;
import com.android.hierarchyviewerlib.models.TreeViewModel.TreeChangeListener;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Slider;
import org.eclipse.swt.widgets.Text;

public class TreeViewControls extends Composite implements TreeChangeListener {

    private Text filterText;

    private Slider zoomSlider;

    public TreeViewControls(Composite parent) {
        super(parent, SWT.NONE);
        GridLayout layout = new GridLayout(5, false);
        layout.marginWidth = layout.marginHeight = 2;
        layout.verticalSpacing = layout.horizontalSpacing = 4;
        setLayout(layout);

        Label filterLabel = new Label(this, SWT.NONE);
        filterLabel.setText("Filter by class or id:");
        filterLabel.setLayoutData(new GridData(GridData.BEGINNING, GridData.CENTER, false, true));

        filterText = new Text(this, SWT.LEFT | SWT.SINGLE);
        filterText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        filterText.addModifyListener(filterTextModifyListener);
        filterText.setText(HierarchyViewerDirector.getDirector().getFilterText());

        Label smallZoomLabel = new Label(this, SWT.NONE);
        smallZoomLabel.setText(" 20%");
        smallZoomLabel
                .setLayoutData(new GridData(GridData.BEGINNING, GridData.CENTER, false, true));

        zoomSlider = new Slider(this, SWT.HORIZONTAL);
        GridData zoomSliderGridData = new GridData(GridData.CENTER, GridData.CENTER, false, false);
        zoomSliderGridData.widthHint = 190;
        zoomSlider.setLayoutData(zoomSliderGridData);
        zoomSlider.setMinimum((int) (TreeViewModel.MIN_ZOOM * 10));
        zoomSlider.setMaximum((int) (TreeViewModel.MAX_ZOOM * 10 + 1));
        zoomSlider.setThumb(1);
        zoomSlider.setSelection((int) Math.round(TreeViewModel.getModel().getZoom() * 10));

        zoomSlider.addSelectionListener(zoomSliderSelectionListener);

        Label largeZoomLabel = new Label(this, SWT.NONE);
        largeZoomLabel
                .setLayoutData(new GridData(GridData.BEGINNING, GridData.CENTER, false, true));
        largeZoomLabel.setText("200%");

        addDisposeListener(disposeListener);

        TreeViewModel.getModel().addTreeChangeListener(this);
    }

    private DisposeListener disposeListener = new DisposeListener() {
        public void widgetDisposed(DisposeEvent e) {
            TreeViewModel.getModel().removeTreeChangeListener(TreeViewControls.this);
        }
    };

    private SelectionListener zoomSliderSelectionListener = new SelectionListener() {
        private int oldValue;

        public void widgetDefaultSelected(SelectionEvent e) {
            // pass
        }

        public void widgetSelected(SelectionEvent e) {
            int newValue = zoomSlider.getSelection();
            if (oldValue != newValue) {
                TreeViewModel.getModel().removeTreeChangeListener(TreeViewControls.this);
                TreeViewModel.getModel().setZoom(newValue / 10.0);
                TreeViewModel.getModel().addTreeChangeListener(TreeViewControls.this);
                oldValue = newValue;
            }
        }
    };

    private ModifyListener filterTextModifyListener = new ModifyListener() {
        public void modifyText(ModifyEvent e) {
            HierarchyViewerDirector.getDirector().filterNodes(filterText.getText());
        }
    };

    public void selectionChanged() {
        // pass
    }

    public void treeChanged() {
        Display.getDefault().syncExec(new Runnable() {
            public void run() {
                if (TreeViewModel.getModel().getTree() != null) {
                    zoomSlider.setSelection((int) Math
                            .round(TreeViewModel.getModel().getZoom() * 10));
                }
                filterText.setText("");
            }
        });
    }

    public void viewportChanged() {
        // pass
    }

    public void zoomChanged() {
        Display.getDefault().syncExec(new Runnable() {
            public void run() {
                zoomSlider.setSelection((int) Math.round(TreeViewModel.getModel().getZoom() * 10));
            }
        });
    };
}
