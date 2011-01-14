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
    private List<String> mTags;

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
    public void error(String tag, String message, Object data) {
        String description = describe(message);
        AdtPlugin.log(IStatus.ERROR, "%1$s: %2$s", mName, description);

        addError(tag, description);
    }

    @Override
    public void error(String tag, String message, Throwable throwable, Object data) {
        String description = describe(message);
        AdtPlugin.log(throwable, "%1$s: %2$s", mName, description);
        if (throwable != null) {
            mHaveExceptions = true;
        }

        addError(tag, description);
    }

    @Override
    public void warning(String tag, String message, Object data) {
        String description = describe(message);
        AdtPlugin.log(IStatus.WARNING, "%1$s: %2$s", mName, description);
        addWarning(tag, description);
    }

    @Override
    public void fidelityWarning(String tag, String message, Throwable throwable, Object data) {
        String description = describe(message);
        AdtPlugin.log(throwable, "%1$s: %2$s", mName, description);
        if (throwable != null) {
            mHaveExceptions = true;
        }

        addFidelityWarning(tag, description);
    }

    private String describe(String message) {
        StringBuilder sb = new StringBuilder();
        if (message != null) {
            if (sb.length() > 0) {
                sb.append(": ");
            }
            sb.append(message);
        }
        return sb.toString();
    }

    private void addWarning(String tag, String description) {
        if (mWarnings == null) {
            mWarnings = new ArrayList<String>();
        } else if (mWarnings.contains(description)) {
            // Avoid duplicates
            return;
        }
        mWarnings.add(description);
        addTag(tag);
    }

    private void addError(String tag, String description) {
        if (mErrors == null) {
            mErrors = new ArrayList<String>();
        } else if (mErrors.contains(description)) {
            // Avoid duplicates
            return;
        }
        mErrors.add(description);
        addTag(tag);
    }

    private void addFidelityWarning(String tag, String description) {
        if (mFidelityWarnings == null) {
            mFidelityWarnings = new ArrayList<String>();
        } else if (mFidelityWarnings.contains(description)) {
            // Avoid duplicates
            return;
        }
        mFidelityWarnings.add(description);
        addTag(tag);
    }

    // ---- Tags ----

    private void addTag(String tag) {
        if (tag != null) {
            if (mTags == null) {
                mTags = new ArrayList<String>();
            }
            mTags.add(tag);
        }
    }

    /**
     * Returns true if the given tag prefix has been seen
     *
     * @param prefix the tag prefix to look for
     * @return true iff any tags with the given prefix was seen during the render
     */
    public boolean seenTagPrefix(String prefix) {
        if (mTags != null) {
            for (String tag : mTags) {
                if (tag.startsWith(prefix)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Returns true if the given tag has been seen
     *
     * @param tag the tag to look for
     * @return true iff the tag was seen during the render
     */
    public boolean seenTag(String tag) {
        if (mTags != null) {
            return mTags.contains(tag);
        } else {
            return false;
        }
    }


}
