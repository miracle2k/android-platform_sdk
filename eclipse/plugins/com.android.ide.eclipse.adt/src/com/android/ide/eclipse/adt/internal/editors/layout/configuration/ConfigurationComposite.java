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
import com.android.ide.eclipse.adt.internal.resources.configurations.FolderConfiguration;
import com.android.ide.eclipse.adt.internal.resources.configurations.LanguageQualifier;
import com.android.ide.eclipse.adt.internal.resources.configurations.PixelDensityQualifier;
import com.android.ide.eclipse.adt.internal.resources.configurations.RegionQualifier;
import com.android.ide.eclipse.adt.internal.resources.configurations.ResourceQualifier;
import com.android.ide.eclipse.adt.internal.resources.configurations.ScreenDimensionQualifier;
import com.android.ide.eclipse.adt.internal.resources.configurations.ScreenOrientationQualifier;
import com.android.ide.eclipse.adt.internal.resources.configurations.PixelDensityQualifier.Density;
import com.android.ide.eclipse.adt.internal.resources.configurations.ScreenOrientationQualifier.ScreenOrientation;
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
import com.android.ide.eclipse.adt.internal.ui.ConfigurationSelector.LanguageRegionVerifier;
import com.android.layoutlib.api.IResourceValue;
import com.android.layoutlib.api.IStyleResourceValue;
import com.android.sdklib.IAndroidTarget;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IStatus;
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
 * - {@link #openFile(FolderConfiguration, IAndroidTarget)}<br>
 *   Called after the creation to init the composite with a file being opened in a new editor.<br>
 *<br>
 * - {@link #replaceFile(FolderConfiguration)}<br>
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

    private final static String THEME_SEPARATOR = "----------"; //$NON-NLS-1$

    private final static String FAKE_LOCALE_VALUE = "__"; //$NON-NLS-1$

    private final static int LOCALE_LANG = 0;
    private final static int LOCALE_REGION = 1;

    private Button mClippingButton;
    private Label mCurrentLayoutLabel;

    private Combo mDeviceCombo;
    private Combo mDeviceConfigCombo;
    private Combo mLocaleCombo;
    private Combo mThemeCombo;
    private Button mCreateButton;

    private int mPlatformThemeCount = 0;
    private boolean mDisableUpdates = false;

    private List<LayoutDevice> mDeviceList;

    private final ArrayList<ResourceQualifier[] > mLocaleList =
        new ArrayList<ResourceQualifier[]>();

    /**
     * clipping value. If true, the rendering is limited to the screensize. This is the default
     * value
     */
    private boolean mClipping = true;

    /**
     * TODO: remove as it's saved in mCurrentStave. Just need to make sure there's no NPE when mCurrentState is null.
     */
    private LayoutDevice mCurrentDevice;

    private SelectionState mCurrentState = null;

    private boolean mSdkChanged = false;

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
     * State of the config selection. This is used during UI reset to attempt to return the
     * rendering to its original configuration.
     */
    private static class SelectionState {
        String deviceName;
        String configName;
        ResourceQualifier[] locale;
        String theme;
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
        int cols = 10;  // device*2+config*2+locale*2+separator*2+theme+createBtn

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

        new Label(this, SWT.NONE).setText("Devices");
        mDeviceCombo = new Combo(this, SWT.DROP_DOWN | SWT.READ_ONLY);
        mDeviceCombo.setLayoutData(new GridData(
                GridData.HORIZONTAL_ALIGN_FILL | GridData.GRAB_HORIZONTAL));
        mDeviceCombo.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                onDeviceChange(true /* recomputeLayout*/);
            }
        });

        new Label(this, SWT.NONE).setText("Config");
        mDeviceConfigCombo = new Combo(this, SWT.DROP_DOWN | SWT.READ_ONLY);
        mDeviceConfigCombo.setLayoutData(new GridData(
                GridData.HORIZONTAL_ALIGN_FILL | GridData.GRAB_HORIZONTAL));
        mDeviceConfigCombo.addSelectionListener(new SelectionAdapter() {
            @Override
             public void widgetSelected(SelectionEvent e) {
                onDeviceConfigChange();
            }
        });

        new Label(this, SWT.NONE).setText("Locale");
        mLocaleCombo = new Combo(this, SWT.DROP_DOWN | SWT.READ_ONLY);
        mLocaleCombo.setLayoutData(new GridData(
                GridData.HORIZONTAL_ALIGN_FILL | GridData.GRAB_HORIZONTAL));
        mLocaleCombo.addVerifyListener(new LanguageRegionVerifier());
        mLocaleCombo.addSelectionListener(new SelectionListener() {
            public void widgetDefaultSelected(SelectionEvent e) {
                onLocaleChange();
            }
            public void widgetSelected(SelectionEvent e) {
                onLocaleChange();
            }
        });

        // first separator
        Label separator = new Label(this, SWT.SEPARATOR | SWT.VERTICAL);
        separator.setLayoutData(gd = new GridData(
                GridData.VERTICAL_ALIGN_FILL | GridData.GRAB_VERTICAL));
        gd.heightHint = 0;

        mThemeCombo = new Combo(this, SWT.READ_ONLY | SWT.DROP_DOWN);
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
     * Init the UI with a given file configuration and project target. This must only be called
     * the first time the {@link ConfigurationComposite} is created.
     * <p/>This will NOT trigger a redraw event (will not call
     * {@link IConfigListener#onConfigurationChange()}.)
     * The state of the selection of the various combos will be initialized to default values that
     * are compatible with the opened file.
     *
     * @param file the file being opened
     *
     * @see #replaceFile(FolderConfiguration)
     * @see #changeFileOnNewConfig(FolderConfiguration)
     */
    public void openFile(IFile file) {
        mEditedFile = file;
        IProject iProject = mEditedFile.getProject();

        mDisableUpdates = true; // we do not want to trigger onXXXChange when setting
                                // new values in the widgets.

        // only attempt to do anything if the SDK and targets are loaded.
        LoadStatus sdkStatus = AdtPlugin.getDefault().getSdkLoadStatus();
        if (sdkStatus == LoadStatus.LOADED) {
            // init the devices since the SDK is loaded.
            initDevices();

            Sdk currentSdk = Sdk.getCurrent();
            if (currentSdk != null) {
                mTarget = currentSdk.getTarget(iProject);
            }

            LoadStatus targetStatus = Sdk.getCurrent().checkAndLoadTargetData(mTarget, null);

            if (targetStatus == LoadStatus.LOADED) {
                mResources = ResourceManager.getInstance().getProjectResources(iProject);
                ResourceFolder resFolder = mResources.getResourceFolder((IFolder)file.getParent());
                mEditedConfig = resFolder.getConfiguration();

                // update the themes and locales.
                updateThemes();
                updateLocales();

                // update the clipping state
                AndroidTargetData data = Sdk.getCurrent().getTargetData(mTarget);
                if (data != null) {
                    LayoutBridge bridge = data.getLayoutBridge();
                    setClippingSupport(bridge.apiLevel >= 4);
                }

                // attempt to find a device/locale that can display this particular config.
                findAndSetCompatibleConfig(false /*favorCurrentConfig*/);

                // compute the final current config
                computeCurrentConfig();

                // update the string showing the config value
                updateConfigDisplay(mEditedConfig);

                saveState();
            }
        }

        mDisableUpdates = false;
    }

    /**
     * Replaces the UI with a given file configuration. This is meant to answer the user
     * Explicitly opening a different version of the same layout from the Package Explorer.
     * <p/>This attempts to keep the current config, but may change it if it's not compatible.
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
        if (mCurrentDevice == null) {
            openFile(file);
            return;
        }

        mEditedFile = file;
        IProject iProject = mEditedFile.getProject();
        mResources = ResourceManager.getInstance().getProjectResources(iProject);

        ResourceFolder resFolder = ResourceManager.getInstance().getResourceFolder(file);
        mEditedConfig = resFolder.getConfiguration();

        mDisableUpdates = true; // we do not want to trigger onXXXChange when setting
        // new values in the widgets.

        // only attempt to do anything if the SDK and targets are loaded.
        LoadStatus sdkStatus = AdtPlugin.getDefault().getSdkLoadStatus();
        if (sdkStatus == LoadStatus.LOADED) {
            LoadStatus targetStatus = Sdk.getCurrent().checkAndLoadTargetData(mTarget, null);

            if (targetStatus == LoadStatus.LOADED) {

                // update the current config selection to make sure it's
                // compatible with the new file
                adaptConfigSelection();

                // compute the final current config
                computeCurrentConfig();

                // update the string showing the config value
                updateConfigDisplay(mEditedConfig);

                saveState();
            }
        }

        mDisableUpdates = false;
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

        mDisableUpdates = true; // we do not want to trigger onXXXChange when setting
                                // new values in the widgets.

        // this is going to be followed by a call to onTargetLoaded.
        // So we can only care about the layout devices in this case.
        initDevices();

        mDisableUpdates = false;
    }

    /**
     * Responds to the event that the {@link IAndroidTarget} data was loaded, or the project's
     * target changed
     */
    public void onTargetChange() {
        if (mCurrentState == null) {
            // this means the file was opened before the target finished loaded.
            // This is basically an initial call to openFile that's delayed.
            openFile(mEditedFile);
            return;
        }

        // update the resource and config if they are not present
        if (mResources == null) {
            mResources = ResourceManager.getInstance().getProjectResources(
                    mEditedFile.getProject());
        }

        if (mEditedConfig == null) {
            ResourceFolder resFolder = mResources.getResourceFolder(
                    (IFolder)mEditedFile.getParent());
            mEditedConfig = resFolder.getConfiguration();
        }

        mDisableUpdates = true; // we do not want to trigger onXXXChange when setting
                                // new values in the widgets.

        // update the themes. The locales need not be updated as they are stricly based on the
        // content of the project
        updateThemes();

        // at this point, this means the target of the project was changed, or the whole SDK
        // was changed reloaded (different location?).
        // The former means the devices/configs are still there, the latter means they've
        // been reloaded (in #onSdkLoaded).
        if (mSdkChanged) {
            findAndSetCompatibleConfig(false /*favorCurrentConfig*/);
        } else {
            adaptConfigSelection();
        }

        // update the clipping state
        AndroidTargetData data = Sdk.getCurrent().getTargetData(mTarget);
        if (data != null) {
            LayoutBridge bridge = data.getLayoutBridge();
            setClippingSupport(bridge.apiLevel >= 4);
        }

        computeCurrentConfig();

        mDisableUpdates = false;
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
                selectDevice(mCurrentDevice = anyDeviceMatch);
                fillConfigCombo(anyConfigMatchName);
                mLocaleCombo.select(anyLocaleIndex);

                // TODO: display a better warning!
                computeCurrentConfig();
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
            selectDevice(mCurrentDevice = bestDeviceMatch);
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
    private void adaptConfigSelection() {
        // check the device config (ie sans locale)
        boolean needConfigChange = true; // if still true, we need to find another config.
        boolean currentConfigIsCompatible = false;
        int configIndex = mDeviceConfigCombo.getSelectionIndex();
        if (configIndex != -1) {
            String configName = mDeviceConfigCombo.getItem(configIndex);
            FolderConfiguration currentConfig = mCurrentDevice.getConfigs().get(configName);
            if (mEditedConfig.isMatchFor(currentConfig)) {
                currentConfigIsCompatible = true; // current config is compatible
                if (isCurrentFileBestMatchFor(currentConfig)) {
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
            Map<String, FolderConfiguration> configs = mCurrentDevice.getConfigs();
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
                    if (FAKE_LOCALE_VALUE.equals(
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

    private void saveState() {
        if (mCurrentDevice != null) {
            if (mCurrentState == null) {
                mCurrentState = new SelectionState();
            }

            mCurrentState.deviceName = mCurrentDevice.getName();

            int index = mDeviceConfigCombo.getSelectionIndex();
            if (index != -1) {
                mCurrentState.configName = mDeviceConfigCombo.getItem(index);
            } else {
                mCurrentState.configName = null;
            }

            // since the locales are relative to the project, only keeping the index is enough
            index = mLocaleCombo.getSelectionIndex();
            if (index != -1) {
                mCurrentState.locale = mLocaleList.get(index);
            } else {
                mCurrentState.locale = null;
            }

            index = mThemeCombo.getSelectionIndex();
            if (index != -1) {
                mCurrentState.theme = mThemeCombo.getItem(index);
            }
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

        mDisableUpdates = true;

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
                        new RegionQualifier(FAKE_LOCALE_VALUE)
                });
            }
        }

        // add a locale not present in the project resources. This will let the dev
        // tests his/her default values.
        if (hasLocale) {
            mLocaleCombo.add("Other");
        } else {
            mLocaleCombo.add("Any");
        }

        // create language/region qualifier that will never be matched by qualified resources.
        mLocaleList.add(new ResourceQualifier[] {
                new LanguageQualifier(FAKE_LOCALE_VALUE),
                new RegionQualifier(FAKE_LOCALE_VALUE)
        });

        if (mCurrentState != null && mCurrentState.locale != null) {
            // FIXME: this may fails if the layout was deleted (and was the last one to have that local.
            // (we have other problem in this case though)
            setLocaleCombo(mCurrentState.locale[LOCALE_LANG],
                    mCurrentState.locale[LOCALE_REGION]);
        } else {
            mLocaleCombo.select(0);
        }

        mThemeCombo.getParent().layout();

        mDisableUpdates = false;
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

        mDisableUpdates = true;

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
        if (mCurrentState != null && mCurrentState.theme != null) {
            final int count = mThemeCombo.getItemCount();
            for (int i = 0 ; i < count ; i++) {
                if (mCurrentState.theme.equals(mThemeCombo.getItem(i))) {
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

        mDisableUpdates = false;
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
        if (mCurrentDevice != null) {
            float dpi = mCurrentDevice.getXDpi();
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
        if (mCurrentDevice != null) {
            float dpi = mCurrentDevice.getYDpi();
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
        if (mDisableUpdates == true) {
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
            if (mCurrentDevice != null) {
                int index = mDeviceConfigCombo.getSelectionIndex();
                if (index != -1) {
                    FolderConfiguration oldConfig = mCurrentDevice.getConfigs().get(
                            mDeviceConfigCombo.getItem(index));

                    LayoutDevice newDevice = mDeviceList.get(deviceIndex);

                    newConfigName = getClosestMatch(oldConfig, newDevice.getConfigs());
                }
            }

            mCurrentDevice = mDeviceList.get(deviceIndex);
        } else {
            mCurrentDevice = null;
        }

        fillConfigCombo(newConfigName);

        computeCurrentConfig();

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
        mDisableUpdates = true;

        LayoutDevice oldCurrent = mCurrentDevice;

        // but first, update the device combo
        initDevices();

        // attempts to reselect the current device.
        if (selectDevice(oldCurrent)) {
            // current device still exists.
            // reselect the config
            selectConfig(mCurrentState.configName);

            // reset the UI as if it was just a replacement file, since we can keep
            // the current device (and possibly config).
            adaptConfigSelection();

        } else {
            // find a new device/config to match the current file.
            findAndSetCompatibleConfig(false /*favorCurrentConfig*/);
        }

        mDisableUpdates = false;

        // recompute the current config
        computeCurrentConfig();

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
     * fills the config combo with new values based on {@link #mCurrentDevice}.
     * @param refName an optional name. if set the selection will match this name (if found)
     */
    private void fillConfigCombo(String refName) {
        mDeviceConfigCombo.removeAll();

        if (mCurrentDevice != null) {
            Set<String> configNames = mCurrentDevice.getConfigs().keySet();

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
        if (mDisableUpdates == true) {
            return;
        }

        if (computeCurrentConfig() && mListener != null) {
            mListener.onConfigurationChange();
        }
    }

    /**
     * Call back for language combo selection
     */
    private void onLocaleChange() {
        // because mLanguage triggers onLanguageChange at each modification, the filling
        // of the combo with data will trigger notifications, and we don't want that.
        if (mDisableUpdates == true) {
            return;
        }

        if (computeCurrentConfig() &&  mListener != null) {
            mListener.onConfigurationChange();
        }
    }

    /**
     * Saves the current state and the current configuration
     */
    private boolean computeCurrentConfig() {
        saveState();

        if (mCurrentDevice != null) {
            // get the device config from the device/config combos.
            int configIndex = mDeviceConfigCombo.getSelectionIndex();
            String name = mDeviceConfigCombo.getItem(configIndex);
            FolderConfiguration config = mCurrentDevice.getConfigs().get(name);

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

            // update the create button.
            checkCreateEnable();

            return true;
        }

        return false;
    }

    private void onThemeChange() {
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

        saveState();
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

