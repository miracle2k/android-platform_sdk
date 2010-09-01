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

package com.android.hierarchyviewer;

import com.android.ddmlib.Log;
import com.android.ddmuilib.ImageLoader;
import com.android.hierarchyviewer.actions.AboutAction;
import com.android.hierarchyviewer.actions.LoadAllViewsAction;
import com.android.hierarchyviewer.actions.QuitAction;
import com.android.hierarchyviewer.actions.ShowOverlayAction;
import com.android.hierarchyviewer.util.ActionButton;
import com.android.hierarchyviewerlib.HierarchyViewerDirector;
import com.android.hierarchyviewerlib.actions.CapturePSDAction;
import com.android.hierarchyviewerlib.actions.DisplayViewAction;
import com.android.hierarchyviewerlib.actions.InspectScreenshotAction;
import com.android.hierarchyviewerlib.actions.InvalidateAction;
import com.android.hierarchyviewerlib.actions.LoadOverlayAction;
import com.android.hierarchyviewerlib.actions.LoadViewHierarchyAction;
import com.android.hierarchyviewerlib.actions.PixelPerfectAutoRefreshAction;
import com.android.hierarchyviewerlib.actions.RefreshPixelPerfectAction;
import com.android.hierarchyviewerlib.actions.RefreshPixelPerfectTreeAction;
import com.android.hierarchyviewerlib.actions.RefreshViewAction;
import com.android.hierarchyviewerlib.actions.RefreshWindowsAction;
import com.android.hierarchyviewerlib.actions.RequestLayoutAction;
import com.android.hierarchyviewerlib.actions.SavePixelPerfectAction;
import com.android.hierarchyviewerlib.actions.SaveTreeViewAction;
import com.android.hierarchyviewerlib.models.PixelPerfectModel;
import com.android.hierarchyviewerlib.models.TreeViewModel;
import com.android.hierarchyviewerlib.models.PixelPerfectModel.IImageChangeListener;
import com.android.hierarchyviewerlib.models.TreeViewModel.ITreeChangeListener;
import com.android.hierarchyviewerlib.ui.DeviceSelector;
import com.android.hierarchyviewerlib.ui.LayoutViewer;
import com.android.hierarchyviewerlib.ui.PixelPerfect;
import com.android.hierarchyviewerlib.ui.PixelPerfectControls;
import com.android.hierarchyviewerlib.ui.PixelPerfectLoupe;
import com.android.hierarchyviewerlib.ui.PixelPerfectPixelPanel;
import com.android.hierarchyviewerlib.ui.PixelPerfectTree;
import com.android.hierarchyviewerlib.ui.PropertyViewer;
import com.android.hierarchyviewerlib.ui.TreeView;
import com.android.hierarchyviewerlib.ui.TreeViewControls;
import com.android.hierarchyviewerlib.ui.TreeViewOverview;

import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.window.ApplicationWindow;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Shell;

public class HierarchyViewerApplication extends ApplicationWindow {

    private static final int INITIAL_WIDTH = 1024;
    private static final int INITIAL_HEIGHT = 768;

    private static HierarchyViewerApplication sMainWindow;

    // Images for moving between the 3 main windows.
    private Image mDeviceViewImage;
    private Image mPixelPerfectImage;
    private Image mTreeViewImage;
    private Image mDeviceViewSelectedImage;
    private Image mPixelPerfectSelectedImage;
    private Image mTreeViewSelectedImage;

    // And their buttons
    private Button mTreeViewButton;
    private Button mPixelPerfectButton;
    private Button mDeviceViewButton;

    private Label mProgressLabel;
    private ProgressBar mProgressBar;
    private String mProgressString;

    private Composite mDeviceSelectorPanel;
    private Composite mTreeViewPanel;
    private Composite mPixelPerfectPanel;
    private StackLayout mMainWindowStackLayout;
    private DeviceSelector mDeviceSelector;
    private Composite mStatusBar;
    private TreeView mTreeView;
    private Composite mMainWindow;
    private Image mOnBlackImage;
    private Image mOnWhiteImage;
    private Button mOnBlackWhiteButton;
    private Button mShowExtras;
    private LayoutViewer mLayoutViewer;
    private PixelPerfectLoupe mPixelPerfectLoupe;
    private Composite mTreeViewControls;

    private HierarchyViewerDirector mDirector;

