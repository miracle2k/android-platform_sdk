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

package com.android.ide.common.rendering.api;

public class LayoutLog {

    public final static String TAG_UNSUPPORTED = "unsupported";
    public final static String TAG_BROKEN = "broken";
    public final static String TAG_RESOURCES_RESOLVE = "resources.resolve";
    public final static String TAG_RESOURCES_READ = "resources.read";
    public final static String TAG_RESOURCES_FORMAT = "resources.format";
    public final static String TAG_MATRIX_AFFINE = "matrix.affine";
    public final static String TAG_MATRIX_INVERSE = "matrix.inverse";
    public final static String TAG_MASKFILTER = "maskfilter";
    public final static String TAG_DRAWFILTER = "drawfilter";
    public final static String TAG_PATHEFFECT = "patheffect";
    public final static String TAG_COLORFILTER = "colorfilter";
    public final static String TAG_RASTERIZER = "rasterizer";
    public final static String TAG_SHADER = "shader";
    public final static String TAG_XFERMODE = "xfermode";


    public void warning(String tag, String message) {
    }

    public void fidelityWarning(String tag, String message, Throwable throwable) {
    }

    public void error(String tag, String message) {
    }

    /**
     * Logs an error message and a {@link Throwable}.
     * @param message the message to log.
     * @param throwable the {@link Throwable} to log.
     */
    public void error(String tag, String message, Throwable throwable) {

    }

}
