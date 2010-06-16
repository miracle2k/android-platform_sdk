/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.apkbuilder;

import com.android.apkbuilder.internal.ApkBuilderHelper;
import com.android.apkbuilder.internal.ApkBuilderHelper.ApkCreationException;
import com.android.apkbuilder.internal.ApkBuilderHelper.ApkFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;


/**
 * Command line APK builder with signing support.
 */
public final class ApkBuilderMain {

    public final static class WrongOptionException extends Exception {
        private static final long serialVersionUID = 1L;

        public WrongOptionException(String message) {
            super(message);
        }
    }

    /**
     * Main method. This is meant to be called from the command line through an exec.
     * <p/>WARNING: this will call {@link System#exit(int)} if anything goes wrong.
     * @param args command line arguments.
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            printUsageAndQuit();
        }

        try {
            ApkBuilderHelper helper = new ApkBuilderHelper();


            // read the first args that should be a file path
            File outFile = helper.getOutFile(args[0]);

            ArrayList<FileInputStream> zipArchives = new ArrayList<FileInputStream>();
            ArrayList<File> archiveFiles = new ArrayList<File>();
            ArrayList<ApkFile> javaResources = new ArrayList<ApkFile>();
            ArrayList<FileInputStream> resourcesJars = new ArrayList<FileInputStream>();
            ArrayList<ApkFile> nativeLibraries = new ArrayList<ApkFile>();

            int index = 1;
            do {
                String argument = args[index++];

                if ("-v".equals(argument)) {
                    helper.setVerbose(true);
                } else if ("-d".equals(argument)) {
                    helper.setDebugMode(true);
                } else if ("-u".equals(argument)) {
                    helper.setSignedPackage(false);
                } else if ("-z".equals(argument)) {
                    // quick check on the next argument.
                    if (index == args.length)  {
                        printAndExit("Missing value for -z");
                    }

                    try {
                        FileInputStream input = new FileInputStream(args[index++]);
                        zipArchives.add(input);
                    } catch (FileNotFoundException e) {
                        throw new ApkCreationException("-z file is not found");
                    }
                } else if ("-f". equals(argument)) {
                    // quick check on the next argument.
                    if (index == args.length) {
                        printAndExit("Missing value for -f");
                    }

                    archiveFiles.add(ApkBuilderHelper.getInputFile(args[index++]));
                } else if ("-rf". equals(argument)) {
                    // quick check on the next argument.
                    if (index == args.length) {
                        printAndExit("Missing value for -rf");
                    }

                    ApkBuilderHelper.processSourceFolderForResource(
                            new File(args[index++]), javaResources);
                } else if ("-rj". equals(argument)) {
                    // quick check on the next argument.
                    if (index == args.length) {
                        printAndExit("Missing value for -rj");
                    }

                    ApkBuilderHelper.processJar(new File(args[index++]), resourcesJars);
                } else if ("-nf".equals(argument)) {
                    // quick check on the next argument.
                    if (index == args.length) {
                        printAndExit("Missing value for -nf");
                    }

                    ApkBuilderHelper.processNativeFolder(new File(args[index++]),
                            helper.getDebugMode(), nativeLibraries,
                            helper.isVerbose(), null /*abiFilter*/);
                } else if ("-storetype".equals(argument)) {
                    // quick check on the next argument.
                    if (index == args.length) {
                        printAndExit("Missing value for -storetype");
                    }

                    helper.setStoreType(args[index++]);
                } else {
                    printAndExit("Unknown argument: " + argument);
                }
            } while (index < args.length);

            helper.createPackage(outFile, zipArchives, archiveFiles, javaResources, resourcesJars,
                    nativeLibraries);

        } catch (FileNotFoundException e) {
            printAndExit(e.getMessage());
        } catch (ApkCreationException e) {
            printAndExit(e.getMessage());
        }
    }

    private static void printUsageAndQuit() {
        // 80 cols marker:  01234567890123456789012345678901234567890123456789012345678901234567890123456789
        System.err.println("A command line tool to package an Android application from various sources.");
        System.err.println("Usage: apkbuilder <out archive> [-v][-u][-storetype STORE_TYPE] [-z inputzip]");
        System.err.println("            [-f inputfile] [-rf input-folder] [-rj -input-path]");
        System.err.println("");
        System.err.println("    -v      Verbose.");
        System.err.println("    -d      Debug Mode: Includes debug files in the APK file.");
        System.err.println("    -u      Creates an unsigned package.");
        System.err.println("    -storetype Forces the KeyStore type. If ommited the default is used.");
        System.err.println("");
        System.err.println("    -z      Followed by the path to a zip archive.");
        System.err.println("            Adds the content of the application package.");
        System.err.println("");
        System.err.println("    -f      Followed by the path to a file.");
        System.err.println("            Adds the file to the application package.");
        System.err.println("");
        System.err.println("    -rf     Followed by the path to a source folder.");
        System.err.println("            Adds the java resources found in that folder to the application");
        System.err.println("            package, while keeping their path relative to the source folder.");
        System.err.println("");
        System.err.println("    -rj     Followed by the path to a jar file or a folder containing");
        System.err.println("            jar files.");
        System.err.println("            Adds the java resources found in the jar file(s) to the application");
        System.err.println("            package.");
        System.err.println("");
        System.err.println("    -nf     Followed by the root folder containing native libraries to");
        System.err.println("            include in the application package.");

        System.exit(1);
    }

    private static void printAndExit(String... messages) {
        for (String message : messages) {
            System.err.println(message);
        }
        System.exit(1);
    }
}
