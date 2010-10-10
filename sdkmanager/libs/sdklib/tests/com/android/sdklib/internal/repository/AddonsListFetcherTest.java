/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.sdklib.internal.repository;

import com.android.sdklib.internal.repository.AddonsListFetcher.Site;
import com.android.sdklib.repository.SdkAddonsListConstants;

import org.w3c.dom.Document;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import junit.framework.TestCase;

/**
 * Tests for {@link AddonsListFetcher}
 */
public class AddonsListFetcherTest extends TestCase {

    /**
     * An internal helper class to give us visibility to the protected members we want
     * to test.
     */
    private static class MockAddonsListFetcher extends AddonsListFetcher {

        public Site[] _parseAddonsList(Document doc, String nsUri, ITaskMonitor monitor) {
            return super.parseAddonsList(doc, nsUri, monitor);
        }

        public int _getXmlSchemaVersion(InputStream xml) {
            return super.getXmlSchemaVersion(xml);
        }

        public String _validateXml(InputStream xml, String url, int version,
                                   String[] outError, Boolean[] validatorFound) {
            return super.validateXml(xml, url, version, outError, validatorFound);
        }

        public Document _getDocument(InputStream xml, ITaskMonitor monitor) {
            return super.getDocument(xml, monitor);
        }

    }

    private MockAddonsListFetcher mFetcher;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mFetcher = new MockAddonsListFetcher();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();

        mFetcher = null;
    }

    /**
     * Validate we can still load a valid addon schema version 1
     */
    public void testLoadSample_1() throws Exception {
        InputStream xmlStream =
            getTestResource("/com/android/sdklib/testdata/addons_list_sample_1.xml");

        // guess the version from the XML document
        int version = mFetcher._getXmlSchemaVersion(xmlStream);
        assertEquals(1, version);

        Boolean[] validatorFound = new Boolean[] { Boolean.FALSE };
        String[] validationError = new String[] { null };
        String url = "not-a-valid-url://addons_list.xml";

        String uri = mFetcher._validateXml(
                xmlStream, url, version, validationError, validatorFound);
        assertEquals(Boolean.TRUE, validatorFound[0]);
        assertEquals(null, validationError[0]);
        assertEquals(SdkAddonsListConstants.getSchemaUri(1), uri);

        // Validation was successful, load the document
        MockMonitor monitor = new MockMonitor();
        Document doc = mFetcher._getDocument(xmlStream, monitor);
        assertNotNull(doc);

        // Get the sites
        Site[] result = mFetcher._parseAddonsList(doc, uri, monitor);

        assertEquals("", monitor.getCapturedDescriptions());
        assertEquals("", monitor.getCapturedResults());

        // check the sites we found...
        assertEquals(3, result.length);

        assertEquals("My Example Add-ons.", result[0].getUiName());
        assertEquals("http://www.example.com/my_addons.xml", result[0].getUrl());

        // The XML file is UTF-8 so we support character sets
        assertEquals("ありがとうございます。", result[1].getUiName());
        assertEquals("http://www.example.co.jp/addons.xml", result[1].getUrl());

        assertEquals("Example of directory URL.", result[2].getUiName());
        assertEquals("http://www.example.com/", result[2].getUrl());
    }

    /**
     * Returns an SdkLib file resource as a {@link ByteArrayInputStream},
     * which has the advantage that we can use {@link InputStream#reset()} on it
     * at any time to read it multiple times.
     * <p/>
     * The default for getResourceAsStream() is to return a {@link FileInputStream} that
     * does not support reset(), yet we need it in the tested code.
     *
     * @throws IOException if some I/O read fails
     */
    private ByteArrayInputStream getTestResource(String filename) throws IOException {
        InputStream xmlStream = this.getClass().getResourceAsStream(filename);

        try {
            byte[] data = new byte[8192];
            int offset = 0;
            int n;

            while ((n = xmlStream.read(data, offset, data.length - offset)) != -1) {
                offset += n;

                if (offset == data.length) {
                    byte[] newData = new byte[offset + 8192];
                    System.arraycopy(data, 0, newData, 0, offset);
                    data = newData;
                }
            }

            return new ByteArrayInputStream(data, 0, offset);
        } finally {
            if (xmlStream != null) {
                xmlStream.close();
            }
        }
    }
}
