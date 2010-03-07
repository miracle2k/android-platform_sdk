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

import com.android.sdklib.AndroidVersion;
import com.android.sdklib.internal.repository.AddonPackage;
import com.android.sdklib.internal.repository.Archive;
import com.android.sdklib.internal.repository.DocPackage;
import com.android.sdklib.internal.repository.ExtraPackage;
import com.android.sdklib.internal.repository.IMinApiLevelDependency;
import com.android.sdklib.internal.repository.IMinToolsDependency;
import com.android.sdklib.internal.repository.IPackageVersion;
import com.android.sdklib.internal.repository.IPlatformDependency;
import com.android.sdklib.internal.repository.MinToolsPackage;
import com.android.sdklib.internal.repository.Package;
import com.android.sdklib.internal.repository.PlatformPackage;
import com.android.sdklib.internal.repository.RepoSource;
import com.android.sdklib.internal.repository.RepoSources;
import com.android.sdklib.internal.repository.SamplePackage;
import com.android.sdklib.internal.repository.ToolPackage;
import com.android.sdklib.internal.repository.Package.UpdateInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

/**
 * The logic to compute which packages to install, based on the choices
 * made by the user. This adds dependent packages as needed.
 * <p/>
 * When the user doesn't provide a selection, looks at local package to find
 * those that can be updated and compute dependencies too.
 */
class UpdaterLogic {

    /**
     * Compute which packages to install by taking the user selection
     * and adding dependent packages as needed.
     *
     * When the user doesn't provide a selection, looks at local packages to find
     * those that can be updated and compute dependencies too.
     */
    public ArrayList<ArchiveInfo> computeUpdates(
            Collection<Archive> selectedArchives,
            RepoSources sources,
            Package[] localPkgs) {

        ArrayList<ArchiveInfo> archives = new ArrayList<ArchiveInfo>();
        ArrayList<Package> remotePkgs = new ArrayList<Package>();
        RepoSource[] remoteSources = sources.getSources();

        // Create ArchiveInfos out of local (installed) packages.
        ArchiveInfo[] localArchives = createLocalArchives(localPkgs);

        if (selectedArchives == null) {
            selectedArchives = findUpdates(localArchives, remotePkgs, remoteSources);
        }

        for (Archive a : selectedArchives) {
            insertArchive(a,
                    archives,
                    selectedArchives,
                    remotePkgs,
                    remoteSources,
                    localArchives,
                    false /*automated*/);
        }

        return archives;
    }

