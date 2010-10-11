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

package com.android.sdklib.repository;


import com.android.sdklib.internal.repository.SdkSource;

import java.io.InputStream;

/**
 * Public constants for the sdk-repository XML Schema.
 */
public class SdkRepoConstants extends RepoConstants {

    /** The URL of the official Google sdk-repository site. */
    public static final String URL_GOOGLE_SDK_SITE =
        "https://dl-ssl.google.com/android/repository/";                        //$NON-NLS-1$

    /** The default name looked for by {@link SdkSource} when trying to load an
     * sdk-repository XML if the URL doesn't match an existing resource. */
    public static final String URL_DEFAULT_FILENAME = "repository.xml";         //$NON-NLS-1$

    /** The base of our sdk-repository XML namespace. */
    private static final String NS_BASE =
        "http://schemas.android.com/sdk/android/repository/";                   //$NON-NLS-1$

    /**
     * The pattern of our sdk-repository XML namespace.
     * Matcher's group(1) is the schema version (integer).
     */
    public static final String NS_PATTERN = NS_BASE + "([1-9][0-9]*)";          //$NON-NLS-1$

    /** The latest version of the sdk-repository XML Schema.
     *  Valid version numbers are between 1 and this number, included. */
    public static final int NS_LATEST_VERSION = 3;

    /** The XML namespace of the latest sdk-repository XML. */
    public static final String NS_URI = getSchemaUri(NS_LATEST_VERSION);

    /** The root sdk-repository element */
    public static final String NODE_SDK_REPOSITORY = "sdk-repository";        //$NON-NLS-1$

    /** A platform package. */
    public static final String NODE_PLATFORM        = "platform";             //$NON-NLS-1$
    /** A tool package. */
    public static final String NODE_TOOL            = "tool";                 //$NON-NLS-1$
    /** A platform-tool package. */
    public static final String NODE_PLATFORM_TOOL   = "platform-tool";        //$NON-NLS-1$
    /** A doc package. */
    public static final String NODE_DOC             = "doc";                  //$NON-NLS-1$
    /** A sample package. */
    public static final String NODE_SAMPLE          = "sample";               //$NON-NLS-1$

    /**
     * List of possible nodes in a repository XML. Used to populate options automatically
     * in the no-GUI mode.
     */
    public static final String[] NODES = {
        NODE_PLATFORM,
        NODE_TOOL,
        NODE_PLATFORM_TOOL,
        NODE_DOC,
        NODE_SAMPLE,
        NODE_EXTRA
    };

    /**
     * Returns a stream to the requested repository XML Schema.
     *
     * @param version Between 1 and {@link #NS_LATEST_VERSION}, included.
     * @return An {@link InputStream} object for the local XSD file or
     *         null if there is no schema for the requested version.
     */
    public static InputStream getXsdStream(int version) {
        String filename = String.format("sdk-repository-%d.xsd", version);      //$NON-NLS-1$
        return SdkRepoConstants.class.getResourceAsStream(filename);
    }

    /**
     * Returns the URI of the SDK Repository schema for the given version number.
     * @param version Between 1 and {@link #NS_LATEST_VERSION} included.
     */
    public static String getSchemaUri(int version) {
        return String.format(NS_BASE + "%d", version);           //$NON-NLS-1$
    }
}
