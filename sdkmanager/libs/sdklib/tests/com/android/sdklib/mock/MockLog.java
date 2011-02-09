/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.sdklib.mock;

import com.android.sdklib.ISdkLog;

import java.util.ArrayList;
import java.util.Formatter;

/**
 * An instance of {@link ISdkLog} that captures all messages to an internal list.
 * Messages can be retrieved later using {@link #toString()}.
 * Useful for unit-tests.
 */
public class MockLog implements ISdkLog {
    private ArrayList<String> mMessages = new ArrayList<String>();

    private void add(String code, String format, Object... args) {
        mMessages.add(new Formatter().format(code + format, args).toString());
    }

    public void warning(String format, Object... args) {
        add("W ", format, args);
    }

    public void printf(String format, Object... args) {
        add("P ", format, args);
    }

    public void error(Throwable t, String format, Object... args) {
        if (t != null) {
            add("T", "%s", t.toString());
        }
        add("E ", format, args);
    }

    @Override
    public String toString() {
        return mMessages.toString();
    }

    public void clear() {
        mMessages.clear();
    }
}
