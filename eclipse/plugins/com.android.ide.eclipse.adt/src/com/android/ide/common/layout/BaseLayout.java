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

import com.android.ide.common.api.DropFeedback;
import com.android.ide.common.api.IAttributeInfo;
import com.android.ide.common.api.IClientRulesEngine;
import com.android.ide.common.api.IDragElement;
import com.android.ide.common.api.IGraphics;
import com.android.ide.common.api.INode;
import com.android.ide.common.api.MenuAction;
import com.android.ide.common.api.Point;
import com.android.ide.common.api.Rect;
import com.android.ide.common.api.IAttributeInfo.Format;
import com.android.ide.common.api.IDragElement.IDragAttribute;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BaseLayout extends BaseView {

    @Override
    public boolean onInitialize(String fqcn, IClientRulesEngine engine) {
        return super.onInitialize(fqcn, engine);
    }

    @Override
    public void onDispose() {
        super.onDispose();
    }

    @Override
    public List<MenuAction> getContextMenu(INode selectedNode) {
        return super.getContextMenu(selectedNode);
    }

    // ==== Paste support ====

    /**
     * The default behavior for pasting in a layout is to simulate a drop in the
     * top-left corner of the view.
     * <p/>
     * Note that we explicitly do not call super() here -- the BasView.onPaste
     * will call onPasteBeforeChild() instead.
     * <p/>
     * Derived layouts should override this behavior if not appropriate.
     */
    @Override
    public void onPaste(INode targetNode, IDragElement[] elements) {

        DropFeedback feedback = onDropEnter(targetNode, elements);
        if (feedback != null) {
            Point p = targetNode.getBounds().getTopLeft();
            feedback = onDropMove(targetNode, elements, feedback, p);
            if (feedback != null) {
                onDropLeave(targetNode, elements, feedback);
                onDropped(targetNode, elements, feedback, p);
            }
        }
    }

    /**
     * The default behavior for pasting in a layout with a specific child target
     * is to simulate a drop right above the top left of the given child target.
     * <p/>
     * This method is invoked by BaseView.groovy when onPaste() is called --
     * views don't generally accept children and instead use the target node as
     * a hint to paste "before" it.
     */
    public void onPasteBeforeChild(INode parentNode, INode targetNode, IDragElement[] elements) {

        DropFeedback feedback = onDropEnter(parentNode, elements);
        if (feedback != null) {
            Point parentP = parentNode.getBounds().getTopLeft();
            Point targetP = targetNode.getBounds().getTopLeft();
            if (parentP.y < targetP.y) {
                targetP.y -= 1;
            }

            feedback = onDropMove(parentNode, elements, feedback, targetP);
            if (feedback != null) {
                onDropLeave(parentNode, elements, feedback);
                onDropped(parentNode, elements, feedback, targetP);
            }
        }
    }

    // ==== Utility methods used by derived layouts ====

    /**
     * Draws the bounds of the given elements and all its children elements in
     * the canvas with the specified offset.
     */
    protected void drawElement(IGraphics gc, IDragElement element, int offsetX, int offsetY) {
        Rect b = element.getBounds();
        if (b.isValid()) {
            b = b.copy().offsetBy(offsetX, offsetY);
            gc.drawRect(b);
        }

        for (IDragElement inner : element.getInnerElements()) {
            drawElement(gc, inner, offsetX, offsetY);
        }
    }

    /**
     * Collect all the "android:id" IDs from the dropped elements. When moving
     * objects within the same canvas, that's all there is to do. However if the
     * objects are moved to a different canvas or are copied then set
     * createNewIds to true to find the existing IDs under targetNode and create
     * a map with new non-conflicting unique IDs as needed. Returns a map String
     * old-id => tuple (String new-id, String fqcn) where fqcn is the FQCN of
     * the element.
     */
    protected Map<String, Pair<String, String>> getDropIdMap(INode targetNode,
            IDragElement[] elements, boolean createNewIds) {
        Map<String, Pair<String, String>> idMap = new HashMap<String, Pair<String, String>>();

        if (createNewIds) {
            collectIds(idMap, elements);
            // Need to remap ids if necessary
            idMap = remapIds(targetNode, idMap);
        }

        return idMap;
    }

    /**
     * Fills idMap with a map String id => tuple (String id, String fqcn) where
     * fqcn is the FQCN of the element (in case we want to generate new IDs
     * based on the element type.)
     *
     * @see #getDropIdMap
     */
    protected Map<String, Pair<String, String>> collectIds(Map<String, Pair<String, String>> idMap,
            IDragElement[] elements) {
        for (IDragElement element : elements) {
            IDragAttribute attr = element.getAttribute(ANDROID_URI, ATTR_ID);
            if (attr != null) {
                String id = attr.getValue();
                if (id != null && id.length() > 0) {
                    idMap.put(id, Pair.of(id, element.getFqcn()));
                }
            }

            collectIds(idMap, element.getInnerElements());
        }

        return idMap;
    }

    /**
     * Used by #getDropIdMap to find new IDs in case of conflict.
     */
    protected Map<String, Pair<String, String>> remapIds(INode node,
            Map<String, Pair<String, String>> idMap) {
        // Visit the document to get a list of existing ids
        Set<String> existingIdSet = new HashSet<String>();
        collectExistingIds(node.getRoot(), existingIdSet);

        Map<String, Pair<String, String>> new_map = new HashMap<String, Pair<String, String>>();
        for (Map.Entry<String, Pair<String, String>> entry : idMap.entrySet()) {
            String key = entry.getKey();
            Pair<String, String> value = entry.getValue();

            String id = normalizeId(key);

            if (!existingIdSet.contains(id)) {
                // Not a conflict. Use as-is.
                new_map.put(key, value);
                if (!key.equals(id)) {
                    new_map.put(id, value);
                }
            } else {
                // There is a conflict. Get a new id.
                String new_id = findNewId(value.getSecond(), existingIdSet);
                value = Pair.of(new_id, value.getSecond());
                new_map.put(id, value);
                new_map.put(id.replaceFirst("@\\+", "@"), value);
            }
        }

        return new_map;
    }

    /**
     * Used by #remapIds to find a new ID for a conflicting element.
     */
    protected String findNewId(String fqcn, Set<String> existingIdSet) {
        // Get the last component of the FQCN (e.g. "android.view.Button" =>
        // "Button")
        String name = fqcn.substring(fqcn.lastIndexOf('.') + 1);

        for (int i = 1; i < 1000000; i++) {
            String id = String.format("@+id/%s%02d", name, i);
            if (!existingIdSet.contains(id)) {
                existingIdSet.add(id);
                return id;
            }
        }

        // We'll never reach here.
        return null;
    }

    /**
     * Used by #getDropIdMap to find existing IDs recursively.
     */
    protected void collectExistingIds(INode root, Set<String> existingIdSet) {
        if (root == null) {
            return;
        }

        String id = root.getStringAttr(ANDROID_URI, ATTR_ID);
        if (id != null) {
            id = normalizeId(id);

            if (!existingIdSet.contains(id)) {
                existingIdSet.add(id);
            }
        }

        for (INode child : root.getChildren()) {
            collectExistingIds(child, existingIdSet);
        }
    }

    /**
     * Transforms @id/name into @+id/name to treat both forms the same way.
     */
    protected String normalizeId(String id) {
        if (id.indexOf("@+") == -1) {
            id = id.replaceFirst("@", "@+");
        }
        return id;
    }

    /**
     * For use by {@link BaseLayout#addAttributes} A filter should return a
     * valid replacement string.
     */
    public static interface AttributeFilter {
        String replace(String attributeUri, String attributeName, String attributeValue);
    }

    private static final String[] EXCLUDED_ATTRIBUTES = new String[] {
        // from AbsoluteLayout
        "layout_x",
        "layout_y",

        // from RelativeLayout
        "layout_above",
        "layout_below",
        "layout_toLeftOf",
        "layout_toRightOf",
        "layout_alignBaseline",
        "layout_alignTop",
        "layout_alignBottom",
        "layout_alignLeft",
        "layout_alignRight",
        "layout_alignParentTop",
        "layout_alignParentBottom",
        "layout_alignParentLeft",
        "layout_alignParentRight",
        "layout_alignWithParentMissing",
        "layout_centerHorizontal",
        "layout_centerInParent",
        "layout_centerVertical",
    };

    /**
     * Default attribute filter used by the various layouts to filter out some properties
     * we don't want to offer.
     */
    public static final AttributeFilter DEFAULT_ATTR_FILTER = new AttributeFilter() {
        Set<String> mExcludes;

        public String replace(String uri, String name, String value) {
            if (!ANDROID_URI.equals(uri)) {
                return value;
            }

            if (mExcludes == null) {
                mExcludes = new HashSet<String>(EXCLUDED_ATTRIBUTES.length);
                mExcludes.addAll(Arrays.asList(EXCLUDED_ATTRIBUTES));
            }

            return mExcludes.contains(name) ? null : value;
        }
    };

    /**
     * Copies all the attributes from oldElement to newNode. Uses the idMap to
     * transform the value of all attributes of Format.REFERENCE. If filter is
     * non-null, it's a filter that can rewrite the attribute string.
     */
    protected void addAttributes(INode newNode, IDragElement oldElement,
            Map<String, Pair<String, String>> idMap, AttributeFilter filter) {

        // A little trick here: when creating new UI widgets by dropping them
        // from the palette, we assign them a new id and then set the text
        // attribute to that id, so for example a Button will have
        // android:text="@+id/Button01".
        // Here we detect if such an id is being remapped to a new id and if
        // there's a text attribute with exactly the same id name, we update it
        // too.
        String oldText = null;
        String oldId = null;
        String newId = null;

        for (IDragAttribute attr : oldElement.getAttributes()) {
            String uri = attr.getUri();
            String name = attr.getName();
            String value = attr.getValue();

            if (uri.equals(ANDROID_URI)) {
                if (name.equals(ATTR_ID)) {
                    oldId = value;
                } else if (name.equals(ATTR_TEXT)) {
                    oldText = value;
                }
            }

            IAttributeInfo attrInfo = newNode.getAttributeInfo(uri, name);
            if (attrInfo != null) {
                Format[] formats = attrInfo.getFormats();
                if (IAttributeInfo.Format.REFERENCE.in(formats)) {
                    if (idMap.containsKey(value)) {
                        value = idMap.get(value).getFirst();
                    }
                }
            }

            if (filter != null) {
                value = filter.replace(uri, name, value);
            }
            if (value != null && value.length() > 0) {
                newNode.setAttribute(uri, name, value);

                if (uri.equals(ANDROID_URI) && name.equals(ATTR_ID) && oldId != null && !oldId.equals(value)) {
                    newId = value;
                }
            }
        }

        if (newId != null && oldText != null && oldText.equals(oldId)) {
            newNode.setAttribute(ANDROID_URI, ATTR_TEXT, newId);
        }
    }

    /**
     * Adds all the children elements of oldElement to newNode, recursively.
     * Attributes are adjusted by calling addAttributes with idMap as necessary,
     * with no closure filter.
     */
    protected void addInnerElements(INode newNode, IDragElement oldElement,
            Map<String, Pair<String, String>> idMap) {

        for (IDragElement element : oldElement.getInnerElements()) {
            String fqcn = element.getFqcn();
            INode childNode = newNode.appendChild(fqcn);

            addAttributes(childNode, element, idMap, null /* filter */);
            addInnerElements(childNode, element, idMap);
        }
    }
}