    /**
     * Finds new packages that the user does not have in his/her local SDK
     * and adds them to the list of archives to install.
     */
    public void addNewPlatforms(ArrayList<ArchiveInfo> archives,
            RepoSources sources,
            Package[] localPkgs) {

        // Create ArchiveInfos out of local (installed) packages.
        ArchiveInfo[] localArchives = createLocalArchives(localPkgs);

        // Find the highest platform installed
        float currentPlatformScore = 0;
        float currentSampleScore = 0;
        float currentAddonScore = 0;
        float currentDocScore = 0;
        HashMap<String, Float> currentExtraScore = new HashMap<String, Float>();
        for (Package p : localPkgs) {
            int rev = p.getRevision();
            int api = 0;
            boolean isPreview = false;
            if (p instanceof IPackageVersion) {
                AndroidVersion vers = ((IPackageVersion) p).getVersion();
                api = vers.getApiLevel();
                isPreview = vers.isPreview();
            }

            // The score is 10*api + (1 if preview) + rev/100
            // This allows previews to rank above a non-preview and
            // allows revisions to rank appropriately.
            float score = api * 10 + (isPreview ? 1 : 0) + rev/100.f;

            if (p instanceof PlatformPackage) {
                currentPlatformScore = Math.max(currentPlatformScore, score);
            } else if (p instanceof SamplePackage) {
                currentSampleScore = Math.max(currentSampleScore, score);
            } else if (p instanceof AddonPackage) {
                currentAddonScore = Math.max(currentAddonScore, score);
            } else if (p instanceof ExtraPackage) {
                currentExtraScore.put(((ExtraPackage) p).getPath(), score);
            } else if (p instanceof DocPackage) {
                currentDocScore = Math.max(currentDocScore, score);
            }
        }

        RepoSource[] remoteSources = sources.getSources();
        ArrayList<Package> remotePkgs = new ArrayList<Package>();
        fetchRemotePackages(remotePkgs, remoteSources);

        Package suggestedDoc = null;

        for (Package p : remotePkgs) {
            int rev = p.getRevision();
            int api = 0;
            boolean isPreview = false;
            if (p instanceof  IPackageVersion) {
                AndroidVersion vers = ((IPackageVersion) p).getVersion();
                api = vers.getApiLevel();
                isPreview = vers.isPreview();
            }

            float score = api * 10 + (isPreview ? 1 : 0) + rev/100.f;

            boolean shouldAdd = false;
            if (p instanceof PlatformPackage) {
                shouldAdd = score > currentPlatformScore;
            } else if (p instanceof SamplePackage) {
                shouldAdd = score > currentSampleScore;
            } else if (p instanceof AddonPackage) {
                shouldAdd = score > currentAddonScore;
            } else if (p instanceof ExtraPackage) {
                String key = ((ExtraPackage) p).getPath();
                shouldAdd = !currentExtraScore.containsKey(key) ||
                    score > currentExtraScore.get(key).floatValue();
            } else if (p instanceof DocPackage) {
                // We don't want all the doc, only the most recent one
                if (score > currentDocScore) {
                    suggestedDoc = p;
                    currentDocScore = score;
                }
            }

            if (shouldAdd) {
                // We should suggest this package for installation.
                for (Archive a : p.getArchives()) {
                    if (a.isCompatible()) {
                        insertArchive(a,
                                archives,
                                null /*selectedArchives*/,
                                remotePkgs,
                                remoteSources,
                                localArchives,
                                true /*automated*/);
                    }
                }
            }
        }

        if (suggestedDoc != null) {
            // We should suggest this package for installation.
            for (Archive a : suggestedDoc.getArchives()) {
                if (a.isCompatible()) {
                    insertArchive(a,
                            archives,
                            null /*selectedArchives*/,
                            remotePkgs,
                            remoteSources,
                            localArchives,
                            true /*automated*/);
                }
            }
        }
    }

    /**
     * Create a array of {@link ArchiveInfo} based on all local (already installed)
     * packages. The array is always non-null but may be empty.
     * <p/>
     * The local {@link ArchiveInfo} are guaranteed to have one non-null archive
     * that you can retrieve using {@link ArchiveInfo#getNewArchive()}.
     */
    protected ArchiveInfo[] createLocalArchives(Package[] localPkgs) {

        if (localPkgs != null) {
            ArrayList<ArchiveInfo> list = new ArrayList<ArchiveInfo>();
            for (Package p : localPkgs) {
                // Only accept packages that have one compatible archive.
                // Local package should have 1 and only 1 compatible archive anyway.
                for (Archive a : p.getArchives()) {
                    if (a != null && a.isCompatible()) {
                        // We create an "installed" archive info to wrap the local package.
                        // Note that dependencies are not computed since right now we don't
                        // deal with more than one level of dependencies and installed archives
                        // are deemed implicitly accepted anyway.
                        list.add(new LocalArchiveInfo(a));
                    }
                }
            }

            return list.toArray(new ArchiveInfo[list.size()]);
        }

        return new ArchiveInfo[0];
    }

    /**
     * Find suitable updates to all current local packages.
     */
    private Collection<Archive> findUpdates(ArchiveInfo[] localArchives,
            ArrayList<Package> remotePkgs,
            RepoSource[] remoteSources) {
        ArrayList<Archive> updates = new ArrayList<Archive>();

        fetchRemotePackages(remotePkgs, remoteSources);

        for (ArchiveInfo ai : localArchives) {
            Archive na = ai.getNewArchive();
            if (na == null) {
                continue;
            }
            Package localPkg = na.getParentPackage();

            for (Package remotePkg : remotePkgs) {
                if (localPkg.canBeUpdatedBy(remotePkg) == UpdateInfo.UPDATE) {
                    // Found a suitable update. Only accept the remote package
                    // if it provides at least one compatible archive.

                    for (Archive a : remotePkg.getArchives()) {
                        if (a.isCompatible()) {
                            updates.add(a);
                            break;
                        }
                    }
                }
            }
        }

        return updates;
    }

