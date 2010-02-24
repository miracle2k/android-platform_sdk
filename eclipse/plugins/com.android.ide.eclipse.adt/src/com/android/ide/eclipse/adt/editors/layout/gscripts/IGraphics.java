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

package com.android.ide.eclipse.adt.editors.layout.gscripts;

/**
 * Represents a graphical context that rules can use to draw on the canvas.
 * <p/>
 * The wrapper GC is only valid during the context of a paint operation.
 * This means {@link IViewRule}s should not cache this object and call it at
 * just about any time, it is only valid during a call that actually receives
 * the GC wrapper.
 */
public interface IGraphics {

    /**
     * Registers a color using 0x00rrggbb where each component is
     * 0..0xFF.
     * <p/>
     * Transparency is handled separately using {@link #setAlpha(int)}.
     * <p/>
     * If the same color is registered twice, the same object will
     * be returned.
     */
    IColor registerColor(int rgb);

    /**
     * Returns the height, in pixels, of the default font.
     */
    int getFontHeight();

    /**
     * Sets the foreground color. The foreground color is used
     * for drawing operations including when text is drawn.
     */
    void setForeground(IColor color);

    /**
     * Sets the background color. The background color is used
     * for fill operations.
     */
    void setBackground(IColor color);

    /**
     * Sets the receiver's alpha value which must be
     * between 0 (transparent) and 255 (opaque).
     * <p>
     * This operation requires the operating system's advanced
     * graphics subsystem which may not be available on some
     * platforms.
     *
     * @return False if the GC doesn't support alpha.
     */
    boolean setAlpha(int alpha);

    /**
     * A line style for {@link IGraphics#setLineStyle(LineStyle)}.
     */
    enum LineStyle {
        /** Style for solid lines. */
        LINE_SOLID,
        /** Style for dashed lines. */
        LINE_DASH,
        /** Style for dotted lines. */
        LINE_DOT,
        /** Style for alternating dash-dot lines. */
        LINE_DASHDOT,
        /** Style for dash-dot-dot lines. */
        LINE_DASHDOTDOT
    }

    /**
     * Sets the current line style.
     */
    void setLineStyle(LineStyle style);

    /**
     * Sets the width that will be used when drawing lines.
     * The operation is ignored if <var>width</var> is less than 1.
     */
    void setLineWidth(int width);

    /**
     * Draws a line between 2 points, using the current foreground
     * color and alpha.
     */
    void drawLine(int x1, int y1, int x2, int y2);
    /**
     * Draws a line between 2 points, using the current foreground
     * color and alpha.
     */
    void drawLine(Point p1, Point p2);

    /**
     * Draws a rectangle outline between 2 points, using the current
     * foreground color and alpha.
     */
    void drawRect(int x1, int y1, int x2, int y2);
    /**
     * Draws a rectangle outline between 2 points, using the current
     * foreground color and alpha.
     */
    void drawRect(Point p1, Point p2);
    /**
     * Draws a rectangle outline between 2 points, using the current
     * foreground color and alpha.
     */
    void drawRect(Rect r);

    /**
     * Fills a rectangle outline between 2 points, using the current
     * background color and alpha.
     */
    void fillRect(int x1, int y1, int x2, int y2);
    /**
     * Fills a rectangle outline between 2 points, using the current
     * background color and alpha.
     */
    void fillRect(Point p1, Point p2);
    /**
     * Fills a rectangle outline between 2 points, using the current
     * background color and alpha.
     */
    void fillRect(Rect r);

    /**
     * Draws the given string, using the current foreground color.
     * No tab expansion or carriage return processing will be performed.
     *
     * @param string the string to be drawn.
     * @param x the x coordinate of the top left corner of the text.
     * @param y the y coordinate of the top left corner of the text.
     */
    void drawString(String string, int x, int y);

    /**
     * Draws the given string, using the current foreground color.
     * No tab expansion or carriage return processing will be performed.
     *
     * @param string the string to be drawn.
     * @param topLeft the top left corner of the text.
     */
    void drawString(String string, Point topLeft);
}
