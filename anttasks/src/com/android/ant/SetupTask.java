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

import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.ISdkLog;
import com.android.sdklib.SdkConstants;
import com.android.sdklib.SdkManager;
import com.android.sdklib.IAndroidTarget.IOptionalLibrary;
import com.android.sdklib.internal.project.ProjectProperties;
import com.android.sdklib.internal.project.ProjectProperties.PropertyType;
import com.android.sdklib.io.FileWrapper;
import com.android.sdklib.io.FolderWrapper;
import com.android.sdklib.xml.AndroidManifest;
import com.android.sdklib.xml.AndroidXPathFactory;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.ImportTask;
import org.apache.tools.ant.types.Path;
import org.apache.tools.ant.types.Path.PathElement;
import org.xml.sax.InputSource;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;

/**
 * Setup/Import Ant task. This task accomplishes:
 * <ul>
 * <li>Gets the project target hash string from {@link ProjectProperties#PROPERTY_TARGET},
 * and resolves it to get the project's {@link IAndroidTarget}.</li>
 * <li>Sets up properties so that aapt can find the android.jar in the resolved target.</li>
 * <li>Sets up the boot classpath ref so that the <code>javac</code> task knows where to find
 * the libraries. This includes the default android.jar from the resolved target but also optional
 * libraries provided by the target (if any, when the target is an add-on).</li>
 * <li>Imports the build rules located in the resolved target so that the build actually does
 * something. This can be disabled with the attribute <var>import</var> set to <code>false</code>
 * </li></ul>
 *
 * This is used in build.xml/template.
 *
 */
public final class SetupTask extends ImportTask {
    // main rules file
    private final static String RULES_MAIN = "main_rules.xml";
    // test rules file - depends on android_rules.xml
    private final static String RULES_TEST = "test_rules.xml";
    // library rules file.
    private final static String RULES_LIBRARY = "lib_rules.xml";

    // ant property with the path to the android.jar
    private final static String PROPERTY_ANDROID_JAR = "android.jar";

    // ant property with the path to the framework.jar
    private final static String PROPERTY_ANDROID_AIDL = "android.aidl";

    // ant property with the path to the aapt tool
    private final static String PROPERTY_AAPT = "aapt";
    // ant property with the path to the aidl tool
    private final static String PROPERTY_AIDL = "aidl";
    // ant property with the path to the dx tool
    private final static String PROPERTY_DX = "dx";
    // ref id to the <path> object containing all the boot classpaths.
    private final static String REF_CLASSPATH = "android.target.classpath";

    private boolean mDoImport = true;

