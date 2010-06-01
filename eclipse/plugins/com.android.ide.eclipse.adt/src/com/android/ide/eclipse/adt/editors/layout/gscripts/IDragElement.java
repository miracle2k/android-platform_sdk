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

import java.util.List;

/**
 * Represents an XML element with a name, attributes and inner elements.
 * <p/>
 * The semantic of the element name is to be a fully qualified class name of a View to inflate.
 * The element name is not expected to have a namespace.
 */
public interface IDragElement {

    /**
     * Returns the element name, which must match a fully qualified class name of
     * a View to inflate.
     */
    public abstract String getFqcn();

    /**
     * Returns the node matching the element, if it came from an existing canvas
     * in the same Eclipse instance. <br/>
     * The node is null if this is a new element being created or if the element
     * originated on a canvas in a different instance of Eclipse.
     */
    public abstract INode getNode();

    /**
     * Returns a list of attributes. The list can be empty but is never null.
     */
    public abstract List<IDragAttribute> getAttributes();

    /**
     * Returns the requested attribute or null if not found.
     */
    public abstract IDragAttribute getAttribute(String uri, String localName);

    /**
     * Returns a list of inner elements. The list can be empty but is never null.
     */
    public abstract List<IDragElement> getInnerElements();

    /**
     * An XML attribute in the {@link IDragElement}.
     */
    public interface IDragAttribute {

        /** Returns the namespace URI of the attribute. Cannot be null nor empty. */
        public abstract String getUri();

        /** Returns the XML local name of the attribute. Cannot be null nor empty. */
        public abstract String getName();

        /** Returns the value of the attribute. Cannot be null. Can be empty. */
        public abstract String getValue();
    }
}

