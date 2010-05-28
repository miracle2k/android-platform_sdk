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
import com.android.ide.eclipse.adt.editors.layout.gscripts.Rect;

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

    private final String mName;
    private final Rect mBounds;
    private final String mParentLayoutFqcn;
    private final ArrayList<IDragAttribute> mAttributes = new ArrayList<IDragAttribute>();
    private final ArrayList<IDragElement> mElements = new ArrayList<IDragElement>();

    /**
     * Creates a new {@link SimpleElement} with the specified element name.
     *
     * @param name A fully qualified class name of a View to inflate.
     */
    public SimpleElement(String name, Rect bounds, String parentLayoutFqcn) {
        mName = name;
        mBounds = bounds == null ? new Rect() : bounds;
        mParentLayoutFqcn = parentLayoutFqcn;
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
    public Rect getBounds() {
        return mBounds;
    }

    /**
     * Returns the FQCN of the parent layout if the element came from an existing
     * canvas. Returns null if this is a new element being created.
     */
    public String getParentLayoutFqcn() {
        return mParentLayoutFqcn;
    }

    public List<IDragAttribute> getAttributes() {
        return Collections.unmodifiableList(mAttributes);
    }

    public List<IDragElement> getInnerElements() {
        return Collections.unmodifiableList(mElements);
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
        sb.append("{$N=").append(mName);
        if (mBounds.isValid()) {
            sb.append("$B=").append(mBounds.x).append(' ')
                           .append(mBounds.y).append(' ')
                           .append(mBounds.w).append(' ')
                           .append(mBounds.h);
        }
        if (mParentLayoutFqcn != null) {
            sb.append("$P=").append(mParentLayoutFqcn);
        }
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
                    // This is the element's opening
                    String name = null;
                    Rect bounds = new Rect();
                    String parentLayout = null;
                    for (String a : s.substring(1).split("\\$")) {  //$NON-NLS-1$
                        a = a.trim();
                        if (a.length() < 2) {
                            continue;
                        }
                        if (a.startsWith("N=")) {                   //$NON-NLS-1$
                            name = a.substring(2);

                        } else if (a.startsWith("P=")) {            //$NON-NLS-1$
                            parentLayout = a.substring(2);

                        } else if (a.startsWith("B=")) {            //$NON-NLS-1$
                            String[] b = a.split(" ");              //$NON-NLS-1$
                            if (b.length == 4) {
                                try {
                                    bounds.x = Integer.parseInt(b[0]);
                                    bounds.y = Integer.parseInt(b[1]);
                                    bounds.w = Integer.parseInt(b[2]);
                                    bounds.h = Integer.parseInt(b[3]);
                                } catch (NumberFormatException ex) {
                                    // ignore
                                }
                            }
                        }
                    }
                    e = new SimpleElement(name, bounds, parentLayout);
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

            return mName.equals(se.mName) &&
                    mBounds.equals(se.mBounds) &&
                    ((mParentLayoutFqcn == null && se.mParentLayoutFqcn == null) ||
                     (mParentLayoutFqcn.equals(mParentLayoutFqcn))) &&
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
        c = 31*c + mBounds.hashCode();
        if (mParentLayoutFqcn != null) {
            c = 31*c + mParentLayoutFqcn.hashCode();
        }
        if (c > 0x0FFFFFFFFL) {
            // wrap any overflow
            c = c ^ (c >> 32);
        }
        return (int)(c & 0x0FFFFFFFFL);
    }
}