    @Override
    public void execute() throws BuildException {
        Project antProject = getProject();

        // get the SDK location
        File sdkDir = TaskHelper.getSdkLocation(antProject);
        String sdkOsPath = sdkDir.getPath();

        // Make sure the OS sdk path ends with a directory separator
        if (sdkOsPath.length() > 0 && !sdkOsPath.endsWith(File.separator)) {
            sdkOsPath += File.separator;
        }

        // display SDK Tools revision
        int toolsRevison = TaskHelper.getToolsRevision(sdkDir);
        if (toolsRevison != -1) {
            System.out.println("Android SDK Tools Revision " + toolsRevison);
        }

        // get the target property value
        String targetHashString = antProject.getProperty(ProjectProperties.PROPERTY_TARGET);

        boolean isTestProject = false;

        if (antProject.getProperty("tested.project.dir") != null) {
            isTestProject = true;
        }

        if (targetHashString == null) {
            throw new BuildException("Android Target is not set.");
        }

        // load up the sdk targets.
        final ArrayList<String> messages = new ArrayList<String>();
        SdkManager manager = SdkManager.createManager(sdkOsPath, new ISdkLog() {
            public void error(Throwable t, String errorFormat, Object... args) {
                if (errorFormat != null) {
                    messages.add(String.format("Error: " + errorFormat, args));
                }
                if (t != null) {
                    messages.add("Error: " + t.getMessage());
                }
            }

            public void printf(String msgFormat, Object... args) {
                messages.add(String.format(msgFormat, args));
            }

            public void warning(String warningFormat, Object... args) {
                messages.add(String.format("Warning: " + warningFormat, args));
            }
        });

        if (manager == null) {
            // since we failed to parse the SDK, lets display the parsing output.
            for (String msg : messages) {
                System.out.println(msg);
            }
            throw new BuildException("Failed to parse SDK content.");
        }

        // resolve it
        IAndroidTarget androidTarget = manager.getTargetFromHashString(targetHashString);

        if (androidTarget == null) {
            throw new BuildException(String.format(
                    "Unable to resolve target '%s'", targetHashString));
        }

        // display the project info
        System.out.println("Project Target: " + androidTarget.getName());
        if (androidTarget.isPlatform() == false) {
            System.out.println("Vendor: " + androidTarget.getVendor());
            System.out.println("Platform Version: " + androidTarget.getVersionName());
        }
        System.out.println("API level: " + androidTarget.getVersion().getApiString());

        // check if the project is a library
        boolean isLibrary = false;

        String libraryProp = antProject.getProperty(ProjectProperties.PROPERTY_LIBRARY);
        if (libraryProp != null) {
            isLibrary = Boolean.valueOf(libraryProp).booleanValue();
        }

        if (isLibrary) {
            System.out.println("Project Type: Android Library");
        }

        // look for referenced libraries.
        processReferencedLibraries(antProject, androidTarget);

        // always check the manifest minSdkVersion.
        checkManifest(antProject, androidTarget.getVersion());

        // sets up the properties to find android.jar/framework.aidl/target tools
        String androidJar = androidTarget.getPath(IAndroidTarget.ANDROID_JAR);
        antProject.setProperty(PROPERTY_ANDROID_JAR, androidJar);

        String androidAidl = androidTarget.getPath(IAndroidTarget.ANDROID_AIDL);
        antProject.setProperty(PROPERTY_ANDROID_AIDL, androidAidl);

        antProject.setProperty(PROPERTY_AAPT, androidTarget.getPath(IAndroidTarget.AAPT));
        antProject.setProperty(PROPERTY_AIDL, androidTarget.getPath(IAndroidTarget.AIDL));
        antProject.setProperty(PROPERTY_DX, androidTarget.getPath(IAndroidTarget.DX));

        // sets up the boot classpath

        // create the Path object
        Path bootclasspath = new Path(antProject);

        // create a PathElement for the framework jar
        PathElement element = bootclasspath.createPathElement();
        element.setPath(androidJar);

        // create PathElement for each optional library.
        IOptionalLibrary[] libraries = androidTarget.getOptionalLibraries();
        if (libraries != null) {
            HashSet<String> visitedJars = new HashSet<String>();
            for (IOptionalLibrary library : libraries) {
                String jarPath = library.getJarPath();
                if (visitedJars.contains(jarPath) == false) {
                    visitedJars.add(jarPath);

                    element = bootclasspath.createPathElement();
                    element.setPath(library.getJarPath());
                }
            }
        }

        // finally sets the path in the project with a reference
        antProject.addReference(REF_CLASSPATH, bootclasspath);

        // Now the import section. This is only executed if the task actually has to import a file.
        if (mDoImport) {
            // check the ant folder exists in the tools folder of the SDK.
            File rulesFolder = new File(
                    new File(sdkOsPath, SdkConstants.FD_TOOLS),
                    SdkConstants.FD_ANT);

            // make sure the file exists.
            if (rulesFolder.isDirectory() == false) {
                throw new BuildException(String.format("Rules directory '%s' is missing.",
                        rulesFolder.getAbsolutePath()));
            }

            // name of the rules files to import based on the type of project
            String importedRulesFileName =
                    isLibrary ? RULES_LIBRARY : isTestProject ? RULES_TEST : RULES_MAIN;

            // now check the rules file exists.
            File rules = new File(rulesFolder, importedRulesFileName);

            if (rules.isFile() == false) {
                throw new BuildException(String.format("Build rules file '%s' is missing.",
                        rules));
            }

            // display the file being imported.
            // figure out the path relative to the SDK
            String rulesOsPath = rules.getAbsolutePath();
            if (rulesOsPath.startsWith(sdkOsPath)) {
                rulesOsPath = rulesOsPath.substring(sdkOsPath.length());
                if (rulesOsPath.startsWith(File.separator)) {
                    rulesOsPath = rulesOsPath.substring(1);
                }
            }
            System.out.println("\nImporting rules file: " + rulesOsPath);

            // set the file location to import
            setFile(rules.getAbsolutePath());

            // and import
            super.execute();
        }
    }

    /**
     * Sets the value of the "import" attribute.
     * @param value the value.
     */
    public void setImport(boolean value) {
        mDoImport = value;
    }

