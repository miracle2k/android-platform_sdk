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

import com.android.layoutlib.api.IImageFactory;

import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.graphics.Device;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;

import java.awt.Point;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.PixelInterleavedSampleModel;
import java.awt.image.Raster;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;

/**
 * The {@link ImageOverlay} class renders an image as an overlay.
 */
public class ImageOverlay extends Overlay implements IImageFactory {
    /** Current background image. Null when there's no image. */
    private Image mImage;
    /** Current background AWT image. This is created by {@link #getImage()}, which is called
     * by the LayoutLib. */
    private BufferedImage mAwtImage;

    /** The associated {@link LayoutCanvas}. */
    private LayoutCanvas mCanvas;

    /** Vertical scaling & scrollbar information. */
    private CanvasTransform mVScale;

    /** Horizontal scaling & scrollbar information. */
    private CanvasTransform mHScale;

    /**
     * A lazily instantiated SWT sample model
     */
    private PixelInterleavedSampleModel mSampleModel;


    /**
     * Constructs an {@link ImageOverlay} tied to the given canvas.
     *
     * @param canvas The {@link LayoutCanvas} to paint the overlay over.
     * @param hScale The horizontal scale information.
     * @param vScale The vertical scale information.
     */
    public ImageOverlay(LayoutCanvas canvas, CanvasTransform hScale, CanvasTransform vScale) {
        this.mCanvas = canvas;
        this.mHScale = hScale;
        this.mVScale = vScale;
    }

    @Override
    public void create(Device device) {
        super.create(device);
    }

    @Override
    public void dispose() {
        if (mImage != null) {
            mImage.dispose();
            mImage = null;
        }
    }

    /**
     * Sets the image to be drawn as an overlay from the passed in AWT
     * {@link BufferedImage} (which will be converted to an SWT image).
     * <p/>
     * The image <b>can</b> be null, which is the case when we are dealing with
     * an empty document.
     *
     * @param awtImage The AWT image to be rendered as an SWT image.
     * @return The corresponding SWT image, or null.
     */
    public Image setImage(BufferedImage awtImage) {
        if (awtImage != mAwtImage) {
            mAwtImage = null;

            if (mImage != null) {
                mImage.dispose();
            }

            if (awtImage == null) {
                mImage = null;
            } else {
                mImage = SwtUtils.convertToSwt(mCanvas.getDisplay(), awtImage, false, -1);
            }
        } else {
            // The image being passed is the one that was created in #getImage(int,int),
            // we can create an SWT image more efficiently.
            WritableRaster awtRaster = mAwtImage.getRaster();
            DataBufferByte byteBuffer = (DataBufferByte) awtRaster.getDataBuffer();
            byte[] data = byteBuffer.getData();

            ImageData imageData = new ImageData(mAwtImage.getWidth(), mAwtImage.getHeight(), 32,
                        new PaletteData(0x00FF0000, 0x0000FF00, 0x000000FF));

            // normally we'd use ImageData.setPixels() but it only accepts int[] for 32 bits image.
            // However from #getImage(int, int), we know the raster data is the same exact format
            // as the SWT image, so we just do a copy.
            System.arraycopy(data, 0, imageData.data, 0, data.length);

            mImage  = new Image(getDevice(), imageData);
        }

        return mImage;
    }

    /**
     * Returns the currently painted image, or null if none has been set
     *
     * @return the currently painted image or null
     */
    public Image getImage() {
        return mImage;
    }

    @Override
    public void paint(GC gc) {
        if (mImage != null) {
            boolean valid = mCanvas.getViewHierarchy().isValid();
            if (!valid) {
                gc_setAlpha(gc, 128); // half-transparent
            }

            CanvasTransform hi = mHScale;
            CanvasTransform vi = mVScale;

            // we only anti-alias when reducing the image size.
            int oldAlias = -2;
            if (hi.getScale() < 1.0) {
                oldAlias = gc_setAntialias(gc, SWT.ON);
            }

            gc.drawImage(
                    mImage,
                    0,                      // srcX
                    0,                      // srcY
                    hi.getImgSize(),        // srcWidth
                    vi.getImgSize(),        // srcHeight
                    hi.translate(0),        // destX
                    vi.translate(0),        // destY
                    hi.getScalledImgSize(), // destWidth
                    vi.getScalledImgSize());  // destHeight

            if (oldAlias != -2) {
                gc_setAntialias(gc, oldAlias);
            }

            if (!valid) {
                gc_setAlpha(gc, 255); // opaque
            }
        }
    }

    /**
     * Sets the alpha for the given GC.
     * <p/>
     * Alpha may not work on all platforms and may fail with an exception, which
     * is hidden here (false is returned in that case).
     *
     * @param gc the GC to change
     * @param alpha the new alpha, 0 for transparent, 255 for opaque.
     * @return True if the operation worked, false if it failed with an
     *         exception.
     * @see GC#setAlpha(int)
     */
    private boolean gc_setAlpha(GC gc, int alpha) {
        try {
            gc.setAlpha(alpha);
            return true;
        } catch (SWTException e) {
            return false;
        }
    }

    /**
     * Sets the non-text antialias flag for the given GC.
     * <p/>
     * Antialias may not work on all platforms and may fail with an exception,
     * which is hidden here (-2 is returned in that case).
     *
     * @param gc the GC to change
     * @param alias One of {@link SWT#DEFAULT}, {@link SWT#ON}, {@link SWT#OFF}.
     * @return The previous aliasing mode if the operation worked, or -2 if it
     *         failed with an exception.
     * @see GC#setAntialias(int)
     */
    private int gc_setAntialias(GC gc, int alias) {
        try {
            int old = gc.getAntialias();
            gc.setAntialias(alias);
            return old;
        } catch (SWTException e) {
            return -2;
        }
    }

    public BufferedImage getImage(int w, int h) {
        if (mAwtImage == null ||
                mAwtImage.getWidth() != w ||
                mAwtImage.getHeight() != h) {

            ImageData imageData =
                new ImageData(w, h, 32, new PaletteData(0x00FF0000, 0x0000FF00, 0x000000FF));
            Image image = new Image(getDevice(), imageData);

            // get the new imageData in case the host OS forced a different format.
            imageData = image.getImageData();

            // create a writable raster around the image data.
            WritableRaster raster = (WritableRaster) Raster.createRaster(
                    getSampleModel(imageData.palette, w, h),
                    new DataBufferByte(imageData.data, imageData.data.length),
                    new Point(0,0));

            ColorModel colorModel = new ComponentColorModel(
                    ColorSpace.getInstance(ColorSpace.CS_LINEAR_RGB),
                    true /*hasAlpha*/, true /*isAlphaPremultiplied*/,
                    ColorModel.TRANSLUCENT, DataBuffer.TYPE_BYTE);

            mAwtImage = new BufferedImage(colorModel, raster, false, null);
        }

        return mAwtImage;
    }

    private SampleModel getSampleModel(PaletteData palette, int w, int h) {
        if (mSampleModel == null) {
            return mSampleModel = new PixelInterleavedSampleModel(DataBuffer.TYPE_BYTE, w, h,
                    4 /*pixel stride*/, w * 4 /*scanlineStride*/,
                    getBandOffset(palette));
        }

        return mSampleModel.createCompatibleSampleModel(w, h);
    }

    private int[] getBandOffset(PaletteData palette) {
        // FIXME actually figure out the PixelInterleavedSampleModel's band offset from the image data palette.
        return new int[] {3, 2, 1, 0};
    }
}
