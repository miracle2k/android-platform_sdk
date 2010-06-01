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

package com.android.ide.eclipse.adt.internal.editors.layout;

import com.android.ide.eclipse.adt.internal.editors.descriptors.ElementDescriptor;
import com.android.ide.eclipse.adt.internal.editors.layout.descriptors.LayoutDescriptors;
import com.android.ide.eclipse.adt.internal.editors.uimodel.UiAttributeNode;
import com.android.ide.eclipse.adt.internal.editors.uimodel.UiElementNode;
import com.android.ide.eclipse.adt.internal.sdk.AndroidTargetData;
import com.android.ide.eclipse.adt.internal.sdk.Sdk;
import com.android.layoutlib.api.IXmlPullParser;
import com.android.layoutlib.api.IDensityBasedResourceValue.Density;
import com.android.layoutlib.api.ILayoutResult.ILayoutViewInfo;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkConstants;

import org.eclipse.core.resources.IProject;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xmlpull.v1.XmlPullParserException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@link IXmlPullParser} implementation on top of {@link UiElementNode}.
 * <p/>
 * It's designed to work on layout files, and will most likely not work on other resource files.
 * <p/>
 * This pull parser generates {@link ILayoutViewInfo}s which key is a {@link UiElementNode}.
 */
public final class UiElementPullParser extends BasePullParser {
    private final static String ATTR_PADDING = "padding"; //$NON-NLS-1$
    private final static Pattern sFloatPattern = Pattern.compile("(-?[0-9]+(?:\\.[0-9]+)?)(.*)"); //$NON-NLS-1$

    private final int[] sIntOut = new int[1];

    private final ArrayList<UiElementNode> mNodeStack = new ArrayList<UiElementNode>();
    private UiElementNode mRoot;
    private final boolean mExplodedRendering;
    private boolean mZeroAttributeIsPadding = false;
    private boolean mIncreaseExistingPadding = false;
    private List<ElementDescriptor> mLayoutDescriptors;
    private final int mDensityValue;
    private final float mXdpi;
    private final String mDefaultPaddingValue;

    public UiElementPullParser(UiElementNode top, boolean explodeRendering, int densityValue,
            float xdpi, IProject project) {
        super();
        mRoot = top;
        mExplodedRendering = explodeRendering;
        mDensityValue = densityValue;
        mXdpi = xdpi;
        mDefaultPaddingValue = ExplodedRenderingHelper.PADDING_VALUE + "px"; //$NON-NLS-1$
        if (mExplodedRendering) {
            // get the layout descriptor
            IAndroidTarget target = Sdk.getCurrent().getTarget(project);
            AndroidTargetData data = Sdk.getCurrent().getTargetData(target);
            LayoutDescriptors descriptors = data.getLayoutDescriptors();
            mLayoutDescriptors = descriptors.getLayoutDescriptors();
        }
        push(mRoot);
    }

    private UiElementNode getCurrentNode() {
        if (mNodeStack.size() > 0) {
            return mNodeStack.get(mNodeStack.size()-1);
        }

        return null;
    }

    private Node getAttribute(int i) {
        if (mParsingState != START_TAG) {
            throw new IndexOutOfBoundsException();
        }

        // get the current uiNode
        UiElementNode uiNode = getCurrentNode();

        // get its xml node
        Node xmlNode = uiNode.getXmlNode();

        if (xmlNode != null) {
            return xmlNode.getAttributes().item(i);
        }

        return null;
    }

    private void push(UiElementNode node) {
        mNodeStack.add(node);

        mZeroAttributeIsPadding = false;
        mIncreaseExistingPadding = false;

        if (mExplodedRendering) {
            // first get the node name
            String xml = node.getDescriptor().getXmlLocalName();
            for (ElementDescriptor descriptor : mLayoutDescriptors) {
                if (xml.equals(descriptor.getXmlLocalName())) {
                    NamedNodeMap attributes = node.getXmlNode().getAttributes();
                    Node padding = attributes.getNamedItemNS(SdkConstants.NS_RESOURCES, "padding");
                    if (padding == null) {
                        // we'll return an extra padding
                        mZeroAttributeIsPadding = true;
                    } else {
                        mIncreaseExistingPadding = true;
                    }

                    break;
                }
            }
        }
    }

