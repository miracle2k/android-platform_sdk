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

import junit.framework.TestCase;

public class RenderLoggerTest extends TestCase {
    public void testLogger1() throws Exception {
        RenderLogger l = new RenderLogger("foo");
        assertFalse(l.hasProblems());
    }

    public void testLogger2() throws Exception {
        RenderLogger l = new RenderLogger("foo");
        assertFalse(l.hasProblems());
        l.fidelityWarning(null, "No perspective Transforms", null);
        l.fidelityWarning(null, "No GPS", null);
        assertTrue(l.hasProblems());
        assertEquals("The graphics preview may not be accurate:\n"
                + "* No perspective Transforms\n" + "* No GPS\n", l.getProblems());
        assertFalse(l.seenTag("foo"));
        assertFalse(l.seenTag(null));
    }

    public void testLogger3() throws Exception {
        RenderLogger l = new RenderLogger("foo");
        assertFalse(l.hasProblems());
        l.error("timeout", "Sample Error", new RuntimeException());
        l.warning("slow", "Sample warning");
        assertTrue(l.hasProblems());
        assertEquals("Sample Error\n" + "Sample warning\n"
                + "Exception details are logged in Window > Show View > Error Log", l.getProblems());
        assertFalse(l.seenTag("foo"));
        assertTrue(l.seenTag("timeout"));
        assertTrue(l.seenTag("slow"));
        assertFalse(l.seenTagPrefix("foo"));
        assertTrue(l.seenTagPrefix("timeout"));
        assertTrue(l.seenTagPrefix("slow"));
        assertTrue(l.seenTagPrefix("time"));
        assertFalse(l.seenTagPrefix("timeouts"));
    }
}
