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
package com.android.ide.eclipse.adt.internal.editors.layout.gle2;

import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.ScrollBar;

/**
 * Helper class to convert between control pixel coordinates and canvas coordinates.
 * Takes care of the zooming and offset of the canvas.
 */
public class ScaleInfo implements ICanvasTransform {
    /**
     * The canvas which controls the zooming.
     */
    private final LayoutCanvas mCanvas;

    /** Canvas image size (original, before zoom), in pixels. */
    private int mImgSize;

    /** Client size, in pixels. */
    private int mClientSize;

    /** Left-top offset in client pixel coordinates. */
    private int mTranslate;

    /** Scaling factor, > 0. */
    private double mScale;

    /** Scrollbar widget. */
    private ScrollBar mScrollbar;

    public ScaleInfo(LayoutCanvas layoutCanvas, ScrollBar scrollbar) {
        mCanvas = layoutCanvas;
        mScrollbar = scrollbar;
        mScale = 1.0;
        mTranslate = 0;

        mScrollbar.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                // User requested scrolling. Changes translation and redraw canvas.
                mTranslate = mScrollbar.getSelection();
                ScaleInfo.this.mCanvas.redraw();
            }
        });
    }

    /**
     * Sets the new scaling factor. Recomputes scrollbars.
     * @param scale Scaling factor, > 0.
     */
    public void setScale(double scale) {
        if (mScale != scale) {
            mScale = scale;
            resizeScrollbar();
        }
    }

    /**
     * Returns current scaling factor.
     *
     * @return The current scaling factor
     */
    public double getScale() {
        return mScale;
    }

    /**
     * Returns Canvas image size (original, before zoom), in pixels.
     *
     * @return Canvas image size (original, before zoom), in pixels
     */
    public int getImgSize() {
        return mImgSize;
    }

    /**
     * Returns the scaled image size in pixels.
     *
     * @return The scaled image size in pixels.
     */
    public int getScalledImgSize() {
        return (int) (mImgSize * mScale);
    }

    /** Changes the size of the canvas image and the client size. Recomputes scrollbars. */
    public void setSize(int imgSize, int clientSize) {
        mImgSize = imgSize;
        setClientSize(clientSize);
    }

    /** Changes the size of the client size. Recomputes scrollbars. */
    public void setClientSize(int clientSize) {
        mClientSize = clientSize;
        resizeScrollbar();
    }

    private void resizeScrollbar() {
        // scaled image size
        int sx = (int) (mImgSize * mScale);

        // actual client area is always reduced by the margins
        int cx = mClientSize - 2 * IMAGE_MARGIN;

        if (sx < cx) {
            mScrollbar.setEnabled(false);
        } else {
            mScrollbar.setEnabled(true);

            // max scroll value is the scaled image size
            // thumb value is the actual viewable area out of the scaled img size
            mScrollbar.setMaximum(sx);
            mScrollbar.setThumb(cx);
        }
    }

    public int translate(int canvasX) {
        return IMAGE_MARGIN - mTranslate + (int) (mScale * canvasX);
    }

    public int scale(int canwasW) {
        return (int) (mScale * canwasW);
    }

    public int inverseTranslate(int screenX) {
        return (int) ((screenX - IMAGE_MARGIN + mTranslate) / mScale);
    }
}
