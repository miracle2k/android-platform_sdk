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

import com.android.ddmlib.IDevice;
import com.android.ddmuilib.ImageLoader;
import com.android.hierarchyviewer.actions.AboutAction;
import com.android.hierarchyviewer.actions.CapturePSDAction;
import com.android.hierarchyviewer.actions.DisplayViewAction;
import com.android.hierarchyviewer.actions.InspectScreenshotAction;
import com.android.hierarchyviewer.actions.InvalidateAction;
import com.android.hierarchyviewer.actions.LoadAllViewsAction;
import com.android.hierarchyviewer.actions.LoadOverlayAction;
import com.android.hierarchyviewer.actions.LoadViewHierarchyAction;
import com.android.hierarchyviewer.actions.PixelPerfectAutoRefreshAction;
import com.android.hierarchyviewer.actions.QuitAction;
import com.android.hierarchyviewer.actions.RefreshPixelPerfectAction;
import com.android.hierarchyviewer.actions.RefreshPixelPerfectTreeAction;
import com.android.hierarchyviewer.actions.RefreshViewAction;
import com.android.hierarchyviewer.actions.RefreshWindowsAction;
import com.android.hierarchyviewer.actions.RequestLayoutAction;
import com.android.hierarchyviewer.actions.SavePixelPerfectAction;
import com.android.hierarchyviewer.actions.SaveTreeViewAction;
import com.android.hierarchyviewer.actions.ShowOverlayAction;
import com.android.hierarchyviewer.util.ActionButton;
import com.android.hierarchyviewerlib.HierarchyViewerDirector;
import com.android.hierarchyviewerlib.device.Window;
import com.android.hierarchyviewerlib.models.DeviceSelectionModel;
import com.android.hierarchyviewerlib.models.PixelPerfectModel;
import com.android.hierarchyviewerlib.models.TreeViewModel;
import com.android.hierarchyviewerlib.models.DeviceSelectionModel.WindowChangeListener;
import com.android.hierarchyviewerlib.models.PixelPerfectModel.ImageChangeListener;
import com.android.hierarchyviewerlib.models.TreeViewModel.TreeChangeListener;
import com.android.hierarchyviewerlib.ui.DeviceSelector;
import com.android.hierarchyviewerlib.ui.LayoutViewer;
import com.android.hierarchyviewerlib.ui.PixelPerfect;
import com.android.hierarchyviewerlib.ui.PixelPerfectLoupe;
import com.android.hierarchyviewerlib.ui.PixelPerfectPixelPanel;
import com.android.hierarchyviewerlib.ui.PixelPerfectTree;
import com.android.hierarchyviewerlib.ui.PropertyViewer;
import com.android.hierarchyviewerlib.ui.TreeView;
import com.android.hierarchyviewerlib.ui.TreeViewOverview;

import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.window.ApplicationWindow;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
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
import org.eclipse.swt.widgets.Slider;
import org.eclipse.swt.widgets.Text;

public class HierarchyViewerApplication extends ApplicationWindow {

    private static final int INITIAL_WIDTH = 1024;

    private static final int INITIAL_HEIGHT = 768;

    private static HierarchyViewerApplication APP;

    // Images for moving between the 3 main windows.

    private Image deviceViewImage;

    private Image pixelPerfectImage;

    private Image treeViewImage;

    private Image deviceViewSelectedImage;

    private Image pixelPerfectSelectedImage;

    private Image treeViewSelectedImage;

    // And their buttons

    private Button treeViewButton;

    private Button pixelPerfectButton;

    private Button deviceViewButton;


    private Label progressLabel;

    private ProgressBar progressBar;

    private String progressString;

    private Composite deviceSelectorPanel;

    private Composite treeViewPanel;

    private Composite pixelPerfectPanel;

    private StackLayout mainWindowStackLayout;

    private DeviceSelector deviceSelector;

    private Composite statusBar;

    private TreeView treeView;

    private Composite mainWindow;

    private Image onBlackImage;

    private Image onWhiteImage;

    private Button onBlackWhiteButton;

    private Button showExtras;

    private LayoutViewer layoutViewer;

    private StackLayout statusBarStackLayout;

    private Composite treeViewControls;

    private Slider zoomSlider;

    private Text filterText;

    private Composite statusBarControlPanel;

    private PixelPerfectLoupe pixelPerfectLoupe;

    private boolean autoRefresh = false;

    private int refreshInterval = 5;

    private int refreshTimeLeft = 5;

    private Slider overlaySlider;

    private Slider ppZoomSlider;

    private Slider refreshSlider;

    public static final HierarchyViewerApplication getApp() {
        return APP;
    }

