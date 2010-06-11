/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.ide.eclipse.adt.internal.resources;

import com.android.ide.eclipse.adt.editors.layout.gscripts.IAttributeInfo;


/**
 * Information about an attribute as gathered from the attrs.xml file where
 * the attribute was declared. This must include a format (string, reference, float, etc.),
 * possible flag or enum values, whether it's deprecated and its javadoc.
 */
public class AttributeInfo implements IAttributeInfo {
    /** XML Name of the attribute */
    private String mName;

    /** Formats of the attribute. Cannot be null. Should have at least one format. */
    private Format[] mFormats;
    /** Values for enum. null for other types. */
    private String[] mEnumValues;
    /** Values for flag. null for other types. */
    private String[] mFlagValues;
    /** Short javadoc (i.e. the first sentence). */
    private String mJavaDoc;
    /** Documentation for deprecated attributes. Null if not deprecated. */
    private String mDeprecatedDoc;

    /**
     * @param name The XML Name of the attribute
     * @param formats The formats of the attribute. Cannot be null.
     *                Should have at least one format.
     */
    public AttributeInfo(String name, Format[] formats) {
        mName = name;
        mFormats = formats;
    }

    /**
     * @param name The XML Name of the attribute
     * @param formats The formats of the attribute. Cannot be null.
     *                Should have at least one format.
     * @param javadoc Short javadoc (i.e. the first sentence).
     */
    public AttributeInfo(String name, Format[] formats, String javadoc) {
        mName = name;
        mFormats = formats;
        mJavaDoc = javadoc;
    }

    public AttributeInfo(AttributeInfo info) {
        mName = info.mName;
        mFormats = info.mFormats;
        mEnumValues = info.mEnumValues;
        mFlagValues = info.mFlagValues;
        mJavaDoc = info.mJavaDoc;
        mDeprecatedDoc = info.mDeprecatedDoc;
    }

    /** Returns the XML Name of the attribute */
    public String getName() {
        return mName;
    }
    /** Returns the formats of the attribute. Cannot be null.
     *  Should have at least one format. */
    public Format[] getFormats() {
        return mFormats;
    }
    /** Returns the values for enums. null for other types. */
    public String[] getEnumValues() {
        return mEnumValues;
    }
    /** Returns the values for flags. null for other types. */
    public String[] getFlagValues() {
        return mFlagValues;
    }
    /** Returns a short javadoc, .i.e. the first sentence. */
    public String getJavaDoc() {
        return mJavaDoc;
    }
    /** Returns the documentation for deprecated attributes. Null if not deprecated. */
    public String getDeprecatedDoc() {
        return mDeprecatedDoc;
    }

    /** Sets the values for enums. null for other types. */
    public AttributeInfo setEnumValues(String[] values) {
        mEnumValues = values;
        return this;
    }

    /** Sets the values for flags. null for other types. */
    public AttributeInfo setFlagValues(String[] values) {
        mFlagValues = values;
        return this;
    }

    /** Sets a short javadoc, .i.e. the first sentence. */
    public void setJavaDoc(String javaDoc) {
        mJavaDoc = javaDoc;
    }

    /** Sets the documentation for deprecated attributes. Null if not deprecated. */
    public void setDeprecatedDoc(String deprecatedDoc) {
        mDeprecatedDoc = deprecatedDoc;
    }
}
