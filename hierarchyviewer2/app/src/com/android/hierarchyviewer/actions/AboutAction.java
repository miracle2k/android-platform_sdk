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

import org.eclipse.jface.action.Action;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

public class AboutAction extends Action implements ImageAction {

    private static AboutAction action;

    private Image image;

    private Shell shell;

    private AboutAction(Shell shell) {
        super("&About");
        this.shell = shell;
        setAccelerator(SWT.MOD1 + 'A');
        ImageLoader imageLoader = ImageLoader.getLoader(HierarchyViewerDirector.class);
        image = imageLoader.loadImage("about-small.jpg", Display.getDefault());
        setImageDescriptor(ImageDescriptor.createFromImage(image));
        setToolTipText("Shows the about dialog");
    }

    public static AboutAction getAction(Shell shell) {
        if (action == null) {
            action = new AboutAction(shell);
        }
        return action;
    }

    @Override
    public void run() {
        new AboutDialog(shell).open();
    }

    public Image getImage() {
        return image;
    }
}