    public HierarchyViewerApplication() {
        super(null);

        APP = this;

        addMenuBar();
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("Hierarchy Viewer");
        ImageLoader imageLoader = ImageLoader.getLoader(HierarchyViewerDirector.class);
        Image image = imageLoader.loadImage("load-view-hierarchy.png", Display.getDefault());
        shell.setImage(image);
    }

    @Override
    public MenuManager createMenuManager() {
        return new MenuManager();
    }

    public void run() {
        HierarchyViewerDirector director = HierarchyViewerApplicationDirector.createDirector();
        director.initDebugBridge();
        director.startListenForDevices();
        director.populateDeviceSelectionModel();
        DeviceSelectionModel.getModel().addWindowChangeListener(windowChangeListener);
        TreeViewModel.getModel().addTreeChangeListener(treeChangeListener);
        PixelPerfectModel.getModel().addImageChangeListener(imageChangeListener);

        setBlockOnOpen(true);

        Thread pixelPerfectRefreshingThread = new Thread(autoRefresher);
        pixelPerfectRefreshingThread.start();

        open();

        pixelPerfectRefreshingThread.interrupt();

        DeviceSelectionModel.getModel().removeWindowChangeListener(windowChangeListener);
        TreeViewModel.getModel().removeTreeChangeListener(treeChangeListener);
        PixelPerfectModel.getModel().removeImageChangeListener(imageChangeListener);

        Display.getCurrent().dispose();
        ImageLoader.dispose();
        director.stopListenForDevices();
        director.stopDebugBridge();
        director.terminate();
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
        treeViewImage = imageLoader.loadImage("tree-view.png", Display.getDefault());
        treeViewSelectedImage =
                imageLoader.loadImage("tree-view-selected.png", Display.getDefault());
        pixelPerfectImage = imageLoader.loadImage("pixel-perfect-view.png", Display.getDefault());
        pixelPerfectSelectedImage =
                imageLoader.loadImage("pixel-perfect-view-selected.png", Display.getDefault());
        deviceViewImage = imageLoader.loadImage("device-view.png", Display.getDefault());
        deviceViewSelectedImage =
                imageLoader.loadImage("device-view-selected.png", Display.getDefault());
        onBlackImage = imageLoader.loadImage("on-black.png", Display.getDefault());
        onWhiteImage = imageLoader.loadImage("on-white.png", Display.getDefault());
    }

    @Override
    protected Control createContents(Composite parent) {
        loadResources();

        Composite control = new Composite(parent, SWT.NONE);
        GridLayout mainLayout = new GridLayout();
        mainLayout.marginHeight = mainLayout.marginWidth = 0;
        mainLayout.verticalSpacing = mainLayout.horizontalSpacing = 0;
        control.setLayout(mainLayout);
        mainWindow = new Composite(control, SWT.NONE);
        mainWindow.setLayoutData(new GridData(GridData.FILL_BOTH));
        mainWindowStackLayout = new StackLayout();
        mainWindow.setLayout(mainWindowStackLayout);

        buildDeviceSelectorPanel(mainWindow);
        buildTreeViewPanel(mainWindow);
        buildPixelPerfectPanel(mainWindow);

        buildStatusBar(control);

        showDeviceSelector();

        return control;
    }


