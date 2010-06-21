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

import java.util.Map;


/**
 * An {@link IViewRule} describes the GLE rules that apply to a given Layout or View object
 * in the Graphical Layout Editor (GLE).
 * <p/>
 * Such a rule is implemented using a Groovy script located in the
 * com.android.ide.eclipse.adt.internal.editors.layout.gre package or in a
 * projects' /gscript folder for custom views.
 * <p/>
 * The Groovy script must be named using the fully qualified class name of the View or Layout,
 * e.g. "android.widget.LinearLayout.groovy". If the rule engine can't find a groovy script
 * for a given element, it will use the closest matching parent (e.g. View instead of ViewGroup).
 * <p/>
 * Rule instances are stateless. They are created once per View class to handle and are shared
 * across platforms or editor instances. As such, rules methods should never cache editor-specific
 * arguments that they might receive.
 */
public interface IViewRule {

    /**
     * This method is called by the rule engine when the script is first loaded.
     * It gives the rule a chance to initialize itself.
     *
     * @param fqcn The fully qualified class name of the Layout or View that will be managed by
     *   this rule. This can be cached as it will never change for the lifetime of this rule
     *   instance. This may or may not match the script's filename as it may be the fqcn of a
     *   class derived from the one this rule can handle.
     * @return True if this rule can handle the given FQCN. False if the rule can't handle the
     *   given FQCN, in which case the rule engine will find another rule matching a parent clas.
     */
    boolean onInitialize(String fqcn);

    /**
     * This method is called by the rules engine just before the script is unloaded.
     */
    void onDispose();

    /**
     * Returns the class name to display when an element is selected in the GLE.
     * <p/>
     * If null is returned, the GLE will automatically shorten the class name using its
     * own heuristic, which is to keep the first 2 package components and the class name.
     * The class name is the <code>fqcn</code> argument that was given
     * to {@link #onInitialize(String)}.
     *
     * @return Null for the default behavior or a shortened string.
     */
    String getDisplayName();


    // ==== Selection ====

    /**
     * Called by the canvas when a view is being selected.
     * <p/>
     * Before the method is called, the canvas' Graphic Context is initialized
     * with a foreground color already set to the desired selection color, fully
     * opaque and with the default adequate font.
     *
     * @param gc An {@link IGraphics} instance, to perform drawing operations.
     * @param selectedNode The node selected. Never null.
     * @param displayName The name to display, as returned by {@link #getDisplayName()}.
     * @param isMultipleSelection A boolean set to true if more than one element is selected.
     */
    void onSelected(IGraphics gc,
            INode selectedNode,
            String displayName,
            boolean isMultipleSelection);

    /**
     * Called by the canvas when a single child view is being selected.
     * <p/>
     * Note that this is called only for single selections.
     * <p/>
     * This allows a parent to draw stuff around its children, for example to display
     * layout attributes graphically.
     *
     * @param gc An {@link IGraphics} instance, to perform drawing operations.
     * @param parentNode The parent of the node selected. Never null.
     * @param childNode The child node that was selected. Never null.
     */
    void onChildSelected(IGraphics gc,
            INode parentNode,
            INode childNode);


    // ==== XML Creation ====

    /**
     * Returns the default attributes that a new XML element of this type should have
     * when added to an XML layout file. Note that these defaults can be overridden by the
     * specific code performing the insertion.
     *
     * TODO:
     * - added=>created
     * - list tuple(uri, local name, str: value)
     * - gen id
     *
     * @return A map of attribute:values for a new element of this type. Can be null or empty.
     */
    Map<?, ?> getDefaultAttributes();


    // ==== Drag'n'drop support ====

    /**
     * Called when the d'n'd starts dragging over the target node.
     * If interested, returns a DropFeedback passed to onDrop/Move/Leave/Paint.
     * If not interested in drop, return null.
     * Followed by a paint.
     */
    DropFeedback onDropEnter(INode targetNode,
            IDragElement[] elements);

    /**
     * Called after onDropEnter.
     * Returns a DropFeedback passed to onDrop/Move/Leave/Paint (typically same
     * as input one).
     * Returning null will invalidate the drop workflow.
     */
    DropFeedback onDropMove(INode targetNode,
            IDragElement[] elements,
            DropFeedback feedback,
            Point where);

    /**
     * Called when drop leaves the target without actually dropping.
     * <p/>
     * When switching between views, onDropLeave is called on the old node *after* onDropEnter
     * is called after a new node that returned a non-null feedback. The feedback received here
     * is the one given by the previous onDropEnter on the same target.
     * <p/>
     * E.g. call order is:
     * <pre>
     * - onDropEnter(node1) => feedback1
     * <i>...user moves to new view...</i>
     * - onDropEnter(node2) => feedback2
     * - onDropLeave(node1, feedback1)
     * <i>...user leaves canvas...</i>
     * - onDropLeave(node2, feedback2)
     * </pre>
     */
    void onDropLeave(INode targetNode,
            IDragElement[] elements,
            DropFeedback feedback);

    /**
     * Called when drop is released over the target to perform the actual drop.
     */
    void onDropped(INode targetNode,
            IDragElement[] elements,
            DropFeedback feedback,
            Point where);

}
