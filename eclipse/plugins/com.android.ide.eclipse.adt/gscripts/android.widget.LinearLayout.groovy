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

package com.android.adt.gscripts;

/**
 * An {@link IViewRule} for android.widget.LinearLayout and all its derived classes.
 */
public class AndroidWidgetLinearLayoutRule extends BaseLayout {

    // ==== Drag'n'drop support ====

    /**
     * Called when a drop operation starts, whilst the d'n'd is dragging the cursor over the
     * views. The purpose of the drop operation will be to create a new element.
     * <p/>
     * Drop targets that can't accept child views should always return null.
     * <p/>
     * Drop targets that can accept child views must return a non-empty list of drop zones,
     * customized to the actual bounds of the target.
     * The drop zones will be visually shown to the user. Once the user releases the mouse
     * in one of the drop zone, the dropAccept/dropFinish methods will be called.
     * <p/>
     * Note that at this stage, the drop operation does not offer a way to know what is going
     * to be dropped. We just know it's a view descriptor, typically from the layout palette,
     * but we don't know which view class yet.
     *
     * @param targetNode The XML view that is currently the target of the drop.
     * @return Null or an empty list if the rule rejects the drop, or a list of usable drop zones.
     */
//    public ArrayList<DropZone> dropStart(INode targetNode) {
//
//        // for testing, we're going to make 2 drop zones: top and bottom.
//        // TODO find inner elements bounds & orientation, add margings
//        def r = targetNode.getBounds();
//        DropZone d1 = new DropZone();
//        DropZone d2 = new DropZone();
//        r.h /= 2;
//        d1.bounds.set(r);
//        d2.bounds.set(r);
//        d2.bounds.y += r.h;
//
//        return [ d1, d2 ];
//    }

    /**
     * Called after the user selects to drop the given source into one of the drop zones.
     * <p/>
     * This method should use the methods from the {@link INode} to actually create the
     * new XML matching the source descriptor.
     *
     * @param sourceFqcn The FQCN of the view being dropped.
     * @param targetNode The XML view that is currently the target of the drop.
     * @param selectedZone One of the drop zones returned by {@link #dropStart(INode)}.
     */
//    public void dropFinish(
//            String sourceFqcn,
//            INode targetNode,
//            DropZone selectedZone,
//            Point where) {
//        // TODO
//    }
}
