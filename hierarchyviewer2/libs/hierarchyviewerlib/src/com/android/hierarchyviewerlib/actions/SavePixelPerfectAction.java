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

package com.android.hierarchyviewerlib.actions;

import com.android.ddmuilib.ImageLoader;
import com.android.hierarchyviewerlib.HierarchyViewerDirector;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

public class SavePixelPerfectAction extends PixelPerfectEnabledAction implements ImageAction {

    private static SavePixelPerfectAction action;

    private Image image;

    private Shell shell;

    private SavePixelPerfectAction(Shell shell) {
        super("&Save as PNG");
        this.shell = shell;
        setAccelerator(SWT.MOD1 + 'S');
        ImageLoader imageLoader = ImageLoader.getLoader(HierarchyViewerDirector.class);
        image = imageLoader.loadImage("save.png", Display.getDefault());
        setImageDescriptor(ImageDescriptor.createFromImage(image));
        setToolTipText("Save the screenshot as a PNG image");
    }

    public static SavePixelPerfectAction getAction(Shell shell) {
        if (action == null) {
            action = new SavePixelPerfectAction(shell);
        }
        return action;
    }

    @Override
    public void run() {
        HierarchyViewerDirector.getDirector().savePixelPerfect(shell);
    }

    public Image getImage() {
        return image;
    }
}
