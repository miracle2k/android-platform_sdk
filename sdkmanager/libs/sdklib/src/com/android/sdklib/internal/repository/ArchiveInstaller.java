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

package com.android.sdklib.internal.repository;

import com.android.sdklib.SdkConstants;
import com.android.sdklib.SdkManager;
import com.android.sdklib.repository.RepoConstants;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Enumeration;
import java.util.Properties;


/**
 * Performs the work of installing a given {@link Archive}.
 */
public class ArchiveInstaller {

    public static final int NUM_MONITOR_INC = 100;

    /**
     * Install this {@link ArchiveInstaller}s.
     * The archive will be skipped if it is incompatible.
     *
     * @return True if the archive was installed, false otherwise.
     */
    public boolean install(Archive archive,
            String osSdkRoot,
            boolean forceHttp,
            SdkManager sdkManager,
            ITaskMonitor monitor) {

        Package pkg = archive.getParentPackage();

        File archiveFile = null;
        String name = pkg.getShortDescription();

        if (pkg instanceof ExtraPackage && !((ExtraPackage) pkg).isPathValid()) {
            monitor.setResult("Skipping %1$s: %2$s is not a valid install path.",
                    name,
                    ((ExtraPackage) pkg).getPath());
            return false;
        }

        if (archive.isLocal()) {
            // This should never happen.
            monitor.setResult("Skipping already installed archive: %1$s for %2$s",
                    name,
                    archive.getOsDescription());
            return false;
        }

        if (!archive.isCompatible()) {
            monitor.setResult("Skipping incompatible archive: %1$s for %2$s",
                    name,
                    archive.getOsDescription());
            return false;
        }

        archiveFile = downloadFile(archive, osSdkRoot, monitor, forceHttp);
        if (archiveFile != null) {
            // Unarchive calls the pre/postInstallHook methods.
            if (unarchive(archive, osSdkRoot, archiveFile, sdkManager, monitor)) {
                monitor.setResult("Installed %1$s", name);
                // Delete the temp archive if it exists, only on success
                deleteFileOrFolder(archiveFile);
                return true;
            }
        }

        return false;
    }

    /**
     * Downloads an archive and returns the temp file with it.
     * Caller is responsible with deleting the temp file when done.
     */
    private File downloadFile(Archive archive,
            String osSdkRoot,
            ITaskMonitor monitor,
            boolean forceHttp) {

        String name = archive.getParentPackage().getShortDescription();
        String desc = String.format("Downloading %1$s", name);
        monitor.setDescription(desc);
        monitor.setResult(desc);

        String link = archive.getUrl();
        if (!link.startsWith("http://")                          //$NON-NLS-1$
                && !link.startsWith("https://")                  //$NON-NLS-1$
                && !link.startsWith("ftp://")) {                 //$NON-NLS-1$
            // Make the URL absolute by prepending the source
            Package pkg = archive.getParentPackage();
            SdkSource src = pkg.getParentSource();
            if (src == null) {
                monitor.setResult("Internal error: no source for archive %1$s", name);
                return null;
            }

            // take the URL to the repository.xml and remove the last component
            // to get the base
            String repoXml = src.getUrl();
            int pos = repoXml.lastIndexOf('/');
            String base = repoXml.substring(0, pos + 1);

            link = base + link;
        }

        if (forceHttp) {
            link = link.replaceAll("https://", "http://");  //$NON-NLS-1$ //$NON-NLS-2$
        }

        // Get the basename of the file we're downloading, i.e. the last component
        // of the URL
        int pos = link.lastIndexOf('/');
        String base = link.substring(pos + 1);

        // Rather than create a real temp file in the system, we simply use our
        // temp folder (in the SDK base folder) and use the archive name for the
        // download. This allows us to reuse or continue downloads.

        File tmpFolder = getTempFolder(osSdkRoot);
        if (!tmpFolder.isDirectory()) {
            if (tmpFolder.isFile()) {
                deleteFileOrFolder(tmpFolder);
            }
            if (!tmpFolder.mkdirs()) {
                monitor.setResult("Failed to create directory %1$s", tmpFolder.getPath());
                return null;
            }
        }
        File tmpFile = new File(tmpFolder, base);

        // if the file exists, check its checksum & size. Use it if complete
        if (tmpFile.exists()) {
            if (tmpFile.length() == archive.getSize()) {
                String chksum = "";
                try {
                    chksum = fileChecksum(archive.getChecksumType().getMessageDigest(),
                                          tmpFile,
                                          monitor);
                } catch (NoSuchAlgorithmException e) {
                    // Ignore.
                }
                if (chksum.equalsIgnoreCase(archive.getChecksum())) {
                    // File is good, let's use it.
                    return tmpFile;
                }
            }

            // Existing file is either of different size or content.
            // TODO: continue download when we support continue mode.
            // Right now, let's simply remove the file and start over.
            deleteFileOrFolder(tmpFile);
        }

        if (fetchUrl(archive, tmpFile, link, desc, monitor)) {
            // Fetching was successful, let's use this file.
            return tmpFile;
        } else {
            // Delete the temp file if we aborted the download
            // TODO: disable this when we want to support partial downloads!
            deleteFileOrFolder(tmpFile);
            return null;
        }
    }

