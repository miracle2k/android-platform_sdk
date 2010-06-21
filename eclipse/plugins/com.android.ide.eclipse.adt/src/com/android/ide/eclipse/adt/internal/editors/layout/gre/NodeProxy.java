/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.ide.eclipse.adt.internal.editors.layout.gre;

import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.editors.layout.gscripts.IAttributeInfo;
import com.android.ide.eclipse.adt.editors.layout.gscripts.INode;
import com.android.ide.eclipse.adt.editors.layout.gscripts.Rect;
import com.android.ide.eclipse.adt.internal.editors.AndroidXmlEditor;
import com.android.ide.eclipse.adt.internal.editors.descriptors.AttributeDescriptor;
import com.android.ide.eclipse.adt.internal.editors.descriptors.DescriptorsUtils;
import com.android.ide.eclipse.adt.internal.editors.descriptors.DocumentDescriptor;
import com.android.ide.eclipse.adt.internal.editors.descriptors.ElementDescriptor;
import com.android.ide.eclipse.adt.internal.editors.layout.LayoutEditor;
import com.android.ide.eclipse.adt.internal.editors.layout.descriptors.LayoutDescriptors;
import com.android.ide.eclipse.adt.internal.editors.layout.descriptors.ViewElementDescriptor;
import com.android.ide.eclipse.adt.internal.editors.layout.gle2.SimpleAttribute;
import com.android.ide.eclipse.adt.internal.editors.layout.uimodel.UiViewElementNode;
import com.android.ide.eclipse.adt.internal.editors.uimodel.UiAttributeNode;
import com.android.ide.eclipse.adt.internal.editors.uimodel.UiDocumentNode;
import com.android.ide.eclipse.adt.internal.editors.uimodel.UiElementNode;
import com.android.ide.eclipse.adt.internal.sdk.AndroidTargetData;

import org.eclipse.swt.graphics.Rectangle;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import groovy.lang.Closure;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 *
 */
public class NodeProxy implements INode {

    private final UiViewElementNode mNode;
    private final Rect mBounds;
    private final NodeFactory mFactory;

    /**
     * Creates a new {@link INode} that wraps an {@link UiViewElementNode} that is
     * actually valid in the current UI/XML model. The view may not be part of the canvas
     * yet (e.g. if it has just been dynamically added and the canvas hasn't reloaded yet.)
     * <p/>
     * This method is package protected. To create a node, please use {@link NodeFactory} instead.
     *
     * @param uiNode The node to wrap.
     * @param bounds The bounds of a the view in the canvas. Must be either: <br/>
     *   - a valid rect for a view that is actually in the canvas <br/>
     *   - <b>*or*</b> null (or an invalid rect) for a view that has just been added dynamically
     *   to the model. We never store a null bounds rectangle in the node, a null rectangle
     *   will be converted to an invalid rectangle.
     * @param factory A {@link NodeFactory} to create unique children nodes.
     */
    /*package*/ NodeProxy(UiViewElementNode uiNode, Rectangle bounds, NodeFactory factory) {
        mNode = uiNode;
        mFactory = factory;
        if (bounds == null) {
            mBounds = new Rect();
        } else {
            mBounds = new Rect(bounds);
        }
    }

    public void debugPrintf(String msg, Object...params) {
        AdtPlugin.printToConsole(
                mNode == null ? "Groovy" : mNode.getDescriptor().getXmlLocalName() + ".groovy",
                String.format(msg, params)
                );
    }

    public Rect getBounds() {
        return mBounds;
    }


    /**
     * Updates the bounds of this node proxy. Bounds cannot be null, but it can be invalid.
     * This is a package-protected method, only the {@link NodeFactory} uses this method.
     */
    /*package*/ void setBounds(Rectangle bounds) {
        mBounds.set(bounds);
    }

    /* package */ UiViewElementNode getNode() {
        return mNode;
    }


    // ---- Hierarchy handling ----


    public INode getRoot() {
        if (mNode != null) {
            UiElementNode p = mNode.getUiRoot();
            // The node root should be a document. Instead what we really mean to
            // return is the top level view element.
            if (p instanceof UiDocumentNode) {
                List<UiElementNode> children = p.getUiChildren();
                if (children.size() > 0) {
                    p = children.get(0);
                }
            }

            // Cope with a badly structured XML layout
            while (p != null && !(p instanceof UiViewElementNode)) {
                p = p.getUiNextSibling();
            }

            if (p instanceof UiViewElementNode) {
                return mFactory.create((UiViewElementNode) p);
            }
        }

        return null;
    }

