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

import com.android.apkbuilder.ApkBuilder.ApkCreationException;
import com.android.apkbuilder.internal.ApkBuilderImpl;
import com.android.apkbuilder.internal.ApkBuilderImpl.ApkFile;
import com.android.sdklib.internal.project.ApkSettings;
import com.android.sdklib.internal.project.ProjectProperties;
import com.android.sdklib.internal.project.ProjectProperties.PropertyType;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.Path;
import org.apache.tools.ant.types.Path.PathElement;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Map.Entry;

public class ApkBuilderTask extends Task {

    // ref id to the <path> object containing all the boot classpaths.
    private final static String REF_APK_PATH = "android.apks.path";

    private String mOutFolder;
    private String mBaseName;
    private boolean mVerbose = false;
    private boolean mSigned = true;
    private boolean mDebug = false;

    private final ArrayList<Path> mZipList = new ArrayList<Path>();
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
     */
    public void setBasename(String baseName) {
        mBaseName = baseName;
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
     * Returns an object representing a nested <var>zip</var> element.
     */
    public Object createZip() {
        Path path = new Path(getProject());
        mZipList.add(path);
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

        ApkBuilderImpl apkBuilder = new ApkBuilderImpl();
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
                    mArchiveFiles.add(ApkBuilderImpl.getInputFile(path));
                }
            }

            // now go through the list of file to directly add the to the list.
            for (Path pathList : mSourceList) {
                for (String path : pathList.list()) {
                    ApkBuilderImpl.processSourceFolderForResource(new File(path),
                            mJavaResources);
                }
            }

            // now go through the list of jar folders.
            for (Path pathList : mJarfolderList) {
                for (String path : pathList.list()) {
                    ApkBuilderImpl.processJar(new File(path), mResourcesJars);
                }
            }

            // now go through the list of jar files.
            for (Path pathList : mJarfileList) {
                for (String path : pathList.list()) {
                    ApkBuilderImpl.processJar(new File(path), mResourcesJars);
                }
            }

            // now the native lib folder.
            for (Path pathList : mNativeList) {
                for (String path : pathList.list()) {
                    ApkBuilderImpl.processNativeFolder(new File(path), mDebug,
                            mNativeLibraries);
                }
            }

            // create the Path item that will contain all the generated APKs
            // for reuse by other targets (signing/zipaligning)
            Path path = new Path(antProject);

            // The createApk method uses mBaseName for the base name of the packages (resources
            // and apk files).
            // The generated apk file name is
            // debug:   {base}[-{config}]-debug-unaligned.apk
            // release: {base}[-{config}]-unsigned.apk
            // Unfortunately for 1.5 projects and before the 'install' ant target expects the name
            // of the default debug package to be {base}-debug.apk
            // In order to support those package, we look for the 'out.debug.unaligned.package'
            // property. If this exist, then we generate {base}[-{config}]-debug-unaligned.apk
            // otherwise we generate {base}[-{config}]-debug.apk
            // FIXME: Make apkbuilder export the package name used instead of
            // having to keep apkbuilder and the rules file in sync
            String debugPackageSuffix = "-debug-unaligned.apk";
            if (antProject.getProperty("out.debug.unaligned.package") == null
                    && antProject.getProperty("out-debug-unaligned-package") == null) {
                debugPackageSuffix = "-debug.apk";
            }

            // first do a full resource package
            createApk(apkBuilder, null /*configName*/, null /*resourceFilter*/, path,
                    debugPackageSuffix);

            // now see if we need to create file with filtered resources.
            // Get the project base directory.
            File baseDir = antProject.getBaseDir();
            ProjectProperties properties = ProjectProperties.load(baseDir.getAbsolutePath(),
                    PropertyType.DEFAULT);

            ApkSettings apkSettings = new ApkSettings(properties);
            if (apkSettings != null) {
                Map<String, String> apkFilters = apkSettings.getResourceFilters();
                if (apkFilters.size() > 0) {
                    for (Entry<String, String> entry : apkFilters.entrySet()) {
                        createApk(apkBuilder, entry.getKey(), entry.getValue(), path,
                                debugPackageSuffix);
                    }
                }
            }

            // finally sets the path in the project with a reference
            antProject.addReference(REF_APK_PATH, path);

        } catch (FileNotFoundException e) {
            throw new BuildException(e);
        } catch (IllegalArgumentException e) {
            throw new BuildException(e);
        } catch (ApkCreationException e) {
            throw new BuildException(e);
        }
    }

    /**
     * Creates an application package.
     * @param apkBuilder
     * @param configName the name of the filter config. Can be null in which case a full resource
     * package will be generated.
     * @param resourceFilter the resource configuration filter to pass to aapt (if configName is
     * non null)
     * @param path Ant {@link Path} to which add the generated APKs as {@link PathElement}
     * @param debugPackageSuffix suffix for the debug packages.
     * @throws FileNotFoundException
     * @throws ApkCreationException
     */
    private void createApk(ApkBuilderImpl apkBuilder, String configName, String resourceFilter,
            Path path, String debugPackageSuffix)
            throws FileNotFoundException, ApkCreationException {
        // All the files to be included in the archive have already been prep'ed up, except
        // the resource package.
        // figure out its name.
        String filename;
        if (configName != null && resourceFilter != null) {
            filename = mBaseName + "-" + configName + ".ap_";
        } else {
            filename = mBaseName + ".ap_";
        }

        // now we add it to the list of zip archive (it's just a zip file).

        // it's used as a zip archive input
        FileInputStream resoucePackageZipFile = new FileInputStream(new File(mOutFolder, filename));
        mZipArchives.add(resoucePackageZipFile);

        // prepare the filename to generate. Same thing as the resource file.
        if (configName != null && resourceFilter != null) {
            filename = mBaseName + "-" + configName;
        } else {
            filename = mBaseName;
        }

        if (mSigned) {
            filename = filename + debugPackageSuffix;
        } else {
            filename = filename + "-unsigned.apk";
        }

        if (configName == null || resourceFilter == null) {
            if (mSigned) {
                System.out.println(String.format(
                        "Creating %s and signing it with a debug key...", filename));
            } else {
                System.out.println(String.format(
                        "Creating %s for release...", filename));
            }
        } else {
            if (mSigned) {
                System.out.println(String.format(
                        "Creating %1$s (with %2$s) and signing it with a debug key...",
                        filename, resourceFilter));
            } else {
                System.out.println(String.format(
                        "Creating %1$s (with %2$s) for release...",
                        filename, resourceFilter));
            }
        }

        // out File
        File f = new File(mOutFolder, filename);

        // add it to the Path object
        PathElement element = path.createPathElement();
        element.setLocation(f);

        // and generate the apk
        apkBuilder.createPackage(f.getAbsoluteFile(), mZipArchives,
                mArchiveFiles, mJavaResources, mResourcesJars, mNativeLibraries);

        // we are done. We need to remove the resource package from the list of zip archives
        // in case we have another apk to generate.
        mZipArchives.remove(resoucePackageZipFile);
    }
}
