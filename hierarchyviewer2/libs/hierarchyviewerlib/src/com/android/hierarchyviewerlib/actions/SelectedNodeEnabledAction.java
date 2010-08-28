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

import com.android.hierarchyviewerlib.models.TreeViewModel;
import com.android.hierarchyviewerlib.models.TreeViewModel.ITreeChangeListener;

import org.eclipse.jface.action.Action;
import org.eclipse.swt.widgets.Display;

public class SelectedNodeEnabledAction extends Action implements ITreeChangeListener {
    public SelectedNodeEnabledAction(String name) {
        super(name);
        setEnabled(TreeViewModel.getModel().getTree() != null
                && TreeViewModel.getModel().getSelection() != null);
        TreeViewModel.getModel().addTreeChangeListener(this);
    }

    public void selectionChanged() {
        Display.getDefault().syncExec(new Runnable() {
            public void run() {
                setEnabled(TreeViewModel.getModel().getTree() != null
                        && TreeViewModel.getModel().getSelection() != null);
            }
        });
    }

    public void treeChanged() {
        Display.getDefault().syncExec(new Runnable() {
            public void run() {
                setEnabled(TreeViewModel.getModel().getTree() != null
                        && TreeViewModel.getModel().getSelection() != null);
            }
        });
    }

    public void viewportChanged() {
    }

    public void zoomChanged() {
    }
}
