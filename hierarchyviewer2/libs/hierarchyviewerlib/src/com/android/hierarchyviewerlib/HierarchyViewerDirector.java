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

package com.android.hierarchyviewerlib;

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.Log;
import com.android.ddmlib.RawImage;
import com.android.ddmlib.TimeoutException;
import com.android.ddmlib.AndroidDebugBridge.IDeviceChangeListener;
import com.android.hierarchyviewerlib.device.DeviceBridge;
import com.android.hierarchyviewerlib.device.ViewNode;
import com.android.hierarchyviewerlib.device.Window;
import com.android.hierarchyviewerlib.device.WindowUpdater;
import com.android.hierarchyviewerlib.device.DeviceBridge.ViewServerInfo;
import com.android.hierarchyviewerlib.device.WindowUpdater.IWindowChangeListener;
import com.android.hierarchyviewerlib.models.DeviceSelectionModel;
import com.android.hierarchyviewerlib.models.PixelPerfectModel;
import com.android.hierarchyviewerlib.models.TreeViewModel;
import com.android.hierarchyviewerlib.ui.CaptureDisplay;
import com.android.hierarchyviewerlib.ui.TreeView;
import com.android.hierarchyviewerlib.ui.util.DrawableViewNode;
import com.android.hierarchyviewerlib.ui.util.PsdFile;

import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Shell;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Timer;
import java.util.TimerTask;

/**
 * This is the class where most of the logic resides.
 */
