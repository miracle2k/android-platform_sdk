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

package com.android.ide.eclipse.adt.internal.editors.layout;

import com.android.ide.eclipse.adt.internal.editors.descriptors.ElementDescriptor;
import com.android.ide.eclipse.adt.internal.editors.layout.descriptors.LayoutDescriptors;
import com.android.ide.eclipse.adt.internal.editors.uimodel.UiElementNode;
import com.android.ide.eclipse.adt.internal.sdk.AndroidTargetData;
import com.android.ide.eclipse.adt.internal.sdk.Sdk;
import com.android.sdklib.IAndroidTarget;

import org.eclipse.core.resources.IProject;

import java.util.ArrayList;
import java.util.List;

/**
 * This class computes the new screen size in "exploded rendering" mode.
 * It goes through the whole layout tree and figures out how many embedded layouts will have
 * extra padding and compute how that will affect the screen size.
 *
 * TODO
 * - find a better class name :)
 * - move the logic for each layout to groovy scripts?
 * - support custom classes (by querying JDT for its super class and reverting to its behavior)
 */
public final class ExplodedRenderingHelper {
    /** value of the padding in pixel.
     * TODO: make a preference?
     */
    public final static int PADDING_VALUE = 10;

    private final int[] mPadding = new int[] { 0, 0 };
    private List<ElementDescriptor> mLayoutDescriptors;

    public ExplodedRenderingHelper(UiElementNode top, IProject iProject) {
        // get the layout descriptor
        IAndroidTarget target = Sdk.getCurrent().getTarget(iProject);
        AndroidTargetData data = Sdk.getCurrent().getTargetData(target);
        LayoutDescriptors descriptors = data.getLayoutDescriptors();
        mLayoutDescriptors = descriptors.getLayoutDescriptors();

        computePadding(top, mPadding);
    }

    /**
     * Returns the number of extra padding in the X axis. This doesn't return a number of pixel
     * or dip, but how many paddings are pushing the screen dimension out.
     */
    public int getWidthPadding() {
        return mPadding[0];
    }

    /**
     * Returns the number of extra padding in the Y axis. This doesn't return a number of pixel
     * or dip, but how many paddings are pushing the screen dimension out.
     */
    public int getHeightPadding() {
        return mPadding[1];
    }

    /**
     * Computes the number of padding for a given view, and fills the given array of int.
     * <p/>index 0 is X axis, index 1 is Y axis
     * @param view the view to compute
     * @param padding the result padding (index 0 is X axis, index 1 is Y axis)
     */
    private void computePadding(UiElementNode view, int[] padding) {
        String localName = view.getDescriptor().getXmlLocalName();

        // first compute for each children
        List<UiElementNode> children = view.getUiChildren();
        int count = children.size();
        if (count > 0) {
            // compute the padding for all the children.
            List<int[]> childrenPadding = new ArrayList<int[]>(count);
            for (int i = 0 ; i < count ; i++) {
                UiElementNode child = children.get(i);
                int[] p = new int[] { 0, 0 };
                childrenPadding.add(p);
                computePadding(child, p);
            }

            // now combine/compare based on the parent.
            // TODO: need a better way to do this, groovy or other.
            if (count == 1) {
                int[] p = childrenPadding.get(0);
                padding[0] = p[0];
                padding[1] = p[1];
            } else {
                if ("LinearLayout".equals(localName)) {
                    // TODO: figure out the orientation of the layout
                    combineLinearLayout(childrenPadding, padding, false);
                } else if ("TableLayout".equals(localName)) {
                    combineLinearLayout(childrenPadding, padding, false);
                } else if ("TableRow".equals(localName)) {
                    combineLinearLayout(childrenPadding, padding, true);
                } else {
                    // unknown layouts are not exploded.
                }
            }
        }

        // if the view itself is a layout, add its padding
        for (ElementDescriptor desc : mLayoutDescriptors) {
            if (localName.equals(desc.getXmlName())) {
                padding[0]++;
                padding[1]++;
                break;
            }
        }
    }

    private void combineLinearLayout(List<int[]> paddings, int[] resultPadding,
            boolean horizontal) {
        // The way the children are combined will depend on the direction.
        // For instance in a vertical layout, we add the y padding as they all add to the length
        // of the needed canvas, while we take the biggest x padding needed by the children

        // the axis in which we take the sum of the padding of the children
        int sumIndex = horizontal ? 0 : 1;
        // the axis in which we take the max of the padding of the children
        int maxIndex = horizontal ? 1 : 0;

        int max = -1;
        for (int[] p : paddings) {
            resultPadding[sumIndex] += p[sumIndex];
            if (max == -1 || max < p[maxIndex]) {
                max = p[maxIndex];
            }
        }
        resultPadding[maxIndex] = max;
    }
}
