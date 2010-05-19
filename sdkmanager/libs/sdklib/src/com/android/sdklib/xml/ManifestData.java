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
    String mApiLevelRequirement = null;
    /** List of all instrumentations declared by the manifest */
    final ArrayList<Instrumentation> mInstrumentations =
        new ArrayList<Instrumentation>();
    /** List of all libraries in use declared by the manifest */
    final ArrayList<String> mLibraries = new ArrayList<String>();

    SupportsScreens mSupportsScreens;
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
     */
    public final static class SupportsScreens {
        Boolean mResizeable;
        Boolean mAnyDensity;
        Boolean mSmallScreens;
        Boolean mLargeScreens;
        Boolean mNormalScreens;

        /**
         * returns the value of the <code>resizeable</code> attribute or null if not present.
         */
        public Boolean getResizeable() {
            return mResizeable;
        }

        /**
         * returns the value of the <code>anyDensity</code> attribute or null if not present.
         */
        public Boolean getAnyDensity() {
            return mAnyDensity;
        }

        /**
         * returns the value of the <code>smallScreens</code> attribute or null if not present.
         */
        public Boolean getSmallScreens() {
            return mSmallScreens;
        }

        /**
         * returns the value of the <code>normalScreens</code> attribute or null if not present.
         */
        public Boolean getNormalScreens() {
            return mNormalScreens;
        }

        /**
         * returns the value of the <code>largeScreens</code> attribute or null if not present.
         */
        public Boolean getLargeScreens() {
            return mLargeScreens;
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
    public String getApiLevelRequirement() {
        return mApiLevelRequirement;
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
     * @return An array of library names, or empty if no libraries were found.
     */
    public String[] getUsesLibraries() {
        return mLibraries.toArray(new String[mLibraries.size()]);
    }

    /**
     * Returns the {@link SupportsScreens} object representing the <code>supports-screens</code>
     * node, or null if the node doesn't exist at all.
     */
    public SupportsScreens getSupportsScreens() {
        return mSupportsScreens;
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
