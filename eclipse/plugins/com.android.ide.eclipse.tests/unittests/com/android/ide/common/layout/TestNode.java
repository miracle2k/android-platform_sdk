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
package com.android.ide.common.layout;

import com.android.ide.common.api.IAttributeInfo;
import com.android.ide.common.api.INode;
import com.android.ide.common.api.INodeHandler;
import com.android.ide.common.api.Rect;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Test/mock implementation of {@link INode} */
public class TestNode implements INode {
    private TestNode mParent;

    private final List<TestNode> mChildren = new ArrayList<TestNode>();

    private final String mFqcn;

    private Rect mBounds = new Rect(); // Invalid bounds initially

    private Map<String, IAttribute> mAttributes = new HashMap<String, IAttribute>();

    private Map<String, IAttributeInfo> mAttributeInfos = new HashMap<String, IAttributeInfo>();

    public TestNode(String fqcn) {
        this.mFqcn = fqcn;
    }

    public TestNode bounds(Rect bounds) {
        this.mBounds = bounds;

        return this;
    }

    public TestNode id(String id) {
        return set(BaseView.ANDROID_URI, BaseView.ATTR_ID, id);
    }

    public TestNode set(String uri, String name, String value) {
        setAttribute(uri, name, value);

        return this;
    }

    public TestNode add(TestNode child) {
        mChildren.add(child);
        child.mParent = this;

        return this;
    }

    public TestNode add(TestNode... children) {
        for (TestNode child : children) {
            mChildren.add(child);
            child.mParent = this;
        }

        return this;
    }

    public static TestNode create(String fcqn) {
        return new TestNode(fcqn);
    }

    public void removeChild(int index) {
        TestNode removed = mChildren.remove(index);
        removed.mParent = null;
    }

    // ==== INODE ====

    public INode appendChild(String viewFqcn) {
        return insertChildAt(viewFqcn, mChildren.size());
    }

    public void editXml(String undoName, INodeHandler callback) {
        callback.handle(this);
    }

    public IAttributeInfo getAttributeInfo(String uri, String attrName) {
        return mAttributeInfos.get(uri + attrName);
    }

    public Rect getBounds() {
        return mBounds;
    }

    public INode[] getChildren() {
        return mChildren.toArray(new INode[mChildren.size()]);
    }

    public IAttributeInfo[] getDeclaredAttributes() {
        return mAttributeInfos.values().toArray(new IAttributeInfo[mAttributeInfos.size()]);
    }

    public String getFqcn() {
        return mFqcn;
    }

    public IAttribute[] getLiveAttributes() {
        return mAttributes.values().toArray(new IAttribute[mAttributes.size()]);
    }

    public INode getParent() {
        return mParent;
    }

    public INode getRoot() {
        TestNode curr = this;
        while (curr.mParent != null) {
            curr = curr.mParent;
        }

        return curr;
    }

    public String getStringAttr(String uri, String attrName) {
        IAttribute attr = mAttributes.get(uri + attrName);
        if (attr == null) {
            return null;
        }

        return attr.getValue();
    }

    public INode insertChildAt(String viewFqcn, int index) {
        TestNode child = new TestNode(viewFqcn);
        if (index == -1) {
            mChildren.add(child);
        } else {
            mChildren.add(index, child);
        }
        child.mParent = this;
        return child;
    }

    public boolean setAttribute(String uri, String localName, String value) {
        mAttributes.put(uri + localName, new TestAttribute(uri, localName, value));
        return true;
    }

    @Override
    public String toString() {
        return "TestNode [fqn=" + mFqcn + ", infos=" + mAttributeInfos
                + ", attributes=" + mAttributes + ", bounds=" + mBounds + "]";
    }
}