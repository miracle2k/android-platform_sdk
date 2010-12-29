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

import com.android.ide.common.api.DropFeedback;
import com.android.ide.common.api.IDragElement;
import com.android.ide.common.api.INode;

/** Rule for AdapterView subclasses that don't have more specific rules */
public class AdapterViewRule extends BaseLayoutRule {
    @Override
    public DropFeedback onDropEnter(INode targetNode, IDragElement[] elements) {
        // You are not allowed to insert children into AdapterViews; you must
        // use the dedicated addView methods etc dynamically
        return null;
    }
}