    private ArchiveInfo insertArchive(Archive archive,
            ArrayList<ArchiveInfo> outArchives,
            Collection<Archive> selectedArchives,
            ArrayList<Package> remotePkgs,
            RepoSource[] remoteSources,
            ArchiveInfo[] localArchives,
            boolean automated) {
        Package p = archive.getParentPackage();

        // Is this an update?
        Archive updatedArchive = null;
        for (ArchiveInfo ai : localArchives) {
            Archive a = ai.getNewArchive();
            if (a != null) {
                Package lp = a.getParentPackage();

                if (lp.canBeUpdatedBy(p) == UpdateInfo.UPDATE) {
                    updatedArchive = a;
                }
            }
        }

        // Find dependencies
        ArchiveInfo[] deps = findDependency(p,
                outArchives,
                selectedArchives,
                remotePkgs,
                remoteSources,
                localArchives);

        // Make sure it's not a dup
        ArchiveInfo ai = null;

        for (ArchiveInfo ai2 : outArchives) {
            Archive a2 = ai2.getNewArchive();
            if (a2 != null && a2.getParentPackage().sameItemAs(archive.getParentPackage())) {
                ai = ai2;
                break;
            }
        }

        if (ai == null) {
            ai = new ArchiveInfo(
                archive,        //newArchive
                updatedArchive, //replaced
                deps            //dependsOn
                );
            outArchives.add(ai);
        }

        if (deps != null) {
            for (ArchiveInfo d : deps) {
                d.addDependencyFor(ai);
            }
        }

        return ai;
    }

    /**
     * Resolves dependencies for a given package.
     *
     * Returns null if no dependencies were found.
     * Otherwise return an array of {@link ArchiveInfo}, which is guaranteed to have
     * at least size 1 and contain no null elements.
     */
    private ArchiveInfo[] findDependency(Package pkg,
            ArrayList<ArchiveInfo> outArchives,
            Collection<Archive> selectedArchives,
            ArrayList<Package> remotePkgs,
            RepoSource[] remoteSources,
            ArchiveInfo[] localArchives) {

        // Current dependencies can be:
        // - addon: *always* depends on platform of same API level
        // - platform: *might* depends on tools of rev >= min-tools-rev
        // - extra: *might* depends on platform with api >= min-api-level

        ArrayList<ArchiveInfo> list = new ArrayList<ArchiveInfo>();

        if (pkg instanceof IPlatformDependency) {
            ArchiveInfo ai = findPlatformDependency(
                    (IPlatformDependency) pkg,
                    outArchives,
                    selectedArchives,
                    remotePkgs,
                    remoteSources,
                    localArchives);

            if (ai != null) {
                list.add(ai);
            }
        }

        if (pkg instanceof IMinToolsDependency) {

            ArchiveInfo ai = findToolsDependency(
                    (IMinToolsDependency) pkg,
                    outArchives,
                    selectedArchives,
                    remotePkgs,
                    remoteSources,
                    localArchives);

            if (ai != null) {
                list.add(ai);
            }
        }

        if (pkg instanceof IMinApiLevelDependency) {

            ArchiveInfo ai = findMinApiLevelDependency(
                    (IMinApiLevelDependency) pkg,
                    outArchives,
                    selectedArchives,
                    remotePkgs,
                    remoteSources,
                    localArchives);

            if (ai != null) {
                list.add(ai);
            }
        }

        if (list.size() > 0) {
            return list.toArray(new ArchiveInfo[list.size()]);
        }

        return null;
    }

