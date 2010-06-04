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

package com.android.ide.eclipse.adt.internal.editors.layout.gle2;

import com.android.ide.eclipse.adt.internal.editors.layout.gre.MockNodeProxy;
import com.android.ide.eclipse.adt.internal.editors.layout.gre.NodeFactory;
import com.android.ide.eclipse.adt.internal.editors.layout.gre.NodeProxy;

import org.eclipse.swt.graphics.Rectangle;

import java.util.Arrays;

import junit.framework.TestCase;

public class SimpleElementTest extends TestCase {

    private SimpleElement e;
    private NodeProxy mNode;
    private NodeFactory mFactory;

    /**
     * Helper method to compare arrays' *content* is equal (instead of object identity).
     * Also produces a suitable output to understand mismatch, if any.
     * <p/>
     * Pre-requisite: The arrays' elements must properly implement {@link Object#equals(Object)}
     * and a sensible {@link Object#toString()}.
     */
    private static void assertArrayEquals(Object[] expected, Object[] actual) {
        if (!Arrays.equals(expected, actual)) {
            // In case of failure, transform the arguments into strings and let
            // assertEquals(string) handle it as it can produce a nice diff of the string.
            String strExpected = expected == null ? "(null)" : Arrays.toString(expected);
            String strActual = actual == null ? "(null)" : Arrays.toString(actual);

            if (strExpected.equals(strActual)) {
                fail(String.format("Array not equal:\n Expected[%d]=%s\n Actual[%d]=%s",
                        expected == null ? 0 : expected.length,
                        strExpected,
                        actual == null ? 0 : actual.length,
                        strActual));
            } else {
                assertEquals(strExpected, strActual);
            }
        }
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mFactory = new NodeFactory();
        mNode = new MockNodeProxy(
                "android.view.LinearLayout",
                new Rectangle(10, 15, 50, 40),
                mFactory);
        e = new SimpleElement("android.view.LinearLayout", mNode);
    }

    public final void testGetFqcn() {
        assertEquals("android.view.LinearLayout", e.getFqcn());
    }

    public final void testGetNode() {
        assertSame(mNode, e.getNode());
    }

    public final void testToString() {
        assertEquals("{1,android.view.LinearLayout\n" +
                     "}\n",
                e.toString());

        e.addAttribute(new SimpleAttribute("uri", "name", "value"));
        e.addAttribute(new SimpleAttribute("my-uri", "second-name", "my = value "));

        assertEquals("{1,android.view.LinearLayout\n" +
                "@name:uri=value\n" +
                "@second-name:my-uri=my = value \n" +
                "}\n",
           e.toString());


        SimpleElement se2 = new SimpleElement("android.view.Button", mNode);
        se2.addAttribute(new SimpleAttribute("uri1", "name1", "value1"));
        SimpleElement se3 = new SimpleElement("android.view.CheckBox", mNode);
        se3.addAttribute(new SimpleAttribute("uri2", "name2", "value2"));
        se3.addAttribute(new SimpleAttribute("uri3", "name3", "value3"));
        e.addInnerElement(se2);
        e.addInnerElement(se3);

        assertEquals("{1,android.view.LinearLayout\n" +
                "@name:uri=value\n" +
                "@second-name:my-uri=my = value \n" +
                "{1,android.view.Button\n" +
                "@name1:uri1=value1\n" +
                "}\n" +
                "{1,android.view.CheckBox\n" +
                "@name2:uri2=value2\n" +
                "@name3:uri3=value3\n" +
                "}\n" +
                "}\n",
           e.toString());
    }

    public final void testParseString() {
        // Note: SimpleElements that are restored from a string do NOT have
        // a valid NodeProxy associated (i.e. SimpleElement.getNode() is null).
        // So we can't compare the "e" element created in setUp().

        SimpleElement se = new SimpleElement("android.view.LinearLayout", null);

        assertArrayEquals(
            new SimpleElement[] { se },
            SimpleElement.parseString(
                "{1,android.view.LinearLayout\n" +
                "}\n"));


        se.addAttribute(new SimpleAttribute("uri", "name", "value"));
        se.addAttribute(new SimpleAttribute("my-uri", "second-name", "my = value "));

        assertArrayEquals(
                new SimpleElement[] { se },
                SimpleElement.parseString(
                        "{1,android.view.LinearLayout\n" +
                        "@name:uri=value\n" +
                        "@second-name:my-uri=my = value \n" +
                        "}\n"));


        SimpleElement se2 = new SimpleElement("android.view.Button", null);
        se2.addAttribute(new SimpleAttribute("uri1", "name1", "value1"));
        SimpleElement se3 = new SimpleElement("android.view.CheckBox", null);
        se3.addAttribute(new SimpleAttribute("uri2", "name2", "value2"));
        se3.addAttribute(new SimpleAttribute("uri3", "name3", "value3"));
        se.addInnerElement(se2);
        se.addInnerElement(se3);

        assertArrayEquals(
                new SimpleElement[] { se },
                SimpleElement.parseString(
                        "{1,android.view.LinearLayout\n" +
                        "@name:uri=value\n" +
                        "@second-name:my-uri=my = value \n" +
                        "{1,android.view.Button\n" +
                        "@name1:uri1=value1\n" +
                        "}\n" +
                        "{1,android.view.CheckBox\n" +
                        "@name2:uri2=value2\n" +
                        "@name3:uri3=value3\n" +
                        "}\n" +
                        "}\n"));

        // Parse string can also parse an array of elements
        assertArrayEquals(
                new SimpleElement[] { se, se2, se3 },
                SimpleElement.parseString(
                        "{1,android.view.LinearLayout\n" +
                        "@name:uri=value\n" +
                        "@second-name:my-uri=my = value \n" +
                        "{1,android.view.Button\n" +
                        "@name1:uri1=value1\n" +
                        "}\n" +
                        "{1,android.view.CheckBox\n" +
                        "@name2:uri2=value2\n" +
                        "@name3:uri3=value3\n" +
                        "}\n" +
                        "}\n" +
                        "{1,android.view.Button\n" +
                        "@name1:uri1=value1\n" +
                        "}\n" +
                        "{1,android.view.CheckBox\n" +
                        "@name2:uri2=value2\n" +
                        "@name3:uri3=value3\n" +
                        "}\n"));

    }

