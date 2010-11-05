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

package com.android.ide.eclipse.adt.internal.editors.layout.gle2;

import org.eclipse.swt.graphics.Device;
import org.eclipse.swt.graphics.GC;

/**
 * An Overlay is a set of graphics which can be painted on top of the visual
 * editor. Different {@link Gesture}s produce context specific overlays, such as
 * swiping rectangles from the {@link MarqueeGesture} and guidelines from the
 * {@link MoveGesture}.
 */
public abstract class Overlay {
    /**
     * Construct the overlay, using the given graphics context for painting.
     */
    public Overlay() {
        super();
    }

    /**
     * Initializes the overlay before the first use, if applicable. This is a
     * good place to initialize resources like colors.
     *
     * @param device The device to allocate resources for; the parameter passed
     *            to {@link #paint} will correspond to this device.
     */
    public void create(Device device) {
    }

    /**
     * Releases resources held by the overlay. Called by the editor when an
     * overlay has been removed.
     */
    public void dispose() {
    }

    /**
     * Paints the overlay.
     *
     * @param gc The SWT {@link GC} object to draw into.
     */
    public void paint(GC gc) {
        throw new IllegalArgumentException("paint() not implemented, probably done "
                + "with specialized paint signature");
    }
}
