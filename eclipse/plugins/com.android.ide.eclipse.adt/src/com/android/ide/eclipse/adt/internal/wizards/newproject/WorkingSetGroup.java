/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.eclipse.org/org/documents/epl-v10.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.ide.eclipse.adt.internal.wizards.newproject;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.wizards.NewWizardMessages;
import org.eclipse.jdt.internal.ui.workingsets.IWorkingSetIDs;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.ui.IWorkingSet;
import org.eclipse.ui.dialogs.WorkingSetConfigurationBlock;

/**
 * Copied from
 * org.eclipse.jdt.ui.wizards.NewJavaProjectWizardPageOne$WorkingSetGroup
 *
 * Creates the working set group with controls that allow
 * the selection of working sets
 */
public class WorkingSetGroup {

    private WorkingSetConfigurationBlock fWorkingSetBlock;

    public WorkingSetGroup() {
        String[] workingSetIds = new String[] {
                IWorkingSetIDs.JAVA, IWorkingSetIDs.RESOURCE
        };
        fWorkingSetBlock = new WorkingSetConfigurationBlock(workingSetIds, JavaPlugin.getDefault()
                .getDialogSettings());
    }

    public Control createControl(Composite composite) {
        Group workingSetGroup = new Group(composite, SWT.NONE);
        workingSetGroup.setFont(composite.getFont());
        workingSetGroup.setText(NewWizardMessages.NewJavaProjectWizardPageOne_WorkingSets_group);
        workingSetGroup.setLayout(new GridLayout(1, false));

        fWorkingSetBlock.createContent(workingSetGroup);

        return workingSetGroup;
    }

    public void setWorkingSets(IWorkingSet[] workingSets) {
        fWorkingSetBlock.setWorkingSets(workingSets);
    }

    public IWorkingSet[] getSelectedWorkingSets() {
        return fWorkingSetBlock.getSelectedWorkingSets();
    }
}
