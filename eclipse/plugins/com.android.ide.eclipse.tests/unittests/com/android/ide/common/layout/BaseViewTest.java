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

package com.android.ide.common.layout;

import java.util.Arrays;
import java.util.Collections;

import junit.framework.TestCase;

public class BaseViewTest extends TestCase {
    public final void testPrettyName() {
        assertEquals(null, BaseView.prettyName(null));
        assertEquals("", BaseView.prettyName(""));
        assertEquals("Foo", BaseView.prettyName("foo"));
        assertEquals("Foo bar", BaseView.prettyName("foo_bar"));
        // TODO: We should check this to capitalize each initial word
        // assertEquals("Foo Bar", BaseView.prettyName("foo_bar"));
        // TODO: We should also handle camelcase properties
        // assertEquals("Foo Bar", BaseView.prettyName("fooBar"));
    }

    public final void testJoin() {
        assertEquals("foo", BaseView.join('|', Arrays.asList("foo")));
        assertEquals("", BaseView.join('|', Collections.<String>emptyList()));
        assertEquals("foo,bar", BaseView.join(',', Arrays.asList("foo", "bar")));
        assertEquals("foo|bar", BaseView.join('|', Arrays.asList("foo", "bar")));
    }
}
