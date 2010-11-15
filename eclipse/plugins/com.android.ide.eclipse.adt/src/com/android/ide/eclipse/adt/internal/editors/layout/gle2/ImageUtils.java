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

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

/**
 * Utilities related to image processing.
 */
public class ImageUtils {
    /**
     * Returns true if the given image has no dark pixels
     *
     * @param image the image to be checked for dark pixels
     * @return true if no dark pixels were found
     */
    public static boolean containsDarkPixels(BufferedImage image) {
        for (int y = 0, height = image.getHeight(); y < height; y++) {
            for (int x = 0, width = image.getWidth(); x < width; x++) {
                int pixel = image.getRGB(x, y);
                if ((pixel & 0xFF000000) != 0) {
                    int r = (pixel & 0xFF0000) >> 16;
                    int g = (pixel & 0x00FF00) >> 8;
                    int b = (pixel & 0x0000FF);

                    // One perceived luminance formula is (0.299*red + 0.587*green + 0.114*blue)
                    // In order to keep this fast since we don't need a very accurate
                    // measure, I'll just estimate this with integer math:
                    long brightness = (299L*r + 587*g + 114*b) / 1000;
                    if (brightness < 128) {
                        return true;
                    }
                }
            }
        }
        return false;
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
     * @param blankArgb the color considered to be blank, as a 32 pixel integer with 8
     *            bits of alpha, red, green and blue
     * @param initialCrop If not null, specifies a rectangle which contains an initial
     *            crop to continue. This can be used to crop an image where you already
     *            know about margins in the image
     * @return a cropped version of the source image, or null if the whole image was blank
     *         and cropping completely removed everything
     */
    public static BufferedImage cropColor(BufferedImage image,
            final int blankArgb, Rect initialCrop) {
        CropFilter filter = new CropFilter() {
            public boolean crop(BufferedImage bufferedImage, int x, int y) {
                return blankArgb == bufferedImage.getRGB(x, y);
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

        int width = x2 - x1;
        int height = y2 - y1;

        // Now extract the sub-image
        BufferedImage cropped = new BufferedImage(width, height, image.getType());
        Graphics g = cropped.getGraphics();
        g.drawImage(image, 0, 0, width, height, x1, y1, x2, y2, null);

        g.dispose();

        return cropped;
    }

    /**
     * Creates a drop shadow of a given image and returns a new image which shows the
     * input image on top of its drop shadow.
     *
     * @param source the source image to be shadowed
     * @param shadowSize the size of the shadow in pixels
     * @param shadowOpacity the opacity of the shadow, with 0=transparent and 1=opaque
     * @param shadowRgb the RGB int to use for the shadow color
     * @return a new image with the source image on top of its shadow
     */
    public static BufferedImage createDropShadow(BufferedImage source, int shadowSize,
            float shadowOpacity, int shadowRgb) {

        // This code is based on
        //      http://www.jroller.com/gfx/entry/non_rectangular_shadow

        BufferedImage image = new BufferedImage(source.getWidth() + shadowSize * 2,
                source.getHeight() + shadowSize * 2,
                BufferedImage.TYPE_INT_ARGB);

        Graphics2D g2 = image.createGraphics();
        g2.drawImage(source, null, shadowSize, shadowSize);

        int dstWidth = image.getWidth();
        int dstHeight = image.getHeight();

        int left = (shadowSize - 1) >> 1;
        int right = shadowSize - left;
        int xStart = left;
        int xStop = dstWidth - right;
        int yStart = left;
        int yStop = dstHeight - right;

        shadowRgb = shadowRgb & 0x00FFFFFF;

        int[] aHistory = new int[shadowSize];
        int historyIdx = 0;

        int aSum;

        int[] dataBuffer = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        int lastPixelOffset = right * dstWidth;
        float sumDivider = shadowOpacity / shadowSize;

        // horizontal pass
        for (int y = 0, bufferOffset = 0; y < dstHeight; y++, bufferOffset = y * dstWidth) {
            aSum = 0;
            historyIdx = 0;
            for (int x = 0; x < shadowSize; x++, bufferOffset++) {
                int a = dataBuffer[bufferOffset] >>> 24;
                aHistory[x] = a;
                aSum += a;
            }

            bufferOffset -= right;

            for (int x = xStart; x < xStop; x++, bufferOffset++) {
                int a = (int) (aSum * sumDivider);
                dataBuffer[bufferOffset] = a << 24 | shadowRgb;

                // subtract the oldest pixel from the sum
                aSum -= aHistory[historyIdx];

                // get the latest pixel
                a = dataBuffer[bufferOffset + right] >>> 24;
                aHistory[historyIdx] = a;
                aSum += a;

                if (++historyIdx >= shadowSize) {
                    historyIdx -= shadowSize;
                }
            }
        }
        // vertical pass
        for (int x = 0, bufferOffset = 0; x < dstWidth; x++, bufferOffset = x) {
            aSum = 0;
            historyIdx = 0;
            for (int y = 0; y < shadowSize; y++, bufferOffset += dstWidth) {
                int a = dataBuffer[bufferOffset] >>> 24;
                aHistory[y] = a;
                aSum += a;
            }

            bufferOffset -= lastPixelOffset;

            for (int y = yStart; y < yStop; y++, bufferOffset += dstWidth) {
                int a = (int) (aSum * sumDivider);
                dataBuffer[bufferOffset] = a << 24 | shadowRgb;

                // subtract the oldest pixel from the sum
                aSum -= aHistory[historyIdx];

                // get the latest pixel
                a = dataBuffer[bufferOffset + lastPixelOffset] >>> 24;
                aHistory[historyIdx] = a;
                aSum += a;

                if (++historyIdx >= shadowSize) {
                    historyIdx -= shadowSize;
                }
            }
        }

        g2.drawImage(source, null, 0, 0);
        g2.dispose();

        return image;
    }
}
