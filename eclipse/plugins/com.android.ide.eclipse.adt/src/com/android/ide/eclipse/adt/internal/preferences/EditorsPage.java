/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.ide.eclipse.adt.internal.preferences;

import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.sdkuilib.internal.widgets.ResolutionChooserDialog;

import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.StringButtonFieldEditor;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

/**
 * Preference page for the editors.
 */
public class EditorsPage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

    public EditorsPage() {
        super(GRID);
        setPreferenceStore(AdtPlugin.getDefault().getPreferenceStore());
    }

    public void init(IWorkbench workbench) {
        // pass
    }

    @Override
    protected void createFieldEditors() {
        addField(new DensityFieldEditor(AdtPrefs.PREFS_MONITOR_DENSITY,
                "Monitor Density", getFieldEditorParent()));
    }

    /**
     * Custom {@link StringButtonFieldEditor} to call out to {@link ResolutionChooserDialog}
     * when the button is called.
     */
    private static class DensityFieldEditor extends StringButtonFieldEditor {

        public DensityFieldEditor(String name, String labelText, Composite parent) {
            super(name, labelText, parent);
            setChangeButtonText("Compute...");
        }

        @Override
        protected String changePressed() {
            ResolutionChooserDialog dialog = new ResolutionChooserDialog(getShell());
            if (dialog.open() == Window.OK) {
                return Integer.toString(dialog.getDensity());
            }

            return null;
        }
    }
}
