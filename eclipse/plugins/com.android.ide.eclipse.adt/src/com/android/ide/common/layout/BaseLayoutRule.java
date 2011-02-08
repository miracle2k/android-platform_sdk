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

import static com.android.ide.common.layout.LayoutConstants.ANDROID_URI;
import static com.android.ide.common.layout.LayoutConstants.ATTR_ID;
import static com.android.ide.common.layout.LayoutConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.ide.common.layout.LayoutConstants.ATTR_LAYOUT_MARGIN;
import static com.android.ide.common.layout.LayoutConstants.ATTR_LAYOUT_MARGIN_BOTTOM;
import static com.android.ide.common.layout.LayoutConstants.ATTR_LAYOUT_MARGIN_LEFT;
import static com.android.ide.common.layout.LayoutConstants.ATTR_LAYOUT_MARGIN_RIGHT;
import static com.android.ide.common.layout.LayoutConstants.ATTR_LAYOUT_MARGIN_TOP;
import static com.android.ide.common.layout.LayoutConstants.ATTR_LAYOUT_WIDTH;
import static com.android.ide.common.layout.LayoutConstants.ATTR_TEXT;
import static com.android.ide.common.layout.LayoutConstants.VALUE_FILL_PARENT;
import static com.android.ide.common.layout.LayoutConstants.VALUE_MATCH_PARENT;
import static com.android.ide.common.layout.LayoutConstants.VALUE_WRAP_CONTENT;

import com.android.ide.common.api.DropFeedback;
import com.android.ide.common.api.IAttributeInfo;
import com.android.ide.common.api.IDragElement;
import com.android.ide.common.api.IGraphics;
import com.android.ide.common.api.IMenuCallback;
import com.android.ide.common.api.INode;
import com.android.ide.common.api.INodeHandler;
import com.android.ide.common.api.MenuAction;
import com.android.ide.common.api.Point;
import com.android.ide.common.api.Rect;
import com.android.ide.common.api.IAttributeInfo.Format;
import com.android.ide.common.api.IDragElement.IDragAttribute;
import com.android.ide.common.api.MenuAction.ChoiceProvider;
import com.android.util.Pair;