    /**
     * Resolves dependencies on tools.
     *
     * A platform or an extra package can both have a min-tools-rev, in which case it
     * depends on having a tools package of the requested revision.
     * Finds the tools dependency. If found, add it to the list of things to install.
     * Returns the archive info dependency, if any.
     */
    protected ArchiveInfo findToolsDependency(
            IMinToolsDependency pkg,
            ArrayList<ArchiveInfo> outArchives,
            Collection<Archive> selectedArchives,
            ArrayList<Package> remotePkgs,
            RepoSource[] remoteSources,
            ArchiveInfo[] localArchives) {
        // This is the requirement to match.
        int rev = pkg.getMinToolsRevision();

        if (rev == MinToolsPackage.MIN_TOOLS_REV_NOT_SPECIFIED) {
            // Well actually there's no requirement.
            return null;
        }

        // First look in locally installed packages.
        for (ArchiveInfo ai : localArchives) {
            Archive a = ai.getNewArchive();
            if (a != null) {
                Package p = a.getParentPackage();
                if (p instanceof ToolPackage) {
                    if (((ToolPackage) p).getRevision() >= rev) {
                        // We found one already installed.
                        return null;
                    }
                }
            }
        }

        // Look in archives already scheduled for install
        for (ArchiveInfo ai : outArchives) {
            Archive a = ai.getNewArchive();
            if (a != null) {
                Package p = a.getParentPackage();
                if (p instanceof ToolPackage) {
                    if (((ToolPackage) p).getRevision() >= rev) {
                        // The dependency is already scheduled for install, nothing else to do.
                        return ai;
                    }
                }
            }
        }

        // Otherwise look in the selected archives.
        if (selectedArchives != null) {
            for (Archive a : selectedArchives) {
                Package p = a.getParentPackage();
                if (p instanceof ToolPackage) {
                    if (((ToolPackage) p).getRevision() >= rev) {
                        // It's not already in the list of things to install, so add it now
                        return insertArchive(a,
                                outArchives,
                                selectedArchives,
                                remotePkgs,
                                remoteSources,
                                localArchives,
                                true /*automated*/);
                    }
                }
            }
        }

        // Finally nothing matched, so let's look at all available remote packages
        fetchRemotePackages(remotePkgs, remoteSources);
        for (Package p : remotePkgs) {
            if (p instanceof ToolPackage) {
                if (((ToolPackage) p).getRevision() >= rev) {
                    // It's not already in the list of things to install, so add the
                    // first compatible archive we can find.
                    for (Archive a : p.getArchives()) {
                        if (a.isCompatible()) {
                            return insertArchive(a,
                                    outArchives,
                                    selectedArchives,
                                    remotePkgs,
                                    remoteSources,
                                    localArchives,
                                    true /*automated*/);
                        }
                    }
                }
            }
        }

        // We end up here if nothing matches. We don't have a good platform to match.
        // We need to indicate this extra depends on a missing platform archive
        // so that it can be impossible to install later on.
        return new MissingToolArchiveInfo(rev);
    }

    /**
     * Resolves dependencies on platform for an addon.
     *
     * An addon depends on having a platform with the same API level.
     *
     * Finds the platform dependency. If found, add it to the list of things to install.
     * Returns the archive info dependency, if any.
     */
    protected ArchiveInfo findPlatformDependency(
            IPlatformDependency pkg,
            ArrayList<ArchiveInfo> outArchives,
            Collection<Archive> selectedArchives,
            ArrayList<Package> remotePkgs,
            RepoSource[] remoteSources,
            ArchiveInfo[] localArchives) {
        // This is the requirement to match.
        AndroidVersion v = pkg.getVersion();

        // Find a platform that would satisfy the requirement.

        // First look in locally installed packages.
        for (ArchiveInfo ai : localArchives) {
            Archive a = ai.getNewArchive();
            if (a != null) {
                Package p = a.getParentPackage();
                if (p instanceof PlatformPackage) {
                    if (v.equals(((PlatformPackage) p).getVersion())) {
                        // We found one already installed.
                        return null;
                    }
                }
            }
        }

        // Look in archives already scheduled for install
        for (ArchiveInfo ai : outArchives) {
            Archive a = ai.getNewArchive();
            if (a != null) {
                Package p = a.getParentPackage();
                if (p instanceof PlatformPackage) {
                    if (v.equals(((PlatformPackage) p).getVersion())) {
                        // The dependency is already scheduled for install, nothing else to do.
                        return ai;
                    }
                }
            }
        }

        // Otherwise look in the selected archives.
        if (selectedArchives != null) {
            for (Archive a : selectedArchives) {
                Package p = a.getParentPackage();
                if (p instanceof PlatformPackage) {
                    if (v.equals(((PlatformPackage) p).getVersion())) {
                        // It's not already in the list of things to install, so add it now
                        return insertArchive(a,
                                outArchives,
                                selectedArchives,
                                remotePkgs,
                                remoteSources,
                                localArchives,
                                true /*automated*/);
                    }
                }
            }
        }

        // Finally nothing matched, so let's look at all available remote packages
        fetchRemotePackages(remotePkgs, remoteSources);
        for (Package p : remotePkgs) {
            if (p instanceof PlatformPackage) {
                if (v.equals(((PlatformPackage) p).getVersion())) {
                    // It's not already in the list of things to install, so add the
                    // first compatible archive we can find.
                    for (Archive a : p.getArchives()) {
                        if (a.isCompatible()) {
                            return insertArchive(a,
                                    outArchives,
                                    selectedArchives,
                                    remotePkgs,
                                    remoteSources,
                                    localArchives,
                                    true /*automated*/);
                        }
                    }
                }
            }
        }

        // We end up here if nothing matches. We don't have a good platform to match.
        // We need to indicate this addon depends on a missing platform archive
        // so that it can be impossible to install later on.
        return new MissingPlatformArchiveInfo(pkg.getVersion());
    }

