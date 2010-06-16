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

package com.android.sdklib.internal.build;

import com.android.prefs.AndroidLocation.AndroidLocationException;
import com.android.sdklib.internal.build.DebugKeyProvider.KeytoolException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.regex.Pattern;

/**
 * Command line APK builder with signing support.
 */
public final class ApkBuilderHelper {

    private final static Pattern PATTERN_JAR_EXT = Pattern.compile("^.+\\.jar$",
            Pattern.CASE_INSENSITIVE);
    private final static Pattern PATTERN_NATIVELIB_EXT = Pattern.compile("^.+\\.so$",
            Pattern.CASE_INSENSITIVE);

    private final static String NATIVE_LIB_ROOT = "lib/";
    private final static String GDBSERVER_NAME = "gdbserver";

    public final static class ApkCreationException extends Exception {
        private static final long serialVersionUID = 1L;

        public ApkCreationException(String message) {
            super(message);
        }

        public ApkCreationException(Throwable throwable) {
            super(throwable);
        }
    }


    /**
     * A File to be added to the APK archive.
     * <p/>This includes the {@link File} representing the file and its path in the archive.
     */
    public final static class ApkFile {
        String archivePath;
        File file;

        ApkFile(File file, String path) {
            this.file = file;
            this.archivePath = path;
        }
    }

    private JavaResourceFilter mResourceFilter = new JavaResourceFilter();
    private boolean mVerbose = false;
    private boolean mSignedPackage = true;
    private boolean mDebugMode = false;
    /** the optional type of the debug keystore. If <code>null</code>, the default */
    private String mStoreType = null;

    public void setVerbose(boolean verbose) {
        mVerbose = verbose;
    }

    public boolean isVerbose() {
        return mVerbose;
    }

    public void setSignedPackage(boolean signedPackage) {
        mSignedPackage = signedPackage;
    }

    public void setDebugMode(boolean debugMode) {
        mDebugMode = debugMode;
    }

    public boolean getDebugMode() {
        return mDebugMode;
    }

    /**
     * Set the type of the keystore. <code>null</code> means the default.
     * @param storeType
     */
    public void setStoreType(String storeType) {
        mStoreType = storeType;
    }

    /**
     * Returns the store type. <code>null</code> means default.
     * @return
     */
    public String getStoreType() {
        return mStoreType;
    }

    public File getOutFile(String filepath) throws ApkCreationException {
        File f = new File(filepath);

        if (f.isDirectory()) {
            throw new ApkCreationException(filepath + " is a directory!");
        }

        if (f.exists()) { // will be a file in this case.
            if (f.canWrite() == false) {
                throw new ApkCreationException("Cannot write " + filepath);
            }
        } else {
            try {
                if (f.createNewFile() == false) {
                    throw new ApkCreationException("Failed to create " + filepath);
                }
            } catch (IOException e) {
                throw new ApkCreationException(
                        "Failed to create '" + filepath + "' : " + e.getMessage());
            }
        }

        return f;
    }

    /**
     * Returns a {@link File} representing a given file path. The path must represent
     * an actual existing file (not a directory). The path may be relative.
     * @param filepath the path to a file.
     * @return the File representing the path.
     * @throws ApkCreationException if the path represents a directory or if the file does not
     * exist, or cannot be read.
     */
    public static File getInputFile(String filepath) throws ApkCreationException {
        File f = new File(filepath);

        if (f.isDirectory()) {
            throw new ApkCreationException(filepath + " is a directory!");
        }

        if (f.exists()) {
            if (f.canRead() == false) {
                throw new ApkCreationException("Cannot read " + filepath);
            }
        } else {
            throw new ApkCreationException(filepath + " does not exists!");
        }

        return f;
    }

    /**
     * Processes a source folder and adds its java resources to a given list of {@link ApkFile}.
     * @param folder the folder representing the source folder.
     * @param javaResources the list of {@link ApkFile} to fill.
     * @throws ApkCreationException
     */
    public static void processSourceFolderForResource(File folder,
            ArrayList<ApkFile> javaResources) throws ApkCreationException {
        if (folder.isDirectory()) {
            // file is a directory, process its content.
            File[] files = folder.listFiles();
            for (File file : files) {
                processFileForResource(file, null, javaResources);
            }
        } else {
            // not a directory? output error and quit.
            if (folder.exists()) {
                throw new ApkCreationException(folder.getAbsolutePath() + " is not a folder!");
            } else {
                throw new ApkCreationException(folder.getAbsolutePath() + " does not exist!");
            }
        }
    }

