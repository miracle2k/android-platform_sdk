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

package com.android.adt.gscripts;

public class BaseLayout extends BaseView {

    public boolean onInitialize(String fqcn) {
        return super.onInitialize(fqcn);
    }

    public void onDispose() {
        super.onDispose();
    }

    // ==== Utility methods used by derived layouts ====

    // TODO revisit.
    protected String[] getLayoutAttrFilter() {
        return [
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
        ];
    }

    /**
     * Draws the bounds of the given elements and all its children elements
     * in the canvas with the specified offet.
     */
    protected void drawElement(IGraphics gc, IDragElement element, int offsetX, int offsetY) {
        Rect b = element.getBounds();
        if (b.isValid()) {
            b = b.copy().offsetBy(offsetX, offsetY);
            gc.drawRect(b);
        }

        for(inner in element.getInnerElements()) {
            drawElement(gc, inner, offsetX, offsetY);
        }
    }

    /**
     * Collect all the "android:id" IDs from the dropped elements.
     *
     * When moving objects within the same canvas, that's all there is to do.
     * However if the objects are moved to a different canvas or are copied
     * then set createNewIds to true to find the existing IDs under targetNode
     * and create a map with new non-conflicting unique IDs as needed.
     *
     * Returns a map String old-id => tuple (String new-id, String fqcn)
     * where fqcn is the FQCN of the element.
     */
    protected Map getDropIdMap(INode targetNode,
                               IDragElement[] elements,
                               boolean createNewIds) {
        def idMap = [:];

        if (createNewIds) {
            collectIds(idMap, elements);
            // Need to remap ids if necessary
            idMap = remapIds(targetNode, idMap);
        }

        return idMap;
    }


    /**
     * Fills idMap with a map String id => tuple (String id, String fqcn)
     * where fqcn is the FQCN of the element (in case we want to generate
     * new IDs based on the element type.)
     *
     * @see #getDropIdMap
     */
    protected Map collectIds(Map idMap, IDragElement[] elements) {
        for (element in elements) {
            def attr = element.getAttribute(ANDROID_URI, ATTR_ID);
            if (attr != null) {
                String id = attr.getValue();
                if (id != null && id != "") {
                    idMap.put(id, [id, element.getFqcn()]);
                }
            }

            collectIds(idMap, element.getInnerElements());
        }

        return idMap;
    }

    /**
     * Used by #getDropIdMap to find new IDs in case of conflict.
     */
    protected Map remapIds(INode node, Map idMap) {
        // Visit the document to get a list of existing ids
        def existingIdMap = [:];
        collectExistingIds(node.getRoot(), existingIdMap);

        def new_map = [:];
        idMap.each() { key, value ->
            def id = normalizeId(key);

            if (!existingIdMap.containsKey(id)) {
                // Not a conflict. Use as-is.
                new_map.put(key, value);
                if (key != id) {
                    new_map.put(id, value);
                }
            } else {
                // There is a conflict. Get a new id.
                def new_id = findNewId(value[1], existingIdMap);
                value[0] = new_id;
                new_map.put(id, value);
                new_map.put(id.replaceFirst("@\\+", "@"), value);
            }
        }

        return new_map;
    }

    /**
     * Used by #remapIds to find a new ID for a conflicting element.
     */
    protected String findNewId(String fqcn, Map existingIdMap) {
        // Get the last component of the FQCN (e.g. "android.view.Button" => "Button")
        String name = fqcn[fqcn.lastIndexOf(".")+1 .. fqcn.length()-1];

        for (int i = 1; i < 1000000; i++) {
            String id = String.format("@+id/%s%02d", name, i);
            if (!existingIdMap.containsKey(id)) {
                existingIdMap.put(id, id);
                return id;
            }
        }

        // We'll never reach here.
        return null;
    }

    /**
     * Used by #getDropIdMap to find existing IDs recursively.
     */
    protected void collectExistingIds(INode root, Map existingIdMap) {
        if (root == null) {
            return;
        }

        def id = root.getStringAttr(ANDROID_URI, ATTR_ID);
        if (id != null) {
            id = normalizeId(id);

            if (!existingIdMap.containsKey(id)) {
                existingIdMap.put(id, id);
            }
        }

        for(child in root.getChildren()) {
            collectExistingIds(child, existingIdMap);
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
     * Copies all the attributes from oldElement to newNode.
     *
     * Uses the idMap to transform the value of all attributes of Format.REFERENCE,
     * If filter is non-null, it's a closure that takes for argument:
     *   String attribue-uri (namespace), String attribute-name, String attribute-value
     * The closure should return a valid replacement string.
     * The closure can return either null, false or an empty string to prevent the attribute
     * from being copied into the new node.
     */
    protected void addAttributes(INode newNode, IDragElement oldElement,
                                 Map idMap, Closure filter) {

        // A little trick here: when creating new UI widgets by dropping them from
        // the palette, we assign them a new id and then set the text attribute
        // to that id, so for example a Button will have android:text="@+id/Button01".
        // Here we detect if such an id is being remapped to a new id and if there's
        // a text attribute with exactly the same id name, we update it too.
        String oldText = null;
        String oldId = null;
        String newId = null;

        for (attr in oldElement.getAttributes()) {
            String uri = attr.getUri();
            String name = attr.getName();
            String value = attr.getValue();

            if (uri == ANDROID_URI) {
                if (name == ATTR_ID) {
                    oldId = value;
                } else if (name == ATTR_TEXT) {
                    oldText = value;
                }
            }

            def attrInfo = newNode.getAttributeInfo(uri, name);
            if (attrInfo != null) {
                def formats = attrInfo.getFormats();
                if (formats != null && IAttributeInfo.Format.REFERENCE in formats) {
                    if (idMap.containsKey(value)) {
                        value = idMap[value][0];
                    }
                }
            }

            if (filter != null) {
                value = filter(uri, name, value);
            }
            if (value != null && value != false && value != "") {
                newNode.setAttribute(uri, name, value);

                if (uri == ANDROID_URI && name == ATTR_ID && oldId != null && value != oldId) {
                    newId = value;
                }
            }
        }

        if (newId != null && oldText == oldId) {
            newNode.setAttribute(ANDROID_URI, ATTR_TEXT, newId);
        }
    }

    /**
     * Adds all the children elements of oldElement to newNode, recursively.
     * Attributes are adjusted by calling addAttributes with idMap as necessary, with
     * no closure filter.
     */
    protected void addInnerElements(INode newNode, IDragElement oldElement, Map idMap) {

        for (element in oldElement.getInnerElements()) {
            String fqcn = element.getFqcn();
            INode childNode = newNode.appendChild(fqcn);

            addAttributes(childNode, element, idMap, null /* closure */);
            addInnerElements(childNode, element, idMap);
        }
    }

}
