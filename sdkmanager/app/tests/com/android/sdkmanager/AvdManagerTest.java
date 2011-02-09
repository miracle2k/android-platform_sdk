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
import com.android.sdklib.internal.project.ProjectProperties;
import com.android.sdklib.io.FileWrapper;
import com.android.sdklib.mock.MockLog;

import java.io.File;
import java.util.Map;

import junit.framework.TestCase;

public class AvdManagerTest extends TestCase {

    private AvdManager mAvdManager;
    private SdkManager mSdkManager;
    private MockLog mLog;
    private File mFakeSdk;
    private File mAvdFolder;
    private IAndroidTarget mTarget;

    @Override
    public void setUp() throws Exception {
        mLog = new MockLog();
        mFakeSdk = SdkManagerTestUtil.makeFakeSdk(createTempFile(this.getClass().getSimpleName(), null));
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

    public void testCreateAvdWithoutSnapshot() {
        mAvdManager.createAvd(
                mAvdFolder, this.getName(), mTarget, null, null, null, false, false, mLog);

        assertEquals("[P Created AVD '" + this.getName() + "' based on Android 0.0\n]",
                mLog.toString());
        assertTrue("Expected config.ini in " + mAvdFolder,
                new File(mAvdFolder, "config.ini").exists());
        Map<String, String> map = ProjectProperties.parsePropertyFile(
                new FileWrapper(mAvdFolder, "config.ini"), mLog);
        assertEquals("HVGA", map.get("skin.name"));
        assertEquals("platforms/v0_0/skins/HVGA", map.get("skin.path"));
        assertEquals("platforms/v0_0/images/", map.get("image.sysdir.1"));
        assertEquals(null, map.get("snapshot.present"));
        assertTrue("Expected userdata.img in " + mAvdFolder,
                new File(mAvdFolder, "userdata.img").exists());
        assertFalse("Expected NO snapshots.img in " + mAvdFolder,
                new File(mAvdFolder, "snapshots.img").exists());
    }

    public void testCreateAvdWithSnapshot() {
        mAvdManager.createAvd(
                mAvdFolder, this.getName(), mTarget, null, null, null, false, true, mLog);

        assertEquals("[P Created AVD '" + this.getName() + "' based on Android 0.0\n]",
                mLog.toString());
        assertTrue("Expected snapshots.img in " + mAvdFolder,
                new File(mAvdFolder, "snapshots.img").exists());
        Map<String, String> map = ProjectProperties.parsePropertyFile(
                new FileWrapper(mAvdFolder, "config.ini"), mLog);
        assertEquals("true", map.get("snapshot.present"));
    }
}
