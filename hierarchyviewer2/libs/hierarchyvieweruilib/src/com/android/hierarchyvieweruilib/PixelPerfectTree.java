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

package com.android.hierarchyvieweruilib;

import com.android.ddmuilib.ImageLoader;
import com.android.hierarchyviewerlib.ComponentRegistry;
import com.android.hierarchyviewerlib.device.ViewNode;
import com.android.hierarchyviewerlib.models.PixelPerfectModel;
import com.android.hierarchyviewerlib.models.PixelPerfectModel.ImageChangeListener;

import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.TreeSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;

import java.util.List;

public class PixelPerfectTree extends Composite implements ImageChangeListener, SelectionListener {

    private TreeViewer treeViewer;

    private Tree tree;

    private PixelPerfectModel model;

    private Image folderImage;

    private Image fileImage;

    private class ContentProvider implements ITreeContentProvider, ILabelProvider {
        public Object[] getChildren(Object element) {
            if (element instanceof ViewNode) {
                List<ViewNode> children = ((ViewNode) element).children;
                return children.toArray(new ViewNode[children.size()]);
            }
            return null;
        }

        public Object getParent(Object element) {
            if (element instanceof ViewNode) {
                return ((ViewNode) element).parent;
            }
            return null;
        }

        public boolean hasChildren(Object element) {
            if (element instanceof ViewNode) {
                return ((ViewNode) element).children.size() != 0;
            }
            return false;
        }

        public Object[] getElements(Object element) {
            if (element instanceof PixelPerfectModel) {
                ViewNode viewNode = ((PixelPerfectModel) element).getViewNode();
                if (viewNode == null) {
                    return new Object[0];
                }
                return new Object[] {
                    viewNode
                };
            }
            return new Object[0];
        }

        public void dispose() {
            // pass
        }

        public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
            // pass
        }

        public Image getImage(Object element) {
            if (element instanceof ViewNode) {
                if (hasChildren(element)) {
                    return folderImage;
                }
                return fileImage;
            }
            return null;
        }

        public String getText(Object element) {
            if (element instanceof ViewNode) {
                return ((ViewNode) element).name;
            }
            return null;
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

    public PixelPerfectTree(Composite parent) {
        super(parent, SWT.NONE);
        setLayout(new FillLayout());
        treeViewer = new TreeViewer(this, SWT.SINGLE);
        treeViewer.setAutoExpandLevel(TreeViewer.ALL_LEVELS);

        tree = treeViewer.getTree();
        TreeColumn col = new TreeColumn(tree, SWT.LEFT);
        col.setText("Name");
        col.pack();
        tree.setHeaderVisible(true);
        tree.addSelectionListener(this);

        loadResources();

        model = ComponentRegistry.getPixelPerfectModel();
        ContentProvider contentProvider = new ContentProvider();
        treeViewer.setContentProvider(contentProvider);
        treeViewer.setLabelProvider(contentProvider);
        treeViewer.setInput(model);
        model.addImageChangeListener(this);

    }

    public void loadResources() {
        ImageLoader loader = ImageLoader.getDdmUiLibLoader();
        fileImage = loader.loadImage("file.png", Display.getDefault());

        folderImage = loader.loadImage("folder.png", Display.getDefault());
    }

    @Override
    public void dispose() {
        super.dispose();
        fileImage.dispose();
        folderImage.dispose();
    }

    @Override
    public boolean setFocus() {
        return tree.setFocus();
    }

    public void imageLoaded() {
        Display.getDefault().asyncExec(new Runnable() {
            public void run() {
                treeViewer.refresh();
                treeViewer.expandAll();
            }
        });
    }

    public void imageChanged() {
        // pass
    }

    public void crosshairMoved() {
        // pass
    }

    public void selectionChanged() {
        // pass
    }

    public void focusChanged() {
        imageLoaded();
    }

    public void widgetDefaultSelected(SelectionEvent e) {
        // pass
    }

    public void widgetSelected(SelectionEvent e) {
        // To combat phantom selection...
        if (((TreeSelection) treeViewer.getSelection()).isEmpty()) {
            model.setSelected(null);
        } else {
            model.setSelected((ViewNode) e.item.getData());
        }
    }

    public void zoomChanged() {
        // pass
    }

}
