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
package com.android.ide.eclipse.adt.internal.editors.xml;

import junit.framework.TestCase;

public class XmlHyperlinkResolverTest extends TestCase {
    public void testFqnRegexp() throws Exception {
        assertTrue(XmlHyperlinkResolver.CLASS_PATTERN.matcher("com.android.Foo").matches());
        assertTrue(XmlHyperlinkResolver.CLASS_PATTERN.matcher("com.android.pk_g.Foo_Bar1").
                matches());
        assertTrue(XmlHyperlinkResolver.CLASS_PATTERN.matcher("com.android.Foo$Inner").matches());

        // Should we allow non-standard packages and class names?
        // For now, we're allowing it -- see how this works out in practice.
        //assertFalse(XmlHyperlinkResolver.CLASS_PATTERN.matcher("Foo.bar").matches());
        assertTrue(XmlHyperlinkResolver.CLASS_PATTERN.matcher("Foo.bar").matches());

        assertFalse(XmlHyperlinkResolver.CLASS_PATTERN.matcher("LinearLayout").matches());
        assertFalse(XmlHyperlinkResolver.CLASS_PATTERN.matcher(".").matches());
        assertFalse(XmlHyperlinkResolver.CLASS_PATTERN.matcher(".F").matches());
        assertFalse(XmlHyperlinkResolver.CLASS_PATTERN.matcher("f.").matches());
        assertFalse(XmlHyperlinkResolver.CLASS_PATTERN.matcher("Foo").matches());
        assertFalse(XmlHyperlinkResolver.CLASS_PATTERN.matcher("com.android.1Foo").matches());
        assertFalse(XmlHyperlinkResolver.CLASS_PATTERN.matcher("1com.Foo").matches());
    }
}
