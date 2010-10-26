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

import com.android.ide.common.api.DrawingStyle;
import com.android.ide.common.api.IColor;
import com.android.ide.common.api.IGraphics;
import com.android.ide.common.api.IViewRule;
import com.android.ide.common.api.Point;
import com.android.ide.common.api.Rect;

import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.FontMetrics;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.RGB;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Wraps an SWT {@link GC} into an {@link IGraphics} interface so that {@link IViewRule} objects
 * can directly draw on the canvas.
 * <p/>
 * The actual wrapped GC object is only non-null during the context of a paint operation.
 */
public class GCWrapper implements IGraphics {

    /**
     * The actual SWT {@link GC} being wrapped. This can change during the lifetime of the
     * object. It is generally set to something during an onPaint method and then changed
     * to null when not in the context of a paint.
     */
    private GC mGc;

    /**
     * Current style being used for drawing.
     */
    private SwtDrawingStyle mCurrentStyle = SwtDrawingStyle.INVALID;

    /**
     * Implementation of IColor wrapping an SWT color.
     */
    private class ColorWrapper implements IColor {
        private final Color mColor;

        public ColorWrapper(Color color) {
            mColor = color;
        }

        public Color getColor() {
            return mColor;
        }
    }

    /** A map of registered colors. All these colors must be disposed at the end. */
    private final HashMap<Integer, ColorWrapper> mColorMap = new HashMap<Integer, ColorWrapper>();

    /**
     * A map of the {@link SwtDrawingStyle} stroke colors that we have actually
     * used (to be disposed)
     */
    private final Map<DrawingStyle, Color> mStyleStrokeMap = new EnumMap<DrawingStyle, Color>(
            DrawingStyle.class);

    /**
     * A map of the {@link SwtDrawingStyle} fill colors that we have actually
     * used (to be disposed)
     */
    private final Map<DrawingStyle, Color> mStyleFillMap = new EnumMap<DrawingStyle, Color>(
            DrawingStyle.class);

    /** The cached pixel height of the default current font. */
    private int mFontHeight = 0;

    /** The scaling of the canvas in X. */
    private final ICanvasTransform mHScale;
    /** The scaling of the canvas in Y. */
    private final ICanvasTransform mVScale;

    public GCWrapper(ICanvasTransform hScale, ICanvasTransform vScale) {
        mHScale = hScale;
        mVScale = vScale;
        mGc = null;
    }

    void setGC(GC gc) {
        mGc = gc;
    }

    private GC getGc() {
        return mGc;
    }

    void checkGC() {
        if (mGc == null) {
            throw new RuntimeException("IGraphics used without a valid context.");
        }
    }

    void dispose() {
        for (ColorWrapper c : mColorMap.values()) {
            c.getColor().dispose();
        }
        mColorMap.clear();

        for (Color c : mStyleStrokeMap.values()) {
            c.dispose();
        }
        mStyleStrokeMap.clear();

        for (Color c : mStyleFillMap.values()) {
            c.dispose();
        }
        mStyleFillMap.clear();
    }

    //-------------

    public IColor registerColor(int rgb) {
        checkGC();

        Integer key = Integer.valueOf(rgb);
        ColorWrapper c = mColorMap.get(key);
        if (c == null) {
            c = new ColorWrapper(new Color(getGc().getDevice(),
                    (rgb >> 16) & 0xFF,
                    (rgb >>  8) & 0xFF,
                    (rgb >>  0) & 0xFF));
            mColorMap.put(key, c);
        }

        return c;
    }

    /** Returns the (cached) pixel height of the current font. */
    public int getFontHeight() {
        if (mFontHeight < 1) {
            checkGC();
            FontMetrics fm = getGc().getFontMetrics();
            mFontHeight = fm.getHeight();
        }
        return mFontHeight;
    }

    public IColor getForeground() {
        Color c = getGc().getForeground();
        return new ColorWrapper(c);
    }

    public IColor getBackground() {
        Color c = getGc().getBackground();
        return new ColorWrapper(c);
    }

    public int getAlpha() {
        return getGc().getAlpha();
    }

    public void setForeground(IColor color) {
        checkGC();
        getGc().setForeground(((ColorWrapper) color).getColor());
    }

    public void setBackground(IColor color) {
        checkGC();
        getGc().setBackground(((ColorWrapper) color).getColor());
    }

    public void setAlpha(int alpha) {
        checkGC();
        try {
            getGc().setAlpha(alpha);
        } catch (SWTException e) {
            // This means that we cannot set the alpha on this platform; this is
            // an acceptable no-op.
        }
    }

