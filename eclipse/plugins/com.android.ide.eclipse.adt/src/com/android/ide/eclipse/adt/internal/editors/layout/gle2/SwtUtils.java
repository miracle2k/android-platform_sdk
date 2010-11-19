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

import com.android.ide.common.api.Rect;

import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Display;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.Raster;

/**
 * Various generic SWT utilities such as image conversion.
 */
public class SwtUtils {
    private SwtUtils() {
    }

    /**
     * Converts an AWT {@link BufferedImage} into an equivalent SWT {@link Image}. Whether
     * the transparency data is transferred is optional, and this method can also apply an
     * alpha adjustment during the conversion.
     *
     * @param display The display where the SWT image will be shown
     * @param awtImage The AWT {@link BufferedImage}
     * @param transferAlpha If true, copy alpha data out of the source image
     * @param globalAlpha If -1, do nothing, otherwise adjust the alpha of the final image
     *            by the given amount in the range [0,255]
     * @return A new SWT {@link Image} with the same contents as the source
     *         {@link BufferedImage}
     */
    public static Image convertToSwt(Display display, BufferedImage awtImage,
            boolean transferAlpha, int globalAlpha) {
        int width = awtImage.getWidth();
        int height = awtImage.getHeight();

        Raster raster = awtImage.getData(new java.awt.Rectangle(width, height));
        int[] imageDataBuffer = ((DataBufferInt) raster.getDataBuffer()).getData();

        ImageData imageData = new ImageData(width, height, 32, new PaletteData(0x00FF0000,
                0x0000FF00, 0x000000FF));

        imageData.setPixels(0, 0, imageDataBuffer.length, imageDataBuffer, 0);

        if (transferAlpha) {
            byte[] alphaData = new byte[height * width];
            for (int y = 0; y < height; y++) {
                byte[] alphaRow = new byte[width];
                for (int x = 0; x < width; x++) {
                    int alpha = awtImage.getRGB(x, y) >>> 24;

                    // We have to multiply in the alpha now since if we
                    // set ImageData.alpha, it will ignore the alphaData.
                    if (globalAlpha != -1) {
                        alpha = alpha * globalAlpha >> 8;
                    }

                    alphaRow[x] = (byte) alpha;
                }
                System.arraycopy(alphaRow, 0, alphaData, y * width, width);
            }

            imageData.alphaData = alphaData;
        } else if (globalAlpha != -1) {
            imageData.alpha = globalAlpha;
        }

        Image image = new Image(display, imageData);
        return image;
    }

    /**
     * Converts a direct-color model SWT image to an equivalent AWT image. If the image
     * does not have a supported color model, returns null. This method does <b>NOT</b>
     * preserve alpha in the source image.
     *
     * @param swtImage the SWT image to be converted to AWT
     * @return an AWT image representing the source SWT image
     */
    public static BufferedImage convertToAwt(Image swtImage) {
        ImageData data = swtImage.getImageData();
        BufferedImage awtImage = new BufferedImage(data.width, data.height, BufferedImage.TYPE_INT_ARGB);
        PaletteData palette = data.palette;
        if (palette.isDirect) {
            for (int y = 0; y < data.height; y++) {
                for (int x = 0; x < data.width; x++) {
                  int pixel = data.getPixel(x, y);
                  awtImage.setRGB(x, y, 0xFF000000 | pixel);
                }
            }
        } else {
            return null;
        }

        return awtImage;
    }

    /**
     * Converts the given SWT {@link Rectangle} into an ADT {@link Rect}
     *
     * @param swtRect the SWT {@link Rectangle}
     * @return an equivalent {@link Rect}
     */
    public static Rect toRect(Rectangle swtRect) {
        return new Rect(swtRect.x, swtRect.y, swtRect.width, swtRect.height);
    }

    /**
     * Sets the values of the given ADT {@link Rect} to the values of the given SWT
     * {@link Rectangle}
     *
     * @param target the ADT {@link Rect} to modify
     * @param source the SWT {@link Rectangle} to read values from
     */
    public static void set(Rect target, Rectangle source) {
        target.set(source.x, source.y, source.width, source.height);
    }

    /**
     * Compares an ADT {@link Rect} with an SWT {@link Rectangle} and returns true if they
     * are equivalent
     *
     * @param r1 the ADT {@link Rect}
     * @param r2 the SWT {@link Rectangle}
     * @return true if the two rectangles are equivalent
     */
    public static boolean equals(Rect r1, Rectangle r2) {
        return r1.x == r2.x && r1.y == r2.y && r1.w == r2.width && r1.h == r2.height;

    }
}
