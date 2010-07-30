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

package com.android.ide.eclipse.adt.internal.sdk;

import com.android.ide.eclipse.adt.internal.resources.configurations.CountryCodeQualifier;
import com.android.ide.eclipse.adt.internal.resources.configurations.FolderConfiguration;
import com.android.ide.eclipse.adt.internal.resources.configurations.KeyboardStateQualifier;
import com.android.ide.eclipse.adt.internal.resources.configurations.NavigationMethodQualifier;
import com.android.ide.eclipse.adt.internal.resources.configurations.NavigationStateQualifier;
import com.android.ide.eclipse.adt.internal.resources.configurations.NetworkCodeQualifier;
import com.android.ide.eclipse.adt.internal.resources.configurations.PixelDensityQualifier;
import com.android.ide.eclipse.adt.internal.resources.configurations.ScreenDimensionQualifier;
import com.android.ide.eclipse.adt.internal.resources.configurations.ScreenOrientationQualifier;
import com.android.ide.eclipse.adt.internal.resources.configurations.ScreenRatioQualifier;
import com.android.ide.eclipse.adt.internal.resources.configurations.ScreenSizeQualifier;
import com.android.ide.eclipse.adt.internal.resources.configurations.TextInputMethodQualifier;
import com.android.ide.eclipse.adt.internal.resources.configurations.TouchScreenQualifier;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Class representing a layout device.
 *
 * A Layout device is a collection of {@link FolderConfiguration} that can be used to render Android
 * layout files.
 *
 * It also contains a single xdpi/ydpi that is independent of the {@link FolderConfiguration}.
 *
 * If the device is meant to represent a true device, then most of the FolderConfigurations' content
 * should be identical, with only a few qualifiers (orientation, keyboard state) that would differ.
 * However it is simpler to reuse the FolderConfiguration class (with the non changing qualifiers
 * duplicated in each configuration) as it's what's being used by the rendering library.
 *
 * To create, edit and delete LayoutDevice objects, see {@link LayoutDeviceManager}.
 * The class is not technically immutable but behaves as such outside of its package.
 */
public class LayoutDevice {

    private final String mName;

    /**
     * Wrapper around a {@link FolderConfiguration}.
     * <p/>This adds a name, accessible through {@link #getName()}.
     * <p/>The folder config can be accessed through {@link #getConfig()}.
     *
     */
    public final static class DeviceConfig {
        private final String mName;
        private final FolderConfiguration mConfig;

        DeviceConfig(String name, FolderConfiguration config) {
            mName = name;
            mConfig = config;
        }

        public String getName() {
            return mName;
        }

        public FolderConfiguration getConfig() {
            return mConfig;
        }
    }

    /** editable list of the config */
    private final ArrayList<DeviceConfig> mConfigs = new ArrayList<DeviceConfig>();
    /** Read-only list */
    private List<DeviceConfig> mROList;

    private float mXDpi = Float.NaN;
    private float mYDpi = Float.NaN;

    LayoutDevice(String name) {
        mName = name;
    }

    /**
     * Saves the Layout Device into a document under a given node
     * @param doc the document.
     * @param parentNode the parent node.
     */
    void saveTo(Document doc, Element parentNode) {
        // create the device node
        Element deviceNode = createNode(doc, parentNode, LayoutDevicesXsd.NODE_DEVICE);

        // create the name attribute (no namespace on this one).
        deviceNode.setAttribute(LayoutDevicesXsd.ATTR_NAME, mName);

        // create a default with the x/y dpi
        Element defaultNode = createNode(doc, deviceNode, LayoutDevicesXsd.NODE_DEFAULT);
        if (Float.isNaN(mXDpi) == false) {
            Element xdpiNode = createNode(doc, defaultNode, LayoutDevicesXsd.NODE_XDPI);
            xdpiNode.setTextContent(Float.toString(mXDpi));
        }
        if (Float.isNaN(mYDpi) == false) {
            Element xdpiNode = createNode(doc, defaultNode, LayoutDevicesXsd.NODE_YDPI);
            xdpiNode.setTextContent(Float.toString(mYDpi));
        }

        // then save all the configs.
        synchronized (mConfigs) {
            for (DeviceConfig config : mConfigs) {
                saveConfigTo(doc, deviceNode, config.getName(), config.getConfig());
            }
        }
    }

