/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.hierarchyviewerlib.ui;

import com.android.hierarchyviewerlib.HierarchyViewerDirector;
import com.android.hierarchyviewerlib.models.PixelPerfectModel;
import com.android.hierarchyviewerlib.models.PixelPerfectModel.IImageChangeListener;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Slider;

public class PixelPerfectControls extends Composite implements IImageChangeListener {

    private Slider mOverlaySlider;

    private Slider mZoomSlider;

    private Slider mAutoRefreshSlider;

    public PixelPerfectControls(Composite parent) {
        super(parent, SWT.NONE);
        setLayout(new FormLayout());

        Label overlayTransparencyRight = new Label(this, SWT.NONE);
        overlayTransparencyRight.setText("100%");
        FormData overlayTransparencyRightData = new FormData();
        overlayTransparencyRightData.right = new FormAttachment(100, -2);
        overlayTransparencyRightData.top = new FormAttachment(0, 2);
        overlayTransparencyRight.setLayoutData(overlayTransparencyRightData);

        Label refreshRight = new Label(this, SWT.NONE);
        refreshRight.setText("40s");
        FormData refreshRightData = new FormData();
        refreshRightData.right = new FormAttachment(100, -2);
        refreshRightData.top = new FormAttachment(overlayTransparencyRight, 2);
        refreshRightData.left = new FormAttachment(overlayTransparencyRight, 0, SWT.LEFT);
        refreshRight.setLayoutData(refreshRightData);

        Label zoomRight = new Label(this, SWT.NONE);
        zoomRight.setText("24x");
        FormData zoomRightData = new FormData();
        zoomRightData.right = new FormAttachment(100, -2);
        zoomRightData.top = new FormAttachment(refreshRight, 2);
        zoomRightData.left = new FormAttachment(overlayTransparencyRight, 0, SWT.LEFT);
        zoomRight.setLayoutData(zoomRightData);

        Label overlayTransparency = new Label(this, SWT.NONE);
        Label refresh = new Label(this, SWT.NONE);

        overlayTransparency.setText("Overlay:");
        FormData overlayTransparencyData = new FormData();
        overlayTransparencyData.left = new FormAttachment(0, 2);
        overlayTransparencyData.top = new FormAttachment(0, 2);
        overlayTransparencyData.right = new FormAttachment(refresh, 0, SWT.RIGHT);
        overlayTransparency.setLayoutData(overlayTransparencyData);

        refresh.setText("Refresh Rate:");
        FormData refreshData = new FormData();
        refreshData.top = new FormAttachment(overlayTransparency, 2);
        refreshData.left = new FormAttachment(0, 2);
        refresh.setLayoutData(refreshData);

        Label zoom = new Label(this, SWT.NONE);
        zoom.setText("Zoom:");
        FormData zoomData = new FormData();
        zoomData.right = new FormAttachment(refresh, 0, SWT.RIGHT);
        zoomData.top = new FormAttachment(refresh, 2);
        zoomData.left = new FormAttachment(0, 2);
        zoom.setLayoutData(zoomData);

        Label overlayTransparencyLeft = new Label(this, SWT.RIGHT);
        overlayTransparencyLeft.setText("0%");
        FormData overlayTransparencyLeftData = new FormData();
        overlayTransparencyLeftData.top = new FormAttachment(0, 2);
        overlayTransparencyLeftData.left = new FormAttachment(overlayTransparency, 2);
        overlayTransparencyLeft.setLayoutData(overlayTransparencyLeftData);

        Label refreshLeft = new Label(this, SWT.RIGHT);
        refreshLeft.setText("1s");
        FormData refreshLeftData = new FormData();
        refreshLeftData.top = new FormAttachment(overlayTransparencyLeft, 2);
        refreshLeftData.left = new FormAttachment(refresh, 2);
        refreshLeft.setLayoutData(refreshLeftData);

        Label zoomLeft = new Label(this, SWT.RIGHT);
        zoomLeft.setText("2x");
        FormData zoomLeftData = new FormData();
        zoomLeftData.top = new FormAttachment(refreshLeft, 2);
        zoomLeftData.left = new FormAttachment(zoom, 2);
        zoomLeft.setLayoutData(zoomLeftData);

        mOverlaySlider = new Slider(this, SWT.HORIZONTAL);
        mOverlaySlider.setMinimum(0);
        mOverlaySlider.setMaximum(101);
        mOverlaySlider.setThumb(1);
        mOverlaySlider.setSelection((int) Math.round(PixelPerfectModel.getModel()
                .getOverlayTransparency() * 100));

        Image overlayImage = PixelPerfectModel.getModel().getOverlayImage();
        mOverlaySlider.setEnabled(overlayImage != null);
        FormData overlaySliderData = new FormData();
        overlaySliderData.right = new FormAttachment(overlayTransparencyRight, -4);
        overlaySliderData.top = new FormAttachment(0, 2);
        overlaySliderData.left = new FormAttachment(overlayTransparencyLeft, 4);
        mOverlaySlider.setLayoutData(overlaySliderData);

        mOverlaySlider.addSelectionListener(overlaySliderSelectionListener);

        mAutoRefreshSlider = new Slider(this, SWT.HORIZONTAL);
        mAutoRefreshSlider.setMinimum(1);
        mAutoRefreshSlider.setMaximum(41);
        mAutoRefreshSlider.setThumb(1);
        mAutoRefreshSlider.setSelection(HierarchyViewerDirector.getDirector()
                .getPixelPerfectAutoRefreshInverval());
        FormData refreshSliderData = new FormData();
        refreshSliderData.right = new FormAttachment(overlayTransparencyRight, -4);
        refreshSliderData.top = new FormAttachment(overlayTransparencyRight, 2);
        refreshSliderData.left = new FormAttachment(mOverlaySlider, 0, SWT.LEFT);
        mAutoRefreshSlider.setLayoutData(refreshSliderData);

        mAutoRefreshSlider.addSelectionListener(mRefreshSliderSelectionListener);

        mZoomSlider = new Slider(this, SWT.HORIZONTAL);
        mZoomSlider.setMinimum(2);
        mZoomSlider.setMaximum(25);
        mZoomSlider.setThumb(1);
        mZoomSlider.setSelection(PixelPerfectModel.getModel().getZoom());
        FormData zoomSliderData = new FormData();
        zoomSliderData.right = new FormAttachment(overlayTransparencyRight, -4);
        zoomSliderData.top = new FormAttachment(refreshRight, 2);
        zoomSliderData.left = new FormAttachment(mOverlaySlider, 0, SWT.LEFT);
        mZoomSlider.setLayoutData(zoomSliderData);

        mZoomSlider.addSelectionListener(mZoomSliderSelectionListener);

        addDisposeListener(mDisposeListener);

        PixelPerfectModel.getModel().addImageChangeListener(this);
    }

