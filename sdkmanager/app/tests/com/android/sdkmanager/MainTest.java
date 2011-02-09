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

package com.android.sdkmanager;


import static java.io.File.createTempFile;

import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkManager;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.sdklib.mock.MockLog;

import java.io.File;

import junit.framework.TestCase;

public class MainTest extends TestCase {

    private File mFakeSdk;
    private MockLog mLog;
    private SdkManager mSdkManager;
    private AvdManager mAvdManager;
    private File mAvdFolder;
    private IAndroidTarget mTarget;
    private File fakeSdkDir;

    @Override
    public void setUp() throws Exception {
        mLog = new MockLog();
        fakeSdkDir = createTempFile(this.getClass().getSimpleName() + "_" + this.getName(), null);
        mFakeSdk = SdkManagerTestUtil.makeFakeSdk(fakeSdkDir);
        mSdkManager = SdkManager.createManager(mFakeSdk.getAbsolutePath(), mLog);
        assertNotNull("sdkManager location was invalid", mSdkManager);

        mAvdManager = new AvdManager(mSdkManager, mLog);
        mAvdFolder = new File(mFakeSdk, "avdData");
        mTarget = mSdkManager.getTargets()[0];
    }

    @Override
    public void tearDown() throws Exception {
        SdkManagerTestUtil.deleteDir(mFakeSdk);
    }

    public void txestDisplayEmptyAvdList() {
        Main main = new Main();
        main.setLogger(mLog);
        mLog.clear();
        main.displayAvdList(mAvdManager);
        assertEquals("P Available Android Virtual Devices:\n", mLog.toString());
    }

    public void testDisplayAvdListOfOneNonSnapshot() {
        Main main = new Main();
        main.setLogger(mLog);
        mAvdManager.createAvd(
                mAvdFolder, this.getName(), mTarget, null, null, null, false, false, mLog);
        mLog.clear();
        main.displayAvdList(mAvdManager);
        assertEquals(
                "[P Available Android Virtual Devices:\n"
                + ", P     Name: " + this.getName() + "\n"
                + ", P     Path: " + mAvdFolder + "\n"
                + ", P   Target: Android 0.0 (API level 0)\n"
                + ", P     Skin: HVGA\n"
                + "]",
                mLog.toString());
    }

    public void testDisplayAvdListOfOneSnapshot() {
        Main main = new Main();
        main.setLogger(mLog);
        mAvdManager.createAvd(
                mAvdFolder, this.getName(), mTarget, null, null, null, false, true, mLog);
        mLog.clear();
        main.displayAvdList(mAvdManager);
        assertEquals(
                "[P Available Android Virtual Devices:\n"
                + ", P     Name: " + this.getName() + "\n"
                + ", P     Path: " + mAvdFolder + "\n"
                + ", P   Target: Android 0.0 (API level 0)\n"
                + ", P     Skin: HVGA\n"
                + ", P Snapshot: true\n"
                + "]",
                mLog.toString());
    }
}
