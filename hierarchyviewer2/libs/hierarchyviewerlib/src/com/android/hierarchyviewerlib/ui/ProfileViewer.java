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

package com.android.hierarchyviewerlib.ui;

import com.android.hierarchyviewerlib.ComponentRegistry;
import com.android.hierarchyviewerlib.models.TreeViewModel;
import com.android.hierarchyviewerlib.models.TreeViewModel.TreeChangeListener;
import com.android.hierarchyviewerlib.ui.util.DrawableViewNode;
import com.android.hierarchyviewerlib.ui.util.TreeColumnResizer;

import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;

import java.text.DecimalFormat;

public class ProfileViewer extends Composite implements TreeChangeListener {
    private TreeViewModel model;

    private TreeViewer treeViewer;

    private Tree tree;

    private DrawableViewNode selectedNode;

    private class ContentProvider implements ITreeContentProvider, ITableLabelProvider {

        public Object[] getChildren(Object parentElement) {
            synchronized (ProfileViewer.this) {
                return new Object[0];
            }
        }

        public Object getParent(Object element) {
            synchronized (ProfileViewer.this) {
                return new Object[0];
            }
        }

        public boolean hasChildren(Object element) {
            synchronized (ProfileViewer.this) {
                return false;
            }
        }

        public Object[] getElements(Object inputElement) {
            synchronized (ProfileViewer.this) {
                if (selectedNode != null && selectedNode.viewNode.measureTime != -1) {
                    return new String[] {
                            "measure", "layout", "draw"
                    };
                }
                return new Object[0];
            }
        }

        public void dispose() {
            // pass
        }

        public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
            // pass
        }

        public Image getColumnImage(Object element, int column) {
            return null;
        }

        public String getColumnText(Object element, int column) {
            synchronized (ProfileViewer.this) {
                if (selectedNode != null) {
                    if (column == 0) {
                        return (String) element;
                    } else if (column == 1) {
                        DecimalFormat formatter = new DecimalFormat("0.000");
                        if(((String)element).equals("measure")) {
                            if (selectedNode.viewNode.measureTime == -1) {
                                return "unknown";
                            }
                            return formatter.format(selectedNode.viewNode.measureTime);
                        } else if (((String) element).equals("layout")) {
                            if (selectedNode.viewNode.layoutTime == -1) {
                                return "unknown";
                            }
                            return formatter.format(selectedNode.viewNode.layoutTime);
                        } else {
                            if (selectedNode.viewNode.drawTime == -1) {
                                return "unknown";
                            }
                            return formatter.format(selectedNode.viewNode.drawTime);
                        }
                    }
                }
                return "";
            }
        }

        public void addListener(ILabelProviderListener listener) {
            // pass
        }

        public boolean isLabelProperty(Object element, String property) {
            // pass
            return false;
        }

        public void removeListener(ILabelProviderListener listener) {
            // pass
        }
    }

    public ProfileViewer(Composite parent) {
        super(parent, SWT.NONE);
        setLayout(new FillLayout());
        treeViewer = new TreeViewer(this, SWT.NONE);

        tree = treeViewer.getTree();
        tree.setLinesVisible(true);
        tree.setHeaderVisible(true);

        TreeColumn operationColumn = new TreeColumn(tree, SWT.NONE);
        operationColumn.setText("Operation");
        TreeColumn durationColumn = new TreeColumn(tree, SWT.NONE);
        durationColumn.setText("Duration (ms)");

        model = ComponentRegistry.getTreeViewModel();
        ContentProvider contentProvider = new ContentProvider();
        treeViewer.setContentProvider(contentProvider);
        treeViewer.setLabelProvider(contentProvider);
        treeViewer.setInput(model);
        model.addTreeChangeListener(this);

        new TreeColumnResizer(this, operationColumn, durationColumn);
    }

    public void selectionChanged() {
        synchronized (this) {
            selectedNode = model.getSelection();
        }
        doRefresh();
    }

    public void treeChanged() {
        synchronized (this) {
            selectedNode = model.getSelection();
        }
        doRefresh();
    }

    public void viewportChanged() {
        // pass
    }

    public void zoomChanged() {
        // pass
    }

    private void doRefresh() {
        Display.getDefault().asyncExec(new Runnable() {
            public void run() {
                treeViewer.refresh();
            }
        });
    }
}