    private void buildStatusBar(Composite parent) {
        statusBar = new Composite(parent, SWT.NONE);
        statusBar.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        FormLayout statusBarLayout = new FormLayout();
        statusBarLayout.marginHeight = statusBarLayout.marginWidth = 2;

        statusBar.setLayout(statusBarLayout);

        deviceViewButton = new Button(statusBar, SWT.TOGGLE);
        deviceViewButton.setImage(deviceViewImage);
        deviceViewButton.setToolTipText("Switch to the window selection view");
        deviceViewButton.addSelectionListener(deviceViewButtonSelectionListener);
        FormData deviceViewButtonFormData = new FormData();
        deviceViewButtonFormData.left = new FormAttachment();
        deviceViewButton.setLayoutData(deviceViewButtonFormData);

        treeViewButton = new Button(statusBar, SWT.TOGGLE);
        treeViewButton.setImage(treeViewImage);
        treeViewButton.setEnabled(false);
        treeViewButton.setToolTipText("Switch to the tree view");
        treeViewButton.addSelectionListener(treeViewButtonSelectionListener);
        FormData treeViewButtonFormData = new FormData();
        treeViewButtonFormData.left = new FormAttachment(deviceViewButton, 2);
        treeViewButton.setLayoutData(treeViewButtonFormData);

        pixelPerfectButton = new Button(statusBar, SWT.TOGGLE);
        pixelPerfectButton.setImage(pixelPerfectImage);
        pixelPerfectButton.setEnabled(false);
        pixelPerfectButton.setToolTipText("Switch to the pixel perfect view");
        pixelPerfectButton.addSelectionListener(pixelPerfectButtonSelectionListener);
        FormData pixelPerfectButtonFormData = new FormData();
        pixelPerfectButtonFormData.left = new FormAttachment(treeViewButton, 2);
        pixelPerfectButton.setLayoutData(pixelPerfectButtonFormData);

        // Tree View control panel...
        statusBarControlPanel = new Composite(statusBar, SWT.NONE);
        FormData statusBarControlPanelFormData = new FormData();
        statusBarControlPanelFormData.left = new FormAttachment(pixelPerfectButton, 2);
        statusBarControlPanelFormData.top = new FormAttachment(treeViewButton, 0, SWT.CENTER);
        statusBarControlPanel.setLayoutData(statusBarControlPanelFormData);

        statusBarStackLayout = new StackLayout();
        statusBarControlPanel.setLayout(statusBarStackLayout);

        treeViewControls = new Composite(statusBarControlPanel, SWT.NONE);
        GridLayout treeViewControlLayout = new GridLayout(5, false);
        treeViewControlLayout.marginWidth = treeViewControlLayout.marginHeight = 2;
        treeViewControlLayout.verticalSpacing = treeViewControlLayout.horizontalSpacing = 4;
        treeViewControls.setLayout(treeViewControlLayout);

        Label filterLabel = new Label(treeViewControls, SWT.NONE);
        filterLabel.setText("Filter by class or id:");
        filterLabel.setLayoutData(new GridData(GridData.BEGINNING, GridData.CENTER, false, true));

        filterText = new Text(treeViewControls, SWT.LEFT | SWT.SINGLE);
        GridData filterTextGridData = new GridData(GridData.FILL_HORIZONTAL);
        filterTextGridData.widthHint = 148;
        filterText.setLayoutData(filterTextGridData);
        filterText.addModifyListener(filterTextModifyListener);

        Label smallZoomLabel = new Label(treeViewControls, SWT.NONE);
        smallZoomLabel.setText(" 20%");
        smallZoomLabel
                .setLayoutData(new GridData(GridData.BEGINNING, GridData.CENTER, false, true));

        zoomSlider = new Slider(treeViewControls, SWT.HORIZONTAL);
        GridData zoomSliderGridData = new GridData(GridData.CENTER, GridData.CENTER, false, false);
        zoomSliderGridData.widthHint = 190;
        zoomSlider.setLayoutData(zoomSliderGridData);
        zoomSlider.setMinimum((int) (TreeViewModel.MIN_ZOOM * 10));
        zoomSlider.setMaximum((int) (TreeViewModel.MAX_ZOOM * 10 + 1));
        zoomSlider.setThumb(1);
        zoomSlider.setSelection(10);

        zoomSlider.addSelectionListener(zoomSliderSelectionListener);

        Label largeZoomLabel = new Label(treeViewControls, SWT.NONE);
        largeZoomLabel
                .setLayoutData(new GridData(GridData.BEGINNING, GridData.CENTER, false, true));
        largeZoomLabel.setText("200%");

        // Progress stuff
        progressLabel = new Label(statusBar, SWT.RIGHT);

        progressBar = new ProgressBar(statusBar, SWT.HORIZONTAL | SWT.INDETERMINATE | SWT.SMOOTH);
        FormData progressBarFormData = new FormData();
        progressBarFormData.right = new FormAttachment(100, 0);
        progressBarFormData.top = new FormAttachment(treeViewButton, 0, SWT.CENTER);
        progressBar.setLayoutData(progressBarFormData);

        FormData progressLabelFormData = new FormData();
        progressLabelFormData.right = new FormAttachment(progressBar, -2);
        progressLabelFormData.top = new FormAttachment(treeViewButton, 0, SWT.CENTER);
        progressLabel.setLayoutData(progressLabelFormData);

        if (progressString == null) {
            progressLabel.setVisible(false);
            progressBar.setVisible(false);
        } else {
            progressLabel.setText(progressString);
        }
    }

