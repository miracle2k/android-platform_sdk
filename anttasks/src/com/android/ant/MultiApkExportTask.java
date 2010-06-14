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
import com.android.sdklib.internal.export.ProjectConfig;
import com.android.sdklib.internal.export.MultiApkExportHelper.ExportException;
import com.android.sdklib.internal.export.MultiApkExportHelper.Target;
import com.android.sdklib.internal.project.ProjectProperties;

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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

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
    private XPathFactory mXPathFactory;

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

        String appPackage = getValidatedProperty(antProject, ProjectProperties.PROPERTY_PACKAGE);
        System.out.println("Multi APK export for: " + appPackage);
        String version = getValidatedProperty(antProject, ProjectProperties.PROPERTY_VERSIONCODE);
        int versionCode;
        try {
            versionCode = Integer.parseInt(version);
        } catch (NumberFormatException e) {
            throw new BuildException("version value is not a valid integer.", e);
        }
        System.out.println("versionCode: " + version);

        // get the list of projects
        String projectList = getValidatedProperty(antProject, "projects");

        File rootFolder = antProject.getBaseDir();
        MultiApkExportHelper helper = new MultiApkExportHelper(rootFolder.getAbsolutePath(),
                appPackage, versionCode, mTarget, System.out);

        try {
            if (mTarget == Target.CLEAN) {
                // for a clean, we don't need the list of ApkData, we only need the list of
                // projects
                List<ProjectConfig> projects = helper.getProjects(projectList);
                for (ProjectConfig projectConfig : projects) {
                    executeCleanSubAnt(antProject, projectConfig);
                }
            } else {
                // checks whether the projects can be signed.
                String value = antProject.getProperty(ProjectProperties.PROPERTY_KEY_STORE);
                String keyStore = value != null && value.length() > 0 ? value : null;
                value = antProject.getProperty(ProjectProperties.PROPERTY_KEY_ALIAS);
                String keyAlias = value != null && value.length() > 0 ? value : null;
                boolean canSign = keyStore != null && keyAlias != null;

                List<ApkData> apks = helper.getApkData(projectList);

                // some temp var used by the project loop
                HashSet<String> compiledProject = new HashSet<String>();
                mXPathFactory = XPathFactory.newInstance();

                File exportProjectOutput = new File(
                        getValidatedProperty(antProject, "out.absolute.dir"));

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

                    Map<String, String> variantMap = apk.getSoftVariantMap();

                    if (variantMap.size() > 0) {
                        // if there are soft variants, only export those.
                        for (Entry<String, String> entry : variantMap.entrySet()) {
                            executeReleaseSubAnt(antProject, appPackage, versionCode, apk, entry,
                                    exportProjectOutput, canSign, keyStore, keyAlias,
                                    keyStorePassword, keyAliasPassword, compiledProject);
                        }
                    } else {
                        // do the full export.
                        executeReleaseSubAnt(antProject, appPackage, versionCode, apk, null,
                                exportProjectOutput, canSign, keyStore, keyAlias,
                                keyStorePassword, keyAliasPassword, compiledProject);

                    }
                }

                helper.writeLogs();
            }
        } catch (ExportException e) {
            // we only want to have Ant display the message, not the stack trace, since
            // we use Exceptions to report errors, so we build the BuildException only
            // with the message and not the cause exception.
            throw new BuildException(e.getMessage());
        }
    }

    /**
     * Creates and execute a clean sub ant task.
     * @param antProject the current Ant project
     * @param projectConfig the project to clean.
     */
    private void executeCleanSubAnt(Project antProject, ProjectConfig projectConfig) {

        String relativePath = projectConfig.getRelativePath();

        // this output is prepended by "[android-export] " (17 chars), so we put 61 stars
        System.out.println("\n*************************************************************");
        System.out.println("Cleaning project: " + relativePath);

        SubAnt subAnt = new SubAnt();
        subAnt.setTarget(mTarget.getTarget());
        subAnt.setProject(antProject);

        File subProjectFolder = projectConfig.getProjectFolder();

        FileSet fileSet = new FileSet();
        fileSet.setProject(antProject);
        fileSet.setDir(subProjectFolder);
        fileSet.setIncludes("build.xml");
        subAnt.addFileset(fileSet);

        // TODO: send the verbose flag from the main build.xml to the subAnt project.
        //subAnt.setVerbose(true);

        // end of the output by this task. Everything that follows will be output
        // by the subant.
        System.out.println("Calling to project's Ant file...");
        System.out.println("----------\n");

        subAnt.execute();
    }

    /**
     * Creates and executes a release sub ant task.
     * @param antProject the current Ant project
     * @param appPackage the application package string.
     * @param versionCode the current version of the application
     * @param apk the {@link ApkData} being exported.
     * @param softVariant the soft variant being exported, or null, if this is a full export.
     * @param exportProjectOutput the folder in which the files must be exported.
     * @param canSign whether the application package can be signed. This is dependent on the
     * availability of some required values.
     * @param keyStore the path to the keystore for signing
     * @param keyAlias the alias of the key to be used for signing
     * @param keyStorePassword the password of the keystore for signing
     * @param keyAliasPassword the password of the key alias for signing
     * @param compiledProject a list of projects that have already been compiled.
     */
    private void executeReleaseSubAnt(Project antProject, String appPackage, int versionCode,
            ApkData apk, Entry<String, String> softVariant, File exportProjectOutput,
            boolean canSign, String keyStore, String keyAlias,
            String keyStorePassword, String keyAliasPassword, Set<String> compiledProject) {

        String relativePath = apk.getProjectConfig().getRelativePath();

        // this output is prepended by "[android-export] " (17 chars), so we put 61 stars
        System.out.println("\n*************************************************************");
        System.out.println("Exporting project: " + relativePath);

        SubAnt subAnt = new SubAnt();
        subAnt.setTarget(mTarget.getTarget());
        subAnt.setProject(antProject);

        File subProjectFolder = apk.getProjectConfig().getProjectFolder();

        FileSet fileSet = new FileSet();
        fileSet.setProject(antProject);
        fileSet.setDir(subProjectFolder);
        fileSet.setIncludes("build.xml");
        subAnt.addFileset(fileSet);

        // TODO: send the verbose flag from the main build.xml to the subAnt project.
        //subAnt.setVerbose(true);

        // only do the compilation part if it's the first time we export
        // this project.
        // (projects can be export multiple time if some properties are set up to
        // generate more than one APK (for instance ABI split).
        if (compiledProject.contains(relativePath) == false) {
            compiledProject.add(relativePath);
        } else {
            addProp(subAnt, "do.not.compile", "true");
        }

        // set the version code, and filtering
        int compositeVersionCode = apk.getCompositeVersionCode(versionCode);
        addProp(subAnt, "version.code", Integer.toString(compositeVersionCode));
        System.out.println("Composite versionCode: " + compositeVersionCode);
        String abi = apk.getAbi();
        if (abi != null) {
            addProp(subAnt, "filter.abi", abi);
            System.out.println("ABI Filter: " + abi);
        }

        // set the output file names/paths. Keep all the temporary files in the project
        // folder, and only put the final file (which is different depending on whether
        // the file can be signed) locally.

        // read the base name from the build.xml file.
        String name = null;
        try {
            File buildFile = new File(subProjectFolder, "build.xml");
            XPath xPath = mXPathFactory.newXPath();
            name = xPath.evaluate("/project/@name",
                    new InputSource(new FileInputStream(buildFile)));
        } catch (XPathExpressionException e) {
            throw new BuildException("Failed to read build.xml", e);
        } catch (FileNotFoundException e) {
            throw new BuildException("build.xml is missing.", e);
        }

        // override the resource pack file as well as the final name
        String pkgName = name + "-" + apk.getBuildInfo();
        String finalNameRoot = appPackage + "-" + compositeVersionCode;
        if (softVariant != null) {
            String tmp = "-" + softVariant.getKey();
            pkgName += tmp;
            finalNameRoot += tmp;

            // set the resource filter.
            addProp(subAnt, "aapt.resource.filter", softVariant.getValue());
            System.out.println("res Filter: " + softVariant.getValue());
        }

        // set the resource pack file name.
        addProp(subAnt, "resource.package.file.name", pkgName + ".ap_");

        if (canSign) {
            // set the properties for the password.
            addProp(subAnt, ProjectProperties.PROPERTY_KEY_STORE, keyStore);
            addProp(subAnt, ProjectProperties.PROPERTY_KEY_ALIAS, keyAlias);
            addProp(subAnt, "key.store.password", keyStorePassword);
            addProp(subAnt, "key.alias.password", keyAliasPassword);

            // temporary file only get a filename change (still stored in the project
            // bin folder).
            addProp(subAnt, "out.unsigned.file.name",
                    name + "-" + apk.getBuildInfo() + "-unsigned.apk");
            addProp(subAnt, "out.unaligned.file",
                    name + "-" + apk.getBuildInfo() + "-unaligned.apk");

            // final file is stored locally with a name based on the package
            String outputName = finalNameRoot + "-release.apk";
            apk.setOutputName(softVariant != null ? softVariant.getKey() : null, outputName);
            addProp(subAnt, "out.release.file",
                    new File(exportProjectOutput, outputName).getAbsolutePath());

        } else {
            // put some empty prop. This is to override possible ones defined in the
            // project. The reason is that if there's more than one project, we don't
            // want some to signed and some not to be (and we don't want each project
            // to prompt for password.)
            addProp(subAnt, ProjectProperties.PROPERTY_KEY_STORE, "");
            addProp(subAnt, ProjectProperties.PROPERTY_KEY_ALIAS, "");
            // final file is the unsigned version. It gets stored locally.
            String outputName = finalNameRoot + "-unsigned.apk";
            apk.setOutputName(softVariant != null ? softVariant.getKey() : null, outputName);
            addProp(subAnt, "out.unsigned.file",
                    new File(exportProjectOutput, outputName).getAbsolutePath());
        }

        // end of the output by this task. Everything that follows will be output
        // by the subant.
        System.out.println("Calling to project's Ant file...");
        System.out.println("----------\n");

        subAnt.execute();
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
}
