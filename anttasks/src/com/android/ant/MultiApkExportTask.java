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

package com.android.ant;

import com.android.sdklib.internal.export.ApkData;
import com.android.sdklib.internal.export.MultiApkExportHelper;
import com.android.sdklib.internal.export.MultiApkExportHelper.ExportException;
import com.android.sdklib.internal.export.MultiApkExportHelper.Target;
import com.android.sdklib.io.FileWrapper;
import com.android.sdklib.io.IAbstractFile;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.taskdefs.Input;
import org.apache.tools.ant.taskdefs.Property;
import org.apache.tools.ant.taskdefs.SubAnt;
import org.apache.tools.ant.types.FileSet;
import org.xml.sax.InputSource;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.HashSet;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

/**
 * Multiple APK export task.
 * This task is meant to replace {@link SetupTask} as the main setup/export task, importing
 * the rules and generating the export for all projects.
 */
public class MultiApkExportTask extends Task {

    private Target mTarget;

    public void setTarget(String target) {
        mTarget = Target.getTarget(target);
    }

    @Override
    public void execute() throws BuildException {
        Project antProject = getProject();

        if (mTarget == null) {
            throw new BuildException("'target' attribute not set.");
        }

        // get the SDK location
        File sdk = TaskHelper.getSdkLocation(antProject);

        // display SDK Tools revision
        int toolsRevison = TaskHelper.getToolsRevision(sdk);
        if (toolsRevison != -1) {
            System.out.println("Android SDK Tools Revision " + toolsRevison);
        }

        String appPackage = getValidatedProperty(antProject, "package");
        System.out.println("Multi APK export for: " + appPackage);
        String version = getValidatedProperty(antProject, "version");
        int versionCode;
        try {
            versionCode = Integer.parseInt(version);
        } catch (NumberFormatException e) {
            throw new BuildException("version value is not a valid integer.", e);
        }
        System.out.println("versionCode: " + version);

        // checks whether the projects can be signed.
        boolean canSign = false;
        String keyStore = null, keyAlias = null;
        if (mTarget == Target.RELEASE) {
            String value = antProject.getProperty("key.store");
            keyStore = value != null && value.length() > 0 ? value : null;
            value = antProject.getProperty("key.alias");
            keyAlias = value != null && value.length() > 0 ? value : null;
            canSign = keyStore != null && keyAlias != null;
        }

        // get the list of projects
        String projects = getValidatedProperty(antProject, "projects");

        // look to see if there's an export log from a previous export
        IAbstractFile log = getBuildLog(appPackage, versionCode);

        MultiApkExportHelper helper = new MultiApkExportHelper(appPackage, versionCode, mTarget);
        try {
            ApkData[] apks = helper.getProjects(projects, log);

            // some temp var used by the project loop
            HashSet<String> compiledProject = new HashSet<String>();
            XPathFactory xPathFactory = XPathFactory.newInstance();

            File exportProjectOutput = new File(getValidatedProperty(antProject, "out.absolute.dir"));

            // if there's no error, and we can sign, prompt for the passwords.
            String keyStorePassword = null;
            String keyAliasPassword = null;
            if (canSign) {
                System.out.println("Found signing keystore and key alias. Need passwords.");

                Input input = new Input();
                input.setProject(antProject);
                input.setAddproperty("key.store.password");
                input.setMessage(String.format("Please enter keystore password (store: %1$s):",
                        keyStore));
                input.execute();

                input = new Input();
                input.setProject(antProject);
                input.setAddproperty("key.alias.password");
                input.setMessage(String.format("Please enter password for alias '%1$s':",
                        keyAlias));
                input.execute();

                // and now read the property so that they can be set into the sub ant task.
                keyStorePassword = getValidatedProperty(antProject, "key.store.password");
                keyAliasPassword = getValidatedProperty(antProject, "key.alias.password");
            }

            for (ApkData apk : apks) {
                // this output is prepended by "[android-export] " (17 chars), so we put 61 stars
                System.out.println("\n*************************************************************");
                System.out.println("Exporting project: " + apk.getRelativePath());

                SubAnt subAnt = new SubAnt();
                subAnt.setTarget(mTarget.getTarget());
                subAnt.setProject(antProject);

                File subProjectFolder = new File(antProject.getBaseDir(), apk.getRelativePath());

                FileSet fileSet = new FileSet();
                fileSet.setProject(antProject);
                fileSet.setDir(subProjectFolder);
                fileSet.setIncludes("build.xml");
                subAnt.addFileset(fileSet);

    //            subAnt.setVerbose(true);

                if (mTarget == Target.RELEASE) {
                    // only do the compilation part if it's the first time we export
                    // this project.
                    // (projects can be export multiple time if some properties are set up to
                    // generate more than one APK (for instance ABI split).
                    if (compiledProject.contains(apk.getRelativePath()) == false) {
                        compiledProject.add(apk.getRelativePath());
                    } else {
                        addProp(subAnt, "do.not.compile", "true");
                    }

                    // set the version code, and filtering
                    String compositeVersionCode = getVersionCodeString(versionCode, apk);
                    addProp(subAnt, "version.code", compositeVersionCode);
                    System.out.println("Composite versionCode: " + compositeVersionCode);
                    String abi = apk.getAbi();
                    if (abi != null) {
                        addProp(subAnt, "filter.abi", abi);
                        System.out.println("ABI Filter: " + abi);
                    }

                    // end of the output by this task. Everything that follows will be output
                    // by the subant.
                    System.out.println("Calling to project's Ant file...");
                    System.out.println("----------\n");

                    // set the output file names/paths. Keep all the temporary files in the project
                    // folder, and only put the final file (which is different depending on whether
                    // the file can be signed) locally.

                    // read the base name from the build.xml file.
                    String name = null;
                    try {
                        File buildFile = new File(subProjectFolder, "build.xml");
                        XPath xPath = xPathFactory.newXPath();
                        name = xPath.evaluate("/project/@name",
                                new InputSource(new FileInputStream(buildFile)));
                    } catch (XPathExpressionException e) {
                        throw new BuildException("Failed to read build.xml", e);
                    } catch (FileNotFoundException e) {
                        throw new BuildException("build.xml is missing.", e);
                    }

                    // override the resource pack file.
                    addProp(subAnt, "resource.package.file.name",
                            name + "-" + apk.getBuildInfo() + ".ap_");

                    if (canSign) {
                        // set the properties for the password.
                        addProp(subAnt, "key.store", keyStore);
                        addProp(subAnt, "key.alias", keyAlias);
                        addProp(subAnt, "key.store.password", keyStorePassword);
                        addProp(subAnt, "key.alias.password", keyAliasPassword);

                        // temporary file only get a filename change (still stored in the project
                        // bin folder).
                        addProp(subAnt, "out.unsigned.file.name",
                                name + "-" + apk.getBuildInfo() + "-unsigned.apk");
                        addProp(subAnt, "out.unaligned.file",
                                name + "-" + apk.getBuildInfo() + "-unaligned.apk");

                        // final file is stored locally.
                        apk.setOutputName(name + "-" + compositeVersionCode + "-release.apk");
                        addProp(subAnt, "out.release.file", new File(exportProjectOutput,
                                apk.getOutputName()).getAbsolutePath());

                    } else {
                        // put some empty prop. This is to override possible ones defined in the
                        // project. The reason is that if there's more than one project, we don't
                        // want some to signed and some not to be (and we don't want each project
                        // to prompt for password.)
                        addProp(subAnt, "key.store", "");
                        addProp(subAnt, "key.alias", "");
                        // final file is the unsigned version. It gets stored locally.
                        apk.setOutputName(name + "-" + compositeVersionCode + "-unsigned.apk");
                        addProp(subAnt, "out.unsigned.file", new File(exportProjectOutput,
                                apk.getOutputName()).getAbsolutePath());
                    }
                }

                subAnt.execute();
            }

            if (mTarget == Target.RELEASE) {
                helper.makeBuildLog(log, apks);
            }
        } catch (ExportException e) {
            // we only want to have Ant display the message, not the stack trace, since
            // we use Exceptions to report errors, so we build the BuildException only
            // with the message and not the cause exception.
            throw new BuildException(e.getMessage());
        }
    }

