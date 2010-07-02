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

public interface ICanvasTransform {
    /**
     * Margin around the rendered image.
     * Should be enough space to display the layout width and height pseudo widgets.
     */
    public static final int IMAGE_MARGIN = 25;

    /**
     * Computes the transformation from a X/Y canvas image coordinate
     * to client pixel coordinate.
     * <p/>
     * This takes into account the {@link #IMAGE_MARGIN},
     * the current scaling and the current translation.
     *
     * @param canvasX A canvas image coordinate (X or Y).
     * @return The transformed coordinate in client pixel space.
     */
    public int translate(int canvasX);

    /**
     * Computes the transformation from a canvas image size (width or height) to
     * client pixel coordinates.
     *
     * @param canwasW A canvas image size (W or H).
     * @return The transformed coordinate in client pixel space.
     */
    public int scale(int canwasW);

    /**
     * Computes the transformation from a X/Y client pixel coordinate
     * to canvas image coordinate.
     * <p/>
     * This takes into account the {@link #IMAGE_MARGIN},
     * the current scaling and the current translation.
     * <p/>
     * This is the inverse of {@link #translate(int)}.
     *
     * @param screenX A client pixel space coordinate (X or Y).
     * @return The transformed coordinate in canvas image space.
     */
    public int inverseTranslate(int screenX);
}