    /**
     * Checks the manifest <code>minSdkVersion</code> attribute.
     * @param antProject the ant project
     * @param androidVersion the version of the platform the project is compiling against.
     */
    private void checkManifest(Project antProject, AndroidVersion androidVersion) {
        try {
            File manifest = new File(antProject.getBaseDir(), SdkConstants.FN_ANDROID_MANIFEST_XML);

            XPath xPath = AndroidXPathFactory.newXPath();

            // check the package name.
            String value = xPath.evaluate(
                    "/"  + AndroidManifest.NODE_MANIFEST +
                    "/@" + AndroidManifest.ATTRIBUTE_PACKAGE,
                    new InputSource(new FileInputStream(manifest)));
            if (value != null) { // aapt will complain if it's missing.
                // only need to check that the package has 2 segments
                if (value.indexOf('.') == -1) {
                    throw new BuildException(String.format(
                            "Application package '%1$s' must have a minimum of 2 segments.",
                            value));
                }
            }

            // check the minSdkVersion value
            value = xPath.evaluate(
                    "/"  + AndroidManifest.NODE_MANIFEST +
                    "/"  + AndroidManifest.NODE_USES_SDK +
                    "/@" + AndroidXPathFactory.DEFAULT_NS_PREFIX + ":" +
                            AndroidManifest.ATTRIBUTE_MIN_SDK_VERSION,
                    new InputSource(new FileInputStream(manifest)));

            if (androidVersion.isPreview()) {
                // in preview mode, the content of the minSdkVersion must match exactly the
                // platform codename.
                String codeName = androidVersion.getCodename();
                if (codeName.equals(value) == false) {
                    throw new BuildException(String.format(
                            "For '%1$s' SDK Preview, attribute minSdkVersion in AndroidManifest.xml must be '%1$s'",
                            codeName));
                }
            } else if (value.length() > 0) {
                // for normal platform, we'll only display warnings if the value is lower or higher
                // than the target api level.
                // First convert to an int.
                int minSdkValue = -1;
                try {
                    minSdkValue = Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    // looks like it's not a number: error!
                    throw new BuildException(String.format(
                            "Attribute %1$s in AndroidManifest.xml must be an Integer!",
                            AndroidManifest.ATTRIBUTE_MIN_SDK_VERSION));
                }

                int projectApiLevel = androidVersion.getApiLevel();
                if (minSdkValue < projectApiLevel) {
                    System.out.println(String.format(
                            "WARNING: Attribute %1$s in AndroidManifest.xml (%2$d) is lower than the project target API level (%3$d)",
                            AndroidManifest.ATTRIBUTE_MIN_SDK_VERSION,
                            minSdkValue, projectApiLevel));
                } else if (minSdkValue > androidVersion.getApiLevel()) {
                    System.out.println(String.format(
                            "WARNING: Attribute %1$s in AndroidManifest.xml (%2$d) is higher than the project target API level (%3$d)",
                            AndroidManifest.ATTRIBUTE_MIN_SDK_VERSION,
                            minSdkValue, projectApiLevel));
                }
            } else {
                // no minSdkVersion? display a warning
                System.out.println(
                        "WARNING: No minSdkVersion value set. Application will install on all Android versions.");
            }

        } catch (XPathExpressionException e) {
            throw new BuildException(e);
        } catch (FileNotFoundException e) {
            throw new BuildException(e);
        }
    }

