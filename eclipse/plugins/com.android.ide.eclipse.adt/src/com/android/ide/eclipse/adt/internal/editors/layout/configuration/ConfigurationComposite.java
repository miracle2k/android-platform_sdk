/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.ide.eclipse.adt.internal.editors.layout.configuration;

import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.internal.editors.IconFactory;
import com.android.ide.eclipse.adt.internal.resources.ResourceType;
import com.android.ide.eclipse.adt.internal.resources.configurations.DockModeQualifier;
import com.android.ide.eclipse.adt.internal.resources.configurations.FolderConfiguration;
import com.android.ide.eclipse.adt.internal.resources.configurations.LanguageQualifier;
import com.android.ide.eclipse.adt.internal.resources.configurations.NightModeQualifier;
import com.android.ide.eclipse.adt.internal.resources.configurations.PixelDensityQualifier;
import com.android.ide.eclipse.adt.internal.resources.configurations.RegionQualifier;
import com.android.ide.eclipse.adt.internal.resources.configurations.ResourceQualifier;
import com.android.ide.eclipse.adt.internal.resources.configurations.ScreenDimensionQualifier;
import com.android.ide.eclipse.adt.internal.resources.configurations.ScreenOrientationQualifier;
import com.android.ide.eclipse.adt.internal.resources.manager.ProjectResources;
import com.android.ide.eclipse.adt.internal.resources.manager.ResourceFile;
import com.android.ide.eclipse.adt.internal.resources.manager.ResourceFolder;
import com.android.ide.eclipse.adt.internal.resources.manager.ResourceFolderType;
import com.android.ide.eclipse.adt.internal.resources.manager.ResourceManager;
import com.android.ide.eclipse.adt.internal.sdk.AndroidTargetData;
import com.android.ide.eclipse.adt.internal.sdk.LayoutDevice;
import com.android.ide.eclipse.adt.internal.sdk.LayoutDeviceManager;
import com.android.ide.eclipse.adt.internal.sdk.LoadStatus;
import com.android.ide.eclipse.adt.internal.sdk.Sdk;
import com.android.ide.eclipse.adt.internal.sdk.AndroidTargetData.LayoutBridge;
import com.android.layoutlib.api.IResourceValue;
import com.android.layoutlib.api.IStyleResourceValue;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.resources.Density;
import com.android.sdklib.resources.DockMode;
import com.android.sdklib.resources.NightMode;
import com.android.sdklib.resources.ScreenOrientation;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.draw2d.geometry.Rectangle;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.Map.Entry;

/**
 * A composite that displays the current configuration displayed in a Graphical Layout Editor.
 * <p/>
 * The composite has several entry points:<br>
 * - {@link #setFile(File)}<br>
 *   Called after the constructor to set the file being edited. Nothing else is performed.<br>
 *<br>
 * - {@link #onXmlModelLoaded()}<br>
 *   Called when the XML model is loaded, either the first time or when the Target/SDK changes.
 *   This initializes the UI, either with the first compatible configuration found, or attempts
 *   to restore a configuration if one is found to have been saved in the file persistent storage.
 *   (see {@link #storeState()})<br>
 *<br>
 * - {@link #replaceFile(File)}<br>
 *   Called when a file, representing the same resource but with a different config is opened<br>
 *   by the user.<br>
 *<br>
 * - {@link #changeFileOnNewConfig(FolderConfiguration)}<br>
 *   Called when config change triggers the editing of a file with a different config.
 *<p/>
 * Additionally, the composite can handle the following events.<br>
 * - SDK reload. This is when the main SDK is finished loading.<br>
 * - Target reload. This is when the target used by the project is the edited file has finished<br>
 *   loading.<br>
 */
public class ConfigurationComposite extends Composite {

    private final static String CONFIG_STATE = "state";  //$NON-NLS-1$
    private final static String THEME_SEPARATOR = "----------"; //$NON-NLS-1$

    private final static int LOCALE_LANG = 0;
    private final static int LOCALE_REGION = 1;

    private Button mClippingButton;
    private Label mCurrentLayoutLabel;

    private Combo mDeviceCombo;
    private Combo mDeviceConfigCombo;
    private Combo mLocaleCombo;
    private Combo mDockCombo;
    private Combo mNightCombo;
    private Combo mThemeCombo;
    private Button mCreateButton;

    private int mPlatformThemeCount = 0;
    /** updates are disabled if > 0 */
    private int mDisableUpdates = 0;

    private List<LayoutDevice> mDeviceList;

    private final ArrayList<ResourceQualifier[] > mLocaleList =
        new ArrayList<ResourceQualifier[]>();

    /**
     * clipping value. If true, the rendering is limited to the screensize. This is the default
     * value
     */
    private boolean mClipping = true;

    private final ConfigState mState = new ConfigState();

    private boolean mSdkChanged = false;
    private boolean mFirstXmlModelChange = true;

    /** The config listener given to the constructor. Never null. */
    private final IConfigListener mListener;

    /** The {@link FolderConfiguration} representing the state of the UI controls */
    private final FolderConfiguration mCurrentConfig = new FolderConfiguration();

    /** The file being edited */
    private IFile mEditedFile;
    /** The {@link ProjectResources} for the edited file's project */
    private ProjectResources mResources;
    /** The target of the project of the file being edited. */
    private IAndroidTarget mTarget;
    /** The {@link FolderConfiguration} being edited. */
    private FolderConfiguration mEditedConfig;


    /**
     * Interface implemented by the part which owns a {@link ConfigurationComposite}.
     * This notifies the owners when the configuration change.
     * The owner must also provide methods to provide the configuration that will
     * be displayed.
     */
    public interface IConfigListener {
        void onConfigurationChange();
        void onThemeChange();
        void onCreate();
        void onClippingChange();

        ProjectResources getProjectResources();
        ProjectResources getFrameworkResources();
        Map<String, Map<String, IResourceValue>> getConfiguredProjectResources();
        Map<String, Map<String, IResourceValue>> getConfiguredFrameworkResources();
    }

    /**
     * State of the current config. This is used during UI reset to attempt to return the
     * rendering to its original configuration.
     */
    private class ConfigState {
        private final static String SEP = ":"; //$NON-NLS-1$
        private final static String SEP_LOCALE = "-"; //$NON-NLS-1$

        LayoutDevice device;
        String configName;
        ResourceQualifier[] locale;
        String theme;
        /** dock mode. Guaranteed to be non null */
        DockMode dock = DockMode.NONE;
        /** night mode. Guaranteed to be non null */
        NightMode night = NightMode.NOTNIGHT;

