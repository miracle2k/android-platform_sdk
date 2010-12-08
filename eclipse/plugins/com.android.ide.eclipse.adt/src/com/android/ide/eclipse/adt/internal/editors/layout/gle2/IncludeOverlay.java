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

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.Rectangle;

import java.util.List;

/**
 * The {@link IncludeOverlay} class renders masks to -partially- hide everything outside
 * an included file's own content. This overlay is in use when you are editing an included
 * file shown within a different file's context (e.g. "Show In > other").
 */
public class IncludeOverlay extends Overlay {
    /** Mask transparency - 0 is transparent, 255 is opaque */
    private static final int MASK_TRANSPARENCY = 208;

    /** The associated {@link LayoutCanvas}. */
    private LayoutCanvas mCanvas;

    /**
     * Constructs an {@link IncludeOverlay} tied to the given canvas.
     *
     * @param canvas The {@link LayoutCanvas} to paint the overlay over.
     */
    public IncludeOverlay(LayoutCanvas canvas) {
        this.mCanvas = canvas;
    }

    @Override
    public void paint(GC gc) {
        List<CanvasViewInfo> included = mCanvas.getViewHierarchy().getIncluded();
        if (included == null || included.size() != 1) {
            // We don't support multiple included children yet. When that works,
            // this code should use a BSP tree to figure out which regions to paint
            // to leave holes in the mask.
            return;
        }

        Image image = mCanvas.getImageOverlay().getImage();
        if (image == null) {
            return;
        }
        ImageData data = image.getImageData();

        Rectangle hole = included.get(0).getAbsRect();

        int oldAlpha = gc.getAlpha();
        gc.setAlpha(MASK_TRANSPARENCY);
        Color bg = gc.getDevice().getSystemColor(SWT.COLOR_WIDGET_BACKGROUND);
        gc.setBackground(bg);

        ControlPoint topLeft = LayoutPoint.create(mCanvas, hole.x, hole.y).toControl();
        ControlPoint bottomRight = LayoutPoint.create(mCanvas, hole.x + hole.width,
                hole.y + hole.height).toControl();
        CanvasTransform hi = mCanvas.getHorizontalTransform();
        CanvasTransform vi = mCanvas.getVerticalTransform();
        int deltaX = hi.translate(0);
        int deltaY = vi.translate(0);
        int x1 = topLeft.x;
        int y1 = topLeft.y;
        int x2 = bottomRight.x;
        int y2 = bottomRight.y;
        int width = data.width;
        int height = data.height;

        width = hi.getScalledImgSize();
        height = vi.getScalledImgSize();

        if (y1 > deltaX) {
            // Top
            gc.fillRectangle(deltaX, deltaY, width, y1 - deltaY);
        }

        if (y2 < height) {
            // Bottom
            gc.fillRectangle(deltaX, y2, width, height - y2 + deltaY);
        }

        if (x1 > deltaX) {
            // Left
            gc.fillRectangle(deltaX, y1, x1 - deltaX, y2 - y1);
        }

        if (x2 < width) {
            // Right
            gc.fillRectangle(x2, y1, width - x2 + deltaX, y2 - y1);
        }

        gc.setAlpha(oldAlpha);
    }
}
