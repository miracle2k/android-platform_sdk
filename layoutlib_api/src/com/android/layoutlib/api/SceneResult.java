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

package com.android.layoutlib.api;


/**
 * Scene result class.
 */
public class SceneResult {

    private final LayoutStatus mStatus;
    private final String mErrorMessage;
    private final Throwable mThrowable;

    public enum LayoutStatus { SUCCESS, ERROR, NOT_IMPLEMENTED };

    /**
     * Singleton SUCCESS {@link SceneResult} object.
     */
    public static final SceneResult SUCCESS = new SceneResult(LayoutStatus.SUCCESS);

    /**
     * Creates an error {@link SceneResult} object with the given message.
     */
    public SceneResult(String errorMessage) {
        mStatus = LayoutStatus.ERROR;
        mErrorMessage = errorMessage;
        mThrowable = null;
    }

    /**
     * Creates an error {@link SceneResult} object with the given message and {@link Throwable}
     */
    public SceneResult(String errorMessage, Throwable t) {
        mStatus = LayoutStatus.ERROR;
        mErrorMessage = errorMessage;
        mThrowable = t;
    }

    /*package*/ SceneResult(LayoutStatus status) {
        mStatus = LayoutStatus.NOT_IMPLEMENTED;
        mErrorMessage = null;
        mThrowable = null;
    }

    /**
     * Returns the status. This is never null.
     */
    public LayoutStatus getStatus() {
        return mStatus;
    }

    /**
     * Returns the error message. This can be null if the status is {@link LayoutStatus#SUCCESS}.
     */
    public String getErrorMessage() {
        return mErrorMessage;
    }

    /**
     * Returns the exception. This can be null.
     */
    public Throwable getException() {
        return mThrowable;
    }
}
