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

import com.android.sdklib.SdkConstants;
import com.android.sdklib.internal.project.ApkSettings;
import com.android.sdklib.internal.project.ProjectProperties;
import com.android.sdklib.internal.project.ProjectProperties.PropertyType;
import com.android.sdklib.io.FileWrapper;
import com.android.sdklib.io.StreamException;
import com.android.sdklib.xml.AndroidManifest;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.taskdefs.Property;
import org.apache.tools.ant.taskdefs.SubAnt;
import org.apache.tools.ant.types.FileSet;
import org.xml.sax.InputSource;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

/**
 * Multiple APK export task.
 * This task is meant to replace {@link SetupTask} as the main setup/export task, importing
 * the rules and generating the export for all projects.
 */
public class MultiApkExportTask extends Task {

    /**
     * Class representing one apk that needs to be generated. This contains
     * which project it must be created from, and which filters should be used.
     *
     * This class is meant to be sortable in a way that allows generation of the buildInfo
     * value that goes in the composite versionCode.
     */
    private static class ExportData implements Comparable<ExportData> {

        String relativePath;
        File project;
        int buildInfo;
        int minor;

        // the following are used to sort the export data and generate buildInfo
        int minSdkVersion;
        String abi;
        int glVersion;
        // screen size?

        ExportData() {
            // do nothing.
        }

        public ExportData(ExportData data) {
            relativePath = data.relativePath;
            project = data.project;
            buildInfo = data.buildInfo;
            minor = data.buildInfo;
            minSdkVersion = data.minSdkVersion;
            abi = data.abi;
            glVersion = data.glVersion;
        }

        public int compareTo(ExportData o) {
            int minSdkDiff = minSdkVersion - o.minSdkVersion;
            if (minSdkDiff != 0) {
                return minSdkDiff;
            }

            if (abi != null) {
                if (o.abi != null) {
                    return abi.compareTo(o.abi);
                } else {
                    return -1;
                }
            } else if (o.abi != null) {
                return 1;
            }

            if (glVersion != 0) {
                if (o.glVersion != 0) {
                    return glVersion - o.glVersion;
                } else {
                    return -1;
                }
            } else if (o.glVersion != 0) {
                return 1;
            }

            return 0;
        }
    }

    private static enum Target {
        RELEASE("release"), CLEAN("clean");

        private final String mName;

        Target(String name) {
            mName = name;
        }

        String getTarget() {
            return mName;
        }

        static Target getTarget(String value) {
            for (Target t : values()) {
                if (t.mName.equals(value)) {
                    return t;
                }

            }

            return null;
        }
    }

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
        boolean canSign = true;


        ExportData[] projects = getProjects(antProject, appPackage);
        HashSet<String> compiledProject = new HashSet<String>();

        XPathFactory xPathFactory = XPathFactory.newInstance();

        File exportProjectOutput = new File(getValidatedProperty(antProject, "out.absolute.dir"));

        for (ExportData projectData : projects) {
            // this output is prepended by "[android-export] " (17 chars), so we put 61 stars
            System.out.println("\n*************************************************************");
            System.out.println("Exporting project: " + projectData.relativePath);

            SubAnt subAnt = new SubAnt();
            subAnt.setTarget(mTarget.getTarget());
            subAnt.setProject(antProject);

            File subProjectFolder = new File(antProject.getBaseDir(), projectData.relativePath);

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
                if (compiledProject.contains(projectData.relativePath) == false) {
                    compiledProject.add(projectData.relativePath);
                } else {
                    addProp(subAnt, "do.not.compile", "true");
                }

                // set the version code, and filtering
                String compositeVersionCode = getVersionCodeString(versionCode, projectData);
                addProp(subAnt, "version.code", compositeVersionCode);
                System.out.println("Composite versionCode: " + compositeVersionCode);
                if (projectData.abi != null) {
                    addProp(subAnt, "filter.abi", projectData.abi);
                    System.out.println("ABI Filter: " + projectData.abi);
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
                        name + "-" + projectData.buildInfo + ".ap_");

                if (canSign) {
                    // temporary file only get a filename change (still stored in the project
                    // bin folder).
                    addProp(subAnt, "out.unsigned.file.name",
                            name + "-" + projectData.buildInfo + "-unsigned.apk");
                    addProp(subAnt, "out.unaligned.file",
                            name + "-" + projectData.buildInfo + "-unaligned.apk");

                    // final file is stored locally.
                    addProp(subAnt, "out.release.file", new File(exportProjectOutput,
                            name + "-" + projectData.buildInfo + "-release.apk").getAbsolutePath());
                } else {
                    // final file is the unsigned version. It gets stored locally.
                    addProp(subAnt, "out.unsigned.file", new File(exportProjectOutput,
                            name + "-" + projectData.buildInfo + "-unsigned.apk").getAbsolutePath());
                }
            }

