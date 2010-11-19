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
package com.android.ide.common.layout;

import com.android.ide.common.api.INode;
import com.android.ide.common.api.IViewRule;
import com.android.ide.common.api.InsertType;

/**
 * An {@link IViewRule} for android.widget.TableRow.
 */
public class TableRowRule extends LinearLayoutRule {
    @Override
    protected boolean isVertical(INode node) {
        return false;
    }

    @Override
    protected boolean supportsOrientation() {
        return false;
    }

    @Override
    public void onChildInserted(INode child, INode parent, InsertType insertType) {
        // Overridden to inhibit the setting of layout_width/layout_height since
        // the table row will enforce match_parent and wrap_content for width and height
        // respectively.
    }

}
