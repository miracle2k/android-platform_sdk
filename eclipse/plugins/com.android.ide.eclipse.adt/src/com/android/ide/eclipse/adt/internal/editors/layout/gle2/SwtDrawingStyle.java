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

import com.android.ide.eclipse.adt.editors.layout.gscripts.DrawingStyle;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.RGB;

/**
 * Description of the drawing styles with specific color, line style and alpha
 * definitions. This class corresponds to the more generic {@link DrawingStyle}
 * class which defines the drawing styles but does not introduce any specific
 * SWT values to the API clients.
 * <p>
 * TODO: This class should eventually pick up theme preferences.
 */
public enum SwtDrawingStyle {
    /**
     * The style used for the border of a selected view
     */
    SELECTION_BORDER(new RGB(0xFF, 0x00, 0x00), null, 1, SWT.LINE_SOLID, 255),

    /**
     * The style used for the interior of a selected view
     */
    SELECTION_FILL(null, new RGB(0xFF, 0x00, 0x00), 1, SWT.LINE_SOLID, 64),

    /**
     * The style used for hovered views (e.g. when the mouse is directly on top
     * of the view)
     */
    HOVER(new RGB(0xFF, 0x99, 0x00), null, 1, SWT.LINE_DOT, 255),

    /**
     * The style used to draw anchors (lines to the other views the given view
     * is anchored to)
     */
    ANCHOR(new RGB(0xFF, 0x00, 0x00), null, 1, SWT.LINE_SOLID, 255),

    /**
     * The style used to draw outlines (the structure of views)
     */
    OUTLINE(new RGB(0x00, 0xFF, 0x00), null, 1, SWT.LINE_DOT, 255),

    /**
     * The style used to draw the recipient/target View of a drop. This is
     * typically going to be the bounding-box of the view into which you are
     * adding a new child.
     */
    DROP_RECIPIENT(new RGB(0xFF, 0x99, 0x00), new RGB(0xFF, 0x99, 0x00), 1, SWT.LINE_SOLID, 255),

    /**
     * The style used to draw a potential drop area <b>within</b> a
     * {@link #DROP_RECIPIENT}. For example, if you are dragging into a view
     * with a LinearLayout, the {@link #DROP_RECIPIENT} will be the view itself,
     * whereas each possible insert position between two children will be a
     * {@link #DROP_ZONE}. If the mouse is over a {@link #DROP_ZONE} it should
     * be drawn using the style {@link #DROP_ZONE_ACTIVE}.
     */
    DROP_ZONE(new RGB(0xFF, 0x99, 0x00), new RGB(0xFF, 0x99, 0x00), 1, SWT.LINE_DOT, 255),

    /**
     * The style used to draw a currently active drop zone within a drop
     * recipient. See the documentation for {@link #DROP_ZONE} for details on
     * the distinction between {@link #DROP_RECIPIENT}, {@link #DROP_ZONE} and
     * {@link #DROP_ZONE_ACTIVE}.
     */
    DROP_ZONE_ACTIVE(new RGB(0xFF, 0x99, 0x00), new RGB(0xFF, 0x99, 0x00), 1, SWT.LINE_SOLID, 255),

    /**
     * The style used to raw illegal/error/invalid markers
     */
    INVALID(new RGB(0x00, 0x00, 0xFF), null, 3, SWT.LINE_SOLID, 255),

    /**
     * A style used for unspecified purposes; can be used by a client to have
     * yet another color that is domain specific; using this color constant
     * rather than your own hardcoded value means that you will be guaranteed to
     * pick up a color that is themed properly and will look decent with the
     * rest of the colors
     */
    CUSTOM1(new RGB(0xFF, 0x00, 0xFF), null, 1, SWT.LINE_SOLID, 255),

    /**
     * A second styled used for unspecified purposes; see {@link #CUSTOM1} for
     * details.
     */
    CUSTOM2(new RGB(0x00, 0xFF, 0xFF), null, 1, SWT.LINE_DOT, 255);

    /**
     * Construct a new style value with the given foreground, background, width,
     * linestyle and transparency.
     *
     * @param fg A color descriptor for the foreground color, or null if no
     *            foreground color should be set
     * @param bg A color descriptor for the background color, or null if no
     *            foreground color should be set
     * @param width The line width, in pixels, or 0 if no line width should be
     *            set
     * @param lineStyle The SWT line style - such as {@link SWT#LINE_SOLID}.
     * @param alpha The alpha value, an integer in the range 0 to 255 where 0 is
     *            fully transparent and 255 is fully opaque.
     */
    private SwtDrawingStyle(RGB fg, RGB bg, int width, int lineStyle, int alpha) {
        mFg = fg;
        mBg = bg;
        mWidth = width;
        mLineStyle = lineStyle;
        mAlpha = alpha;
    }

    /**
     * Return the foreground RGB color description to be used for this style, or
     * null if none
     */
    public RGB getForeground() {
        return mFg;
    }

    /**
     * Return the background RGB color description to be used for this style, or
     * null if none
     */
    public RGB getBackground() {
        return mBg;
    }

    /** Return the line width to be used for this style */
    public int getLineWidth() {
        return mWidth;
    }

    /** Return the SWT line style to be used for this style */
    public int getLineStyle() {
        return mLineStyle;
    }

    /** Return the alpha value (in the range 0,255) to be used for this style */
    public int getAlpha() {
        return mAlpha;
    }

    /**
     * Return the corresponding SwtDrawingStyle for the given
     * {@link DrawingStyle}
     */
    public static SwtDrawingStyle of(DrawingStyle style) {
        switch (style) {
            case SELECTION_BORDER:
                return SELECTION_BORDER;
            case SELECTION_FILL:
                return SELECTION_FILL;
            case HOVER:
                return HOVER;
            case ANCHOR:
                return ANCHOR;
            case OUTLINE:
                return OUTLINE;
            case DROP_ZONE:
                return DROP_ZONE;
            case DROP_ZONE_ACTIVE:
                return DROP_ZONE_ACTIVE;
            case DROP_RECIPIENT:
                return DROP_RECIPIENT;
            case INVALID:
                return INVALID;
            case CUSTOM1:
                return CUSTOM1;
            case CUSTOM2:
                return CUSTOM2;

                // Internal error
            default:
                throw new IllegalArgumentException("Unknown style " + style);
        }
    }

    private final RGB mFg;

    private final RGB mBg;

    private final int mWidth;

    private final int mLineStyle;

    private final int mAlpha;
}