import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BaseLayoutRule extends BaseViewRule {
    private static final String ACTION_FILL_WIDTH = "_fillW";  //$NON-NLS-1$
    private static final String ACTION_FILL_HEIGHT = "_fillH"; //$NON-NLS-1$
    private static final String ACTION_MARGIN = "_margin";     //$NON-NLS-1$
    private static final URL ICON_MARGINS =
        BaseLayoutRule.class.getResource("margins.png"); //$NON-NLS-1$
    private static final URL ICON_GRAVITY =
        BaseLayoutRule.class.getResource("gravity.png"); //$NON-NLS-1$
    private static final URL ICON_FILL_WIDTH =
        BaseLayoutRule.class.getResource("fillwidth.png"); //$NON-NLS-1$
    private static final URL ICON_FILL_HEIGHT =
        BaseLayoutRule.class.getResource("fillheight.png"); //$NON-NLS-1$

    // ==== Layout Actions support ====

    // The Margin layout parameters are available for LinearLayout, FrameLayout, RelativeLayout,
    // and their subclasses.
    protected MenuAction createMarginAction(final INode parentNode,
            final List<? extends INode> children) {

        final List<? extends INode> targets = children == null || children.size() == 0 ?
                Collections.singletonList(parentNode)
                : children;
        final INode first = targets.get(0);

        IMenuCallback actionCallback = new IMenuCallback() {
            public void action(MenuAction action, final String valueId, final Boolean newValue) {
                parentNode.editXml("Change Margins", new INodeHandler() {
                    public void handle(INode n) {
                        String uri = ANDROID_URI;
                        String all = first.getStringAttr(uri, ATTR_LAYOUT_MARGIN);
                        String left = first.getStringAttr(uri, ATTR_LAYOUT_MARGIN_LEFT);
                        String right = first.getStringAttr(uri, ATTR_LAYOUT_MARGIN_RIGHT);
                        String top = first.getStringAttr(uri, ATTR_LAYOUT_MARGIN_TOP);
                        String bottom = first.getStringAttr(uri, ATTR_LAYOUT_MARGIN_BOTTOM);
                        String[] margins = mRulesEngine.displayMarginInput(all, left,
                                right, top, bottom);
                        if (margins != null) {
                            assert margins.length == 5;
                            for (INode child : targets) {
                                child.setAttribute(uri, ATTR_LAYOUT_MARGIN, margins[0]);
                                child.setAttribute(uri, ATTR_LAYOUT_MARGIN_LEFT, margins[1]);
                                child.setAttribute(uri, ATTR_LAYOUT_MARGIN_RIGHT, margins[2]);
                                child.setAttribute(uri, ATTR_LAYOUT_MARGIN_TOP, margins[3]);
                                child.setAttribute(uri, ATTR_LAYOUT_MARGIN_BOTTOM, margins[4]);
                            }
                        }
                    }
                });
            }
        };

        return MenuAction.createAction(ACTION_MARGIN, "Change Margins...", null, actionCallback,
                        ICON_MARGINS, 40);
    }

    // Both LinearLayout and RelativeLayout have a gravity (but RelativeLayout applies it
    // to the parent whereas for LinearLayout it's on the children)
    protected MenuAction createGravityAction(final List<? extends INode> targets, final
            String attributeName) {
        if (targets != null && targets.size() > 0) {
            final INode first = targets.get(0);
            ChoiceProvider provider = new ChoiceProvider() {
                public void addChoices(List<String> titles, List<URL> iconUrls,
                        List<String> ids) {
                    IAttributeInfo info = first.getAttributeInfo(ANDROID_URI, attributeName);
                    if (info != null) {
                        // Generate list of possible gravity value constants
                        assert IAttributeInfo.Format.FLAG.in(info.getFormats());
                        for (String name : info.getFlagValues()) {
                            titles.add(prettyName(name));
                            ids.add(name);
                        }
                    }
                }
            };

            return MenuAction.createChoices("_gravity", "Change Gravity", //$NON-NLS-1$
                    null,
                    new PropertyCallback(targets, "Change Gravity", ANDROID_URI,
                            attributeName),
                    provider,
                    first.getStringAttr(ANDROID_URI, attributeName), ICON_GRAVITY,
                    43);
        }

        return null;
    }

    @Override
    public void addLayoutActions(List<MenuAction> actions, final INode parentNode,
            final List<? extends INode> children) {
        super.addLayoutActions(actions, parentNode, children);

        final List<? extends INode> targets = children == null || children.size() == 0 ?
                Collections.singletonList(parentNode)
                : children;
        final INode first = targets.get(0);

        // Shared action callback
        IMenuCallback actionCallback = new IMenuCallback() {
            public void action(MenuAction action, final String valueId, final Boolean newValue) {
                final String actionId = action.getId();
                final String undoLabel;
                if (actionId.equals(ACTION_FILL_WIDTH)) {
                    undoLabel = "Change Width Fill";
                } else if (actionId.equals(ACTION_FILL_HEIGHT)) {
                    undoLabel = "Change Height Fill";
                } else {
                    return;
                }
                parentNode.editXml(undoLabel, new INodeHandler() {
                    public void handle(INode n) {
                        String attribute = actionId.equals(ACTION_FILL_WIDTH)
                                ? ATTR_LAYOUT_WIDTH : ATTR_LAYOUT_HEIGHT;
                        String value;
                        if (newValue) {
                            if (supportsMatchParent()) {
                                value = VALUE_MATCH_PARENT;
                            } else {
                                value = VALUE_FILL_PARENT;
                            }
                        } else {
                            value = VALUE_WRAP_CONTENT;
                        }
                        for (INode child : targets) {
                            child.setAttribute(ANDROID_URI, attribute, value);
                        }
                    }
                });
            }
        };

        actions.add(MenuAction.createToggle(ACTION_FILL_WIDTH, "Toggle Fill Width",
                isFilled(first, ATTR_LAYOUT_WIDTH), actionCallback, ICON_FILL_WIDTH, 10));
        actions.add(MenuAction.createToggle(ACTION_FILL_HEIGHT, "Toggle Fill Height",
                isFilled(first, ATTR_LAYOUT_HEIGHT), actionCallback, ICON_FILL_HEIGHT, 20));
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
     * This method is invoked by BaseView when onPaste() is called --
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
    protected static Map<String, Pair<String, String>> getDropIdMap(INode targetNode,
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
    protected static Map<String, Pair<String, String>> collectIds(
            Map<String, Pair<String, String>> idMap,
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
    protected static Map<String, Pair<String, String>> remapIds(INode node,
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
                new_map.put(id.replaceFirst("@\\+", "@"), value); //$NON-NLS-1$ //$NON-NLS-2$
            }
        }

        return new_map;
    }

    /**
     * Used by #remapIds to find a new ID for a conflicting element.
     */
    protected static String findNewId(String fqcn, Set<String> existingIdSet) {
        // Get the last component of the FQCN (e.g. "android.view.Button" =>
        // "Button")
        String name = fqcn.substring(fqcn.lastIndexOf('.') + 1);

        for (int i = 1; i < 1000000; i++) {
            String id = String.format("@+id/%s%02d", name, i); //$NON-NLS-1$
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
    protected static void collectExistingIds(INode root, Set<String> existingIdSet) {
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
    protected static String normalizeId(String id) {
        if (id.indexOf("@+") == -1) { //$NON-NLS-1$
            id = id.replaceFirst("@", "@+"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        return id;
    }

    /**
     * For use by {@link BaseLayoutRule#addAttributes} A filter should return a
     * valid replacement string.
     */
    protected static interface AttributeFilter {
        String replace(String attributeUri, String attributeName, String attributeValue);
    }

    private static final String[] EXCLUDED_ATTRIBUTES = new String[] {
        // from AbsoluteLayout
        "layout_x",                      //$NON-NLS-1$
        "layout_y",                      //$NON-NLS-1$

        // from RelativeLayout
        "layout_above",                  //$NON-NLS-1$
        "layout_below",                  //$NON-NLS-1$
        "layout_toLeftOf",               //$NON-NLS-1$
        "layout_toRightOf",              //$NON-NLS-1$
        "layout_alignBaseline",          //$NON-NLS-1$
        "layout_alignTop",               //$NON-NLS-1$
        "layout_alignBottom",            //$NON-NLS-1$
        "layout_alignLeft",              //$NON-NLS-1$
        "layout_alignRight",             //$NON-NLS-1$
        "layout_alignParentTop",         //$NON-NLS-1$
        "layout_alignParentBottom",      //$NON-NLS-1$
        "layout_alignParentLeft",        //$NON-NLS-1$
        "layout_alignParentRight",       //$NON-NLS-1$
        "layout_alignWithParentMissing", //$NON-NLS-1$
        "layout_centerHorizontal",       //$NON-NLS-1$
        "layout_centerInParent",         //$NON-NLS-1$
        "layout_centerVertical",         //$NON-NLS-1$
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
    protected static void addAttributes(INode newNode, IDragElement oldElement,
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

                if (uri.equals(ANDROID_URI) && name.equals(ATTR_ID) &&
                        oldId != null && !oldId.equals(value)) {
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
    protected static void addInnerElements(INode newNode, IDragElement oldElement,
            Map<String, Pair<String, String>> idMap) {

        for (IDragElement element : oldElement.getInnerElements()) {
            String fqcn = element.getFqcn();
            INode childNode = newNode.appendChild(fqcn);

            addAttributes(childNode, element, idMap, null /* filter */);
            addInnerElements(childNode, element, idMap);
        }
    }

    /**
     * Insert the given elements into the given node at the given position
     *
     * @param targetNode the node to insert into
     * @param elements the elements to insert
     * @param createNewIds if true, generate new ids when there is a conflict
     * @param initialInsertPos index among targetnode's children which to insert the
     *            children
     */
    public static void insertAt(final INode targetNode, final IDragElement[] elements,
            final boolean createNewIds, final int initialInsertPos) {

        // Collect IDs from dropped elements and remap them to new IDs
        // if this is a copy or from a different canvas.
        final Map<String, Pair<String, String>> idMap = getDropIdMap(targetNode, elements,
                createNewIds);

        targetNode.editXml("Insert Elements", new INodeHandler() {

            public void handle(INode node) {
                // Now write the new elements.
                int insertPos = initialInsertPos;
                for (IDragElement element : elements) {
                    String fqcn = element.getFqcn();

                    INode newChild = targetNode.insertChildAt(fqcn, insertPos);

                    // insertPos==-1 means to insert at the end. Otherwise
                    // increment the insertion position.
                    if (insertPos >= 0) {
                        insertPos++;
                    }

                    // Copy all the attributes, modifying them as needed.
                    addAttributes(newChild, element, idMap, DEFAULT_ATTR_FILTER);
                    addInnerElements(newChild, element, idMap);
                }
            }
        });
    }
}