        String getData() {
            StringBuilder sb = new StringBuilder();
            if (device != null) {
                sb.append(device.getName());
                sb.append(SEP);
                sb.append(configName);
                sb.append(SEP);
                if (locale != null) {
                    if (locale[0] != null && locale[1] != null) {
                        // locale[0]/[1] can be null sometimes when starting Eclipse
                        sb.append(((LanguageQualifier) locale[0]).getValue());
                        sb.append(SEP_LOCALE);
                        sb.append(((RegionQualifier) locale[1]).getValue());
                    }
                }
                sb.append(SEP);
                sb.append(theme);
                sb.append(SEP);
                sb.append(dock.getResourceValue());
                sb.append(SEP);
                sb.append(night.getResourceValue());
                sb.append(SEP);
            }

            return sb.toString();
        }

        boolean setData(String data) {
            String[] values = data.split(SEP);
            if (values.length == 6) {
                for (LayoutDevice d : mDeviceList) {
                    if (d.getName().equals(values[0])) {
                        device = d;
                        FolderConfiguration config = device.getConfigs().get(values[1]);
                        if (config != null) {
                            configName = values[1];

                            locale = new ResourceQualifier[2];
                            String locales[] = values[2].split(SEP_LOCALE);
                            if (locales.length >= 2) {
                                if (locales[0].length() > 0) {
                                    locale[0] = new LanguageQualifier(locales[0]);
                                }
                                if (locales[1].length() > 0) {
                                    locale[1] = new RegionQualifier(locales[1]);
                                }
                            }

                            theme = values[3];
                            dock = DockMode.getEnum(values[4]);
                            if (dock == null) {
                                dock = DockMode.NONE;
                            }
                            night = NightMode.getEnum(values[5]);
                            if (night == null) {
                                night = NightMode.NOTNIGHT;
                            }

                            return true;
                        }
                    }
                }
            }

            return false;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            if (device != null) {
                sb.append(device.getName());
            } else {
                sb.append("null");
            }
            sb.append(SEP);
            sb.append(configName);
            sb.append(SEP);
            if (locale != null) {
                sb.append(((LanguageQualifier) locale[0]).getValue());
                sb.append(SEP_LOCALE);
                sb.append(((RegionQualifier) locale[1]).getValue());
            }
            sb.append(SEP);
            sb.append(theme);
            sb.append(SEP);
            sb.append(dock.getResourceValue());
            sb.append(SEP);
            sb.append(night.getResourceValue());
            sb.append(SEP);

            return sb.toString();
        }
    }

    /**
     * Interface implemented by the part which owns a {@link ConfigurationComposite}
     * to define and handle custom toggle buttons in the button bar. Each toggle is
     * implemented using a button, with a callback when the button is selected.
     */
    public static abstract class CustomToggle {

        /** The UI label of the toggle. Can be null if the image exists. */
        private final String mUiLabel;

        /** The image to use for this toggle. Can be null if the label exists. */
        private final Image mImage;

        /** The tooltip for the toggle. Can be null. */
        private final String mUiTooltip;

        /**
         * Initializes a new {@link CustomToggle}. The values set here will be used
         * later to create the actual toggle.
         *
         * @param uiLabel   The UI label of the toggle. Can be null if the image exists.
         * @param image     The image to use for this toggle. Can be null if the label exists.
         * @param uiTooltip The tooltip for the toggle. Can be null.
         */
        public CustomToggle(
                String uiLabel,
                Image image,
                String uiTooltip) {
            mUiLabel = uiLabel;
            mImage = image;
            mUiTooltip = uiTooltip;
        }

        /** Called by the {@link ConfigurationComposite} when the button is selected. */
        public abstract void onSelected(boolean newState);

