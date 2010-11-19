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

package com.android.ide.eclipse.adt.internal.editors.layout.gre;

/**
 * An implementation of {@link IViewMetadata} which consults the
 * SDK descriptors to answer metadata questions about views.
 */
import com.android.ide.common.api.IViewMetadata;
import com.android.ide.eclipse.adt.internal.editors.descriptors.ElementDescriptor;
import com.android.ide.eclipse.adt.internal.editors.layout.IGraphicalLayoutEditor;
import com.android.ide.eclipse.adt.internal.editors.layout.LayoutEditor;

import java.util.HashMap;
import java.util.Map;

final class ViewMetadata implements IViewMetadata {
    /** The {@link ElementDescriptor} for this view, computed lazily */
    private ElementDescriptor mDescriptor;

    /** The fully qualified class name of the view whose metadata this class represents */
    private String mFqcn;

    /** The {@link IGraphicalLayoutEditor} associated with the view we're looking up */
    private LayoutEditor mEditor;

    /**
     * A map from class names to {@link FillPreference} which indicates how each view
     * prefers to grow when added in various layout contexts
     */
    private static final Map<String,FillPreference> mFill = new HashMap<String,FillPreference>();
    static {
        // Hardcoded metadata about fill preferences for various known views. We should
        // work to try to get this into the platform as designtime annotations.

        mFill.put("android.widget.EditText", FillPreference.WIDTH_IN_VERTICAL);     //$NON-NLS-1$
        mFill.put("android.widget.DialerFilter", FillPreference.WIDTH_IN_VERTICAL); //$NON-NLS-1$
        mFill.put("android.widget.SeekBar", FillPreference.WIDTH_IN_VERTICAL);      //$NON-NLS-1$
        mFill.put("android.widget.Spinner", FillPreference.WIDTH_IN_VERTICAL);      //$NON-NLS-1$
        mFill.put("android.widget.AutoComplete", FillPreference.WIDTH_IN_VERTICAL); //$NON-NLS-1$
        mFill.put("android.widget.ListView", FillPreference.WIDTH_IN_VERTICAL);     //$NON-NLS-1$
        mFill.put("android.widget.GridView", FillPreference.OPPOSITE);              //$NON-NLS-1$
        mFill.put("android.widget.Gallery", FillPreference.WIDTH_IN_VERTICAL);      //$NON-NLS-1$
        mFill.put("android.widget.TabWidget", FillPreference.WIDTH_IN_VERTICAL);    //$NON-NLS-1$
        mFill.put("android.widget.MapView", FillPreference.OPPOSITE);               //$NON-NLS-1$
        mFill.put("android.widget.WebView", FillPreference.OPPOSITE);               //$NON-NLS-1$

        // In addition, all layouts are FillPreference.OPPOSITE - these are computed
        // lazily rather than enumerating them here

        // For any other view, the fallback fill preference is FillPreference.NONE.
    }

    public ViewMetadata(LayoutEditor editor, String fqcn) {
        super();
        mFqcn = fqcn;
        mEditor = editor;
    }

    /** Lazily look up the descriptor for the FQCN of this metadata object */
    private boolean findDescriptor() {
        if (mDescriptor == null) {
            // Look up the corresponding view element node. We don't need the graphical part;
            // we just need the project context. Maybe I should extract this code into
            // a utility.
            mDescriptor = mEditor.getFqcnViewDescriptor(mFqcn);
        }

        return mDescriptor != null;
    }

    public boolean isParent() {
        if (findDescriptor()) {
            return mDescriptor.hasChildren();
        }

        return false;
    }

    public String getDisplayName() {
        if (findDescriptor()) {
            return mDescriptor.getUiName();
        }

        return mFqcn.substring(mFqcn.lastIndexOf('.') + 1); // This also works when there is no "."
    }

    public String getTooltip() {
        if (findDescriptor()) {
            return mDescriptor.getTooltip();
        }

        return null;
    }

    /** Returns true if this view represents a layout */
    private boolean isLayout() {
        if (findDescriptor()) {
            return mDescriptor.hasChildren();
        }
        return false;
    }

    public FillPreference getFillPreference() {
        FillPreference fillPreference = mFill.get(mFqcn);
        if (fillPreference == null) {
            if (isLayout()) {
                fillPreference = FillPreference.OPPOSITE;
            } else {
                fillPreference = FillPreference.NONE;
            }
            mFill.put(mFqcn, fillPreference);
        }

        return fillPreference;
    }
}
