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

        String appPackage = getValidatedProperty(antProject, "package");
        System.out.println("Multi APK export for: " + appPackage);
        String version = getValidatedProperty(antProject, "versionCode");
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
            mXPathFactory = XPathFactory.newInstance();

            File exportProjectOutput = new File(getValidatedProperty(antProject,
                    "out.absolute.dir"));

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

                // first, do the full export.
                makeSubAnt(antProject, appPackage, versionCode, apk, null,
                        exportProjectOutput, canSign, keyStore, keyAlias,
                        keyStorePassword, keyAliasPassword, compiledProject);

                // then do the soft variants.
                for (Entry<String, String> entry : variantMap.entrySet()) {
                    makeSubAnt(antProject, appPackage, versionCode, apk, entry,
                            exportProjectOutput, canSign, keyStore, keyAlias,
                            keyStorePassword, keyAliasPassword, compiledProject);
                }

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
     * Creates and executes a sub ant task.
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
    private void makeSubAnt(Project antProject, String appPackage, int versionCode,
            ApkData apk, Entry<String, String> softVariant, File exportProjectOutput,
            boolean canSign, String keyStore, String keyAlias,
            String keyStorePassword, String keyAliasPassword, Set<String> compiledProject) {

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

//        subAnt.setVerbose(true);

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
                addProp(subAnt, "key.store", "");
                addProp(subAnt, "key.alias", "");
                // final file is the unsigned version. It gets stored locally.
                String outputName = finalNameRoot + "-unsigned.apk";
                apk.setOutputName(softVariant != null ? softVariant.getKey() : null, outputName);
                addProp(subAnt, "out.unsigned.file",
                        new File(exportProjectOutput, outputName).getAbsolutePath());
            }
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