    private void buildDeviceSelectorPanel(Composite parent) {
        deviceSelectorPanel = new Composite(parent, SWT.NONE);
        GridLayout gridLayout = new GridLayout();
        gridLayout.marginWidth = gridLayout.marginHeight = 0;
        gridLayout.horizontalSpacing = gridLayout.verticalSpacing = 0;
        deviceSelectorPanel.setLayout(gridLayout);

        Composite buttonPanel = new Composite(deviceSelectorPanel, SWT.NONE);
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
        LoadViewHierarchyAction.getAction().setEnabled(false);

        ActionButton inspectScreenshotButton =
                new ActionButton(innerButtonPanel, InspectScreenshotAction.getAction());
        inspectScreenshotButton.setLayoutData(new GridData(GridData.FILL_BOTH));
        InspectScreenshotAction.getAction().setEnabled(false);

        Composite deviceSelectorContainer = new Composite(deviceSelectorPanel, SWT.BORDER);
        deviceSelectorContainer.setLayoutData(new GridData(GridData.FILL_BOTH));
        deviceSelectorContainer.setLayout(new FillLayout());
        deviceSelector = new DeviceSelector(deviceSelectorContainer);
    }

    public void buildTreeViewPanel(Composite parent) {
        treeViewPanel = new Composite(parent, SWT.NONE);
        GridLayout gridLayout = new GridLayout();
        gridLayout.marginWidth = gridLayout.marginHeight = 0;
        gridLayout.horizontalSpacing = gridLayout.verticalSpacing = 0;
        treeViewPanel.setLayout(gridLayout);

        Composite buttonPanel = new Composite(treeViewPanel, SWT.NONE);
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

        SashForm mainSash = new SashForm(treeViewPanel, SWT.HORIZONTAL | SWT.SMOOTH);
        mainSash.setLayoutData(new GridData(GridData.FILL_BOTH));
        Composite treeViewContainer = new Composite(mainSash, SWT.BORDER);
        treeViewContainer.setLayout(new FillLayout());
        treeView = new TreeView(treeViewContainer);

        SashForm sideSash = new SashForm(mainSash, SWT.VERTICAL | SWT.SMOOTH);

        mainSash.SASH_WIDTH = 4;
        mainSash.setWeights(new int[] {
                7, 3
        });

        Composite treeViewOverviewContainer = new Composite(sideSash, SWT.BORDER);
        treeViewOverviewContainer.setLayout(new FillLayout());
        TreeViewOverview treeViewOverview = new TreeViewOverview(treeViewOverviewContainer);

        Composite propertyViewerContainer = new Composite(sideSash, SWT.BORDER);
        propertyViewerContainer.setLayout(new FillLayout());
        PropertyViewer propertyViewer = new PropertyViewer(propertyViewerContainer);

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

        onBlackWhiteButton = new Button(buttonBar, SWT.PUSH);
        onBlackWhiteButton.setImage(onWhiteImage);
        onBlackWhiteButton.addSelectionListener(onBlackWhiteSelectionListener);

        showExtras = new Button(buttonBar, SWT.CHECK);
        showExtras.setText("Show Extras");
        showExtras.addSelectionListener(showExtrasSelectionListener);

        ActionButton loadAllViewsButton =
                new ActionButton(fullButtonBar, LoadAllViewsAction.getAction());
        loadAllViewsButton.setLayoutData(new GridData(GridData.END, GridData.CENTER, true, true));
        loadAllViewsButton.addSelectionListener(loadAllViewsSelectionListener);

        Composite layoutViewerMainContainer = new Composite(layoutViewerContainer, SWT.BORDER);
        layoutViewerMainContainer.setLayoutData(new GridData(GridData.FILL_BOTH));
        layoutViewerMainContainer.setLayout(new FillLayout());
        layoutViewer = new LayoutViewer(layoutViewerMainContainer);

        sideSash.SASH_WIDTH = 4;
        sideSash.setWeights(new int[] {
                238, 332, 416
        });

    }

