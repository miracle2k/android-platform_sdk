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

package com.android.sdklib.xml;

import com.android.sdklib.SdkConstants;
import com.android.sdklib.io.IAbstractFile;
import com.android.sdklib.io.IAbstractFolder;
import com.android.sdklib.io.StreamException;

import org.xml.sax.InputSource;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;

/**
 * Helper and Constants for the AndroidManifest.xml file.
 *
 */
public final class AndroidManifest {

    public final static String NODE_MANIFEST = "manifest"; //$NON-NLS-1$
    public final static String NODE_APPLICATION = "application"; //$NON-NLS-1$
    public final static String NODE_ACTIVITY = "activity"; //$NON-NLS-1$
    public final static String NODE_SERVICE = "service"; //$NON-NLS-1$
    public final static String NODE_RECEIVER = "receiver"; //$NON-NLS-1$
    public final static String NODE_PROVIDER = "provider"; //$NON-NLS-1$
    public final static String NODE_INTENT = "intent-filter"; //$NON-NLS-1$
    public final static String NODE_ACTION = "action"; //$NON-NLS-1$
    public final static String NODE_CATEGORY = "category"; //$NON-NLS-1$
    public final static String NODE_USES_SDK = "uses-sdk"; //$NON-NLS-1$
    public final static String NODE_INSTRUMENTATION = "instrumentation"; //$NON-NLS-1$
    public final static String NODE_USES_LIBRARY = "uses-library"; //$NON-NLS-1$

    public final static String ATTRIBUTE_PACKAGE = "package"; //$NON-NLS-1$
    public final static String ATTRIBUTE_NAME = "name"; //$NON-NLS-1$
    public final static String ATTRIBUTE_PROCESS = "process"; //$NON-NLS-$
    public final static String ATTRIBUTE_DEBUGGABLE = "debuggable"; //$NON-NLS-$
    public final static String ATTRIBUTE_MIN_SDK_VERSION = "minSdkVersion"; //$NON-NLS-$
    public final static String ATTRIBUTE_TARGET_PACKAGE = "targetPackage"; //$NON-NLS-1$
    public final static String ATTRIBUTE_EXPORTED = "exported"; //$NON-NLS-1$

    /**
     * Returns the package for a given project.
     * @param projectFolder the folder of the project.
     * @return the package info or null (or empty) if not found.
     * @throws XPathExpressionException
     * @throws StreamException If any error happens when reading the manifest.
     */
    public static String getPackage(IAbstractFolder projectFolder)
            throws XPathExpressionException, StreamException {
        IAbstractFile file = projectFolder.getFile(SdkConstants.FN_ANDROID_MANIFEST_XML);
        return getPackage(file);
    }

    /**
     * Returns the package for a given manifest.
     * @param manifestFile the manifest to parse.
     * @return the package info or null (or empty) if not found.
     * @throws XPathExpressionException
     * @throws StreamException If any error happens when reading the manifest.
     */
    public static String getPackage(IAbstractFile manifestFile)
            throws XPathExpressionException, StreamException {
        XPath xPath = AndroidXPathFactory.newXPath();

        return xPath.evaluate(
                "/"  + NODE_MANIFEST +
                "/@" + ATTRIBUTE_PACKAGE,
                new InputSource(manifestFile.getContents()));
    }

    /**
     * Combines a java package, with a class value from the manifest to make a fully qualified
     * class name
     * @param javaPackage the java package from the manifest.
     * @param className the class name from the manifest.
     * @return the fully qualified class name.
     */
    public static String combinePackageAndClassName(String javaPackage, String className) {
        if (className == null || className.length() == 0) {
            return javaPackage;
        }
        if (javaPackage == null || javaPackage.length() == 0) {
            return className;
        }

        // the class name can be a subpackage (starts with a '.'
        // char), a simple class name (no dot), or a full java package
        boolean startWithDot = (className.charAt(0) == '.');
        boolean hasDot = (className.indexOf('.') != -1);
        if (startWithDot || hasDot == false) {

            // add the concatenation of the package and class name
            if (startWithDot) {
                return javaPackage + className;
            } else {
                return javaPackage + '.' + className;
            }
        } else {
            // just add the class as it should be a fully qualified java name.
            return className;
        }
    }

    /**
     * Given a fully qualified activity name (e.g. com.foo.test.MyClass) and given a project
     * package base name (e.g. com.foo), returns the relative activity name that would be used
     * the "name" attribute of an "activity" element.
     *
     * @param fullActivityName a fully qualified activity class name, e.g. "com.foo.test.MyClass"
     * @param packageName The project base package name, e.g. "com.foo"
     * @return The relative activity name if it can be computed or the original fullActivityName.
     */
    public static String extractActivityName(String fullActivityName, String packageName) {
        if (packageName != null && fullActivityName != null) {
            if (packageName.length() > 0 && fullActivityName.startsWith(packageName)) {
                String name = fullActivityName.substring(packageName.length());
                if (name.length() > 0 && name.charAt(0) == '.') {
                    return name;
                }
            }
        }

        return fullActivityName;
    }
}