    public final void testAddGetAttribute() {
        assertNotNull(e.getAttributes());
        assertArrayEquals(
                new SimpleAttribute[] {},
                e.getAttributes());

        e.addAttribute(new SimpleAttribute("uri", "name", "value"));
        assertArrayEquals(
                new SimpleAttribute[] { new SimpleAttribute("uri", "name", "value") },
                e.getAttributes());

        e.addAttribute(new SimpleAttribute("my-uri", "second-name", "value"));
        assertArrayEquals(
                new SimpleAttribute[] { new SimpleAttribute("uri", "name", "value"),
                                        new SimpleAttribute("my-uri", "second-name", "value") },
                e.getAttributes());

        assertNull(e.getAttribute("unknown uri", "name"));
        assertNull(e.getAttribute("uri", "unknown name"));
        assertEquals(new SimpleAttribute("uri", "name", "value"),
                     e.getAttribute("uri", "name"));
        assertEquals(new SimpleAttribute("my-uri", "second-name", "value"),
                     e.getAttribute("my-uri", "second-name"));
    }

    public final void testAddGetInnerElements() {
        assertNotNull(e.getInnerElements());
        assertArrayEquals(
                new SimpleElement[] {},
                e.getInnerElements());

        e.addInnerElement(new SimpleElement("android.view.Button", mNode));
        assertArrayEquals(
                new SimpleElement[] { new SimpleElement("android.view.Button", mNode) },
                e.getInnerElements());

        e.addInnerElement(new SimpleElement("android.view.CheckBox", mNode));
        assertArrayEquals(
                new SimpleElement[] { new SimpleElement("android.view.Button", mNode),
                                      new SimpleElement("android.view.CheckBox", mNode) },
                e.getInnerElements());
    }

    public final void testEqualsObject() {
        assertFalse(e.equals(null));
        assertFalse(e.equals(new Object()));

        assertNotSame(new SimpleElement("android.view.LinearLayout", mNode), e);
        assertEquals(new SimpleElement("android.view.LinearLayout", mNode), e);
        assertTrue(e.equals(new SimpleElement("android.view.LinearLayout", mNode)));

        // not the same FQCN
        assertFalse(e.equals(new SimpleElement("android.view.Button", mNode)));
        // not the same node FQCN or bounds
        assertFalse(e.equals(new SimpleElement("android.view.LinearLayout",
                                                new MockNodeProxy(
                                                        "android.view.LinearLayout",
                                                        new Rectangle(100, 150, 500, 400),
                                                        mFactory))));
        assertFalse(e.equals(new SimpleElement("android.view.LinearLayout",
                                                new MockNodeProxy(
                                                        "android.view.Button",
                                                        new Rectangle(10, 15, 50, 40),
                                                        mFactory))));

    }

    public final void testHashCode() {
        int he = e.hashCode();

        assertEquals(he, new SimpleElement("android.view.LinearLayout", mNode).hashCode());

        // not the same FQCN
        assertFalse(he == new SimpleElement("android.view.Button", mNode).hashCode());
        // not the same node FQCN or bounds
        assertFalse(he == new SimpleElement("android.view.LinearLayout",
                                            new MockNodeProxy(
                                                    "android.view.LinearLayout",
                                                    new Rectangle(100, 150, 500, 400),
                                                    mFactory)).hashCode());
        assertFalse(he == new SimpleElement("android.view.LinearLayout",
                                            new MockNodeProxy(
                                                    "android.view.Button",
                                                    new Rectangle(10, 15, 50, 40),
                                                    mFactory)).hashCode());

    }

}
