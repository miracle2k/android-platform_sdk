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
    private Button button;

    private Action action;

    public ActionButton(Composite parent, ImageAction action) {
        this.action = (Action) action;
        if (this.action.getStyle() == Action.AS_CHECK_BOX) {
            button = new Button(parent, SWT.CHECK);
        } else {
            button = new Button(parent, SWT.PUSH);
        }
        button.setText(action.getText());
        button.setImage(action.getImage());
        this.action.addPropertyChangeListener(this);
        button.addSelectionListener(this);
        button.setToolTipText(action.getToolTipText());
        button.setEnabled(this.action.isEnabled());
    }

    public void propertyChange(PropertyChangeEvent e) {
        if (e.getProperty().toUpperCase().equals("ENABLED")) {
            button.setEnabled((Boolean) e.getNewValue());
        } else if (e.getProperty().toUpperCase().equals("CHECKED")) {
            button.setSelection(action.isChecked());
        }
    }

    public void setLayoutData(Object data) {
        button.setLayoutData(data);
    }

    public void widgetDefaultSelected(SelectionEvent e) {
        // pass
    }

    public void widgetSelected(SelectionEvent e) {
        if (action.getStyle() == Action.AS_CHECK_BOX) {
            action.setChecked(button.getSelection());
        }
        action.run();
    }

    public void addSelectionListener(SelectionListener listener) {
        button.addSelectionListener(listener);
    }
}
