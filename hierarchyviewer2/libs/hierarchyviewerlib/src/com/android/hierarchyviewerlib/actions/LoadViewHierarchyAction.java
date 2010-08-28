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

import com.android.ddmlib.IDevice;
import com.android.ddmuilib.ImageLoader;
import com.android.hierarchyviewerlib.HierarchyViewerDirector;
import com.android.hierarchyviewerlib.device.Window;
import com.android.hierarchyviewerlib.models.DeviceSelectionModel;
import com.android.hierarchyviewerlib.models.DeviceSelectionModel.IWindowChangeListener;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;

public class LoadViewHierarchyAction extends Action implements ImageAction, IWindowChangeListener {

    private static LoadViewHierarchyAction sAction;

    private Image mImage;

    private LoadViewHierarchyAction() {
        super("Load View &Hierarchy");
        setAccelerator(SWT.MOD1 + 'H');
        ImageLoader imageLoader = ImageLoader.getLoader(HierarchyViewerDirector.class);
        mImage = imageLoader.loadImage("load-view-hierarchy.png", Display.getDefault()); //$NON-NLS-1$
        setImageDescriptor(ImageDescriptor.createFromImage(mImage));
        setToolTipText("Load the view hierarchy into the tree view");
        setEnabled(
                DeviceSelectionModel.getModel().getSelectedWindow() != null);
        DeviceSelectionModel.getModel().addWindowChangeListener(this);
    }

    public static LoadViewHierarchyAction getAction() {
        if (sAction == null) {
            sAction = new LoadViewHierarchyAction();
        }
        return sAction;
    }

    @Override
    public void run() {
        HierarchyViewerDirector.getDirector().loadViewHierarchy();
    }

    public Image getImage() {
        return mImage;
    }

    public void deviceChanged(IDevice device) {
        // pass
    }

    public void deviceConnected(IDevice device) {
        // pass
    }

    public void deviceDisconnected(IDevice device) {
        // pass
    }

    public void focusChanged(IDevice device) {
        // pass
    }

    public void selectionChanged(final IDevice device, final Window window) {
        Display.getDefault().syncExec(new Runnable() {
            public void run() {
                LoadViewHierarchyAction.getAction().setEnabled(window != null);
            }
        });
    }
}
