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

package com.android.ide.eclipse.hierarchyviewer.views;

import com.android.ddmuilib.ImageLoader;
import com.android.hierarchyviewerlib.HierarchyViewerDirector;
import com.android.hierarchyviewerlib.models.TreeViewModel;
import com.android.hierarchyviewerlib.models.TreeViewModel.TreeChangeListener;
import com.android.hierarchyviewerlib.ui.LayoutViewer;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.part.ViewPart;

public class LayoutView extends ViewPart implements TreeChangeListener {

    public static final String ID = "com.android.ide.eclipse.hierarchyviewer.views.LayoutView";

    private LayoutViewer layoutViewer;

    private Image onBlack;

    private Image onWhite;

    private Action showExtrasAction = new Action("Show &Extras", Action.AS_CHECK_BOX) {
        @Override
        public void run() {
            layoutViewer.setShowExtras(isChecked());
        }
    };

    private Action loadAllViewsAction = new Action("Load All &Views") {
        @Override
        public void run() {
            HierarchyViewerDirector.getDirector().loadAllViews();
            showExtrasAction.setChecked(true);
            layoutViewer.setShowExtras(true);
        }
    };

    private Action onBlackWhiteAction = new Action("Change Background &Color") {
        @Override
        public void run() {
            boolean newValue = !layoutViewer.getOnBlack();
            layoutViewer.setOnBlack(newValue);
            if (newValue) {
                setImageDescriptor(ImageDescriptor.createFromImage(onWhite));
            } else {
                setImageDescriptor(ImageDescriptor.createFromImage(onBlack));
            }
        }
    };

    @Override
    public void createPartControl(Composite parent) {
        showExtrasAction.setAccelerator(SWT.MOD1 + 'E');
        ImageLoader imageLoader = ImageLoader.getLoader(HierarchyViewerDirector.class);
        Image image = imageLoader.loadImage("show-extras.png", Display.getDefault());
        showExtrasAction.setImageDescriptor(ImageDescriptor.createFromImage(image));
        showExtrasAction.setToolTipText("Show images");
        showExtrasAction.setEnabled(TreeViewModel.getModel().getTree() != null);

        onWhite = imageLoader.loadImage("on-white.png", Display.getDefault());
        onBlack = imageLoader.loadImage("on-black.png", Display.getDefault());

        onBlackWhiteAction.setAccelerator(SWT.MOD1 + 'C');
        onBlackWhiteAction.setImageDescriptor(ImageDescriptor.createFromImage(onWhite));
        onBlackWhiteAction.setToolTipText("Change layout viewer background color");

        loadAllViewsAction.setAccelerator(SWT.MOD1 + 'V');
        image = imageLoader.loadImage("load-all-views.png", Display.getDefault());
        loadAllViewsAction.setImageDescriptor(ImageDescriptor.createFromImage(image));
        loadAllViewsAction.setToolTipText("Load all view images");
        loadAllViewsAction.setEnabled(TreeViewModel.getModel().getTree() != null);

        parent.setLayout(new FillLayout());

        layoutViewer = new LayoutViewer(parent);

        placeActions();

        TreeViewModel.getModel().addTreeChangeListener(this);
    }

    public void placeActions() {
        IActionBars actionBars = getViewSite().getActionBars();

        IMenuManager mm = actionBars.getMenuManager();
        mm.removeAll();
        mm.add(onBlackWhiteAction);
        mm.add(showExtrasAction);
        mm.add(loadAllViewsAction);

        IToolBarManager tm = actionBars.getToolBarManager();
        tm.removeAll();
        tm.add(onBlackWhiteAction);
        tm.add(showExtrasAction);
        tm.add(loadAllViewsAction);
    }

    @Override
    public void dispose() {
        super.dispose();
        TreeViewModel.getModel().removeTreeChangeListener(this);
    }

    @Override
    public void setFocus() {
        layoutViewer.setFocus();
    }

    public void selectionChanged() {
        // pass
    }

    public void treeChanged() {
        Display.getDefault().syncExec(new Runnable() {
            public void run() {
                loadAllViewsAction.setEnabled(TreeViewModel.getModel().getTree() != null);
                showExtrasAction.setEnabled(TreeViewModel.getModel().getTree() != null);
            }
        });
    }

    public void viewportChanged() {
        // pass
    }

    public void zoomChanged() {
        // pass
    }

}
