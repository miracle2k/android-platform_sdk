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


package com.android.ide.eclipse.adt.internal.editors.descriptors;

import com.android.ide.eclipse.adt.internal.editors.layout.descriptors.ViewElementDescriptor;
import com.android.ide.eclipse.adt.internal.editors.uimodel.UiDocumentNode;
import com.android.ide.eclipse.adt.internal.editors.uimodel.UiElementNode;

import junit.framework.TestCase;

/**
 * Unit tests for DescriptorsUtils in the editors plugin
 */
public class DescriptorsUtilsTest extends TestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public void testPrettyAttributeUiName() {
        assertEquals("", DescriptorsUtils.prettyAttributeUiName(""));

        assertEquals("Max width for view",
                DescriptorsUtils.prettyAttributeUiName("maxWidthForView"));

        assertEquals("Layout width",
                DescriptorsUtils.prettyAttributeUiName("layout_width"));

        // X Y and Z are capitalized when used as single words (so "T" becomes "t")
        assertEquals("Axis X", DescriptorsUtils.prettyAttributeUiName("axisX"));
        assertEquals("Axis Y", DescriptorsUtils.prettyAttributeUiName("axisY"));
        assertEquals("Axis Z", DescriptorsUtils.prettyAttributeUiName("axisZ"));
        assertEquals("Axis t", DescriptorsUtils.prettyAttributeUiName("axisT"));

