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

package com.android.ide.eclipse.hierarchyviewer;

import com.android.ide.eclipse.ddms.Perspective;
import com.android.ide.eclipse.hierarchyviewer.views.DeviceSelectorView;
import com.android.ide.eclipse.hierarchyviewer.views.PixelPerfectLoupeView;
import com.android.ide.eclipse.hierarchyviewer.views.PixelPerfectTreeView;
import com.android.ide.eclipse.hierarchyviewer.views.PixelPerfectView;

import org.eclipse.ui.IFolderLayout;
import org.eclipse.ui.IPageLayout;
import org.eclipse.ui.IPerspectiveFactory;

public class PixelPerfectPespective implements IPerspectiveFactory {

    public static final String ID =
            "com.android.ide.eclipse.hierarchyviewer.PixelPerfectPespective";

    public void createInitialLayout(IPageLayout layout) {
        layout.setEditorAreaVisible(false);

        String editorArea = layout.getEditorArea();
        IFolderLayout folder;

        folder = layout.createFolder("tree", IPageLayout.LEFT, 0.25f, editorArea);
        folder.addView(DeviceSelectorView.ID);
        folder.addView(PixelPerfectTreeView.ID);

        folder = layout.createFolder("overview", IPageLayout.RIGHT, 0.4f, editorArea);
        folder.addView(PixelPerfectView.ID);

        folder = layout.createFolder("main", IPageLayout.RIGHT, 0.35f, editorArea);
        folder.addView(PixelPerfectLoupeView.ID);


        layout.addShowViewShortcut(DeviceSelectorView.ID);
        layout.addShowViewShortcut(PixelPerfectTreeView.ID);
        layout.addShowViewShortcut(PixelPerfectLoupeView.ID);
        layout.addShowViewShortcut(PixelPerfectView.ID);

        layout.addPerspectiveShortcut("org.eclipse.jdt.ui.JavaPerspective");
        layout.addPerspectiveShortcut(TreeViewPerspective.ID);
        layout.addPerspectiveShortcut(Perspective.ID);
    }

}
