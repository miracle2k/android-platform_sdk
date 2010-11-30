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

import static junit.framework.Assert.fail;

import com.android.ide.common.api.IAttributeInfo;

/** Test/mock implementation of {@link IAttributeInfo} */
public class TestAttributeInfo implements IAttributeInfo {
    private final String mName;

    public TestAttributeInfo(String name) {
        this.mName = name;
    }

    public String getDeprecatedDoc() {
        fail("Not supported yet in tests");
        return null;
    }

    public String[] getEnumValues() {
        fail("Not supported yet in tests");
        return null;
    }

    public String[] getFlagValues() {
        fail("Not supported yet in tests");
        return null;
    }

    public Format[] getFormats() {
        fail("Not supported yet in tests");
        return null;
    }

    public String getJavaDoc() {
        fail("Not supported yet in tests");
        return null;
    }

    public String getName() {
        return mName;
    }
}