    /**
     * Creates and returns a new NS-enabled node.
     * @param doc the {@link Document}
     * @param parentNode the parent node. The new node is appended to this one as a child.
     * @param name the name of the node.
     * @return the newly created node.
     */
    private Element createNode(Document doc, Element parentNode, String name) {
        Element newNode = doc.createElementNS(
                LayoutDevicesXsd.NS_LAYOUT_DEVICE_XSD, name);
        newNode.setPrefix(doc.lookupPrefix(LayoutDevicesXsd.NS_LAYOUT_DEVICE_XSD));
        parentNode.appendChild(newNode);

        return newNode;
    }

    /**
     * Saves a {@link FolderConfiguration} in a {@link Document}.
     * @param doc the Document in which to save
     * @param parent the parent node
     * @param configName the name of the config
     * @param config the config to save
     */
    private void saveConfigTo(Document doc, Element parent, String configName,
            FolderConfiguration config) {
        Element configNode = createNode(doc, parent, LayoutDevicesXsd.NODE_CONFIG);

        // create the name attribute (no namespace on this one).
        configNode.setAttribute(LayoutDevicesXsd.ATTR_NAME, configName);

        // now do the qualifiers
        CountryCodeQualifier ccq = config.getCountryCodeQualifier();
        if (ccq != null) {
            Element node = createNode(doc, configNode, LayoutDevicesXsd.NODE_COUNTRY_CODE);
            node.setTextContent(Integer.toString(ccq.getCode()));
        }

        NetworkCodeQualifier ncq = config.getNetworkCodeQualifier();
        if (ncq != null) {
            Element node = createNode(doc, configNode, LayoutDevicesXsd.NODE_NETWORK_CODE);
            node.setTextContent(Integer.toString(ncq.getCode()));
        }

        ScreenSizeQualifier ssq = config.getScreenSizeQualifier();
        if (ssq != null) {
            Element node = createNode(doc, configNode, LayoutDevicesXsd.NODE_SCREEN_SIZE);
            node.setTextContent(ssq.getFolderSegment());
        }

        ScreenRatioQualifier srq = config.getScreenRatioQualifier();
        if (srq != null) {
            Element node = createNode(doc, configNode, LayoutDevicesXsd.NODE_SCREEN_RATIO);
            node.setTextContent(srq.getFolderSegment());
        }

        ScreenOrientationQualifier soq = config.getScreenOrientationQualifier();
        if (soq != null) {
            Element node = createNode(doc, configNode, LayoutDevicesXsd.NODE_SCREEN_ORIENTATION);
            node.setTextContent(soq.getFolderSegment());
        }

        PixelDensityQualifier pdq = config.getPixelDensityQualifier();
        if (pdq != null) {
            Element node = createNode(doc, configNode, LayoutDevicesXsd.NODE_PIXEL_DENSITY);
            node.setTextContent(pdq.getFolderSegment());
        }

        TouchScreenQualifier ttq = config.getTouchTypeQualifier();
        if (ttq != null) {
            Element node = createNode(doc, configNode, LayoutDevicesXsd.NODE_TOUCH_TYPE);
            node.setTextContent(ttq.getFolderSegment());
        }

        KeyboardStateQualifier ksq = config.getKeyboardStateQualifier();
        if (ksq != null) {
            Element node = createNode(doc, configNode, LayoutDevicesXsd.NODE_KEYBOARD_STATE);
            node.setTextContent(ksq.getFolderSegment());
        }

        TextInputMethodQualifier timq = config.getTextInputMethodQualifier();
        if (timq != null) {
            Element node = createNode(doc, configNode, LayoutDevicesXsd.NODE_TEXT_INPUT_METHOD);
            node.setTextContent(timq.getFolderSegment());
        }

        NavigationStateQualifier nsq = config.getNavigationStateQualifier();
        if (nsq != null) {
            Element node = createNode(doc, configNode, LayoutDevicesXsd.NODE_NAV_STATE);
            node.setTextContent(nsq.getFolderSegment());
        }

        NavigationMethodQualifier nmq = config.getNavigationMethodQualifier();
        if (nmq != null) {
            Element node = createNode(doc, configNode, LayoutDevicesXsd.NODE_NAV_METHOD);
            node.setTextContent(nmq.getFolderSegment());
        }

        ScreenDimensionQualifier sdq = config.getScreenDimensionQualifier();
        if (sdq != null) {
            Element sizeNode = createNode(doc, configNode, LayoutDevicesXsd.NODE_SCREEN_DIMENSION);

            Element node = createNode(doc, sizeNode, LayoutDevicesXsd.NODE_SIZE);
            node.setTextContent(Integer.toString(sdq.getValue1()));

            node = createNode(doc, sizeNode, LayoutDevicesXsd.NODE_SIZE);
            node.setTextContent(Integer.toString(sdq.getValue2()));
        }
    }

