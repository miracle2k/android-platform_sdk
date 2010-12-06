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

package com.android.ide.eclipse.adt.internal.refactoring.changes;

/**
 * This class describes the text changes of android layout files
 *
 */
public class AndroidLayoutChangeDescription {

    private String mClassName;

    private String mNewName;

    private int mType;

    /**
     * the view layout
     */
    public static final int VIEW_TYPE = 0;

    /**
     * the standalone layout
     */
    public static final int STANDALONE_TYPE = 1;

    /**
     * Creates a new <code>AndroidDocumentChange</code>
     *
     * @param className the old layout class name
     * @param newName the new layout class name
     * @param type the layout type; valid value are VIEW_TYPE and STANDALONE_TYPE
     */
    public AndroidLayoutChangeDescription(String className, String newName, int type) {
        this.mClassName = className;
        this.mNewName = newName;
        this.mType = type;
    }

    /**
     * @return the old class name
     */
    public String getClassName() {
        return mClassName;
    }

    /**
     * @return the new class name
     */
    public String getNewName() {
        return mNewName;
    }

    /**
     * @return the layout type
     */
    public int getType() {
        return mType;
    }

    /**
     * @return true if the layout is standalone
     */
    public boolean isStandalone() {
        return mType == STANDALONE_TYPE;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((mClassName == null) ? 0 : mClassName.hashCode());
        result = prime * result + ((mNewName == null) ? 0 : mNewName.hashCode());
        result = prime * result + mType;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        AndroidLayoutChangeDescription other = (AndroidLayoutChangeDescription) obj;
        if (mClassName == null) {
            if (other.mClassName != null)
                return false;
        } else if (!mClassName.equals(other.mClassName))
            return false;
        if (mNewName == null) {
            if (other.mNewName != null)
                return false;
        } else if (!mNewName.equals(other.mNewName))
            return false;
        if (mType != other.mType)
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "AndroidLayoutChangeDescription [className=" + mClassName + ", newName=" + mNewName
                + ", type=" + mType + "]";
    }

}