    private DisposeListener mDisposeListener = new DisposeListener() {
        public void widgetDisposed(DisposeEvent e) {
            PixelPerfectModel.getModel().removeImageChangeListener(PixelPerfectControls.this);
        }
    };

    private SelectionListener overlaySliderSelectionListener = new SelectionListener() {
        private int oldValue;

        public void widgetDefaultSelected(SelectionEvent e) {
            // pass
        }

        public void widgetSelected(SelectionEvent e) {
            int newValue = mOverlaySlider.getSelection();
            if (oldValue != newValue) {
                PixelPerfectModel.getModel().removeImageChangeListener(PixelPerfectControls.this);
                PixelPerfectModel.getModel().setOverlayTransparency(newValue / 100.0);
                PixelPerfectModel.getModel().addImageChangeListener(PixelPerfectControls.this);
                oldValue = newValue;
            }
        }
    };

    private SelectionListener mRefreshSliderSelectionListener = new SelectionListener() {
        private int oldValue;

        public void widgetDefaultSelected(SelectionEvent e) {
            // pass
        }

        public void widgetSelected(SelectionEvent e) {
            int newValue = mAutoRefreshSlider.getSelection();
            if (oldValue != newValue) {
                HierarchyViewerDirector.getDirector().setPixelPerfectAutoRefreshInterval(newValue);
            }
        }
    };

    private SelectionListener mZoomSliderSelectionListener = new SelectionListener() {
        private int oldValue;

        public void widgetDefaultSelected(SelectionEvent e) {
            // pass
        }

        public void widgetSelected(SelectionEvent e) {
            int newValue = mZoomSlider.getSelection();
            if (oldValue != newValue) {
                PixelPerfectModel.getModel().removeImageChangeListener(PixelPerfectControls.this);
                PixelPerfectModel.getModel().setZoom(newValue);
                PixelPerfectModel.getModel().addImageChangeListener(PixelPerfectControls.this);
                oldValue = newValue;
            }
        }
    };

    public void crosshairMoved() {
        // pass
    }

    public void treeChanged() {
        // pass
    }

    public void imageChanged() {
        // pass
    }

    public void imageLoaded() {
        Display.getDefault().syncExec(new Runnable() {
            public void run() {
                Image overlayImage = PixelPerfectModel.getModel().getOverlayImage();
                mOverlaySlider.setEnabled(overlayImage != null);
                if (PixelPerfectModel.getModel().getImage() == null) {
                } else {
                    mZoomSlider.setSelection(PixelPerfectModel.getModel().getZoom());
                }
            }
        });
    }

    public void overlayChanged() {
        Display.getDefault().syncExec(new Runnable() {
            public void run() {
                Image overlayImage = PixelPerfectModel.getModel().getOverlayImage();
                mOverlaySlider.setEnabled(overlayImage != null);
            }
        });
    }

    public void overlayTransparencyChanged() {
        Display.getDefault().syncExec(new Runnable() {
            public void run() {
                mOverlaySlider.setSelection((int) (PixelPerfectModel.getModel()
                        .getOverlayTransparency() * 100));
            }
        });
    }

    public void selectionChanged() {
        // pass
    }

    public void zoomChanged() {
        Display.getDefault().syncExec(new Runnable() {
            public void run() {
                mZoomSlider.setSelection(PixelPerfectModel.getModel().getZoom());
            }
        });
    }
}