    private void buildPixelPerfectPanel(Composite parent) {
        pixelPerfectPanel = new Composite(parent, SWT.NONE);
        GridLayout gridLayout = new GridLayout();
        gridLayout.marginWidth = gridLayout.marginHeight = 0;
        gridLayout.horizontalSpacing = gridLayout.verticalSpacing = 0;
        pixelPerfectPanel.setLayout(gridLayout);

        Composite buttonPanel = new Composite(pixelPerfectPanel, SWT.NONE);
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

        ShowOverlayAction.getAction().setEnabled(false);

        ActionButton autoRefresh =
                new ActionButton(innerButtonPanel, PixelPerfectAutoRefreshAction.getAction());
        autoRefresh.setLayoutData(new GridData(GridData.FILL_BOTH));

        SashForm mainSash = new SashForm(pixelPerfectPanel, SWT.HORIZONTAL | SWT.SMOOTH);
        mainSash.setLayoutData(new GridData(GridData.FILL_BOTH));
        mainSash.SASH_WIDTH = 4;

        Composite pixelPerfectTreeContainer = new Composite(mainSash, SWT.BORDER);
        pixelPerfectTreeContainer.setLayout(new FillLayout());
        PixelPerfectTree pixelPerfectTree = new PixelPerfectTree(pixelPerfectTreeContainer);

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

        pixelPerfectLoupe = new PixelPerfectLoupe(pixelPerfectLoupeBorder);
        pixelPerfectLoupe.setLayoutData(new GridData(GridData.FILL_BOTH));

        PixelPerfectPixelPanel pixelPerfectPixelPanel =
                new PixelPerfectPixelPanel(pixelPerfectLoupeBorder);
        pixelPerfectPixelPanel.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        Composite pixelPerfectControls = new Composite(pixelPerfectLoupeContainer, SWT.NONE);
        pixelPerfectControls.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        pixelPerfectControls.setLayout(new FormLayout());

        Label overlayTransparencyRight = new Label(pixelPerfectControls, SWT.NONE);
        overlayTransparencyRight.setText("100%");
        FormData overlayTransparencyRightData = new FormData();
        overlayTransparencyRightData.right = new FormAttachment(100, -2);
        overlayTransparencyRightData.top = new FormAttachment(0, 2);
        overlayTransparencyRight.setLayoutData(overlayTransparencyRightData);

        Label refreshRight = new Label(pixelPerfectControls, SWT.NONE);
        refreshRight.setText("40s");
        FormData refreshRightData = new FormData();
        refreshRightData.right = new FormAttachment(100, -2);
        refreshRightData.top = new FormAttachment(overlayTransparencyRight, 2);
        refreshRightData.left = new FormAttachment(overlayTransparencyRight, 0, SWT.LEFT);
        refreshRight.setLayoutData(refreshRightData);

        Label zoomRight = new Label(pixelPerfectControls, SWT.NONE);
        zoomRight.setText("24x");
        FormData zoomRightData = new FormData();
        zoomRightData.right = new FormAttachment(100, -2);
        zoomRightData.top = new FormAttachment(refreshRight, 2);
        zoomRightData.left = new FormAttachment(overlayTransparencyRight, 0, SWT.LEFT);
        zoomRight.setLayoutData(zoomRightData);

        Label overlayTransparency = new Label(pixelPerfectControls, SWT.NONE);
        Label refresh = new Label(pixelPerfectControls, SWT.NONE);

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

        Label zoom = new Label(pixelPerfectControls, SWT.NONE);
        zoom.setText("Zoom:");
        FormData zoomData = new FormData();
        zoomData.right = new FormAttachment(refresh, 0, SWT.RIGHT);
        zoomData.top = new FormAttachment(refresh, 2);
        zoomData.left = new FormAttachment(0, 2);
        zoom.setLayoutData(zoomData);

        Label overlayTransparencyLeft = new Label(pixelPerfectControls, SWT.RIGHT);
        overlayTransparencyLeft.setText("0%");
        FormData overlayTransparencyLeftData = new FormData();
        overlayTransparencyLeftData.top = new FormAttachment(0, 2);
        overlayTransparencyLeftData.left = new FormAttachment(overlayTransparency, 2);
        overlayTransparencyLeft.setLayoutData(overlayTransparencyLeftData);

        Label refreshLeft = new Label(pixelPerfectControls, SWT.RIGHT);
        refreshLeft.setText("1s");
        FormData refreshLeftData = new FormData();
        refreshLeftData.top = new FormAttachment(overlayTransparencyLeft, 2);
        refreshLeftData.left = new FormAttachment(refresh, 2);
        refreshLeft.setLayoutData(refreshLeftData);

        Label zoomLeft = new Label(pixelPerfectControls, SWT.RIGHT);
        zoomLeft.setText("2x");
        FormData zoomLeftData = new FormData();
        zoomLeftData.top = new FormAttachment(refreshLeft, 2);
        zoomLeftData.left = new FormAttachment(zoom, 2);
        zoomLeft.setLayoutData(zoomLeftData);

        overlaySlider = new Slider(pixelPerfectControls, SWT.HORIZONTAL);
        overlaySlider.setMinimum(0);
        overlaySlider.setMaximum(101);
        overlaySlider.setThumb(1);
        overlaySlider.setSelection(50);
        overlaySlider.setEnabled(false);
        FormData overlaySliderData = new FormData();
        overlaySliderData.right = new FormAttachment(overlayTransparencyRight, -4);
        overlaySliderData.top = new FormAttachment(0, 2);
        overlaySliderData.left = new FormAttachment(overlayTransparencyLeft, 4);
        overlaySlider.setLayoutData(overlaySliderData);

        overlaySlider.addSelectionListener(overlaySliderSelectionListener);

        refreshSlider = new Slider(pixelPerfectControls, SWT.HORIZONTAL);
        refreshSlider.setMinimum(1);
        refreshSlider.setMaximum(41);
        refreshSlider.setThumb(1);
        refreshSlider.setSelection(refreshInterval);
        FormData refreshSliderData = new FormData();
        refreshSliderData.right = new FormAttachment(overlayTransparencyRight, -4);
        refreshSliderData.top = new FormAttachment(overlayTransparencyRight, 2);
        refreshSliderData.left = new FormAttachment(overlaySlider, 0, SWT.LEFT);
        refreshSlider.setLayoutData(refreshSliderData);

        refreshSlider.addSelectionListener(refreshSliderSelectionListener);

        ppZoomSlider = new Slider(pixelPerfectControls, SWT.HORIZONTAL);
        ppZoomSlider.setMinimum(2);
        ppZoomSlider.setMaximum(25);
        ppZoomSlider.setThumb(1);
        ppZoomSlider.setSelection(PixelPerfectModel.DEFAULT_ZOOM);
        FormData zoomSliderData = new FormData();
        zoomSliderData.right = new FormAttachment(overlayTransparencyRight, -4);
        zoomSliderData.top = new FormAttachment(refreshRight, 2);
        zoomSliderData.left = new FormAttachment(overlaySlider, 0, SWT.LEFT);
        ppZoomSlider.setLayoutData(zoomSliderData);

        ppZoomSlider.addSelectionListener(ppZoomSliderSelectionListener);

        Composite pixelPerfectContainer = new Composite(mainSash, SWT.BORDER);
        pixelPerfectContainer.setLayout(new FillLayout());
        PixelPerfect pixelPerfect = new PixelPerfect(pixelPerfectContainer);

        mainSash.setWeights(new int[] {
                272, 376, 346
        });

    }