        private void createToggle(Composite parent) {
            final Button b = new Button(parent, SWT.TOGGLE | SWT.FLAT);

            if (mUiTooltip != null) {
                b.setToolTipText(mUiTooltip);
            }
            if (mImage != null) {
                b.setImage(mImage);
            }
            if (mUiLabel != null) {
                b.setText(mUiLabel);
            }

            b.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e) {
                    onSelected(b.getSelection());
                }
            });
        }
    }

    /**
     * Creates a new {@link ConfigurationComposite} and adds it to the parent.
     *
     * @param listener An {@link IConfigListener} that gets and sets configuration properties.
     *          Mandatory, cannot be null.
     * @param customToggles An array of {@link CustomToggle} to define extra toggles button
     *          to display at the top of the composite. Can be empty or null.
     * @param parent The parent composite.
     * @param style The style of this composite.
     */
    public ConfigurationComposite(IConfigListener listener,
            CustomToggle[] customToggles,
            Composite parent, int style) {
        super(parent, style);
        mListener = listener;

        if (customToggles == null) {
            customToggles = new CustomToggle[0];
        }

        GridLayout gl;
        GridData gd;
        int cols = 9;  // device+config+locale+dock+day/night+separator*2+theme+createBtn

        // ---- First line: custom buttons, clipping button, editing config display.
        Composite labelParent = new Composite(this, SWT.NONE);
        labelParent.setLayout(gl = new GridLayout(3 + customToggles.length, false));
        gl.marginWidth = gl.marginHeight = 0;
        labelParent.setLayoutData(gd = new GridData(GridData.FILL_HORIZONTAL));
        gd.horizontalSpan = cols;

        new Label(labelParent, SWT.NONE).setText("Editing config:");
        mCurrentLayoutLabel = new Label(labelParent, SWT.NONE);
        mCurrentLayoutLabel.setLayoutData(gd = new GridData(GridData.FILL_HORIZONTAL));
        gd.widthHint = 50;

        for (CustomToggle toggle : customToggles) {
            toggle.createToggle(labelParent);
        }

        mClippingButton = new Button(labelParent, SWT.TOGGLE | SWT.FLAT);
        mClippingButton.setSelection(mClipping);
        mClippingButton.setToolTipText("Toggles screen clipping on/off");
        mClippingButton.setImage(IconFactory.getInstance().getIcon("clipping")); //$NON-NLS-1$
        mClippingButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                onClippingChange();
            }
        });

        // ---- 2nd line: device/config/locale/theme Combos, create button.

        setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        setLayout(gl = new GridLayout(cols, false));
        gl.marginHeight = 0;
        gl.horizontalSpacing = 0;

        mDeviceCombo = new Combo(this, SWT.DROP_DOWN | SWT.READ_ONLY);
        mDeviceCombo.setLayoutData(new GridData(
                GridData.HORIZONTAL_ALIGN_FILL | GridData.GRAB_HORIZONTAL));
        mDeviceCombo.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                onDeviceChange(true /* recomputeLayout*/);
            }
        });

        mDeviceConfigCombo = new Combo(this, SWT.DROP_DOWN | SWT.READ_ONLY);
        mDeviceConfigCombo.setLayoutData(new GridData(
                GridData.HORIZONTAL_ALIGN_FILL | GridData.GRAB_HORIZONTAL));
        mDeviceConfigCombo.addSelectionListener(new SelectionAdapter() {
            @Override
             public void widgetSelected(SelectionEvent e) {
                onDeviceConfigChange();
            }
        });

        mLocaleCombo = new Combo(this, SWT.DROP_DOWN | SWT.READ_ONLY);
        mLocaleCombo.setLayoutData(new GridData(
                GridData.HORIZONTAL_ALIGN_FILL | GridData.GRAB_HORIZONTAL));
        mLocaleCombo.addSelectionListener(new SelectionListener() {
            public void widgetDefaultSelected(SelectionEvent e) {
                onLocaleChange();
            }
            public void widgetSelected(SelectionEvent e) {
                onLocaleChange();
            }
        });

        mDockCombo = new Combo(this, SWT.DROP_DOWN | SWT.READ_ONLY);
        mDockCombo.setLayoutData(new GridData(
                GridData.HORIZONTAL_ALIGN_FILL | GridData.GRAB_HORIZONTAL));
        for (DockMode mode : DockMode.values()) {
            mDockCombo.add(mode.getLongDisplayValue());
        }
        mDockCombo.addSelectionListener(new SelectionListener() {
            public void widgetDefaultSelected(SelectionEvent e) {
                onDockChange();
            }
            public void widgetSelected(SelectionEvent e) {
                onDockChange();
            }
        });

        mNightCombo = new Combo(this, SWT.DROP_DOWN | SWT.READ_ONLY);
        mNightCombo.setLayoutData(new GridData(
                GridData.HORIZONTAL_ALIGN_FILL | GridData.GRAB_HORIZONTAL));
        for (NightMode mode : NightMode.values()) {
            mNightCombo.add(mode.getLongDisplayValue());
        }
        mNightCombo.addSelectionListener(new SelectionListener() {
            public void widgetDefaultSelected(SelectionEvent e) {
                onDayChange();
            }
            public void widgetSelected(SelectionEvent e) {
                onDayChange();
            }
        });

        // first separator
        Label separator = new Label(this, SWT.SEPARATOR | SWT.VERTICAL);
        separator.setLayoutData(gd = new GridData(
                GridData.VERTICAL_ALIGN_FILL | GridData.GRAB_VERTICAL));
        gd.heightHint = 0;

        mThemeCombo = new Combo(this, SWT.READ_ONLY | SWT.DROP_DOWN);
        mThemeCombo.setLayoutData(new GridData(
                GridData.HORIZONTAL_ALIGN_FILL | GridData.GRAB_HORIZONTAL));
        mThemeCombo.setEnabled(false);

        mThemeCombo.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                onThemeChange();
            }
        });

        // second separator
        separator = new Label(this, SWT.SEPARATOR | SWT.VERTICAL);
        separator.setLayoutData(gd = new GridData(
                GridData.VERTICAL_ALIGN_FILL | GridData.GRAB_VERTICAL));
        gd.heightHint = 0;

        mCreateButton = new Button(this, SWT.PUSH | SWT.FLAT);
        mCreateButton.setText("Create...");
        mCreateButton.setEnabled(false);
        mCreateButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                if (mListener != null) {
                    mListener.onCreate();
                }
            }
        });
    }

    // ---- Init and reset/reload methods ----

    /**
     * Sets the reference to the file being edited.
     * <p/>The UI is intialized in {@link #onXmlModelLoaded()} which is called as the XML model is
     * loaded (or reloaded as the SDK/target changes).
     *
     * @param file the file being opened
     *
     * @see #onXmlModelLoaded()
     * @see #replaceFile(FolderConfiguration)
     * @see #changeFileOnNewConfig(FolderConfiguration)
     */
    public void setFile(IFile file) {
        mEditedFile = file;
    }

    /**
     * Replaces the UI with a given file configuration. This is meant to answer the user
     * explicitly opening a different version of the same layout from the Package Explorer.
     * <p/>This attempts to keep the current config, but may change it if it's not compatible or
     * not the best match
     * <p/>This will NOT trigger a redraw event (will not call
     * {@link IConfigListener#onConfigurationChange()}.)
     * @param file the file being opened.
     * @param fileConfig The {@link FolderConfiguration} of the opened file.
     * @param target the {@link IAndroidTarget} of the file's project.
     *
     * @see #replaceFile(FolderConfiguration)
     */
    public void replaceFile(IFile file) {
        // if there is no previous selection, revert to default mode.
        if (mState.device == null) {
            setFile(file); // onTargetChanged will be called later.
            return;
        }

        mEditedFile = file;
        IProject iProject = mEditedFile.getProject();
        mResources = ResourceManager.getInstance().getProjectResources(iProject);

        ResourceFolder resFolder = ResourceManager.getInstance().getResourceFolder(file);
        mEditedConfig = resFolder.getConfiguration();

        mDisableUpdates++; // we do not want to trigger onXXXChange when setting
                           // new values in the widgets.

        // only attempt to do anything if the SDK and targets are loaded.
        LoadStatus sdkStatus = AdtPlugin.getDefault().getSdkLoadStatus();
        if (sdkStatus == LoadStatus.LOADED) {
            LoadStatus targetStatus = Sdk.getCurrent().checkAndLoadTargetData(mTarget, null);

            if (targetStatus == LoadStatus.LOADED) {

                // update the current config selection to make sure it's
                // compatible with the new file
                adaptConfigSelection(true /*needBestMatch*/);

                // compute the final current config
                computeCurrentConfig(true /*force*/);

                // update the string showing the config value
                updateConfigDisplay(mEditedConfig);
            }
        }

        mDisableUpdates--;
    }

    /**
     * Updates the UI with a new file that was opened in response to a config change.
     * @param file the file being opened.
     *
     * @see #openFile(FolderConfiguration, IAndroidTarget)
     * @see #replaceFile(FolderConfiguration)
     */
    public void changeFileOnNewConfig(IFile file) {
        mEditedFile = file;
        IProject iProject = mEditedFile.getProject();
        mResources = ResourceManager.getInstance().getProjectResources(iProject);

        ResourceFolder resFolder = ResourceManager.getInstance().getResourceFolder(file);
        mEditedConfig = resFolder.getConfiguration();

        // All that's needed is to update the string showing the config value
        // (since the config combo were chosen by the user).
        updateConfigDisplay(mEditedConfig);
    }

    /**
     * Responds to the event that the basic SDK information finished loading.
     * @param target the possibly new target object associated with the file being edited (in case
     * the SDK path was changed).
     */
    public void onSdkLoaded(IAndroidTarget target) {
        // a change to the SDK means that we need to check for new/removed devices.
        mSdkChanged = true;

        // store the new target.
        mTarget = target;

        mDisableUpdates++; // we do not want to trigger onXXXChange when setting
                           // new values in the widgets.

        // this is going to be followed by a call to onTargetLoaded.
        // So we can only care about the layout devices in this case.
        initDevices();

        mDisableUpdates--;
    }

    /**
     * Answers to the XML model being loaded, either the first time or when the Targget/SDK changes.
     * <p>This initializes the UI, either with the first compatible configuration found,
     * or attempts to restore a configuration if one is found to have been saved in the file
     * persistent storage.
     * <p>If the SDK or target are not loaded, nothing will happend (but the method must be called
     * back when those are loaded).
     * <p>The method automatically handles being called the first time after editor creation, or
     * being called after during SDK/Target changes (as long as {@link #onSdkLoaded(IAndroidTarget)}
     * is properly called).
     *
     * @see #storeState()
     * @see #onSdkLoaded(IAndroidTarget)
     */
    public void onXmlModelLoaded() {
        // only attempt to do anything if the SDK and targets are loaded.
        LoadStatus sdkStatus = AdtPlugin.getDefault().getSdkLoadStatus();
        if (sdkStatus == LoadStatus.LOADED) {
            mDisableUpdates++; // we do not want to trigger onXXXChange when setting

            // init the devices if needed (new SDK or first time going through here)
            if (mSdkChanged || mFirstXmlModelChange) {
                initDevices();
            }

            IProject iProject = mEditedFile.getProject();

            Sdk currentSdk = Sdk.getCurrent();
            if (currentSdk != null) {
                mTarget = currentSdk.getTarget(iProject);
            }

            LoadStatus targetStatus = LoadStatus.FAILED;
            if (mTarget != null) {
                targetStatus = Sdk.getCurrent().checkAndLoadTargetData(mTarget, null);
            }

            if (targetStatus == LoadStatus.LOADED) {
                if (mResources == null) {
                    mResources = ResourceManager.getInstance().getProjectResources(iProject);
                }
                if (mEditedConfig == null) {
                    ResourceFolder resFolder = mResources.getResourceFolder(
                            (IFolder) mEditedFile.getParent());
                    mEditedConfig = resFolder.getConfiguration();
                }

                // update the clipping state
                AndroidTargetData targetData = Sdk.getCurrent().getTargetData(mTarget);
                if (targetData != null) {
                    LayoutBridge bridge = targetData.getLayoutBridge();
                    setClippingSupport(bridge.apiLevel >= 4);
                }

                // get the file stored state
                boolean loadedConfigData = false;
                try {
                    QualifiedName qname = new QualifiedName(AdtPlugin.PLUGIN_ID, CONFIG_STATE);
                    String data = mEditedFile.getPersistentProperty(qname);
                    if (data != null) {
                        loadedConfigData = mState.setData(data);
                    }
                } catch (CoreException e) {
                    // pass
                }

                // update the themes and locales.
                updateThemes();
                updateLocales();

                // If the current state was loaded from the persistent storage, we update the
                // UI with it and then try to adapt it (which will handle incompatible
                // configuration).
                // Otherwise, just look for the first compatible configuration.
                if (loadedConfigData) {
                    // first make sure we have the config to adapt
                    selectDevice(mState.device);
                    fillConfigCombo(mState.configName);

                    adaptConfigSelection(false /*needBestMatch*/);

                    mDockCombo.select(DockMode.getIndex(mState.dock));
                    mNightCombo.select(NightMode.getIndex(mState.night));
                } else {
                    findAndSetCompatibleConfig(false /*favorCurrentConfig*/);

                    mDockCombo.select(0);
                    mNightCombo.select(0);
                }

                // update the string showing the config value
                updateConfigDisplay(mEditedConfig);

                // compute the final current config
                computeCurrentConfig(true /*force*/);
            }

            mDisableUpdates--;
            mFirstXmlModelChange  = false;
        }
    }

    /**
     * Finds a device/config that can display {@link #mEditedConfig}.
     * <p/>Once found the device and config combos are set to the config.
     * <p/>If there is no compatible configuration, a custom one is created.
     * @param favorCurrentConfig if true, and no best match is found, don't change
     * the current config. This must only be true if the current config is compatible.
     */
    private void findAndSetCompatibleConfig(boolean favorCurrentConfig) {
        LayoutDevice anyDeviceMatch = null; // a compatible device/config/locale
        String anyConfigMatchName = null;
        int anyLocaleIndex = -1;

        LayoutDevice bestDeviceMatch = null; // an actual best match
        String bestConfigMatchName = null;
        int bestLocaleIndex = -1;

        FolderConfiguration testConfig = new FolderConfiguration();

        mainloop: for (LayoutDevice device : mDeviceList) {
            for (Entry<String, FolderConfiguration> entry :
                    device.getConfigs().entrySet()) {
                testConfig.set(entry.getValue());

                // look on the locales.
                for (int i = 0 ; i < mLocaleList.size() ; i++) {
                    ResourceQualifier[] locale = mLocaleList.get(i);

                    // update the test config with the locale qualifiers
                    testConfig.setLanguageQualifier((LanguageQualifier)locale[LOCALE_LANG]);
                    testConfig.setRegionQualifier((RegionQualifier)locale[LOCALE_REGION]);

                    if (mEditedConfig.isMatchFor(testConfig)) {
                        // this is a basic match. record it in case we don't find a match
                        // where the edited file is a best config.
                        if (anyDeviceMatch == null) {
                            anyDeviceMatch = device;
                            anyConfigMatchName = entry.getKey();
                            anyLocaleIndex = i;
                        }

                        if (isCurrentFileBestMatchFor(testConfig)) {
                            // this is what we want.
                            bestDeviceMatch = device;
                            bestConfigMatchName = entry.getKey();
                            bestLocaleIndex = i;
                            break mainloop;
                        }
                    }
                }
            }
        }

        if (bestDeviceMatch == null) {
            if (favorCurrentConfig) {
                // quick check
                if (mEditedConfig.isMatchFor(mCurrentConfig) == false) {
                    AdtPlugin.log(IStatus.ERROR,
                            "favorCurrentConfig can only be true if the current config is compatible");
                }

                // just display the warning
                AdtPlugin.printErrorToConsole(mEditedFile.getProject(),
                        String.format(
                                "'%1$s' is not a best match for any device/locale combination.",
                                mEditedConfig.toDisplayString()),
                        String.format(
                                "Displaying it with '%1$s'",
                                mCurrentConfig.toDisplayString()));
            } else if (anyDeviceMatch != null) {
                // select the device anyway.
                selectDevice(mState.device = anyDeviceMatch);
                fillConfigCombo(anyConfigMatchName);
                mLocaleCombo.select(anyLocaleIndex);

                // TODO: display a better warning!
                computeCurrentConfig(false /*force*/);
                AdtPlugin.printErrorToConsole(mEditedFile.getProject(),
                        String.format(
                                "'%1$s' is not a best match for any device/locale combination.",
                                mEditedConfig.toDisplayString()),
                        String.format(
                                "Displaying it with '%1$s'",
                                mCurrentConfig.toDisplayString()));

            } else {
                // TODO: there is no device/config able to display the layout, create one.
                // For the base config values, we'll take the first device and config,
                // and replace whatever qualifier required by the layout file.
            }
        } else {
            selectDevice(mState.device = bestDeviceMatch);
            fillConfigCombo(bestConfigMatchName);
            mLocaleCombo.select(bestLocaleIndex);
        }
    }

    /**
     * Adapts the current device/config selection so that it's compatible with
     * {@link #mEditedConfig}.
     * <p/>If the current selection is compatible, nothing is changed.
     * <p/>If it's not compatible, configs from the current devices are tested.
     * <p/>If none are compatible, it reverts to
     * {@link #findAndSetCompatibleConfig(FolderConfiguration)}
     */
    private void adaptConfigSelection(boolean needBestMatch) {
        // check the device config (ie sans locale)
        boolean needConfigChange = true; // if still true, we need to find another config.
        boolean currentConfigIsCompatible = false;
        int configIndex = mDeviceConfigCombo.getSelectionIndex();
        if (configIndex != -1) {
            String configName = mDeviceConfigCombo.getItem(configIndex);
            FolderConfiguration currentConfig = mState.device.getConfigs().get(configName);
            if (mEditedConfig.isMatchFor(currentConfig)) {
                currentConfigIsCompatible = true; // current config is compatible
                if (needBestMatch == false || isCurrentFileBestMatchFor(currentConfig)) {
                    needConfigChange = false;
                }
            }
        }

        if (needConfigChange) {
            // if the current config/locale isn't a correct match, then
            // look for another config/locale in the same device.
            FolderConfiguration testConfig = new FolderConfiguration();

            // first look in the current device.
            String matchName = null;
            int localeIndex = -1;
            Map<String, FolderConfiguration> configs = mState.device.getConfigs();
            mainloop: for (Entry<String, FolderConfiguration> entry : configs.entrySet()) {
                testConfig.set(entry.getValue());

                // loop on the locales.
                for (int i = 0 ; i < mLocaleList.size() ; i++) {
                    ResourceQualifier[] locale = mLocaleList.get(i);

                    // update the test config with the locale qualifiers
                    testConfig.setLanguageQualifier((LanguageQualifier)locale[LOCALE_LANG]);
                    testConfig.setRegionQualifier((RegionQualifier)locale[LOCALE_REGION]);

                    if (mEditedConfig.isMatchFor(testConfig) &&
                            isCurrentFileBestMatchFor(testConfig)) {
                        matchName = entry.getKey();
                        localeIndex = i;
                        break mainloop;
                    }
                }
            }

            if (matchName != null) {
                selectConfig(matchName);
                mLocaleCombo.select(localeIndex);
            } else {
                // no match in current device with any config/locale
                // attempt to find another device that can display this particular config.
                findAndSetCompatibleConfig(currentConfigIsCompatible);
            }
        }
    }

    /**
     * Finds a locale matching the config from a file.
     * @param language the language qualifier or null if none is set.
     * @param region the region qualifier or null if none is set.
     */
    private void setLocaleCombo(ResourceQualifier language, ResourceQualifier region) {
        // find the locale match. Since the locale list is based on the content of the
        // project resources there must be an exact match.
        // The only trick is that the region could be null in the fileConfig but in our
        // list of locales, this is represented as a RegionQualifier with value of
        // FAKE_LOCALE_VALUE.
        final int count = mLocaleList.size();
        for (int i = 0 ; i < count ; i++) {
            ResourceQualifier[] locale = mLocaleList.get(i);

            // the language qualifier in the locale list is never null.
            if (locale[LOCALE_LANG].equals(language)) {
                // region comparison is more complex, as the region could be null.
                if (region == null) {
                    if (RegionQualifier.FAKE_REGION_VALUE.equals(
                            ((RegionQualifier)locale[LOCALE_REGION]).getValue())) {
                        // match!
                        mLocaleCombo.select(i);
                        break;
                    }
                } else if (region.equals(locale[LOCALE_REGION])) {
                    // match!
                    mLocaleCombo.select(i);
                    break;
                }
            }
        }
    }

    private void updateConfigDisplay(FolderConfiguration fileConfig) {
        String current = fileConfig.toDisplayString();
        mCurrentLayoutLabel.setText(current != null ? current : "(Default)");
    }

    private void saveState(boolean force) {
        if (mDisableUpdates == 0) {
            int index = mDeviceConfigCombo.getSelectionIndex();
            if (index != -1) {
                mState.configName = mDeviceConfigCombo.getItem(index);
            } else {
                mState.configName = null;
            }

            // since the locales are relative to the project, only keeping the index is enough
            index = mLocaleCombo.getSelectionIndex();
            if (index != -1) {
                mState.locale = mLocaleList.get(index);
            } else {
                mState.locale = null;
            }

            index = mThemeCombo.getSelectionIndex();
            if (index != -1) {
                mState.theme = mThemeCombo.getItem(index);
            }

            index = mDockCombo.getSelectionIndex();
            if (index != -1) {
                mState.dock = DockMode.getByIndex(index);
            }

            index = mNightCombo.getSelectionIndex();
            if (index != -1) {
                mState.night = NightMode.getByIndex(index);
            }
        }
    }

    /**
     * Stores the current config selection into the edited file.
     */
    public void storeState() {
        try {
            QualifiedName qname = new QualifiedName(AdtPlugin.PLUGIN_ID, CONFIG_STATE); //$NON-NLS-1$
            mEditedFile.setPersistentProperty(qname, mState.getData());
        } catch (CoreException e) {
            // pass
        }
    }

    /**
     * Updates the locale combo.
     * This must be called from the UI thread.
     */
    public void updateLocales() {
        if (mListener == null) {
            return; // can't do anything w/o it.
        }

        mDisableUpdates++;

        // Reset the combo
        mLocaleCombo.removeAll();
        mLocaleList.clear();

        SortedSet<String> languages = null;
        boolean hasLocale = false;

        // get the languages from the project.
        ProjectResources project = mListener.getProjectResources();

        // in cases where the opened file is not linked to a project, this could be null.
        if (project != null) {
            // now get the languages from the project.
            languages = project.getLanguages();

            for (String language : languages) {
                hasLocale = true;

                LanguageQualifier langQual = new LanguageQualifier(language);

                // find the matching regions and add them
                SortedSet<String> regions = project.getRegions(language);
                for (String region : regions) {
                    mLocaleCombo.add(String.format("%1$s / %2$s", language, region)); //$NON-NLS-1$
                    RegionQualifier regionQual = new RegionQualifier(region);
                    mLocaleList.add(new ResourceQualifier[] { langQual, regionQual });
                }

                // now the entry for the other regions the language alone
                if (regions.size() > 0) {
                    mLocaleCombo.add(String.format("%1$s / Other", language)); //$NON-NLS-1$
                } else {
                    mLocaleCombo.add(String.format("%1$s / Any", language)); //$NON-NLS-1$
                }
                // create a region qualifier that will never be matched by qualified resources.
                mLocaleList.add(new ResourceQualifier[] {
                        langQual,
                        new RegionQualifier(RegionQualifier.FAKE_REGION_VALUE)
                });
            }
        }

        // add a locale not present in the project resources. This will let the dev
        // tests his/her default values.
        if (hasLocale) {
            mLocaleCombo.add("Other");
        } else {
            mLocaleCombo.add("Any locale");
        }

        // create language/region qualifier that will never be matched by qualified resources.
        mLocaleList.add(new ResourceQualifier[] {
                new LanguageQualifier(LanguageQualifier.FAKE_LANG_VALUE),
                new RegionQualifier(RegionQualifier.FAKE_REGION_VALUE)
        });

        if (mState.locale != null) {
            // FIXME: this may fails if the layout was deleted (and was the last one to have that local.
            // (we have other problem in this case though)
            setLocaleCombo(mState.locale[LOCALE_LANG],
                    mState.locale[LOCALE_REGION]);
        } else {
            mLocaleCombo.select(0);
        }

        mThemeCombo.getParent().layout();

        mDisableUpdates--;
    }

    /**
     * Updates the theme combo.
     * This must be called from the UI thread.
     */
    private void updateThemes() {
        if (mListener == null) {
            return; // can't do anything w/o it.
        }

        ProjectResources frameworkProject = mListener.getFrameworkResources();

        mDisableUpdates++;

        // Reset the combo
        mThemeCombo.removeAll();
        mPlatformThemeCount = 0;

        ArrayList<String> themes = new ArrayList<String>();

        // get the themes, and languages from the Framework.
        if (frameworkProject != null) {
            // get the configured resources for the framework
            Map<String, Map<String, IResourceValue>> frameworResources =
                mListener.getConfiguredFrameworkResources();

            if (frameworResources != null) {
                // get the styles.
                Map<String, IResourceValue> styles = frameworResources.get(
                        ResourceType.STYLE.getName());


                // collect the themes out of all the styles.
                for (IResourceValue value : styles.values()) {
                    String name = value.getName();
                    if (name.startsWith("Theme.") || name.equals("Theme")) {
                        themes.add(value.getName());
                        mPlatformThemeCount++;
                    }
                }

                // sort them and add them to the combo
                Collections.sort(themes);

                for (String theme : themes) {
                    mThemeCombo.add(theme);
                }

                mPlatformThemeCount = themes.size();
                themes.clear();
            }
        }

        // now get the themes and languages from the project.
        ProjectResources project = mListener.getProjectResources();
        // in cases where the opened file is not linked to a project, this could be null.
        if (project != null) {
            // get the configured resources for the project
            Map<String, Map<String, IResourceValue>> configuredProjectRes =
                mListener.getConfiguredProjectResources();

            if (configuredProjectRes != null) {
                // get the styles.
                Map<String, IResourceValue> styleMap = configuredProjectRes.get(
                        ResourceType.STYLE.getName());

                if (styleMap != null) {
                    // collect the themes out of all the styles, ie styles that extend,
                    // directly or indirectly a platform theme.
                    for (IResourceValue value : styleMap.values()) {
                        if (isTheme(value, styleMap)) {
                            themes.add(value.getName());
                        }
                    }

                    // sort them and add them the to the combo.
                    if (mPlatformThemeCount > 0 && themes.size() > 0) {
                        mThemeCombo.add(THEME_SEPARATOR);
                    }

                    Collections.sort(themes);

                    for (String theme : themes) {
                        mThemeCombo.add(theme);
                    }
                }
            }
        }

        // try to reselect the previous theme.
        if (mState.theme != null) {
            final int count = mThemeCombo.getItemCount();
            for (int i = 0 ; i < count ; i++) {
                if (mState.theme.equals(mThemeCombo.getItem(i))) {
                    mThemeCombo.select(i);
                    break;
                }
            }
            mThemeCombo.setEnabled(true);
        } else if (mThemeCombo.getItemCount() > 0) {
            mThemeCombo.select(0);
            mThemeCombo.setEnabled(true);
        } else {
            mThemeCombo.setEnabled(false);
        }

        mThemeCombo.getParent().layout();

        mDisableUpdates--;
    }

    // ---- getters for the config selection values ----

    public FolderConfiguration getEditedConfig() {
        return mEditedConfig;
    }

    public FolderConfiguration getCurrentConfig() {
        return mCurrentConfig;
    }

    public void getCurrentConfig(FolderConfiguration config) {
        config.set(mCurrentConfig);
    }

    /**
     * Returns the currently selected {@link Density}. This is guaranteed to be non null.
     */
    public Density getDensity() {
        if (mCurrentConfig != null) {
            PixelDensityQualifier qual = mCurrentConfig.getPixelDensityQualifier();
            if (qual != null) {
                // just a sanity check
                Density d = qual.getValue();
                if (d != Density.NODPI) {
                    return d;
                }
            }
        }

        // no config? return medium as the default density.
        return Density.MEDIUM;
    }

    /**
     * Returns the current device xdpi.
     */
    public float getXDpi() {
        if (mState.device != null) {
            float dpi = mState.device.getXDpi();
            if (Float.isNaN(dpi) == false) {
                return dpi;
            }
        }

        // get the pixel density as the density.
        return getDensity().getDpiValue();
    }

    /**
     * Returns the current device ydpi.
     */
    public float getYDpi() {
        if (mState.device != null) {
            float dpi = mState.device.getYDpi();
            if (Float.isNaN(dpi) == false) {
                return dpi;
            }
        }

        // get the pixel density as the density.
        return getDensity().getDpiValue();
    }

    public Rectangle getScreenBounds() {
        // get the orientation from the current device config
        ScreenOrientationQualifier qual = mCurrentConfig.getScreenOrientationQualifier();
        ScreenOrientation orientation = ScreenOrientation.PORTRAIT;
        if (qual != null) {
            orientation = qual.getValue();
        }

        // get the device screen dimension
        ScreenDimensionQualifier qual2 = mCurrentConfig.getScreenDimensionQualifier();
        int s1, s2;
        if (qual2 != null) {
            s1 = qual2.getValue1();
            s2 = qual2.getValue2();
        } else {
            s1 = 480;
            s2 = 320;
        }

        switch (orientation) {
            default:
            case PORTRAIT:
                return new Rectangle(0, 0, s2, s1);
            case LANDSCAPE:
                return new Rectangle(0, 0, s1, s2);
            case SQUARE:
                return new Rectangle(0, 0, s1, s1);
        }
    }


    /**
     * Returns the current theme, or null if the combo has no selection.
     */
    public String getTheme() {
        int themeIndex = mThemeCombo.getSelectionIndex();
        if (themeIndex != -1) {
            return mThemeCombo.getItem(themeIndex);
        }

        return null;
    }

    /**
     * Returns whether the current theme selection is a project theme.
     * <p/>The returned value is meaningless if {@link #getTheme()} returns <code>null</code>.
     * @return true for project theme, false for framework theme
     */
    public boolean isProjectTheme() {
        return mThemeCombo.getSelectionIndex() >= mPlatformThemeCount;
    }

    public boolean getClipping() {
        return mClipping;
    }

    private void setClippingSupport(boolean b) {
        mClippingButton.setEnabled(b);
        if (b) {
            mClippingButton.setToolTipText("Toggles screen clipping on/off");
        } else {
            mClipping = true;
            mClippingButton.setSelection(true);
            mClippingButton.setToolTipText("Non clipped rendering is not supported");
        }
    }

    /**
     * Loads the list of {@link LayoutDevice} and inits the UI with it.
     */
    private void initDevices() {
        mDeviceList = null;

        Sdk sdk = Sdk.getCurrent();
        if (sdk != null) {
            LayoutDeviceManager manager = sdk.getLayoutDeviceManager();
            mDeviceList = manager.getCombinedList();
        }


        // remove older devices if applicable
        mDeviceCombo.removeAll();
        mDeviceConfigCombo.removeAll();

        // fill with the devices
        if (mDeviceList != null) {
            for (LayoutDevice device : mDeviceList) {
                mDeviceCombo.add(device.getName());
            }
            mDeviceCombo.select(0);

            if (mDeviceList.size() > 0) {
                Map<String, FolderConfiguration> configs = mDeviceList.get(0).getConfigs();
                Set<String> configNames = configs.keySet();
                for (String name : configNames) {
                    mDeviceConfigCombo.add(name);
                }
                mDeviceConfigCombo.select(0);
                if (configNames.size() == 1) {
                    mDeviceConfigCombo.setEnabled(false);
                }
            }
        }

        // add the custom item
        mDeviceCombo.add("Custom...");
    }

    /**
     * Selects a given {@link LayoutDevice} in the device combo, if it is found.
     * @param device the device to select
     * @return true if the device was found.
     */
    private boolean selectDevice(LayoutDevice device) {
        final int count = mDeviceList.size();
        for (int i = 0 ; i < count ; i++) {
            // since device comes from mDeviceList, we can use the == operator.
            if (device == mDeviceList.get(i)) {
                mDeviceCombo.select(i);
                return true;
            }
        }

        return false;
    }

    /**
     * Selects a config by name.
     * @param name the name of the config to select.
     */
    private void selectConfig(String name) {
        final int count = mDeviceConfigCombo.getItemCount();
        for (int i = 0 ; i < count ; i++) {
            String item = mDeviceConfigCombo.getItem(i);
            if (name.equals(item)) {
                mDeviceConfigCombo.select(i);
                return;
            }
        }
    }

    /**
     * Called when the selection of the device combo changes.
     * @param recomputeLayout
     */
    private void onDeviceChange(boolean recomputeLayout) {
        // because changing the content of a combo triggers a change event, respect the
        // mDisableUpdates flag
        if (mDisableUpdates > 0) {
            return;
        }

        String newConfigName = null;

        int deviceIndex = mDeviceCombo.getSelectionIndex();
        if (deviceIndex != -1) {
            // check if the user is asking for the custom item
            if (deviceIndex == mDeviceCombo.getItemCount() - 1) {
                onCustomDeviceConfig();
                return;
            }

            // get the previous config, so that we can look for a close match
            if (mState.device != null) {
                int index = mDeviceConfigCombo.getSelectionIndex();
                if (index != -1) {
                    FolderConfiguration oldConfig = mState.device.getConfigs().get(
                            mDeviceConfigCombo.getItem(index));

                    LayoutDevice newDevice = mDeviceList.get(deviceIndex);

                    newConfigName = getClosestMatch(oldConfig, newDevice.getConfigs());
                }
            }

            mState.device = mDeviceList.get(deviceIndex);
        } else {
            mState.device = null;
        }

        fillConfigCombo(newConfigName);

        computeCurrentConfig(false /*force*/);

        if (recomputeLayout) {
            onDeviceConfigChange();
        }
    }

    /**
     * Handles a user request for the {@link ConfigManagerDialog}.
     */
    private void onCustomDeviceConfig() {
        ConfigManagerDialog dialog = new ConfigManagerDialog(getShell());
        dialog.open();

        // save the user devices
        Sdk.getCurrent().getLayoutDeviceManager().save();

        // Update the UI with no triggered event
        mDisableUpdates++;

        LayoutDevice oldCurrent = mState.device;

        // but first, update the device combo
        initDevices();

        // attempts to reselect the current device.
        if (selectDevice(oldCurrent)) {
            // current device still exists.
            // reselect the config
            selectConfig(mState.configName);

            // reset the UI as if it was just a replacement file, since we can keep
            // the current device (and possibly config).
            adaptConfigSelection(false /*needBestMatch*/);

        } else {
            // find a new device/config to match the current file.
            findAndSetCompatibleConfig(false /*favorCurrentConfig*/);
        }

        mDisableUpdates--;

        // recompute the current config
        computeCurrentConfig(false /*force*/);

        // force a redraw
        onDeviceChange(true /*recomputeLayout*/);
    }

    /**
     * Attempts to find a close config among a list
     * @param oldConfig the reference config.
     * @param configs the list of config to search through
     * @return the name of the closest config match, or possibly null if no configs are compatible
     * (this can only happen if the configs don't have a single qualifier that is the same).
     */
    private String getClosestMatch(FolderConfiguration oldConfig,
            Map<String, FolderConfiguration> configs) {

        // create 2 lists as we're going to go through one and put the candidates in the other.
        ArrayList<Entry<String, FolderConfiguration>> list1 =
            new ArrayList<Entry<String,FolderConfiguration>>();
        ArrayList<Entry<String, FolderConfiguration>> list2 =
            new ArrayList<Entry<String,FolderConfiguration>>();

        list1.addAll(configs.entrySet());

        final int count = FolderConfiguration.getQualifierCount();
        for (int i = 0 ; i < count ; i++) {
            // compute the new candidate list by only taking configs that have
            // the same i-th qualifier as the old config
            for (Entry<String, FolderConfiguration> entry : list1) {
                ResourceQualifier oldQualifier = oldConfig.getQualifier(i);

                FolderConfiguration config = entry.getValue();
                ResourceQualifier newQualifier = config.getQualifier(i);

                if (oldQualifier == null) {
                    if (newQualifier == null) {
                        list2.add(entry);
                    }
                } else if (oldQualifier.equals(newQualifier)) {
                    list2.add(entry);
                }
            }

            // at any moment if the new candidate list contains only one match, its name
            // is returned.
            if (list2.size() == 1) {
                return list2.get(0).getKey();
            }

            // if the list is empty, then all the new configs failed. It is considered ok, and
            // we move to the next qualifier anyway. This way, if a qualifier is different for
            // all new configs it is simply ignored.
            if (list2.size() != 0) {
                // move the candidates back into list1.
                list1.clear();
                list1.addAll(list2);
                list2.clear();
            }
        }

        // the only way to reach this point is if there's an exact match.
        // (if there are more than one, then there's a duplicate config and it doesn't matter,
        // we take the first one).
        if (list1.size() > 0) {
            return list1.get(0).getKey();
        }

        return null;
    }

    /**
     * fills the config combo with new values based on {@link #mCurrentState#device}.
     * @param refName an optional name. if set the selection will match this name (if found)
     */
    private void fillConfigCombo(String refName) {
        mDeviceConfigCombo.removeAll();

        if (mState.device != null) {
            Set<String> configNames = mState.device.getConfigs().keySet();

            int selectionIndex = 0;
            int i = 0;

            for (String name : configNames) {
                mDeviceConfigCombo.add(name);

                if (name.equals(refName)) {
                    selectionIndex = i;
                }
                i++;
            }

            mDeviceConfigCombo.select(selectionIndex);
            mDeviceConfigCombo.setEnabled(configNames.size() > 1);
        }
    }

    /**
     * Called when the device config selection changes.
     */
    private void onDeviceConfigChange() {
        // because changing the content of a combo triggers a change event, respect the
        // mDisableUpdates flag
        if (mDisableUpdates > 0) {
            return;
        }

        if (computeCurrentConfig(false /*force*/) && mListener != null) {
            mListener.onConfigurationChange();
        }
    }

    /**
     * Call back for language combo selection
     */
    private void onLocaleChange() {
        // because mLocaleList triggers onLanguageChange at each modification, the filling
        // of the combo with data will trigger notifications, and we don't want that.
        if (mDisableUpdates > 0) {
            return;
        }

        if (computeCurrentConfig(false /*force*/) &&  mListener != null) {
            mListener.onConfigurationChange();
        }
    }

    private void onDockChange() {
        if (computeCurrentConfig(false /*force*/) &&  mListener != null) {
            mListener.onConfigurationChange();
        }
    }

    private void onDayChange() {
        if (computeCurrentConfig(false /*force*/) &&  mListener != null) {
            mListener.onConfigurationChange();
        }
    }

    /**
     * Saves the current state and the current configuration
     * @param force forces saving the states even if updates are disabled
     *
     * @see #saveState(boolean)
     */
    private boolean computeCurrentConfig(boolean force) {
        saveState(force);

        if (mState.device != null) {
            // get the device config from the device/config combos.
            int configIndex = mDeviceConfigCombo.getSelectionIndex();
            String name = mDeviceConfigCombo.getItem(configIndex);
            FolderConfiguration config = mState.device.getConfigs().get(name);

            // replace the config with the one from the device
            mCurrentConfig.set(config);

            // replace the locale qualifiers with the one coming from the locale combo
            int localeIndex = mLocaleCombo.getSelectionIndex();
            if (localeIndex != -1) {
                ResourceQualifier[] localeQualifiers = mLocaleList.get(localeIndex);

                mCurrentConfig.setLanguageQualifier(
                        (LanguageQualifier)localeQualifiers[LOCALE_LANG]);
                mCurrentConfig.setRegionQualifier(
                        (RegionQualifier)localeQualifiers[LOCALE_REGION]);
            }

            int index = mDockCombo.getSelectionIndex();
            if (index == -1) {
                index = 0; // no selection = 0
            }
            mCurrentConfig.setDockModeQualifier(new DockModeQualifier(DockMode.getByIndex(index)));

            index = mNightCombo.getSelectionIndex();
            if (index == -1) {
                index = 0; // no selection = 0
            }
            mCurrentConfig.setNightModeQualifier(
                    new NightModeQualifier(NightMode.getByIndex(index)));

            // update the create button.
            checkCreateEnable();

            return true;
        }

        return false;
    }

    private void onThemeChange() {
        saveState(false /*force*/);

        int themeIndex = mThemeCombo.getSelectionIndex();
        if (themeIndex != -1) {
            String theme = mThemeCombo.getItem(themeIndex);

            if (theme.equals(THEME_SEPARATOR)) {
                mThemeCombo.select(0);
            }

            if (mListener != null) {
                mListener.onThemeChange();
            }
        }
    }

    private void onClippingChange() {
        mClipping = mClippingButton.getSelection();
        if (mListener != null) {
            mListener.onClippingChange();
        }
    }

    /**
     * Returns whether the given <var>style</var> is a theme.
     * This is done by making sure the parent is a theme.
     * @param value the style to check
     * @param styleMap the map of styles for the current project. Key is the style name.
     * @return True if the given <var>style</var> is a theme.
     */
    private boolean isTheme(IResourceValue value, Map<String, IResourceValue> styleMap) {
        if (value instanceof IStyleResourceValue) {
            IStyleResourceValue style = (IStyleResourceValue)value;

            boolean frameworkStyle = false;
            String parentStyle = style.getParentStyle();
            if (parentStyle == null) {
                // if there is no specified parent style we look an implied one.
                // For instance 'Theme.light' is implied child style of 'Theme',
                // and 'Theme.light.fullscreen' is implied child style of 'Theme.light'
                String name = style.getName();
                int index = name.lastIndexOf('.');
                if (index != -1) {
                    parentStyle = name.substring(0, index);
                }
            } else {
                // remove the useless @ if it's there
                if (parentStyle.startsWith("@")) {
                    parentStyle = parentStyle.substring(1);
                }

                // check for framework identifier.
                if (parentStyle.startsWith("android:")) {
                    frameworkStyle = true;
                    parentStyle = parentStyle.substring("android:".length());
                }

                // at this point we could have the format style/<name>. we want only the name
                if (parentStyle.startsWith("style/")) {
                    parentStyle = parentStyle.substring("style/".length());
                }
            }

            if (parentStyle != null) {
                if (frameworkStyle) {
                    // if the parent is a framework style, it has to be 'Theme' or 'Theme.*'
                    return parentStyle.equals("Theme") || parentStyle.startsWith("Theme.");
                } else {
                    // if it's a project style, we check this is a theme.
                    value = styleMap.get(parentStyle);
                    if (value != null) {
                        return isTheme(value, styleMap);
                    }
                }
            }
        }

        return false;
    }

    private void checkCreateEnable() {
        mCreateButton.setEnabled(mEditedConfig.equals(mCurrentConfig) == false);
    }

    /**
     * Checks whether the current edited file is the best match for a given config.
     * <p/>
     * This tests against other versions of the same layout in the project.
     * <p/>
     * The given config must be compatible with the current edited file.
     * @param config the config to test.
     * @return true if the current edited file is the best match in the project for the
     * given config.
     */
    private boolean isCurrentFileBestMatchFor(FolderConfiguration config) {
        ResourceFile match = mResources.getMatchingFile(mEditedFile.getName(),
                ResourceFolderType.LAYOUT, config);

        if (match != null) {
            return match.getFile().equals(mEditedFile);
        } else {
            // if we stop here that means the current file is not even a match!
            AdtPlugin.log(IStatus.ERROR, "Current file is not a match for the given config.");
        }

        return false;
    }
}

