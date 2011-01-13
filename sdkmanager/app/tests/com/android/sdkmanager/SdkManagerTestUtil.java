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

package com.android.sdkmanager;

import com.android.prefs.AndroidLocation;
import com.android.sdklib.SdkConstants;
import com.android.sdklib.SdkManager;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class SdkManagerTestUtil {
    /**
     * Build enough of a skeleton SDK to make the tests pass.
     *<p>
     * Ideally this wouldn't touch the file system, but I'm not inclined to
     * fiddle around with mock file systems just at the moment.
     *
     * @return an sdk manager to a fake sdk
     * @throws IOException
     */
    public static File makeFakeSdk(File fakeSdk) throws IOException {
        fakeSdk.delete();
        fakeSdk.mkdirs();
        AndroidLocation.resetFolder();
        System.setProperty("user.home", fakeSdk.getAbsolutePath());
        File addonsDir = new File(fakeSdk, SdkConstants.FD_ADDONS);
        addonsDir.mkdir();
        File toolsLibEmuDir = new File(fakeSdk, SdkConstants.OS_SDK_TOOLS_LIB_FOLDER + "emulator");
        toolsLibEmuDir.mkdirs();
        new File(toolsLibEmuDir, "snapshots.img").createNewFile();
        File platformsDir = new File(fakeSdk, SdkConstants.FD_PLATFORMS);

        // Creating a fake target here on down
        File targetDir = new File(platformsDir, "v0_0");
        targetDir.mkdirs();
        new File(targetDir, SdkConstants.FN_FRAMEWORK_LIBRARY).createNewFile();
        new File(targetDir, SdkConstants.FN_FRAMEWORK_AIDL).createNewFile();
        new File(targetDir, SdkConstants.FN_SOURCE_PROP).createNewFile();
        File buildProp = new File(targetDir, SdkConstants.FN_BUILD_PROP);
        FileWriter out = new FileWriter(buildProp);
        out.write(SdkManager.PROP_VERSION_RELEASE + "=0.0\n");
        out.write(SdkManager.PROP_VERSION_SDK + "=0\n");
        out.write(SdkManager.PROP_VERSION_CODENAME + "=REL\n");
        out.close();
        File imagesDir = new File(targetDir, "images");
        imagesDir.mkdirs();
        new File(imagesDir, "userdata.img").createNewFile();
        File skinsDir = new File(targetDir, "skins");
        File hvgaDir = new File(skinsDir, "HVGA");
        hvgaDir.mkdirs();
        return fakeSdk;
    }

    /**
     * Recursive delete directory. Mostly for fake SDKs.
     *
     * @param root directory to delete
     */
    public static void deleteDir(File root) {
        if (root.exists()) {
            for (File file : root.listFiles()) {
                if (file.isDirectory()) {
                    deleteDir(file);
                } else {
                    file.delete();
                }
            }
            root.delete();
        }
    }

}