    public void setLineStyle(LineStyle style) {
        int swtStyle = 0;
        switch (style) {
        case LINE_SOLID:
            swtStyle = SWT.LINE_SOLID;
            break;
        case LINE_DASH:
            swtStyle = SWT.LINE_DASH;
            break;
        case LINE_DOT:
            swtStyle = SWT.LINE_DOT;
            break;
        case LINE_DASHDOT:
            swtStyle = SWT.LINE_DASHDOT;
            break;
        case LINE_DASHDOTDOT:
            swtStyle = SWT.LINE_DASHDOTDOT;
            break;
        default:
            assert false : style;
            break;
        }

        if (swtStyle != 0) {
            checkGC();
            getGc().setLineStyle(swtStyle);
        }
    }

    public void setLineWidth(int width) {
        checkGC();
        if (width > 0) {
            getGc().setLineWidth(width);
        }
    }

    // lines

    public void drawLine(int x1, int y1, int x2, int y2) {
        checkGC();
        useStrokeAlpha();
        x1 = mHScale.translate(x1);
        y1 = mVScale.translate(y1);
        x2 = mHScale.translate(x2);
        y2 = mVScale.translate(y2);
        getGc().drawLine(x1, y1, x2, y2);
    }

    public void drawLine(Point p1, Point p2) {
        drawLine(p1.x, p1.y, p2.x, p2.y);
    }

    // rectangles

    public void drawRect(int x1, int y1, int x2, int y2) {
        checkGC();
        useStrokeAlpha();
        int x = mHScale.translate(x1);
        int y = mVScale.translate(y1);
        int w = mHScale.scale(x2 - x1);
        int h = mVScale.scale(y2 - y1);
        getGc().drawRectangle(x, y, w, h);
    }

    public void drawRect(Point p1, Point p2) {
        drawRect(p1.x, p1.y, p2.x, p2.y);
    }

    public void drawRect(Rect r) {
        checkGC();
        useStrokeAlpha();
        int x = mHScale.translate(r.x);
        int y = mVScale.translate(r.y);
        int w = mHScale.scale(r.w);
        int h = mVScale.scale(r.h);
        getGc().drawRectangle(x, y, w, h);
    }

    public void fillRect(int x1, int y1, int x2, int y2) {
        checkGC();
        useFillAlpha();
        int x = mHScale.translate(x1);
        int y = mVScale.translate(y1);
        int w = mHScale.scale(x2 - x1);
        int h = mVScale.scale(y2 - y1);
        getGc().fillRectangle(x, y, w, h);
    }

    public void fillRect(Point p1, Point p2) {
        fillRect(p1.x, p1.y, p2.x, p2.y);
    }

    public void fillRect(Rect r) {
        checkGC();
        useFillAlpha();
        int x = mHScale.translate(r.x);
        int y = mVScale.translate(r.y);
        int w = mHScale.scale(r.w);
        int h = mVScale.scale(r.h);
        getGc().fillRectangle(x, y, w, h);
    }

    // circles (actually ovals)

    public void drawOval(int x1, int y1, int x2, int y2) {
        checkGC();
        useStrokeAlpha();
        int x = mHScale.translate(x1);
        int y = mVScale.translate(y1);
        int w = mHScale.scale(x2 - x1);
        int h = mVScale.scale(y2 - y1);
        getGc().drawOval(x, y, w, h);
    }

    public void drawOval(Point p1, Point p2) {
        drawOval(p1.x, p1.y, p2.x, p2.y);
    }

    public void drawOval(Rect r) {
        checkGC();
        useStrokeAlpha();
        int x = mHScale.translate(r.x);
        int y = mVScale.translate(r.y);
        int w = mHScale.scale(r.w);
        int h = mVScale.scale(r.h);
        getGc().drawOval(x, y, w, h);
    }

    public void fillOval(int x1, int y1, int x2, int y2) {
        checkGC();
        useFillAlpha();
        int x = mHScale.translate(x1);
        int y = mVScale.translate(y1);
        int w = mHScale.scale(x2 - x1);
        int h = mVScale.scale(y2 - y1);
        getGc().fillOval(x, y, w, h);
    }

    public void fillOval(Point p1, Point p2) {
        fillOval(p1.x, p1.y, p2.x, p2.y);
    }

    public void fillOval(Rect r) {
        checkGC();
        useFillAlpha();
        int x = mHScale.translate(r.x);
        int y = mVScale.translate(r.y);
        int w = mHScale.scale(r.w);
        int h = mVScale.scale(r.h);
        getGc().fillOval(x, y, w, h);
    }


    // strings

