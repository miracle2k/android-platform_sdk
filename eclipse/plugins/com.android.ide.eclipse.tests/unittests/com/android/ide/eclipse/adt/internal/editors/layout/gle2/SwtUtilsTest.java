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
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;

import junit.framework.TestCase;

public class SwtUtilsTest extends TestCase {
    public void testCropBlank() throws Exception {
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics g = image.getGraphics();
        g.setColor(new Color(0, true));
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.dispose();

        BufferedImage crop = SwtUtils.cropBlank(image, null);
        assertNull(crop);
    }

    public void testCropBlankPre() throws Exception {
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics g = image.getGraphics();
        g.setColor(new Color(0, true));
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.dispose();

        BufferedImage crop = SwtUtils.cropBlank(image, new Rect(5, 5, 80, 80));
        assertNull(crop);
    }

    public void testCropNonblank() throws Exception {
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics g = image.getGraphics();
        g.setColor(new Color(0, false));
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.dispose();

        BufferedImage crop = SwtUtils.cropBlank(image, null);
        assertNotNull(crop);
        assertEquals(image.getWidth(), crop.getWidth());
        assertEquals(image.getHeight(), crop.getHeight());
    }

    public void testCropSomething() throws Exception {
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics g = image.getGraphics();
        g.setColor(new Color(0, true));
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.setColor(new Color(0xFF00FF00, true));
        g.fillRect(25, 25, 50, 50);
        g.dispose();

        BufferedImage crop = SwtUtils.cropBlank(image, null);
        assertNotNull(crop);
        assertEquals(50, crop.getWidth());
        assertEquals(50, crop.getHeight());
        assertEquals(0xFF00FF00, crop.getRGB(0, 0));
        assertEquals(0xFF00FF00, crop.getRGB(49, 49));
    }

    public void testCropSomethingPre() throws Exception {
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics g = image.getGraphics();
        g.setColor(new Color(0, true));
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.setColor(new Color(0xFF00FF00, true));
        g.fillRect(25, 25, 50, 50);
        g.dispose();

        BufferedImage crop = SwtUtils.cropBlank(image, new Rect(0, 0, 100, 100));
        assertNotNull(crop);
        assertEquals(50, crop.getWidth());
        assertEquals(50, crop.getHeight());
        assertEquals(0xFF00FF00, crop.getRGB(0, 0));
        assertEquals(0xFF00FF00, crop.getRGB(49, 49));
    }

    public void testCropSomethingPre2() throws Exception {
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics g = image.getGraphics();
        g.setColor(new Color(0, true));
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.setColor(new Color(0xFF00FF00, true));
        g.fillRect(25, 25, 50, 50);
        g.dispose();

        BufferedImage crop = SwtUtils.cropBlank(image, new Rect(5, 5, 80, 80));
        assertNotNull(crop);
        assertEquals(50, crop.getWidth());
        assertEquals(50, crop.getHeight());
        assertEquals(0xFF00FF00, crop.getRGB(0, 0));
        assertEquals(0xFF00FF00, crop.getRGB(49, 49));
    }

    public void testCropColor() throws Exception {
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics g = image.getGraphics();
        g.setColor(new Color(0xFF00FF00, true));
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.dispose();

        BufferedImage crop = SwtUtils.cropColor(image, 0xFF00FF00, null);
        assertNull(crop);
    }

    public void testCropNonColor() throws Exception {
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics g = image.getGraphics();
        g.setColor(new Color(0xFF00FF00, true));
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.dispose();

        BufferedImage crop = SwtUtils.cropColor(image, 0xFFFF0000, null);
        assertNotNull(crop);
        assertEquals(image.getWidth(), crop.getWidth());
        assertEquals(image.getHeight(), crop.getHeight());
    }

    public void testCropColorSomething() throws Exception {
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics g = image.getGraphics();
        g.setColor(new Color(0xFF00FF00, true));
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.setColor(new Color(0xFFFF0000, true));
        g.fillRect(25, 25, 50, 50);
        g.dispose();

        BufferedImage crop = SwtUtils.cropColor(image, 0xFF00FF00, null);
        assertEquals(50, crop.getWidth());
        assertEquals(50, crop.getHeight());
        assertEquals(0xFFFF0000, crop.getRGB(0, 0));
        assertEquals(0xFFFF0000, crop.getRGB(49, 49));
    }

