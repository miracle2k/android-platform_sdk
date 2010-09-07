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

package com.android.ide.eclipse.pdt;

import com.android.ide.eclipse.ddms.DdmsPlugin;
import com.android.ide.eclipse.pdt.internal.preferences.PrefPage;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

public class PdtPlugin extends AbstractUIPlugin {

    public final static String PLUGIN_ID = "com.android.ide.eclipse.pdt"; //$NON-NLS-1$
    private static PdtPlugin sPlugin;

    public PdtPlugin() {
        sPlugin = this;
    }

    /**
     * Returns the shared instance
     *
     * @return the shared instance
     */
    public static synchronized PdtPlugin getDefault() {
        return sPlugin;
    }

    @Override
    public void start(BundleContext context) throws Exception {
        super.start(context);

        // set the listener for the preference change
        getPreferenceStore().addPropertyChangeListener(new IPropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent event) {
                // if the SDK changed, we have to do some extra work
                if (PrefPage.PREFS_DEVTREE_DIR.equals(event.getProperty())) {
                    // restart adb, in case it's a different version
                    DdmsPlugin.setAdb(getAdbLocation(), true /* startAdb */);
                }
            }
        });
    }

    public static String getAdbLocation() {
        // this always return a store, even a temp one if an error occurred.
        IPreferenceStore store = sPlugin.getPreferenceStore();

        // returns an empty, non-null, string if the preference is not found.
        String devTree = store.getString(PrefPage.PREFS_DEVTREE_DIR);

        if (devTree.length() == 0) {
            devTree = System.getenv("ANDROID_BUILD_TOP"); //$NON-NLS-1$
        }

        if (devTree != null && devTree.length() > 0) {
            return devTree + "/out/host/" + currentPlatform() + "/bin/adb"; //$NON-NLS-1$ //$NON-NLS-2$
        }

        return null;
    }

    /**
     * Returns the current platform name as used by the Android build system
     *
     */
    private static String currentPlatform() {
        String os = System.getProperty("os.name");          //$NON-NLS-1$
        if (os.startsWith("Mac OS")) {                      //$NON-NLS-1$
            return "darwin-x86";                            //$NON-NLS-1$
        } else if (os.startsWith("Windows")) {              //$NON-NLS-1$
            return "windows";                               //$NON-NLS-1$
        } else if (os.startsWith("Linux")) {                //$NON-NLS-1$
            return "linux-x86";                             //$NON-NLS-1$
        }

        return ""; //$NON-NLS-1$
    }

}
