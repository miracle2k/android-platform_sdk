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

import static com.android.ide.common.layout.LayoutConstants.FQCN_LINEAR_LAYOUT;

import com.android.ide.eclipse.adt.internal.editors.layout.descriptors.ViewElementDescriptor;
import com.android.ide.eclipse.adt.internal.editors.layout.gre.ViewMetadataRepository;
import com.android.ide.eclipse.adt.internal.sdk.AndroidTargetData;
import com.android.ide.eclipse.adt.internal.sdk.Sdk;
import com.android.ide.eclipse.adt.internal.wizards.newxmlfile.ResourceNameValidator;
import com.android.resources.ResourceType;
import com.android.sdklib.IAndroidTarget;
import com.android.util.Pair;

import org.eclipse.core.resources.IProject;
import org.eclipse.ltk.ui.refactoring.RefactoringWizard;
import org.eclipse.ltk.ui.refactoring.UserInputWizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import java.util.List;

class WrapInWizard extends RefactoringWizard {
    private final IProject mProject;

    public WrapInWizard(WrapInRefactoring ref, IProject project) {
        super(ref, DIALOG_BASED_USER_INTERFACE | PREVIEW_EXPAND_FIRST_NODE);
        mProject = project;
        setDefaultPageTitle("Wrap in Container");
    }

    @Override
    protected void addUserInputPages() {
        addPage(new InputPage(mProject));
    }

    /** Wizard page which inputs parameters for the {@link WrapInRefactoring} operation */
    private static class InputPage extends UserInputWizardPage {
        private final IProject mProject;
        private Text mIdText;
        private Combo mTypeCombo;
        private Button mUpdateReferences;

        public InputPage(IProject project) {
            super("WrapInInputPage");  //$NON-NLS-1$
            mProject = project;
        }

        public void createControl(Composite parent) {
            Composite composite = new Composite(parent, SWT.NONE);
            composite.setLayout(new GridLayout(2, false));

            Label typeLabel = new Label(composite, SWT.NONE);
            typeLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
            typeLabel.setText("Type of Container:");

            mTypeCombo = new Combo(composite, SWT.READ_ONLY);
            mTypeCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
            SelectionAdapter selectionListener = new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e) {
                    validatePage();
                }
            };
            mTypeCombo.addSelectionListener(selectionListener);

            Label idLabel = new Label(composite, SWT.NONE);
            idLabel.setText("New Layout Id:");
            idLabel.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));

            mIdText = new Text(composite, SWT.BORDER);
            mIdText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
            mIdText.addModifyListener(new ModifyListener() {
                public void modifyText(ModifyEvent e) {
                    validatePage();
                }
            });

            mUpdateReferences = new Button(composite, SWT.CHECK);
            mUpdateReferences.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER,
                    false, false, 2, 1));
            mUpdateReferences.setSelection(true);
            mUpdateReferences.setText("Update layout references");
            mUpdateReferences.addSelectionListener(selectionListener);

            addLayouts(mProject, mTypeCombo, null);

            setControl(composite);
            validatePage();

            mTypeCombo.setFocus();
        }

        private boolean validatePage() {
            boolean ok = true;

            String id = mIdText.getText().trim();

            if (id.length() == 0) {
                // It's okay to not define a title...
                // ...unless you want to update references
                if (mUpdateReferences.getSelection()) {
                    setErrorMessage("ID required when updating layout references");
                    ok = false;
                }
            } else {
                // ...but if you do, it has to be valid!
                ResourceNameValidator validator = ResourceNameValidator.create(false, mProject,
                        ResourceType.ID);
                String message = validator.isValid(id);
                if (message != null) {
                    setErrorMessage(message);
                    ok = false;
                }
            }

            if (mTypeCombo.getText().equals(SEPARATOR_LABEL)) {
                setErrorMessage("Select a container type");
                ok = false;
            }

            if (ok) {
                setErrorMessage(null);

                // Record state
                WrapInRefactoring refactoring =
                    (WrapInRefactoring) getRefactoring();
                refactoring.setId(id);
                refactoring.setType(mTypeCombo.getText());
                refactoring.setUpdateReferences(mUpdateReferences.getSelection());
            }

            setPageComplete(ok);
            return ok;
        }
    }

    static final String SEPARATOR_LABEL =
        "----------------------------------------"; //$NON-NLS-1$

    static void addLayouts(IProject project, Combo combo, String exclude) {
        // Populate type combo
        // TODO 1: Include 3rd party add-ons
        // TODO 2: Include custom layouts in the project
        // TODO 3: Only display the basename, and keep track of the full class names
        //    here and associate them back when initializing the type for the user
        Sdk currentSdk = Sdk.getCurrent();
        if (currentSdk != null) {
            int initialIndex = 0;
            IAndroidTarget target = currentSdk.getTarget(project);
            if (target != null) {
                AndroidTargetData targetData = currentSdk.getTargetData(target);
                if (targetData != null) {
                    ViewMetadataRepository repository = ViewMetadataRepository.get();
                    List<Pair<String,List<ViewElementDescriptor>>> entries =
                        repository.getPaletteEntries(targetData, false, true);
                    // Find the layout category - it contains LinearLayout
                    List<ViewElementDescriptor> layoutDescriptors = null;

                    search: for (Pair<String,List<ViewElementDescriptor>> pair : entries) {
                        List<ViewElementDescriptor> list = pair.getSecond();
                        for (ViewElementDescriptor d : list) {
                            if (d.getFullClassName().equals(FQCN_LINEAR_LAYOUT)) {
                                // Found - use this list
                                layoutDescriptors = list;
                                break search;
                            }
                        }
                    }
                    if (layoutDescriptors != null) {
                        for (ViewElementDescriptor d : layoutDescriptors) {
                            String className = d.getFullClassName();
                            if (exclude == null || !exclude.equals(className)) {
                                combo.add(className);
                            }
                        }

                        // SWT does not support separators in combo boxes
                        combo.add(SEPARATOR_LABEL);
                    }

                    // Now add ALL known layout descriptors in case the user has
                    // a special case
                    layoutDescriptors =
                        targetData.getLayoutDescriptors().getLayoutDescriptors();

                    for (ViewElementDescriptor d : layoutDescriptors) {
                        String className = d.getFullClassName();
                        if (exclude == null || !exclude.equals(className)) {
                            combo.add(className);
                        }
                    }
                }
            }
            combo.select(initialIndex);
        } else {
            combo.add("SDK not initialized");
            combo.setEnabled(false);
        }
    }
}
