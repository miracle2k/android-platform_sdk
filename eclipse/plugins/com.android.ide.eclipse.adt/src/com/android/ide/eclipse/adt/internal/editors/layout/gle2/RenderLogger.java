/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.ide.eclipse.adt.internal.editors.layout.gle2;

import com.android.ide.common.rendering.api.LayoutLog;
import com.android.ide.eclipse.adt.AdtPlugin;

import org.eclipse.core.runtime.IStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link LayoutLog} which records the problems it encounters and offers them as a
 * single summary at the end
 */
class RenderLogger extends LayoutLog {
    private final String mName;
    private List<String> mFidelityWarnings;
    private List<String> mWarnings;
    private List<String> mErrors;
    private boolean mHaveExceptions;

    /** Construct a logger for the given named layout */
    RenderLogger(String name) {
        mName = name;
    }

    /**
     * Are there any logged errors or warnings during the render?
     *
     * @return true if there were problems during the render
     */
    public boolean hasProblems() {
        return mFidelityWarnings != null || mErrors != null || mWarnings != null ||
            mHaveExceptions;
    }

    /**
     * Returns a (possibly multi-line) description of all the problems
     *
     * @return a string describing the rendering problems
     */
    public String getProblems() {
        StringBuilder sb = new StringBuilder();

        if (mErrors != null) {
            for (String error : mErrors) {
                sb.append(error).append('\n');
            }
        }

        if (mWarnings != null) {
            for (String warning : mWarnings) {
                sb.append(warning).append('\n');
            }
        }

        if (mFidelityWarnings != null) {
            sb.append("The graphics preview may not be accurate:\n");
            for (String warning : mFidelityWarnings) {
                sb.append("* ");
                sb.append(warning).append('\n');
            }
        }

        if (mHaveExceptions) {
            sb.append("Exception details are logged in Window > Show View > Error Log");
        }

        return sb.toString();
    }

    // ---- extends LayoutLog ----

    @Override
    public void error(String tag, String message) {
        String description = describe(tag, message);
        AdtPlugin.log(IStatus.ERROR, "%1$s: %2$s", mName, description);

        addError(description);
    }

    @Override
    public void error(String tag, Throwable throwable) {
        AdtPlugin.log(throwable, "%1$s: %2$s", mName, tag);
        assert throwable != null;
        mHaveExceptions = true;

        String message = throwable.getMessage();
        if (message == null) {
            message = throwable.getClass().getName();
        } else if (tag == null && throwable instanceof ClassNotFoundException
                && !message.contains(ClassNotFoundException.class.getSimpleName())) {
            tag = ClassNotFoundException.class.getSimpleName();
        }
        String description = describe(tag, message);
        addError(description);
    }

    @Override
    public void error(String tag, String message, Throwable throwable) {
        String description = describe(tag, message);
        AdtPlugin.log(throwable, "%1$s: %2$s", mName, description);
        if (throwable != null) {
            mHaveExceptions = true;
        }

        addError(description);
    }

    @Override
    public void warning(String tag, String message) {
        String description = describe(tag, message);
        AdtPlugin.log(IStatus.WARNING, "%1$s: %2$s", mName, description);
        addWarning(description);
    }

    @Override
    public void fidelityWarning(String tag, String message, Throwable throwable) {
        String description = describe(tag, message);
        AdtPlugin.log(throwable, "%1$s: %2$s", mName, description);
        if (throwable != null) {
            mHaveExceptions = true;
        }

        addFidelityWarning(description);
    }

    private String describe(String tag, String message) {
        StringBuilder sb = new StringBuilder();
        if (tag != null) {
            sb.append(tag);
        }
        if (message != null) {
            if (sb.length() > 0) {
                sb.append(": ");
            }
            sb.append(message);
        }
        return sb.toString();
    }

    private void addWarning(String description) {
        if (mWarnings == null) {
            mWarnings = new ArrayList<String>();
        } else if (mWarnings.contains(description)) {
            // Avoid duplicates
            return;
        }
        mWarnings.add(description);
    }

    private void addError(String description) {
        if (mErrors == null) {
            mErrors = new ArrayList<String>();
        } else if (mErrors.contains(description)) {
            // Avoid duplicates
            return;
        }
        mErrors.add(description);
    }

    private void addFidelityWarning(String description) {
        if (mFidelityWarnings == null) {
            mFidelityWarnings = new ArrayList<String>();
        } else if (mFidelityWarnings.contains(description)) {
            // Avoid duplicates
            return;
        }
        mFidelityWarnings.add(description);
    }
}
