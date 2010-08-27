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
import com.android.hierarchyviewerlib.device.ViewNode;
import com.android.hierarchyviewerlib.device.ViewNode.Property;
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

import java.util.ArrayList;

public class PropertyViewer extends Composite implements TreeChangeListener {
    private TreeViewModel model;

    private TreeViewer treeViewer;

    private Tree tree;

    private DrawableViewNode selectedNode;

    private class ContentProvider implements ITreeContentProvider, ITableLabelProvider {

        public Object[] getChildren(Object parentElement) {
            synchronized (PropertyViewer.this) {
                if (selectedNode != null && parentElement instanceof String) {
                    String category = (String) parentElement;
                    ArrayList<Property> returnValue = new ArrayList<Property>();
                    for (Property property : selectedNode.viewNode.properties) {
                        if (category.equals(ViewNode.MISCELLANIOUS)) {
                            if (property.name.indexOf(':') == -1) {
                                returnValue.add(property);
                            }
                        } else {
                            if (property.name.startsWith(((String) parentElement) + ":")) {
                                returnValue.add(property);
                            }
                        }
                    }
                    return returnValue.toArray(new Property[returnValue.size()]);
                }
                return new Object[0];
            }
        }

        public Object getParent(Object element) {
            synchronized (PropertyViewer.this) {
                if (selectedNode != null && element instanceof Property) {
                    if (selectedNode.viewNode.categories.size() == 0) {
                        return null;
                    }
                    String name = ((Property) element).name;
                    int index = name.indexOf(':');
                    if (index == -1) {
                        return ViewNode.MISCELLANIOUS;
                    }
                    return name.substring(0, index);
                }
                return null;
            }
        }

        public boolean hasChildren(Object element) {
            synchronized (PropertyViewer.this) {
                if (selectedNode != null && element instanceof String) {
                    String category = (String) element;
                    for (String name : selectedNode.viewNode.namedProperties.keySet()) {
                        if (category.equals(ViewNode.MISCELLANIOUS)) {
                            if (name.indexOf(':') == -1) {
                                return true;
                            }
                        } else {
                            if (name.startsWith(((String) element) + ":")) {
                                return true;
                            }
                        }
                    }
                }
                return false;
            }
        }

        public Object[] getElements(Object inputElement) {
            synchronized (PropertyViewer.this) {
                if (selectedNode != null && inputElement instanceof TreeViewModel) {
                    if (selectedNode.viewNode.categories.size() == 0) {
                        return selectedNode.viewNode.properties
                                .toArray(new Property[selectedNode.viewNode.properties.size()]);
                    } else {
                        return selectedNode.viewNode.categories
                                .toArray(new String[selectedNode.viewNode.categories.size()]);
                    }
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
            synchronized (PropertyViewer.this) {
                if (selectedNode != null) {
                    if (element instanceof String && column == 0) {
                        String category = (String) element;
                        return Character.toUpperCase(category.charAt(0)) + category.substring(1);
                    } else if (element instanceof Property) {
                        if (column == 0) {
                            String returnValue = ((Property) element).name;
                            int index = returnValue.indexOf(':');
                            if (index != -1) {
                                return returnValue.substring(index + 1);
                            }
                            return returnValue;
                        } else if (column == 1) {
                            return ((Property) element).value;
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

    public PropertyViewer(Composite parent) {
        super(parent, SWT.NONE);
        setLayout(new FillLayout());
        treeViewer = new TreeViewer(this, SWT.NONE);

        tree = treeViewer.getTree();
        tree.setLinesVisible(true);
        tree.setHeaderVisible(true);

        TreeColumn propertyColumn = new TreeColumn(tree, SWT.NONE);
        propertyColumn.setText("Property");
        TreeColumn valueColumn = new TreeColumn(tree, SWT.NONE);
        valueColumn.setText("Value");

        model = ComponentRegistry.getTreeViewModel();
        ContentProvider contentProvider = new ContentProvider();
        treeViewer.setContentProvider(contentProvider);
        treeViewer.setLabelProvider(contentProvider);
        treeViewer.setInput(model);
        model.addTreeChangeListener(this);

        new TreeColumnResizer(this, propertyColumn, valueColumn);
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
