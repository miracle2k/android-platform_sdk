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

package com.android.ide.eclipse.adt.internal.editors.resources.configurations;

import com.android.ide.eclipse.adt.internal.resources.configurations.FolderConfiguration;
import com.android.ide.eclipse.adt.internal.resources.configurations.ScreenSizeQualifier;
import com.android.sdklib.resources.ScreenSize;

import junit.framework.TestCase;

public class ScreenSizeQualifierTest extends TestCase {

    private ScreenSizeQualifier ssq;
    private FolderConfiguration config;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        ssq = new ScreenSizeQualifier();
        config = new FolderConfiguration();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        ssq = null;
        config = null;
    }

    public void testSmall() {
        assertEquals(true, ssq.checkAndSet("small", config)); //$NON-NLS-1$
        assertTrue(config.getScreenSizeQualifier() != null);
        assertEquals(ScreenSize.SMALL, config.getScreenSizeQualifier().getValue());
        assertEquals("small", config.getScreenSizeQualifier().toString()); //$NON-NLS-1$
    }

    public void testNormal() {
        assertEquals(true, ssq.checkAndSet("normal", config)); //$NON-NLS-1$
        assertTrue(config.getScreenSizeQualifier() != null);
        assertEquals(ScreenSize.NORMAL, config.getScreenSizeQualifier().getValue());
        assertEquals("normal", config.getScreenSizeQualifier().toString()); //$NON-NLS-1$
    }

    public void testLarge() {
        assertEquals(true, ssq.checkAndSet("large", config)); //$NON-NLS-1$
        assertTrue(config.getScreenSizeQualifier() != null);
        assertEquals(ScreenSize.LARGE, config.getScreenSizeQualifier().getValue());
        assertEquals("large", config.getScreenSizeQualifier().toString()); //$NON-NLS-1$
    }

    public void testXLarge() {
        assertEquals(true, ssq.checkAndSet("xlarge", config)); //$NON-NLS-1$
        assertTrue(config.getScreenSizeQualifier() != null);
        assertEquals(ScreenSize.XLARGE, config.getScreenSizeQualifier().getValue());
        assertEquals("xlarge", config.getScreenSizeQualifier().toString()); //$NON-NLS-1$
    }
}
