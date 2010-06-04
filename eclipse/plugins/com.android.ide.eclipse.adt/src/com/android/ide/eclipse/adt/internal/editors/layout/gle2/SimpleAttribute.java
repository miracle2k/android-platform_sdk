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

import com.android.ide.eclipse.adt.editors.layout.gscripts.IDragElement.IDragAttribute;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents one XML attribute in a {@link SimpleElement}.
 * <p/>
 * The attribute is always represented by a namespace URI, a name and a value, none of which
 * can be null. The name and URI cannot be empty.
 * <p/>
 * For a more detailed explanation of the purpose of this class,
 * please see {@link SimpleXmlTransfer}.
 */
public class SimpleAttribute implements IDragAttribute {
    private final String mName;
    private final String mValue;
    private final String mUri;

    /**
     * Creates a new {@link SimpleAttribute}.
     *
     * @param uri The URI of the attribute. Cannot be null nor empty.
     * @param name The XML local name of the attribute. Cannot be null nor empty.
     * @param value The value of the attribute. Can be empty but not null.
     */
    public SimpleAttribute(String uri, String name, String value) {
        mUri = uri;
        mName = name;
        mValue = value;
    }

    /** Returns the namespace URI of the attribute. Cannot be null nor empty. */
    public String getUri() {
        return mUri;
    }

    /** Returns the XML local name of the attribute. Cannot be null nor empty. */
    public String getName() {
        return mName;
    }

    /** Returns the value of the attribute. Cannot be null. Can be empty. */
    public String getValue() {
        return mValue;
    }

    // reader and writer methods

    @Override
    public String toString() {
        return String.format("@%s:%s=%s\n", mName, mUri, mValue);    //$NON-NLS-1$
    }

    private static final Pattern REGEXP =
        Pattern.compile("[^@]*@([^:]+):([^=]+)=([^\n]*)\n*");       //$NON-NLS-1$

    static SimpleAttribute parseString(String value) {
        Matcher m = REGEXP.matcher(value);
        if (m.matches()) {
            return new SimpleAttribute(m.group(2), m.group(1), m.group(3));
        }

        return null;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof SimpleAttribute) {
            SimpleAttribute sa = (SimpleAttribute) obj;

            return mName.equals(sa.mName) &&
                    mUri.equals(sa.mUri) &&
                    mValue.equals(sa.mValue);
        }
        return false;
    }

    @Override
    public int hashCode() {
        long c = mName.hashCode();
        // uses the formula defined in java.util.List.hashCode()
        c = 31*c + mUri.hashCode();
        c = 31*c + mValue.hashCode();
        if (c > 0x0FFFFFFFFL) {
            // wrap any overflow
            c = c ^ (c >> 32);
        }
        return (int)(c & 0x0FFFFFFFFL);
    }
}
