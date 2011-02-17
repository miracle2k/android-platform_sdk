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

package com.android.ide.eclipse.adt.internal.editors.layout.refactoring;

import org.eclipse.core.resources.IProject;
import org.eclipse.ltk.ui.refactoring.RefactoringWizard;
import org.eclipse.ltk.ui.refactoring.UserInputWizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

class ChangeLayoutWizard extends RefactoringWizard {
    private final IProject mProject;

    public ChangeLayoutWizard(ChangeLayoutRefactoring ref, IProject project) {
        super(ref, DIALOG_BASED_USER_INTERFACE | PREVIEW_EXPAND_FIRST_NODE);
        mProject = project;
        setDefaultPageTitle("Change Layout");
    }

    @Override
    protected void addUserInputPages() {
        ChangeLayoutRefactoring ref = (ChangeLayoutRefactoring) getRefactoring();
        String oldType = ref.getOldType();
        addPage(new InputPage(mProject, oldType));
    }

    /** Wizard page which inputs parameters for the {@link ChangeLayoutRefactoring} operation */
    private static class InputPage extends UserInputWizardPage {
        private final IProject mProject;
        private final String mOldType;
        private Combo mTypeCombo;

        public InputPage(IProject project, String oldType) {
            super("ChangeLayoutInputPage");  //$NON-NLS-1$
            mProject = project;
            mOldType = oldType;
        }

        public void createControl(Composite parent) {
            Composite composite = new Composite(parent, SWT.NONE);
            composite.setLayout(new GridLayout(2, false));

            Label typeLabel = new Label(composite, SWT.NONE);
            typeLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
            typeLabel.setText("New Layout Type:");

            mTypeCombo = new Combo(composite, SWT.READ_ONLY);
            mTypeCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
            SelectionAdapter selectionListener = new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e) {
                    validatePage();
                }
            };
            mTypeCombo.addSelectionListener(selectionListener);

            WrapInWizard.addLayouts(mProject, mTypeCombo, mOldType);

            setControl(composite);
            validatePage();
        }

        private boolean validatePage() {
            boolean ok = true;

            if (mTypeCombo.getText().equals(WrapInWizard.SEPARATOR_LABEL)) {
                setErrorMessage("Select a layout type");
                ok = false;
            }

            if (ok) {
                setErrorMessage(null);

                // Record state
                ChangeLayoutRefactoring refactoring =
                    (ChangeLayoutRefactoring) getRefactoring();
                refactoring.setType(mTypeCombo.getText());
            }

            setPageComplete(ok);
            return ok;
        }
    }
}
