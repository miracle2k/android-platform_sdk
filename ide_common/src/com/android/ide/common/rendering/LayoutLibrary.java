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

package com.android.ide.common.rendering;

import com.android.ide.common.log.ILogger;
import com.android.ide.common.rendering.api.Bridge;
import com.android.ide.common.rendering.api.Capability;
import com.android.ide.common.rendering.api.ILayoutPullParser;
import com.android.ide.common.rendering.api.LayoutLog;
import com.android.ide.common.rendering.api.Params;
import com.android.ide.common.rendering.api.RenderSession;
import com.android.ide.common.rendering.api.Result;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.ide.common.rendering.api.Params.RenderingMode;
import com.android.ide.common.rendering.api.Result.Status;
import com.android.ide.common.rendering.legacy.ILegacyCallback;
import com.android.ide.common.rendering.legacy.ILegacyPullParser;
import com.android.ide.common.sdk.LoadStatus;
import com.android.layoutlib.api.ILayoutBridge;
import com.android.layoutlib.api.ILayoutLog;
import com.android.layoutlib.api.ILayoutResult;
import com.android.layoutlib.api.IProjectCallback;
import com.android.layoutlib.api.IResourceValue;
import com.android.layoutlib.api.IXmlPullParser;
import com.android.layoutlib.api.ILayoutResult.ILayoutViewInfo;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Map;

/**
 * Class to use the Layout library.
 * <p/>
 * Use {@link #load(String, ILogger)} to load the jar file.
 * <p/>
 * Use the layout library with:
 * {@link #init(String, Map)}, {@link #supports(Capability)}, {@link #createSession(Params)},
 * {@link #dispose()}, {@link #clearCaches(Object)}.
 *
 * <p/>
 * For client wanting to access both new and old (pre API level 5) layout libraries, it is
 * important that the following interfaces be used:<br>
 * {@link ILegacyPullParser} instead of {@link ILayoutPullParser}<br>
 * {@link ILegacyCallback} instead of{@link com.android.ide.common.rendering.api.IProjectCallback}.
 * <p/>
 * These interfaces will ensure that both new and older Layout libraries can be accessed.
 */
@SuppressWarnings("deprecation")
public class LayoutLibrary {

    public final static String CLASS_BRIDGE = "com.android.layoutlib.bridge.Bridge"; //$NON-NLS-1$

    /** Link to the layout bridge */
    private final Bridge mBridge;
    /** Link to a ILayoutBridge in case loaded an older library */
    private final ILayoutBridge mLegacyBridge;
    /** Status of the layoutlib.jar loading */
    private final LoadStatus mStatus;
    /** Message associated with the {@link LoadStatus}. This is mostly used when
     * {@link #getStatus()} returns {@link LoadStatus#FAILED}.
     */
    private final String mLoadMessage;
    /** classloader used to load the jar file */
    private final ClassLoader mClassLoader;

    /**
     * Returns the {@link LoadStatus} of the loading of the layoutlib jar file.
     */
    public LoadStatus getStatus() {
        return mStatus;
    }

