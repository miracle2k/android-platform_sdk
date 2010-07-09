/*
 * Copyright (C) 2009 The Android Open Source Project
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

import com.android.ide.eclipse.adt.internal.editors.IconFactory;
import com.android.ide.eclipse.adt.internal.resources.configurations.DockModeQualifier;
import com.android.ide.eclipse.adt.internal.resources.configurations.FolderConfiguration;
import com.android.ide.eclipse.adt.internal.resources.configurations.LanguageQualifier;
import com.android.ide.eclipse.adt.internal.resources.configurations.NightModeQualifier;
import com.android.ide.eclipse.adt.internal.resources.configurations.RegionQualifier;
import com.android.ide.eclipse.adt.internal.resources.configurations.ResourceQualifier;
import com.android.ide.eclipse.adt.internal.resources.configurations.VersionQualifier;
import com.android.ide.eclipse.adt.internal.sdk.LayoutDevice;
import com.android.ide.eclipse.adt.internal.ui.ConfigurationSelector;
import com.android.ide.eclipse.adt.internal.ui.ConfigurationSelector.ConfigurationState;
import com.android.ide.eclipse.adt.internal.ui.ConfigurationSelector.IQualifierFilter;
import com.android.sdkuilib.ui.GridDialog;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.VerifyEvent;
import org.eclipse.swt.events.VerifyListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParseException;
import java.util.regex.Pattern;

/**
 * Dialog to edit both a {@link LayoutDevice}, and a {@link FolderConfiguration} at the same time.
 */
public class ConfigEditDialog extends GridDialog {

    private static final Pattern FLOAT_PATTERN;
    static {
        // get the decimal separator char
        DecimalFormatSymbols dfs = new DecimalFormatSymbols();

        // make a pattern that's basically ##.# where .# is optional and where . can be . or ,
        // depending on the locale
        FLOAT_PATTERN = Pattern.compile(
            "\\d*(" +                                                       //$NON-NLS-1$
            Pattern.quote(new String(new char[] { dfs.getDecimalSeparator() })) +
            "\\d?)?");                                                      //$NON-NLS-1$
    }

    private final FolderConfiguration mConfig = new FolderConfiguration();

    private ConfigurationSelector mConfigSelector;
    private Composite mStatusComposite;
    private Label mStatusLabel;
    private Label mStatusImage;

    private Image mError;

    private String mDeviceName;
    private String mConfigName;
    private float mXDpi = Float.NaN;
    private float mYDpi = Float.NaN;

    private final DecimalFormat mDecimalFormat = new DecimalFormat();


    public ConfigEditDialog(Shell parentShell, FolderConfiguration config) {
        super(parentShell, 1, false);
        mConfig.set(config);
    }

    public void setDeviceName(String name) {
        mDeviceName = name;
    }

    public String getDeviceName() {
        return mDeviceName;
    }

    public void setXDpi(float xdpi) {
        mXDpi = xdpi;
    }

    public float getXDpi() {
        return mXDpi;
    }

    public void setYDpi(float ydpi) {
        mYDpi = ydpi;
    }

    public float getYDpi() {
        return mYDpi;
    }

    public void setConfigName(String name) {
        mConfigName = name;
    }

    public String getConfigName() {
        return mConfigName;
    }

    public void setConfig(FolderConfiguration config) {
        mConfig.set(config);
    }

    public void getConfig(FolderConfiguration config) {
        config.set(mConfig);
    }

