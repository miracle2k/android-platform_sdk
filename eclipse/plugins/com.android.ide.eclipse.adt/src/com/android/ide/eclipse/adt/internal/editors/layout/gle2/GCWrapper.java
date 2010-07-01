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

import com.android.ide.eclipse.adt.editors.layout.gscripts.IColor;
import com.android.ide.eclipse.adt.editors.layout.gscripts.IGraphics;
import com.android.ide.eclipse.adt.editors.layout.gscripts.IViewRule;
import com.android.ide.eclipse.adt.editors.layout.gscripts.Point;
import com.android.ide.eclipse.adt.editors.layout.gscripts.Rect;

import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.FontMetrics;
import org.eclipse.swt.graphics.GC;

import java.util.HashMap;

/**
 * Wraps an SWT {@link GC} into an {@link IGraphics} interface so that {@link IViewRule} objects
 * can directly draw on the canvas.
 * </p>
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

    public void setForeground(IColor color) {
        checkGC();
        getGc().setForeground(((ColorWrapper) color).getColor());
    }

    public void setBackground(IColor color) {
        checkGC();
        getGc().setBackground(((ColorWrapper) color).getColor());
    }

    public boolean setAlpha(int alpha) {
        checkGC();
        try {
            getGc().setAlpha(alpha);
            return true;
        } catch (SWTException e) {
            return false;
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
        int x = mHScale.translate(r.x);
        int y = mVScale.translate(r.y);
        int w = mHScale.scale(r.w);
        int h = mVScale.scale(r.h);
        getGc().drawRectangle(x, y, w, h);
    }

    public void fillRect(int x1, int y1, int x2, int y2) {
        checkGC();
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
        int x = mHScale.translate(r.x);
        int y = mVScale.translate(r.y);
        int w = mHScale.scale(r.w);
        int h = mVScale.scale(r.h);
        getGc().fillRectangle(x, y, w, h);
    }

    // circles (actually ovals)

    public void drawOval(int x1, int y1, int x2, int y2) {
        checkGC();
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
        int x = mHScale.translate(r.x);
        int y = mVScale.translate(r.y);
        int w = mHScale.scale(r.w);
        int h = mVScale.scale(r.h);
        getGc().drawOval(x, y, w, h);
    }

    public void fillOval(int x1, int y1, int x2, int y2) {
        checkGC();
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
        int x = mHScale.translate(r.x);
        int y = mVScale.translate(r.y);
        int w = mHScale.scale(r.w);
        int h = mVScale.scale(r.h);
        getGc().fillOval(x, y, w, h);
    }


    // strings

    public void drawString(String string, int x, int y) {
        checkGC();
        x = mHScale.translate(x);
        y = mVScale.translate(y);
        getGc().drawString(string, x, y, true /*isTransparent*/);
    }

    public void drawString(String string, Point topLeft) {
        drawString(string, topLeft.x, topLeft.y);
    }
}
