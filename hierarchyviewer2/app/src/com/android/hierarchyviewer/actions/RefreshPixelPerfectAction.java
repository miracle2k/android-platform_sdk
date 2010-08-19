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
import com.android.hierarchyviewerlib.HierarchyViewerDirector;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;

public class RefreshPixelPerfectAction extends Action implements ImageAction {

    private static RefreshPixelPerfectAction action;

    private Image image;

    private RefreshPixelPerfectAction() {
        super("&Refresh Screenshot");
        setAccelerator(SWT.F5);
        ImageLoader imageLoader = ImageLoader.getLoader(HierarchyViewerDirector.class);
        image = imageLoader.loadImage("refresh-windows.png", Display.getDefault());
        setImageDescriptor(ImageDescriptor.createFromImage(image));
        setToolTipText("Refresh the screenshot");
    }

    public static RefreshPixelPerfectAction getAction() {
        if (action == null) {
            action = new RefreshPixelPerfectAction();
        }
        return action;
    }

    @Override
    public void run() {
        HierarchyViewerDirector.getDirector().refreshPixelPerfect();
    }

    public Image getImage() {
        return image;
    }
}