    @Override
    public void createDialogContent(Composite parent) {
        mError = IconFactory.getInstance().getIcon("error"); //$NON-NLS-1$

        Group deviceGroup = new Group(parent, SWT.NONE);
        deviceGroup.setText("Device");
        deviceGroup.setLayoutData(new GridData(GridData.FILL_BOTH));
        deviceGroup.setLayout(new GridLayout(2, false));

        Label l = new Label(deviceGroup, SWT.None);
        l.setText("Name");

        final Text deviceNameText = new Text(deviceGroup, SWT.BORDER);
        deviceNameText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        if (mDeviceName != null) {
            deviceNameText.setText(mDeviceName);
        }
        deviceNameText.addModifyListener(new ModifyListener() {
            public void modifyText(ModifyEvent e) {
                mDeviceName = deviceNameText.getText().trim();
                validateOk();
            }
        });


        VerifyListener floatVerifier = new VerifyListener() {
            public void verifyText(VerifyEvent event) {
                // combine the current content and the new text
                String text = ((Text)event.widget).getText();
                text = text.substring(0, event.start) + event.text + text.substring(event.end);

                // now make sure it's a match for the regex
                event.doit = FLOAT_PATTERN.matcher(text).matches();
            }
        };

        l = new Label(deviceGroup, SWT.None);
        l.setText("x dpi");

        final Text deviceXDpiText = new Text(deviceGroup, SWT.BORDER);
        deviceXDpiText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        if (Float.isNaN(mXDpi) == false) {
            deviceXDpiText.setText(String.format("%.1f", mXDpi)); //$NON-NLS-1$
        }
        deviceXDpiText.addVerifyListener(floatVerifier);
        deviceXDpiText.addModifyListener(new ModifyListener() {
            public void modifyText(ModifyEvent e) {
                String value = deviceXDpiText.getText();
                if (value.length() == 0) {
                    mXDpi = Float.NaN;
                } else {
                    try {
                        mXDpi = mDecimalFormat.parse(value).floatValue();
                    } catch (ParseException exception) {
                        mXDpi = Float.NaN;
                    }
                }
            }
        });

        l = new Label(deviceGroup, SWT.None);
        l.setText("y dpi");

        final Text deviceYDpiText = new Text(deviceGroup, SWT.BORDER);
        deviceYDpiText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        if (Float.isNaN(mYDpi) == false) {
            deviceYDpiText.setText(String.format("%.1f", mYDpi)); //$NON-NLS-1$
        }
        deviceYDpiText.addVerifyListener(floatVerifier);
        deviceYDpiText.addModifyListener(new ModifyListener() {
            public void modifyText(ModifyEvent e) {
                String value = deviceYDpiText.getText();
                if (value.length() == 0) {
                    mYDpi = Float.NaN;
                } else {
                    try {
                        mYDpi = mDecimalFormat.parse(value).floatValue();
                    } catch (ParseException exception) {
                        mYDpi = Float.NaN;
                    }
                }
            }
        });

        Group configGroup = new Group(parent, SWT.NONE);
        configGroup.setText("Configuration");
        configGroup.setLayoutData(new GridData(GridData.FILL_BOTH));
        configGroup.setLayout(new GridLayout(2, false));

        l = new Label(configGroup, SWT.None);
        l.setText("Name");

        final Text configNameText = new Text(configGroup, SWT.BORDER);
        configNameText.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        if (mConfigName != null) {
            configNameText.setText(mConfigName);
        }
        configNameText.addModifyListener(new ModifyListener() {
            public void modifyText(ModifyEvent e) {
                mConfigName = configNameText.getText().trim();
                validateOk();
            }
        });

        mConfigSelector = new ConfigurationSelector(configGroup, true /*deviceMode*/);
        // configure the selector to be in "device mode" and not accept language/region/version
        // since those are selected from a different combo
        // FIXME: add version combo.
        mConfigSelector.setQualifierFilter(new IQualifierFilter() {
            public boolean accept(ResourceQualifier qualifier) {
                if (qualifier instanceof LanguageQualifier ||
                        qualifier instanceof RegionQualifier ||
                        qualifier instanceof DockModeQualifier ||
                        qualifier instanceof NightModeQualifier ||
                        qualifier instanceof VersionQualifier) {
                    return false;
                }

                return true;
            }
        });
        mConfigSelector.setConfiguration(mConfig);
        GridData gd;
        mConfigSelector.setLayoutData(gd = new GridData(GridData.FILL_HORIZONTAL));
        gd.horizontalSpan = 2;
        gd.widthHint = ConfigurationSelector.WIDTH_HINT;
        gd.heightHint = ConfigurationSelector.HEIGHT_HINT;

        // add a listener to check on the validity of the FolderConfiguration as
        // they are built.
        mConfigSelector.setOnChangeListener(new Runnable() {
            public void run() {
                if (mConfigSelector.getState() == ConfigurationState.OK) {
                    mConfigSelector.getConfiguration(mConfig);
                }

                validateOk();
            }
        });

        mStatusComposite = new Composite(parent, SWT.NONE);
        mStatusComposite.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        GridLayout gl = new GridLayout(2, false);
        mStatusComposite.setLayout(gl);
        gl.marginHeight = gl.marginWidth = 0;

        mStatusImage = new Label(mStatusComposite, SWT.NONE);
        mStatusLabel = new Label(mStatusComposite, SWT.NONE);
        mStatusLabel.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        resetStatus();
    }

    @Override
    protected Control createContents(Composite parent) {
        Control c = super.createContents(parent);
        validateOk();
        return c;
    }

    /**
     * resets the status label to show the file that will be created.
     */
    private void resetStatus() {
        String displayString = Dialog.shortenText(
                String.format("Config: %1$s", mConfig.toString()),
                mStatusLabel);
        mStatusLabel.setText(displayString);
    }

    private void setError(String text) {
        String displayString = Dialog.shortenText(text, mStatusLabel);
        mStatusLabel.setText(displayString);
        mStatusImage.setImage(mError);
        getButton(IDialogConstants.OK_ID).setEnabled(false);
    }

    private void validateOk() {
        // check the device name
        if (mDeviceName == null || mDeviceName.length() == 0) {
            setError("Device name must not be empty");
            return;
        }

        // check the config name
        if (mConfigName == null || mConfigName.length() == 0) {
            setError("Configuration name must not be empty");
            return;
        }

        // and check the config itself
        ConfigurationState state = mConfigSelector.getState();

        switch (state) {
            case INVALID_CONFIG:
                ResourceQualifier invalidQualifier = mConfigSelector.getInvalidQualifier();
                setError(String.format(
                        "Invalid Configuration: %1$s has no filter set.",
                        invalidQualifier.getName()));
                return;
            case REGION_WITHOUT_LANGUAGE:
                setError("The Region qualifier requires the Language qualifier.");
                return;
        }

        // no error
        mStatusImage.setImage(null);
        resetStatus();
        getButton(IDialogConstants.OK_ID).setEnabled(true);

        // need to relayout, because of the change in size in mErrorImage.
        mStatusComposite.layout();
    }
}
