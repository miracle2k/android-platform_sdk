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
import org.eclipse.swt.widgets.Display;

import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.Raster;

/**
 * Various generic SWT utilities such as image conversion and cropping.
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
    public static Image convertImage(Display display, BufferedImage awtImage,
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
     * Crops blank pixels from the edges of the image and returns the cropped result. We
     * crop off pixels that are blank (meaning they have an alpha value = 0). Note that
     * this is not the same as pixels that aren't opaque (an alpha value other than 255).
     *
     * @param image the image to be cropped
     * @param initialCrop If not null, specifies a rectangle which contains an initial
     *            crop to continue. This can be used to crop an image where you already
     *            know about margins in the image
     * @return a cropped version of the source image, or null if the whole image was blank
     *         and cropping completely removed everything
     */
    public static BufferedImage cropBlank(BufferedImage image, Rect initialCrop) {
        CropFilter filter = new CropFilter() {
            public boolean crop(BufferedImage bufferedImage, int x, int y) {
                int rgb = bufferedImage.getRGB(x, y);
                return (rgb & 0xFF000000) == 0x00000000;
                // TODO: Do a threshold of 80 instead of just 0? Might give better
                // visual results -- e.g. check <= 0x80000000
            }
        };
        return crop(image, filter, initialCrop);
    }

    /**
     * Crops pixels of a given color from the edges of the image and returns the cropped
     * result.
     *
     * @param image the image to be cropped
     * @param blankRgba the color considered to be blank, as a 32 pixel integer with 8
     *            bits of alpha, red, green and blue
     * @param initialCrop If not null, specifies a rectangle which contains an initial
     *            crop to continue. This can be used to crop an image where you already
     *            know about margins in the image
     * @return a cropped version of the source image, or null if the whole image was blank
     *         and cropping completely removed everything
     */
    public static BufferedImage cropColor(BufferedImage image,
            final int blankRgba, Rect initialCrop) {
        CropFilter filter = new CropFilter() {
            public boolean crop(BufferedImage bufferedImage, int x, int y) {
                return blankRgba == bufferedImage.getRGB(x, y);
            }
        };
        return crop(image, filter, initialCrop);
    }

    /**
     * Interface implemented by cropping functions that determine whether
     * a pixel should be cropped or not.
     */
    private static interface CropFilter {
        /**
         * Returns true if the pixel is should be cropped.
         *
         * @param image the image containing the pixel in question
         * @param x the x position of the pixel
         * @param y the y position of the pixel
         * @return true if the pixel should be cropped (for example, is blank)
         */
        boolean crop(BufferedImage image, int x, int y);
    }

    private static BufferedImage crop(BufferedImage image, CropFilter filter, Rect initialCrop) {
        if (image == null) {
            return null;
        }

        // First, determine the dimensions of the real image within the image
        int x1, y1, x2, y2;
        if (initialCrop != null) {
            x1 = initialCrop.x;
            y1 = initialCrop.y;
            x2 = initialCrop.x + initialCrop.w;
            y2 = initialCrop.y + initialCrop.h;
        } else {
            x1 = 0;
            y1 = 0;
            x2 = image.getWidth();
            y2 = image.getHeight();
        }

        // Nothing left to crop
        if (x1 == x2 || y1 == y2) {
            return null;
        }

        // This algorithm is a bit dumb -- it just scans along the edges looking for
        // a pixel that shouldn't be cropped. I could maybe try to make it smarter by
        // for example doing a binary search to quickly eliminate large empty areas to
        // the right and bottom -- but this is slightly tricky with components like the
        // AnalogClock where I could accidentally end up finding a blank horizontal or
        // vertical line somewhere in the middle of the rendering of the clock, so for now
        // we do the dumb thing -- not a big deal since we tend to crop reasonably
        // small images.

        // First determine top edge
        topEdge: for (; y1 < y2; y1++) {
            for (int x = x1; x < x2; x++) {
                if (!filter.crop(image, x, y1)) {
                    break topEdge;
                }
            }
        }

        if (y1 == image.getHeight()) {
            // The image is blank
            return null;
        }

        // Next determine left edge
        leftEdge: for (; x1 < x2; x1++) {
            for (int y = y1; y < y2; y++) {
                if (!filter.crop(image, x1, y)) {
                    break leftEdge;
                }
            }
        }

        // Next determine right edge
        rightEdge: for (; x2 > x1; x2--) {
            for (int y = y1; y < y2; y++) {
                if (!filter.crop(image, x2 - 1, y)) {
                    break rightEdge;
                }
            }
        }

        // Finally determine bottom edge
        bottomEdge: for (; y2 > y1; y2--) {
            for (int x = x1; x < x2; x++) {
                if (!filter.crop(image, x, y2 - 1)) {
                    break bottomEdge;
                }
            }
        }

        // No need to crop?
        if (x1 == 0 && y1 == 0 && x2 == image.getWidth() && y2 == image.getHeight()) {
            return image;
        }

        if (x1 == x2 || y1 == y2) {
            // Nothing left after crop -- blank image
            return null;
        }

        // Now extract the sub-image
        BufferedImage cropped = new BufferedImage(x2 - x1, y2 - y1, image.getType());
        Graphics g = cropped.getGraphics();
        g.drawImage(image, 0, 0, x2 - x1, y2 - y1, x1, y1, x2, y2, null);
        g.dispose();

        return cropped;
    }
}