    /**
     * Resolves platform dependencies for extras.
     * An extra depends on having a platform with a minimun API level.
     *
     * We try to return the highest API level available above the specified minimum.
     * Note that installed packages have priority so if one installed platform satisfies
     * the dependency, we'll use it even if there's a higher API platform available but
     * not installed yet.
     *
     * Finds the platform dependency. If found, add it to the list of things to install.
     * Returns the archive info dependency, if any.
     */
    protected ArchiveInfo findMinApiLevelDependency(
            IMinApiLevelDependency pkg,
            ArrayList<ArchiveInfo> outArchives,
            Collection<Archive> selectedArchives,
            ArrayList<Package> remotePkgs,
            RepoSource[] remoteSources,
            ArchiveInfo[] localArchives) {

        int api = pkg.getMinApiLevel();

        if (api == ExtraPackage.MIN_API_LEVEL_NOT_SPECIFIED) {
            return null;
        }

        // Find a platform that would satisfy the requirement.

        // First look in locally installed packages.
        for (ArchiveInfo ai : localArchives) {
            Archive a = ai.getNewArchive();
            if (a != null) {
                Package p = a.getParentPackage();
                if (p instanceof PlatformPackage) {
                    if (((PlatformPackage) p).getVersion().isGreaterOrEqualThan(api)) {
                        // We found one already installed.
                        return null;
                    }
                }
            }
        }

        // Look in archives already scheduled for install
        int foundApi = 0;
        ArchiveInfo foundAi = null;

        for (ArchiveInfo ai : outArchives) {
            Archive a = ai.getNewArchive();
            if (a != null) {
                Package p = a.getParentPackage();
                if (p instanceof PlatformPackage) {
                    if (((PlatformPackage) p).getVersion().isGreaterOrEqualThan(api)) {
                        if (api > foundApi) {
                            foundApi = api;
                            foundAi = ai;
                        }
                    }
                }
            }
        }

        if (foundAi != null) {
            // The dependency is already scheduled for install, nothing else to do.
            return foundAi;
        }

        // Otherwise look in the selected archives *or* available remote packages
        // and takes the best out of the two sets.
        foundApi = 0;
        Archive foundArchive = null;
        if (selectedArchives != null) {
            for (Archive a : selectedArchives) {
                Package p = a.getParentPackage();
                if (p instanceof PlatformPackage) {
                    if (((PlatformPackage) p).getVersion().isGreaterOrEqualThan(api)) {
                        if (api > foundApi) {
                            foundApi = api;
                            foundArchive = a;
                        }
                    }
                }
            }
        }

        // Finally nothing matched, so let's look at all available remote packages
        fetchRemotePackages(remotePkgs, remoteSources);
        for (Package p : remotePkgs) {
            if (p instanceof PlatformPackage) {
                if (((PlatformPackage) p).getVersion().isGreaterOrEqualThan(api)) {
                    if (api > foundApi) {
                        // It's not already in the list of things to install, so add the
                        // first compatible archive we can find.
                        for (Archive a : p.getArchives()) {
                            if (a.isCompatible()) {
                                foundApi = api;
                                foundArchive = a;
                            }
                        }
                    }
                }
            }
        }

        if (foundArchive != null) {
            // It's not already in the list of things to install, so add it now
            return insertArchive(foundArchive,
                    outArchives,
                    selectedArchives,
                    remotePkgs,
                    remoteSources,
                    localArchives,
                    true /*automated*/);
        }

        // We end up here if nothing matches. We don't have a good platform to match.
        // We need to indicate this extra depends on a missing platform archive
        // so that it can be impossible to install later on.
        return new MissingPlatformArchiveInfo(new AndroidVersion(api, null /*codename*/));
    }

