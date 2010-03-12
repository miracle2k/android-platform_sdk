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

import com.android.sdklib.internal.project.ApkSettings;
import com.android.sdklib.internal.project.ProjectProperties;
import com.android.sdklib.internal.project.ProjectProperties.PropertyType;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.taskdefs.ExecTask;
import org.apache.tools.ant.types.Path;

import java.io.File;
import java.util.ArrayList;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Task able to run an Exec task on aapt several times.
 * <p>It does not follow the exec task format, instead it has its own parameters, which maps
 * directly to aapt.</p>
 * <p>The following map shows how to use the task for each supported aapt command line
 * parameter.</p>
 *
 * <table border="1">
 * <tr><td><b>Aapt Option</b></td><td><b>Ant Name</b></td><td><b>Type</b></td></tr>
 * <tr><td>path to aapt</td><td>executable</td><td>attribute (Path)</td>
 * <tr><td>command</td><td>command</td><td>attribute (String)</td>
 * <tr><td>-v</td><td>verbose</td><td>attribute (boolean)</td></tr>
 * <tr><td>-f</td><td>force</td><td>attribute (boolean)</td></tr>
 * <tr><td>-M AndroidManifest.xml</td><td>manifest</td><td>attribute (Path)</td></tr>
 * <tr><td>-I base-package</td><td>androidjar</td><td>attribute (Path)</td></tr>
 * <tr><td>-A asset-source-dir</td><td>assets</td><td>attribute (Path</td></tr>
 * <tr><td>-S resource-sources</td><td>&lt;res path=""&gt;</td><td>nested element(s)<br>with attribute (Path)</td></tr>
 * <tr><td>-0 extension</td><td>&lt;nocompress extension=""&gt;<br>&lt;nocompress&gt;</td><td>nested element(s)<br>with attribute (String)</td></tr>
 * <tr><td>-F apk-file</td><td>apkfolder<br>outfolder<br>apkbasename<br>basename</td><td>attribute (Path)<br>attribute (Path) deprecated<br>attribute (String)<br>attribute (String) deprecated</td></tr>
 * <tr><td>-J R-file-dir</td><td>rfolder</td><td>attribute (Path)<br>-m always enabled</td></tr>
 * <tr><td></td><td></td><td></td></tr>
 * </table>
 */
public final class AaptExecLoopTask extends Task {

    /**
     * Class representing a &lt;nocompress&gt; node in the main task XML.
     * This let the developers prevent compression of some files in assets/ and res/raw/
     * by extension.
     * If the extension is null, this will disable compression for all  files in assets/ and
     * res/raw/
     */
    public final static class NoCompress {
        String mExtension;

        /**
         * Sets the value of the "extension" attribute.
         * @param extention the extension.
         */
        public void setExtension(String extention) {
            mExtension = extention;
        }
    }

    private String mExecutable;
    private String mCommand;
    private boolean mForce = true; // true due to legacy reasons
    private boolean mVerbose = false;
    private String mManifest;
    private ArrayList<Path> mResources;
    private String mAssets;
    private String mAndroidJar;
    private String mApkFolder;
    private String mApkBaseName;
    private String mRFolder;
    private final ArrayList<NoCompress> mNoCompressList = new ArrayList<NoCompress>();

    /**
     * Sets the value of the "executable" attribute.
     * @param executable the value.
     */
    public void setExecutable(Path executable) {
        mExecutable = TaskHelper.checkSinglePath("executable", executable);
    }

    /**
     * Sets the value of the "command" attribute.
     * @param command the value.
     */
    public void setCommand(String command) {
        mCommand = command;
    }

    /**
     * Sets the value of the "force" attribute.
     * @param force the value.
     */
    public void setForce(boolean force) {
        mForce = force;
    }

    /**
     * Sets the value of the "verbose" attribute.
     * @param verbose the value.
     */
    public void setVerbose(boolean verbose) {
        mVerbose = verbose;
    }

    /**
     * Sets the value of the "manifest" attribute.
     * @param manifest the value.
     */
    public void setManifest(Path manifest) {
        mManifest = TaskHelper.checkSinglePath("manifest", manifest);
    }

    /**
     * Sets the value of the "resources" attribute.
     * @param resources the value.
     *
     * @deprecated Uses nested element(s) <res path="value" />
     */
    @Deprecated
    public void setResources(Path resources) {
        if (mResources == null) {
            mResources = new ArrayList<Path>();
        }

        mResources.add(new Path(getProject(), resources.toString()));
    }

    /**
     * Sets the value of the "assets" attribute.
     * @param assets the value.
     */
    public void setAssets(Path assets) {
        mAssets = TaskHelper.checkSinglePath("assets", assets);
    }

    /**
     * Sets the value of the "androidjar" attribute.
     * @param androidJar the value.
     */
    public void setAndroidjar(Path androidJar) {
        mAndroidJar = TaskHelper.checkSinglePath("androidjar", androidJar);
    }

    /**
     * Sets the value of the "outfolder" attribute.
     * @param outFolder the value.
     * @deprecated use {@link #setApkfolder(Path)}
     */
    @Deprecated
    public void setOutfolder(Path outFolder) {
        mApkFolder = TaskHelper.checkSinglePath("outfolder", outFolder);
    }

    /**
     * Sets the value of the "apkfolder" attribute.
     * @param apkFolder the value.
     */
    public void setApkfolder(Path apkFolder) {
        mApkFolder = TaskHelper.checkSinglePath("apkfolder", apkFolder);
    }

    /**
     * Sets the value of the "basename" attribute.
     * @param baseName the value.
     * @deprecated use {@link #setApkbasename(String)}
     */
    @Deprecated
    public void setBasename(String baseName) {
        mApkBaseName = baseName;
    }

    /**
     * Sets the value of the "apkbasename" attribute.
     * @param apkbaseName the value.
     */
    public void setApkbasename(String apkbaseName) {
        mApkBaseName = apkbaseName;
    }

    /**
     * Sets the value of the "rfolder" attribute.
     * @param rFolder the value.
     */
    public void setRfolder(Path rFolder) {
        mRFolder = TaskHelper.checkSinglePath("rfolder", rFolder);
    }

    /**
     * Returns an object representing a nested <var>nocompress</var> element.
     */
    public Object createNocompress() {
        NoCompress nc = new NoCompress();
        mNoCompressList.add(nc);
        return nc;
    }

    /**
     * Returns an object representing a nested <var>res</var> element.
     */
    public Object createRes() {
        if (mResources == null) {
            mResources = new ArrayList<Path>();
        }

        Path path = new Path(getProject());
        mResources.add(path);

        return path;
    }

    /*
     * (non-Javadoc)
     *
     * Executes the loop. Based on the values inside default.properties, this will
     * create alternate temporary ap_ files.
     *
     * @see org.apache.tools.ant.Task#execute()
     */
    @Override
    public void execute() throws BuildException {
        Project taskProject = getProject();

        // first do a full resource package
        createPackage(null /*configName*/, null /*resourceFilter*/, null /*customPackage*/);

        if (mRFolder != null && new File(mRFolder).isDirectory()) {
            String libPkgProp = taskProject.getProperty("android.libraries.package");
            if (libPkgProp != null) {
                // get the main package to compare in case the libraries use the same
                String mainPackage = taskProject.getProperty("manifest.package");

                String[] libPkgs = libPkgProp.split(";");
                for (String libPkg : libPkgs) {
                    if (libPkg.length() > 0 && mainPackage.equals(libPkg) == false) {
                        // FIXME: instead of recreating R.java from scratch, maybe copy
                        // the files (R.java and manifest.java)? This would force to replace
                        // the package line on the fly.
                        createPackage(null, null, libPkg);
                    }
                }
            }
        }

        // now see if we need to create file with filtered resources.
        // Get the project base directory.
        File baseDir = taskProject.getBaseDir();
        ProjectProperties properties = ProjectProperties.load(baseDir.getAbsolutePath(),
                PropertyType.DEFAULT);


        ApkSettings apkSettings = new ApkSettings(properties);
        if (apkSettings != null) {
            Map<String, String> apkFilters = apkSettings.getResourceFilters();
            if (apkFilters.size() > 0) {
                for (Entry<String, String> entry : apkFilters.entrySet()) {
                    createPackage(entry.getKey(), entry.getValue(), null /*custom package*/);
                }
            }
        }
    }

    /**
     * Creates a resource package.
     * @param configName the name of the filter config. Can be null in which case a full resource
     * package will be generated.
     * @param resourceFilter the resource configuration filter to pass to aapt (if configName is
     * non null)
     */
    private void createPackage(String configName, String resourceFilter, String customPackage) {
        Project taskProject = getProject();

        final boolean generateRClass = mRFolder != null && new File(mRFolder).isDirectory();

        if (generateRClass) {
        } else if (configName == null || resourceFilter == null) {
            System.out.println("Creating full resource package...");
        } else {
            System.out.println(String.format(
                    "Creating resource package for config '%1$s' (%2$s)...",
                    configName, resourceFilter));
        }

        // create a task for the default apk.
        ExecTask task = new ExecTask();
        task.setExecutable(mExecutable);
        task.setFailonerror(true);

        // aapt command. Only "package" is supported at this time really.
        task.createArg().setValue(mCommand);

        // force flag
        if (mForce) {
            task.createArg().setValue("-f");
        }

        // verbose flag
        if (mVerbose) {
            task.createArg().setValue("-v");
        }

        if (generateRClass) {
            task.createArg().setValue("-m");
        }

        // filters if needed
        if (configName != null && resourceFilter != null) {
            task.createArg().setValue("-c");
            task.createArg().setValue(resourceFilter);
        }

        // no compress flag
        // first look to see if there's a NoCompress object with no specified extension
        boolean compressNothing = false;
        for (NoCompress nc : mNoCompressList) {
            if (nc.mExtension == null) {
                task.createArg().setValue("-0");
                task.createArg().setValue("");
                compressNothing = true;
                break;
            }
        }

        if (compressNothing == false) {
            for (NoCompress nc : mNoCompressList) {
                task.createArg().setValue("-0");
                task.createArg().setValue(nc.mExtension);
            }
        }

        if (customPackage != null) {
            task.createArg().setValue("--custom-package");
            task.createArg().setValue(customPackage);
        }

        // if the project contains libraries, force auto-add-overlay
        Object libSrc = taskProject.getReference("android.libraries.res");
        if (libSrc != null) {
            task.createArg().setValue("--auto-add-overlay");
        }

        // manifest location
        if (mManifest != null) {
            task.createArg().setValue("-M");
            task.createArg().setValue(mManifest);
        }

        // resources locations.
        if (mResources.size() > 0) {
            for (Path pathList : mResources) {
                for (String path : pathList.list()) {
                    // This may not exists, and aapt doesn't like it, so we check first.
                    File res = new File(path);
                    if (res.isDirectory()) {
                        task.createArg().setValue("-S");
                        task.createArg().setValue(path);
                    }
                }
            }
        }

        // add other resources coming from library project
        Object libPath = taskProject.getReference("android.libraries.res");
        if (libPath instanceof Path) {
            for (String path : ((Path)libPath).list()) {
                // This may not exists, and aapt doesn't like it, so we check first.
                File res = new File(path);
                if (res.isDirectory()) {
                    task.createArg().setValue("-S");
                    task.createArg().setValue(path);
                }
            }
        }

        // assets location. This may not exists, and aapt doesn't like it, so we check first.
        if (mAssets != null && new File(mAssets).isDirectory()) {
            task.createArg().setValue("-A");
            task.createArg().setValue(mAssets);
        }

        // android.jar
        if (mAndroidJar != null) {
            task.createArg().setValue("-I");
            task.createArg().setValue(mAndroidJar);
        }

        // apk file. This is based on the apkFolder, apkBaseName, and the configName (if applicable)
        if (mApkBaseName != null && mApkBaseName != null) {
            String filename;
            if (configName != null && resourceFilter != null) {
                filename = mApkBaseName + "-" + configName + ".ap_";
            } else {
                filename = mApkBaseName + ".ap_";
            }

            File file = new File(mApkFolder, filename);
            task.createArg().setValue("-F");
            task.createArg().setValue(file.getAbsolutePath());
        }

        // R class generation
        if (generateRClass) {
            task.createArg().setValue("-J");
            task.createArg().setValue(mRFolder);
        }

        // final setup of the task
        task.setProject(taskProject);
        task.setOwningTarget(getOwningTarget());

        // execute it.
        task.execute();
    }
}
