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

package com.android.hierarchyviewer;

import com.android.hierarchyviewerlib.HierarchyViewerDirector;
import com.android.sdklib.SdkConstants;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * This is the application version of the director.
 */
public class HierarchyViewerApplicationDirector extends HierarchyViewerDirector {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    public void terminate() {
        super.terminate();
        executor.shutdown();
    }

    /*
     * Gets the location of adb. The script that runs the hierarchy viewer
     * defines com.android.hierarchyviewer.bindir.
     */
    @Override
    public String getAdbLocation() {
        String hvParentLocation = System.getProperty("com.android.hierarchyviewer.bindir");
        // TODO REMOVE THIS.
        hvParentLocation = "/usr/local/google/android-ext/out/host/linux-x86/bin";
        System.out.println(hvParentLocation);
        if (hvParentLocation != null && hvParentLocation.length() != 0) {
            return hvParentLocation + File.separator + SdkConstants.FN_ADB;
        }
        return SdkConstants.FN_ADB;
    }

    /*
     * In the application, we handle background tasks using a single thread,
     * just to get rid of possible race conditions that can occur. We update the
     * progress bar to show that we are doing something in the background.
     */
    @Override
    public void executeInBackground(final Runnable task) {
        executor.execute(new Runnable() {
            public void run() {
                System.out.println("STARTING TASK");
                task.run();
                System.out.println("ENDING TASK");
            }
        });
    }

}