    public INode getParent() {
        if (mNode != null) {
            UiElementNode p = mNode.getUiParent();
            if (p instanceof UiViewElementNode) {
                return mFactory.create((UiViewElementNode) p);
            }
        }

        return null;
    }

    public INode[] getChildren() {
        if (mNode != null) {
            ArrayList<INode> nodes = new ArrayList<INode>();
            for (UiElementNode uiChild : mNode.getUiChildren()) {
                if (uiChild instanceof UiViewElementNode) {
                    nodes.add(mFactory.create((UiViewElementNode) uiChild));
                }
            }

            return nodes.toArray(new INode[nodes.size()]);
        }

        return new INode[0];
    }


    // ---- XML Editing ---

    public void editXml(String undoName, final Closure c) {
        final AndroidXmlEditor editor = mNode.getEditor();

        if (editor.isEditXmlModelPending()) {
            throw new RuntimeException("Error: calls to INode.editXml cannot be nested!");
        }

        if (editor instanceof LayoutEditor) {
            // Create an undo wrapper, which takes a runnable
            ((LayoutEditor) editor).wrapUndoRecording(
                    undoName,
                    new Runnable() {
                        public void run() {
                            // Create an edit-XML wrapper, which takes a runnable
                            editor.editXmlModel(new Runnable() {
                                public void run() {
                                    // Here editor.isEditXmlModelPending returns true and it
                                    // is safe to edit the model using any method from INode.

                                    // Finally execute the closure that will act on the XML
                                    c.call(NodeProxy.this);
                                }
                            });
                        }
                    });
        }
    }

    private void checkEditOK() {
        final AndroidXmlEditor editor = mNode.getEditor();
        if (!editor.isEditXmlModelPending()) {
            throw new RuntimeException("Error: XML edit call without using INode.editXml!");
        }
    }

    public INode appendChild(String viewFqcn) {
        checkEditOK();

        // Find the descriptor for this FQCN
        ViewElementDescriptor vd = getFqcnViewDescritor(viewFqcn);
        if (vd == null) {
            debugPrintf("Can't create a new %s element", viewFqcn);
            return null;
        }

        // Append at the end.
        UiElementNode uiNew = mNode.appendNewUiChild(vd);

        // TODO we probably want to defer that to the GRE to use IViewRule#getDefaultAttributes()
        DescriptorsUtils.setDefaultLayoutAttributes(uiNew, false /*updateLayout*/);

        Node xmlNode = uiNew.createXmlNode();

        if (!(uiNew instanceof UiViewElementNode) || xmlNode == null) {
            // Both things are not supposed to happen. When they do, we're in big trouble.
            // We don't really know how to revert the state at this point and the UI model is
            // now out of sync with the XML model.
            // Panic ensues.
            // The best bet is to abort now. The edit wrapper will release the edit and the
            // XML/UI should get reloaded properly (with a likely invalid XML.)
            debugPrintf("Failed to create a new %s element", viewFqcn);
            throw new RuntimeException("XML node creation failed."); //$NON-NLS-1$
        }

        return mFactory.create((UiViewElementNode) uiNew);
    }

    public INode insertChildAt(String viewFqcn, int index) {
        checkEditOK();

        // Find the descriptor for this FQCN
        ViewElementDescriptor vd = getFqcnViewDescritor(viewFqcn);
        if (vd == null) {
            debugPrintf("Can't create a new %s element", viewFqcn);
            return null;
        }

        // Insert at the requested position or at the end.
        int n = mNode.getUiChildren().size();
        UiElementNode uiNew = null;
        if (index < 0 || index >= n) {
            uiNew = mNode.appendNewUiChild(vd);
        } else {
            uiNew = mNode.insertNewUiChild(index, vd);
        }

        // TODO we probably want to defer that to the GRE to use IViewRule#getDefaultAttributes()
        DescriptorsUtils.setDefaultLayoutAttributes(uiNew, false /*updateLayout*/);

        Node xmlNode = uiNew.createXmlNode();

        if (!(uiNew instanceof UiViewElementNode) || xmlNode == null) {
            // Both things are not supposed to happen. When they do, we're in big trouble.
            // We don't really know how to revert the state at this point and the UI model is
            // now out of sync with the XML model.
            // Panic ensues.
            // The best bet is to abort now. The edit wrapper will release the edit and the
            // XML/UI should get reloaded properly (with a likely invalid XML.)
            debugPrintf("Failed to create a new %s element", viewFqcn);
            throw new RuntimeException("XML node creation failed."); //$NON-NLS-1$
        }

        return mFactory.create((UiViewElementNode) uiNew);
    }

