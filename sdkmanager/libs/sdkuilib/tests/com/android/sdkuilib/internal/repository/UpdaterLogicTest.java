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

package com.android.sdkuilib.internal.repository;

import com.android.sdklib.ISdkLog;
import com.android.sdklib.SdkManager;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.sdklib.internal.repository.Archive;
import com.android.sdklib.internal.repository.ITaskFactory;
import com.android.sdklib.internal.repository.MockAddonPackage;
import com.android.sdklib.internal.repository.MockBrokenPackage;
import com.android.sdklib.internal.repository.MockPlatformPackage;
import com.android.sdklib.internal.repository.MockPlatformToolPackage;
import com.android.sdklib.internal.repository.MockToolPackage;
import com.android.sdklib.internal.repository.Package;
import com.android.sdklib.internal.repository.SdkSource;
import com.android.sdklib.internal.repository.SdkSources;
import com.android.sdkuilib.internal.repository.icons.ImageFactory;

import org.eclipse.swt.widgets.Shell;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import junit.framework.TestCase;

public class UpdaterLogicTest extends TestCase {

    private static class NullUpdaterData implements IUpdaterData {

        public AvdManager getAvdManager() {
            return null;
        }

        public ImageFactory getImageFactory() {
            return null;
        }

        public ISdkLog getSdkLog() {
            return null;
        }

        public SdkManager getSdkManager() {
            return null;
        }

        public SettingsController getSettingsController() {
            return null;
        }

        public ITaskFactory getTaskFactory() {
            return null;
        }

        public Shell getWindowShell() {
            return null;
        }

    }

    private static class MockUpdaterLogic extends UpdaterLogic {
        private final Package[] mRemotePackages;

        public MockUpdaterLogic(IUpdaterData updaterData, Package[] remotePackages) {
            super(updaterData);
            mRemotePackages = remotePackages;
        }

        @Override
        protected void fetchRemotePackages(Collection<Package> remotePkgs,
                SdkSource[] remoteSources) {
            // Ignore remoteSources and instead uses the remotePackages list given to the
            // constructor.
            if (mRemotePackages != null) {
                remotePkgs.addAll(Arrays.asList(mRemotePackages));
            }
        }
    }

    /**
     * Addon packages depend on a base platform package.
     * This test checks that UpdaterLogic.findPlatformToolsDependency(...)
     * can find the base platform for a given addon.
     */
    public void testFindAddonDependency() {
        MockUpdaterLogic mul = new MockUpdaterLogic(new NullUpdaterData(), null);

        MockPlatformPackage p1 = new MockPlatformPackage(1, 1);
        MockPlatformPackage p2 = new MockPlatformPackage(2, 1);

        MockAddonPackage a1 = new MockAddonPackage(p1, 1);
        MockAddonPackage a2 = new MockAddonPackage(p2, 2);

        ArrayList<ArchiveInfo> out = new ArrayList<ArchiveInfo>();
        ArrayList<Archive> selected = new ArrayList<Archive>();
        ArrayList<Package> remote = new ArrayList<Package>();

        // a2 depends on p2, which is not in the locals
        Package[] localPkgs = { p1, a1 };
        ArchiveInfo[] locals = mul.createLocalArchives(localPkgs);

        SdkSource[] sources = null;

        // a2 now depends on a "fake" archive info with no newArchive that wraps the missing
        // underlying platform.
        ArchiveInfo fai = mul.findPlatformDependency(a2, out, selected, remote, sources, locals);
        assertNotNull(fai);
        assertNull(fai.getNewArchive());
        assertTrue(fai.isRejected());
        assertEquals(0, out.size());

        // p2 is now selected, and should be scheduled for install in out
        Archive p2_archive = p2.getArchives()[0];
        selected.add(p2_archive);
        ArchiveInfo ai2 = mul.findPlatformDependency(a2, out, selected, remote, sources, locals);
        assertNotNull(ai2);
        assertSame(p2_archive, ai2.getNewArchive());
        assertEquals(1, out.size());
        assertSame(p2_archive, out.get(0).getNewArchive());
    }