    public void setAutoRefresh(boolean value) {
        if (value) {
            refreshTimeLeft = refreshInterval;
            autoRefresh = true;
        } else {
            autoRefresh = false;
        }
    }

    public void showOverlayInLoupe(boolean value) {
        pixelPerfectLoupe.setShowOverlay(value);
    }

    // Shows the progress notification...
    public void startTask(final String taskName) {
        progressString = taskName;
        Display.getDefault().syncExec(new Runnable() {
            public void run() {
                if (progressLabel != null && progressBar != null) {
                    progressLabel.setText(taskName);
                    progressLabel.setVisible(true);
                    progressBar.setVisible(true);
                    statusBar.layout();
                }
            }
        });
    }

    // And hides it!
    public void endTask() {
        progressString = null;
        Display.getDefault().syncExec(new Runnable() {
            public void run() {
                if (progressLabel != null && progressBar != null) {
                    progressLabel.setVisible(false);
                    progressBar.setVisible(false);
                }
            }
        });
    }

    public void showDeviceSelector() {
        // Show the menus.
        MenuManager mm = getMenuBarManager();
        mm.removeAll();

        MenuManager file = new MenuManager("&File");
        mm.add(file);

        file.add(QuitAction.getAction());

        MenuManager device = new MenuManager("&Devices");
        mm.add(device);

        device.add(RefreshWindowsAction.getAction());
        device.add(LoadViewHierarchyAction.getAction());
        device.add(InspectScreenshotAction.getAction());

        MenuManager help = new MenuManager("&Help");
        mm.add(help);

        help.add(AboutAction.getAction(getShell()));

        mm.updateAll(true);

        deviceViewButton.setSelection(true);
        deviceViewButton.setImage(deviceViewSelectedImage);

        treeViewButton.setSelection(false);
        treeViewButton.setImage(treeViewImage);

        pixelPerfectButton.setSelection(false);
        pixelPerfectButton.setImage(pixelPerfectImage);

        mainWindowStackLayout.topControl = deviceSelectorPanel;

        mainWindow.layout();

        deviceSelector.setFocus();

        statusBarStackLayout.topControl = null;
        statusBarControlPanel.setVisible(false);
        statusBarControlPanel.layout();
    }

    public void showTreeView() {
        // Show the menus.
        MenuManager mm = getMenuBarManager();
        mm.removeAll();

        MenuManager file = new MenuManager("&File");
        mm.add(file);

        file.add(QuitAction.getAction());

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

        deviceViewButton.setSelection(false);
        deviceViewButton.setImage(deviceViewImage);

        treeViewButton.setSelection(true);
        treeViewButton.setImage(treeViewSelectedImage);

        pixelPerfectButton.setSelection(false);
        pixelPerfectButton.setImage(pixelPerfectImage);

        mainWindowStackLayout.topControl = treeViewPanel;

        mainWindow.layout();

        treeView.setFocus();

        statusBarStackLayout.topControl = treeViewControls;
        statusBarControlPanel.setVisible(true);
        statusBarControlPanel.layout();
    }