    /*
     * If a thread bails with an uncaught exception, bring the whole
     * thing down.
     */
    private static class UncaughtHandler implements Thread.UncaughtExceptionHandler {
        public void uncaughtException(Thread t, Throwable e) {
            Log.e("HierarchyViewer", "shutting down due to uncaught exception");
            Log.e("HierarchyViewer", e);
            System.exit(1);
        }
    }

    public static final HierarchyViewerApplication getMainWindow() {
        return sMainWindow;
    }

    public HierarchyViewerApplication() {
        super(null /*shell*/);

        sMainWindow = this;

        addMenuBar();
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("Hierarchy Viewer");
        ImageLoader imageLoader = ImageLoader.getLoader(HierarchyViewerDirector.class);
        Image image = imageLoader.loadImage("load-view-hierarchy.png", Display.getDefault()); //$NON-NLS-1$
        shell.setImage(image);
    }

    @Override
    public MenuManager createMenuManager() {
        return new MenuManager();
    }

    public void run() {
        setBlockOnOpen(true);

        open();

        TreeViewModel.getModel().removeTreeChangeListener(mTreeChangeListener);
        PixelPerfectModel.getModel().removeImageChangeListener(mImageChangeListener);

        ImageLoader.dispose();
        mDirector.stopListenForDevices();
        mDirector.stopDebugBridge();
        mDirector.terminate();
    }

    @Override
    protected void initializeBounds() {
        Rectangle monitorArea = Display.getDefault().getPrimaryMonitor().getBounds();
        getShell().setSize(Math.min(monitorArea.width, INITIAL_WIDTH),
                Math.min(monitorArea.height, INITIAL_HEIGHT));
        getShell().setLocation(monitorArea.x + (monitorArea.width - INITIAL_WIDTH) / 2,
                monitorArea.y + (monitorArea.height - INITIAL_HEIGHT) / 2);
    }

    private void loadResources() {
        ImageLoader imageLoader = ImageLoader.getLoader(HierarchyViewerDirector.class);
        mTreeViewImage = imageLoader.loadImage("tree-view.png", Display.getDefault()); //$NON-NLS-1$
        mTreeViewSelectedImage =
                imageLoader.loadImage("tree-view-selected.png", Display.getDefault()); //$NON-NLS-1$
        mPixelPerfectImage = imageLoader.loadImage("pixel-perfect-view.png", Display.getDefault()); //$NON-NLS-1$
        mPixelPerfectSelectedImage =
                imageLoader.loadImage("pixel-perfect-view-selected.png", Display.getDefault()); //$NON-NLS-1$
        mDeviceViewImage = imageLoader.loadImage("device-view.png", Display.getDefault()); //$NON-NLS-1$
        mDeviceViewSelectedImage =
                imageLoader.loadImage("device-view-selected.png", Display.getDefault()); //$NON-NLS-1$
        mOnBlackImage = imageLoader.loadImage("on-black.png", Display.getDefault()); //$NON-NLS-1$
        mOnWhiteImage = imageLoader.loadImage("on-white.png", Display.getDefault()); //$NON-NLS-1$
    }

    @Override
    protected Control createContents(Composite parent) {
        // create this only once the window is opened to please SWT on Mac
        mDirector = HierarchyViewerApplicationDirector.createDirector();
        mDirector.initDebugBridge();
        mDirector.startListenForDevices();
        mDirector.populateDeviceSelectionModel();

        TreeViewModel.getModel().addTreeChangeListener(mTreeChangeListener);
        PixelPerfectModel.getModel().addImageChangeListener(mImageChangeListener);

        loadResources();

        Composite control = new Composite(parent, SWT.NONE);
        GridLayout mainLayout = new GridLayout();
        mainLayout.marginHeight = mainLayout.marginWidth = 0;
        mainLayout.verticalSpacing = mainLayout.horizontalSpacing = 0;
        control.setLayout(mainLayout);
        mMainWindow = new Composite(control, SWT.NONE);
        mMainWindow.setLayoutData(new GridData(GridData.FILL_BOTH));
        mMainWindowStackLayout = new StackLayout();
        mMainWindow.setLayout(mMainWindowStackLayout);

        buildDeviceSelectorPanel(mMainWindow);
        buildTreeViewPanel(mMainWindow);
        buildPixelPerfectPanel(mMainWindow);

        buildStatusBar(control);

        showDeviceSelector();

        return control;
    }