    /**
     * Adds config to the LayoutDevice.
     * <p/>This ensures that no two configurations have the same. If a config already exists
     * with the same name, the new config replaces it.
     *
     * @param name the name of the config.
     * @param config the config.
     */
    void addConfig(String name, FolderConfiguration config) {
        synchronized (mConfigs) {
            doAddConfig(name, config);
            seal();
        }
    }

    /**
     * Adds a list of config to the LayoutDevice
     * <p/>This ensures that no two configurations have the same. If a config already exists
     * with the same name, the new config replaces it.

     * @param configs the configs to add.
     */
    void addConfigs(List<DeviceConfig> configs) {
        synchronized (mConfigs) {
            // add the configs manually one by one, to check for no duplicate.
            for (DeviceConfig config : configs) {
                String name = config.getName();

                for (DeviceConfig c : mConfigs) {
                    if (c.getName().equals(name)) {
                        mConfigs.remove(c);
                        break;
                    }
                }

                mConfigs.add(config);
            }

            seal();
        }
    }

    /**
     * Removes a config by its name.
     * @param name the name of the config to remove.
     */
    void removeConfig(String name) {
        synchronized (mConfigs) {
            for (DeviceConfig config : mConfigs) {
                if (config.getName().equals(name)) {
                    mConfigs.remove(config);
                    seal();
                    return;
                }
            }
        }
    }

    /**
     * Adds config to the LayoutDevice. This is to be used to add plenty of
     * configurations. It must be followed by {@link #_seal()}.
     * <p/>This ensures that no two configurations have the same. If a config already exists
     * with the same name, the new config replaces it.
     * <p/><strong>This must be called inside a <code>synchronized(mConfigs)</code> block.</strong>
     *
     * @param name the name of the config
     * @param config the config.
     */
    private void doAddConfig(String name, FolderConfiguration config) {
        // remove config that would have the same name to ensure no duplicate
        for (DeviceConfig c : mConfigs) {
            if (c.getName().equals(name)) {
                mConfigs.remove(c);
                break;
            }
        }
        mConfigs.add(new DeviceConfig(name, config));
    }

    /**
     * Seals the layout device by setting up {@link #mROList}.
     * <p/><strong>This must be called inside a <code>synchronized(mConfigs)</code> block.</strong>
     */
    private void seal() {
        mROList = Collections.unmodifiableList(mConfigs);
    }

    void setXDpi(float xdpi) {
        mXDpi = xdpi;
    }

    void setYDpi(float ydpi) {
        mYDpi = ydpi;
    }

    public String getName() {
        return mName;
    }

    /**
     * Returns an unmodifiable list of all the {@link DeviceConfig}.
     */
    public List<DeviceConfig> getConfigs() {
        synchronized (mConfigs) {
            return mROList;
        }
    }

    /**
     * Returns a {@link DeviceConfig} by its name.
     */
    public DeviceConfig getDeviceConfigByName(String name) {
        synchronized (mConfigs) {
            for (DeviceConfig config : mConfigs) {
                if (config.getName().equals(name)) {
                    return config;
                }
            }
        }

        return null;
    }

    /**
     * Returns a {@link FolderConfiguration} by its name.
     */
    public FolderConfiguration getFolderConfigByName(String name) {
        synchronized (mConfigs) {
            for (DeviceConfig config : mConfigs) {
                if (config.getName().equals(name)) {
                    return config.getConfig();
                }
            }
        }

        return null;
    }

    /**
     * Returns the dpi of the Device screen in X.
     * @return the dpi of screen or {@link Float#NaN} if it's not set.
     */
    public float getXDpi() {
        return mXDpi;
    }

    /**
     * Returns the dpi of the Device screen in Y.
     * @return the dpi of screen or {@link Float#NaN} if it's not set.
     */
    public float getYDpi() {
        return mYDpi;
    }
 }