    public void showPixelPerfect() {
        // Show the menus.
        MenuManager mm = getMenuBarManager();
        mm.removeAll();

        MenuManager file = new MenuManager("&File");
        mm.add(file);

        file.add(QuitAction.getAction());

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

        deviceViewButton.setSelection(false);
        deviceViewButton.setImage(deviceViewImage);

        treeViewButton.setSelection(false);
        treeViewButton.setImage(treeViewImage);

        pixelPerfectButton.setSelection(true);
        pixelPerfectButton.setImage(pixelPerfectSelectedImage);

        mainWindowStackLayout.topControl = pixelPerfectPanel;

        mainWindow.layout();

        pixelPerfectLoupe.setFocus();

        statusBarStackLayout.topControl = null;
        statusBarControlPanel.setVisible(false);
        statusBarControlPanel.layout();
    }

    private SelectionListener deviceViewButtonSelectionListener = new SelectionListener() {
        public void widgetDefaultSelected(SelectionEvent e) {
            // pass
        }

        public void widgetSelected(SelectionEvent e) {
            deviceViewButton.setSelection(true);
            showDeviceSelector();
        }
    };

    private SelectionListener treeViewButtonSelectionListener = new SelectionListener() {
        public void widgetDefaultSelected(SelectionEvent e) {
            // pass
        }

        public void widgetSelected(SelectionEvent e) {
            treeViewButton.setSelection(true);
            showTreeView();
        }
    };

    private SelectionListener pixelPerfectButtonSelectionListener = new SelectionListener() {
        public void widgetDefaultSelected(SelectionEvent e) {
            // pass
        }

        public void widgetSelected(SelectionEvent e) {
            pixelPerfectButton.setSelection(true);
            showPixelPerfect();
        }
    };

    private SelectionListener onBlackWhiteSelectionListener = new SelectionListener() {
        public void widgetDefaultSelected(SelectionEvent e) {
            // pass
        }

        public void widgetSelected(SelectionEvent e) {
            if (layoutViewer.getOnBlack()) {
                layoutViewer.setOnBlack(false);
                onBlackWhiteButton.setImage(onBlackImage);
            } else {
                layoutViewer.setOnBlack(true);
                onBlackWhiteButton.setImage(onWhiteImage);
            }
        }
    };

    private SelectionListener showExtrasSelectionListener = new SelectionListener() {
        public void widgetDefaultSelected(SelectionEvent e) {
            // pass
        }

        public void widgetSelected(SelectionEvent e) {
            layoutViewer.setShowExtras(showExtras.getSelection());
        }
    };

    private SelectionListener loadAllViewsSelectionListener = new SelectionListener() {
        public void widgetDefaultSelected(SelectionEvent e) {
            // pass
        }

        public void widgetSelected(SelectionEvent e) {
            showExtras.setSelection(true);
            showExtrasSelectionListener.widgetSelected(null);
        }
    };

    private SelectionListener zoomSliderSelectionListener = new SelectionListener() {
        private int oldValue;

        public void widgetDefaultSelected(SelectionEvent e) {
            // pass
        }

        public void widgetSelected(SelectionEvent e) {
            int newValue = zoomSlider.getSelection();
            if (oldValue != newValue) {
                TreeViewModel.getModel().removeTreeChangeListener(treeChangeListener);
                TreeViewModel.getModel().setZoom(newValue / 10.0);
                TreeViewModel.getModel().addTreeChangeListener(treeChangeListener);
                oldValue = newValue;
            }
        }
    };

