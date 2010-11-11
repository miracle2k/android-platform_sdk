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

import com.android.layoutlib.api.ILayoutBridge;
import com.android.layoutlib.api.ILayoutResult;
import com.android.layoutlib.api.LayoutBridge;
import com.android.layoutlib.api.LayoutScene;
import com.android.layoutlib.api.SceneParams;
import com.android.layoutlib.api.SceneResult;
import com.android.layoutlib.api.ViewInfo;
import com.android.layoutlib.api.ILayoutResult.ILayoutViewInfo;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Map;

/**
 * {@link LayoutBridge} wrapper around a {@link ILayoutBridge}.
 * <p/>
 * The goal is to let tools only uses the latest API by providing a conversion interface
 * between the really old API ({@link ILayoutBridge}) and the new one ({@link ILayoutBridge}).
 *
 */
@SuppressWarnings("deprecation")
class LayoutBridgeWrapper extends LayoutBridge {

    private final ILayoutBridge mBridge;
    private final ClassLoader mClassLoader;

    LayoutBridgeWrapper(ILayoutBridge bridge, ClassLoader classLoader) {
        mBridge = bridge;
        mClassLoader = classLoader;
    }

    @Override
    public int getApiLevel() {
        int apiLevel = 1;
        try {
            apiLevel = mBridge.getApiLevel();
        } catch (AbstractMethodError e) {
            // the first version of the api did not have this method
            // so this is 1
        }

        return apiLevel;
    }

    @Override
    public boolean init(String fontOsLocation, Map<String, Map<String, Integer>> enumValueMap) {
        return mBridge.init(fontOsLocation, enumValueMap);
    }

    @Override
    public boolean dispose() {
        // there's no dispose in ILayoutBridge
        return true;
    }


    @Override
    public LayoutScene createScene(SceneParams params) {
        int apiLevel = mBridge.getApiLevel();

        ILayoutResult result = null;

        if (apiLevel == 4) {
            // Final ILayoutBridge API added support for "render full height"
            result = mBridge.computeLayout(
                    params.getLayoutDescription(), params.getProjectKey(),
                    params.getScreenWidth(), params.getScreenHeight(), params.getRenderFullSize(),
                    params.getDensity(), params.getXdpi(), params.getYdpi(),
                    params.getThemeName(), params.getIsProjectTheme(),
                    params.getProjectResources(), params.getFrameworkResources(),
                    params.getProjectCallback(), params.getLogger());
        } else if (apiLevel == 3) {
            // api 3 add density support.
            result = mBridge.computeLayout(
                    params.getLayoutDescription(), params.getProjectKey(),
                    params.getScreenWidth(), params.getScreenHeight(),
                    params.getDensity(), params.getXdpi(), params.getYdpi(),
                    params.getThemeName(), params.getIsProjectTheme(),
                    params.getProjectResources(), params.getFrameworkResources(),
                    params.getProjectCallback(), params.getLogger());
        } else if (apiLevel == 2) {
            // api 2 added boolean for separation of project/framework theme
            result = mBridge.computeLayout(
                    params.getLayoutDescription(), params.getProjectKey(),
                    params.getScreenWidth(), params.getScreenHeight(),
                    params.getThemeName(), params.getIsProjectTheme(),
                    params.getProjectResources(), params.getFrameworkResources(),
                    params.getProjectCallback(), params.getLogger());
        } else {
            // First api with no density/dpi, and project theme boolean mixed
            // into the theme name.

            // change the string if it's a custom theme to make sure we can
            // differentiate them
            String themeName = params.getThemeName();
            if (params.getIsProjectTheme()) {
                themeName = "*" + themeName; //$NON-NLS-1$
            }

            result = mBridge.computeLayout(
                    params.getLayoutDescription(), params.getProjectKey(),
                    params.getScreenWidth(), params.getScreenHeight(),
                    themeName,
                    params.getProjectResources(), params.getFrameworkResources(),
                    params.getProjectCallback(), params.getLogger());
        }

        // clean up that is not done by the ILayoutBridge itself
        cleanUp();

        return convertToScene(result);
    }


    @Override
    public void clearCaches(Object projectKey) {
        mBridge.clearCaches(projectKey);
    }

    /**
     * Converts a {@link ILayoutResult} to a {@link LayoutScene}.
     */
    private LayoutScene convertToScene(ILayoutResult result) {

        SceneResult sceneResult;
        ViewInfo rootViewInfo;

        if (result.getSuccess() == ILayoutResult.SUCCESS) {
            sceneResult = SceneResult.SUCCESS;
            rootViewInfo = convertToViewInfo(result.getRootView());
        } else {
            sceneResult = new SceneResult(result.getErrorMessage());
            rootViewInfo = null;
        }

        // create a BasicLayoutScene. This will return the given values but return the default
        // implementation for all method.
        // ADT should gracefully handle the default implementations of LayoutScene
        return new BasicLayoutScene(sceneResult, rootViewInfo, result.getImage());
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
    private void cleanUp() {
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