    /**
     * Process a jar file or a jar folder
     * @param file the {@link File} to process
     * @param resourcesJars the collection of FileInputStream to fill up with jar files.
     * @throws FileNotFoundException
     * @throws ApkCreationException
     */
    public static void processJar(File file, Collection<FileInputStream> resourcesJars)
            throws FileNotFoundException, ApkCreationException {
        if (file.isDirectory()) {
            String[] filenames = file.list(new FilenameFilter() {
                public boolean accept(File dir, String name) {
                    return PATTERN_JAR_EXT.matcher(name).matches();
                }
            });

            for (String filename : filenames) {
                File f = new File(file, filename);
                processJarFile(f, resourcesJars);
            }
        } else if (file.isFile()) {
            processJarFile(file, resourcesJars);
        } else {
            throw new ApkCreationException(file.getAbsolutePath()+ " does not exist!");
        }
    }

    public static void processJarFile(File file, Collection<FileInputStream> resourcesJars)
            throws FileNotFoundException {
        FileInputStream input = new FileInputStream(file);
        resourcesJars.add(input);
    }

    /**
     * Processes a {@link File} that could be a {@link ApkFile}, or a folder containing
     * java resources.
     * @param file the {@link File} to process.
     * @param path the relative path of this file to the source folder. Can be <code>null</code> to
     * identify a root file.
     * @param javaResources the Collection of {@link ApkFile} object to fill.
     */
    private static void processFileForResource(File file, String path,
            Collection<ApkFile> javaResources) {
        if (file.isDirectory()) {
            // a directory? we check it
            if (JavaResourceFilter.checkFolderForPackaging(file.getName())) {
                // if it's valid, we append its name to the current path.
                if (path == null) {
                    path = file.getName();
                } else {
                    path = path + "/" + file.getName();
                }

                // and process its content.
                File[] files = file.listFiles();
                for (File contentFile : files) {
                    processFileForResource(contentFile, path, javaResources);
                }
            }
        } else {
            // a file? we check it
            if (JavaResourceFilter.checkFileForPackaging(file.getName())) {
                // we append its name to the current path
                if (path == null) {
                    path = file.getName();
                } else {
                    path = path + "/" + file.getName();
                }

                // and add it to the list.
                javaResources.add(new ApkFile(file, path));
            }
        }
    }

