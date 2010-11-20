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

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;

import junit.framework.TestCase;

public class SwtUtilsTest extends TestCase {

    public void testImageConvertNoAlpha() throws Exception {
        // Note: We need an TYPE_INT_ARGB SWT image here (instead of TYPE_INT_ARGB_PRE) to
        // prevent the alpha from being pre-multiplied into the RGB when drawing the image.
        BufferedImage inImage = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        Graphics g = inImage.getGraphics();
        g.setColor(new Color(0xAA112233, true));
        g.fillRect(0, 0, inImage.getWidth(), inImage.getHeight());
        g.dispose();

        Shell shell = new Shell();
        Display display = shell.getDisplay();

        // Convert the RGB image, effectively discarding the alpha channel entirely.
        Image outImage = SwtUtils.convertToSwt(display, inImage, false, -1);
        assertNotNull(outImage);

        ImageData outData = outImage.getImageData();
        assertEquals(inImage.getWidth(), outData.width);
        assertEquals(inImage.getHeight(), outData.height);
        assertNull(outData.alphaData);
        assertEquals(SWT.TRANSPARENCY_NONE, outData.getTransparencyType());

        PaletteData inPalette  = SwtUtils.getAwtPaletteData(inImage.getType());
        PaletteData outPalette = outData.palette;

        for (int y = 0; y < outData.height; y++) {
            for (int x = 0; x < outData.width; x++) {
                // Note: we can't compare pixel directly as integers since convertToSwt() might
                // have changed the RGBA ordering depending on the platform (e.g. it will on
                // Windows.)
                RGB expected = inPalette.getRGB( inImage.getRGB(  x, y));
                RGB actual   = outPalette.getRGB(outData.getPixel(x, y));
                assertEquals(expected, actual);
            }
        }

        // Convert back to AWT and compare with original AWT image
        BufferedImage awtImage = SwtUtils.convertToAwt(outImage);
        assertNotNull(awtImage);

        // Both image have the same RGBA ordering
        assertEquals(BufferedImage.TYPE_INT_ARGB, inImage.getType());
        assertEquals(BufferedImage.TYPE_INT_ARGB, awtImage.getType());

        int awtAlphaMask = 0xFF000000;

        for (int y = 0; y < outData.height; y++) {
            for (int x = 0; x < outData.width; x++) {
                // Note: we can compare pixels as integers since we just
                // asserted both images have the same color image type except
                // for the content of the alpha channel.
                int actual = awtImage.getRGB(x, y);
                assertEquals(awtAlphaMask, actual & awtAlphaMask);
                assertEquals(awtAlphaMask | inImage.getRGB(x, y), awtImage.getRGB(x, y));
            }
        }
    }

    public void testImageConvertGlobalAlpha() throws Exception {
        BufferedImage inImage = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        Graphics g = inImage.getGraphics();
        g.setColor(new Color(0xAA112233, true));
        g.fillRect(0, 0, inImage.getWidth(), inImage.getHeight());
        g.dispose();

        Shell shell = new Shell();
        Display display = shell.getDisplay();

        Image outImage = SwtUtils.convertToSwt(display, inImage, false, 128);
        assertNotNull(outImage);

        ImageData outData = outImage.getImageData();
        assertEquals(inImage.getWidth(), outData.width);
        assertEquals(inImage.getHeight(), outData.height);
        assertEquals(128, outData.alpha);
        assertEquals(SWT.TRANSPARENCY_NONE, outData.getTransparencyType());
        assertNull(outData.alphaData);

        PaletteData inPalette  = SwtUtils.getAwtPaletteData(inImage.getType());
        PaletteData outPalette = outData.palette;

        for (int y = 0; y < outData.height; y++) {
            for (int x = 0; x < outData.width; x++) {

                RGB expected = inPalette.getRGB( inImage.getRGB(  x, y));
                RGB actual   = outPalette.getRGB(outData.getPixel(x, y));
                assertEquals(expected, actual);
            }
        }
    }

    public void testImageConvertAlpha() throws Exception {
        BufferedImage inImage = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        Graphics g = inImage.getGraphics();
        g.setColor(new Color(0xAA112233, true));
        g.fillRect(0, 0, inImage.getWidth(), inImage.getHeight());
        g.dispose();

        Shell shell = new Shell();
        Display display = shell.getDisplay();

        Image outImage = SwtUtils.convertToSwt(display, inImage, true, -1);
        assertNotNull(outImage);

        ImageData outData = outImage.getImageData();
        assertEquals(inImage.getWidth(), outData.width);
        assertEquals(inImage.getHeight(), outData.height);
        assertEquals(SWT.TRANSPARENCY_ALPHA, outData.getTransparencyType());

        PaletteData inPalette  = SwtUtils.getAwtPaletteData(inImage.getType());
        PaletteData outPalette = outData.palette;

        for (int y = 0; y < outData.height; y++) {
            for (int x = 0; x < outData.width; x++) {
                RGB expected = inPalette.getRGB( inImage.getRGB(  x, y));
                RGB actual   = outPalette.getRGB(outData.getPixel(x, y));
                assertEquals(expected, actual);

                // Note: >> instead of >>> since we will compare with byte (a signed number)
                int expectedAlpha = inImage.getRGB(x, y) >> 24;
                int actualAlpha = outData.alphaData[y * outData.width + x];
                assertEquals(expectedAlpha, actualAlpha);
            }
        }
    }

    public void testImageConvertAlphaMultiplied() throws Exception {
        BufferedImage inImage = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        Graphics g = inImage.getGraphics();
        g.setColor(new Color(0xAA112233, true));
        g.fillRect(0, 0, inImage.getWidth(), inImage.getHeight());
        g.dispose();

        Shell shell = new Shell();
        Display display = shell.getDisplay();
        Image outImage = SwtUtils.convertToSwt(display, inImage, true, 32);
        assertNotNull(outImage);

        // Expected alpha is 0xAA from the AWT input image pre-multiplied by 32 in convertToSwt.
        int expectedAlpha = (0xAA * 32) >> 8;

        ImageData outData = outImage.getImageData();
        assertEquals(inImage.getWidth(), outData.width);
        assertEquals(inImage.getHeight(), outData.height);
        assertEquals(SWT.TRANSPARENCY_ALPHA, outData.getTransparencyType());

        PaletteData inPalette  = SwtUtils.getAwtPaletteData(inImage.getType());
        PaletteData outPalette = outData.palette;

        for (int y = 0; y < outData.height; y++) {
            for (int x = 0; x < outData.width; x++) {
                RGB expected = inPalette.getRGB( inImage.getRGB(  x, y));
                RGB actual   = outPalette.getRGB(outData.getPixel(x, y));
                assertEquals(expected, actual);

                byte actualAlpha = outData.alphaData[y * outData.width + x];
                assertEquals(expectedAlpha, actualAlpha);
            }
        }
    }

    public final void testSetRectangle() {
        Rect r = new Rect(1, 2, 3, 4);
        Rectangle r2 = new Rectangle(3, 4, 20, 30);
        SwtUtils.set(r, r2);

        assertEquals(3, r.x);
        assertEquals(4, r.y);
        assertEquals(20, r.w);
        assertEquals(30, r.h);
    }

    public final void testRectRectangle() {
        Rectangle r = new Rectangle(3, 4, 20, 30);
        Rect r2 = SwtUtils.toRect(r);

        assertEquals(3, r2.x);
        assertEquals(4, r2.y);
        assertEquals(20, r2.w);
        assertEquals(30, r2.h);
    }

}
