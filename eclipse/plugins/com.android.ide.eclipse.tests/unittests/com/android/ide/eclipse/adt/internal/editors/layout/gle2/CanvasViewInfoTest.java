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

package com.android.ide.eclipse.adt.internal.editors.layout.gle2;

import com.android.ide.common.rendering.api.Capability;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.ide.eclipse.adt.internal.editors.descriptors.AttributeDescriptor;
import com.android.ide.eclipse.adt.internal.editors.descriptors.ElementDescriptor;
import com.android.ide.eclipse.adt.internal.editors.layout.descriptors.ViewElementDescriptor;
import com.android.ide.eclipse.adt.internal.editors.layout.uimodel.UiViewElementNode;

import org.eclipse.swt.graphics.Rectangle;

import java.util.Arrays;

import junit.framework.TestCase;

public class CanvasViewInfoTest extends TestCase {

    private static ViewElementDescriptor createDesc(String name, String fqn, boolean hasChildren) {
        if (hasChildren) {
            return new ViewElementDescriptor(name, name, fqn, "", "", new AttributeDescriptor[0],
                    new AttributeDescriptor[0], new ElementDescriptor[1], false);
        } else {
            return new ViewElementDescriptor(name, fqn);
        }
    }

    private static UiViewElementNode createNode(UiViewElementNode parent, String fqn,
            boolean hasChildren) {
        String name = fqn.substring(fqn.lastIndexOf('.') + 1);
        ViewElementDescriptor descriptor = createDesc(name, fqn, hasChildren);
        if (parent != null) {
            return (UiViewElementNode) parent.appendNewUiChild(descriptor);
        } else {
            return new UiViewElementNode(descriptor);
        }
    }

    private static UiViewElementNode createNode(String fqn, boolean hasChildren) {
        return createNode(null, fqn, hasChildren);
    }

    public void testNormalCreate() throws Exception {
        // Normal view hierarchy, no null keys anywhere

        UiViewElementNode rootNode = createNode("android.widget.LinearLayout", true);
        ViewInfo root = new ViewInfo("LinearLayout", rootNode, 10, 10, 100, 100);
        UiViewElementNode child1Node = createNode(rootNode, "android.widget.Button", false);
        ViewInfo child1 = new ViewInfo("Button", child1Node, 0, 0, 50, 20);
        UiViewElementNode child2Node = createNode(rootNode, "android.widget.Button", false);
        ViewInfo child2 = new ViewInfo("Button", child2Node, 0, 20, 70, 25);
        root.setChildren(Arrays.asList(child1, child2));

        CanvasViewInfo rootView = CanvasViewInfo.create(root);
        assertNotNull(rootView);
        assertEquals("LinearLayout", rootView.getName());
        assertEquals(new Rectangle(10, 10, 89, 89), rootView.getAbsRect());
        assertEquals(new Rectangle(10, 10, 89, 89), rootView.getSelectionRect());
        assertNull(rootView.getParent());
        assertSame(rootView.getUiViewNode(), rootNode);
        assertEquals(2, rootView.getChildren().size());
        CanvasViewInfo childView1 = rootView.getChildren().get(0);
        CanvasViewInfo childView2 = rootView.getChildren().get(1);

        assertEquals("Button", childView1.getName());
        assertSame(rootView, childView1.getParent());
        assertEquals(new Rectangle(10, 10, 49, 19), childView1.getAbsRect());
        assertEquals(new Rectangle(10, 10, 49, 19), childView1.getSelectionRect());
        assertSame(childView1.getUiViewNode(), child1Node);

        assertEquals("Button", childView2.getName());
        assertSame(rootView, childView2.getParent());
        assertEquals(new Rectangle(10, 30, 69, 4), childView2.getAbsRect());
        assertEquals(new Rectangle(10, 30, 69, 5), childView2.getSelectionRect());
        assertSame(childView2.getUiViewNode(), child2Node);
    }

