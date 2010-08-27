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
import com.android.hierarchyviewer.HierarchyViewerApplication;
import com.android.hierarchyviewerlib.HierarchyViewerDirector;
import com.android.hierarchyviewerlib.actions.ImageAction;
import com.android.hierarchyviewerlib.models.PixelPerfectModel;
import com.android.hierarchyviewerlib.models.PixelPerfectModel.ImageChangeListener;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;

public class ShowOverlayAction extends Action implements ImageAction, ImageChangeListener {

    private static ShowOverlayAction action;

    private Image image;

    private ShowOverlayAction() {
        super("Show In &Loupe", Action.AS_CHECK_BOX);
        setAccelerator(SWT.MOD1 + 'L');
        ImageLoader imageLoader = ImageLoader.getLoader(HierarchyViewerDirector.class);
        image = imageLoader.loadImage("show-overlay.png", Display.getDefault());
        setImageDescriptor(ImageDescriptor.createFromImage(image));
        setToolTipText("Show the overlay in the loupe view");
        setEnabled(PixelPerfectModel.getModel().getOverlayImage() != null);
        PixelPerfectModel.getModel().addImageChangeListener(this);
    }

    public static ShowOverlayAction getAction() {
        if (action == null) {
            action = new ShowOverlayAction();
        }
        return action;
    }

    @Override
    public void run() {
        HierarchyViewerApplication.getApp().showOverlayInLoupe(action.isChecked());
    }

    public Image getImage() {
        return image;
    }
    
    public void crosshairMoved() {
        // pass
    }

    public void treeChanged() {
        // pass
    }

    public void imageChanged() {
        // pass
    }

    public void imageLoaded() {
        Display.getDefault().syncExec(new Runnable() {
            public void run() {
                Image overlayImage = PixelPerfectModel.getModel().getOverlayImage();
                setEnabled(overlayImage != null);
            }
        });
    }

    public void overlayChanged() {
        Display.getDefault().syncExec(new Runnable() {
            public void run() {
                setEnabled(PixelPerfectModel.getModel().getOverlayImage() != null);
            }
        });
    }

    public void overlayTransparencyChanged() {
        // pass
    }

    public void selectionChanged() {
        // pass
    }

    public void zoomChanged() {
        // pass
    }
}