    private void buildStatusBar(Composite parent) {
        mStatusBar = new Composite(parent, SWT.NONE);
        mStatusBar.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        FormLayout statusBarLayout = new FormLayout();
        statusBarLayout.marginHeight = statusBarLayout.marginWidth = 2;

        mStatusBar.setLayout(statusBarLayout);

        mDeviceViewButton = new Button(mStatusBar, SWT.TOGGLE);
        mDeviceViewButton.setImage(mDeviceViewImage);
        mDeviceViewButton.setToolTipText("Switch to the window selection view");
        mDeviceViewButton.addSelectionListener(deviceViewButtonSelectionListener);
        FormData deviceViewButtonFormData = new FormData();
        deviceViewButtonFormData.left = new FormAttachment();
        mDeviceViewButton.setLayoutData(deviceViewButtonFormData);

        mTreeViewButton = new Button(mStatusBar, SWT.TOGGLE);
        mTreeViewButton.setImage(mTreeViewImage);
        mTreeViewButton.setEnabled(false);
        mTreeViewButton.setToolTipText("Switch to the tree view");
        mTreeViewButton.addSelectionListener(treeViewButtonSelectionListener);
        FormData treeViewButtonFormData = new FormData();
        treeViewButtonFormData.left = new FormAttachment(mDeviceViewButton, 2);
        mTreeViewButton.setLayoutData(treeViewButtonFormData);

        mPixelPerfectButton = new Button(mStatusBar, SWT.TOGGLE);
        mPixelPerfectButton.setImage(mPixelPerfectImage);
        mPixelPerfectButton.setEnabled(false);
        mPixelPerfectButton.setToolTipText("Switch to the pixel perfect view");
        mPixelPerfectButton.addSelectionListener(pixelPerfectButtonSelectionListener);
        FormData pixelPerfectButtonFormData = new FormData();
        pixelPerfectButtonFormData.left = new FormAttachment(mTreeViewButton, 2);
        mPixelPerfectButton.setLayoutData(pixelPerfectButtonFormData);

        // Tree View control panel...
        mTreeViewControls = new TreeViewControls(mStatusBar);
        FormData treeViewControlsFormData = new FormData();
        treeViewControlsFormData.left = new FormAttachment(mPixelPerfectButton, 2);
        treeViewControlsFormData.top = new FormAttachment(mTreeViewButton, 0, SWT.CENTER);
        treeViewControlsFormData.width = 552;
        mTreeViewControls.setLayoutData(treeViewControlsFormData);

        // Progress stuff
        mProgressLabel = new Label(mStatusBar, SWT.RIGHT);

        mProgressBar = new ProgressBar(mStatusBar, SWT.HORIZONTAL | SWT.INDETERMINATE | SWT.SMOOTH);
        FormData progressBarFormData = new FormData();
        progressBarFormData.right = new FormAttachment(100, 0);
        progressBarFormData.top = new FormAttachment(mTreeViewButton, 0, SWT.CENTER);
        mProgressBar.setLayoutData(progressBarFormData);

        FormData progressLabelFormData = new FormData();
        progressLabelFormData.right = new FormAttachment(mProgressBar, -2);
        progressLabelFormData.top = new FormAttachment(mTreeViewButton, 0, SWT.CENTER);
        mProgressLabel.setLayoutData(progressLabelFormData);

        if (mProgressString == null) {
            mProgressLabel.setVisible(false);
            mProgressBar.setVisible(false);
        } else {
            mProgressLabel.setText(mProgressString);
        }
    }

