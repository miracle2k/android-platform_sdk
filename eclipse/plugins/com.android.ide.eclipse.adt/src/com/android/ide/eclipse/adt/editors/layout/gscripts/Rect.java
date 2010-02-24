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
 * Mutable rectangle bounds.
 * <p/>
 * To be valid, w >= 1 and h >= 1.
 * By definition:
 * - right side = x + w - 1.
 * - bottom side = y + h - 1.
 */
public class Rect {
    public int x, y, w, h;

    /** Initialize an invalid rectangle. */
    public Rect() {
    }

    /** Initialize rectangle to the given values. They can be invalid. */
    public Rect(int x, int y, int w, int h) {
        set(x, y, w, h);
    }

    /** Initialize rectangle to the given values. They can be invalid. */
    public void set(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    /** Initialize rectangle to match the given one. */
    public void set(Rect r) {
        x = r.x;
        y = r.y;
        w = r.w;
        h = r.h;
    }

    /** Returns a new instance of a rectangle with the same values. */
    public Rect copy() {
        return new Rect(x, y, w, h);
    }

    /** Returns true if the rectangle has valid bounds, i.e. w>0 and h>0. */
    public boolean isValid() {
        return w > 0 && h > 0;
    }

    /** Returns true if the rectangle contains the x,y coordinates, borders included. */
    public boolean contains(int x, int y) {
        return isValid() &&
            x >= this.x &&
            y >= this.y &&
            x < (this.x + this.w) &&
            y < (this.y + this.h);
    }

    @Override
    public String toString() {
        return String.format("Rect [%dx%d - %dx%d]", x, y, w, h);
    }
}