    public void testShowIn() throws Exception {
        // Test rendering of "Show Included In" (included content rendered
        // within an outer content that has null keys)

        ViewInfo root = new ViewInfo("LinearLayout", null, 10, 10, 100, 100);
        ViewInfo child1 = new ViewInfo("CheckBox", null, 0, 0, 50, 20);
        UiViewElementNode child2Node = createNode("android.widget.RelativeLayout", true);
        ViewInfo child2 = new ViewInfo("RelativeLayout", child2Node, 0, 20, 70, 25);
        root.setChildren(Arrays.asList(child1, child2));
        UiViewElementNode child21Node = createNode("android.widget.Button", false);
        ViewInfo child21 = new ViewInfo("RadioButton", child21Node, 0, 20, 70, 25);
        child2.setChildren(Arrays.asList(child21));

        CanvasViewInfo rootView = CanvasViewInfo.create(root);
        assertNotNull(rootView);
        assertEquals("LinearLayout", rootView.getName());
        assertEquals(new Rectangle(10, 10, 89, 89), rootView.getAbsRect());
        assertEquals(new Rectangle(10, 10, 89, 89), rootView.getSelectionRect());
        assertNull(rootView.getParent());
        assertNull(rootView.getUiViewNode());
        assertEquals(1, rootView.getChildren().size());
        CanvasViewInfo includedView = rootView.getChildren().get(0);

        assertEquals("RelativeLayout", includedView.getName());
        assertSame(rootView, includedView.getParent());
        assertEquals(new Rectangle(10, 30, 69, 4), includedView.getAbsRect());
        assertEquals(new Rectangle(10, 30, 69, 5), includedView.getSelectionRect());
        assertSame(includedView.getUiViewNode(), child2Node);

        CanvasViewInfo grandChild = includedView.getChildren().get(0);
        assertNotNull(grandChild);
        assertEquals("RadioButton", grandChild.getName());
        assertSame(child21Node, grandChild.getUiViewNode());
        assertEquals(new Rectangle(10, 50, 69, 4), grandChild.getAbsRect());
        assertEquals(new Rectangle(10, 50, 69, 5), grandChild.getSelectionRect());
    }

    public void testIncludeTag() throws Exception {
        // Test rendering of included views on layoutlib 5+ (e.g. has <include> tag)

        UiViewElementNode rootNode = createNode("android.widget.LinearLayout", true);
        ViewInfo root = new ViewInfo("LinearLayout", rootNode, 10, 10, 100, 100);
        UiViewElementNode child1Node = createNode(rootNode, "android.widget.Button", false);
        ViewInfo child1 = new ViewInfo("CheckBox", child1Node, 0, 0, 50, 20);
        UiViewElementNode child2Node = createNode(rootNode, "include", true);
        ViewInfo child2 = new ViewInfo("RelativeLayout", child2Node, 0, 20, 70, 25);
        root.setChildren(Arrays.asList(child1, child2));
        ViewInfo child21 = new ViewInfo("RadioButton", null, 0, 20, 70, 25);
        child2.setChildren(Arrays.asList(child21));

        CanvasViewInfo rootView = CanvasViewInfo.create(root);
        assertNotNull(rootView);
        assertEquals("LinearLayout", rootView.getName());
        assertEquals(new Rectangle(10, 10, 89, 89), rootView.getAbsRect());
        assertEquals(new Rectangle(10, 10, 89, 89), rootView.getSelectionRect());
        assertNull(rootView.getParent());
        assertSame(rootNode, rootView.getUiViewNode());
        assertEquals(2, rootView.getChildren().size());

        CanvasViewInfo childView1 = rootView.getChildren().get(0);
        CanvasViewInfo includedView = rootView.getChildren().get(1);

        assertEquals("CheckBox", childView1.getName());
        assertSame(rootView, childView1.getParent());
        assertEquals(new Rectangle(10, 10, 49, 19), childView1.getAbsRect());
        assertEquals(new Rectangle(10, 10, 49, 19), childView1.getSelectionRect());
        assertSame(childView1.getUiViewNode(), child1Node);

        assertEquals("RelativeLayout", includedView.getName());
        assertSame(rootView, includedView.getParent());
        assertEquals(new Rectangle(10, 30, 69, 4), includedView.getAbsRect());
        assertEquals(new Rectangle(10, 30, 69, 5), includedView.getSelectionRect());
        assertSame(includedView.getUiViewNode(), child2Node);
        assertEquals(0, includedView.getChildren().size());
    }

    public void testNoIncludeTag() throws Exception {
        // Test rendering of included views on layoutlib 4- (e.g. no <include> tag cookie
        // in
        // view info)

        UiViewElementNode rootNode = createNode("android.widget.LinearLayout", true);
        ViewInfo root = new ViewInfo("LinearLayout", rootNode, 10, 10, 100, 100);
        UiViewElementNode child1Node = createNode(rootNode, "android.widget.Button", false);
        ViewInfo child1 = new ViewInfo("CheckBox", child1Node, 0, 0, 50, 20);
        UiViewElementNode child2Node = createNode(rootNode, "include", true);
        ViewInfo child2 = new ViewInfo("RelativeLayout", null /* layoutlib 4 */, 0, 20, 70, 25);
        root.setChildren(Arrays.asList(child1, child2));
        ViewInfo child21 = new ViewInfo("RadioButton", null, 0, 20, 70, 25);
        child2.setChildren(Arrays.asList(child21));

        CanvasViewInfo rootView = CanvasViewInfo.create(root);
        assertNotNull(rootView);
        assertEquals("LinearLayout", rootView.getName());
        assertEquals(new Rectangle(10, 10, 89, 89), rootView.getAbsRect());
        assertEquals(new Rectangle(10, 10, 89, 89), rootView.getSelectionRect());
        assertNull(rootView.getParent());
        assertSame(rootNode, rootView.getUiViewNode());
        assertEquals(2, rootView.getChildren().size());

        CanvasViewInfo childView1 = rootView.getChildren().get(0);
        CanvasViewInfo includedView = rootView.getChildren().get(1);

        assertEquals("CheckBox", childView1.getName());
        assertSame(rootView, childView1.getParent());
        assertEquals(new Rectangle(10, 10, 49, 19), childView1.getAbsRect());
        assertEquals(new Rectangle(10, 10, 49, 19), childView1.getSelectionRect());
        assertSame(childView1.getUiViewNode(), child1Node);

        assertEquals("RelativeLayout", includedView.getName());
        assertSame(rootView, includedView.getParent());
        assertEquals(new Rectangle(10, 30, 69, 4), includedView.getAbsRect());
        assertEquals(new Rectangle(10, 30, 69, 5), includedView.getSelectionRect());
        assertSame(includedView.getUiViewNode(), child2Node);
        assertEquals(0, includedView.getChildren().size());
    }