    public void testNullOk() throws Exception {
        SwtUtils.cropBlank(null, null);
        SwtUtils.cropColor(null, 0, null);
    }

    public void testImageConvertNoAlpha() throws Exception {
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics g = image.getGraphics();
        g.setColor(new Color(0xFF00FF00, true));
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.dispose();

        Shell shell = new Shell();
        Display display = shell.getDisplay();
        Image swtImage = SwtUtils.convertImage(display, image, false, -1);
        assertNotNull(swtImage);
        ImageData data = swtImage.getImageData();
        assertEquals(image.getWidth(), data.width);
        assertEquals(image.getHeight(), data.height);
        assertNull(data.alphaData);
        for (int y = 0; y < data.height; y++) {
            for (int x = 0; x < data.width; x++) {
                assertEquals(image.getRGB(x, y) & 0xFFFFFF, data.getPixel(x, y));
            }
        }
    }

    public void testImageConvertGlobalAlpha() throws Exception {
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics g = image.getGraphics();
        g.setColor(new Color(0xFF00FF00, true));
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.dispose();

        Shell shell = new Shell();
        Display display = shell.getDisplay();
        Image swtImage = SwtUtils.convertImage(display, image, false, 128);
        assertNotNull(swtImage);
        ImageData data = swtImage.getImageData();
        assertEquals(image.getWidth(), data.width);
        assertEquals(image.getHeight(), data.height);
        assertNull(data.alphaData);
        assertEquals(128, data.alpha);
        for (int y = 0; y < data.height; y++) {
            for (int x = 0; x < data.width; x++) {
                assertEquals(image.getRGB(x, y) & 0xFFFFFF, data.getPixel(x, y));
            }
        }
    }

    public void testImageConvertAlpha() throws Exception {
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics g = image.getGraphics();
        g.setColor(new Color(0xFF00FF00, true));
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.dispose();

        Shell shell = new Shell();
        Display display = shell.getDisplay();
        Image swtImage = SwtUtils.convertImage(display, image, true, -1);
        assertNotNull(swtImage);
        ImageData data = swtImage.getImageData();
        assertEquals(image.getWidth(), data.width);
        assertEquals(image.getHeight(), data.height);
        for (int y = 0; y < data.height; y++) {
            for (int x = 0; x < data.width; x++) {
                assertEquals(image.getRGB(x, y) & 0xFFFFFF, data.getPixel(x, y));
                // Note: >> instead of >>> since we will compare with byte (a signed
                // number)
                assertEquals(image.getRGB(x, y) >> 24, data.alphaData[y * data.width + x]);
            }
        }
    }

    public void testImageConvertAlphaMultiplied() throws Exception {
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics g = image.getGraphics();
        g.setColor(new Color(0xFF00FF00, true));
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.dispose();

        Shell shell = new Shell();
        Display display = shell.getDisplay();
        Image swtImage = SwtUtils.convertImage(display, image, true, 32);
        assertNotNull(swtImage);
        ImageData data = swtImage.getImageData();
        assertEquals(image.getWidth(), data.width);
        assertEquals(image.getHeight(), data.height);
        for (int y = 0; y < data.height; y++) {
            for (int x = 0; x < data.width; x++) {
                assertEquals(image.getRGB(x, y) & 0xFFFFFF, data.getPixel(x, y));
                int expectedAlpha = (image.getRGB(x,y) >>> 24);
                byte expected = (byte)(expectedAlpha / 8);
                byte actual = data.alphaData[y * data.width + x];
                assertEquals(expected, actual);
            }
        }
    }

    public void testNothingTodo() throws Exception {
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics g = image.getGraphics();
        g.setColor(new Color(0xFF00FF00, true));
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.dispose();

        BufferedImage crop = SwtUtils.cropColor(image, 0xFFFF0000, new Rect(40, 40, 0, 0));
        assertNull(crop);
    }

}
