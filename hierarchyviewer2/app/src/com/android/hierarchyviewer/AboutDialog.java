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
import com.android.hierarchyviewerlib.HierarchyViewerDirector;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

public class AboutDialog extends Dialog {
    private Image mAboutImage;

    private Image mSmallImage;

    public AboutDialog(Shell shell) {
        super(shell);
        ImageLoader imageLoader = ImageLoader.getLoader(HierarchyViewerDirector.class);
        mSmallImage = imageLoader.loadImage("load-view-hierarchy.png", Display.getDefault()); //$NON-NLS-1$
        mAboutImage = imageLoader.loadImage("about.jpg", Display.getDefault()); //$NON-NLS-1$
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        createButton(parent, IDialogConstants.OK_ID, IDialogConstants.OK_LABEL, true);
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite control = new Composite(parent, SWT.NONE);
        control.setLayout(new GridLayout(2, true));
        Composite imageControl = new Composite(control, SWT.BORDER);
        imageControl.setLayout(new FillLayout());
        imageControl.setLayoutData(new GridData(GridData.FILL_VERTICAL));
        Label imageLabel = new Label(imageControl, SWT.CENTER);
        imageLabel.setImage(mAboutImage);

        CLabel textLabel = new CLabel(control, SWT.NONE);
        textLabel
                .setText("Hierarchy Viewer\nCopyright 2010, The Android Open Source Project\nAll Rights Reserved.");
        textLabel.setLayoutData(new GridData(GridData.BEGINNING, GridData.CENTER, true, true));
        getShell().setText("About...");
        getShell().setImage(mSmallImage);
        return control;

    }
}
