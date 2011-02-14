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

import com.android.ide.eclipse.adt.internal.wizards.newxmlfile.ResourceNameValidator;
import com.android.resources.ResourceType;

import org.eclipse.core.resources.IProject;
import org.eclipse.ltk.ui.refactoring.RefactoringWizard;
import org.eclipse.ltk.ui.refactoring.UserInputWizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

class ExtractIncludeWizard extends RefactoringWizard {
    private final IProject mProject;

    public ExtractIncludeWizard(ExtractIncludeRefactoring ref, IProject project) {
        super(ref, DIALOG_BASED_USER_INTERFACE | PREVIEW_EXPAND_FIRST_NODE);
        mProject = project;
        setDefaultPageTitle(ref.getName());
    }

    @Override
    protected void addUserInputPages() {
        ExtractIncludeRefactoring ref = (ExtractIncludeRefactoring) getRefactoring();
        String initialName = ref.getInitialName();
        addPage(new InputPage(mProject, initialName));
    }

    /** Wizard page which inputs parameters for the {@link ExtractIncludeRefactoring} operation */
    private static class InputPage extends UserInputWizardPage {
        private final IProject mProject;
        private final String mSuggestedName;
        private Text mNameText;
        private Button mUpdateReferences;
        private Button mReplaceAllOccurrences;

        public InputPage(IProject project, String suggestedName) {
            super("ExtractIncludeInputPage");  //$NON-NLS-1$
            mProject = project;
            mSuggestedName = suggestedName;
        }

        public void createControl(Composite parent) {
            Composite composite = new Composite(parent, SWT.NONE);
            composite.setLayout(new GridLayout(2, false));

            Label nameLabel = new Label(composite, SWT.NONE);
            nameLabel.setText("New Layout Name:");
            nameLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));

            mNameText = new Text(composite, SWT.BORDER);
            mNameText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
            mNameText.addModifyListener(new ModifyListener() {
                public void modifyText(ModifyEvent e) {
                    validatePage();
                }
            });

            mUpdateReferences = new Button(composite, SWT.CHECK);
            mUpdateReferences.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER,
                    false, false, 2, 1));
            mUpdateReferences.setSelection(true);
            mUpdateReferences.setText("Update layout references");

            mReplaceAllOccurrences = new Button(composite, SWT.CHECK);
            mReplaceAllOccurrences.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER,
                    false, false, 2, 1));
            mReplaceAllOccurrences.setText("Replace all occurrences with include to new layout");
            mReplaceAllOccurrences.setEnabled(false);

            // Initialize UI:
            if (mSuggestedName != null) {
                mNameText.setText(mSuggestedName);
            }

            setControl(composite);
            validatePage();
        }

        private boolean validatePage() {
            boolean ok = true;

            String text = mNameText.getText().trim();

            if (text.length() == 0) {
                setErrorMessage("Provide a name for the new layout");
                ok = false;
            } else {
                ResourceNameValidator validator = ResourceNameValidator.create(false, mProject,
                        ResourceType.LAYOUT);
                String message = validator.isValid(text);
                if (message != null) {
                    setErrorMessage(message);
                    ok = false;
                }
            }

            if (ok) {
                setErrorMessage(null);

                // Record state
                ExtractIncludeRefactoring refactoring =
                    (ExtractIncludeRefactoring) getRefactoring();
                refactoring.setLayoutName(text);
                refactoring.setReplaceOccurrences(mReplaceAllOccurrences.getSelection());
                refactoring.setUpdateReferences(mUpdateReferences.getSelection());
            }

            setPageComplete(ok);
            return ok;
        }
    }
}