    private UiElementNode pop() {
        return mNodeStack.remove(mNodeStack.size()-1);
    }

    // ------------- IXmlPullParser --------

    /**
     * {@inheritDoc}
     * <p/>
     * This implementation returns the underlying DOM node of type {@link UiElementNode}.
     * Note that the link between the GLE and the parsing code depends on this being the actual
     * type returned, so you can't just randomly change it here.
     * <p/>
     * Currently used by:
     * - private method GraphicalLayoutEditor#updateNodeWithBounds(ILayoutViewInfo).
     * - private constructor of LayoutCanvas.CanvasViewInfo.
     */
    public Object getViewKey() {
        return getCurrentNode();
    }

    // ------------- XmlPullParser --------

    public String getPositionDescription() {
        return "XML DOM element depth:" + mNodeStack.size();
    }

    /*
     * This does not seem to be called by the layoutlib, but we keep this (and maintain
     * it) just in case.
     */
    public int getAttributeCount() {
        UiElementNode node = getCurrentNode();

        if (node != null) {
            Collection<UiAttributeNode> attributes = node.getUiAttributes();
            int count = attributes.size();

            return count + (mZeroAttributeIsPadding ? 1 : 0);
        }

        return 0;
    }

    /*
     * This does not seem to be called by the layoutlib, but we keep this (and maintain
     * it) just in case.
     */
    public String getAttributeName(int i) {
        if (mZeroAttributeIsPadding) {
            if (i == 0) {
                return ATTR_PADDING;
            } else {
                i--;
            }
        }

        Node attribute = getAttribute(i);
        if (attribute != null) {
            return attribute.getLocalName();
        }

        return null;
    }

    /*
     * This does not seem to be called by the layoutlib, but we keep this (and maintain
     * it) just in case.
     */
    public String getAttributeNamespace(int i) {
        if (mZeroAttributeIsPadding) {
            if (i == 0) {
                return SdkConstants.NS_RESOURCES;
            } else {
                i--;
            }
        }

        Node attribute = getAttribute(i);
        if (attribute != null) {
            return attribute.getNamespaceURI();
        }
        return ""; //$NON-NLS-1$
    }

    /*
     * This does not seem to be called by the layoutlib, but we keep this (and maintain
     * it) just in case.
     */
    public String getAttributePrefix(int i) {
        if (mZeroAttributeIsPadding) {
            if (i == 0) {
                // figure out the prefix associated with the android namespace.
                Document doc = mRoot.getXmlDocument();
                return doc.lookupPrefix(SdkConstants.NS_RESOURCES);
            } else {
                i--;
            }
        }

        Node attribute = getAttribute(i);
        if (attribute != null) {
            return attribute.getPrefix();
        }
        return null;
    }

    /*
     * This does not seem to be called by the layoutlib, but we keep this (and maintain
     * it) just in case.
     */
    public String getAttributeValue(int i) {
        if (mZeroAttributeIsPadding) {
            if (i == 0) {
                return mDefaultPaddingValue;
            } else {
                i--;
            }
        }

        Node attribute = getAttribute(i);
        if (attribute != null) {
            String value = attribute.getNodeValue();
            if (mIncreaseExistingPadding && ATTR_PADDING.equals(attribute.getLocalName()) &&
                    SdkConstants.NS_RESOURCES.equals(attribute.getNamespaceURI())) {
                // add the padding and return the value
                return addPaddingToValue(value);
            }
            return value;
        }

        return null;
    }

