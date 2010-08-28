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

package com.android.hierarchyviewer.actions;

import com.android.ddmuilib.ImageLoader;
import com.android.hierarchyviewer.AboutDialog;
import com.android.hierarchyviewerlib.HierarchyViewerDirector;
import com.android.hierarchyviewerlib.actions.ImageAction;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

public class AboutAction extends Action implements ImageAction {

    private static AboutAction sAction;

    private Image mImage;

    private Shell mShell;

    private AboutAction(Shell shell) {
        super("&About");
        this.mShell = shell;
        setAccelerator(SWT.MOD1 + 'A');
        ImageLoader imageLoader = ImageLoader.getLoader(HierarchyViewerDirector.class);
        mImage = imageLoader.loadImage("about-small.jpg", Display.getDefault()); //$NON-NLS-1$
        setImageDescriptor(ImageDescriptor.createFromImage(mImage));
        setToolTipText("Shows the about dialog");
    }

    public static AboutAction getAction(Shell shell) {
        if (sAction == null) {
            sAction = new AboutAction(shell);
        }
        return sAction;
    }

    @Override
    public void run() {
        new AboutDialog(mShell).open();
    }

    public Image getImage() {
        return mImage;
    }
}