            subAnt.execute();
        }

        // TODO: export build log.

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
     * gets the projects to export from the property, checks they exist, validates them,
     * loads their export info and return it.
     * If a project does not exist or is not valid, this will throw a {@link BuildException}.
     * @param antProject the Ant project.
     * @param appPackage the application package. Projects' manifest must match this.
     */
    private ExportData[] getProjects(Project antProject, String appPackage) {
        String projects = antProject.getProperty("projects");
        String[] paths = projects.split("\\:");

        ArrayList<ExportData> datalist = new ArrayList<ExportData>();

        for (String path : paths) {
            File projectFolder = new File(path);

            // resolve the path (to remove potential ..)
            try {
                projectFolder = projectFolder.getCanonicalFile();

                // project folder must exist and be a directory
                if (projectFolder.isDirectory() == false) {
                    throw new BuildException(String.format(
                            "Project folder '%1$s' is not a valid directory.",
                            projectFolder.getAbsolutePath()));
                }

                // Check AndroidManifest.xml is present
                FileWrapper androidManifest = new FileWrapper(projectFolder,
                        SdkConstants.FN_ANDROID_MANIFEST_XML);

                if (androidManifest.isFile() == false) {
                    throw new BuildException(String.format(
                            "%1$s is not a valid project (%2$s not found).",
                            projectFolder.getAbsolutePath(),
                            SdkConstants.FN_ANDROID_MANIFEST_XML));
                }

                ArrayList<ExportData> datalist2 = checkManifest(androidManifest, appPackage);

                // if the method returns without throwing, this is a good project to
                // export.
                for (ExportData data : datalist2) {
                    data.relativePath = path;
                    data.project = projectFolder;
                }

                datalist.addAll(datalist2);

            } catch (IOException e) {
                throw new BuildException(String.format("Failed to resolve path %1$s", path), e);
            }
        }

        // sort the projects and assign buildInfo
        Collections.sort(datalist);
        int buildInfo = 0;
        for (ExportData data : datalist) {
            data.buildInfo = buildInfo++;
        }

        return datalist.toArray(new ExportData[datalist.size()]);
    }

    private ArrayList<ExportData> checkManifest(FileWrapper androidManifest, String appPackage) {
        try {
            String manifestPackage = AndroidManifest.getPackage(androidManifest);
            if (appPackage.equals(manifestPackage) == false) {
                throw new BuildException(String.format(
                        "%1$s package value is not valid. Found '%2$s', expected '%3$s'.",
                        androidManifest.getPath(), manifestPackage, appPackage));
            }

            if (AndroidManifest.hasVersionCode(androidManifest)) {
                throw new BuildException(String.format(
                        "%1$s is not valid: versionCode must not be set for multi-apk export.",
                        androidManifest.getPath()));
            }

            int minSdkVersion = AndroidManifest.getMinSdkVersion(androidManifest);
            if (minSdkVersion == -1) {
                throw new BuildException(
                        "Codename in minSdkVersion is not supported by multi-apk export.");
            }

            ArrayList<ExportData> dataList = new ArrayList<ExportData>();
            ExportData data = new ExportData();
            dataList.add(data);
            data.minSdkVersion = minSdkVersion;

            // only look for more exports if the target is not clean.
            if (mTarget != Target.CLEAN) {
                // load the project properties
                String projectPath = androidManifest.getParent();
                ProjectProperties projectProp = ProjectProperties.load(projectPath,
                        PropertyType.DEFAULT);
                if (projectProp == null) {
                    throw new BuildException(String.format(
                            "%1$s is missing.", PropertyType.DEFAULT.getFilename()));
                }

                ApkSettings apkSettings = new ApkSettings(projectProp);
                if (apkSettings.isSplitByAbi()) {
                    // need to find the available ABIs.
                    List<String> abis = findAbis(projectPath);
                    ExportData current = data;
                    for (String abi : abis) {
                        if (current == null) {
                            current = new ExportData(data);
                            dataList.add(current);
                        }

                        current.abi = abi;
                        current = null;
                    }
                }
            }

            return dataList;
        } catch (XPathExpressionException e) {
            throw new BuildException(
                    String.format("Failed to validate %1$s", androidManifest.getPath()), e);
        } catch (StreamException e) {
            throw new BuildException(
                    String.format("Failed to validate %1$s", androidManifest.getPath()), e);
        }
    }

    private List<String> findAbis(String projectPath) {
        ArrayList<String> abiList = new ArrayList<String>();
        File libs = new File(projectPath, SdkConstants.FD_NATIVE_LIBS);
        if (libs.isDirectory()) {
            File[] abis = libs.listFiles();
            for (File abi : abis) {
                if (abi.isDirectory()) {
                    // only add the abi folder if there are .so files in it.
                    String[] content = abi.list(new FilenameFilter() {
                        public boolean accept(File dir, String name) {
                            return name.toLowerCase().endsWith(".so");
                        }
                    });

                    if (content.length > 0) {
                        abiList.add(abi.getName());
                    }
                }
            }
        }

        return abiList;
    }

    private void addProp(SubAnt task, String name, String value) {
        Property prop = new Property();
        prop.setName(name);
        prop.setValue(value);
        task.addProperty(prop);
    }

    private String getVersionCodeString(int versionCode, ExportData projectData) {
        int trueVersionCode = versionCode * 10000;
        trueVersionCode += projectData.buildInfo * 100;
        trueVersionCode += projectData.minor;

        return Integer.toString(trueVersionCode);
    }

}
