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

package com.android.ide.eclipse.adt.internal.wizards.newxmlfile;

import com.android.ide.eclipse.adt.internal.resources.manager.ResourceFolderType;
import com.android.resources.ResourceType;

import java.util.Collections;

import junit.framework.TestCase;

public class ResourceNameValidatorTest extends TestCase {
    public void testValidator() throws Exception {
        // Valid
        ResourceNameValidator validator = ResourceNameValidator.create(true,
                ResourceFolderType.VALUES);
        assertTrue(validator.isValid("foo") == null);
        assertTrue(validator.isValid("foo.xml") == null);
        assertTrue(validator.isValid("Foo123_$") == null);

        // Invalid
        assertTrue(validator.isValid("") != null);
        assertTrue(validator.isValid(" ") != null);
        assertTrue(validator.isValid("foo.xm") != null);
        assertTrue(validator.isValid("foo bar") != null);
        assertTrue(validator.isValid("1foo") != null);
        assertTrue(validator.isValid("foo%bar") != null);
        assertTrue(ResourceNameValidator.create(true, Collections.singleton("foo"),
                ResourceType.STRING).isValid("foo") != null);

        // Only lowercase chars allowed in file-based resource names
        assertTrue(ResourceNameValidator.create(true, ResourceFolderType.LAYOUT)
                .isValid("Foo123_$") != null);

    }
}
