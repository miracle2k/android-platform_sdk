/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.sdklib;


/**
 * An implementation of {@link ISdkLog} that prints to {@link System#out} and {@link System#err}.
 * <p/>
 * This is mostly useful for unit tests. It should not be used by GUI-based tools (e.g.
 * Eclipse plugin or SWT-based apps) which should have a better way to expose their logging
 * error and warnings.
 */
public class StdSdkLog implements ISdkLog {

    public void error(Throwable t, String errorFormat, Object... args) {
        if (errorFormat != null) {
            System.err.printf("Error: " + errorFormat, args);
            if (!errorFormat.endsWith("\n")) {
                System.err.printf("\n");
            }
        }
        if (t != null) {
            System.err.printf("Error: %s\n", t.getMessage());
        }
    }

    public void warning(String warningFormat, Object... args) {
        System.out.printf("Warning: " + warningFormat, args);
        if (!warningFormat.endsWith("\n")) {
            System.out.printf("\n");
        }
    }

    public void printf(String msgFormat, Object... args) {
        System.out.printf(msgFormat, args);
    }
}
