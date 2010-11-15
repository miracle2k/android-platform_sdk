/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.ide.common.api;

/**
 * Information about an attribute as gathered from the attrs.xml file where
 * the attribute was declared. This must include a format (string, reference, float, etc.),
 * possible flag or enum values, whether it's deprecated and its javadoc.
 * <p>
 * <b>NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.</b>
 * </p>
 */
public interface IAttributeInfo {

    /** An attribute format, e.g. string, reference, float, etc. */
    public enum Format {
        STRING,
        BOOLEAN,
        INTEGER,
        FLOAT,
        REFERENCE,
        COLOR,
        DIMENSION,
        FRACTION,
        ENUM,
        FLAG;

        /**
         * Returns true if and only if this format is in the given array of
         * formats
         *
         * @param formats An array of formats, or null.
         * @return True if and only if the given array (if any) contains this
         *         format.
         */
        public boolean in(Format[] formats) {
            if (formats == null) {
                return false;
            }
            for (Format f : formats) {
                if (f == this) {
                    return true;
                }
            }

            return false;
        }
    }

    /** Returns the XML Name of the attribute */
    public String getName();

    /** Returns the formats of the attribute. Cannot be null.
     *  Should have at least one format. */
    public Format[] getFormats();

    /** Returns the values for enums. null for other types. */
    public String[] getEnumValues();

    /** Returns the values for flags. null for other types. */
    public String[] getFlagValues();

    /** Returns a short javadoc, .i.e. the first sentence. */
    public String getJavaDoc();

    /** Returns the documentation for deprecated attributes. Null if not deprecated. */
    public String getDeprecatedDoc();

}
