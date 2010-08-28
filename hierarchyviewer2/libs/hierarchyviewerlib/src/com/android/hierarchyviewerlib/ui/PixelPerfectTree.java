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

import com.android.ddmuilib.ImageLoader;
import com.android.hierarchyviewerlib.device.ViewNode;
import com.android.hierarchyviewerlib.models.PixelPerfectModel;
import com.android.hierarchyviewerlib.models.PixelPerfectModel.IImageChangeListener;

import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.TreeSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Tree;

import java.util.List;

public class PixelPerfectTree extends Composite implements IImageChangeListener, SelectionListener {

    private TreeViewer mTreeViewer;

    private Tree mTree;

    private PixelPerfectModel mModel;

    private Image mFolderImage;

    private Image mFileImage;

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
                    return mFolderImage;
                }
                return mFileImage;
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
        mTreeViewer = new TreeViewer(this, SWT.SINGLE);
        mTreeViewer.setAutoExpandLevel(TreeViewer.ALL_LEVELS);

        mTree = mTreeViewer.getTree();
        mTree.addSelectionListener(this);

        loadResources();

        addDisposeListener(mDisposeListener);

        mModel = PixelPerfectModel.getModel();
        ContentProvider contentProvider = new ContentProvider();
        mTreeViewer.setContentProvider(contentProvider);
        mTreeViewer.setLabelProvider(contentProvider);
        mTreeViewer.setInput(mModel);
        mModel.addImageChangeListener(this);

    }

    private void loadResources() {
        ImageLoader loader = ImageLoader.getDdmUiLibLoader();
        mFileImage = loader.loadImage("file.png", Display.getDefault());
        mFolderImage = loader.loadImage("folder.png", Display.getDefault());
    }

    private DisposeListener mDisposeListener = new DisposeListener() {
        public void widgetDisposed(DisposeEvent e) {
            mModel.removeImageChangeListener(PixelPerfectTree.this);
        }
    };

    @Override
    public boolean setFocus() {
        return mTree.setFocus();
    }

    public void imageLoaded() {
        Display.getDefault().syncExec(new Runnable() {
            public void run() {
                mTreeViewer.refresh();
                mTreeViewer.expandAll();
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

    public void treeChanged() {
        imageLoaded();
    }

    public void widgetDefaultSelected(SelectionEvent e) {
        // pass
    }

    public void widgetSelected(SelectionEvent e) {
        // To combat phantom selection...
        if (((TreeSelection) mTreeViewer.getSelection()).isEmpty()) {
            mModel.setSelected(null);
        } else {
            mModel.setSelected((ViewNode) e.item.getData());
        }
    }

    public void zoomChanged() {
        // pass
    }

    public void overlayChanged() {
        // pass
    }

    public void overlayTransparencyChanged() {
        // pass
    }
}
