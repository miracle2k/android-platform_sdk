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

import java.util.ArrayList;
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


    // ==== XML Creation ====


    /**
     * Returns the default attributes that a new XML element of this type should have
     * when added to an XML layout file. Note that these defaults can be overridden by the
     * specific code performing the insertion.
     *
     * @return A map of attribute:values for a new element of this type. Can be null or empty.
     */
    Map<?, ?> getDefaultAttributes();


    // ==== Drag'n'drop support ====


    /**
     * Called when a drop operation starts, whilst the d'n'd is dragging the cursor over the
     * views. The purpose of the drop operation will be to create a new element.
     * <p/>
     * Drop targets that can't accept child views should always return null, in which case
     * the rule engine will ask the parent view (typically a layout).
     * <p/>
     * Drop targets that can accept child views must return a non-empty list of drop zones,
     * customized to the actual bounds of the target.
     * The drop zones will be visually shown to the user. Once the user releases the mouse
     * in one of the drop zone, the dropAccept/dropFinish methods will be called.
     * <p/>
     * Note that at this stage, the drop operation does not offer a way to know what is going
     * to be dropped. We just know it's a view descriptor, typically from the layout palette,
     * but we don't know which view class yet.
     *
     * @param targetNode The XML view that is currently the target of the drop.
     * @return Null or an empty list if the rule rejects the drop, or a list of usable drop zones.
     */
    ArrayList<DropZone> dropStart(INodeProxy targetNode);

    /**
     * Called after the user selects to drop the given source into one of the drop zones.
     * <p/>
     * This method should use the methods from the {@link INodeProxy} to actually create the
     * new XML matching the source descriptor.
     *
     * @param sourceFqcn The FQCN of the view being dropped.
     * @param targetNode The XML view that is currently the target of the drop.
     * @param selectedZone One of the drop zones returned by {@link #dropStart(INodeProxy)}.
     * @param where The location, in the selected zone, of the drop.
     */
    void dropFinish(
            String sourceFqcn,
            INodeProxy targetNode,
            DropZone selectedZone,
            Point where);
}