    private void buildDeviceSelectorPanel(Composite parent) {
        mDeviceSelectorPanel = new Composite(parent, SWT.NONE);
        GridLayout gridLayout = new GridLayout();
        gridLayout.marginWidth = gridLayout.marginHeight = 0;
        gridLayout.horizontalSpacing = gridLayout.verticalSpacing = 0;
        mDeviceSelectorPanel.setLayout(gridLayout);

        Composite buttonPanel = new Composite(mDeviceSelectorPanel, SWT.NONE);
        buttonPanel.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        GridLayout buttonLayout = new GridLayout();
        buttonLayout.marginWidth = buttonLayout.marginHeight = 0;
        buttonLayout.horizontalSpacing = buttonLayout.verticalSpacing = 0;
        buttonPanel.setLayout(buttonLayout);

        Composite innerButtonPanel = new Composite(buttonPanel, SWT.NONE);
        innerButtonPanel.setLayoutData(new GridData(GridData.FILL_VERTICAL));
        GridLayout innerButtonPanelLayout = new GridLayout(3, true);
        innerButtonPanelLayout.marginWidth = innerButtonPanelLayout.marginHeight = 2;
        innerButtonPanelLayout.horizontalSpacing = innerButtonPanelLayout.verticalSpacing = 2;
        innerButtonPanel.setLayout(innerButtonPanelLayout);

        ActionButton refreshWindows =
                new ActionButton(innerButtonPanel, RefreshWindowsAction.getAction());
        refreshWindows.setLayoutData(new GridData(GridData.FILL_BOTH));

        ActionButton loadViewHierarchyButton =
                new ActionButton(innerButtonPanel, LoadViewHierarchyAction.getAction());
        loadViewHierarchyButton.setLayoutData(new GridData(GridData.FILL_BOTH));

        ActionButton inspectScreenshotButton =
                new ActionButton(innerButtonPanel, InspectScreenshotAction.getAction());
        inspectScreenshotButton.setLayoutData(new GridData(GridData.FILL_BOTH));

        Composite deviceSelectorContainer = new Composite(mDeviceSelectorPanel, SWT.BORDER);
        deviceSelectorContainer.setLayoutData(new GridData(GridData.FILL_BOTH));
        deviceSelectorContainer.setLayout(new FillLayout());
        mDeviceSelector = new DeviceSelector(deviceSelectorContainer, true, true);
    }

