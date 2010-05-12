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
import org.apache.tools.ant.taskdefs.Input;
import org.apache.tools.ant.taskdefs.Property;
import org.apache.tools.ant.taskdefs.SubAnt;
import org.apache.tools.ant.types.FileSet;
import org.xml.sax.InputSource;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
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

    private final static int MAX_MINOR = 100;
    private final static int MAX_BUILDINFO = 100;
    private final static int OFFSET_BUILD_INFO = MAX_MINOR;
    private final static int OFFSET_VERSION_CODE = OFFSET_BUILD_INFO * MAX_BUILDINFO;

    /**
     * Class representing one apk that needs to be generated. This contains
     * which project it must be created from, and which filters should be used.
     *
     * This class is meant to be sortable in a way that allows generation of the buildInfo
     * value that goes in the composite versionCode.
     */
    public static class ApkData implements Comparable<ApkData> {

        private final static int INDEX_OUTPUTNAME = 0;
        private final static int INDEX_PROJECT    = 1;
        private final static int INDEX_MINOR      = 2;
        private final static int INDEX_MINSDK     = 3;
        private final static int INDEX_ABI        = 4;
        private final static int INDEX_MAX        = 5;

        String outputName;
        String relativePath;
        File project;
        int buildInfo;
        int minor;

        // the following are used to sort the export data and generate buildInfo
        int minSdkVersion;
        String abi;
        int glVersion;
        // screen size?

        public ApkData() {
            // do nothing.
        }

        public ApkData(ApkData data) {
            relativePath = data.relativePath;
            project = data.project;
            buildInfo = data.buildInfo;
            minor = data.buildInfo;
            minSdkVersion = data.minSdkVersion;
            abi = data.abi;
            glVersion = data.glVersion;
        }

        public String toString() {
            StringBuilder sb = new StringBuilder(outputName);
            sb.append(" / ").append(relativePath);
            sb.append(" / ").append(buildInfo);
            sb.append(" / ").append(minor);
            sb.append(" / ").append(minSdkVersion);
            sb.append(" / ").append(abi);

            return sb.toString();
        }

        public int compareTo(ApkData o) {
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

        /**
         * Writes the apk description in the given writer. a single line is used to write
         * everything.
         * @param writer
         * @throws IOException
         *
         * @see {@link #read(String)}
         */
        public void write(FileWriter writer) throws IOException {
            for (int i = 0 ; i < ApkData.INDEX_MAX ; i++) {
                write(i, writer);
            }
        }

        /**
         * reads the apk description from a log line.
         * @param data
         *
         * @see #write(FileWriter)
         */
        public void read(String line) {
            String[] dataStrs = line.split(",");
            for (int i = 0 ; i < ApkData.INDEX_MAX ; i++) {
                read(i, dataStrs);
            }
        }

        private void write(int index, FileWriter writer) throws IOException {
            switch (index) {
                case INDEX_OUTPUTNAME:
                    writeValue(writer, outputName);
                    break;
                case INDEX_PROJECT:
                    writeValue(writer, relativePath);
                    break;
                case INDEX_MINOR:
                    writeValue(writer, minor);
                    break;
                case INDEX_MINSDK:
                    writeValue(writer, minSdkVersion);
                    break;
                case INDEX_ABI:
                    writeValue(writer, abi != null ? abi : "");
                    break;
            }
        }

        private void read(int index, String[] data) {
            switch (index) {
                case INDEX_OUTPUTNAME:
                    outputName = data[index];
                    break;
                case INDEX_PROJECT:
                    relativePath = data[index];
                    break;
                case INDEX_MINOR:
                    minor = Integer.parseInt(data[index]);
                    break;
                case INDEX_MINSDK:
                    minSdkVersion = Integer.parseInt(data[index]);
                    break;
                case INDEX_ABI:
                    if (index < data.length && data[index].length() > 0) {
                        abi = data[index];
                    }
                    break;
            }
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
        boolean canSign = false;
        String keyStore = null, keyAlias = null;
        if (mTarget == Target.RELEASE) {
            String value = antProject.getProperty("key.store");
            keyStore = value != null && value.length() > 0 ? value : null;
            value = antProject.getProperty("key.alias");
            keyAlias = value != null && value.length() > 0 ? value : null;
            canSign = keyStore != null && keyAlias != null;
        }

        // get the list of apk to export and their configuration.
        ApkData[] apks = getProjects(antProject, appPackage);

        // look to see if there's an export log from a previous export
        File log = getBuildLog(appPackage, versionCode);
        if (mTarget == Target.RELEASE && log.isFile()) {
            // load the log and compare to current export list.
            // Any difference will force a new versionCode.
            ApkData[] previousApks = getProjects(log);

            if (previousApks.length != apks.length) {
                throw new BuildException(String.format(
                        "Project export is setup differently from previous export at versionCode %d.\n" +
                        "Any change in the multi-apk configuration require a increment of the versionCode.",
                        versionCode));
            }

            for (int i = 0 ; i < previousApks.length ; i++) {
                // update the minor value from what is in the log file.
                apks[i].minor = previousApks[i].minor;
                if (apks[i].compareTo(previousApks[i]) != 0) {
                    throw new BuildException(String.format(
                            "Project export is setup differently from previous export at versionCode %d.\n" +
                            "Any change in the multi-apk configuration require a increment of the versionCode.",
                            versionCode));
                }
            }
        }

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
            System.out.println("Exporting project: " + apk.relativePath);

            SubAnt subAnt = new SubAnt();
            subAnt.setTarget(mTarget.getTarget());
            subAnt.setProject(antProject);

            File subProjectFolder = new File(antProject.getBaseDir(), apk.relativePath);

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
                if (compiledProject.contains(apk.relativePath) == false) {
                    compiledProject.add(apk.relativePath);
                } else {
                    addProp(subAnt, "do.not.compile", "true");
                }

                // set the version code, and filtering
                String compositeVersionCode = getVersionCodeString(versionCode, apk);
                addProp(subAnt, "version.code", compositeVersionCode);
                System.out.println("Composite versionCode: " + compositeVersionCode);
                if (apk.abi != null) {
                    addProp(subAnt, "filter.abi", apk.abi);
                    System.out.println("ABI Filter: " + apk.abi);
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
                        name + "-" + apk.buildInfo + ".ap_");

                if (canSign) {
                    // set the properties for the password.
                    addProp(subAnt, "key.store", keyStore);
                    addProp(subAnt, "key.alias", keyAlias);
                    addProp(subAnt, "key.store.password", keyStorePassword);
                    addProp(subAnt, "key.alias.password", keyAliasPassword);

                    // temporary file only get a filename change (still stored in the project
                    // bin folder).
                    addProp(subAnt, "out.unsigned.file.name",
                            name + "-" + apk.buildInfo + "-unsigned.apk");
                    addProp(subAnt, "out.unaligned.file",
                            name + "-" + apk.buildInfo + "-unaligned.apk");

                    // final file is stored locally.
                    apk.outputName = name + "-" + compositeVersionCode + "-release.apk";
                    addProp(subAnt, "out.release.file", new File(exportProjectOutput,
                            apk.outputName).getAbsolutePath());

                } else {
                    // put some empty prop. This is to override possible ones defined in the
                    // project. The reason is that if there's more than one project, we don't
                    // want some to signed and some not to be (and we don't want each project
                    // to prompt for password.)
                    addProp(subAnt, "key.store", "");
                    addProp(subAnt, "key.alias", "");
                    // final file is the unsigned version. It gets stored locally.
                    apk.outputName = name + "-" + compositeVersionCode + "-unsigned.apk";
                    addProp(subAnt, "out.unsigned.file", new File(exportProjectOutput,
                            apk.outputName).getAbsolutePath());
                }
            }

            subAnt.execute();
        }

        if (mTarget == Target.RELEASE) {
            makeBuildLog(appPackage, versionCode, apks);
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
     * gets the projects to export from the property, checks they exist, validates them,
     * loads their export info and return it.
     * If a project does not exist or is not valid, this will throw a {@link BuildException}.
     * @param antProject the Ant project.
     * @param appPackage the application package. Projects' manifest must match this.
     */
    private ApkData[] getProjects(Project antProject, String appPackage) {
        String projects = antProject.getProperty("projects");
        String[] paths = projects.split("\\:");

        ArrayList<ApkData> datalist = new ArrayList<ApkData>();

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

                ArrayList<ApkData> datalist2 = checkManifest(androidManifest, appPackage);

                // if the method returns without throwing, this is a good project to
                // export.
                for (ApkData data : datalist2) {
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
        for (ApkData data : datalist) {
            data.buildInfo = buildInfo++;
        }

        return datalist.toArray(new ApkData[datalist.size()]);
    }

    /**
     * Checks a manifest of the project for inclusion in the list of exported APK.
     * If the manifest is correct, a list of apk to export is created and returned.
     * @param androidManifest the manifest to check
     * @param appPackage the package name of the application being exported, as read from
     * export.properties.
     * @return
     */
    private ArrayList<ApkData> checkManifest(FileWrapper androidManifest, String appPackage) {
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

            ArrayList<ApkData> dataList = new ArrayList<ApkData>();
            ApkData data = new ApkData();
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
                    ApkData current = data;
                    for (String abi : abis) {
                        if (current == null) {
                            current = new ApkData(data);
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

    /**
     * Finds ABIs in a project folder. This is based on the presence of libs/<abi>/ folder.
     * @param projectPath
     * @return
     */
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
        int trueVersionCode = versionCode * OFFSET_VERSION_CODE;
        trueVersionCode += apkData.buildInfo * OFFSET_BUILD_INFO;
        trueVersionCode += apkData.minor;

        return Integer.toString(trueVersionCode);
    }

    /**
     * Returns the {@link File} for the build log.
     * @param appPackage
     * @param versionCode
     * @return
     */
    private File getBuildLog(String appPackage, int versionCode) {
        return new File(appPackage + "." + versionCode + ".log");
    }

    /**
     * Loads and returns a list of {@link ApkData} from a build log.
     * @param log
     * @return
     */
    private ApkData[] getProjects(File log) {
        ArrayList<ApkData> datalist = new ArrayList<ApkData>();

        FileReader reader = null;
        BufferedReader bufferedReader = null;
        try {
            reader = new FileReader(log);
            bufferedReader = new BufferedReader(reader);
            String line;
            int lineIndex = 0;
            int apkIndex = 0;
            while ((line = bufferedReader.readLine()) != null) {
                line = line.trim();
                if (line.length() == 0 || line.startsWith("#")) {
                    continue;
                }

                switch (lineIndex) {
                    case 0:
                        // read package value
                        lineIndex++;
                        break;
                    case 1:
                        // read versionCode value
                        lineIndex++;
                        break;
                    default:
                        // read apk description
                        ApkData data = new ApkData();
                        data.buildInfo = apkIndex++;
                        datalist.add(data);
                        data.read(line);
                        if (data.minor >= MAX_MINOR) {
                            throw new BuildException(
                                    "Valid minor version code values are 0-" + (MAX_MINOR-1));
                        }
                        break;
                }
            }
        } catch (IOException e) {
            throw new BuildException("Failed to read existing build log", e);
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException e) {
                throw new BuildException("Failed to read existing build log", e);
            }
        }

        return datalist.toArray(new ApkData[datalist.size()]);
    }

    /**
     * Writes the build log for a given list of {@link ApkData}.
     * @param appPackage
     * @param versionCode
     * @param apks
     */
    private void makeBuildLog(String appPackage, int versionCode, ApkData[] apks) {
        File log = getBuildLog(appPackage, versionCode);
        FileWriter writer = null;
        try {
            writer = new FileWriter(log);

            writer.append("# Multi-APK BUILD log.\n");
            writer.append("# Only edit manually to change minor versions.\n");

            writeValue(writer, "package", appPackage);
            writeValue(writer, "versionCode", versionCode);

            writer.append("# what follows is one line per generated apk with its description.\n");
            writer.append("# the format is CSV in the following order:\n");
            writer.append("# apkname,project,minor, minsdkversion, abi filter,\n");

            for (ApkData apk : apks) {
                apk.write(writer);
                writer.append('\n');
            }

            writer.flush();
        } catch (IOException e) {
            throw new BuildException("Failed to write build log", e);
        } finally {
            try {
                if (writer != null) {
                    writer.close();
                }
            } catch (IOException e) {
                throw new BuildException("Failed to write build log", e);
            }
        }
    }

    private static void writeValue(FileWriter writer, String value) throws IOException {
        writer.append(value).append(',');
    }

    private static void writeValue(FileWriter writer, int value) throws IOException {
        writeValue(writer, Integer.toString(value));
    }

    private void writeValue(FileWriter writer, String name, String value) throws IOException {
        writer.append(name).append('=').append(value).append('\n');
    }

    private void writeValue(FileWriter writer, String name, int value) throws IOException {
        writeValue(writer, name, Integer.toString(value));
    }

}
