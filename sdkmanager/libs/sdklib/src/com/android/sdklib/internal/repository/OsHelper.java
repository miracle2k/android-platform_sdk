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

package com.android.sdklib.internal.repository;

import com.android.sdklib.SdkConstants;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;


/**
 * Helper methods used when dealing with archives installation.
 */
abstract class OsHelper {

    /**
     * Helper to delete a file or a directory.
     * For a directory, recursively deletes all of its content.
     * Files that cannot be deleted right away are marked for deletion on exit.
     * The argument can be null.
     */
    static void deleteFileOrFolder(File fileOrFolder) {
        if (fileOrFolder != null) {
            if (fileOrFolder.isDirectory()) {
                // Must delete content recursively first
                for (File item : fileOrFolder.listFiles()) {
                    deleteFileOrFolder(item);
                }
            }

            if (SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_WINDOWS) {
                // Trying to delete a resource on windows might fail if there's a file
                // indexer locking the resource. Generally retrying will be enough to
                // make it work.
                //
                // Try for half a second before giving up.

                for (int i = 0; i < 5; i++) {
                    if (fileOrFolder.delete()) {
                        return;
                    }

                    try {
                        Thread.sleep(100 /*ms*/);
                    } catch (InterruptedException e) {
                        // Ignore.
                    }
                }

                fileOrFolder.deleteOnExit();

            } else {
                // On Linux or Mac, just straight deleting it should just work.

                if (!fileOrFolder.delete()) {
                    fileOrFolder.deleteOnExit();
                }
            }
        }
    }

    /**
     * Sets the executable Unix permission (0777) on a file or folder.
     * <p/>
     * This invokes a chmod exec, so there is no guarantee of it being fast.
     * Caller must make sure to not invoke this under Windows.
     *
     * @param file The file to set permissions on.
     * @throws IOException If an I/O error occurs
     */
    static void setExecutablePermission(File file) throws IOException {
        Runtime.getRuntime().exec(new String[] {
           "chmod", "777", file.getAbsolutePath()
        });
    }

    /**
     * Copies a binary file.
     *
     * @param source the source file to copy
     * @param dest the destination file to write
     * @return True if the file was successfully copied. False otherwise.
     */
    static boolean copyFile(File source, File dest) {
        byte[] buffer = new byte[8192];

        FileInputStream fis = null;
        FileOutputStream fos = null;
        try {
            fis = new FileInputStream(source);
            fos = new FileOutputStream(dest);

            int read;
            while ((read = fis.read(buffer)) != -1) {
                fos.write(buffer, 0, read);
            }

            return true;

        } catch (Exception e) {
            // Ignore. Simply return false below.

        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    // Ignore.
                }
            }
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    // Ignore.
                }
            }
        }

        return false;
    }
}