        assertEquals("The X axis", DescriptorsUtils.prettyAttributeUiName("theXAxis"));
        assertEquals("The Y axis", DescriptorsUtils.prettyAttributeUiName("theYAxis"));
        assertEquals("The Z axis", DescriptorsUtils.prettyAttributeUiName("theZAxis"));
        assertEquals("The t axis", DescriptorsUtils.prettyAttributeUiName("theTAxis"));
    }

    public void testCapitalize() {
        assertEquals("UPPER", DescriptorsUtils.capitalize("UPPER"));
        assertEquals("Lower", DescriptorsUtils.capitalize("lower"));
        assertEquals("Capital", DescriptorsUtils.capitalize("Capital"));
        assertEquals("CamelCase", DescriptorsUtils.capitalize("camelCase"));
        assertEquals("", DescriptorsUtils.capitalize(""));
    }

    public void testFormatTooltip() {
        assertEquals("", DescriptorsUtils.formatTooltip(""));

        assertEquals("\"application\"",
                DescriptorsUtils.formatTooltip(
                        "<code>application</code>"));

        assertEquals("android.content.Intent",
                DescriptorsUtils.formatTooltip(
                        "{@link android.content.Intent}"));

        assertEquals("FLAG_ACTIVITY_SINGLE_TOP",
                DescriptorsUtils.formatTooltip(
                        "{@link android.content.Intent#FLAG_ACTIVITY_SINGLE_TOP}"));

        assertEquals("activity-alias",
                DescriptorsUtils.formatTooltip(
                        "{@link \t  #AndroidManifestActivityAlias  \tactivity-alias }"));

        assertEquals("\"permission\"",
                DescriptorsUtils.formatTooltip(
                        "{@link #AndroidManifestPermission &lt;permission&gt;}"));

        assertEquals("and etc.",
                DescriptorsUtils.formatTooltip(
                        "{@link #IntentCategory <category> and etc. }"));

        assertEquals("Activity.onNewIntent()",
                DescriptorsUtils.formatTooltip(
                        "{@link android.app.Activity#onNewIntent Activity.onNewIntent()}"));
    }

    public void testFormatFormText() {
        ElementDescriptor desc = new ElementDescriptor("application");
        desc.setSdkUrl(DescriptorsUtils.MANIFEST_SDK_URL + "TagApplication");
        String docBaseUrl = "http://base";
        assertEquals("<form><li style=\"image\" value=\"image\"></li></form>", DescriptorsUtils.formatFormText("", desc, docBaseUrl));

        assertEquals("<form><li style=\"image\" value=\"image\"><a href=\"http://base/reference/android/R.styleable.html#TagApplication\">application</a></li></form>",
                DescriptorsUtils.formatFormText(
                        "<code>application</code>",
                        desc, docBaseUrl));

        assertEquals("<form><li style=\"image\" value=\"image\"><b>android.content.Intent</b></li></form>",
                DescriptorsUtils.formatFormText(
                        "{@link android.content.Intent}",
                        desc, docBaseUrl));

        assertEquals("<form><li style=\"image\" value=\"image\"><a href=\"http://base/reference/android/R.styleable.html#AndroidManifestPermission\">AndroidManifestPermission</a></li></form>",
                DescriptorsUtils.formatFormText(
                        "{@link #AndroidManifestPermission}",
                        desc, docBaseUrl));

        assertEquals("<form><li style=\"image\" value=\"image\"><a href=\"http://base/reference/android/R.styleable.html#AndroidManifestPermission\">\"permission\"</a></li></form>",
                DescriptorsUtils.formatFormText(
                        "{@link #AndroidManifestPermission &lt;permission&gt;}",
                        desc, docBaseUrl));
    }

    public void testGetFreeWidgetId() throws Exception {
        DocumentDescriptor documentDescriptor =
            new DocumentDescriptor("layout_doc", null); //$NON-NLS-1$
        UiDocumentNode model = new UiDocumentNode(documentDescriptor);
        UiElementNode uiRoot = model.getUiRoot();

        assertEquals("@+id/button1", DescriptorsUtils.getFreeWidgetId(uiRoot, "Button"));
        assertEquals("@+id/linearLayout1",
                DescriptorsUtils.getFreeWidgetId(uiRoot, "LinearLayout"));
    }

    private static ViewElementDescriptor createDesc(String name, String fqn, boolean hasChildren) {
        if (hasChildren) {
            return new ViewElementDescriptor(name, name, fqn, "", "", new AttributeDescriptor[0],
                    new AttributeDescriptor[0], new ElementDescriptor[1], false);
        } else {
            return new ViewElementDescriptor(name, fqn);
        }
    }

    public void testCanInsertChildren() throws Exception {
        assertFalse(DescriptorsUtils.canInsertChildren(createDesc("android:Button",
                "android.widget.Button", false), null));
        assertTrue(DescriptorsUtils.canInsertChildren(createDesc("android:LinearLayout",
                "android.view.LinearLayout", true), null));
        assertFalse(DescriptorsUtils.canInsertChildren(createDesc("android:ListView",
                "android.widget.ListView", true), null));
        assertFalse(DescriptorsUtils.canInsertChildren(createDesc("android:ExpandableListView",
                "android.widget.ExpandableListView", true), null));
        assertFalse(DescriptorsUtils.canInsertChildren(createDesc("android:Gallery",
                "android.widget.Gallery", true), null));
        assertFalse(DescriptorsUtils.canInsertChildren(createDesc("android:GridView",
                "android.widget.GridView", true), null));

        // This isn't the Android one (missing android: namespace prefix):
        // This test is disabled since I had to remove the namespace enforcement
        // (see namespace-related comment in canInsertChildren)
        //assertTrue(DescriptorsUtils.canInsertChildren(createDesc("mynamespace:ListView",
        //        "android.widget.ListView", true), null));

        // Custom view without known view object
        assertTrue(DescriptorsUtils.canInsertChildren(createDesc("MyView",
                "foo.bar.MyView", true), null));

        // Custom view with known view object that extends AdapterView
        Object view = new MyClassLoader().findClass("foo.bar.MyView").newInstance();
        assertFalse(DescriptorsUtils.canInsertChildren(createDesc("MyView",
                "foo.bar.MyView", true), view));
    }

    /** Test class loader which finds foo.bar.MyView extends android.widget.AdapterView */
    private static class MyClassLoader extends ClassLoader {
        public MyClassLoader() {
            super(null);
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (name.equals("foo.bar.MyView")) {
                // Simple class stub compiled by javac and dumped as bytes:
                //package foo.bar;
                //public class MyView extends android.widget.AdapterView {
                //    public MyView() {
                //        super(null);
                //    }
                //}
                byte[] classData = new byte[] {
                        -54,-2,-70,-66,0,0,0,49,0,17,10,0,3,0,13,7,0,14,7,0,15,1,0,6,60,105,110,
                        105,116,62,1,0,3,40,41,86,1,0,4,67,111,100,101,1,0,15,76,105,110,101,78,
                        117,109,98,101,114,84,97,98,108,101,1,0,18,76,111,99,97,108,86,97,114,
                        105,97,98,108,101,84,97,98,108,101,1,0,4,116,104,105,115,1,0,16,76,102,
                        111,111,47,98,97,114,47,77,121,86,105,101,119,59,1,0,10,83,111,117,114,
                        99,101,70,105,108,101,1,0,11,77,121,86,105,101,119,46,106,97,118,97,12,
                        0,4,0,16,1,0,14,102,111,111,47,98,97,114,47,77,121,86,105,101,119,1,0,
                        26,97,110,100,114,111,105,100,47,119,105,100,103,101,116,47,65,100,97,
                        112,116,101,114,86,105,101,119,1,0,28,40,76,97,110,100,114,111,105,100,
                        47,99,111,110,116,101,110,116,47,67,111,110,116,101,120,116,59,41,86,0,
                        33,0,2,0,3,0,0,0,0,0,1,0,1,0,4,0,5,0,1,0,6,0,0,0,52,0,2,0,1,0,0,0,6,42,
                        1,-73,0,1,-79,0,0,0,2,0,7,0,0,0,10,0,2,0,0,0,9,0,5,0,10,0,8,0,0,0,12,0,
                        1,0,0,0,6,0,9,0,10,0,0,0,1,0,11,0,0,0,2,0,12
                };
                return defineClass("foo.bar.MyView", classData, 0, classData.length);
            }
            if (name.equals("android.widget.AdapterView")) {
                // Simple class stub compiled by javac and dumped as bytes:
                //package android.widget;
                //public class AdapterView {
                //    public AdapterView(android.content.Context context) { }
                //}
                byte[] classData = new byte[] {
                        -54,-2,-70,-66,0,0,0,49,0,19,10,0,3,0,15,7,0,16,7,0,17,1,0,6,60,105,110,
                        105,116,62,1,0,28,40,76,97,110,100,114,111,105,100,47,99,111,110,116,101,
                        110,116,47,67,111,110,116,101,120,116,59,41,86,1,0,4,67,111,100,101,1,0,
                        15,76,105,110,101,78,117,109,98,101,114,84,97,98,108,101,1,0,18,76,111,
                        99,97,108,86,97,114,105,97,98,108,101,84,97,98,108,101,1,0,4,116,104,105,
                        115,1,0,28,76,97,110,100,114,111,105,100,47,119,105,100,103,101,116,47,
                        65,100,97,112,116,101,114,86,105,101,119,59,1,0,7,99,111,110,116,101,120,
                        116,1,0,25,76,97,110,100,114,111,105,100,47,99,111,110,116,101,110,116,
                        47,67,111,110,116,101,120,116,59,1,0,10,83,111,117,114,99,101,70,105,108,
                        101,1,0,16,65,100,97,112,116,101,114,86,105,101,119,46,106,97,118,97,12,
                        0,4,0,18,1,0,26,97,110,100,114,111,105,100,47,119,105,100,103,101,116,
                        47,65,100,97,112,116,101,114,86,105,101,119,1,0,16,106,97,118,97,47,108,
                        97,110,103,47,79,98,106,101,99,116,1,0,3,40,41,86,0,33,0,2,0,3,0,0,0,0,0,
                        1,0,1,0,4,0,5,0,1,0,6,0,0,0,57,0,1,0,2,0,0,0,5,42,-73,0,1,-79,0,0,0,2,0,
                        7,0,0,0,6,0,1,0,0,0,8,0,8,0,0,0,22,0,2,0,0,0,5,0,9,0,10,0,0,0,0,0,5,0,11,
                        0,12,0,1,0,1,0,13,0,0,0,2,0,14
                };
                return defineClass("android.widget.AdapterView", classData, 0, classData.length);
            }

            return super.findClass(name);
        }
    }

    public void testToXmlAttributeValue() throws Exception {
        assertEquals("", DescriptorsUtils.toXmlAttributeValue(""));
        assertEquals("foo", DescriptorsUtils.toXmlAttributeValue("foo"));
        assertEquals("foo<bar", DescriptorsUtils.toXmlAttributeValue("foo<bar"));

        assertEquals("&quot;", DescriptorsUtils.toXmlAttributeValue("\""));
        assertEquals("&apos;", DescriptorsUtils.toXmlAttributeValue("'"));
        assertEquals("foo&quot;b&apos;&apos;ar",
                DescriptorsUtils.toXmlAttributeValue("foo\"b''ar"));
    }

}
