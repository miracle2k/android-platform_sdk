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

import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Device;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Rectangle;

/**
 * The {@link HoverOverlay} paints an optional hover on top of the layout,
 * highlighting the currently hovered view.
 */
public class HoverOverlay extends Overlay {
    /** Hover border color. Must be disposed, it's NOT a system color. */
    private Color mHoverStrokeColor;

    /** Hover fill color. Must be disposed, it's NOT a system color. */
    private Color mHoverFillColor;

    /** Vertical scaling & scrollbar information. */
    private ScaleInfo mVScale;

    /** Horizontal scaling & scrollbar information. */
    private ScaleInfo mHScale;

    /**
     * Current mouse hover border rectangle. Null when there's no mouse hover.
     * The rectangle coordinates do not take account of the translation, which
     * must be applied to the rectangle when drawing.
     */
    private Rectangle mHoverRect;

    /**
     * Constructs a new {@link HoverOverlay} linked to the given view hierarchy.
     *
     * @param hScale The {@link ScaleInfo} to use to transfer horizontal layout
     *            coordinates to screen coordinates.
     * @param vScale The {@link ScaleInfo} to use to transfer vertical layout
     *            coordinates to screen coordinates.
     */
    public HoverOverlay(ScaleInfo hScale, ScaleInfo vScale) {
        super();
        this.mHScale = hScale;
        this.mVScale = vScale;
    }

    @Override
    public void create(Device device) {
        if (SwtDrawingStyle.HOVER.getStrokeColor() != null) {
            mHoverStrokeColor = new Color(device, SwtDrawingStyle.HOVER.getStrokeColor());
        }
        if (SwtDrawingStyle.HOVER.getFillColor() != null) {
            mHoverFillColor = new Color(device, SwtDrawingStyle.HOVER.getFillColor());
        }
    }

    @Override
    public void dispose() {
        if (mHoverStrokeColor != null) {
            mHoverStrokeColor.dispose();
            mHoverStrokeColor = null;
        }

        if (mHoverFillColor != null) {
            mHoverFillColor.dispose();
            mHoverFillColor = null;
        }
    }

    /**
     * Sets the hover rectangle. The coordinates of the rectangle are in layout
     * coordinates. The recipient is will own this rectangle.
     * <p/>
     * TODO: Consider switching input arguments to two {@link LayoutPoint}s so
     * we don't have ambiguity about the coordinate system of these input
     * parameters.
     * <p/>
     *
     * @param x The top left x coordinate, in layout coordinates, of the hover.
     * @param y The top left y coordinate, in layout coordinates, of the hover.
     * @param w The width of the hover (in layout coordinates).
     * @param h The height of the hover (in layout coordinates).
     */
    public void setHover(int x, int y, int w, int h) {
        mHoverRect = new Rectangle(x, y, w, h);
    }

    /**
     * Removes the hover for the next paint.
     */
    public void clearHover() {
        mHoverRect = null;
    }

    @Override
    public void paint(GC gc) {
        if (mHoverRect != null) {
            // Translate the hover rectangle (in canvas coordinates) to control
            // coordinates
            int x = mHScale.translate(mHoverRect.x);
            int y = mVScale.translate(mHoverRect.y);
            int w = mHScale.scale(mHoverRect.width);
            int h = mVScale.scale(mHoverRect.height);

            if (mHoverStrokeColor != null) {
                int oldAlpha = gc.getAlpha();
                gc.setForeground(mHoverStrokeColor);
                gc.setLineStyle(SwtDrawingStyle.HOVER.getLineStyle());
                gc.setAlpha(SwtDrawingStyle.HOVER.getStrokeAlpha());
                gc.drawRectangle(x, y, w, h);
                gc.setAlpha(oldAlpha);
            }

            if (mHoverFillColor != null) {
                int oldAlpha = gc.getAlpha();
                gc.setAlpha(SwtDrawingStyle.HOVER.getFillAlpha());
                gc.setBackground(mHoverFillColor);
                gc.fillRectangle(x, y, w, h);
                gc.setAlpha(oldAlpha);
            }
        }
    }
}
