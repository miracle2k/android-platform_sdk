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

package com.android.hierarchyviewer.util;

import com.android.hierarchyviewerlib.actions.ImageAction;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;

public class ActionButton implements IPropertyChangeListener, SelectionListener {
    private Button mButton;

    private Action mAction;

    public ActionButton(Composite parent, ImageAction action) {
        this.mAction = (Action) action;
        if (this.mAction.getStyle() == Action.AS_CHECK_BOX) {
            mButton = new Button(parent, SWT.CHECK);
        } else {
            mButton = new Button(parent, SWT.PUSH);
        }
        mButton.setText(action.getText());
        mButton.setImage(action.getImage());
        this.mAction.addPropertyChangeListener(this);
        mButton.addSelectionListener(this);
        mButton.setToolTipText(action.getToolTipText());
        mButton.setEnabled(this.mAction.isEnabled());
    }

    public void propertyChange(PropertyChangeEvent e) {
        if (e.getProperty().toUpperCase().equals("ENABLED")) { //$NON-NLS-1$
            mButton.setEnabled((Boolean) e.getNewValue());
        } else if (e.getProperty().toUpperCase().equals("CHECKED")) { //$NON-NLS-1$
            mButton.setSelection(mAction.isChecked());
        }
    }

    public void setLayoutData(Object data) {
        mButton.setLayoutData(data);
    }

    public void widgetDefaultSelected(SelectionEvent e) {
        // pass
    }

    public void widgetSelected(SelectionEvent e) {
        if (mAction.getStyle() == Action.AS_CHECK_BOX) {
            mAction.setChecked(mButton.getSelection());
        }
        mAction.run();
    }

    public void addSelectionListener(SelectionListener listener) {
        mButton.addSelectionListener(listener);
    }
}
