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

package com.android.sdklib.xml;

import com.android.sdklib.resources.Keyboard;
import com.android.sdklib.resources.Navigation;
import com.android.sdklib.resources.TouchScreen;

import java.util.ArrayList;
import java.util.Set;
import java.util.TreeSet;

/**
 * Class containing the manifest info obtained during the parsing.
 */
public final class ManifestData {
    /** Application package */
    String mPackage;
    /** Application version Code, null if the attribute is not present. */
    Integer mVersionCode = null;
    /** List of all activities */
    final ArrayList<Activity> mActivities = new ArrayList<Activity>();
    /** Launcher activity */
    Activity mLauncherActivity = null;
    /** list of process names declared by the manifest */
    Set<String> mProcesses = null;
    /** debuggable attribute value. If null, the attribute is not present. */
    Boolean mDebuggable = null;
    /** API level requirement. if null the attribute was not present. */
    private String mMinSdkVersionString = null;
    /** API level requirement. Default is 1 even if missing. If value is a codename, then it'll be
     * 0 instead. */
    private int mMinSdkVersion = 1;
    private int mTargetSdkVersion = 0;
    /** List of all instrumentations declared by the manifest */
    final ArrayList<Instrumentation> mInstrumentations =
        new ArrayList<Instrumentation>();
    /** List of all libraries in use declared by the manifest */
    final ArrayList<UsesLibrary> mLibraries = new ArrayList<UsesLibrary>();
    /** List of all feature in use declared by the manifest */
    final ArrayList<UsesFeature> mFeatures = new ArrayList<UsesFeature>();

    SupportsScreens mSupportsScreensFromManifest;
    SupportsScreens mSupportsScreensValues;
    UsesConfiguration mUsesConfiguration;

    /**
     * Instrumentation info obtained from manifest
     */
    public final static class Instrumentation {
        private final String mName;
        private final String mTargetPackage;

        Instrumentation(String name, String targetPackage) {
            mName = name;
            mTargetPackage = targetPackage;
        }

        /**
         * Returns the fully qualified instrumentation class name
         */
        public String getName() {
            return mName;
        }

        /**
         * Returns the Android app package that is the target of this instrumentation
         */
        public String getTargetPackage() {
            return mTargetPackage;
        }
    }

    /**
     * Activity info obtained from the manifest.
     */
    public final static class Activity {
        private final String mName;
        private final boolean mIsExported;
        private boolean mHasAction = false;
        private boolean mHasMainAction = false;
        private boolean mHasLauncherCategory = false;

        public Activity(String name, boolean exported) {
            mName = name;
            mIsExported = exported;
        }

        public String getName() {
            return mName;
        }

        public boolean isExported() {
            return mIsExported;
        }

        public boolean hasAction() {
            return mHasAction;
        }

        public boolean isHomeActivity() {
            return mHasMainAction && mHasLauncherCategory;
        }

        void setHasAction(boolean hasAction) {
            mHasAction = hasAction;
        }

        /** If the activity doesn't yet have a filter set for the launcher, this resets both
         * flags. This is to handle multiple intent-filters where one could have the valid
         * action, and another one of the valid category.
         */
        void resetIntentFilter() {
            if (isHomeActivity() == false) {
                mHasMainAction = mHasLauncherCategory = false;
            }
        }

        void setHasMainAction(boolean hasMainAction) {
            mHasMainAction = hasMainAction;
        }

        void setHasLauncherCategory(boolean hasLauncherCategory) {
            mHasLauncherCategory = hasLauncherCategory;
        }
    }

    /**
     * Class representing the <code>supports-screens</code> node in the manifest.
     * By default, all the getters will return null if there was no value defined in the manifest.
     *
     * To get an instance with all the actual values, use {@link #resolveSupportsScreensValues(int)}
     */
    public final static class SupportsScreens implements Comparable<SupportsScreens> {
        private Boolean mResizeable;
        private Boolean mAnyDensity;
        private Boolean mSmallScreens;
        private Boolean mNormalScreens;
        private Boolean mLargeScreens;

