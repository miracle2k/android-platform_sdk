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

package com.android.ide.eclipse.adt.editors.layout.gscripts;


/**
 * A drop zone, as returned by {@link IViewRule#dropStart(INodeProxy)}.
 * <p/>
 * A zone is characterized by its {@link #bounds} rectangle, which must be valid for the
 * zone to be useful (i.e. with w>0 and h>0). The zone must lie in the bounds given by
 * the {@link INodeProxy} and is in absolute canvas coordinates.
 * <p/>
 * No strong ordering properties are defined if zones overlap each other.
 */
public class DropZone {

    /**
     * The rectangle (in absolute coordinates) of the drop zone.
     * Never null, but the rectangle can be invalid.
     */
    public final Rect bounds = new Rect();

    /**
     * An opaque object that the script can use for its own purpose, e.g. some pre-computed
     * data or a closure that can be computed in dropStart() and used in dropFinish().
     */
    public Object data;

    /**
     * Creates a new DropZone with an invalid bounds rectangle and a null data object.
     */
    public DropZone() {
    }

    /**
     * Creates a new DropZone with a copy of the bounds rectangle and the given data object.
     */
    public DropZone(Rect bounds, Object data) {
        this.bounds.set(bounds);
        this.data = data;
    }

    @Override
    public String toString() {
        return String.format("DropZone <%s, %s>", bounds, data);
    }
}

