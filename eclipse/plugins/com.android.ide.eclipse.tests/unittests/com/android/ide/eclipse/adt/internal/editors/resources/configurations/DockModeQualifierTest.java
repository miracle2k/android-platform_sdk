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

import com.android.ide.eclipse.adt.internal.resources.configurations.DockModeQualifier;
import com.android.sdklib.resources.DockMode;

import junit.framework.TestCase;

public class DockModeQualifierTest extends TestCase {

    private DockModeQualifier mCarQualifier;
    private DockModeQualifier mDeskQualifier;
    private DockModeQualifier mNoneQualifier;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mCarQualifier = new DockModeQualifier(DockMode.CAR);
        mDeskQualifier = new DockModeQualifier(DockMode.DESK);
        mNoneQualifier = new DockModeQualifier(DockMode.NONE);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        mCarQualifier = null;
        mDeskQualifier = null;
        mNoneQualifier = null;
    }

    public void testIsBetterMatchThan() {
        assertTrue(mNoneQualifier.isBetterMatchThan(mCarQualifier, mDeskQualifier));
        assertFalse(mNoneQualifier.isBetterMatchThan(mDeskQualifier, mDeskQualifier));
        assertTrue(mNoneQualifier.isBetterMatchThan(mDeskQualifier, mCarQualifier));
        assertFalse(mNoneQualifier.isBetterMatchThan(mCarQualifier, mCarQualifier));

        assertFalse(mDeskQualifier.isBetterMatchThan(mCarQualifier, mCarQualifier));
        assertFalse(mCarQualifier.isBetterMatchThan(mDeskQualifier, mDeskQualifier));
    }

    public void testIsMatchFor() {
        assertTrue(mNoneQualifier.isMatchFor(mCarQualifier));
        assertTrue(mNoneQualifier.isMatchFor(mDeskQualifier));
        assertTrue(mCarQualifier.isMatchFor(mCarQualifier));
        assertTrue(mDeskQualifier.isMatchFor(mDeskQualifier));

        assertFalse(mCarQualifier.isMatchFor(mNoneQualifier));
        assertFalse(mCarQualifier.isMatchFor(mDeskQualifier));
        assertFalse(mDeskQualifier.isMatchFor(mCarQualifier));
        assertFalse(mDeskQualifier.isMatchFor(mNoneQualifier));
    }
}
