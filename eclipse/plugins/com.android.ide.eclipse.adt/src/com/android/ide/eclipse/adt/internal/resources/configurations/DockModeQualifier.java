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

package com.android.ide.eclipse.adt.internal.resources.configurations;

import com.android.ide.eclipse.adt.internal.editors.IconFactory;
import com.android.sdklib.resources.DockMode;
import com.android.sdklib.resources.ResourceEnum;

import org.eclipse.swt.graphics.Image;

/**
 * Resource Qualifier for Navigation Method.
 */
public final class DockModeQualifier extends EnumBasedResourceQualifier {

    public static final String NAME = "Dock Mode";

    private DockMode mValue;

    public DockModeQualifier() {
        // pass
    }

    public DockModeQualifier(DockMode value) {
        mValue = value;
    }

    public DockMode getValue() {
        return mValue;
    }

    @Override
    ResourceEnum getEnumValue() {
        return mValue;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getShortName() {
        return "Dock Mode";
    }


    @Override
    public Image getIcon() {
        return IconFactory.getInstance().getIcon("dockmode"); //$NON-NLS-1$
    }

    @Override
    public boolean checkAndSet(String value, FolderConfiguration config) {
        DockMode mode = DockMode.getEnum(value);
        if (mode != null) {
            DockModeQualifier qualifier = new DockModeQualifier(mode);
            config.setDockModeQualifier(qualifier);
            return true;
        }

        return false;
    }

    @Override
    public boolean isMatchFor(ResourceQualifier qualifier) {
        // only NONE is a match other DockModes
        if (mValue == DockMode.NONE) {
            return true;
        }

        // others must be an exact match
        return ((DockModeQualifier)qualifier).mValue == mValue;
    }

    @Override
    public boolean isBetterMatchThan(ResourceQualifier compareTo, ResourceQualifier reference) {
        if (compareTo == null) {
            return true;
        }

        DockModeQualifier compareQualifier = (DockModeQualifier)compareTo;
        DockModeQualifier referenceQualifier = (DockModeQualifier)reference;
        // if they are a perfect match, the receiver cannot be a better match.
        if (compareQualifier.getValue() == referenceQualifier.getValue()) {
            return false;
        } else if (mValue == DockMode.NONE) {
            // else "none" can be a match in case there's no exact match
            return true;
        }

        return false;
    }
}
