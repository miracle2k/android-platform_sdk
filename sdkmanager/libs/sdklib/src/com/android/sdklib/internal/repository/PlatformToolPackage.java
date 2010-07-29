/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.sdklib.internal.repository;

import com.android.sdklib.SdkConstants;
import com.android.sdklib.SdkManager;
import com.android.sdklib.internal.repository.Archive.Arch;
import com.android.sdklib.internal.repository.Archive.Os;
import com.android.sdklib.repository.SdkRepository;

import org.w3c.dom.Node;

import java.io.File;
import java.util.Map;
import java.util.Properties;

/**
 * Represents a platform-tool XML node in an SDK repository.
 */
public class PlatformToolPackage extends Package implements IMinPlatformToolsDependency {

    protected static final String PROP_MIN_PLATFORM_TOOLS_REV =
                                                "Platform.MinPlatformToolsRev";  //$NON-NLS-1$

    /**
     * The minimal revision of the platform-tools package required by this package
     * or {@link #MIN_PLATFORM_TOOLS_REV_INVALID} if the value was missing.
     */
    private final int mMinPlatformToolsRevision;

    /**
     * Creates a new platform-tool package from the attributes and elements of the given XML node.
     * <p/>
     * This constructor should throw an exception if the package cannot be created.
     */
    PlatformToolPackage(RepoSource source, Node packageNode, Map<String,String> licenses) {
        super(source, packageNode, licenses);

        mMinPlatformToolsRevision = XmlParserUtils.getXmlInt(
                packageNode,
                SdkRepository.NODE_MIN_PLATFORM_TOOLS_REV,
                MIN_PLATFORM_TOOLS_REV_INVALID);
        if (mMinPlatformToolsRevision == MIN_PLATFORM_TOOLS_REV_INVALID) {
            throw new IllegalArgumentException(
                    String.format("Missing %1$s element in %2$s package",
                            SdkRepository.NODE_MIN_PLATFORM_TOOLS_REV,
                            SdkRepository.NODE_PLATFORM_TOOL));

        }
    }

    /**
     * Manually create a new package with one archive and the given attributes or properties.
     * This is used to create packages from local directories in which case there must be
     * one archive which URL is the actual target location.
     * <p/>
     * By design, this creates a package with one and only one archive.
     */
    PlatformToolPackage(
            RepoSource source,
            Properties props,
            int revision,
            String license,
            String description,
            String descUrl,
            Os archiveOs,
            Arch archiveArch,
            String archiveOsPath) {
        super(source,
                props,
                revision,
                license,
                description,
                descUrl,
                archiveOs,
                archiveArch,
                archiveOsPath);

        mMinPlatformToolsRevision = Integer.parseInt(
                getProperty(
                        props,
                        PROP_MIN_PLATFORM_TOOLS_REV,
                        Integer.toString(MIN_PLATFORM_TOOLS_REV_INVALID)));
    }

    /**
    * The minimal revision of the tools package required by this package if > 0,
    * or {@link #MIN_PLATFORM_TOOLS_REV_INVALID} if the value was missing.
    * <p/>
    * This attribute is not mandatory and should not be normally missing.
     */
    public int getMinPlatformToolsRevision() {
        return mMinPlatformToolsRevision;
    }

    /** Returns a short description for an {@link IDescription}. */
    @Override
    public String getShortDescription() {
        return String.format("Android SDK Platform-tools, revision %1$d%2$s",
                getRevision(),
                isObsolete() ? " (Obsolete)" : "");
    }

    /** Returns a long description for an {@link IDescription}. */
    @Override
    public String getLongDescription() {
        String s = getDescription();
        if (s == null || s.length() == 0) {
            s = getShortDescription();
        }

        if (s.indexOf("revision") == -1) {
            s += String.format("\nRevision %1$d%2$s",
                    getRevision(),
                    isObsolete() ? " (Obsolete)" : "");
        }

        return s;
    }

    /**
     * Computes a potential installation folder if an archive of this package were
     * to be installed right away in the given SDK root.
     * <p/>
     * A "tool" package should always be located in SDK/tools.
     *
     * @param osSdkRoot The OS path of the SDK root folder.
     * @param suggestedDir A suggestion for the installation folder name, based on the root
     *                     folder used in the zip archive.
     * @param sdkManager An existing SDK manager to list current platforms and addons.
     * @return A new {@link File} corresponding to the directory to use to install this package.
     */
    @Override
    public File getInstallFolder(String osSdkRoot, String suggestedDir, SdkManager sdkManager) {
        return new File(osSdkRoot, SdkConstants.FD_PLATFORM_TOOLS);
    }

    @Override
    public boolean sameItemAs(Package pkg) {
        // only one platform-tool package so any platform-tool package is the same item.
        return pkg instanceof PlatformToolPackage;
    }

}
