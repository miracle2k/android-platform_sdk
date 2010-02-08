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
     * that have just been created by the {@link #createChild(String)} method.
     *
     * @return A non-null rectangle, in canvas coordinates.
     */
    Rect getBounds();

    // ---- XML Editing ---

    /**
     * Absolutely <em>all</em> calls that are going to edit the XML must be wrapped
     * by an editXml() call. This call creates both an undo context wrapper and an
     * edit-XML wrapper.
     *
     * @param undoName The UI name that will be given to the undo action.
     * @param closure The code to execute.
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
    INode createChild(String viewFqcn);

    /**
     * Sets an attribute for the underlying XML element.
     * Attributes are not written immediately -- instead the XML editor batches edits and
     * then commits them all together at once later.
     * <p/>
     * The attribute will only be set if the underlying element's descriptor is aware of
     * this attribute.
     * <p/>
     * This call must be done in the context of editXml().
     *
     * @param attributeName The XML local name of the attribute to set.
     * @param value It's value. Cannot be null.
     * @return Whether the attribute was actually set or not.
     */
    boolean setAttribute(String attributeName, String value);



    // -----------

    /** TODO: this is a hack. Shouldn't be here but instead part of some kind of helper
     *  given to IViewRule implementations.
     */
    void debugPrintf(String msg, Object...params);
}
