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
 * An {@link BaseViewRule} describes the GLE rules that apply to a given Layout or View object
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
public abstract class BaseViewRule implements IViewRule {

    public boolean onInitialize(String fqcn) {
        // This base rule can handle any class.
        return true;
    }

    public void onDispose() {
        // Nothing to dispose.
    }

    public String getDisplayName() {
        // Default is to not override the selection display name.
        return null;
    }

    public Map<?, ?> getDefaultAttributes() {
        // The base rule does not have any custom default attributes.
        return null;
    }

    public ArrayList<DropZone> dropStart(INodeProxy targetNode) {
        // By default the base view rule does not participate in element creation by drag'n'drop.
        return null;
    }

    public void dropFinish(String sourceFqcn, INodeProxy targetNode,
            DropZone selectedZone, Point where) {
        // Nothing to do, the base rule does not participate in drag'n'drop.
    }

}
