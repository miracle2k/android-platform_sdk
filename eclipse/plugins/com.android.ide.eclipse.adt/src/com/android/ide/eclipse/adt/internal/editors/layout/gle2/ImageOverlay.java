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
import com.android.ide.common.rendering.api.IImageFactory;

import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.graphics.Device;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
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
     * @param isAlphaChannelImage whether the alpha channel of the image is relevant
     * @return The corresponding SWT image, or null.
     */
    public synchronized Image setImage(BufferedImage awtImage, boolean isAlphaChannelImage) {
        if (awtImage != mAwtImage || awtImage == null) {
            mAwtImage = null;

            if (mImage != null) {
                mImage.dispose();
            }

            if (awtImage == null) {
                mImage = null;
            } else {
                mImage = SwtUtils.convertToSwt(mCanvas.getDisplay(), awtImage,
                        isAlphaChannelImage, -1);
            }
        } else {
            assert awtImage instanceof SwtReadyBufferedImage;

            if (isAlphaChannelImage) {
                mImage = SwtUtils.convertToSwt(mCanvas.getDisplay(), awtImage, true, -1);
            } else {
                mImage = ((SwtReadyBufferedImage)awtImage).getSwtImage();
            }
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
    public synchronized void paint(GC gc) {
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

    /**
     * Custom {@link BufferedImage} class able to convert itself into an SWT {@link Image}
     * efficiently.
     *
     * The BufferedImage also contains an instance of {@link ImageData} that's kept around
     * and used to create new SWT {@link Image} objects in {@link #getSwtImage()}.
     *
     */
    private static final class SwtReadyBufferedImage extends BufferedImage {

        private final ImageData mImageData;
        private final Device mDevice;

        /**
         * Creates the image with a given model, raster and SWT {@link ImageData}
         * @param model the color model
         * @param raster the image raster
         * @param imageData the SWT image data.
         * @param device the {@link Device} in which the SWT image will be painted.
         */
        private SwtReadyBufferedImage(int width, int height, ImageData imageData, Device device) {
            super(width, height, BufferedImage.TYPE_INT_ARGB);
            mImageData = imageData;
            mDevice = device;
        }

        /**
         * Returns a new {@link Image} object initialized with the content of the BufferedImage.
         * @return the image object.
         */
        private Image getSwtImage() {
            // transfer the content of the bufferedImage into the image data.
            WritableRaster raster = getRaster();
            int[] imageDataBuffer = ((DataBufferInt) raster.getDataBuffer()).getData();

            mImageData.setPixels(0, 0, imageDataBuffer.length, imageDataBuffer, 0);

            return new Image(mDevice, mImageData);
        }

        /**
         * Creates a new {@link SwtReadyBufferedImage}.
         * @param w the width of the image
         * @param h the height of the image
         * @param device the device in which the SWT image will be painted
         * @return a new {@link SwtReadyBufferedImage} object
         */
        private static SwtReadyBufferedImage createImage(int w, int h, Device device) {
            ImageData imageData = new ImageData(w, h, 32,
                    new PaletteData(0x00FF0000, 0x0000FF00, 0x000000FF));

            SwtReadyBufferedImage swtReadyImage = new SwtReadyBufferedImage(w, h,
                    imageData, device);

            return swtReadyImage;
        }
    }

    /**
     * Implementation of {@link IImageFactory#getImage(int, int)}.
     */
    public BufferedImage getImage(int w, int h) {
        if (mAwtImage == null ||
                mAwtImage.getWidth() != w ||
                mAwtImage.getHeight() != h) {

            mAwtImage = SwtReadyBufferedImage.createImage(w, h, getDevice());
        }

        return mAwtImage;
    }

    /**
     * Returns the bounds of the current image, or null
     *
     * @return the bounds of the current image, or null
     */
    public Rect getImageBounds() {
        if (mImage == null) {
            return null;
        }

        return new Rect(0, 0, mImage.getImageData().width, mImage.getImageData().height);
    }
}