    /** Fetch all remote packages only if really needed. */
    protected void fetchRemotePackages(ArrayList<Package> remotePkgs, RepoSource[] remoteSources) {
        if (remotePkgs.size() > 0) {
            return;
        }

        for (RepoSource remoteSrc : remoteSources) {
            Package[] pkgs = remoteSrc.getPackages();
            if (pkgs != null) {
                nextPackage: for (Package pkg : pkgs) {
                    for (Archive a : pkg.getArchives()) {
                        // Only add a package if it contains at least one compatible archive
                        if (a.isCompatible()) {
                            remotePkgs.add(pkg);
                            continue nextPackage;
                        }
                    }
                }
            }
        }
    }


    /**
     * A {@link LocalArchiveInfo} is an {@link ArchiveInfo} that wraps an already installed
     * "local" package/archive.
     * <p/>
     * In this case, the "new Archive" is still expected to be non null and the
     * "replaced Archive" isnull. Installed archives are always accepted and never
     * rejected.
     * <p/>
     * Dependencies are not set.
     */
    private static class LocalArchiveInfo extends ArchiveInfo {

        public LocalArchiveInfo(Archive localArchive) {
            super(localArchive, null /*replaced*/, null /*dependsOn*/);
        }

        /** Installed archives are always accepted. */
        @Override
        public boolean isAccepted() {
            return true;
        }

        /** Installed archives are never rejected. */
        @Override
        public boolean isRejected() {
            return false;
        }
    }

    /**
     * A {@link MissingPlatformArchiveInfo} is an {@link ArchiveInfo} that represents a
     * package/archive that we <em>really</em> need as a dependency but that we don't have.
     * <p/>
     * This is currently used for addons and extras in case we can't find a matching base platform.
     * <p/>
     * This kind of archive has specific properties: the new archive to install is null,
     * there are no dependencies and no archive is being replaced. The info can never be
     * accepted and is always rejected.
     */
    private static class MissingPlatformArchiveInfo extends ArchiveInfo {

        private final AndroidVersion mVersion;

        /**
         * Constructs a {@link MissingPlatformArchiveInfo} that will indicate the
         * given platform version is missing.
         */
        public MissingPlatformArchiveInfo(AndroidVersion version) {
            super(null /*newArchive*/, null /*replaced*/, null /*dependsOn*/);
            mVersion = version;
        }

        /** Missing archives are never accepted. */
        @Override
        public boolean isAccepted() {
            return false;
        }

        /** Missing archives are always rejected. */
        @Override
        public boolean isRejected() {
            return true;
        }

        @Override
        public String getShortDescription() {
            return String.format("Missing SDK Platform Android%1$s, API %2$d",
                    mVersion.isPreview() ? " Preview" : "",
                    mVersion.getApiLevel());
        }
    }

    /**
     * A {@link MissingToolArchiveInfo} is an {@link ArchiveInfo} that represents a
     * package/archive that we <em>really</em> need as a dependency but that we don't have.
     * <p/>
     * This is currently used for extras in case we can't find a matching tool revision.
     * <p/>
     * This kind of archive has specific properties: the new archive to install is null,
     * there are no dependencies and no archive is being replaced. The info can never be
     * accepted and is always rejected.
     */
    private static class MissingToolArchiveInfo extends ArchiveInfo {

        private final int mRevision;

        /**
         * Constructs a {@link MissingPlatformArchiveInfo} that will indicate the
         * given platform version is missing.
         */
        public MissingToolArchiveInfo(int revision) {
            super(null /*newArchive*/, null /*replaced*/, null /*dependsOn*/);
            mRevision = revision;
        }

        /** Missing archives are never accepted. */
        @Override
        public boolean isAccepted() {
            return false;
        }

        /** Missing archives are always rejected. */
        @Override
        public boolean isRejected() {
            return true;
        }

        @Override
        public String getShortDescription() {
            return String.format("Missing Android SDK Tools, revision %1$d", mRevision);
        }
    }
}