        /**
         * Returns an instance of {@link SupportsScreens} initialized with the default values
         * based on the given targetSdkVersion.
         * @param targetSdkVersion
         */
        public static SupportsScreens getDefaultValues(int targetSdkVersion) {
            SupportsScreens result = new SupportsScreens();

            result.mNormalScreens = Boolean.TRUE;
            result.mResizeable = result.mAnyDensity = result.mSmallScreens = result.mLargeScreens =
                targetSdkVersion <= 3 ? Boolean.FALSE : Boolean.TRUE;

            return result;
        }

        /**
         * Returns a version of the receiver for which all values have been set, even if they
         * were not present in the manifest.
         * @param targetSdkVersion the target api level of the app, since this has an effect
         * on default values.
         */
        public SupportsScreens resolveSupportsScreensValues(int targetSdkVersion) {
            SupportsScreens result = getDefaultValues(targetSdkVersion);

            // Override the default with the existing values:
            if (mResizeable != null) result.mResizeable = mResizeable;
            if (mAnyDensity != null) result.mAnyDensity = mAnyDensity;
            if (mSmallScreens != null) result.mSmallScreens = mSmallScreens;
            if (mNormalScreens != null) result.mNormalScreens = mNormalScreens;
            if (mLargeScreens != null) result.mLargeScreens = mLargeScreens;

            return result;
        }

        /**
         * returns the value of the <code>resizeable</code> attribute or null if not present.
         */
        public Boolean getResizeable() {
            return mResizeable;
        }

        void setResizeable(Boolean resizeable) {
            mResizeable = getConstantBoolean(resizeable);
        }

        /**
         * returns the value of the <code>anyDensity</code> attribute or null if not present.
         */
        public Boolean getAnyDensity() {
            return mAnyDensity;
        }

        void setAnyDensity(Boolean anyDensity) {
            mAnyDensity = getConstantBoolean(anyDensity);
        }

        /**
         * returns the value of the <code>smallScreens</code> attribute or null if not present.
         */
        public Boolean getSmallScreens() {
            return mSmallScreens;
        }

        void setSmallScreens(Boolean smallScreens) {
            mSmallScreens = getConstantBoolean(smallScreens);
        }

        /**
         * returns the value of the <code>normalScreens</code> attribute or null if not present.
         */
        public Boolean getNormalScreens() {
            return mNormalScreens;
        }

        void setNormalScreens(Boolean normalScreens) {
            mNormalScreens = getConstantBoolean(normalScreens);
        }

        /**
         * returns the value of the <code>largeScreens</code> attribute or null if not present.
         */
        public Boolean getLargeScreens() {
            return mLargeScreens;
        }

        void setLargeScreens(Boolean largeScreens) {
            mLargeScreens = getConstantBoolean(largeScreens);
        }

        /**
         * Returns either {@link Boolean#TRUE} or {@link Boolean#FALSE} based on the value of
         * the given Boolean object.
         */
        private Boolean getConstantBoolean(Boolean v) {
            if (v != null) {
                if (v.equals(Boolean.TRUE)) {
                    return Boolean.TRUE;
                } else {
                    return Boolean.FALSE;
                }
            }

            return null;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof SupportsScreens) {
                SupportsScreens support = (SupportsScreens)obj;
                // since all the fields are guaranteed to be either Boolean.TRUE or Boolean.FALSE
                // (or null), we can simply check they are identical and not bother with
                // calling equals (which would require to check != null.
                // see #getConstanntBoolean(Boolean)
                return mResizeable == support.mResizeable && mAnyDensity == support.mAnyDensity &&
                        mSmallScreens == support.mSmallScreens &&
                        mNormalScreens == support.mNormalScreens &&
                        mLargeScreens == support.mLargeScreens;
            }

            return false;
        }

        public int compareTo(SupportsScreens o) {
            return 0;
        }

