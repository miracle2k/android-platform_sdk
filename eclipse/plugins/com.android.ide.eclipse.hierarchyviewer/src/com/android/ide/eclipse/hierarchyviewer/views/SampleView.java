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

package com.android.ide.eclipse.hierarchyviewer.views;

import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.core.runtime.jobs.Job;

/**
 * This sample class demonstrates how to plug-in a new workbench view. The view
 * shows data obtained from the model. The sample creates a dummy model on the
 * fly, but a real implementation would connect to the model available either in
 * this or another plug-in (e.g. the workspace). The view is connected to the
 * model using a content provider.
 * <p>
 * The view uses a label provider to define how model objects should be
 * presented in the view. Each view can present the same model objects using
 * different labels and icons, if needed. Alternatively, a single label provider
 * can be shared between views in order to ensure that objects of the same type
 * are presented in the same way everywhere.
 * <p>
 */

public class SampleView extends ViewPart {

    /**
     * The ID of the view as specified by the extension.
     */
    public static final String ID = "com.android.ide.eclipse.hierarchyviewer.views.SampleView";

    private Text text;

    /**
     * The constructor.
     */
    public SampleView() {
    }

    /**
     * This is a callback that will allow us to create the viewer and initialize
     * it.
     */
    public void createPartControl(Composite parent) {
        parent.setLayout(new FillLayout());
        text = new Text(parent, SWT.MULTI | SWT.READ_ONLY | SWT.V_SCROLL);
        text.addMouseListener(new MouseListener() {

            public void mouseDoubleClick(MouseEvent e) {
                text.setText(text.getText() + "Scheduling\n");
                Job test = new Job("Simple Job") {
                    @Override
                    protected IStatus run(IProgressMonitor monitor) {
                        try {
                            Display.getDefault().syncExec(new Runnable() {
                                public void run() {
                                    text.setText(text.getText() + "SLEEPING\n");
                                }
                            });
                            Thread.sleep(5000);
                            Display.getDefault().syncExec(new Runnable() {
                                public void run() {
                                    text.setText(text.getText() + "DONE SLEEPING\n");
                                }
                            });
                        } catch (InterruptedException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                        return Status.OK_STATUS;
                    }

                };
                test.schedule();
                text.setText(text.getText() + "Done Scheduling\n");
            }

            public void mouseDown(MouseEvent e) {
                // TODO Auto-generated method stub

            }

            public void mouseUp(MouseEvent e) {
                // TODO Auto-generated method stub

            }

        });
    }

    /**
     * Passing the focus request to the viewer's control.
     */
    public void setFocus() {
        text.setFocus();
    }
}
