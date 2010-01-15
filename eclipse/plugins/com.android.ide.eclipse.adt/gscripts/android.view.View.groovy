/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.adt.gscripts;

import com.android.ide.eclipse.adt.editors.layout.gscripts.BaseViewRule;
import com.android.ide.eclipse.adt.editors.layout.gscripts.INodeProxy;
import com.android.ide.eclipse.adt.editors.layout.gscripts.DropZone;
import com.android.ide.eclipse.adt.editors.layout.gscripts.Rect;
import com.android.ide.eclipse.adt.editors.layout.gscripts.Point;

import java.util.Map;
import java.util.ArrayList;


/**
 * An {@link IViewRule} for android.view.View and all its derived classes.
 * This is the "root" rule, that is used whenever there is not more specific rule to apply.
 */
public class AndroidViewViewRule extends BaseViewRule {

    // TODO if there's nothing to implement here, I might as well remove it.
    // Before that, make sure the engine can deal with the lack of a base class
    // fallback when navigating the hierarchy.

}
