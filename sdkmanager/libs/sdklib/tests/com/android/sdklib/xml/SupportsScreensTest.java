package com.android.sdklib.xml;

import com.android.sdklib.xml.ManifestData.SupportsScreens;

import java.io.InputStream;

import junit.framework.TestCase;

public class SupportsScreensTest extends TestCase {

    private static final String TESTDATA_PATH =
        "/com/android/sdklib/testdata/";  //$NON-NLS-1$
    private static final String TESTAPP2_XML = TESTDATA_PATH +
        "AndroidManifest-testapp2.xml";  //$NON-NLS-1$

    public void testDefaultValuesApi3() {
        SupportsScreens supportsScreens = SupportsScreens.getDefaultValues(3);

        assertNotNull(supportsScreens);
        assertEquals(Boolean.FALSE, supportsScreens.getAnyDensity());
        assertEquals(Boolean.FALSE, supportsScreens.getResizeable());
        assertEquals(Boolean.FALSE, supportsScreens.getSmallScreens());
        assertEquals(Boolean.TRUE, supportsScreens.getNormalScreens());
        assertEquals(Boolean.FALSE, supportsScreens.getLargeScreens());
    }

    public void testDefaultValuesApi4() {
        SupportsScreens supportsScreens = SupportsScreens.getDefaultValues(4);

        assertNotNull(supportsScreens);
        assertEquals(Boolean.TRUE, supportsScreens.getAnyDensity());
        assertEquals(Boolean.TRUE, supportsScreens.getResizeable());
        assertEquals(Boolean.TRUE, supportsScreens.getSmallScreens());
        assertEquals(Boolean.TRUE, supportsScreens.getNormalScreens());
        assertEquals(Boolean.TRUE, supportsScreens.getLargeScreens());
    }

    public void testManifestParsing() throws Exception {
        InputStream manifestStream = this.getClass().getResourceAsStream(TESTAPP2_XML);

        ManifestData data = AndroidManifestParser.parse(manifestStream);
        assertNotNull(data);

        SupportsScreens supportsScreens = data.getSupportsScreensFromManifest();
        assertNotNull(supportsScreens);
        assertEquals(null, supportsScreens.getAnyDensity());
        assertEquals(null, supportsScreens.getResizeable());
        assertEquals(null, supportsScreens.getSmallScreens());
        assertEquals(null, supportsScreens.getNormalScreens());
        assertEquals(Boolean.FALSE, supportsScreens.getLargeScreens());

        supportsScreens = data.getSupportsScreensValues();
        assertNotNull(supportsScreens);
        assertEquals(Boolean.TRUE, supportsScreens.getAnyDensity());
        assertEquals(Boolean.TRUE, supportsScreens.getResizeable());
        assertEquals(Boolean.TRUE, supportsScreens.getSmallScreens());
        assertEquals(Boolean.TRUE, supportsScreens.getNormalScreens());
        assertEquals(Boolean.FALSE, supportsScreens.getLargeScreens());
    }
}
