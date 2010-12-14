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

package com.android.ide.eclipse.adt.internal.editors.layout;

import static com.android.ide.common.layout.LayoutConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.ide.common.layout.LayoutConstants.ATTR_LAYOUT_WIDTH;
import static com.android.ide.common.layout.LayoutConstants.VALUE_MATCH_PARENT;
import static com.android.ide.common.layout.LayoutConstants.VALUE_FILL_PARENT;

import com.android.layoutlib.api.IXmlPullParser;
import com.android.sdklib.SdkConstants;

import org.kxml2.io.KXmlParser;

/**
 * Modified {@link KXmlParser} that adds the methods of {@link IXmlPullParser}.
 * <p/>
 * It will return a given parser when queried for one through
 * {@link IXmlPullParser#getParser(String)} for a given name.
 *
 */
public class ContextPullParser extends KXmlParser implements IXmlPullParser {

    private final String mName;
    private final IXmlPullParser mEmbeddedParser;

    public ContextPullParser(String name, IXmlPullParser embeddedParser) {
        super();
        mName = name;
        mEmbeddedParser = embeddedParser;
    }

    // --- Layout lib API methods

    public IXmlPullParser getParser(String layoutName) {
        if (mName.equals(layoutName)) {
            return mEmbeddedParser;
        }

        return null;
    }

    public Object getViewKey() {
        return null; // never any key to return
    }

    // --- KXMLParser override

    @Override
    public String getAttributeValue(String namespace, String localName) {
        String value = super.getAttributeValue(namespace, localName);

        // on the fly convert match_parent to fill_parent for compatibility with older
        // platforms.
        if (VALUE_MATCH_PARENT.equals(value) &&
                (ATTR_LAYOUT_WIDTH.equals(localName) ||
                        ATTR_LAYOUT_HEIGHT.equals(localName)) &&
                SdkConstants.NS_RESOURCES.equals(namespace)) {
            return VALUE_FILL_PARENT;
        }

        return value;
    }
}