    /** Returns the message associated with the {@link LoadStatus}. This is mostly used when
     * {@link #getStatus()} returns {@link LoadStatus#FAILED}.
     */
    public String getLoadMessage() {
        return mLoadMessage;
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
        String message = null;
        Bridge bridge = null;
        ILayoutBridge legacyBridge = null;
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
                        if (bridgeObject instanceof Bridge) {
                            bridge = (Bridge)bridgeObject;
                        } else if (bridgeObject instanceof ILayoutBridge) {
                            legacyBridge = (ILayoutBridge) bridgeObject;
                        }
                    }
                }

                if (bridge == null && legacyBridge == null) {
                    status = LoadStatus.FAILED;
                    message = "Failed to load " + CLASS_BRIDGE; //$NON-NLS-1$
                    if (log != null) {
                        log.error(null,
                                "Failed to load " + //$NON-NLS-1$
                                CLASS_BRIDGE +
                                " from " +          //$NON-NLS-1$
                                layoutLibJarOsPath);
                    }
                } else {
                    // mark the lib as loaded, unless it's overridden below.
                    status = LoadStatus.LOADED;

                    // check the API, only if it's not a legacy bridge
                    if (bridge != null) {
                        int api = bridge.getApiLevel();
                        if (api > Bridge.API_CURRENT) {
                            status = LoadStatus.FAILED;
                            message = "LayoutLib is too recent. Update your tool!";
                        }
                    }
                }
            }
        } catch (Throwable t) {
            status = LoadStatus.FAILED;
            Throwable cause = t;
            while (cause.getCause() != null) {
                cause = cause.getCause();
            }
            message = "Failed to load the LayoutLib: " + cause.getMessage();
            // log the error.
            if (log != null) {
                log.error(t, message);
            }
        }

        return new LayoutLibrary(bridge, legacyBridge, classLoader, status, message);
    }

    // ------ Layout Lib API proxy

    /**
     * Returns whether the LayoutLibrary supports a given {@link Capability}.
     * @return true if it supports it.
     *
     * @see Bridge#getCapabilities()
     *
     */
    public boolean supports(Capability capability) {
        if (mBridge != null) {
            return mBridge.getCapabilities().contains(capability);
        }

        if (mLegacyBridge != null) {
            switch (capability) {
                case UNBOUND_RENDERING:
                    // legacy stops at 4. 5 is new API.
                    return getLegacyApiLevel() == 4;
            }
        }

        return false;
    }

    /**
     * Initializes the Layout Library object. This must be called before any other action is taken
     * on the instance.
     *
     * @param fontLocation the location of the fonts in the SDK target.
     * @param enumValueMap map attrName => { map enumFlagName => Integer value }. This is typically
     *          read from attrs.xml in the SDK target.
     * @param log a {@link LayoutLog} object. Can be null.
     * @return true if success.
     *
     * @see Bridge#init(String, Map)
     */
    public boolean init(File fontLocation, Map<String, Map<String, Integer>> enumValueMap,
            LayoutLog log) {
        if (mBridge != null) {
            return mBridge.init(fontLocation, enumValueMap, log);
        } else if (mLegacyBridge != null) {
            return mLegacyBridge.init(fontLocation.getAbsolutePath(), enumValueMap);
        }

        return false;
    }

    /**
     * Prepares the layoutlib to unloaded.
     *
     * @see Bridge#dispose()
     */
    public boolean dispose() {
        if (mBridge != null) {
            return mBridge.dispose();
        }

        return true;
    }

    /**
     * Starts a layout session by inflating and rendering it. The method returns a
     * {@link RenderSession} on which further actions can be taken.
     * <p/>
     * Before taking further actions on the scene, it is recommended to use
     * {@link #supports(Capability)} to check what the scene can do.
     *
     * @return a new {@link ILayoutScene} object that contains the result of the scene creation and
     * first rendering or null if {@link #getStatus()} doesn't return {@link LoadStatus#LOADED}.
     *
     * @see Bridge#createSession(Params)
     */
    public RenderSession createSession(Params params) {
        if (mBridge != null) {
            return mBridge.createSession(params);
        } else if (mLegacyBridge != null) {
            return createLegacySession(params);
        }

        return null;
    }

    /**
     * Clears the resource cache for a specific project.
     * <p/>This cache contains bitmaps and nine patches that are loaded from the disk and reused
     * until this method is called.
     * <p/>The cache is not configuration dependent and should only be cleared when a
     * resource changes (at this time only bitmaps and 9 patches go into the cache).
     *
     * @param projectKey the key for the project.
     *
     * @see Bridge#clearCaches(Object)
     */
    public void clearCaches(Object projectKey) {
        if (mBridge != null) {
            mBridge.clearCaches(projectKey);
        } else if (mLegacyBridge != null) {
            mLegacyBridge.clearCaches(projectKey);
        }
    }

    // ------ Implementation

    private LayoutLibrary(Bridge bridge, ILayoutBridge legacyBridge, ClassLoader classLoader,
            LoadStatus status, String message) {
        mBridge = bridge;
        mLegacyBridge = legacyBridge;
        mClassLoader = classLoader;
        mStatus = status;
        mLoadMessage = message;
    }

    /**
     * Returns the API level of the legacy bridge.
     * <p/>
     * This handles the case where ILayoutBridge does not have a {@link ILayoutBridge#getApiLevel()}
     * (at API level 1).
     * <p/>
     * {@link ILayoutBridge#getApiLevel()} should never called directly.
     *
     * @return the api level of {@link #mLegacyBridge}.
     */
    private int getLegacyApiLevel() {
        int apiLevel = 1;
        try {
            apiLevel = mLegacyBridge.getApiLevel();
        } catch (AbstractMethodError e) {
            // the first version of the api did not have this method
            // so this is 1
        }

        return apiLevel;
    }

    private RenderSession createLegacySession(Params params) {
        if (params.getLayoutDescription() instanceof IXmlPullParser == false) {
            throw new IllegalArgumentException("Parser must be of type ILegacyPullParser");
        }
        if (params.getProjectCallback() instanceof
                com.android.layoutlib.api.IProjectCallback == false) {
            throw new IllegalArgumentException("Project callback must be of type ILegacyCallback");
        }

        int apiLevel = getLegacyApiLevel();

        // create a log wrapper since the older api requires a ILayoutLog
        final LayoutLog log = params.getLog();
        ILayoutLog logWrapper = new ILayoutLog() {

            public void warning(String message) {
                log.warning(null, message);
            }

            public void error(Throwable t) {
                log.error(null, t);
            }

            public void error(String message) {
                log.error(null, message);
            }
        };

        // convert the map of ResourceValue into IResourceValue. Super ugly but works.
        @SuppressWarnings("unchecked")
        Map<String, Map<String, IResourceValue>> projectMap =
            (Map<String, Map<String, IResourceValue>>)(Map) params.getProjectResources();
        @SuppressWarnings("unchecked")
        Map<String, Map<String, IResourceValue>> frameworkMap =
            (Map<String, Map<String, IResourceValue>>)(Map) params.getFrameworkResources();

        ILayoutResult result = null;

        if (apiLevel == 4) {
            // Final ILayoutBridge API added support for "render full height"
            result = mLegacyBridge.computeLayout(
                    (IXmlPullParser) params.getLayoutDescription(),
                    params.getProjectKey(),
                    params.getScreenWidth(), params.getScreenHeight(),
                    params.getRenderingMode() == RenderingMode.FULL_EXPAND ? true : false,
                    params.getDensity(), params.getXdpi(), params.getYdpi(),
                    params.getThemeName(), params.isProjectTheme(),
                    projectMap, frameworkMap,
                    (IProjectCallback) params.getProjectCallback(),
                    logWrapper);
        } else if (apiLevel == 3) {
            // api 3 add density support.
            result = mLegacyBridge.computeLayout(
                    (IXmlPullParser) params.getLayoutDescription(), params.getProjectKey(),
                    params.getScreenWidth(), params.getScreenHeight(),
                    params.getDensity(), params.getXdpi(), params.getYdpi(),
                    params.getThemeName(), params.isProjectTheme(),
                    projectMap, frameworkMap,
                    (IProjectCallback) params.getProjectCallback(), logWrapper);
        } else if (apiLevel == 2) {
            // api 2 added boolean for separation of project/framework theme
            result = mLegacyBridge.computeLayout(
                    (IXmlPullParser) params.getLayoutDescription(), params.getProjectKey(),
                    params.getScreenWidth(), params.getScreenHeight(),
                    params.getThemeName(), params.isProjectTheme(),
                    projectMap, frameworkMap,
                    (IProjectCallback) params.getProjectCallback(), logWrapper);
        } else {
            // First api with no density/dpi, and project theme boolean mixed
            // into the theme name.

            // change the string if it's a custom theme to make sure we can
            // differentiate them
            String themeName = params.getThemeName();
            if (params.isProjectTheme()) {
                themeName = "*" + themeName; //$NON-NLS-1$
            }

            result = mLegacyBridge.computeLayout(
                    (IXmlPullParser) params.getLayoutDescription(), params.getProjectKey(),
                    params.getScreenWidth(), params.getScreenHeight(),
                    themeName,
                    projectMap, frameworkMap,
                    (IProjectCallback) params.getProjectCallback(), logWrapper);
        }

        // clean up that is not done by the ILayoutBridge itself
        legacyCleanUp();

        return convertToScene(result);
    }

    /**
     * Converts a {@link ILayoutResult} to a {@link RenderSession}.
     */
    private RenderSession convertToScene(ILayoutResult result) {

        Result sceneResult;
        ViewInfo rootViewInfo = null;

        if (result.getSuccess() == ILayoutResult.SUCCESS) {
            sceneResult = Status.SUCCESS.createResult();
            ILayoutViewInfo oldRootView = result.getRootView();
            if (oldRootView != null) {
                rootViewInfo = convertToViewInfo(oldRootView);
            }
        } else {
            sceneResult = Status.ERROR_UNKNOWN.createResult(result.getErrorMessage());
        }

        // create a BasicLayoutScene. This will return the given values but return the default
        // implementation for all method.
        // ADT should gracefully handle the default implementations of LayoutScene
        return new StaticRenderSession(sceneResult, rootViewInfo, result.getImage());
    }

    /**
     * Converts a {@link ILayoutViewInfo} (and its children) to a {@link ViewInfo}.
     */
    private ViewInfo convertToViewInfo(ILayoutViewInfo view) {
        // create the view info.
        ViewInfo viewInfo = new ViewInfo(view.getName(), view.getViewKey(),
                view.getLeft(), view.getTop(), view.getRight(), view.getBottom());

        // then convert the children
        ILayoutViewInfo[] children = view.getChildren();
        if (children != null) {
            ArrayList<ViewInfo> convertedChildren = new ArrayList<ViewInfo>(children.length);
            for (ILayoutViewInfo child : children) {
                convertedChildren.add(convertToViewInfo(child));
            }
            viewInfo.setChildren(convertedChildren);
        }

        return viewInfo;
    }

    /**
     * Post rendering clean-up that must be done here because it's not done in any layoutlib using
     * {@link ILayoutBridge}.
     */
    private void legacyCleanUp() {
        try {
            Class<?> looperClass = mClassLoader.loadClass("android.os.Looper"); //$NON-NLS-1$
            Field threadLocalField = looperClass.getField("sThreadLocal"); //$NON-NLS-1$
            if (threadLocalField != null) {
                threadLocalField.setAccessible(true);
                // get object. Field is static so no need to pass an object
                ThreadLocal<?> threadLocal = (ThreadLocal<?>) threadLocalField.get(null);
                if (threadLocal != null) {
                    threadLocal.remove();
                }
            }
        } catch (Exception e) {
            // do nothing.
        }
    }
}
