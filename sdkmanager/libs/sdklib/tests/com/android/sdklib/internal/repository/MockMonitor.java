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

package com.android.sdklib.internal.repository;


/**
 * Mock implementation of {@link ITaskMonitor} that simply captures
 * the output in local strings. Does not provide any UI and has no
 * support for creating sub-monitors.
 */
public class MockMonitor implements ITaskMonitor {

    String mCapturedResults = "";
    String mCapturedDescriptions = "";

    public String getCapturedResults() {
        return mCapturedResults;
    }

    public String getCapturedDescriptions() {
        return mCapturedDescriptions;
    }

    public void setResult(String resultFormat, Object... args) {
        mCapturedResults += String.format(resultFormat, args) + "\n";
    }

    public void setProgressMax(int max) {
    }

    public void setDescription(String descriptionFormat, Object... args) {
        mCapturedDescriptions += String.format(descriptionFormat, args) + "\n";
    }

    public boolean isCancelRequested() {
        return false;
    }

    public void incProgress(int delta) {
    }

    public int getProgress() {
        return 0;
    }

    public boolean displayPrompt(String title, String message) {
        return false;
    }

    public ITaskMonitor createSubMonitor(int tickCount) {
        return null;
    }
}