    private void processReferencedLibraries(Project antProject, IAndroidTarget androidTarget) {
        // prepare several paths for future tasks
        Path sourcePath = new Path(antProject);
        Path resPath = new Path(antProject);
        Path libsPath = new Path(antProject);
        Path jarsPath = new Path(antProject);
        StringBuilder sb = new StringBuilder();

        FilenameFilter filter = new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.toLowerCase().endsWith(".jar");
            }
        };

        System.out.println("\n------------------\nResolving library dependencies:");

        ArrayList<File> libraries = getProjectLibraries(antProject);

        if (libraries.size() > 0) {
            System.out.println("------------------\nOrdered libraries:");

            for (File library : libraries) {
                System.out.println(library.getAbsolutePath());

                // get the source path. default is src but can be overriden by the property
                // "source.dir" in build.properties.
                PathElement element = sourcePath.createPathElement();
                ProjectProperties prop = ProjectProperties.load(new FolderWrapper(library),
                        PropertyType.BUILD);

                String sourceDir = SdkConstants.FD_SOURCES;
                if (prop != null) {
                    String value = prop.getProperty(ProjectProperties.PROPERTY_BUILD_SOURCE_DIR);
                    if (value != null) {
                        sourceDir = value;
                    }
                }

                String path = library.getAbsolutePath();

                element.setPath(path + "/" + sourceDir);

                // get the res path. Always $PROJECT/res
                element = resPath.createPathElement();
                element.setPath(path + "/" + SdkConstants.FD_RESOURCES);

                // get the libs path. Always $PROJECT/libs
                element = libsPath.createPathElement();
                element.setPath(path + "/" + SdkConstants.FD_NATIVE_LIBS);

                // get the jars from it too
                File libsFolder = new File(library, SdkConstants.FD_NATIVE_LIBS);
                File[] jarFiles = libsFolder.listFiles(filter);
                if (jarFiles != null) {
                    for (File jarFile : jarFiles) {
                        element = jarsPath.createPathElement();
                        element.setPath(jarFile.getAbsolutePath());
                    }
                }

                // get the package from the manifest.
                FileWrapper manifest = new FileWrapper(library,
                        SdkConstants.FN_ANDROID_MANIFEST_XML);

                try {
                    String value = AndroidManifest.getPackage(manifest);
                    if (value != null) { // aapt will complain if it's missing.
                        sb.append(';');
                        sb.append(value);
                    }
                } catch (Exception e) {
                    throw new BuildException(e);
                }
            }
        } else {
            System.out.println("No library dependencies.\n");
        }

        System.out.println("------------------\n");

        // even with no libraries, always setup these so that various tasks in Ant don't complain
        // (the task themselves can handle a ref to an empty Path)
        antProject.addReference("android.libraries.src", sourcePath);
        antProject.addReference("android.libraries.jars", jarsPath);
        antProject.addReference("android.libraries.libs", libsPath);

        // the rest is done only if there's a library.
        if (sourcePath.list().length > 0) {
            antProject.addReference("android.libraries.res", resPath);
            antProject.setProperty("android.libraries.package", sb.toString());
        }
    }

    /**
     * Returns all the library dependencies of a given Ant project.
     * @param antProject the Ant project
     * @return a list of properties, sorted from highest priority to lowest.
     */
    private ArrayList<File> getProjectLibraries(final Project antProject) {
        ArrayList<File> libraries = new ArrayList<File>();
        File baseDir = antProject.getBaseDir();

        // get the top level list of library dependencies.
        ArrayList<File> topLevelLibraries = getDirectDependencies(baseDir, new IPropertySource() {
            public String getProperty(String name) {
                return antProject.getProperty(name);
            }
        });

        // process the libraries in case they depend on other libraries.
        resolveFullLibraryDependencies(topLevelLibraries, libraries);

        return libraries;
    }

    /**
     * Resolves a given list of libraries, finds out if they depend on other libraries, and
     * returns a full list of all the direct and indirect dependencies in the proper order (first
     * is higher priority when calling aapt).
     * @param inLibraries the libraries to resolve
     * @param outLibraries where to store all the libraries.
     */
    private void resolveFullLibraryDependencies(ArrayList<File> inLibraries,
            ArrayList<File> outLibraries) {
        // loop in the inverse order to resolve dependencies on the libraries, so that if a library
        // is required by two higher level libraries it can be inserted in the correct place
        for (int i = inLibraries.size() - 1  ; i >= 0 ; i--) {
            File library = inLibraries.get(i);

            // get the default.property file for it
            final ProjectProperties defaultProp = ProjectProperties.load(
                    new FolderWrapper(library), PropertyType.DEFAULT);

            // get its libraries
            ArrayList<File> dependencies = getDirectDependencies(library, new IPropertySource() {
                public String getProperty(String name) {
                    return defaultProp.getProperty(name);
                }
            });

            // resolve the dependencies for those libraries
            resolveFullLibraryDependencies(dependencies, outLibraries);

            // and add the current one (if needed) in front (higher priority)
            if (outLibraries.contains(library) == false) {
                outLibraries.add(0, library);
            }
        }
    }

    public interface IPropertySource {
        String getProperty(String name);
    }

    /**
     * Returns the top level library dependencies of a given <var>source</var> representing a
     * project properties.
     * @param baseFolder the base folder of the project (to resolve relative paths)
     * @param source a source of project properties.
     */
    private ArrayList<File> getDirectDependencies(File baseFolder, IPropertySource source) {
        ArrayList<File> libraries = new ArrayList<File>();

        // first build the list. they are ordered highest priority first.
        int index = 1;
        while (true) {
            String propName = ProjectProperties.PROPERTY_LIB_REF + Integer.toString(index++);
            String rootPath = source.getProperty(propName);

            if (rootPath == null) {
                break;
            }

            try {
                File library = new File(baseFolder, rootPath).getCanonicalFile();

                // check for validity
                File defaultProp = new File(library, PropertyType.DEFAULT.getFilename());
                if (defaultProp.isFile() == false) {
                    // error!
                    throw new BuildException(String.format(
                            "%1$s resolve to a path with no %2$s file for project %3$s", rootPath,
                            PropertyType.DEFAULT.getFilename(), baseFolder.getAbsolutePath()));
                }

                if (libraries.contains(library) == false) {
                    System.out.println(String.format("%1$s: %2$s => %3$s",
                            baseFolder.getAbsolutePath(), rootPath, library.getAbsolutePath()));

                    libraries.add(library);
                }
            } catch (IOException e) {
                throw new BuildException("Failed to resolve library path: " + rootPath, e);
            }
        }

        return libraries;
    }
}
