/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.eclipse.org/org/documents/epl-v10.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.ide.eclipse.adt.internal.preferences;

import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.prefs.AndroidLocation.AndroidLocationException;
import com.android.sdklib.internal.build.DebugKeyProvider;
import com.android.sdklib.internal.build.DebugKeyProvider.KeytoolException;

import org.eclipse.core.runtime.Preferences.PropertyChangeEvent;
import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.jface.preference.IPreferenceStore;

import java.io.File;

@SuppressWarnings("deprecation")
public final class AdtPrefs extends AbstractPreferenceInitializer {
    public final static String PREFS_SDK_DIR = AdtPlugin.PLUGIN_ID + ".sdk"; //$NON-NLS-1$

    public final static String PREFS_BUILD_RES_AUTO_REFRESH = AdtPlugin.PLUGIN_ID + ".resAutoRefresh"; //$NON-NLS-1$

    public final static String PREFS_BUILD_FORCE_ERROR_ON_NATIVELIB_IN_JAR = AdtPlugin.PLUGIN_ID + ".forceErrorNativeLibInJar"; //$NON-NLS-1$

    public final static String PREFS_BUILD_VERBOSITY = AdtPlugin.PLUGIN_ID + ".buildVerbosity"; //$NON-NLS-1$

    public final static String PREFS_DEFAULT_DEBUG_KEYSTORE = AdtPlugin.PLUGIN_ID + ".defaultDebugKeyStore"; //$NON-NLS-1$

    public final static String PREFS_CUSTOM_DEBUG_KEYSTORE = AdtPlugin.PLUGIN_ID + ".customDebugKeyStore"; //$NON-NLS-1$

    public final static String PREFS_HOME_PACKAGE = AdtPlugin.PLUGIN_ID + ".homePackage"; //$NON-NLS-1$

    public final static String PREFS_EMU_OPTIONS = AdtPlugin.PLUGIN_ID + ".emuOptions"; //$NON-NLS-1$

    /** singleton instance */
    private final static AdtPrefs sThis = new AdtPrefs();

    /** default store, provided by eclipse */
    private IPreferenceStore mStore;

    /** cached location for the sdk folder */
    private String mOsSdkLocation;

    /** Verbosity of the build */
    private BuildVerbosity mBuildVerbosity = BuildVerbosity.NORMAL;

    private boolean mBuildForceResResfresh = false;
    private boolean mBuildForceErrorOnNativeLibInJar = true;

    public static enum BuildVerbosity {
        /** Build verbosity "Always". Those messages are always displayed, even in silent mode */
        ALWAYS(0),
        /** Build verbosity level "Normal" */
        NORMAL(1),
        /** Build verbosity level "Verbose". Those messages are only displayed in verbose mode */
        VERBOSE(2);

        private int mLevel;

        BuildVerbosity(int level) {
            mLevel = level;
        }

        public int getLevel() {
            return mLevel;
        }

        /**
         * Finds and returns a {@link BuildVerbosity} whose {@link #name()} matches a given name.
         * <p/>This is different from {@link Enum#valueOf(Class, String)} in that it returns null
         * if no matches are found.
         *
         * @param name the name to look up.
         * @return returns the matching enum or null of no match where found.
         */
        public static BuildVerbosity find(String name) {
            for (BuildVerbosity v : values()) {
                if (v.name().equals(name)) {
                    return v;
                }
            }

            return null;
        }
    }

    public static void init(IPreferenceStore preferenceStore) {
        sThis.mStore = preferenceStore;
    }

    public static AdtPrefs getPrefs() {
        return sThis;
    }

    public synchronized void loadValues(PropertyChangeEvent event) {
        // get the name of the property that changed, if any
        String property = event != null ? event.getProperty() : null;

        if (property == null || PREFS_SDK_DIR.equals(property)) {
            mOsSdkLocation = mStore.getString(PREFS_SDK_DIR);

            // make sure it ends with a separator. Normally this is done when the preference
            // is set. But to make sure older version still work, we fix it here as well.
            if (mOsSdkLocation.length() > 0 && mOsSdkLocation.endsWith(File.separator) == false) {
                mOsSdkLocation = mOsSdkLocation + File.separator;
            }
        }

        if (property == null || PREFS_BUILD_VERBOSITY.equals(property)) {
            mBuildVerbosity = BuildVerbosity.find(mStore.getString(PREFS_BUILD_VERBOSITY));
            if (mBuildVerbosity == null) {
                mBuildVerbosity = BuildVerbosity.NORMAL;
            }
        }

        if (property == null || PREFS_BUILD_RES_AUTO_REFRESH.equals(property)) {
            mBuildForceResResfresh = mStore.getBoolean(PREFS_BUILD_RES_AUTO_REFRESH);
        }

        if (property == null || PREFS_BUILD_FORCE_ERROR_ON_NATIVELIB_IN_JAR.equals(property)) {
            mBuildForceErrorOnNativeLibInJar = mStore.getBoolean(PREFS_BUILD_RES_AUTO_REFRESH);
        }
    }

    /**
     * Returns the SDK folder.
     * Guaranteed to be terminated by a platform-specific path separator.
     */
    public synchronized String getOsSdkFolder() {
        return mOsSdkLocation;
    }

    public synchronized BuildVerbosity getBuildVerbosity() {
        return mBuildVerbosity;
    }

    public  boolean getBuildForceResResfresh() {
        return mBuildForceResResfresh;
    }

    public boolean getBuildForceErrorOnNativeLibInJar() {
        return mBuildForceErrorOnNativeLibInJar;
    }

    @Override
    public void initializeDefaultPreferences() {
        IPreferenceStore store = AdtPlugin.getDefault().getPreferenceStore();

        store.setDefault(PREFS_BUILD_RES_AUTO_REFRESH, true);
        store.setDefault(PREFS_BUILD_FORCE_ERROR_ON_NATIVELIB_IN_JAR, true);

        store.setDefault(PREFS_BUILD_VERBOSITY, BuildVerbosity.ALWAYS.name());

        store.setDefault(PREFS_HOME_PACKAGE, "android.process.acore"); //$NON-NLS-1$

        try {
            store.setDefault(PREFS_DEFAULT_DEBUG_KEYSTORE,
                    DebugKeyProvider.getDefaultKeyStoreOsPath());
        } catch (KeytoolException e) {
            AdtPlugin.log(e, "Get default debug keystore path failed"); //$NON-NLS-1$
        } catch (AndroidLocationException e) {
            AdtPlugin.log(e, "Get default debug keystore path failed"); //$NON-NLS-1$
        }
    }
}
