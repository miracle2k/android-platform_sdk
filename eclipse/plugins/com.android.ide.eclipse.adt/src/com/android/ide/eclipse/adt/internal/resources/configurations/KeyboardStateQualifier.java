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

package com.android.ide.eclipse.adt.internal.resources.configurations;

import com.android.ide.eclipse.adt.internal.editors.IconFactory;
import com.android.sdklib.resources.KeyboardState;
import com.android.sdklib.resources.ResourceEnum;

import org.eclipse.swt.graphics.Image;

/**
 * Resource Qualifier for keyboard state.
 */
public final class KeyboardStateQualifier extends EnumBasedResourceQualifier {

    public static final String NAME = "Keyboard State";

    private KeyboardState mValue = null;

    public KeyboardStateQualifier() {
        // pass
    }

    public KeyboardStateQualifier(KeyboardState value) {
        mValue = value;
    }

    public KeyboardState getValue() {
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
        return "Keyboard";
    }

    @Override
    public Image getIcon() {
        return IconFactory.getInstance().getIcon("keyboard"); //$NON-NLS-1$
    }

    @Override
    public boolean checkAndSet(String value, FolderConfiguration config) {
        KeyboardState orientation = KeyboardState.getEnum(value);
        if (orientation != null) {
            KeyboardStateQualifier qualifier = new KeyboardStateQualifier();
            qualifier.mValue = orientation;
            config.setKeyboardStateQualifier(qualifier);
            return true;
        }

        return false;
    }

    @Override
    public boolean isMatchFor(ResourceQualifier qualifier) {
        if (qualifier instanceof KeyboardStateQualifier) {
            KeyboardStateQualifier referenceQualifier = (KeyboardStateQualifier)qualifier;

            // special case where EXPOSED can be used for SOFT
            if (referenceQualifier.mValue == KeyboardState.SOFT &&
                    mValue == KeyboardState.EXPOSED) {
                return true;
            }

            return referenceQualifier.mValue == mValue;
        }

        return false;
    }

    @Override
    public boolean isBetterMatchThan(ResourceQualifier compareTo, ResourceQualifier reference) {
        if (compareTo == null) {
            return true;
        }

        KeyboardStateQualifier compareQualifier = (KeyboardStateQualifier)compareTo;
        KeyboardStateQualifier referenceQualifier = (KeyboardStateQualifier)reference;
        if (referenceQualifier.mValue == KeyboardState.SOFT) { // only case where there could be a
                                                               // better qualifier
            // only return true if it's a better value.
            if (compareQualifier.mValue == KeyboardState.EXPOSED && mValue == KeyboardState.SOFT) {
                return true;
            }
        }

        return false;
    }
}
