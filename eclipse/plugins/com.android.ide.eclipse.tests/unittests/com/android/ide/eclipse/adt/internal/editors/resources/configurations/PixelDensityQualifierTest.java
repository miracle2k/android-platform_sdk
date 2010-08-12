/*
 * Copyright (C) 2007 The Android Open Source Project
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
import com.android.ide.eclipse.adt.internal.resources.configurations.PixelDensityQualifier;
import com.android.sdklib.resources.Density;

import junit.framework.TestCase;

public class PixelDensityQualifierTest extends TestCase {

    private PixelDensityQualifier pdq;
    private FolderConfiguration config;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        pdq = new PixelDensityQualifier();
        config = new FolderConfiguration();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        pdq = null;
        config = null;
    }

    public void testCheckAndSet() {
        assertEquals(true, pdq.checkAndSet("ldpi", config));//$NON-NLS-1$
        assertTrue(config.getPixelDensityQualifier() != null);
        assertEquals(Density.LOW, config.getPixelDensityQualifier().getValue());
        assertEquals("ldpi", config.getPixelDensityQualifier().toString()); //$NON-NLS-1$
    }

    public void testFailures() {
        assertEquals(false, pdq.checkAndSet("", config));//$NON-NLS-1$
        assertEquals(false, pdq.checkAndSet("dpi", config));//$NON-NLS-1$
        assertEquals(false, pdq.checkAndSet("123dpi", config));//$NON-NLS-1$
        assertEquals(false, pdq.checkAndSet("123", config));//$NON-NLS-1$
        assertEquals(false, pdq.checkAndSet("sdfdpi", config));//$NON-NLS-1$
    }

    public void testIsBetterMatchThan() {
        PixelDensityQualifier ldpi = new PixelDensityQualifier(Density.LOW);
        PixelDensityQualifier mdpi = new PixelDensityQualifier(Density.MEDIUM);
        PixelDensityQualifier hdpi = new PixelDensityQualifier(Density.HIGH);
        PixelDensityQualifier xhdpi = new PixelDensityQualifier(Density.XHIGH);

        // first test that each Q is a better match than all other Qs when the ref is the same Q.
        assertTrue(ldpi.isBetterMatchThan(mdpi, ldpi));
        assertTrue(ldpi.isBetterMatchThan(hdpi, ldpi));
        assertTrue(ldpi.isBetterMatchThan(xhdpi, ldpi));

        assertTrue(mdpi.isBetterMatchThan(ldpi, mdpi));
        assertTrue(mdpi.isBetterMatchThan(hdpi, mdpi));
        assertTrue(mdpi.isBetterMatchThan(xhdpi, mdpi));

        assertTrue(hdpi.isBetterMatchThan(ldpi, hdpi));
        assertTrue(hdpi.isBetterMatchThan(mdpi, hdpi));
        assertTrue(hdpi.isBetterMatchThan(xhdpi, hdpi));

        assertTrue(xhdpi.isBetterMatchThan(ldpi, xhdpi));
        assertTrue(xhdpi.isBetterMatchThan(mdpi, xhdpi));
        assertTrue(xhdpi.isBetterMatchThan(hdpi, xhdpi));

        // now test that the highest dpi is always preferable if there's no exact match

        // looking for ldpi:
        assertTrue(hdpi.isBetterMatchThan(mdpi, ldpi));
        assertTrue(xhdpi.isBetterMatchThan(mdpi, ldpi));
        assertTrue(xhdpi.isBetterMatchThan(hdpi, ldpi));
        // the other way around
        assertFalse(mdpi.isBetterMatchThan(hdpi, ldpi));
        assertFalse(mdpi.isBetterMatchThan(xhdpi, ldpi));
        assertFalse(hdpi.isBetterMatchThan(xhdpi, ldpi));

        // looking for mdpi
        assertTrue(hdpi.isBetterMatchThan(ldpi, mdpi));
        assertTrue(xhdpi.isBetterMatchThan(ldpi, mdpi));
        assertTrue(xhdpi.isBetterMatchThan(hdpi, mdpi));
        // the other way around
        assertFalse(ldpi.isBetterMatchThan(hdpi, mdpi));
        assertFalse(ldpi.isBetterMatchThan(xhdpi, mdpi));
        assertFalse(hdpi.isBetterMatchThan(xhdpi, mdpi));

        // looking for hdpi
        assertTrue(mdpi.isBetterMatchThan(ldpi, hdpi));
        assertTrue(xhdpi.isBetterMatchThan(ldpi, hdpi));
        assertTrue(xhdpi.isBetterMatchThan(mdpi, hdpi));
        // the other way around
        assertFalse(ldpi.isBetterMatchThan(mdpi, hdpi));
        assertFalse(ldpi.isBetterMatchThan(xhdpi, hdpi));
        assertFalse(mdpi.isBetterMatchThan(xhdpi, hdpi));

        // looking for xhdpi
        assertTrue(mdpi.isBetterMatchThan(ldpi, xhdpi));
        assertTrue(hdpi.isBetterMatchThan(ldpi, xhdpi));
        assertTrue(hdpi.isBetterMatchThan(mdpi, xhdpi));
        // the other way around
        assertFalse(ldpi.isBetterMatchThan(mdpi, xhdpi));
        assertFalse(ldpi.isBetterMatchThan(hdpi, xhdpi));
        assertFalse(mdpi.isBetterMatchThan(hdpi, xhdpi));
    }
}
