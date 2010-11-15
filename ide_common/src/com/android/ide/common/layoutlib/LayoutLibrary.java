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

package com.android.ide.common.layoutlib;

import com.android.ide.common.log.ILogger;
import com.android.ide.common.sdk.LoadStatus;
import com.android.layoutlib.api.ILayoutBridge;
import com.android.layoutlib.api.LayoutBridge;

import java.io.File;
import java.lang.reflect.Constructor;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * Class representing and allowing to load the layoutlib jar file.
 */
@SuppressWarnings("deprecation")
public class LayoutLibrary {

    public final static String CLASS_BRIDGE = "com.android.layoutlib.bridge.Bridge"; //$NON-NLS-1$

    /** Link to the layout bridge */
    private final LayoutBridge mBridge;
    /** Status of the layoutlib.jar loading */
    private final LoadStatus mStatus;
    /** classloader used to load the jar file */
    private final ClassLoader mClassLoader;

    /**
     * Returns the loaded {@link LayoutBridge} object or null if the loading failed.
     */
    public LayoutBridge getBridge() {
        return mBridge;
    }

    /**
     * Returns the {@link LoadStatus} of the loading of the layoutlib jar file.
     */
    public LoadStatus getStatus() {
        return mStatus;
    }

    /**
     * Returns the classloader used to load the classes in the layoutlib jar file.
     */
    public ClassLoader getClassLoader() {
        return mClassLoader;
    }

    /**
     * Loads the layoutlib.jar file located at the given path and returns a {@link LayoutLibrary}
     * object representing the result.
     * <p/>
     * If loading failed {@link #getStatus()} will reflect this, and {@link #getBridge()} will
     * return null.
     *
     * @param layoutLibJarOsPath the path of the jar file
     * @param log an optional log file.
     * @return a {@link LayoutLibrary} object always.
     */
    public static LayoutLibrary load(String layoutLibJarOsPath, ILogger log) {

        LoadStatus status = LoadStatus.LOADING;
        LayoutBridge bridge = null;
        ClassLoader classLoader = null;

        try {
            // get the URL for the file.
            File f = new File(layoutLibJarOsPath);
            if (f.isFile() == false) {
                if (log != null) {
                    log.error(null, "layoutlib.jar is missing!"); //$NON-NLS-1$
                }
            } else {
                URI uri = f.toURI();
                URL url = uri.toURL();

                // create a class loader. Because this jar reference interfaces
                // that are in the editors plugin, it's important to provide
                // a parent class loader.
                classLoader = new URLClassLoader(
                        new URL[] { url },
                        LayoutLibrary.class.getClassLoader());

                // load the class
                Class<?> clazz = classLoader.loadClass(CLASS_BRIDGE);
                if (clazz != null) {
                    // instantiate an object of the class.
                    Constructor<?> constructor = clazz.getConstructor();
                    if (constructor != null) {
                        Object bridgeObject = constructor.newInstance();
                        if (bridgeObject instanceof LayoutBridge) {
                            bridge = (LayoutBridge)bridgeObject;
                        } else if (bridgeObject instanceof ILayoutBridge) {
                            bridge = new LayoutBridgeWrapper((ILayoutBridge) bridgeObject,
                                    classLoader);
                        }
                    }
                }

                if (bridge == null) {
                    status = LoadStatus.FAILED;
                    if (log != null) {
                        log.error(null, "Failed to load " + CLASS_BRIDGE); //$NON-NLS-1$
                    }
                } else {
                    // mark the lib as loaded.
                    status = LoadStatus.LOADED;
                }
            }
        } catch (Throwable t) {
            status = LoadStatus.FAILED;
            // log the error.
            if (log != null) {
                log.error(t, "Failed to load the LayoutLib");
            }
        }

        return new LayoutLibrary(bridge, classLoader, status);
    }

    private LayoutLibrary(LayoutBridge bridge, ClassLoader classLoader, LoadStatus status) {
        mBridge = bridge;
        mClassLoader = classLoader;
        mStatus = status;
    }
}