    public void drawString(String string, int x, int y) {
        checkGC();
        useStrokeAlpha();
        x = mHScale.translate(x);
        y = mVScale.translate(y);
        // Background fill of text is not useful because it does not
        // use the alpha; we instead supply a separate method (drawBoxedStrings) which
        // first paints a semi-transparent mask for the text to sit on
        // top of (this ensures that the text is readable regardless of
        // colors of the pixels below the text)
        getGc().drawString(string, x, y, true /*isTransparent*/);
    }

    public void drawBoxedStrings(int x, int y, List<?> strings) {
        checkGC();

        x = mHScale.translate(x);
        y = mVScale.translate(y);

        // Compute bounds of the box by adding up the sum of the text heights
        // and the max of the text widths
        int width = 0;
        int height = 0;
        int lineHeight = getGc().getFontMetrics().getHeight();
        for (Object s : strings) {
            org.eclipse.swt.graphics.Point extent = getGc().stringExtent(s.toString());
            height += extent.y;
            width = Math.max(width, extent.x);
        }

        // Paint a box below the text
        int padding = 2;
        useFillAlpha();
        getGc().fillRectangle(x - padding, y - padding, width + 2 * padding, height + 2 * padding);

        // Finally draw strings on top
        useStrokeAlpha();
        int lineY = y;
        for (Object s : strings) {
            getGc().drawString(s.toString(), x, lineY, true /* isTransparent */);
            lineY += lineHeight;
        }
    }

    public void drawString(String string, Point topLeft) {
        drawString(string, topLeft.x, topLeft.y);
    }

    // Styles

    public void useStyle(DrawingStyle style) {
        checkGC();

        // Look up the specific SWT style which defines the actual
        // colors and attributes to be used for the logical drawing style.
        SwtDrawingStyle swtStyle = SwtDrawingStyle.of(style);
        RGB stroke = swtStyle.getStrokeColor();
        if (stroke != null) {
            Color color = getStrokeColor(style, stroke);
            mGc.setForeground(color);
        }
        RGB fill = swtStyle.getFillColor();
        if (fill != null) {
            Color color = getFillColor(style, fill);
            mGc.setBackground(color);
        }
        mGc.setLineWidth(swtStyle.getLineWidth());
        mGc.setLineStyle(swtStyle.getLineStyle());
        if (swtStyle.getLineStyle() == SWT.LINE_CUSTOM) {
            mGc.setLineDash(new int[] {
                    8, 4
            });
        }
        mCurrentStyle = swtStyle;
    }

    /** Uses the stroke alpha for subsequent drawing operations. */
    private void useStrokeAlpha() {
        mGc.setAlpha(mCurrentStyle.getStrokeAlpha());
    }

    /** Uses the fill alpha for subsequent drawing operations. */
    private void useFillAlpha() {
        mGc.setAlpha(mCurrentStyle.getFillAlpha());
    }

    /**
     * Get the SWT stroke color (foreground/border) to use for the given style,
     * using the provided color description if we haven't seen this color yet.
     * The color will also be placed in the {@link #mStyleStrokeMap} such that
     * it can be disposed of at cleanup time.
     *
     * @param style The drawing style for which we want a color
     * @param defaultColorDesc The RGB values to initialize the color to if we
     *            haven't seen this color before
     * @return The color object
     */
    private Color getStrokeColor(DrawingStyle style, RGB defaultColorDesc) {
        return getStyleColor(style, defaultColorDesc, mStyleStrokeMap);
    }

    /**
     * Get the SWT fill (background/interior) color to use for the given style,
     * using the provided color description if we haven't seen this color yet.
     * The color will also be placed in the {@link #mStyleStrokeMap} such that
     * it can be disposed of at cleanup time.
     *
     * @param style The drawing style for which we want a color
     * @param defaultColorDesc The RGB values to initialize the color to if we
     *            haven't seen this color before
     * @return The color object
     */
    private Color getFillColor(DrawingStyle style, RGB defaultColorDesc) {
        return getStyleColor(style, defaultColorDesc, mStyleFillMap);
    }

    /**
     * Get the SWT color to use for the given style, using the provided color
     * description if we haven't seen this color yet. The color will also be
     * placed in the map referenced by the map parameter such that it can be
     * disposed of at cleanup time.
     *
     * @param style The drawing style for which we want a color
     * @param defaultColorDesc The RGB values to initialize the color to if we
     *            haven't seen this color before
     * @param map The color map to use
     * @return The color object
     */
    private Color getStyleColor(DrawingStyle style, RGB defaultColorDesc,
            Map<DrawingStyle, Color> map) {
        Color color = map.get(style);
        if (color == null) {
            color = new Color(getGc().getDevice(), defaultColorDesc);
            map.put(style, color);
        }

        return color;
    }
}