    public void buildTreeViewPanel(Composite parent) {
        mTreeViewPanel = new Composite(parent, SWT.NONE);
        GridLayout gridLayout = new GridLayout();
        gridLayout.marginWidth = gridLayout.marginHeight = 0;
        gridLayout.horizontalSpacing = gridLayout.verticalSpacing = 0;
        mTreeViewPanel.setLayout(gridLayout);

        Composite buttonPanel = new Composite(mTreeViewPanel, SWT.NONE);
        buttonPanel.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        GridLayout buttonLayout = new GridLayout();
        buttonLayout.marginWidth = buttonLayout.marginHeight = 0;
        buttonLayout.horizontalSpacing = buttonLayout.verticalSpacing = 0;
        buttonPanel.setLayout(buttonLayout);

        Composite innerButtonPanel = new Composite(buttonPanel, SWT.NONE);
        innerButtonPanel.setLayoutData(new GridData(GridData.FILL_VERTICAL));
        GridLayout innerButtonPanelLayout = new GridLayout(6, true);
        innerButtonPanelLayout.marginWidth = innerButtonPanelLayout.marginHeight = 2;
        innerButtonPanelLayout.horizontalSpacing = innerButtonPanelLayout.verticalSpacing = 2;
        innerButtonPanel.setLayout(innerButtonPanelLayout);

        ActionButton saveTreeView =
                new ActionButton(innerButtonPanel, SaveTreeViewAction.getAction(getShell()));
        saveTreeView.setLayoutData(new GridData(GridData.FILL_BOTH));

        ActionButton capturePSD =
                new ActionButton(innerButtonPanel, CapturePSDAction.getAction(getShell()));
        capturePSD.setLayoutData(new GridData(GridData.FILL_BOTH));

        ActionButton refreshViewAction =
                new ActionButton(innerButtonPanel, RefreshViewAction.getAction());
        refreshViewAction.setLayoutData(new GridData(GridData.FILL_BOTH));

        ActionButton displayView =
                new ActionButton(innerButtonPanel, DisplayViewAction.getAction(getShell()));
        displayView.setLayoutData(new GridData(GridData.FILL_BOTH));

        ActionButton invalidate = new ActionButton(innerButtonPanel, InvalidateAction.getAction());
        invalidate.setLayoutData(new GridData(GridData.FILL_BOTH));

        ActionButton requestLayout =
                new ActionButton(innerButtonPanel, RequestLayoutAction.getAction());
        requestLayout.setLayoutData(new GridData(GridData.FILL_BOTH));

        SashForm mainSash = new SashForm(mTreeViewPanel, SWT.HORIZONTAL | SWT.SMOOTH);
        mainSash.setLayoutData(new GridData(GridData.FILL_BOTH));
        Composite treeViewContainer = new Composite(mainSash, SWT.BORDER);
        treeViewContainer.setLayout(new FillLayout());
        mTreeView = new TreeView(treeViewContainer);

        SashForm sideSash = new SashForm(mainSash, SWT.VERTICAL | SWT.SMOOTH);

        mainSash.SASH_WIDTH = 4;
        mainSash.setWeights(new int[] {
                7, 3
        });

        Composite treeViewOverviewContainer = new Composite(sideSash, SWT.BORDER);
        treeViewOverviewContainer.setLayout(new FillLayout());
        new TreeViewOverview(treeViewOverviewContainer);

        Composite propertyViewerContainer = new Composite(sideSash, SWT.BORDER);
        propertyViewerContainer.setLayout(new FillLayout());
        new PropertyViewer(propertyViewerContainer);

        Composite layoutViewerContainer = new Composite(sideSash, SWT.NONE);
        GridLayout layoutViewerLayout = new GridLayout();
        layoutViewerLayout.marginWidth = layoutViewerLayout.marginHeight = 0;
        layoutViewerLayout.horizontalSpacing = layoutViewerLayout.verticalSpacing = 1;
        layoutViewerContainer.setLayout(layoutViewerLayout);

        Composite fullButtonBar = new Composite(layoutViewerContainer, SWT.NONE);
        fullButtonBar.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        GridLayout fullButtonBarLayout = new GridLayout(2, false);
        fullButtonBarLayout.marginWidth = fullButtonBarLayout.marginHeight = 0;
        fullButtonBarLayout.marginRight = 2;
        fullButtonBarLayout.horizontalSpacing = fullButtonBarLayout.verticalSpacing = 0;
        fullButtonBar.setLayout(fullButtonBarLayout);

        Composite buttonBar = new Composite(fullButtonBar, SWT.NONE);
        buttonBar.setLayoutData(new GridData(GridData.FILL_VERTICAL));
        RowLayout rowLayout = new RowLayout(SWT.HORIZONTAL);
        rowLayout.marginLeft =
                rowLayout.marginRight = rowLayout.marginTop = rowLayout.marginBottom = 0;
        rowLayout.pack = true;
        rowLayout.center = true;
        buttonBar.setLayout(rowLayout);

        mOnBlackWhiteButton = new Button(buttonBar, SWT.PUSH);
        mOnBlackWhiteButton.setImage(mOnWhiteImage);
        mOnBlackWhiteButton.addSelectionListener(onBlackWhiteSelectionListener);
        mOnBlackWhiteButton.setToolTipText("Change layout viewer background color");

        mShowExtras = new Button(buttonBar, SWT.CHECK);
        mShowExtras.setText("Show Extras");
        mShowExtras.addSelectionListener(showExtrasSelectionListener);
        mShowExtras.setToolTipText("Show images");

        ActionButton loadAllViewsButton =
                new ActionButton(fullButtonBar, LoadAllViewsAction.getAction());
        loadAllViewsButton.setLayoutData(new GridData(GridData.END, GridData.CENTER, true, true));
        loadAllViewsButton.addSelectionListener(loadAllViewsSelectionListener);

        Composite layoutViewerMainContainer = new Composite(layoutViewerContainer, SWT.BORDER);
        layoutViewerMainContainer.setLayoutData(new GridData(GridData.FILL_BOTH));
        layoutViewerMainContainer.setLayout(new FillLayout());
        mLayoutViewer = new LayoutViewer(layoutViewerMainContainer);

        sideSash.SASH_WIDTH = 4;
        sideSash.setWeights(new int[] {
                238, 332, 416
        });

    }

