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

package com.android.ant;

import com.android.sdklib.internal.build.ApkBuilderHelper;
import com.android.sdklib.internal.build.ApkBuilderHelper.ApkCreationException;
import com.android.sdklib.internal.build.ApkBuilderHelper.ApkFile;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.Path;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;

public class ApkBuilderTask extends Task {

    private String mOutFolder;
    @Deprecated private String mBaseName;
    private String mApkFilepath;
    private String mResourceFile;
    private boolean mVerbose = false;
    private boolean mSigned = true;
    private boolean mDebug = false;
    private boolean mHasCode = true;
    private String mAbiFilter = null;

    private final ArrayList<Path> mZipList = new ArrayList<Path>();
    private final ArrayList<Path> mDexList = new ArrayList<Path>();
    private final ArrayList<Path> mFileList = new ArrayList<Path>();
    private final ArrayList<Path> mSourceList = new ArrayList<Path>();
    private final ArrayList<Path> mJarfolderList = new ArrayList<Path>();
    private final ArrayList<Path> mJarfileList = new ArrayList<Path>();
    private final ArrayList<Path> mNativeList = new ArrayList<Path>();

    private final ArrayList<FileInputStream> mZipArchives = new ArrayList<FileInputStream>();
    private final ArrayList<File> mArchiveFiles = new ArrayList<File>();
    private final ArrayList<ApkFile> mJavaResources = new ArrayList<ApkFile>();
    private final ArrayList<FileInputStream> mResourcesJars = new ArrayList<FileInputStream>();
    private final ArrayList<ApkFile> mNativeLibraries = new ArrayList<ApkFile>();

    /**
     * Sets the value of the "outfolder" attribute.
     * @param outFolder the value.
     */
    public void setOutfolder(Path outFolder) {
        mOutFolder = TaskHelper.checkSinglePath("outfolder", outFolder);
    }

    /**
     * Sets the value of the "basename" attribute.
     * @param baseName the value.
     * @deprecated
     */
    public void setBasename(String baseName) {
        System.out.println("WARNNG: Using deprecated 'basename' attribute in ApkBuilderTask." +
                "Use 'apkfilepath' (path) instead.");
        mBaseName = baseName;
    }

    /**
     * Sets the full filepath to the apk to generate.
     * @param filepath
     */
    public void setApkfilepath(String filepath) {
        mApkFilepath = filepath;
    }

    /**
     * Sets the resourcefile attribute
     * @param resourceFile
     */
    public void setResourcefile(String resourceFile) {
        mResourceFile = resourceFile;
    }

    /**
     * Sets the value of the "verbose" attribute.
     * @param verbose the value.
     */
    public void setVerbose(boolean verbose) {
        mVerbose = verbose;
    }

    /**
     * Sets the value of the "signed" attribute.
     * @param signed the value.
     */
    public void setSigned(boolean signed) {
        mSigned = signed;
    }

    /**
     * Sets the value of the "debug" attribute.
     * @param debug the debug mode value.
     */
    public void setDebug(boolean debug) {
        mDebug = debug;
    }

    /**
     * Sets an ABI filter. If non <code>null</code>, then only native libraries matching the given
     * ABI will be packaged with the APK.
     * @param abiFilter the ABI to accept (and reject all other). If null or empty string, no ABIs
     * are rejected. This must be a single ABI name as defined by the Android NDK. For a list
     * of valid ABI names, see $NDK/docs/CPU-ARCH-ABIS.TXT
     */
    public void setAbifilter(String abiFilter) {
        if (abiFilter != null && abiFilter.length() > 0) {
            mAbiFilter = abiFilter.trim();
        } else {
            mAbiFilter = null;
        }
    }

    /**
     * Sets the hascode attribute. Default is true.
     * If set to false, then <dex> and <sourcefolder> nodes are ignored and not processed.
     * @param hasCode the value of the attribute.
     */
    public void setHascode(boolean hasCode) {
        mHasCode   = hasCode;
    }

    /**
     * Returns an object representing a nested <var>zip</var> element.
     */
    public Object createZip() {
        Path path = new Path(getProject());
        mZipList.add(path);
        return path;
    }

    /**
     * Returns an object representing a nested <var>dex</var> element.
     * This is similar to a nested <var>file</var> element, except when {@link #mHasCode}
     * is <code>false</code> in which case it's ignored.
     */
    public Object createDex() {
        Path path = new Path(getProject());
        mDexList.add(path);
        return path;
    }

    /**
     * Returns an object representing a nested <var>file</var> element.
     */
    public Object createFile() {
        Path path = new Path(getProject());
        mFileList.add(path);
        return path;
    }

    /**
     * Returns an object representing a nested <var>sourcefolder</var> element.
     */
    public Object createSourcefolder() {
        Path path = new Path(getProject());
        mSourceList.add(path);
        return path;
    }

    /**
     * Returns an object representing a nested <var>jarfolder</var> element.
     */
    public Object createJarfolder() {
        Path path = new Path(getProject());
        mJarfolderList.add(path);
        return path;
    }

