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

package com.android.ide.common.api;



/**
 * Mutable rectangle bounds.
 * <p/>
 * To be valid, w >= 1 and h >= 1.
 * By definition:
 * - right side = x + w - 1.
 * - bottom side = y + h - 1.
 * <p>
 * <b>NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.</b>
 * </p>
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
    public Rect(Rect r) {
        set(r);
    }

    /** Initialize rectangle to the given values. They can be invalid. */
    public Rect set(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        return this;
    }

    /** Initialize rectangle to match the given one. */
    public Rect set(Rect r) {
        set(r.x, r.y, r.w, r.h);
        return this;
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

    /** Returns true if the rectangle contains the x,y coordinates, borders included. */
    public boolean contains(Point p) {
        return p != null && contains(p.x, p.y);
    }

    /**
     * Moves this rectangle by setting it's x,y coordinates to the new values.
     * @return Returns self, for chaining.
     */
    public Rect moveTo(int x, int y) {
        this.x = x;
        this.y = y;
        return this;
    }

    /**
     * Offsets this rectangle by adding the given x,y deltas to the x,y coordinates.
     * @return Returns self, for chaining.
     */
    public Rect offsetBy(int x, int y) {
        this.x += x;
        this.y += y;
        return this;
    }

    public Point getCenter() {
        return new Point(x + (w > 0 ? w / 2 : 0),
                         y + (h > 0 ? h / 2 : 0));
    }

    public Point getTopLeft() {
        return new Point(x, y);
    }

    public Point getBottomLeft() {
        return new Point(x,
                         y + (h > 0 ? h : 0));
    }

    public Point getTopRight() {
        return new Point(x + (w > 0 ? w : 0),
                         y);
    }

    public Point getBottomRight() {
        return new Point(x + (w > 0 ? w : 0),
                         y + (h > 0 ? h : 0));
    }

    @Override
    public String toString() {
        return String.format("Rect [%dx%d - %dx%d]", x, y, w, h);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Rect) {
            Rect rhs = (Rect) obj;
            // validity must be equal on both sides.
            if (isValid() != rhs.isValid()) {
                return false;
            }
            // an invalid rect is equal to any other invalid rect regardless of coordinates
            if (!isValid() && !rhs.isValid()) {
                return true;
            }

            return this.x == rhs.x && this.y == rhs.y && this.w == rhs.w && this.h == rhs.h;
        }

        return false;
    }

    @Override
    public int hashCode() {
        int hc = x;
        hc ^= ((y >>  8) & 0x0FFFFFF) | ((y & 0x00000FF) << 24);
        hc ^= ((w >> 16) & 0x000FFFF) | ((w & 0x000FFFF) << 16);
        hc ^= ((h >> 24) & 0x00000FF) | ((h & 0x0FFFFFF) <<  8);
        return hc;
    }
}