    /**
     * Broken add-on packages require an exact platform package to be present or installed.
     * This tests checks that findExactApiLevelDependency() can find a base
     * platform package for a given broken add-on package.
     */
    public void testFindExactApiLevelDependency() {
        MockUpdaterLogic mul = new MockUpdaterLogic(new NullUpdaterData(), null);

        MockPlatformPackage p1 = new MockPlatformPackage(1, 1);
        MockPlatformPackage p2 = new MockPlatformPackage(2, 1);

        MockBrokenPackage a1 = new MockBrokenPackage(0, 1);
        MockBrokenPackage a2 = new MockBrokenPackage(0, 2);

        ArrayList<ArchiveInfo> out = new ArrayList<ArchiveInfo>();
        ArrayList<Archive> selected = new ArrayList<Archive>();
        ArrayList<Package> remote = new ArrayList<Package>();

        // a2 depends on p2, which is not in the locals
        Package[] localPkgs = { p1, a1 };
        ArchiveInfo[] locals = mul.createLocalArchives(localPkgs);

        SdkSource[] sources = null;

        // a1 depends on p1, which can be found in the locals. p1 is already "installed"
        // so we donn't need to suggest it as a dependency to solve any problem.
        ArchiveInfo found = mul.findExactApiLevelDependency(
                a1, out, selected, remote, sources, locals);
        assertNull(found);

        // a2 now depends on a "fake" archive info with no newArchive that wraps the missing
        // underlying platform.
        found = mul.findExactApiLevelDependency(a2, out, selected, remote, sources, locals);
        assertNotNull(found);
        assertNull(found.getNewArchive());
        assertTrue(found.isRejected());
        assertEquals(0, out.size());

        // p2 is now selected, and should be scheduled for install in out
        Archive p2_archive = p2.getArchives()[0];
        selected.add(p2_archive);
        found = mul.findExactApiLevelDependency(a2, out, selected, remote, sources, locals);
        assertNotNull(found);
        assertSame(p2_archive, found.getNewArchive());
        assertEquals(1, out.size());
        assertSame(p2_archive, out.get(0).getNewArchive());
    }

    /**
     * Platform packages depend on a tool package.
     * This tests checks that UpdaterLogic.findToolsDependency() can find a base
     * tool package for a given platform package.
     */
    public void testFindPlatformDependency() {
        MockUpdaterLogic mul = new MockUpdaterLogic(new NullUpdaterData(), null);

        MockPlatformToolPackage pt1 = new MockPlatformToolPackage(1);

        MockToolPackage t1 = new MockToolPackage(1, 1);
        MockToolPackage t2 = new MockToolPackage(2, 1);

        MockPlatformPackage p2 = new MockPlatformPackage(2, 1, 2);

        ArrayList<ArchiveInfo> out = new ArrayList<ArchiveInfo>();
        ArrayList<Archive> selected = new ArrayList<Archive>();
        ArrayList<Package> remote = new ArrayList<Package>();

        // p2 depends on t2, which is not locally installed
        Package[] localPkgs = { t1, pt1 };
        ArchiveInfo[] locals = mul.createLocalArchives(localPkgs);

        SdkSource[] sources = null;

        // p2 now depends on a "fake" archive info with no newArchive that wraps the missing
        // underlying tool
        ArchiveInfo fai = mul.findToolsDependency(p2, out, selected, remote, sources, locals);
        assertNotNull(fai);
        assertNull(fai.getNewArchive());
        assertTrue(fai.isRejected());
        assertEquals(0, out.size());

        // t2 is now selected and can be used as a dependency
        Archive t2_archive = t2.getArchives()[0];
        selected.add(t2_archive);
        ArchiveInfo ai2 = mul.findToolsDependency(p2, out, selected, remote, sources, locals);
        assertNotNull(ai2);
        assertSame(t2_archive, ai2.getNewArchive());
        assertEquals(1, out.size());
        assertSame(t2_archive, out.get(0).getNewArchive());
    }

