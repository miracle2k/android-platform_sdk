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

import static com.android.ide.common.layout.LayoutConstants.FQCN_TABLE_ROW;

import com.android.ide.common.api.IMenuCallback;
import com.android.ide.common.api.INode;
import com.android.ide.common.api.IViewRule;
import com.android.ide.common.api.InsertType;
import com.android.ide.common.api.MenuAction;

import java.util.List;

/**
 * An {@link IViewRule} for android.widget.TableLayout.
 */
public class TableLayoutRule extends LinearLayoutRule {
    // A table is a linear layout, but with a few differences:
    // the default is vertical, not horizontal
    // The fill of all children should be wrap_content

    @Override
    protected boolean isVertical(INode node) {
        // Tables are always vertical
        return true;
    }

    @Override
    protected boolean supportsOrientation() {
        return false;
    }

    @Override
    public void onChildInserted(INode child, INode parent, InsertType insertType) {
        // Overridden to inhibit the setting of layout_width/layout_height since
        // it should always be match_parent
    }

    /**
     * Add an explicit "Add Row" action to the context menu
     */
    @Override
   public List<MenuAction> getContextMenu(final INode selectedNode) {
        IMenuCallback addTab = new IMenuCallback() {
            public void action(MenuAction action, final String valueId, Boolean newValue) {
                final INode node = selectedNode;
                node.appendChild(FQCN_TABLE_ROW);

            }
        };
        return concatenate(super.getContextMenu(selectedNode),
            new MenuAction.Action("_addrow", "Add Row", //$NON-NLS-1$
                    null, addTab));
    }

}
