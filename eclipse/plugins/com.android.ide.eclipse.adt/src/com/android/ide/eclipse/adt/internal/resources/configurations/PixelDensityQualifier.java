/*
 * Copyright (C) 2007 The Android Open Source Project
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
import com.android.sdklib.resources.Density;
import com.android.sdklib.resources.ResourceEnum;

import org.eclipse.swt.graphics.Image;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resource Qualifier for Screen Pixel Density.
 */
public final class PixelDensityQualifier extends EnumBasedResourceQualifier {
    private final static Pattern sDensityLegacyPattern = Pattern.compile("^(\\d+)dpi$");//$NON-NLS-1$

    public static final String NAME = "Pixel Density";

    private Density mValue = Density.MEDIUM;

    public PixelDensityQualifier() {
        // pass
    }

    public PixelDensityQualifier(Density value) {
        mValue = value;
    }

    public Density getValue() {
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
        return NAME;
    }

    @Override
    public Image getIcon() {
        return IconFactory.getInstance().getIcon("dpi"); //$NON-NLS-1$
    }

    @Override
    public boolean checkAndSet(String value, FolderConfiguration config) {
        Density density = Density.getEnum(value);
        if (density == null) {

            // attempt to read a legacy value.
            Matcher m = sDensityLegacyPattern.matcher(value);
            if (m.matches()) {
                String v = m.group(1);

                try {
                    density = Density.getEnum(Integer.parseInt(v));
                } catch (NumberFormatException e) {
                    // looks like the string we extracted wasn't a valid number
                    // which really shouldn't happen since the regexp would have failed.
                }
            }
        }

        if (density != null) {
            PixelDensityQualifier qualifier = new PixelDensityQualifier();
            qualifier.mValue = density;
            config.setPixelDensityQualifier(qualifier);
            return true;
        }

        return false;
    }

    @Override
    public boolean isMatchFor(ResourceQualifier qualifier) {
        if (qualifier instanceof PixelDensityQualifier) {
            // as long as there's a density qualifier, it's always a match.
            // The best match will be found later.
            return true;
        }

        return false;
    }

    @Override
    public boolean isBetterMatchThan(ResourceQualifier compareTo, ResourceQualifier reference) {
        if (compareTo == null) {
            return true;
        }

        PixelDensityQualifier compareQ = (PixelDensityQualifier)compareTo;
        PixelDensityQualifier referenceQ = (PixelDensityQualifier)reference;

        if (mValue == referenceQ.mValue && compareQ.mValue != referenceQ.mValue) {
            // got exact value, this is the best!
            return true;
        } else {
            // in all case we're going to prefer the higher dpi.
            // if reference is high, we want highest dpi.
            // if reference is medium, we'll prefer to scale down high dpi, than scale up low dpi
            // if reference if low, we'll prefer to scale down high than medium (2:1 over 4:3)
            return mValue.getDpiValue() > compareQ.mValue.getDpiValue();
        }
    }
}