    private void buildPixelPerfectPanel(Composite parent) {
        mPixelPerfectPanel = new Composite(parent, SWT.NONE);
        GridLayout gridLayout = new GridLayout();
        gridLayout.marginWidth = gridLayout.marginHeight = 0;
        gridLayout.horizontalSpacing = gridLayout.verticalSpacing = 0;
        mPixelPerfectPanel.setLayout(gridLayout);

        Composite buttonPanel = new Composite(mPixelPerfectPanel, SWT.NONE);
        buttonPanel.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        GridLayout buttonLayout = new GridLayout();
        buttonLayout.marginWidth = buttonLayout.marginHeight = 0;
        buttonLayout.horizontalSpacing = buttonLayout.verticalSpacing = 0;
        buttonPanel.setLayout(buttonLayout);

        Composite innerButtonPanel = new Composite(buttonPanel, SWT.NONE);
        innerButtonPanel.setLayoutData(new GridData(GridData.FILL_VERTICAL));
        GridLayout innerButtonPanelLayout = new GridLayout(6, true);
        innerButtonPanelLayout.marginWidth = innerButtonPanelLayout.marginHeight = 2;
        innerButtonPanelLayout.horizontalSpacing = innerButtonPanelLayout.verticalSpacing = 2;
        innerButtonPanel.setLayout(innerButtonPanelLayout);

        ActionButton saveTreeView =
                new ActionButton(innerButtonPanel, SavePixelPerfectAction.getAction(getShell()));
        saveTreeView.setLayoutData(new GridData(GridData.FILL_BOTH));

        ActionButton refreshPixelPerfect =
                new ActionButton(innerButtonPanel, RefreshPixelPerfectAction.getAction());
        refreshPixelPerfect.setLayoutData(new GridData(GridData.FILL_BOTH));

        ActionButton refreshPixelPerfectTree =
                new ActionButton(innerButtonPanel, RefreshPixelPerfectTreeAction.getAction());
        refreshPixelPerfectTree.setLayoutData(new GridData(GridData.FILL_BOTH));

        ActionButton loadOverlay =
                new ActionButton(innerButtonPanel, LoadOverlayAction.getAction(getShell()));
        loadOverlay.setLayoutData(new GridData(GridData.FILL_BOTH));

        ActionButton showInLoupe =
                new ActionButton(innerButtonPanel, ShowOverlayAction.getAction());
        showInLoupe.setLayoutData(new GridData(GridData.FILL_BOTH));

        ActionButton autoRefresh =
                new ActionButton(innerButtonPanel, PixelPerfectAutoRefreshAction.getAction());
        autoRefresh.setLayoutData(new GridData(GridData.FILL_BOTH));

        SashForm mainSash = new SashForm(mPixelPerfectPanel, SWT.HORIZONTAL | SWT.SMOOTH);
        mainSash.setLayoutData(new GridData(GridData.FILL_BOTH));
        mainSash.SASH_WIDTH = 4;

        Composite pixelPerfectTreeContainer = new Composite(mainSash, SWT.BORDER);
        pixelPerfectTreeContainer.setLayout(new FillLayout());
        new PixelPerfectTree(pixelPerfectTreeContainer);

        Composite pixelPerfectLoupeContainer = new Composite(mainSash, SWT.NONE);
        GridLayout loupeLayout = new GridLayout();
        loupeLayout.marginWidth = loupeLayout.marginHeight = 0;
        loupeLayout.horizontalSpacing = loupeLayout.verticalSpacing = 0;
        pixelPerfectLoupeContainer.setLayout(loupeLayout);

        Composite pixelPerfectLoupeBorder = new Composite(pixelPerfectLoupeContainer, SWT.BORDER);
        pixelPerfectLoupeBorder.setLayoutData(new GridData(GridData.FILL_BOTH));
        GridLayout pixelPerfectLoupeBorderGridLayout = new GridLayout();
        pixelPerfectLoupeBorderGridLayout.marginWidth =
                pixelPerfectLoupeBorderGridLayout.marginHeight = 0;
        pixelPerfectLoupeBorderGridLayout.horizontalSpacing =
                pixelPerfectLoupeBorderGridLayout.verticalSpacing = 0;
        pixelPerfectLoupeBorder.setLayout(pixelPerfectLoupeBorderGridLayout);

        mPixelPerfectLoupe = new PixelPerfectLoupe(pixelPerfectLoupeBorder);
        mPixelPerfectLoupe.setLayoutData(new GridData(GridData.FILL_BOTH));

        PixelPerfectPixelPanel pixelPerfectPixelPanel =
                new PixelPerfectPixelPanel(pixelPerfectLoupeBorder);
        pixelPerfectPixelPanel.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        PixelPerfectControls pixelPerfectControls =
                new PixelPerfectControls(pixelPerfectLoupeContainer);
        pixelPerfectControls.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));


        Composite pixelPerfectContainer = new Composite(mainSash, SWT.BORDER);
        pixelPerfectContainer.setLayout(new FillLayout());
        new PixelPerfect(pixelPerfectContainer);

        mainSash.setWeights(new int[] {
                272, 376, 346
        });

    }

    public void showOverlayInLoupe(boolean value) {
        mPixelPerfectLoupe.setShowOverlay(value);
    }

    // Shows the progress notification...
    public void startTask(final String taskName) {
        mProgressString = taskName;
        Display.getDefault().syncExec(new Runnable() {
            public void run() {
                if (mProgressLabel != null && mProgressBar != null) {
                    mProgressLabel.setText(taskName);
                    mProgressLabel.setVisible(true);
                    mProgressBar.setVisible(true);
                    mStatusBar.layout();
                }
            }
        });
    }

    // And hides it!
    public void endTask() {
        mProgressString = null;
        Display.getDefault().syncExec(new Runnable() {
            public void run() {
                if (mProgressLabel != null && mProgressBar != null) {
                    mProgressLabel.setVisible(false);
                    mProgressBar.setVisible(false);
                }
            }
        });
    }

    public void showDeviceSelector() {
        // Show the menus.
        MenuManager mm = getMenuBarManager();
        mm.removeAll();

        String os = System.getProperty("os.name"); //$NON-NLS-1$
        if (os.startsWith("Mac OS") == false) { //$NON-NLS-1$
            MenuManager file = new MenuManager("&File");
            mm.add(file);

            file.add(QuitAction.getAction());
        }

        MenuManager device = new MenuManager("&Devices");
        mm.add(device);

        device.add(RefreshWindowsAction.getAction());
        device.add(LoadViewHierarchyAction.getAction());
        device.add(InspectScreenshotAction.getAction());

        MenuManager help = new MenuManager("&Help");
        mm.add(help);

        help.add(AboutAction.getAction(getShell()));

        mm.updateAll(true);

        mDeviceViewButton.setSelection(true);
        mDeviceViewButton.setImage(mDeviceViewSelectedImage);

        mTreeViewButton.setSelection(false);
        mTreeViewButton.setImage(mTreeViewImage);

        mPixelPerfectButton.setSelection(false);
        mPixelPerfectButton.setImage(mPixelPerfectImage);

        mMainWindowStackLayout.topControl = mDeviceSelectorPanel;

        mMainWindow.layout();

        mDeviceSelector.setFocus();

        mTreeViewControls.setVisible(false);
    }

    public void showTreeView() {
        // Show the menus.
        MenuManager mm = getMenuBarManager();
        mm.removeAll();

        String os = System.getProperty("os.name"); //$NON-NLS-1$
        if (os.startsWith("Mac OS") == false) { //$NON-NLS-1$
            MenuManager file = new MenuManager("&File");
            mm.add(file);

            file.add(QuitAction.getAction());
        }

        MenuManager treeViewMenu = new MenuManager("&Tree View");
        mm.add(treeViewMenu);

        treeViewMenu.add(SaveTreeViewAction.getAction(getShell()));
        treeViewMenu.add(CapturePSDAction.getAction(getShell()));
        treeViewMenu.add(new Separator());
        treeViewMenu.add(RefreshViewAction.getAction());
        treeViewMenu.add(DisplayViewAction.getAction(getShell()));
        treeViewMenu.add(new Separator());
        treeViewMenu.add(InvalidateAction.getAction());
        treeViewMenu.add(RequestLayoutAction.getAction());

        MenuManager help = new MenuManager("&Help");
        mm.add(help);

        help.add(AboutAction.getAction(getShell()));

        mm.updateAll(true);

        mDeviceViewButton.setSelection(false);
        mDeviceViewButton.setImage(mDeviceViewImage);

        mTreeViewButton.setSelection(true);
        mTreeViewButton.setImage(mTreeViewSelectedImage);

        mPixelPerfectButton.setSelection(false);
        mPixelPerfectButton.setImage(mPixelPerfectImage);

        mMainWindowStackLayout.topControl = mTreeViewPanel;

        mMainWindow.layout();

        mTreeView.setFocus();

        mTreeViewControls.setVisible(true);
    }

    public void showPixelPerfect() {
        // Show the menus.
        MenuManager mm = getMenuBarManager();
        mm.removeAll();

        String os = System.getProperty("os.name"); //$NON-NLS-1$
        if (os.startsWith("Mac OS") == false) { //$NON-NLS-1$
            MenuManager file = new MenuManager("&File");
            mm.add(file);

            file.add(QuitAction.getAction());
        }

        MenuManager pixelPerfect = new MenuManager("&Pixel Perfect");
        pixelPerfect.add(SavePixelPerfectAction.getAction(getShell()));
        pixelPerfect.add(RefreshPixelPerfectAction.getAction());
        pixelPerfect.add(RefreshPixelPerfectTreeAction.getAction());
        pixelPerfect.add(PixelPerfectAutoRefreshAction.getAction());
        pixelPerfect.add(new Separator());
        pixelPerfect.add(LoadOverlayAction.getAction(getShell()));
        pixelPerfect.add(ShowOverlayAction.getAction());

        mm.add(pixelPerfect);

        MenuManager help = new MenuManager("&Help");
        mm.add(help);

        help.add(AboutAction.getAction(getShell()));

        mm.updateAll(true);

        mDeviceViewButton.setSelection(false);
        mDeviceViewButton.setImage(mDeviceViewImage);

        mTreeViewButton.setSelection(false);
        mTreeViewButton.setImage(mTreeViewImage);

        mPixelPerfectButton.setSelection(true);
        mPixelPerfectButton.setImage(mPixelPerfectSelectedImage);

        mMainWindowStackLayout.topControl = mPixelPerfectPanel;

        mMainWindow.layout();

        mPixelPerfectLoupe.setFocus();

        mTreeViewControls.setVisible(false);
    }

    private SelectionListener deviceViewButtonSelectionListener = new SelectionListener() {
        public void widgetDefaultSelected(SelectionEvent e) {
            // pass
        }

        public void widgetSelected(SelectionEvent e) {
            mDeviceViewButton.setSelection(true);
            showDeviceSelector();
        }
    };

    private SelectionListener treeViewButtonSelectionListener = new SelectionListener() {
        public void widgetDefaultSelected(SelectionEvent e) {
            // pass
        }

        public void widgetSelected(SelectionEvent e) {
            mTreeViewButton.setSelection(true);
            showTreeView();
        }
    };

    private SelectionListener pixelPerfectButtonSelectionListener = new SelectionListener() {
        public void widgetDefaultSelected(SelectionEvent e) {
            // pass
        }

        public void widgetSelected(SelectionEvent e) {
            mPixelPerfectButton.setSelection(true);
            showPixelPerfect();
        }
    };

    private SelectionListener onBlackWhiteSelectionListener = new SelectionListener() {
        public void widgetDefaultSelected(SelectionEvent e) {
            // pass
        }

        public void widgetSelected(SelectionEvent e) {
            if (mLayoutViewer.getOnBlack()) {
                mLayoutViewer.setOnBlack(false);
                mOnBlackWhiteButton.setImage(mOnBlackImage);
            } else {
                mLayoutViewer.setOnBlack(true);
                mOnBlackWhiteButton.setImage(mOnWhiteImage);
            }
        }
    };

    private SelectionListener showExtrasSelectionListener = new SelectionListener() {
        public void widgetDefaultSelected(SelectionEvent e) {
            // pass
        }

        public void widgetSelected(SelectionEvent e) {
            mLayoutViewer.setShowExtras(mShowExtras.getSelection());
        }
    };

    private SelectionListener loadAllViewsSelectionListener = new SelectionListener() {
        public void widgetDefaultSelected(SelectionEvent e) {
            // pass
        }

        public void widgetSelected(SelectionEvent e) {
            mShowExtras.setSelection(true);
            showExtrasSelectionListener.widgetSelected(null);
        }
    };

    private ITreeChangeListener mTreeChangeListener = new ITreeChangeListener() {
        public void selectionChanged() {
            // pass
        }

        public void treeChanged() {
            Display.getDefault().syncExec(new Runnable() {
                public void run() {
                    if (TreeViewModel.getModel().getTree() == null) {
                        showDeviceSelector();
                        mTreeViewButton.setEnabled(false);
                    } else {
                        showTreeView();
                        mTreeViewButton.setEnabled(true);
                    }
                }
            });
        }

        public void viewportChanged() {
            // pass
        }

        public void zoomChanged() {
            // pass
        }
    };

    private IImageChangeListener mImageChangeListener = new IImageChangeListener() {

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
                    if (PixelPerfectModel.getModel().getImage() == null) {
                        mPixelPerfectButton.setEnabled(false);
                        showDeviceSelector();
                    } else {
                        mPixelPerfectButton.setEnabled(true);
                        showPixelPerfect();
                    }
                }
            });
        }

        public void overlayChanged() {
            // pass
        }

        public void overlayTransparencyChanged() {
            // pass
        }

        public void selectionChanged() {
            // pass
        }

        public void zoomChanged() {
            // pass
        }

    };

    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler(new UncaughtHandler());

        Display.setAppName("HierarchyViewer");
        new HierarchyViewerApplication().run();
    }
}
