/*
 * Copyright (C) 2009 The Android Open Source Project
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

import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkConstants;
import com.android.sdklib.SdkManager;
import com.android.sdklib.AndroidVersion.AndroidVersionException;
import com.android.sdklib.internal.repository.Archive.Arch;
import com.android.sdklib.internal.repository.Archive.Os;
import com.android.sdklib.repository.SdkRepository;

import org.w3c.dom.Node;

import java.io.File;
import java.util.Map;
import java.util.Properties;

/**
 * Represents a sample XML node in an SDK repository.
 */
public class SamplePackage extends MinToolsPackage
    implements IPackageVersion, IMinApiLevelDependency, IMinToolsDependency {

    private static final String PROP_MIN_API_LEVEL = "Sample.MinApiLevel";  //$NON-NLS-1$

    /** The matching platform version. */
    private final AndroidVersion mVersion;

    /**
     * The minimal API level required by this extra package, if > 0,
     * or {@link #MIN_API_LEVEL_NOT_SPECIFIED} if there is no such requirement.
     */
    private final int mMinApiLevel;

    /**
     * Creates a new sample package from the attributes and elements of the given XML node.
     * <p/>
     * This constructor should throw an exception if the package cannot be created.
     */
    SamplePackage(RepoSource source, Node packageNode, Map<String,String> licenses) {
        super(source, packageNode, licenses);

        int apiLevel = XmlParserUtils.getXmlInt   (packageNode, SdkRepository.NODE_API_LEVEL, 0);
        String codeName = XmlParserUtils.getXmlString(packageNode, SdkRepository.NODE_CODENAME);
        if (codeName.length() == 0) {
            codeName = null;
        }
        mVersion = new AndroidVersion(apiLevel, codeName);

        mMinApiLevel = XmlParserUtils.getXmlInt(packageNode, SdkRepository.NODE_MIN_API_LEVEL,
                MIN_API_LEVEL_NOT_SPECIFIED);
    }

    /**
     * Creates a new sample package based on an actual {@link IAndroidTarget} (which
     * must have {@link IAndroidTarget#isPlatform()} true) from the {@link SdkManager}.
     * <p/>
     * The target <em>must</em> have an existing sample directory that uses the /samples
     * root form rather than the old form where the samples dir was located under the
     * platform dir.
     * <p/>
     * This is used to list local SDK folders in which case there is one archive which
     * URL is the actual samples path location.
     * <p/>
     * By design, this creates a package with one and only one archive.
     */
    SamplePackage(IAndroidTarget target, Properties props) {
        super(  null,                                   //source
                props,                                  //properties
                0,                                      //revision will be taken from props
                null,                                   //license
                null,                                   //description
                null,                                   //descUrl
                Os.ANY,                                 //archiveOs
                Arch.ANY,                               //archiveArch
                target.getPath(IAndroidTarget.SAMPLES)  //archiveOsPath
                );

        mVersion = target.getVersion();

        mMinApiLevel = Integer.parseInt(
            getProperty(props, PROP_MIN_API_LEVEL, Integer.toString(MIN_API_LEVEL_NOT_SPECIFIED)));
    }

    /**
     * Creates a new sample package from an actual directory path and previously
     * saved properties.
     * <p/>
     * This is used to list local SDK folders in which case there is one archive which
     * URL is the actual samples path location.
     * <p/>
     * By design, this creates a package with one and only one archive.
     *
     * @throws AndroidVersionException if the {@link AndroidVersion} can't be restored
     *                                 from properties.
     */
    SamplePackage(String archiveOsPath, Properties props) throws AndroidVersionException {
        super(null,                                   //source
              props,                                  //properties
              0,                                      //revision will be taken from props
              null,                                   //license
              null,                                   //description
              null,                                   //descUrl
              Os.ANY,                                 //archiveOs
              Arch.ANY,                               //archiveArch
              archiveOsPath                           //archiveOsPath
              );

        mVersion = new AndroidVersion(props);

        mMinApiLevel = Integer.parseInt(
            getProperty(props, PROP_MIN_API_LEVEL, Integer.toString(MIN_API_LEVEL_NOT_SPECIFIED)));
    }

    /**
     * Save the properties of the current packages in the given {@link Properties} object.
     * These properties will later be given to a constructor that takes a {@link Properties} object.
     */
    @Override
    void saveProperties(Properties props) {
        super.saveProperties(props);

        mVersion.saveProperties(props);

        if (getMinApiLevel() != MIN_API_LEVEL_NOT_SPECIFIED) {
            props.setProperty(PROP_MIN_API_LEVEL, Integer.toString(getMinApiLevel()));
        }
    }

    /**
     * Returns the minimal API level required by this extra package, if > 0,
     * or {@link #MIN_API_LEVEL_NOT_SPECIFIED} if there is no such requirement.
     */
    public int getMinApiLevel() {
        return mMinApiLevel;
    }

    /** Returns the matching platform version. */
    public AndroidVersion getVersion() {
        return mVersion;
    }

    /** Returns a short description for an {@link IDescription}. */
    @Override
    public String getShortDescription() {
        String s = String.format("Samples for SDK API %1$s%2$s, revision %3$d%4$s",
                mVersion.getApiString(),
                mVersion.isPreview() ? " Preview" : "",
                getRevision(),
                isObsolete() ? " (Obsolete)" : "");
        return s;
    }

    /**
     * Returns a long description for an {@link IDescription}.
     *
     * The long description is whatever the XML contains for the &lt;description&gt; field,
     * or the short description if the former is empty.
     */
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
     * A sample package is typically installed in SDK/samples/android-"version".
     * However if we can find a different directory that already has this sample
     * version installed, we'll use that one.
     *
     * @param osSdkRoot The OS path of the SDK root folder.
     * @param suggestedDir A suggestion for the installation folder name, based on the root
     *                     folder used in the zip archive.
     * @param sdkManager An existing SDK manager to list current platforms and addons.
     * @return A new {@link File} corresponding to the directory to use to install this package.
     */
    @Override
    public File getInstallFolder(String osSdkRoot, String suggestedDir, SdkManager sdkManager) {

        // The /samples dir at the root of the SDK
        File samplesRoot = new File(osSdkRoot, SdkConstants.FD_SAMPLES);

        // First find if this platform is already installed. If so, reuse the same directory.
        for (IAndroidTarget target : sdkManager.getTargets()) {
            if (target.isPlatform() &&
                    target.getVersion().equals(mVersion)) {
                String p = target.getPath(IAndroidTarget.SAMPLES);
                File f = new File(p);
                if (f.isDirectory()) {
                    // We *only* use this directory if it's using the "new" location
                    // under SDK/samples. We explicitly do not reuse the "old" location
                    // under SDK/platform/android-N/samples.
                    if (f.getParentFile().equals(samplesRoot)) {
                        return f;
                    }
                }
            }
        }

        // Otherwise, get a suitable default
        File folder = new File(samplesRoot,
                String.format("android-%s", getVersion().getApiString())); //$NON-NLS-1$

        for (int n = 1; folder.exists(); n++) {
            // Keep trying till we find an unused directory.
            folder = new File(samplesRoot,
                    String.format("android-%s_%d", getVersion().getApiString(), n)); //$NON-NLS-1$
        }

        return folder;
    }

    /**
     * Makes sure the base /samples folder exists before installing.
     */
    @Override
    public void preInstallHook(String osSdkRoot, Archive archive) {
        super.preInstallHook(osSdkRoot, archive);

        File samplesRoot = new File(osSdkRoot, SdkConstants.FD_SAMPLES);
        if (!samplesRoot.isDirectory()) {
            samplesRoot.mkdir();
        }
    }

    @Override
    public boolean sameItemAs(Package pkg) {
        if (pkg instanceof SamplePackage) {
            SamplePackage newPkg = (SamplePackage)pkg;

            // check they are the same platform.
            return newPkg.getVersion().equals(this.getVersion());
        }

        return false;
    }
}
