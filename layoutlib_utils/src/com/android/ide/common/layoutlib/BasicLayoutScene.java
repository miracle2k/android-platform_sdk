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

import com.android.layoutlib.api.LayoutScene;
import com.android.layoutlib.api.SceneResult;
import com.android.layoutlib.api.ViewInfo;

import java.awt.image.BufferedImage;

/**
 * Basic LayoutScene returning a given {@link SceneResult}, {@link ViewInfo} and
 * {@link BufferedImage}.
 * <p/>
 * All other methods are untouched from the base implementation provided by the API.
 *
 */
public class BasicLayoutScene extends LayoutScene {

    private final SceneResult mResult;
    private final ViewInfo mRootViewInfo;
    private final BufferedImage mImage;

    public BasicLayoutScene(SceneResult result, ViewInfo rootViewInfo, BufferedImage image) {
        mResult = result;
        mRootViewInfo = rootViewInfo;
        mImage = image;
    }

    @Override
    public SceneResult getResult() {
        return mResult;
    }

    @Override
    public ViewInfo getRootView() {
        return mRootViewInfo;
    }

    @Override
    public BufferedImage getImage() {
        return mImage;
    }
}