    /**
     * Computes the SHA-1 checksum of the content of the given file.
     * Returns an empty string on error (rather than null).
     */
    private String fileChecksum(MessageDigest digester, File tmpFile, ITaskMonitor monitor) {
        InputStream is = null;
        try {
            is = new FileInputStream(tmpFile);

            byte[] buf = new byte[65536];
            int n;

            while ((n = is.read(buf)) >= 0) {
                if (n > 0) {
                    digester.update(buf, 0, n);
                }
            }

            return getDigestChecksum(digester);

        } catch (FileNotFoundException e) {
            // The FNF message is just the URL. Make it a bit more useful.
            monitor.setResult("File not found: %1$s", e.getMessage());

        } catch (Exception e) {
            monitor.setResult(e.getMessage());

        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    // pass
                }
            }
        }

        return "";  //$NON-NLS-1$
    }

    /**
     * Returns the SHA-1 from a {@link MessageDigest} as an hex string
     * that can be compared with {@link Archive#getChecksum()}.
     */
    private String getDigestChecksum(MessageDigest digester) {
        int n;
        // Create an hex string from the digest
        byte[] digest = digester.digest();
        n = digest.length;
        String hex = "0123456789abcdef";                     //$NON-NLS-1$
        char[] hexDigest = new char[n * 2];
        for (int i = 0; i < n; i++) {
            int b = digest[i] & 0x0FF;
            hexDigest[i*2 + 0] = hex.charAt(b >>> 4);
            hexDigest[i*2 + 1] = hex.charAt(b & 0x0f);
        }

        return new String(hexDigest);
    }

    /**
     * Actually performs the download.
     * Also computes the SHA1 of the file on the fly.
     * <p/>
     * Success is defined as downloading as many bytes as was expected and having the same
     * SHA1 as expected. Returns true on success or false if any of those checks fail.
     * <p/>
     * Increments the monitor by {@link #NUM_MONITOR_INC}.
     */
    private boolean fetchUrl(Archive archive,
            File tmpFile,
            String urlString,
            String description,
            ITaskMonitor monitor) {
        URL url;

        description += " (%1$d%%, %2$.0f KiB/s, %3$d %4$s left)";

        FileOutputStream os = null;
        InputStream is = null;
        try {
            url = new URL(urlString);
            is = url.openStream();
            os = new FileOutputStream(tmpFile);

            MessageDigest digester = archive.getChecksumType().getMessageDigest();

            byte[] buf = new byte[65536];
            int n;

            long total = 0;
            long size = archive.getSize();
            long inc = size / NUM_MONITOR_INC;
            long next_inc = inc;

            long startMs = System.currentTimeMillis();
            long nextMs = startMs + 2000;  // start update after 2 seconds

            while ((n = is.read(buf)) >= 0) {
                if (n > 0) {
                    os.write(buf, 0, n);
                    digester.update(buf, 0, n);
                }

                long timeMs = System.currentTimeMillis();

                total += n;
                if (total >= next_inc) {
                    monitor.incProgress(1);
                    next_inc += inc;
                }

                if (timeMs > nextMs) {
                    long delta = timeMs - startMs;
                    if (total > 0 && delta > 0) {
                        // percent left to download
                        int percent = (int) (100 * total / size);
                        // speed in KiB/s
                        float speed = (float)total / (float)delta * (1000.f / 1024.f);
                        // time left to download the rest at the current KiB/s rate
                        int timeLeft = (speed > 1e-3) ?
                                               (int)(((size - total) / 1024.0f) / speed) :
                                               0;
                        String timeUnit = "seconds";
                        if (timeLeft > 120) {
                            timeUnit = "minutes";
                            timeLeft /= 60;
                        }

                        monitor.setDescription(description, percent, speed, timeLeft, timeUnit);
                    }
                    nextMs = timeMs + 1000;  // update every second
                }

                if (monitor.isCancelRequested()) {
                    monitor.setResult("Download aborted by user at %1$d bytes.", total);
                    return false;
                }

            }

            if (total != size) {
                monitor.setResult("Download finished with wrong size. Expected %1$d bytes, got %2$d bytes.",
                        size, total);
                return false;
            }

            // Create an hex string from the digest
            String actual   = getDigestChecksum(digester);
            String expected = archive.getChecksum();
            if (!actual.equalsIgnoreCase(expected)) {
                monitor.setResult("Download finished with wrong checksum. Expected %1$s, got %2$s.",
                        expected, actual);
                return false;
            }

            return true;

        } catch (FileNotFoundException e) {
            // The FNF message is just the URL. Make it a bit more useful.
            monitor.setResult("File not found: %1$s", e.getMessage());

        } catch (Exception e) {
            monitor.setResult(e.getMessage());

        } finally {
            if (os != null) {
                try {
                    os.close();
                } catch (IOException e) {
                    // pass
                }
            }

            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    // pass
                }
            }
        }

        return false;
    }

    /**
     * Install the given archive in the given folder.
     */
    private boolean unarchive(Archive archive,
            String osSdkRoot,
            File archiveFile,
            SdkManager sdkManager,
            ITaskMonitor monitor) {
        boolean success = false;
        Package pkg = archive.getParentPackage();
        String pkgName = pkg.getShortDescription();
        String pkgDesc = String.format("Installing %1$s", pkgName);
        monitor.setDescription(pkgDesc);
        monitor.setResult(pkgDesc);

        // We always unzip in a temp folder which name depends on the package type
        // (e.g. addon, tools, etc.) and then move the folder to the destination folder.
        // If the destination folder exists, it will be renamed and deleted at the very
        // end if everything succeeded.

        String pkgKind = pkg.getClass().getSimpleName();

        File destFolder = null;
        File unzipDestFolder = null;
        File oldDestFolder = null;

        try {
            // Find a new temp folder that doesn't exist yet
            unzipDestFolder = createTempFolder(osSdkRoot, pkgKind, "new");  //$NON-NLS-1$

            if (unzipDestFolder == null) {
                // this should not seriously happen.
                monitor.setResult("Failed to find a temp directory in %1$s.", osSdkRoot);
                return false;
            }

            if (!unzipDestFolder.mkdirs()) {
                monitor.setResult("Failed to create directory %1$s", unzipDestFolder.getPath());
                return false;
            }

            String[] zipRootFolder = new String[] { null };
            if (!unzipFolder(archiveFile, archive.getSize(),
                    unzipDestFolder, pkgDesc,
                    zipRootFolder, monitor)) {
                return false;
            }

            if (!generateSourceProperties(archive, unzipDestFolder)) {
                return false;
            }

            // Compute destination directory
            destFolder = pkg.getInstallFolder(osSdkRoot, zipRootFolder[0], sdkManager);

            if (destFolder == null) {
                // this should not seriously happen.
                monitor.setResult("Failed to compute installation directory for %1$s.", pkgName);
                return false;
            }

            if (!pkg.preInstallHook(archive, monitor, osSdkRoot, destFolder)) {
                monitor.setResult("Skipping archive: %1$s", pkgName);
                return false;
            }

            // Swap the old folder by the new one.
            // We have 2 "folder rename" (aka moves) to do.
            // They must both succeed in the right order.
            boolean move1done = false;
            boolean move2done = false;
            while (!move1done || !move2done) {
                File renameFailedForDir = null;

                // Case where the dest dir already exists
                if (!move1done) {
                    if (destFolder.isDirectory()) {
                        // Create a new temp/old dir
                        if (oldDestFolder == null) {
                            oldDestFolder = createTempFolder(osSdkRoot, pkgKind, "old");  //$NON-NLS-1$
                        }
                        if (oldDestFolder == null) {
                            // this should not seriously happen.
                            monitor.setResult("Failed to find a temp directory in %1$s.", osSdkRoot);
                            return false;
                        }

                        // try to move the current dest dir to the temp/old one
                        if (!destFolder.renameTo(oldDestFolder)) {
                            monitor.setResult("Failed to rename directory %1$s to %2$s.",
                                    destFolder.getPath(), oldDestFolder.getPath());
                            renameFailedForDir = destFolder;
                        }
                    }

                    move1done = (renameFailedForDir == null);
                }

                // Case where there's no dest dir or we successfully moved it to temp/old
                // We now try to move the temp/unzip to the dest dir
                if (move1done && !move2done) {
                    if (renameFailedForDir == null && !unzipDestFolder.renameTo(destFolder)) {
                        monitor.setResult("Failed to rename directory %1$s to %2$s",
                                unzipDestFolder.getPath(), destFolder.getPath());
                        renameFailedForDir = unzipDestFolder;
                    }

                    move2done = (renameFailedForDir == null);
                }

                if (renameFailedForDir != null) {
                    if (SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_WINDOWS) {

                        String msg = String.format(
                                "-= Warning ! =-\n" +
                                "A folder failed to be renamed or moved. On Windows this " +
                                "typically means that a program is using that folder (for example " +
                                "Windows Explorer or your anti-virus software.)\n" +
                                "Please momentarily deactivate your anti-virus software.\n" +
                                "Please also close any running programs that may be accessing " +
                                "the directory '%1$s'.\n" +
                                "When ready, press YES to try again.",
                                renameFailedForDir.getPath());

                        if (monitor.displayPrompt("SDK Manager: failed to install", msg)) {
                            // loop, trying to rename the temp dir into the destination
                            continue;
                        }

                    }
                    return false;
                }
                break;
            }

            unzipDestFolder = null;
            success = true;
            pkg.postInstallHook(archive, monitor, destFolder);
            return true;

        } finally {
            // Cleanup if the unzip folder is still set.
            deleteFileOrFolder(oldDestFolder);
            deleteFileOrFolder(unzipDestFolder);

            // In case of failure, we call the postInstallHool with a null directory
            if (!success) {
                pkg.postInstallHook(archive, monitor, null /*installDir*/);
            }
        }
    }

    /**
     * Unzips a zip file into the given destination directory.
     *
     * The archive file MUST have a unique "root" folder. This root folder is skipped when
     * unarchiving. However we return that root folder name to the caller, as it can be used
     * as a template to know what destination directory to use in the Add-on case.
     */
    @SuppressWarnings("unchecked")
    private boolean unzipFolder(File archiveFile,
            long compressedSize,
            File unzipDestFolder,
            String description,
            String[] outZipRootFolder,
            ITaskMonitor monitor) {

        description += " (%1$d%%)";

        ZipFile zipFile = null;
        try {
            zipFile = new ZipFile(archiveFile);

            // figure if we'll need to set the unix permission
            boolean usingUnixPerm = SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_DARWIN ||
                    SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_LINUX;

            // To advance the percent and the progress bar, we don't know the number of
            // items left to unzip. However we know the size of the archive and the size of
            // each uncompressed item. The zip file format overhead is negligible so that's
            // a good approximation.
            long incStep = compressedSize / NUM_MONITOR_INC;
            long incTotal = 0;
            long incCurr = 0;
            int lastPercent = 0;

            byte[] buf = new byte[65536];

            Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();

                String name = entry.getName();

                // ZipFile entries should have forward slashes, but not all Zip
                // implementations can be expected to do that.
                name = name.replace('\\', '/');

                // Zip entries are always packages in a top-level directory
                // (e.g. docs/index.html). However we want to use our top-level
                // directory so we drop the first segment of the path name.
                int pos = name.indexOf('/');
                if (pos < 0 || pos == name.length() - 1) {
                    continue;
                } else {
                    if (outZipRootFolder[0] == null && pos > 0) {
                        outZipRootFolder[0] = name.substring(0, pos);
                    }
                    name = name.substring(pos + 1);
                }

                File destFile = new File(unzipDestFolder, name);

                if (name.endsWith("/")) {  //$NON-NLS-1$
                    // Create directory if it doesn't exist yet. This allows us to create
                    // empty directories.
                    if (!destFile.isDirectory() && !destFile.mkdirs()) {
                        monitor.setResult("Failed to create temp directory %1$s",
                                destFile.getPath());
                        return false;
                    }
                    continue;
                } else if (name.indexOf('/') != -1) {
                    // Otherwise it's a file in a sub-directory.
                    // Make sure the parent directory has been created.
                    File parentDir = destFile.getParentFile();
                    if (!parentDir.isDirectory()) {
                        if (!parentDir.mkdirs()) {
                            monitor.setResult("Failed to create temp directory %1$s",
                                    parentDir.getPath());
                            return false;
                        }
                    }
                }

                FileOutputStream fos = null;
                try {
                    fos = new FileOutputStream(destFile);
                    int n;
                    InputStream entryContent = zipFile.getInputStream(entry);
                    while ((n = entryContent.read(buf)) != -1) {
                        if (n > 0) {
                            fos.write(buf, 0, n);
                        }
                    }
                } finally {
                    if (fos != null) {
                        fos.close();
                    }
                }

                // if needed set the permissions.
                if (usingUnixPerm && destFile.isFile()) {
                    // get the mode and test if it contains the executable bit
                    int mode = entry.getUnixMode();
                    if ((mode & 0111) != 0) {
                        setExecutablePermission(destFile);
                    }
                }

                // Increment progress bar to match. We update only between files.
                for(incTotal += entry.getCompressedSize(); incCurr < incTotal; incCurr += incStep) {
                    monitor.incProgress(1);
                }

                int percent = (int) (100 * incTotal / compressedSize);
                if (percent != lastPercent) {
                    monitor.setDescription(description, percent);
                    lastPercent = percent;
                }

                if (monitor.isCancelRequested()) {
                    return false;
                }
            }

            return true;

        } catch (IOException e) {
            monitor.setResult("Unzip failed: %1$s", e.getMessage());

        } finally {
            if (zipFile != null) {
                try {
                    zipFile.close();
                } catch (IOException e) {
                    // pass
                }
            }
        }

        return false;
    }

    /**
     * Creates a temp folder in the form of osBasePath/temp/prefix.suffixNNN.
     * <p/>
     * This operation is not atomic so there's no guarantee the folder can't get
     * created in between. This is however unlikely and the caller can assume the
     * returned folder does not exist yet.
     * <p/>
     * Returns null if no such folder can be found (e.g. if all candidates exist,
     * which is rather unlikely) or if the base temp folder cannot be created.
     */
    private File createTempFolder(String osBasePath, String prefix, String suffix) {
        File baseTempFolder = getTempFolder(osBasePath);

        if (!baseTempFolder.isDirectory()) {
            if (baseTempFolder.isFile()) {
                deleteFileOrFolder(baseTempFolder);
            }
            if (!baseTempFolder.mkdirs()) {
                return null;
            }
        }

        for (int i = 1; i < 100; i++) {
            File folder = new File(baseTempFolder,
                    String.format("%1$s.%2$s%3$02d", prefix, suffix, i));  //$NON-NLS-1$
            if (!folder.exists()) {
                return folder;
            }
        }
        return null;
    }

    /**
     * Returns the temp folder used by the SDK Manager.
     * This folder is always at osBasePath/temp.
     */
    private File getTempFolder(String osBasePath) {
        File baseTempFolder = new File(osBasePath, RepoConstants.FD_TEMP);
        return baseTempFolder;
    }

    /**
     * Deletes a file or a directory.
     * Directories are deleted recursively.
     * The argument can be null.
     */
    /*package*/ void deleteFileOrFolder(File fileOrFolder) {
        if (fileOrFolder != null) {
            if (fileOrFolder.isDirectory()) {
                // Must delete content recursively first
                for (File item : fileOrFolder.listFiles()) {
                    deleteFileOrFolder(item);
                }
            }
            if (!fileOrFolder.delete()) {
                fileOrFolder.deleteOnExit();
            }
        }
    }

    /**
     * Generates a source.properties in the destination folder that contains all the infos
     * relevant to this archive, this package and the source so that we can reload them
     * locally later.
     */
    private boolean generateSourceProperties(Archive archive, File unzipDestFolder) {
        Properties props = new Properties();

        archive.saveProperties(props);

        Package pkg = archive.getParentPackage();
        if (pkg != null) {
            pkg.saveProperties(props);
        }

        FileOutputStream fos = null;
        try {
            File f = new File(unzipDestFolder, SdkConstants.FN_SOURCE_PROP);

            fos = new FileOutputStream(f);

            props.store( fos, "## Android Tool: Source of this archive.");  //$NON-NLS-1$

            return true;
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                }
            }
        }

        return false;
    }

    /**
     * Sets the executable Unix permission (0777) on a file or folder.
     * @param file The file to set permissions on.
     * @throws IOException If an I/O error occurs
     */
    private void setExecutablePermission(File file) throws IOException {
        Runtime.getRuntime().exec(new String[] {
           "chmod", "777", file.getAbsolutePath()
        });
    }
}