    /**
     * Returns an object representing a nested <var>jarfile</var> element.
     */
    public Object createJarfile() {
        Path path = new Path(getProject());
        mJarfileList.add(path);
        return path;
    }

    /**
     * Returns an object representing a nested <var>nativefolder</var> element.
     */
    public Object createNativefolder() {
        Path path = new Path(getProject());
        mNativeList.add(path);
        return path;
    }

    @Override
    public void execute() throws BuildException {
        Project antProject = getProject();

        ApkBuilderHelper apkBuilder = new ApkBuilderHelper();
        apkBuilder.setVerbose(mVerbose);
        apkBuilder.setSignedPackage(mSigned);
        apkBuilder.setDebugMode(mDebug);

        try {
            // setup the list of everything that needs to go in the archive.

            // go through the list of zip files to add. This will not include
            // the resource package, which is handled separaly for each apk to create.
            for (Path pathList : mZipList) {
                for (String path : pathList.list()) {
                    FileInputStream input = new FileInputStream(path);
                    mZipArchives.add(input);
                }
            }

            // now go through the list of file to directly add the to the list.
            for (Path pathList : mFileList) {
                for (String path : pathList.list()) {
                    mArchiveFiles.add(ApkBuilderHelper.getInputFile(path));
                }
            }

            // only attempt to add Dex files if hasCode is true.
            if (mHasCode) {
                for (Path pathList : mDexList) {
                    for (String path : pathList.list()) {
                        mArchiveFiles.add(ApkBuilderHelper.getInputFile(path));
                    }
                }
            }

            // now go through the list of file to directly add the to the list.
            if (mHasCode) {
                for (Path pathList : mSourceList) {
                    for (String path : pathList.list()) {
                        ApkBuilderHelper.processSourceFolderForResource(new File(path),
                                mJavaResources);
                    }
                }
            }

            // now go through the list of jar folders.
            for (Path pathList : mJarfolderList) {
                for (String path : pathList.list()) {
                    // it's ok if top level folders are missing
                    File folder = new File(path);
                    if (folder.isDirectory()) {
                        ApkBuilderHelper.processJar(folder, mResourcesJars);
                    }
                }
            }

            // now go through the list of jar files.
            for (Path pathList : mJarfileList) {
                for (String path : pathList.list()) {
                    ApkBuilderHelper.processJar(new File(path), mResourcesJars);
                }
            }

            // now the native lib folder.
            for (Path pathList : mNativeList) {
                for (String path : pathList.list()) {
                    // it's ok if top level folders are missing
                    File folder = new File(path);
                    if (folder.isDirectory()) {
                        ApkBuilderHelper.processNativeFolder(folder, mDebug,
                                mNativeLibraries, mVerbose, mAbiFilter);
                    }
                }
            }

            // get the rules revision
            String rulesRevStr = antProject.getProperty(TaskHelper.PROP_RULES_REV);
            int rulesRev = 1;
            try {
                rulesRev = Integer.parseInt(rulesRevStr);
            } catch (NumberFormatException e) {
                // this shouldn't happen since setup task is the one setting up every time.
            }


            File file;
            if (mApkFilepath != null) {
                file = new File(mApkFilepath);
            } else if (rulesRev == 2) {
                if (mSigned) {
                    file = new File(mOutFolder, mBaseName + "-debug-unaligned.apk");
                } else {
                    file = new File(mOutFolder, mBaseName + "-unsigned.apk");
                }
            } else {
                throw new BuildException("missing attribute 'apkFilepath'");
            }

            // create the package.
            createApk(apkBuilder, file);

        } catch (FileNotFoundException e) {
            throw new BuildException(e);
        } catch (IllegalArgumentException e) {
            throw new BuildException(e);
        } catch (ApkCreationException e) {
            e.printStackTrace();
            throw new BuildException(e);
        }
    }

    /**
     * Creates an application package.
     * @param apkBuilder
     * @param outputfile the file to generate
     * @throws FileNotFoundException
     * @throws ApkCreationException
     */
    private void createApk(ApkBuilderHelper apkBuilder, File outputfile)
            throws FileNotFoundException, ApkCreationException {

        // add the resource pack file as a zip archive input.
        FileInputStream resoucePackageZipFile = new FileInputStream(
                new File(mOutFolder, mResourceFile));
        mZipArchives.add(resoucePackageZipFile);

        if (mSigned) {
            System.out.println(String.format(
                    "Creating %s and signing it with a debug key...", outputfile.getName()));
        } else {
            System.out.println(String.format(
                    "Creating %s for release...", outputfile.getName()));
        }

        // and generate the apk
        apkBuilder.createPackage(outputfile.getAbsoluteFile(), mZipArchives,
                mArchiveFiles, mJavaResources, mResourcesJars, mNativeLibraries);

        // we are done. We need to remove the resource package from the list of zip archives
        // in case we have another apk to generate.
        mZipArchives.remove(resoucePackageZipFile);
    }
}