    /**
     * Tool packages require a platform-tool package to be present or installed.
     * This tests checks that UpdaterLogic.findPlatformToolsDependency() can find a base
     * platform-tool package for a given tool package.
     */
    public void testFindPlatformToolDependency() {
        MockUpdaterLogic mul = new MockUpdaterLogic(new NullUpdaterData(), null);

        MockPlatformToolPackage t1 = new MockPlatformToolPackage(1);
        MockPlatformToolPackage t2 = new MockPlatformToolPackage(2);

        MockToolPackage p2 = new MockToolPackage(2, 2);

        ArrayList<ArchiveInfo> out = new ArrayList<ArchiveInfo>();
        ArrayList<Archive> selected = new ArrayList<Archive>();
        ArrayList<Package> remote = new ArrayList<Package>();

        // p2 depends on t2, which is not locally installed
        Package[] localPkgs = { t1 };
        ArchiveInfo[] locals = mul.createLocalArchives(localPkgs);

        SdkSource[] sources = null;

        // p2 now depends on a "fake" archive info with no newArchive that wraps the missing
        // underlying tool
        ArchiveInfo fai = mul.findPlatformToolsDependency(
                                    p2, out, selected, remote, sources, locals);
        assertNotNull(fai);
        assertNull(fai.getNewArchive());
        assertTrue(fai.isRejected());
        assertEquals(0, out.size());

        // t2 is now selected and can be used as a dependency
        Archive t2_archive = t2.getArchives()[0];
        selected.add(t2_archive);
        ArchiveInfo ai2 = mul.findPlatformToolsDependency(
                                    p2, out, selected, remote, sources, locals);
        assertNotNull(ai2);
        assertSame(t2_archive, ai2.getNewArchive());
        assertEquals(1, out.size());
        assertSame(t2_archive, out.get(0).getNewArchive());
    }

    public void testComputeRevisionUpdate() {
        // Scenario:
        // - user has tools rev 7 installed + plat-tools rev 1 installed
        // - server has tools rev 8, depending on plat-tools rev 2
        // - server has tools rev 9, depending on plat-tools rev 3
        // - server has platform 9 that requires min-tools-rev 9
        //
        // If we do an update all, we want to the installer to pick up:
        // - the new platform 9
        // - the tools rev 9 (required by platform 9)
        // - the plat-tools rev 3 (required by tools rev 9)

        final MockPlatformToolPackage pt1 = new MockPlatformToolPackage(1);
        final MockPlatformToolPackage pt2 = new MockPlatformToolPackage(2);
        final MockPlatformToolPackage pt3 = new MockPlatformToolPackage(3);

        final MockToolPackage t7 = new MockToolPackage(7, 1 /*min-plat-tools*/);
        final MockToolPackage t8 = new MockToolPackage(8, 2 /*min-plat-tools*/);
        final MockToolPackage t9 = new MockToolPackage(9, 3 /*min-plat-tools*/);

        final MockPlatformPackage p9 = new MockPlatformPackage(9, 1, 9 /*min-tools*/);

        // Note: the mock updater logic gets the remotes packages from the array given
        // here and bypasses the source (to avoid fetching any actual URLs)
        MockUpdaterLogic mul = new MockUpdaterLogic(new NullUpdaterData(),
                new Package[] { t8, pt2, t9, pt3, p9 });

        SdkSources sources = new SdkSources();
        Package[] localPkgs = { t7, pt1 };

        List<ArchiveInfo> selected = mul.computeUpdates(
                null /*selectedArchives*/,
                sources,
                localPkgs,
                false /*includeObsoletes*/);

        assertEquals(
                "[Android SDK Platform-tools, revision 3, " +
                 "Android SDK Tools, revision 9]",
                Arrays.toString(selected.toArray()));

        mul.addNewPlatforms(
                selected,
                sources,
                localPkgs,
                false /*includeObsoletes*/);

        assertEquals(
                "[Android SDK Platform-tools, revision 3, " +
                 "Android SDK Tools, revision 9, " +
                 "SDK Platform Android android-9, API 9, revision 1]",
                Arrays.toString(selected.toArray()));

        // Now try again but reverse the order of the remote package list.

        mul = new MockUpdaterLogic(new NullUpdaterData(),
                new Package[] { p9, t9, pt3, t8, pt2 });

        selected = mul.computeUpdates(
                null /*selectedArchives*/,
                sources,
                localPkgs,
                false /*includeObsoletes*/);

        assertEquals(
                "[Android SDK Platform-tools, revision 3, " +
                 "Android SDK Tools, revision 9]",
                Arrays.toString(selected.toArray()));

        mul.addNewPlatforms(
                selected,
                sources,
                localPkgs,
                false /*includeObsoletes*/);

        assertEquals(
                "[Android SDK Platform-tools, revision 3, " +
                 "Android SDK Tools, revision 9, " +
                 "SDK Platform Android android-9, API 9, revision 1]",
                Arrays.toString(selected.toArray()));
    }
}