    /*
     * This is the main method used by the LayoutInflater to query for attributes.
     */
    public String getAttributeValue(String namespace, String localName) {
        if (mZeroAttributeIsPadding && ATTR_PADDING.equals(localName) &&
                SdkConstants.NS_RESOURCES.equals(namespace)) {
            return mDefaultPaddingValue;
        }

        // get the current uiNode
        UiElementNode uiNode = getCurrentNode();

        // get its xml node
        Node xmlNode = uiNode.getXmlNode();

        if (xmlNode != null) {
            Node attribute = xmlNode.getAttributes().getNamedItemNS(namespace, localName);
            if (attribute != null) {
                String value = attribute.getNodeValue();
                if (mIncreaseExistingPadding && ATTR_PADDING.equals(localName) &&
                        SdkConstants.NS_RESOURCES.equals(namespace)) {
                    // add the padding and return the value
                    return addPaddingToValue(value);
                }
                return value;
            }
        }

        return null;
    }

    public int getDepth() {
        return mNodeStack.size();
    }

    public String getName() {
        if (mParsingState == START_TAG || mParsingState == END_TAG) {
            return getCurrentNode().getDescriptor().getXmlLocalName();
        }

        return null;
    }

    public String getNamespace() {
        if (mParsingState == START_TAG || mParsingState == END_TAG) {
            return getCurrentNode().getDescriptor().getNamespace();
        }

        return null;
    }

    public String getPrefix() {
        if (mParsingState == START_TAG || mParsingState == END_TAG) {
            Document doc = mRoot.getXmlDocument();
            return doc.lookupPrefix(getCurrentNode().getDescriptor().getNamespace());
        }

        return null;
    }

    public boolean isEmptyElementTag() throws XmlPullParserException {
        if (mParsingState == START_TAG) {
            return getCurrentNode().getUiChildren().size() == 0;
        }

        throw new XmlPullParserException("Call to isEmptyElementTag while not in START_TAG",
                this, null);
    }

    @Override
    public void onNextFromStartDocument() {
        onNextFromStartTag();
    }

    @Override
    public void onNextFromStartTag() {
        // get the current node, and look for text or children (children first)
        UiElementNode node = getCurrentNode();
        List<UiElementNode> children = node.getUiChildren();
        if (children.size() > 0) {
            // move to the new child, and don't change the state.
            push(children.get(0));

            // in case the current state is CURRENT_DOC, we set the proper state.
            mParsingState = START_TAG;
        } else {
            if (mParsingState == START_DOCUMENT) {
                // this handles the case where there's no node.
                mParsingState = END_DOCUMENT;
            } else {
                mParsingState = END_TAG;
            }
        }
    }

    @Override
    public void onNextFromEndTag() {
        // look for a sibling. if no sibling, go back to the parent
        UiElementNode node = getCurrentNode();
        node = node.getUiNextSibling();
        if (node != null) {
            // to go to the sibling, we need to remove the current node,
            pop();
            // and add its sibling.
            push(node);
            mParsingState = START_TAG;
        } else {
            // move back to the parent
            pop();

            // we have only one element left (mRoot), then we're done with the document.
            if (mNodeStack.size() == 1) {
                mParsingState = END_DOCUMENT;
            } else {
                mParsingState = END_TAG;
            }
        }
    }

    // ------- TypedValue stuff
    // This is adapted from com.android.layoutlib.bridge.ResourceHelper
    // (but modified to directly take the parsed value and convert it into pixel instead of
    // storing it into a TypedValue)
    // this was originally taken from platform/frameworks/base/libs/utils/ResourceTypes.cpp

    private static final class DimensionEntry {
        String name;
        int type;

        DimensionEntry(String name, int unit) {
            this.name = name;
            this.type = unit;
        }
    }

    /** {@link DimensionEntry} complex unit: Value is raw pixels. */
    public static final int COMPLEX_UNIT_PX = 0;
    /** {@link DimensionEntry} complex unit: Value is Device Independent
     *  Pixels. */
    public static final int COMPLEX_UNIT_DIP = 1;
    /** {@link DimensionEntry} complex unit: Value is a scaled pixel. */
    public static final int COMPLEX_UNIT_SP = 2;
    /** {@link DimensionEntry} complex unit: Value is in points. */
    public static final int COMPLEX_UNIT_PT = 3;
    /** {@link DimensionEntry} complex unit: Value is in inches. */
    public static final int COMPLEX_UNIT_IN = 4;
    /** {@link DimensionEntry} complex unit: Value is in millimeters. */
    public static final int COMPLEX_UNIT_MM = 5;

