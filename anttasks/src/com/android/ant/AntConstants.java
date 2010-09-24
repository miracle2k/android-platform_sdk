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

/**
 * Constants used by custom tasks and the rules files.
 */
public interface AntConstants {

    /** ant property with the path to the android.jar */
    public final static String PROP_ANDROID_JAR = "android.jar";

    /** ant property with the path to the framework.jar */
    public final static String PROP_ANDROID_AIDL = "android.aidl";

    /** ant property with the path to the aapt tool */
    public final static String PROP_AAPT = "aapt";
    /** ant property with the path to the aidl tool */
    public final static String PROP_AIDL = "aidl";
    /** ant property with the path to the dx tool */
    public final static String PROP_DX = "dx";
    /** ref id to the <path> object containing all the boot classpaths. */
    public final static String PROP_CLASSPATH_REF = "android.target.classpath";

    /** ant property ref to the list of source folder for the project libraries */
    public static final String PROP_PROJECT_LIBS_SRC_REF = "project.libraries.src";
    /** ant property ref to the list of jars for the project libraries */
    public static final String PROP_PROJECT_LIBS_JARS_REF = "project.libraries.jars";
    /** ant property ref to the list of libs folder for the project libraries */
    public static final String PROP_PROJECT_LIBS_LIBS_REF = "project.libraries.libs";
    /** ant property ref to the list of res folder for the project libraries */
    public static final String PROP_PROJECT_LIBS_RES_REF = "project.libraries.res";
    /** ant property for semi-colon separated packages for the project libraries */
    public static final String PROP_PROJECT_LIBS_PKG = "project.libraries.package";
    /** ant property for the test project directory */
    public static final String PROP_TESTED_PROJECT_DIR = "tested.project.dir";

    public static final String PROP_MANIFEST_PACKAGE = "manifest.package";

    public static final String PROP_OUT_ABS_DIR = "out.absolute.dir";

    public static final String PROP_KEY_STORE_PASSWORD = "key.store.password";
    public static final String PROP_KEY_ALIAS_PASSWORD = "key.alias.password";
}
