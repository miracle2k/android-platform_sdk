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

package com.android.ide.eclipse.adt.internal.editors.layout.gre;

import com.android.ide.eclipse.adt.editors.layout.gscripts.INode;
import com.android.ide.eclipse.adt.editors.layout.gscripts.Rect;
import com.android.ide.eclipse.adt.internal.editors.layout.descriptors.ViewElementDescriptor;
import com.android.ide.eclipse.adt.internal.editors.layout.gle2.CanvasViewInfo;
import com.android.ide.eclipse.adt.internal.editors.layout.uimodel.UiViewElementNode;
import com.android.layoutlib.api.ILayoutResult.ILayoutViewInfo;

import java.util.Arrays;

import junit.framework.TestCase;

public class NodeFactoryTest extends TestCase {

    private NodeFactory m;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        m = new NodeFactory();

    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        m = null;
    }

    public final void testCreateCanvasViewInfo() {
        ViewElementDescriptor ved = new ViewElementDescriptor("xml", "MyJavaClass");
        UiViewElementNode uiv = new UiViewElementNode(ved);
        MockLayoutViewInfo lvi = new MockLayoutViewInfo(uiv, "name", 10, 12, 110, 120);
        CanvasViewInfo cvi = new CanvasViewInfo(lvi);

        // Create a NodeProxy.
        NodeProxy proxy = m.create(cvi);

        // getNode() is our only internal implementation method.
        assertNotNull(proxy);
        assertSame(uiv, proxy.getNode());

        // Groovy scripts only see the INode interface so we want to primarily test that.
        INode inode = proxy;
        assertEquals(new Rect(10, 12, 110-10-1, 120-12-1), inode.getBounds());
        assertTrue(Arrays.equals(new INode[0], inode.getChildren()));
        assertEquals("MyJavaClass", inode.getFqcn());
        assertNull(inode.getParent());
        assertSame(inode, inode.getRoot());

    }

    public final void testCreateUiViewElementNode() {
        ViewElementDescriptor ved = new ViewElementDescriptor("xml", "MyJavaClass");
        UiViewElementNode uiv = new UiViewElementNode(ved);

        // Create a NodeProxy.
        NodeProxy proxy = m.create(uiv);

        // getNode() is our only internal implementation method.
        assertNotNull(proxy);
        assertSame(uiv, proxy.getNode());

        // Groovy scripts only see the INode interface so we want to primarily test that.
        INode inode = proxy;
        // Nodes constructed using this create() method do not have valid bounds.
        // There should be one invalid bound rectangle.
        assertNotNull(inode.getBounds());
        assertFalse(inode.getBounds().isValid());
        // All the other properties should be set correctly.
        assertTrue(Arrays.equals(new INode[0], inode.getChildren()));
        assertEquals("MyJavaClass", inode.getFqcn());
        assertNull(inode.getParent());
        assertSame(inode, inode.getRoot());
    }

    public final void testCreateDup() {
        ViewElementDescriptor ved = new ViewElementDescriptor("xml", "MyJavaClass");
        UiViewElementNode uiv = new UiViewElementNode(ved);
        MockLayoutViewInfo lvi = new MockLayoutViewInfo(uiv, "name", 10, 12, 110, 120);
        CanvasViewInfo cvi = new CanvasViewInfo(lvi);

        // NodeProxies are cached. Creating the same one twice returns the same proxy.
        NodeProxy proxy1 = m.create(cvi);
        NodeProxy proxy2 = m.create(cvi);
        assertSame(proxy2, proxy1);
    }

    public final void testClear() {
        ViewElementDescriptor ved = new ViewElementDescriptor("xml", "MyJavaClass");
        UiViewElementNode uiv = new UiViewElementNode(ved);
        MockLayoutViewInfo lvi = new MockLayoutViewInfo(uiv, "name", 10, 12, 110, 120);
        CanvasViewInfo cvi = new CanvasViewInfo(lvi);

        // NodeProxies are cached. Creating the same one twice returns the same proxy.
        NodeProxy proxy1 = m.create(cvi);
        NodeProxy proxy2 = m.create(cvi);
        assertSame(proxy2, proxy1);

        // Clearing the cache will force it create a new proxy.
        m.clear();
        NodeProxy proxy3 = m.create(cvi);
        assertNotSame(proxy1, proxy3);
    }

    private static class MockLayoutViewInfo implements ILayoutViewInfo {
        private final Object mViewKey;
        private final String mName;
        private final int mLeft;
        private final int mTop;
        private final int mRight;
        private final int mBottom;

        public MockLayoutViewInfo(
                Object viewKey,
                String name,
                int left,
                int top,
                int right,
                int bottom) {
                    mViewKey = viewKey;
                    mName = name;
                    mLeft = left;
                    mTop = top;
                    mRight = right;
                    mBottom = bottom;
        }


        public ILayoutViewInfo[] getChildren() {
            return null;
        }

        public Object getViewKey() {
            return mViewKey;
        }

        public String getName() {
            return mName;
        }

        public int getLeft() {
            return mLeft;
        }

        public int getTop() {
            return mTop;
        }

        public int getRight() {
            return mRight;
        }


        public int getBottom() {
            return mBottom;
        }
    }

}