    private final static DimensionEntry[] sDimensions = new DimensionEntry[] {
        new DimensionEntry("px", COMPLEX_UNIT_PX),
        new DimensionEntry("dip", COMPLEX_UNIT_DIP),
        new DimensionEntry("dp", COMPLEX_UNIT_DIP),
        new DimensionEntry("sp", COMPLEX_UNIT_SP),
        new DimensionEntry("pt", COMPLEX_UNIT_PT),
        new DimensionEntry("in", COMPLEX_UNIT_IN),
        new DimensionEntry("mm", COMPLEX_UNIT_MM),
    };

    /**
     * Adds padding to an existing dimension.
     * <p/>This will resolve the attribute value (which can be px, dip, dp, sp, pt, in, mm) to
     * a pixel value, add the padding value ({@link ExplodedRenderingHelper#PADDING_VALUE}),
     * and then return a string with the new value as a px string ("42px");
     * If the conversion fails, only the special padding is returned.
     */
    private String addPaddingToValue(String s) {
        int padding = ExplodedRenderingHelper.PADDING_VALUE;
        if (stringToPixel(s)) {
            padding += sIntOut[0];
        }

        return padding + "px"; //$NON-NLS-1$
    }

    /**
     * Convert the string into a pixel value, and puts it in {@link #sIntOut}
     * @param s the dimension value from an XML attribute
     * @return true if success.
     */
    private boolean stringToPixel(String s) {
        // remove the space before and after
        s.trim();
        int len = s.length();

        if (len <= 0) {
            return false;
        }

        // check that there's no non ascii characters.
        char[] buf = s.toCharArray();
        for (int i = 0 ; i < len ; i++) {
            if (buf[i] > 255) {
                return false;
            }
        }

        // check the first character
        if (buf[0] < '0' && buf[0] > '9' && buf[0] != '.') {
            return false;
        }

        // now look for the string that is after the float...
        Matcher m = sFloatPattern.matcher(s);
        if (m.matches()) {
            String f_str = m.group(1);
            String end = m.group(2);

            float f;
            try {
                f = Float.parseFloat(f_str);
            } catch (NumberFormatException e) {
                // this shouldn't happen with the regexp above.
                return false;
            }

            if (end.length() > 0 && end.charAt(0) != ' ') {
                // We only support dimension-type values, so try to parse the unit for dimension
                DimensionEntry dimension = parseDimension(end);
                if (dimension != null) {
                    // convert the value into pixel based on the dimention type
                    // This is similar to TypedValue.applyDimension()
                    switch (dimension.type) {
                        case COMPLEX_UNIT_PX:
                            // do nothing, value is already in px
                            break;
                        case COMPLEX_UNIT_DIP:
                        case COMPLEX_UNIT_SP: // intended fall-through since we don't
                                              // adjust for font size
                            f *= (float)mDensityValue / Density.DEFAULT_DENSITY;
                            break;
                        case COMPLEX_UNIT_PT:
                            f *= mXdpi * (1.0f / 72);
                            break;
                        case COMPLEX_UNIT_IN:
                            f *= mXdpi;
                            break;
                        case COMPLEX_UNIT_MM:
                            f *= mXdpi * (1.0f / 25.4f);
                            break;
                    }

                    // store result (converted to int)
                    sIntOut[0] = (int) (f + 0.5);

                    return true;
                }
            }
        }

        return false;
    }

    private static DimensionEntry parseDimension(String str) {
        str = str.trim();

        for (DimensionEntry d : sDimensions) {
            if (d.name.equals(str)) {
                return d;
            }
        }

        return null;
    }
}
