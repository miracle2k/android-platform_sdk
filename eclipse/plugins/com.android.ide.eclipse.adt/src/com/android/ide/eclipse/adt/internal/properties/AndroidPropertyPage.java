/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.ide.eclipse.adt.internal.properties;

import com.android.ide.eclipse.adt.internal.project.ProjectState;
import com.android.ide.eclipse.adt.internal.sdk.Sdk;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.internal.project.ProjectProperties;
import com.android.sdkuilib.internal.widgets.SdkTargetSelector;

import org.eclipse.core.resources.IProject;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.ui.IWorkbenchPropertyPage;
import org.eclipse.ui.dialogs.PropertyPage;

/**
 * Property page for "Android" project.
 * This is accessible from the Package Explorer when right clicking a project and choosing
 * "Properties".
 *
 */
public class AndroidPropertyPage extends PropertyPage implements IWorkbenchPropertyPage {

    private IProject mProject;
    private SdkTargetSelector mSelector;
    private Button mIsLibrary;
    // APK-SPLIT: This is not yet supported, so we hide the UI
//    private Button mSplitByDensity;
    private LibraryProperties mLibraryDependencies;

    public AndroidPropertyPage() {
        // pass
    }

    @Override
    protected Control createContents(Composite parent) {
        // get the element (this is not yet valid in the constructor).
        mProject = (IProject)getElement();

        // get the targets from the sdk
        IAndroidTarget[] targets = null;
        if (Sdk.getCurrent() != null) {
            targets = Sdk.getCurrent().getTargets();
        }

        // build the UI.
        Composite top = new Composite(parent, SWT.NONE);
        top.setLayoutData(new GridData(GridData.FILL_BOTH));
        top.setLayout(new GridLayout(1, false));

        Group targetGroup = new Group(top, SWT.NONE);
        targetGroup.setLayoutData(new GridData(GridData.FILL_BOTH));
        targetGroup.setLayout(new GridLayout(1, false));
        targetGroup.setText("Project Build Target");

        mSelector = new SdkTargetSelector(targetGroup, targets);

        Group libraryGroup = new Group(top, SWT.NONE);
        libraryGroup.setLayoutData(new GridData(GridData.FILL_BOTH));
        libraryGroup.setLayout(new GridLayout(1, false));
        libraryGroup.setText("Library");

        mIsLibrary = new Button(libraryGroup, SWT.CHECK);
        mIsLibrary.setText("Is Library");
        mIsLibrary.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                mLibraryDependencies.setEnabled(!mIsLibrary.getSelection());
            }
        });

        mLibraryDependencies = new LibraryProperties(libraryGroup);

        /*
         * APK-SPLIT: This is not yet supported, so we hide the UI
        Group g = new Group(top, SWT.NONE);
        g.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        g.setLayout(new GridLayout(1, false));
        g.setText("APK Generation");

        mSplitByDensity = new Button(g, SWT.CHECK);
        mSplitByDensity.setText("One APK per density");

*/
        // fill the ui
        fillUi();

        // add callbacks
        mSelector.setSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                updateValidity();
            }
        });

        if (mProject.isOpen() == false) {
            // disable the ui.
        }

        return top;
    }

    @Override
    public boolean performOk() {
        Sdk currentSdk = Sdk.getCurrent();
        if (currentSdk != null && mProject.isOpen()) {
            ProjectState state = Sdk.getProjectState(mProject);

            // simply update the project properties. Eclipse will be notified of the file change
            // and will reload it smartly (detecting differences) and updating the ProjectState.
            // See Sdk.mFileListener
            ProjectProperties properties = null;
            boolean mustSaveProp = false;

            IAndroidTarget newTarget = mSelector.getSelected();
            if (newTarget != state.getTarget()) {
                properties = state.getProperties();
                properties.setProperty(ProjectProperties.PROPERTY_TARGET, newTarget.hashString());
                mustSaveProp = true;
            }

            if (mIsLibrary.getSelection() != state.isLibrary()) {
                properties = state.getProperties();
                properties.setProperty(ProjectProperties.PROPERTY_LIBRARY,
                        Boolean.toString(mIsLibrary.getSelection()));
                mustSaveProp = true;
            }

            if (mLibraryDependencies.save(mIsLibrary.getSelection())) {
                mustSaveProp = true;
            }

            // TODO: update ApkSettings.

            if (mustSaveProp) {
                state.saveProperties();
            }
        }

        return true;
    }

    @Override
    protected void performDefaults() {
        fillUi();
        updateValidity();
    }

    private void fillUi() {
        if (Sdk.getCurrent() != null && mProject.isOpen()) {
            ProjectState state = Sdk.getProjectState(mProject);

            // get the target
            IAndroidTarget target = state.getTarget();;
            if (target != null) {
                mSelector.setSelection(target);
            }

            mIsLibrary.setSelection(state.isLibrary());

            mLibraryDependencies.setContent(state);
            mLibraryDependencies.setEnabled(!state.isLibrary());

            /*
             * APK-SPLIT: This is not yet supported, so we hide the UI
            // get the project settings
            ApkSettings settings = currentSdk.getApkSettings(mProject);
            mSplitByDensity.setSelection(settings.isSplitByDpi());
            */
        }

    }

    private void updateValidity() {
        // look for the selection and validate the page if there is a selection
        IAndroidTarget target = mSelector.getSelected();
        setValid(target != null);
    }

}