        @Override
        public String toString() {
            return String.format("AD: %1$s, RS: %2$s, SS: %3$s, NS: %4$s, LS: %5$s",
                    mAnyDensity, mResizeable, mSmallScreens, mNormalScreens, mLargeScreens);
        }
    }

    /**
     * Class representing a <code>uses-library</code> node in the manifest.
     */
    public final static class UsesLibrary {
        String mName;
        Boolean mRequired = Boolean.TRUE; // default is true even if missing

        public String getName() {
            return mName;
        }

        public Boolean getRequired() {
            return mRequired;
        }
    }

    /**
     * Class representing a <code>uses-feature</code> node in the manifest.
     */
    public final static class UsesFeature {
        String mName;
        int mGlEsVersion = 0;
        Boolean mRequired = Boolean.TRUE;  // default is true even if missing

        public String getName() {
            return mName;
        }

        /**
         * Returns the value of the glEsVersion attribute, or 0 if the attribute was not present.
         */
        public int getGlEsVersion() {
            return mGlEsVersion;
        }

        public Boolean getRequired() {
            return mRequired;
        }
    }

    /**
     * Class representing the <code>uses-configuration</code> node in the manifest.
     */
    public final static class UsesConfiguration {
        Boolean mReqFiveWayNav;
        Boolean mReqHardKeyboard;
        Keyboard mReqKeyboardType;
        TouchScreen mReqTouchScreen;
        Navigation mReqNavigation;

        /**
         * returns the value of the <code>reqFiveWayNav</code> attribute or null if not present.
         */
        public Boolean getReqFiveWayNav() {
            return mReqFiveWayNav;
        }

        /**
         * returns the value of the <code>reqNavigation</code> attribute or null if not present.
         */
        public Navigation getReqNavigation() {
            return mReqNavigation;
        }

        /**
         * returns the value of the <code>reqHardKeyboard</code> attribute or null if not present.
         */
        public Boolean getReqHardKeyboard() {
            return mReqHardKeyboard;
        }

        /**
         * returns the value of the <code>reqKeyboardType</code> attribute or null if not present.
         */
        public Keyboard getReqKeyboardType() {
            return mReqKeyboardType;
        }

        /**
         * returns the value of the <code>reqTouchScreen</code> attribute or null if not present.
         */
        public TouchScreen getReqTouchScreen() {
            return mReqTouchScreen;
        }
    }

    /**
     * Returns the package defined in the manifest, if found.
     * @return The package name or null if not found.
     */
    public String getPackage() {
        return mPackage;
    }

    /**
     * Returns the versionCode value defined in the manifest, if found, null otherwise.
     * @return the versionCode or null if not found.
     */
    public Integer getVersionCode() {
        return mVersionCode;
    }

    /**
     * Returns the list of activities found in the manifest.
     * @return An array of fully qualified class names, or empty if no activity were found.
     */
    public Activity[] getActivities() {
        return mActivities.toArray(new Activity[mActivities.size()]);
    }

    /**
     * Returns the name of one activity found in the manifest, that is configured to show
     * up in the HOME screen.
     * @return the fully qualified name of a HOME activity or null if none were found.
     */
    public Activity getLauncherActivity() {
        return mLauncherActivity;
    }

    /**
     * Returns the list of process names declared by the manifest.
     */
    public String[] getProcesses() {
        if (mProcesses != null) {
            return mProcesses.toArray(new String[mProcesses.size()]);
        }

        return new String[0];
    }

    /**
     * Returns the <code>debuggable</code> attribute value or null if it is not set.
     */
    public Boolean getDebuggable() {
        return mDebuggable;
    }

    /**
     * Returns the <code>minSdkVersion</code> attribute, or null if it's not set.
     */
    public String getMinSdkVersionString() {
        return mMinSdkVersionString;
    }

    /**
     * Sets the value of the <code>minSdkVersion</code> attribute.
     * @param minSdkVersion the string value of the attribute in the manifest.
     */
    public void setMinSdkVersionString(String minSdkVersion) {
        mMinSdkVersionString = minSdkVersion;
        if (mMinSdkVersionString != null) {
            try {
                mMinSdkVersion = Integer.parseInt(mMinSdkVersionString);
            } catch (NumberFormatException e) {
                mMinSdkVersion = 0; // 0 means it's a codename.
            }
        }
    }

    /**
     * Returns the <code>minSdkVersion</code> attribute, or 0 if it's not set or is a codename.
     * @see #getMinSdkVersionString()
     */
    public int getMinSdkVersion() {
        return mMinSdkVersion;
    }


    /**
     * Sets the value of the <code>minSdkVersion</code> attribute.
     * @param targetSdkVersion the string value of the attribute in the manifest.
     */
    public void setTargetSdkVersionString(String targetSdkVersion) {
        if (targetSdkVersion != null) {
            try {
                mTargetSdkVersion = Integer.parseInt(targetSdkVersion);
            } catch (NumberFormatException e) {
                // keep the value at 0.
            }
        }
    }

    /**
     * Returns the <code>targetSdkVersion</code> attribute, or the same value as
     * {@link #getMinSdkVersion()} if it was not set in the manifest.
     */
    public int getTargetSdkVersion() {
        if (mTargetSdkVersion == 0) {
            return getMinSdkVersion();
        }

        return mTargetSdkVersion;
    }

    /**
     * Returns the list of instrumentations found in the manifest.
     * @return An array of {@link Instrumentation}, or empty if no instrumentations were
     * found.
     */
    public Instrumentation[] getInstrumentations() {
        return mInstrumentations.toArray(new Instrumentation[mInstrumentations.size()]);
    }

    /**
     * Returns the list of libraries in use found in the manifest.
     * @return An array of {@link UsesLibrary} objects, or empty if no libraries were found.
     */
    public UsesLibrary[] getUsesLibraries() {
        return mLibraries.toArray(new UsesLibrary[mLibraries.size()]);
    }

    /**
     * Returns the list of features in use found in the manifest.
     * @return An array of {@link UsesFeature} objects, or empty if no libraries were found.
     */
    public UsesFeature[] getUsesFeatures() {
        return mFeatures.toArray(new UsesFeature[mFeatures.size()]);
    }

    public int getGlEsVersion() {
        for (UsesFeature feature : mFeatures) {
            if (feature.mGlEsVersion > 0) {
                return feature.mGlEsVersion;
            }
        }
        return -1;
    }

    /**
     * Returns the {@link SupportsScreens} object representing the <code>supports-screens</code>
     * node, or null if the node doesn't exist at all.
     * Some values in the {@link SupportsScreens} instance maybe null, indicating that they
     * were not present in the manifest. To get an instance that contains the values, as seen
     * by the Android platform when the app is running, use {@link #getSupportsScreensValues()}.
     */
    public SupportsScreens getSupportsScreensFromManifest() {
        return mSupportsScreensFromManifest;
    }

    /**
     * Returns an always non-null instance of {@link SupportsScreens} that's been initialized with
     * the default values, and the values from the manifest.
     * The default values depends on the manifest values for minSdkVersion and targetSdkVersion.
     */
    public synchronized SupportsScreens getSupportsScreensValues() {
        if (mSupportsScreensValues == null) {
            if (mSupportsScreensFromManifest == null) {
                mSupportsScreensValues = SupportsScreens.getDefaultValues(getTargetSdkVersion());
            } else {
                // get a SupportsScreen that replace the missing values with default values.
                mSupportsScreensValues = mSupportsScreensFromManifest.resolveSupportsScreensValues(
                        getTargetSdkVersion());
            }
        }

        return mSupportsScreensValues;
    }

    /**
     * Returns the {@link UsesConfiguration} object representing the <code>uses-configuration</code>
     * node, or null if the node doesn't exist at all.
     */
    public UsesConfiguration getUsesConfiguration() {
        return mUsesConfiguration;
    }

    void addProcessName(String processName) {
        if (mProcesses == null) {
            mProcesses = new TreeSet<String>();
        }

        mProcesses.add(processName);
    }

}
