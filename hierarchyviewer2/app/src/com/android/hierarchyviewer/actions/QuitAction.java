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

import com.android.hierarchyviewer.HierarchyViewerApplication;

import org.eclipse.jface.action.Action;
import org.eclipse.swt.SWT;

public class QuitAction extends Action {

    private static QuitAction sAction;

    private QuitAction() {
        super("E&xit");
        setAccelerator(SWT.MOD1 + 'Q');
    }

    public static QuitAction getAction() {
        if (sAction == null) {
            sAction = new QuitAction();
        }
        return sAction;
    }

    @Override
    public void run() {
        HierarchyViewerApplication.getMainWindow().close();
    }
}