    /**
     * Process a {@link File} for native library inclusion.
     * <p/>The root folder must include folders that include .so files.
     * @param root the native root folder.
     * @param nativeLibraries the collection to add native libraries to.
     * @param verbose verbose mode.
     * @param abiFilter optional ABI filter. If non-null only the given ABI is included.
     * @throws ApkCreationException
     */
    public static void processNativeFolder(File root, boolean debugMode,
            Collection<ApkFile> nativeLibraries, boolean verbose, String abiFilter)
            throws ApkCreationException {
        if (root.isDirectory() == false) {
            throw new ApkCreationException(root.getAbsolutePath() + " is not a folder!");
        }

        File[] abiList = root.listFiles();

        if (verbose) {
            System.out.println("Processing native folder: " + root.getAbsolutePath());
            if (abiFilter != null) {
                System.out.println("ABI Filter: " + abiFilter);
            }
        }

        if (abiList != null) {
            for (File abi : abiList) {
                if (abi.isDirectory()) { // ignore files

                    // check the abi filter and reject all other ABIs
                    if (abiFilter != null && abiFilter.equals(abi.getName()) == false) {
                        if (verbose) {
                            System.out.println("Rejecting ABI " + abi.getName());
                        }
                        continue;
                    }

                    File[] libs = abi.listFiles();
                    if (libs != null) {
                        for (File lib : libs) {
                            // only consider files that are .so or, if in debug mode, that
                            // are gdbserver executables
                            if (lib.isFile() &&
                                    (PATTERN_NATIVELIB_EXT.matcher(lib.getName()).matches() ||
                                            (debugMode && GDBSERVER_NAME.equals(lib.getName())))) {
                                String path =
                                    NATIVE_LIB_ROOT + abi.getName() + "/" + lib.getName();

                                nativeLibraries.add(new ApkFile(lib, path));
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Creates the application package
     * @param outFile the package file to create
     * @param zipArchives the list of zip archive
     * @param files the list of files to include in the archive
     * @param javaResources the list of java resources from the source folders.
     * @param resourcesJars the list of jar files from which to take java resources
     * @throws ApkCreationException
     */
    public void createPackage(File outFile, Iterable<? extends FileInputStream> zipArchives,
            Iterable<? extends File> files, Iterable<? extends ApkFile> javaResources,
            Iterable<? extends FileInputStream> resourcesJars,
            Iterable<? extends ApkFile> nativeLibraries) throws ApkCreationException {

        // get the debug key
        try {
            SignedJarBuilder builder;

            if (mSignedPackage) {
                System.err.println(String.format("Using keystore: %s",
                        DebugKeyProvider.getDefaultKeyStoreOsPath()));


                DebugKeyProvider keyProvider = new DebugKeyProvider(
                        null /* osKeyPath: use default */,
                        mStoreType, null /* IKeyGenOutput */);
                PrivateKey key = keyProvider.getDebugKey();
                X509Certificate certificate = (X509Certificate)keyProvider.getCertificate();

                if (key == null) {
                    throw new ApkCreationException("Unable to get debug signature key");
                }

                // compare the certificate expiration date
                if (certificate != null && certificate.getNotAfter().compareTo(new Date()) < 0) {
                    // TODO, regenerate a new one.
                    throw new ApkCreationException("Debug Certificate expired on " +
                            DateFormat.getInstance().format(certificate.getNotAfter()));
                }

                builder = new SignedJarBuilder(
                        new FileOutputStream(outFile.getAbsolutePath(), false /* append */), key,
                        certificate);
            } else {
                builder = new SignedJarBuilder(
                        new FileOutputStream(outFile.getAbsolutePath(), false /* append */),
                        null /* key */, null /* certificate */);
            }

            // add the archives
            for (FileInputStream input : zipArchives) {
                builder.writeZip(input, null /* filter */);
            }

            // add the single files
            for (File input : files) {
                // always put the file at the root of the archive in this case
                builder.writeFile(input, input.getName());
                if (mVerbose) {
                    System.err.println(String.format("%1$s => %2$s", input.getAbsolutePath(),
                            input.getName()));
                }
            }

            // add the java resource from the source folders.
            for (ApkFile resource : javaResources) {
                builder.writeFile(resource.file, resource.archivePath);
                if (mVerbose) {
                    System.err.println(String.format("%1$s => %2$s",
                            resource.file.getAbsolutePath(), resource.archivePath));
                }
            }

            // add the java resource from jar files.
            for (FileInputStream input : resourcesJars) {
                builder.writeZip(input, mResourceFilter);
            }

            // add the native files
            for (ApkFile file : nativeLibraries) {
                builder.writeFile(file.file, file.archivePath);
                if (mVerbose) {
                    System.err.println(String.format("%1$s => %2$s", file.file.getAbsolutePath(),
                            file.archivePath));
                }
            }

            // close and sign the application package.
            builder.close();
        } catch (KeytoolException e) {
            if (e.getJavaHome() == null) {
                throw new ApkCreationException(e.getMessage() +
                        "\nJAVA_HOME seems undefined, setting it will help locating keytool automatically\n" +
                        "You can also manually execute the following command\n:" +
                        e.getCommandLine());
            } else {
                throw new ApkCreationException(e.getMessage() +
                        "\nJAVA_HOME is set to: " + e.getJavaHome() +
                        "\nUpdate it if necessary, or manually execute the following command:\n" +
                        e.getCommandLine());
            }
        } catch (AndroidLocationException e) {
            throw new ApkCreationException(e);
        } catch (Exception e) {
            throw new ApkCreationException(e);
        }
    }
}
