/*
 * Copyright (C) 2011 The Android Open Source Project
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

import com.android.sdklib.SdkManager;
import com.android.sdklib.internal.repository.Archive;
import com.android.sdklib.internal.repository.ArchiveInstaller;
import com.android.sdklib.internal.repository.ITask;
import com.android.sdklib.internal.repository.ITaskFactory;
import com.android.sdklib.internal.repository.ITaskMonitor;
import com.android.sdklib.internal.repository.MockEmptySdkManager;
import com.android.sdklib.internal.repository.Package;
import com.android.sdklib.internal.repository.Archive.Arch;
import com.android.sdklib.internal.repository.Archive.Os;
import com.android.sdklib.mock.MockLog;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Properties;

import junit.framework.TestCase;

public class UpdaterDataTest extends TestCase {

    private MockUpdaterData m;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        m = new MockUpdaterData();
        assertEquals("[]", Arrays.toString(m.getInstalled()));
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    /**
     * Tests the case where we have nothing to install.
     */
    public void testInstallArchives_None() {
        m._installArchives(new ArrayList<ArchiveInfo>());
        assertEquals("[]", Arrays.toString(m.getInstalled()));
    }


    /**
     * Tests the case where there's a simple dependency, in the right order
     * (e.g. install A1 then A2 that depends on A1).
     */
    public void testInstallArchives_SimpleDependency() {

        ArrayList<ArchiveInfo> archives = new ArrayList<ArchiveInfo>();

        Archive a1 = new MockEmptyPackage("a1").getLocalArchive();
        ArchiveInfo ai1 = new ArchiveInfo(a1, null, null);

        Archive a2 = new MockEmptyPackage("a2").getLocalArchive();
        ArchiveInfo ai2 = new ArchiveInfo(a2, null, new ArchiveInfo[] { ai1 } );

        archives.add(ai1);
        archives.add(ai2);

        m._installArchives(archives);
        assertEquals("[a1, a2]", Arrays.toString(m.getInstalled()));
    }

    /**
     * Tests the case where there's a simple dependency, in the wrong order
     * (e.g. install A2 then A1 which A2 depends on)
     */
    public void testInstallArchives_ReverseDependency() {

        ArrayList<ArchiveInfo> archives = new ArrayList<ArchiveInfo>();

        Archive a1 = new MockEmptyPackage("a1").getLocalArchive();
        ArchiveInfo ai1 = new ArchiveInfo(a1, null, null);

        Archive a2 = new MockEmptyPackage("a2").getLocalArchive();
        ArchiveInfo ai2 = new ArchiveInfo(a2, null, new ArchiveInfo[] { ai1 } );

        archives.add(ai2);
        archives.add(ai1);

        m._installArchives(archives);
        // TODO fix bug 14393: a2 is not installed because a1 has not been installed yet.
        assertEquals("[a1, a2]", Arrays.toString(m.getInstalled()));
    }

    // ---


    /** A mock UpdaterData that simply records what would have been installed. */
    private static class MockUpdaterData extends UpdaterData {

        private final List<Archive> mInstalled = new ArrayList<Archive>();

        public MockUpdaterData() {
            super("/tmp/SDK", new MockLog());

            setTaskFactory(new MockTaskFactory());
        }

        /** Gives access to the internal {@link #installArchives(List)}. */
        public void _installArchives(List<ArchiveInfo> result) {
            installArchives(result);
        }

        public Archive[] getInstalled() {
            return mInstalled.toArray(new Archive[mInstalled.size()]);
        }

        @Override
        protected void initSdk() {
            setSdkManager(new MockEmptySdkManager("/tmp/SDK"));
        }

        @Override
        public void reloadSdk() {
            // bypass original implementation
        }

        /** Returns a mock installer that simply records what would have been installed. */
        @Override
        protected ArchiveInstaller createArchiveInstaler() {
            return new ArchiveInstaller() {
                @Override
                public boolean install(
                        Archive archive,
                        String osSdkRoot,
                        boolean forceHttp,
                        SdkManager sdkManager,
                        ITaskMonitor monitor) {
                    mInstalled.add(archive);
                    return true;
                }
            };
        }
    }

    private static class MockTaskFactory implements ITaskFactory {
        public void start(String title, ITask task) {
            new MockTask(task);
        }
    }

    private static class MockTask implements ITaskMonitor {
        public MockTask(ITask task) {
            task.run(this);
        }

        public ITaskMonitor createSubMonitor(int tickCount) {
            return this;
        }

        public boolean displayPrompt(String title, String message) {
            return false;
        }

        public int getProgress() {
            return 0;
        }

        public void incProgress(int delta) {
            // ignore
        }

        public boolean isCancelRequested() {
            return false;
        }

        public void setDescription(String descriptionFormat, Object... args) {
            // ignore
        }

        public void setProgressMax(int max) {
            // ignore
        }

        public void setResult(String resultFormat, Object... args) {
            // ignore
        }
    }

    private static class MockEmptyPackage extends Package {
        private final String mTestHandle;

        public MockEmptyPackage(String testHandle) {
            super(
                null /*source*/,
                null /*props*/,
                0 /*revision*/,
                null /*license*/,
                null /*description*/,
                null /*descUrl*/,
                Os.ANY /*archiveOs*/,
                Arch.ANY /*archiveArch*/,
                null /*archiveOsPath*/
                );
            mTestHandle = testHandle;
        }

        @Override
        protected Archive createLocalArchive(
                Properties props,
                Os archiveOs,
                Arch archiveArch,
                String archiveOsPath) {
            return new Archive(this, props, archiveOs, archiveArch, archiveOsPath) {
                @Override
                public String toString() {
                    return mTestHandle;
                }
            };
        }

        public Archive getLocalArchive() {
            return getArchives()[0];
        }

        @Override
        public File getInstallFolder(String osSdkRoot, SdkManager sdkManager) {
            return null;
        }

        @Override
        public String getShortDescription() {
            return this.getClass().getSimpleName();
        }

        @Override
        public boolean sameItemAs(Package pkg) {
            return false;
        }

    }
}