    /**
     * Gets, validates and returns a project property.
     * The property must exist and be non empty.
     * @param antProject the project
     * @param name the name of the property to return.
     * @return the property value always (cannot be null).
     * @throws BuildException if the property is missing or not valid.
     */
    private String getValidatedProperty(Project antProject, String name) {
        String value = antProject.getProperty(name);
        if (value == null || value.length() == 0) {
            throw new BuildException(String.format("Property '%1$s' is not set or empty.", name));
        }

        return value;
    }


    /**
     * Adds a property to a {@link SubAnt} task.
     * @param task the task.
     * @param name the name of the property.
     * @param value the value of the property.
     */
    private void addProp(SubAnt task, String name, String value) {
        Property prop = new Property();
        prop.setName(name);
        prop.setValue(value);
        task.addProperty(prop);
    }

    /**
     * Computes and returns the composite version code
     * @param versionCode the major version code.
     * @param apkData the apk data.
     * @return the composite versionCode to be used in the manifest.
     */
    private String getVersionCodeString(int versionCode, ApkData apkData) {
        int trueVersionCode = versionCode * MultiApkExportHelper.OFFSET_VERSION_CODE;
        trueVersionCode += apkData.getBuildInfo() * MultiApkExportHelper.OFFSET_BUILD_INFO;
        trueVersionCode += apkData.getMinor();

        return Integer.toString(trueVersionCode);
    }

    /**
     * Returns the {@link File} for the build log.
     * @param appPackage
     * @param versionCode
     * @return A new non-null {@link IAbstractFile} mapping to the build log.
     */
    private IAbstractFile getBuildLog(String appPackage, int versionCode) {
        return new FileWrapper(appPackage + "." + versionCode + ".log");
    }
}
