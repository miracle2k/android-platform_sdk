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

package com.android.ide.eclipse.adt.internal.editors.layout.gle2;

import com.android.ide.eclipse.adt.editors.layout.gscripts.IDragElement;
import com.android.ide.eclipse.adt.editors.layout.gscripts.INode;
import com.android.ide.eclipse.adt.internal.editors.layout.gre.NodeProxy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents an XML element with a name, attributes and inner elements.
 * <p/>
 * The semantic of the element name is to be a fully qualified class name of a View to inflate.
 * The element name is not expected to have a name space.
 * <p/>
 * For a more detailed explanation of the purpose of this class,
 * please see {@link SimpleXmlTransfer}.
 */
class SimpleElement implements IDragElement {

    /** Version number of the internal serialized string format. */
    private static final String FORMAT_VERSION = "1";

    private final String mName;
    private final INode mNode;
    private final ArrayList<IDragAttribute> mAttributes = new ArrayList<IDragAttribute>();
    private final ArrayList<IDragElement> mElements = new ArrayList<IDragElement>();

    private List<IDragAttribute> mReadOnlyAttributes = null;
    private List<IDragElement> mReadOnlyElements = null;

    /**
     * Creates a new {@link SimpleElement} with the specified element name.
     *
     * @param name A fully qualified class name of a View to inflate.
     */
    public SimpleElement(String name, NodeProxy nodeProxy) {
        mName = name;
        mNode = nodeProxy;
    }

    /**
     * Returns the element name, which must match a fully qualified class name of
     * a View to inflate.
     */
    public String getFqcn() {
        return mName;
    }

    /**
     * Returns the bounds of the element, if it came from an existing canvas.
     * The returned rect is invalid and non-nul if this is a new element being created.
     */
    public INode getNode() {
        return mNode;
    }

    public List<IDragAttribute> getAttributes() {
        if (mReadOnlyAttributes == null) {
            mReadOnlyAttributes = Collections.unmodifiableList(mAttributes);
        }
        return mReadOnlyAttributes;
    }

    public IDragAttribute getAttribute(String uri, String localName) {
        for (IDragAttribute attr : mAttributes) {
            if (attr.getUri().equals(uri) && attr.getName().equals(localName)) {
                return attr;
            }
        }

        return null;
    }

    public List<IDragElement> getInnerElements() {
        if (mReadOnlyElements == null) {
            mReadOnlyElements = Collections.unmodifiableList(mElements);
        }
        return mReadOnlyElements;
    }

    public void addAttribute(SimpleAttribute attr) {
        mAttributes.add(attr);
    }

    public void addInnerElement(SimpleElement e) {
        mElements.add(e);
    }

    // reader and writer methods

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        // "1" is the version number of the format.
        sb.append('{').append(FORMAT_VERSION).append(',').append(mName);
        sb.append('\n');
        for (IDragAttribute a : mAttributes) {
            sb.append(a.toString());
        }
        for (IDragElement e : mElements) {
            sb.append(e.toString());
        }
        sb.append("}\n"); //$NON-NLS-1$
        return sb.toString();
    }

    /** Parses a string containing one or more elements. */
    static SimpleElement[] parseString(String value) {
        ArrayList<SimpleElement> elements = new ArrayList<SimpleElement>();
        String[] lines = value.split("\n");
        int[] index = new int[] { 0 };
        SimpleElement element = null;
        while ((element = parseLines(lines, index)) != null) {
            elements.add(element);
        }
        return elements.toArray(new SimpleElement[elements.size()]);
    }

    /**
     * Parses one element from the input lines array, starting at the inOutIndex
     * and updating the inOutIndex to match the next unread line on output.
     */
    private static SimpleElement parseLines(String[] lines, int[] inOutIndex) {
        SimpleElement e = null;
        int index = inOutIndex[0];
        while (index < lines.length) {
            String line = lines[index++];
            String s = line.trim();
            if (s.startsWith("{")) {                                //$NON-NLS-1$
                if (e == null) {
                    // This is the element's opening, it should have
                    // the format "version_number,element_name"
                    String[] s2 = s.substring(1).split(",");        //$NON-NLS-1$
                    if (s2.length == 2 && s2[0].equals(FORMAT_VERSION)) {
                        e = new SimpleElement(s2[1], null);
                    }
                } else {
                    // This is an inner element
                    inOutIndex[0] = index;
                    SimpleElement e2 = SimpleElement.parseLines(lines, inOutIndex);
                    if (e2 != null) {
                        e.addInnerElement(e2);
                    }
                    index = inOutIndex[0];
                }

            } else if (e != null && s.startsWith("@")) {    //$NON-NLS-1$
                SimpleAttribute a = SimpleAttribute.parseString(line);
                if (a != null) {
                    e.addAttribute(a);
                }

            } else if (e != null && s.startsWith("}")) {     //$NON-NLS-1$
                // We're done with this element
                inOutIndex[0] = index;
                return e;
            }
        }
        inOutIndex[0] = index;
        return null;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof SimpleElement) {
            SimpleElement se = (SimpleElement) obj;

            // Note: INode objects should come from the NodeFactory and be unique
            // for a given UiViewNode so it's OK to compare mNode pointers here.

            return mName.equals(se.mName) &&
                    mNode == se.mNode &&
                    mAttributes.size() == se.mAttributes.size() &&
                    mElements.size() == se.mElements.size() &&
                    mAttributes.equals(se.mAttributes) &&
                    mElements.equals(mElements);
        }
        return false;
    }

    @Override
    public int hashCode() {
        long c = mName.hashCode();
        // uses the formula defined in java.util.List.hashCode()
        c = 31*c + mAttributes.hashCode();
        c = 31*c + mElements.hashCode();
        if (mNode != null) {
            c = 31*c + mNode.hashCode();
        }

        if (c > 0x0FFFFFFFFL) {
            // wrap any overflow
            c = c ^ (c >> 32);
        }
        return (int)(c & 0x0FFFFFFFFL);
    }
}