public abstract class HierarchyViewerDirector implements IDeviceChangeListener,
        IWindowChangeListener {

    protected static HierarchyViewerDirector sDirector;

    public static final String TAG = "hierarchyviewer";

    private int mPixelPerfectRefreshesInProgress = 0;

    private Timer mPixelPerfectRefreshTimer = new Timer();

    private boolean mAutoRefresh = false;

    public static final int DEFAULT_PIXEL_PERFECT_AUTOREFRESH_INTERVAL = 5;

    private int mPixelPerfectAutoRefreshInterval = DEFAULT_PIXEL_PERFECT_AUTOREFRESH_INTERVAL;

    private PixelPerfectAutoRefreshTask mCurrentAutoRefreshTask;

    private String mFilterText = ""; //$NON-NLS-1$

    public void terminate() {
        WindowUpdater.terminate();
        mPixelPerfectRefreshTimer.cancel();
    }

    public abstract String getAdbLocation();

    public static HierarchyViewerDirector getDirector() {
        return sDirector;
    }

    /**
     * Init the DeviceBridge with an existing {@link AndroidDebugBridge}.
     * @param bridge the bridge object to use
     */
    public void acquireBridge(AndroidDebugBridge bridge) {
        DeviceBridge.acquireBridge(bridge);
    }

    /**
     * Creates an {@link AndroidDebugBridge} connected to adb at the given location.
     *
     * If a bridge is already running, this disconnects it and creates a new one.
     *
     * @param adbLocation the location to adb.
     */
    public void initDebugBridge() {
        DeviceBridge.initDebugBridge(getAdbLocation());
    }

    public void stopDebugBridge() {
        DeviceBridge.terminate();
    }

    public void populateDeviceSelectionModel() {
        IDevice[] devices = DeviceBridge.getDevices();
        for (IDevice device : devices) {
            deviceConnected(device);
        }
    }

    public void startListenForDevices() {
        DeviceBridge.startListenForDevices(this);
    }

    public void stopListenForDevices() {
        DeviceBridge.stopListenForDevices(this);
    }

    public abstract void executeInBackground(String taskName, Runnable task);

    public void deviceConnected(final IDevice device) {
        executeInBackground("Connecting device", new Runnable() {
            public void run() {
                if (DeviceSelectionModel.getModel().containsDevice(device)) {
                    windowsChanged(device);
                } else if (device.isOnline()) {
                    DeviceBridge.setupDeviceForward(device);
                    if (!DeviceBridge.isViewServerRunning(device)) {
                        if (!DeviceBridge.startViewServer(device)) {
                            // Let's do something interesting here... Try again
                            // in 2 seconds.
                            try {
                                Thread.sleep(2000);
                            } catch (InterruptedException e) {
                            }
                            if (!DeviceBridge.startViewServer(device)) {
                                Log.e(TAG, "Unable to debug device " + device);
                                DeviceBridge.removeDeviceForward(device);
                            } else {
                                loadViewServerInfoAndWindows(device);
                            }
                            return;
                        }
                    }
                    loadViewServerInfoAndWindows(device);
                }
            }
        });
    }

    private void loadViewServerInfoAndWindows(final IDevice device) {

        ViewServerInfo viewServerInfo = DeviceBridge.loadViewServerInfo(device);
        if (viewServerInfo == null) {
            return;
        }
        Window[] windows = DeviceBridge.loadWindows(device);
        DeviceSelectionModel.getModel().addDevice(device, windows);
        if (viewServerInfo.protocolVersion >= 3) {
            WindowUpdater.startListenForWindowChanges(HierarchyViewerDirector.this, device);
            focusChanged(device);
        }

    }

    public void deviceDisconnected(final IDevice device) {
        executeInBackground("Disconnecting device", new Runnable() {
            public void run() {
                ViewServerInfo viewServerInfo = DeviceBridge.getViewServerInfo(device);
                if (viewServerInfo != null && viewServerInfo.protocolVersion >= 3) {
                    WindowUpdater.stopListenForWindowChanges(HierarchyViewerDirector.this, device);
                }
                DeviceBridge.removeDeviceForward(device);
                DeviceBridge.removeViewServerInfo(device);
                DeviceSelectionModel.getModel().removeDevice(device);
                if (PixelPerfectModel.getModel().getDevice() == device) {
                    PixelPerfectModel.getModel().setData(null, null, null);
                }
                Window treeViewWindow = TreeViewModel.getModel().getWindow();
                if (treeViewWindow != null && treeViewWindow.getDevice() == device) {
                    TreeViewModel.getModel().setData(null, null);
                    mFilterText = ""; //$NON-NLS-1$
                }
            }
        });
    }

    public void deviceChanged(IDevice device, int changeMask) {
        if ((changeMask & IDevice.CHANGE_STATE) != 0 && device.isOnline()) {
            deviceConnected(device);
        }
    }

    public void windowsChanged(final IDevice device) {
        executeInBackground("Refreshing windows", new Runnable() {
            public void run() {
                if (!DeviceBridge.isViewServerRunning(device)) {
                    if (!DeviceBridge.startViewServer(device)) {
                        Log.e(TAG, "Unable to debug device " + device);
                        return;
                    }
                }
                Window[] windows = DeviceBridge.loadWindows(device);
                DeviceSelectionModel.getModel().updateDevice(device, windows);
            }
        });
    }

    public void focusChanged(final IDevice device) {
        executeInBackground("Updating focus", new Runnable() {
            public void run() {
                int focusedWindow = DeviceBridge.getFocusedWindow(device);
                DeviceSelectionModel.getModel().updateFocusedWindow(device, focusedWindow);
            }
        });
    }

    public void refreshPixelPerfect() {
        final IDevice device = PixelPerfectModel.getModel().getDevice();
        if (device != null) {
            // Some interesting logic here. We don't want to refresh the pixel
            // perfect view 1000 times in a row if the focus keeps changing. We
            // just
            // want it to refresh following the last focus change.
            boolean proceed = false;
            synchronized (this) {
                if (mPixelPerfectRefreshesInProgress <= 1) {
                    proceed = true;
                    mPixelPerfectRefreshesInProgress++;
                }
            }
            if (proceed) {
                executeInBackground("Refreshing pixel perfect screenshot", new Runnable() {
                    public void run() {
                        Image screenshotImage = getScreenshotImage(device);
                        if (screenshotImage != null) {
                            PixelPerfectModel.getModel().setImage(screenshotImage);
                        }
                        synchronized (HierarchyViewerDirector.this) {
                            mPixelPerfectRefreshesInProgress--;
                        }
                    }

                });
            }
        }
    }

    public void refreshPixelPerfectTree() {
        final IDevice device = PixelPerfectModel.getModel().getDevice();
        if (device != null) {
            executeInBackground("Refreshing pixel perfect tree", new Runnable() {
                public void run() {
                    ViewNode viewNode =
                            DeviceBridge.loadWindowData(Window.getFocusedWindow(device));
                    if (viewNode != null) {
                        PixelPerfectModel.getModel().setTree(viewNode);
                    }
                }

            });
        }
    }

    public void loadPixelPerfectData(final IDevice device) {
        executeInBackground("Loading pixel perfect data", new Runnable() {
            public void run() {
                Image screenshotImage = getScreenshotImage(device);
                if (screenshotImage != null) {
                    ViewNode viewNode =
                            DeviceBridge.loadWindowData(Window.getFocusedWindow(device));
                    if (viewNode != null) {
                        PixelPerfectModel.getModel().setData(device, screenshotImage, viewNode);
                    }
                }
            }
        });
    }

    private Image getScreenshotImage(IDevice device) {
        try {
            final RawImage screenshot = device.getScreenshot();
            if (screenshot == null) {
                return null;
            }
            class ImageContainer {
                public Image image;
            }
            final ImageContainer imageContainer = new ImageContainer();
            Display.getDefault().syncExec(new Runnable() {
                public void run() {
                    ImageData imageData =
                            new ImageData(screenshot.width, screenshot.height, screenshot.bpp,
                                    new PaletteData(screenshot.getRedMask(), screenshot
                                            .getGreenMask(), screenshot.getBlueMask()), 1,
                                    screenshot.data);
                    imageContainer.image = new Image(Display.getDefault(), imageData);
                }
            });
            return imageContainer.image;
        } catch (IOException e) {
            Log.e(TAG, "Unable to load screenshot from device " + device);
        } catch (TimeoutException e) {
            Log.e(TAG, "Timeout loading screenshot from device " + device);
        } catch (AdbCommandRejectedException e) {
            Log.e(TAG, "Adb rejected command to load screenshot from device " + device);
        }
        return null;
    }

    public void loadViewTreeData(final Window window) {
        executeInBackground("Loading view hierarchy", new Runnable() {
            public void run() {

                mFilterText = ""; //$NON-NLS-1$

                ViewNode viewNode = DeviceBridge.loadWindowData(window);
                if (viewNode != null) {
                    DeviceBridge.loadProfileData(window, viewNode);
                    viewNode.setViewCount();
                    TreeViewModel.getModel().setData(window, viewNode);
                }
            }
        });
    }

    public void loadOverlay(final Shell shell) {
        Display.getDefault().syncExec(new Runnable() {
            public void run() {
                FileDialog fileDialog = new FileDialog(shell, SWT.OPEN);
                fileDialog.setFilterExtensions(new String[] {
                    "*.jpg;*.jpeg;*.png;*.gif;*.bmp" //$NON-NLS-1$
                });
                fileDialog.setFilterNames(new String[] {
                    "Image (*.jpg, *.jpeg, *.png, *.gif, *.bmp)"
                });
                fileDialog.setText("Choose an overlay image");
                String fileName = fileDialog.open();
                if (fileName != null) {
                    try {
                        Image image = new Image(Display.getDefault(), fileName);
                        PixelPerfectModel.getModel().setOverlayImage(image);
                    } catch (SWTException e) {
                        Log.e(TAG, "Unable to load image from " + fileName);
                    }
                }
            }
        });
    }

    public void showCapture(final Shell shell, final ViewNode viewNode) {
        executeInBackground("Capturing node", new Runnable() {
            public void run() {
                final Image image = loadCapture(viewNode);
                if (image != null) {

                    Display.getDefault().syncExec(new Runnable() {
                        public void run() {
                            CaptureDisplay.show(shell, viewNode, image);
                        }
                    });
                }
            }
        });
    }

    public Image loadCapture(ViewNode viewNode) {
        final Image image = DeviceBridge.loadCapture(viewNode.window, viewNode);
        if (image != null) {
            viewNode.image = image;

            // Force the layout viewer to redraw.
            TreeViewModel.getModel().notifySelectionChanged();
        }
        return image;
    }

    public void loadCaptureInBackground(final ViewNode viewNode) {
        executeInBackground("Capturing node", new Runnable() {
            public void run() {
                loadCapture(viewNode);
            }
        });
    }

    public void showCapture(Shell shell) {
        DrawableViewNode viewNode = TreeViewModel.getModel().getSelection();
        if (viewNode != null) {
            showCapture(shell, viewNode.viewNode);
        }
    }

    public void refreshWindows() {
        executeInBackground("Refreshing windows", new Runnable() {
            public void run() {
                IDevice[] devicesA = DeviceSelectionModel.getModel().getDevices();
                IDevice[] devicesB = DeviceBridge.getDevices();
                HashSet<IDevice> deviceSet = new HashSet<IDevice>();
                for (int i = 0; i < devicesB.length; i++) {
                    deviceSet.add(devicesB[i]);
                }
                for (int i = 0; i < devicesA.length; i++) {
                    if (deviceSet.contains(devicesA[i])) {
                        windowsChanged(devicesA[i]);
                        deviceSet.remove(devicesA[i]);
                    } else {
                        deviceDisconnected(devicesA[i]);
                    }
                }
                for (IDevice device : deviceSet) {
                    deviceConnected(device);
                }
            }
        });
    }

    public void loadViewHierarchy() {
        Window window = DeviceSelectionModel.getModel().getSelectedWindow();
        if (window != null) {
            loadViewTreeData(window);
        }
    }

    public void inspectScreenshot() {
        IDevice device = DeviceSelectionModel.getModel().getSelectedDevice();
        if (device != null) {
            loadPixelPerfectData(device);
        }
    }

    public void saveTreeView(final Shell shell) {
        Display.getDefault().syncExec(new Runnable() {
            public void run() {
                final DrawableViewNode viewNode = TreeViewModel.getModel().getTree();
                if (viewNode != null) {
                    FileDialog fileDialog = new FileDialog(shell, SWT.SAVE);
                    fileDialog.setFilterExtensions(new String[] {
                        "*.png" //$NON-NLS-1$
                    });
                    fileDialog.setFilterNames(new String[] {
                        "Portable Network Graphics File (*.png)"
                    });
                    fileDialog.setText("Choose where to save the tree image");
                    final String fileName = fileDialog.open();
                    if (fileName != null) {
                        executeInBackground("Saving tree view", new Runnable() {
                            public void run() {
                                Image image = TreeView.paintToImage(viewNode);
                                ImageLoader imageLoader = new ImageLoader();
                                imageLoader.data = new ImageData[] {
                                    image.getImageData()
                                };
                                String extensionedFileName = fileName;
                                if (!extensionedFileName.toLowerCase().endsWith(".png")) { //$NON-NLS-1$
                                    extensionedFileName += ".png"; //$NON-NLS-1$
                                }
                                try {
                                    imageLoader.save(extensionedFileName, SWT.IMAGE_PNG);
                                } catch (SWTException e) {
                                    Log.e(TAG, "Unable to save tree view as a PNG image at "
                                            + fileName);
                                }
                                image.dispose();
                            }
                        });
                    }
                }
            }
        });
    }

    public void savePixelPerfect(final Shell shell) {
        Display.getDefault().syncExec(new Runnable() {
            public void run() {
                Image untouchableImage = PixelPerfectModel.getModel().getImage();
                if (untouchableImage != null) {
                    final ImageData imageData = untouchableImage.getImageData();
                    FileDialog fileDialog = new FileDialog(shell, SWT.SAVE);
                    fileDialog.setFilterExtensions(new String[] {
                        "*.png" //$NON-NLS-1$
                    });
                    fileDialog.setFilterNames(new String[] {
                        "Portable Network Graphics File (*.png)"
                    });
                    fileDialog.setText("Choose where to save the screenshot");
                    final String fileName = fileDialog.open();
                    if (fileName != null) {
                        executeInBackground("Saving pixel perfect", new Runnable() {
                            public void run() {
                                ImageLoader imageLoader = new ImageLoader();
                                imageLoader.data = new ImageData[] {
                                    imageData
                                };
                                String extensionedFileName = fileName;
                                if (!extensionedFileName.toLowerCase().endsWith(".png")) { //$NON-NLS-1$
                                    extensionedFileName += ".png"; //$NON-NLS-1$
                                }
                                try {
                                    imageLoader.save(extensionedFileName, SWT.IMAGE_PNG);
                                } catch (SWTException e) {
                                    Log.e(TAG, "Unable to save tree view as a PNG image at "
                                            + fileName);
                                }
                            }
                        });
                    }
                }
            }
        });
    }

    public void capturePSD(final Shell shell) {
        Display.getDefault().syncExec(new Runnable() {
            public void run() {
                final Window window = TreeViewModel.getModel().getWindow();
                if (window != null) {
                    FileDialog fileDialog = new FileDialog(shell, SWT.SAVE);
                    fileDialog.setFilterExtensions(new String[] {
                        "*.psd" //$NON-NLS-1$
                    });
                    fileDialog.setFilterNames(new String[] {
                        "Photoshop Document (*.psd)"
                    });
                    fileDialog.setText("Choose where to save the window layers");
                    final String fileName = fileDialog.open();
                    if (fileName != null) {
                        executeInBackground("Saving window layers", new Runnable() {
                            public void run() {
                                PsdFile psdFile = DeviceBridge.captureLayers(window);
                                if (psdFile != null) {
                                    String extensionedFileName = fileName;
                                    if (!extensionedFileName.toLowerCase().endsWith(".psd")) { //$NON-NLS-1$
                                        extensionedFileName += ".psd"; //$NON-NLS-1$
                                    }
                                    try {
                                        psdFile.write(new FileOutputStream(extensionedFileName));
                                    } catch (FileNotFoundException e) {
                                        Log.e(TAG, "Unable to write to file " + fileName);
                                    }
                                }
                            }
                        });
                    }
                }
            }
        });
    }

    public void reloadViewHierarchy() {
        Window window = TreeViewModel.getModel().getWindow();
        if (window != null) {
            loadViewTreeData(window);
        }
    }

    public void invalidateCurrentNode() {
        final DrawableViewNode selectedNode = TreeViewModel.getModel().getSelection();
        if (selectedNode != null) {
            executeInBackground("Invalidating view", new Runnable() {
                public void run() {
                    DeviceBridge.invalidateView(selectedNode.viewNode);
                }
            });
        }
    }

    public void relayoutCurrentNode() {
        final DrawableViewNode selectedNode = TreeViewModel.getModel().getSelection();
        if (selectedNode != null) {
            executeInBackground("Request layout", new Runnable() {
                public void run() {
                    DeviceBridge.requestLayout(selectedNode.viewNode);
                }
            });
        }
    }

    public void loadAllViews() {
        executeInBackground("Loading all views", new Runnable() {
            public void run() {
                DrawableViewNode tree = TreeViewModel.getModel().getTree();
                if (tree != null) {
                    loadViewRecursive(tree.viewNode);
                    // Force the layout viewer to redraw.
                    TreeViewModel.getModel().notifySelectionChanged();
                }
            }
        });
    }

    private void loadViewRecursive(ViewNode viewNode) {
        Image image = DeviceBridge.loadCapture(viewNode.window, viewNode);
        if (image == null) {
            return;
        }
        viewNode.image = image;
        final int N = viewNode.children.size();
        for (int i = 0; i < N; i++) {
            loadViewRecursive(viewNode.children.get(i));
        }
    }

    public void filterNodes(String filterText) {
        this.mFilterText = filterText;
        DrawableViewNode tree = TreeViewModel.getModel().getTree();
        if (tree != null) {
            tree.viewNode.filter(filterText);
            // Force redraw
            TreeViewModel.getModel().notifySelectionChanged();
        }
    }

    public String getFilterText() {
        return mFilterText;
    }

    private static class PixelPerfectAutoRefreshTask extends TimerTask {
        @Override
        public void run() {
            HierarchyViewerDirector.getDirector().refreshPixelPerfect();
        }
    };

    public void setPixelPerfectAutoRefresh(boolean value) {
        synchronized (mPixelPerfectRefreshTimer) {
            if (value == mAutoRefresh) {
                return;
            }
            mAutoRefresh = value;
            if (mAutoRefresh) {
                mCurrentAutoRefreshTask = new PixelPerfectAutoRefreshTask();
                mPixelPerfectRefreshTimer.schedule(mCurrentAutoRefreshTask,
                        mPixelPerfectAutoRefreshInterval * 1000,
                        mPixelPerfectAutoRefreshInterval * 1000);
            } else {
                mCurrentAutoRefreshTask.cancel();
                mCurrentAutoRefreshTask = null;
            }
        }
    }

    public void setPixelPerfectAutoRefreshInterval(int value) {
        synchronized (mPixelPerfectRefreshTimer) {
            if (mPixelPerfectAutoRefreshInterval == value) {
                return;
            }
            mPixelPerfectAutoRefreshInterval = value;
            if (mAutoRefresh) {
                mCurrentAutoRefreshTask.cancel();
                long timeLeft =
                        Math.max(0, mPixelPerfectAutoRefreshInterval
                                * 1000
                                - (System.currentTimeMillis() - mCurrentAutoRefreshTask
                                        .scheduledExecutionTime()));
                mCurrentAutoRefreshTask = new PixelPerfectAutoRefreshTask();
                mPixelPerfectRefreshTimer.schedule(mCurrentAutoRefreshTask, timeLeft,
                        mPixelPerfectAutoRefreshInterval * 1000);
            }
        }
    }

    public int getPixelPerfectAutoRefreshInverval() {
        return mPixelPerfectAutoRefreshInterval;
    }
}