    public boolean setAttribute(String uri, String name, String value) {
        checkEditOK();

        UiAttributeNode attr = mNode.setAttributeValue(name, uri, value, true /* override */);
        mNode.commitDirtyAttributesToXml();

        return attr != null;
    }

    public String getStringAttr(String uri, String attrName) {
        UiElementNode uiNode = mNode;

        if (attrName == null) {
            return null;
        }

        if (uiNode.getXmlNode() != null) {
            Node xmlNode = uiNode.getXmlNode();
            if (xmlNode != null) {
                NamedNodeMap nodeAttributes = xmlNode.getAttributes();
                if (nodeAttributes != null) {
                    Node attr = nodeAttributes.getNamedItemNS(uri, attrName);
                    if (attr != null) {
                        return attr.getNodeValue();
                    }
                }
            }
        }
        return null;
    }

    public IAttributeInfo getAttributeInfo(String uri, String attrName) {
        UiElementNode uiNode = mNode;

        if (attrName == null) {
            return null;
        }

        for (AttributeDescriptor desc : uiNode.getAttributeDescriptors()) {
            String dUri = desc.getNamespaceUri();
            String dName = desc.getXmlLocalName();
            if ((uri == null && dUri == null) || (uri != null && uri.equals(dUri))) {
                if (attrName.equals(dName)) {
                    return desc.getAttributeInfo();
                }
            }
        }

        return null;
    }

    public IAttribute[] getAttributes() {
        UiElementNode uiNode = mNode;

        if (uiNode.getXmlNode() != null) {
            Node xmlNode = uiNode.getXmlNode();
            if (xmlNode != null) {
                NamedNodeMap nodeAttributes = xmlNode.getAttributes();
                if (nodeAttributes != null) {

                    int n = nodeAttributes.getLength();
                    IAttribute[] result = new IAttribute[n];
                    for (int i = 0; i < n; i++) {
                        Node attr = nodeAttributes.item(i);
                        String uri = attr.getNamespaceURI();
                        String name = attr.getLocalName();
                        String value = attr.getNodeValue();

                        result[i] = new SimpleAttribute(uri, name, value);
                    }
                    return result;
                }
            }
        }
        return null;

    }


    // --- internal helpers ---

    /**
     * Helper methods that returns a {@link ViewElementDescriptor} for the requested FQCN.
     * Will return null if we can't find that FQCN or we lack the editor/data/descriptors info
     * (which shouldn't really happen since at this point the SDK should be fully loaded and
     * isn't reloading, or we wouldn't be here editing XML for a groovy script.)
     */
    private ViewElementDescriptor getFqcnViewDescritor(String fqcn) {
        AndroidXmlEditor editor = mNode.getEditor();
        if (editor != null) {
            AndroidTargetData data = editor.getTargetData();
            if (data != null) {
                LayoutDescriptors layoutDesc = data.getLayoutDescriptors();
                if (layoutDesc != null) {
                    DocumentDescriptor docDesc = layoutDesc.getDescriptor();
                    if (docDesc != null) {
                        return internalFindFqcnViewDescritor(fqcn, docDesc.getChildren(), null);
                    }
                }
            }
        }

        return null;
    }

    /**
     * Internal helper to recursively search for a {@link ViewElementDescriptor} that matches
     * the requested FQCN.
     *
     * @param fqcn The target View FQCN to find.
     * @param descriptors A list of cildren descriptors to iterate through.
     * @param visited A set we use to remember which descriptors have already been visited,
     *  necessary since the view descriptor hierarchy is cyclic.
     * @return Either a matching {@link ViewElementDescriptor} or null.
     */
    private ViewElementDescriptor internalFindFqcnViewDescritor(String fqcn,
            ElementDescriptor[] descriptors,
            Set<ElementDescriptor> visited) {
        if (visited == null) {
            visited = new HashSet<ElementDescriptor>();
        }

        if (descriptors != null) {
            for (ElementDescriptor desc : descriptors) {
                if (visited.add(desc)) {
                    // Set.add() returns true if this a new element that was added to the set.
                    // That means we haven't visited this descriptor yet.
                    // We want a ViewElementDescriptor with a matching FQCN.
                    if (desc instanceof ViewElementDescriptor &&
                            fqcn.equals(((ViewElementDescriptor) desc).getFullClassName())) {
                        return (ViewElementDescriptor) desc;
                    }

                    // Visit its children
                    ViewElementDescriptor vd =
                        internalFindFqcnViewDescritor(fqcn, desc.getChildren(), visited);
                    if (vd != null) {
                        return vd;
                    }
                }
            }
        }

        return null;
    }

}
