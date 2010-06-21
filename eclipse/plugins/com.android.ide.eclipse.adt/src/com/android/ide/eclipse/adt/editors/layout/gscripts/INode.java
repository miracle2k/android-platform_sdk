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


package com.android.ide.eclipse.adt.editors.layout.gscripts;

import com.android.ide.eclipse.adt.editors.layout.gscripts.IDragElement.IDragAttribute;

import groovy.lang.Closure;


/**
 * Represents a view in the XML layout being edited.
 * Each view or layout maps to exactly one XML node, thus the name.
 * <p/>
 * The primordial characteristic of a node is the fully qualified View class name that
 * it represents (a.k.a FQCN), for example "android.view.View" or "android.widget.Button".
 * <p/>
 * There are 2 kind of nodes:
 * - Nodes matching a view actually rendered in the layout canvas have a valid "bounds"
 *   rectangle that describe their position in pixels in the canvas. <br/>
 * - Nodes created by IViewRule scripts but not yet rendered have an invalid bounds rectangle
 *   since they only exist in the uncommitted XML model and not yet in the rendered View model.
 */
public interface INode {

    /**
     * Returns the bounds of this node.
     * <p/>
     * The bounds are valid when this node maps a view that is already rendered.
     * Typically, if the node is the target of a drag'n'drop operation then you can be
     * guaranteed that its bounds are known and thus are valid.
     * <p/>
     * However the bounds are invalid (e.g. not known yet) for new XML elements
     * that have just been created, e.g. by the {@link #appendChild(String)} method.
     *
     * @return A non-null rectangle, in canvas coordinates.
     */
    Rect getBounds();


    // ---- Hierarchy handling ----

    /**
     * Returns the root element of the view hierarchy. This may be this node if this is
     * the root element. It can also be null when the current node is not yet or no
     * longer attached to the hierarchy.
     */
    INode getRoot();

    /**
     * Returns the parent node of this node, corresponding to the parent view in the layout.
     * The returned parent can be null when the node is the root element, or when the node is
     * not yet or no longer attached to the hierarchy.
     */
    INode getParent();

    /**
     * Returns the list of valid children nodes. The list can be empty but not null.
     */
    INode[] getChildren();


    // ---- XML Editing ---

    /**
     * Absolutely <em>all</em> calls that are going to edit the XML must be wrapped
     * by an editXml() call. This call creates both an undo context wrapper and an
     * edit-XML wrapper.
     *
     * @param undoName The UI name that will be given to the undo action.
     * @param closure The code to execute. The closure receives this INode itself as argument.
     */
    void editXml(String undoName, final Closure closure);

    // TODO define an exception that methods below will throw if editXml() is not wrapping
    // these calls.

    /**
     * Creates a new XML element as a child of this node's XML element.
     * <p/>
     * For this to work, the editor must have a descriptor for the given FQCN.
     * <p/>
     * This call must be done in the context of editXml().
     *
     * @param viewFqcn The FQCN of the element to create. The actual XML local name will
     *  depend on whether this is an Android view or a custom project view.
     * @return The node for the newly created element. Can be null if we failed to create it.
     */
    INode appendChild(String viewFqcn);

    /**
     * Creates a new XML element as a child of this node's XML element and inserts
     * it at the specified position in the children list.
     * <p/>
     * For this to work, the editor must have a descriptor for the given FQCN.
     * <p/>
     * This call must be done in the context of editXml().
     *
     * @param viewFqcn The FQCN of the element to create. The actual XML local name will
     *  depend on whether this is an Android view or a custom project view.
     * @param index Index of the child to insert before. If the index is out of bounds
     *  (less than zero or larger that current last child), appends at the end.
     * @return The node for the newly created element. Can be null if we failed to create it.
     */
    INode insertChildAt(String viewFqcn, int index);

    /**
     * Sets an attribute for the underlying XML element.
     * Attributes are not written immediately -- instead the XML editor batches edits and
     * then commits them all together at once later.
     * <p/>
     * Custom attributes will be created on the fly.
     * <p/>
     * Passing an empty value actually <em>removes</em> an attribute from the XML.
     * <p/>
     * This call must be done in the context of editXml().
     *
     * @param uri The XML namespace URI of the attribute.
     * @param localName The XML <em>local</em> name of the attribute to set.
     * @param value It's value. Cannot be null. An empty value <em>removes</em> the attribute.
     * @return Whether the attribute was actually set or not.
     */
    boolean setAttribute(String uri, String localName, String value);

    /**
     * Returns a given XML attribute.
     * <p/>
     * This looks up an attribute in the <em>current</em> XML source, not the in-memory model.
     * That means that if called in the context of {@link #editXml(String, Closure)}, the value
     * returned here is not affected by {@link #setAttribute(String, String, String)} until
     * the editXml closure is completed and the actual XML is updated.
     *
     * @param uri The XML name-space URI of the attribute.
     * @param attrName The <em>local</em> name of the attribute.
     * @return the attribute as a {@link String}, if it exists, or <code>null</code>.
     */
    String getStringAttr(String uri, String attrName);

    /**
     * Returns the {@link IAttributeInfo} for a given attribute.
     * <p/>
     * The information is useful to determine the format of an attribute (e.g. reference, string,
     * float, enum, flag, etc.) and in the case of enums and flags also gives the possible values.
     * <p/>
     * Note: in Android resources, an enum can only take one of the possible values (e.g.
     * "visibility" can be either "visible" or "none"), whereas a flag can accept one or more
     * value (e.g. "align" can be "center_vertical|center_horizontal".)
     * <p/>
     * Note that this method does not handle custom non-android attributes. It may either
     * return null for these or it may return a synthetic "string" format attribute depending
     * on how the attribute was loaded.
     *
     * @param uri The XML name-space URI of the attribute.
     * @param attrName The <em>local</em> name of the attribute.
     * @return the {@link IAttributeInfo} if the attribute is known, or <code>null</code>.
     */
    public IAttributeInfo getAttributeInfo(String uri, String attrName);


    /**
     * Returns the list of all attributes defined in the XML for this node.
     * <p/>
     * This looks up an attribute in the <em>current</em> XML source, not the in-memory model.
     * That means that if called in the context of {@link #editXml(String, Closure)}, the value
     * returned here is not affected by {@link #setAttribute(String, String, String)} until
     * the editXml closure is completed and the actual XML is updated.
     *
     * @return A non-null possible-empty list of {@link IAttribute}.
     */
    public IAttribute[] getAttributes();

    // -----------

    /** TODO: this is a hack. Shouldn't be here but instead part of some kind of helper
     *  given to IViewRule implementations.
     */
    void debugPrintf(String msg, Object...params);

    // -----------

    /**
     * An XML attribute in an {@link INode}.
     * <p/>
     * The attribute is always represented by a namespace URI, a name and a value.
     * The name cannot be empty.
     * The namespace URI can be empty for an attribute without a namespace but is never null.
     * The value can be empty but cannot be null.
     */
    public static interface IAttribute extends IDragAttribute { }
}
