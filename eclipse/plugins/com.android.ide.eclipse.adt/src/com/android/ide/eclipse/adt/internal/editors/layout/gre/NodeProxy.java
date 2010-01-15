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
import com.android.ide.eclipse.adt.editors.layout.gscripts.INodeProxy;
import com.android.ide.eclipse.adt.editors.layout.gscripts.Rect;
import com.android.ide.eclipse.adt.internal.editors.AndroidEditor;
import com.android.ide.eclipse.adt.internal.editors.descriptors.DescriptorsUtils;
import com.android.ide.eclipse.adt.internal.editors.descriptors.DocumentDescriptor;
import com.android.ide.eclipse.adt.internal.editors.descriptors.ElementDescriptor;
import com.android.ide.eclipse.adt.internal.editors.layout.LayoutEditor;
import com.android.ide.eclipse.adt.internal.editors.layout.descriptors.LayoutDescriptors;
import com.android.ide.eclipse.adt.internal.editors.layout.descriptors.ViewElementDescriptor;
import com.android.ide.eclipse.adt.internal.editors.layout.uimodel.UiViewElementNode;
import com.android.ide.eclipse.adt.internal.editors.uimodel.UiAttributeNode;
import com.android.ide.eclipse.adt.internal.editors.uimodel.UiElementNode;
import com.android.ide.eclipse.adt.internal.sdk.AndroidTargetData;
import com.android.sdklib.SdkConstants;

import org.eclipse.swt.graphics.Rectangle;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.util.HashSet;
import java.util.Set;

import groovy.lang.Closure;

/**
 *
 */
public class NodeProxy implements INodeProxy {

    private final UiViewElementNode mNode;
    private final Rect mBounds;
    private boolean mXmlEditOK;

    /**
     * Creates a new {@link INodeProxy} that wraps an {@link UiViewElementNode} that is
     * actually valid in the current UI/XML model. The view may not be part of the canvas
     * yet (e.g. if it has just been dynamically added and the canvas hasn't reloaded yet.)
     *
     * @param node The node to wrap.
     * @param bounds The bounds of a the view in the canvas. Must be a valid rect for a view
     *   that is actually in the canvas and must be null (or an invalid rect) for a view
     *   that has just been added dynamically to the model.
     */
    public NodeProxy(UiViewElementNode node, Rectangle bounds) {
        mNode = node;
        if (bounds == null) {
            mBounds = new Rect();
        } else {
            mBounds = new Rect(bounds.x, bounds.y, bounds.width, bounds.height);
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

    /* package */ UiViewElementNode getNode() {
        return mNode;
    }

    // ---- XML Editing ---

    public void editXml(String undoName, final Closure c) {
        if (mXmlEditOK) {
            throw new RuntimeException("Error: nested calls to INodeProxy.editXml!");
        }
        try {
            mXmlEditOK = true;

            final AndroidEditor editor = mNode.getEditor();

            if (editor instanceof LayoutEditor) {
                // Create an undo wrapper, which takes a runnable
                ((LayoutEditor) editor).wrapUndoRecording(
                        undoName,
                        new Runnable() {
                            public void run() {
                                // Create an edit-XML wrapper, which takes a runnable
                                editor.editXmlModel(new Runnable() {
                                    public void run() {
                                        // Finally execute the closure that will act on the XML
                                        c.call(this);
                                    }
                                });
                            }
                        });
            }
        } finally {
            mXmlEditOK = false;
        }
    }

    private void checkEditOK() {
        if (!mXmlEditOK) {
            throw new RuntimeException("Error: XML edit call without using INodeProxy.editXml!");
        }
    }

    public INodeProxy createChild(String viewFqcn) {
        checkEditOK();

        // Find the descriptor for this FQCN
        ViewElementDescriptor vd = getFqcnViewDescritor(viewFqcn);
        if (vd == null) {
            debugPrintf("Can't create a new %s element", viewFqcn);
            return null;
        }

        // TODO use UiElementNode.insertNewUiChild() to control the position, which is
        // needed for a relative layout.
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

        return new NodeProxy((UiViewElementNode) uiNew, null);
    }

    public boolean setAttribute(String attributeName, String value) {
        checkEditOK();

        UiAttributeNode attr = mNode.setAttributeValue(attributeName, value, true /* override */);
        mNode.commitDirtyAttributesToXml();

        return attr != null;
    }


    // --- internal helpers ---

    /**
     * Returns a given XML attribute.
     * @param attrName The local name of the attribute.
     * @return the attribute as a {@link String}, if it exists, or <code>null</code>
     */
    private String getStringAttr(String attrName) {
        // TODO this was just copy-pasted from the GLE1 edit code. Need to adapt to this context.
        UiElementNode uiNode = mNode;
        if (uiNode.getXmlNode() != null) {
            Node xmlNode = uiNode.getXmlNode();
            if (xmlNode != null) {
                NamedNodeMap nodeAttributes = xmlNode.getAttributes();
                if (nodeAttributes != null) {
                    Node attr = nodeAttributes.getNamedItemNS(SdkConstants.NS_RESOURCES, attrName);
                    if (attr != null) {
                        return attr.getNodeValue();
                    }
                }
            }
        }
        return null;
    }


    /**
     * Helper methods that returns a {@link ViewElementDescriptor} for the requested FQCN.
     * Will return null if we can't find that FQCN or we lack the editor/data/descriptors info
     * (which shouldn't really happen since at this point the SDK should be fully loaded and
     * isn't reloading, or we wouldn't be here editing XML for a groovy script.)
     */
    private ViewElementDescriptor getFqcnViewDescritor(String fqcn) {
        AndroidEditor editor = mNode.getEditor();
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