    private Runnable autoRefresher = new Runnable() {
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    break;
                }
                refreshTimeLeft--;
                if (autoRefresh && refreshTimeLeft <= 0) {
                    HierarchyViewerDirector.getDirector().refreshPixelPerfect();
                    refreshTimeLeft = refreshInterval;
                }
            }
        }
    };

    private SelectionListener overlaySliderSelectionListener = new SelectionListener() {
        private int oldValue;

        public void widgetDefaultSelected(SelectionEvent e) {
            // pass
        }

        public void widgetSelected(SelectionEvent e) {
            int newValue = overlaySlider.getSelection();
            if (oldValue != newValue) {
                PixelPerfectModel.getModel().removeImageChangeListener(imageChangeListener);
                PixelPerfectModel.getModel().setOverlayTransparency(newValue / 100.0);
                PixelPerfectModel.getModel().addImageChangeListener(imageChangeListener);
                oldValue = newValue;
            }
        }
    };

    private SelectionListener refreshSliderSelectionListener = new SelectionListener() {
        private int oldValue;

        public void widgetDefaultSelected(SelectionEvent e) {
            // pass
        }

        public void widgetSelected(SelectionEvent e) {
            int newValue = refreshSlider.getSelection();
            if (oldValue != newValue) {
                refreshInterval = newValue;
                oldValue = newValue;
            }
        }
    };

    private SelectionListener ppZoomSliderSelectionListener = new SelectionListener() {
        private int oldValue;

        public void widgetDefaultSelected(SelectionEvent e) {
            // pass
        }

        public void widgetSelected(SelectionEvent e) {
            int newValue = ppZoomSlider.getSelection();
            if (oldValue != newValue) {
                PixelPerfectModel.getModel().removeImageChangeListener(imageChangeListener);
                PixelPerfectModel.getModel().setZoom(newValue);
                PixelPerfectModel.getModel().addImageChangeListener(imageChangeListener);
                oldValue = newValue;
            }
        }
    };

    private ModifyListener filterTextModifyListener = new ModifyListener() {
        public void modifyText(ModifyEvent e) {
            HierarchyViewerDirector.getDirector().filterNodes(filterText.getText());
        }
    };

    private WindowChangeListener windowChangeListener = new WindowChangeListener() {
        public void deviceChanged(IDevice device) {
            // pass
        }

        public void deviceConnected(IDevice device) {
            // pass
        }

        public void deviceDisconnected(IDevice device) {
            // pass
        }

        public void focusChanged(IDevice device) {
            // pass
        }

        public void selectionChanged(final IDevice device, final Window window) {
            Display.getDefault().syncExec(new Runnable() {
                public void run() {
                    if (window == null) {
                        LoadViewHierarchyAction.getAction().setEnabled(false);
                    } else {
                        LoadViewHierarchyAction.getAction().setEnabled(true);
                    }
                    if (device == null) {
                        InspectScreenshotAction.getAction().setEnabled(false);
                    } else {
                        InspectScreenshotAction.getAction().setEnabled(true);
                    }
                }
            });
        }
    };

    private TreeChangeListener treeChangeListener = new TreeChangeListener() {
        public void selectionChanged() {
            // pass
        }

        public void treeChanged() {
            Display.getDefault().syncExec(new Runnable() {
                public void run() {
                    if (TreeViewModel.getModel().getTree() == null) {
                        showDeviceSelector();
                        treeViewButton.setEnabled(false);
                    } else {
                        showTreeView();
                        treeViewButton.setEnabled(true);
                        zoomSlider.setSelection((int) Math
                                .round(TreeViewModel.getModel().getZoom() * 10));
                        filterText.setText("");
                    }
                }
            });
        }

        public void viewportChanged() {
            // pass
        }

        public void zoomChanged() {
            // pass
            zoomSlider.setSelection((int) Math.round(TreeViewModel.getModel().getZoom() * 10));
        }
    };

    private ImageChangeListener imageChangeListener = new ImageChangeListener() {

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
                    ShowOverlayAction.getAction().setEnabled(overlayImage != null);
                    overlaySlider.setEnabled(overlayImage != null);
                    if (PixelPerfectModel.getModel().getImage() == null) {
                        pixelPerfectButton.setEnabled(false);
                        showDeviceSelector();
                    } else {
                        ppZoomSlider.setSelection(PixelPerfectModel.getModel().getZoom());
                        pixelPerfectButton.setEnabled(true);
                        showPixelPerfect();
                    }
                }
            });
        }

        public void overlayChanged() {
            Display.getDefault().syncExec(new Runnable() {
                public void run() {
                    Image overlayImage = PixelPerfectModel.getModel().getOverlayImage();
                    ShowOverlayAction.getAction().setEnabled(overlayImage != null);
                    overlaySlider.setEnabled(overlayImage != null);
                }
            });
        }

        public void overlayTransparencyChanged() {
            Display.getDefault().syncExec(new Runnable() {
                public void run() {
                    overlaySlider.setSelection((int) (PixelPerfectModel.getModel()
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
                    ppZoomSlider.setSelection(PixelPerfectModel.getModel().getZoom());
                }
            });
        }

    };

    public static void main(String[] args) {
        Display.getDefault().syncExec(new Runnable() {
            public void run() {
                new HierarchyViewerApplication().run();
            }
        });
        System.exit(0);
    }
}