    public void testMerge() throws Exception {
        // Test rendering of MULTIPLE included views or when there is no simple match
        // between view info and ui element node children

        UiViewElementNode rootNode = createNode("android.widget.LinearLayout", true);
        ViewInfo root = new ViewInfo("LinearLayout", rootNode, 10, 10, 100, 100);
        UiViewElementNode child1Node = createNode(rootNode, "android.widget.Button", false);
        ViewInfo child1 = new ViewInfo("CheckBox", child1Node, 0, 0, 50, 20);
        UiViewElementNode multiChildNode = createNode(rootNode, "foo", true);
        ViewInfo child2 = new ViewInfo("RelativeLayout", null, 0, 20, 70, 25);
        ViewInfo child3 = new ViewInfo("AbsoluteLayout", null, 10, 40, 50, 15);
        root.setChildren(Arrays.asList(child1, child2, child3));
        ViewInfo child21 = new ViewInfo("RadioButton", null, 0, 20, 70, 25);
        child2.setChildren(Arrays.asList(child21));

        CanvasViewInfo rootView = CanvasViewInfo.create(root);
        assertNotNull(rootView);
        assertEquals("LinearLayout", rootView.getName());
        assertEquals(new Rectangle(10, 10, 89, 89), rootView.getAbsRect());
        assertEquals(new Rectangle(10, 10, 89, 89), rootView.getSelectionRect());
        assertNull(rootView.getParent());
        assertSame(rootNode, rootView.getUiViewNode());
        assertEquals(2, rootView.getChildren().size());

        CanvasViewInfo childView1 = rootView.getChildren().get(0);
        CanvasViewInfo includedView = rootView.getChildren().get(1);

        assertEquals("CheckBox", childView1.getName());
        assertSame(rootView, childView1.getParent());
        assertEquals(new Rectangle(10, 10, 49, 19), childView1.getAbsRect());
        assertEquals(new Rectangle(10, 10, 49, 19), childView1.getSelectionRect());
        assertSame(childView1.getUiViewNode(), child1Node);

        assertEquals("foo", includedView.getName());
        assertSame(rootView, includedView.getParent());
        assertEquals(new Rectangle(10, 30, 70, 5), includedView.getAbsRect());
        assertEquals(new Rectangle(10, 30, 70, 5), includedView.getSelectionRect());
        assertEquals(0, includedView.getChildren().size());
        assertSame(multiChildNode, includedView.getUiViewNode());
    }

    /**
     * Dumps out the given {@link ViewInfo} hierarchy to standard out.
     * Useful during development.
     *
     * @param graphicalEditor the editor associated with this hierarchy
     * @param root the root of the {@link ViewInfo} hierarchy
     */
    public static void dump(GraphicalEditorPart graphicalEditor, ViewInfo root) {
        System.out.println("\n\nRendering:");
        boolean supportsEmbedding = graphicalEditor.renderingSupports(Capability.EMBEDDED_LAYOUT);
        System.out.println("Supports Embedded Layout=" + supportsEmbedding);
        System.out.println("Rendering context=" + graphicalEditor.getIncludedWithin());
        dump(root, 0);

    }

    /** Helper for {@link #dump(GraphicalEditorPart, ViewInfo)} */
    private static void dump(ViewInfo info, int depth) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            sb.append("    ");
        }
        sb.append(info.getClassName());
        sb.append(" [");
        sb.append(info.getLeft());
        sb.append(",");
        sb.append(info.getTop());
        sb.append(",");
        sb.append(info.getRight());
        sb.append(",");
        sb.append(info.getBottom());
        sb.append("] ");
        Object cookie = info.getCookie();
        if (cookie instanceof UiViewElementNode) {
            sb.append(" ");
            UiViewElementNode node = (UiViewElementNode) cookie;
            sb.append("<");
            sb.append(node.getXmlNode().getNodeName());
            sb.append("> ");
        } else if (cookie != null) {
            sb.append(" cookie=" + cookie);
        }

        System.out.println(sb.toString());

        for (ViewInfo child : info.getChildren()) {
            dump(child, depth + 1);
        }
    }
}
