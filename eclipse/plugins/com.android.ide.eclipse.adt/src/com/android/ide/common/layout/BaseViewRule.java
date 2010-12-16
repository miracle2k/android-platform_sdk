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
import static com.android.ide.common.layout.LayoutConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.ide.common.layout.LayoutConstants.ATTR_LAYOUT_WIDTH;
import static com.android.ide.common.layout.LayoutConstants.VALUE_FILL_PARENT;
import static com.android.ide.common.layout.LayoutConstants.VALUE_MATCH_PARENT;
import static com.android.ide.common.layout.LayoutConstants.VALUE_WRAP_CONTENT;

import com.android.ide.common.api.DropFeedback;
import com.android.ide.common.api.IAttributeInfo;
import com.android.ide.common.api.IClientRulesEngine;
import com.android.ide.common.api.IDragElement;
import com.android.ide.common.api.IMenuCallback;
import com.android.ide.common.api.INode;
import com.android.ide.common.api.INodeHandler;
import com.android.ide.common.api.IViewRule;
import com.android.ide.common.api.InsertType;
import com.android.ide.common.api.MenuAction;
import com.android.ide.common.api.Point;
import com.android.ide.common.api.IAttributeInfo.Format;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Common IViewRule processing to all view and layout classes.
 */
public class BaseViewRule implements IViewRule {
    private static final String ZCUSTOM = "zcustom"; //$NON-NLS-1$

    protected IClientRulesEngine mRulesEngine;

    // Cache of attributes. Key is FQCN of a node mixed with its view hierarchy
    // parent. Values are a custom map as needed by getContextMenu.
    private Map<String, Map<String, Prop>> mAttributesMap =
        new HashMap<String, Map<String, Prop>>();

    public boolean onInitialize(String fqcn, IClientRulesEngine engine) {
        this.mRulesEngine = engine;

        // This base rule can handle any class so we don't need to filter on
        // FQCN. Derived classes should do so if they can handle some
        // subclasses.

        // If onInitialize returns false, it means it can't handle the given
        // FQCN and will be unloaded.

        return true;
    }

    public void onDispose() {
        // Nothing to dispose.
    }

    public String getDisplayName() {
        // Default is to not override the selection display name.
        return null;
    }

    // === Context Menu ===

    /**
     * Generate custom actions for the context menu: <br/>
     * - Explicit layout_width and layout_height attributes.
     * - List of all other simple toggle attributes.
     */
    public List<MenuAction> getContextMenu(final INode selectedNode) {
        // Compute the key for mAttributesMap. This depends on the type of this
        // node and its parent in the view hierarchy.
        StringBuilder keySb = new StringBuilder();
        keySb.append(selectedNode.getFqcn());
        keySb.append('_');
        INode parent = selectedNode.getParent();
        if (parent != null) {
            keySb.append(parent.getFqcn());
        }
        final String key = keySb.toString();

        String custom_w = null;
        String curr_w = selectedNode.getStringAttr(ANDROID_URI, ATTR_LAYOUT_WIDTH);

        String fillParent = getFillParentValueName();
        boolean canMatchParent = supportsMatchParent();
        if (canMatchParent && VALUE_FILL_PARENT.equals(curr_w)) {
            curr_w = VALUE_MATCH_PARENT;
        } else if (!canMatchParent && VALUE_MATCH_PARENT.equals(curr_w)) {
            curr_w = VALUE_FILL_PARENT;
        } else if (!VALUE_WRAP_CONTENT.equals(curr_w) && !fillParent.equals(curr_w)) {
            custom_w = curr_w;
        }

        String custom_h = null;
        String curr_h = selectedNode.getStringAttr(ANDROID_URI, ATTR_LAYOUT_HEIGHT);

        if (canMatchParent && VALUE_FILL_PARENT.equals(curr_h)) {
            curr_h = VALUE_MATCH_PARENT;
        } else if (!canMatchParent && VALUE_MATCH_PARENT.equals(curr_h)) {
            curr_h = VALUE_FILL_PARENT;
        } else if (!VALUE_WRAP_CONTENT.equals(curr_h) && !fillParent.equals(curr_h)) {
            custom_h = curr_h;
        }
        final String customWidth = custom_w;
        final String customHeight = custom_h;

        IMenuCallback onChange = new IMenuCallback() {

            public void action(
                    final MenuAction action,
                    final String valueId,
                    final Boolean newValue) {
                String fullActionId = action.getId();
                boolean isProp = fullActionId.startsWith("@prop@"); //$NON-NLS-1$
                final String actionId = isProp ? fullActionId.substring(6) : fullActionId;
                final INode node = selectedNode;

                if (fullActionId.equals("layout_1width")) { //$NON-NLS-1$
                    final String newAttrValue = getValue(valueId, customWidth);
                    if (newAttrValue != null) {
                        node.editXml("Change attribute " + ATTR_LAYOUT_WIDTH, new INodeHandler() {
                            public void handle(INode n) {
                                n.setAttribute(ANDROID_URI, ATTR_LAYOUT_WIDTH, newAttrValue);
                            }
                        });
                    }
                    return;
                } else if (fullActionId.equals("layout_2height")) { //$NON-NLS-1$
                    // Ask the user
                    final String newAttrValue = getValue(valueId, customHeight);
                    if (newAttrValue != null) {
                        node.editXml("Change attribute " + ATTR_LAYOUT_HEIGHT, new INodeHandler() {
                            public void handle(INode n) {
                                n.setAttribute(ANDROID_URI, ATTR_LAYOUT_HEIGHT, newAttrValue);
                            }
                        });
                    }
                    return;
                }

                if (isProp) {
                    Map<String, Prop> props = mAttributesMap.get(key);
                    final Prop prop = (props != null) ? props.get(actionId) : null;

                    if (prop != null) {
                        node.editXml("Change attribute " + actionId, new INodeHandler() {
                            public void handle(INode n) {
                                if (prop.isToggle()) {
                                    // case of toggle
                                    String value = "";                  //$NON-NLS-1$
                                    if (valueId.equals("1t")) {         //$NON-NLS-1$
                                        value = newValue ? "true" : ""; //$NON-NLS-1$ //$NON-NLS-2$
                                    } else if (valueId.equals("2f")) {
                                        value = newValue ? "false" : "";//$NON-NLS-1$ //$NON-NLS-2$
                                    }
                                    n.setAttribute(ANDROID_URI, actionId, value);
                                } else if (prop.isFlag()) {
                                    // case of a flag
                                    String values = "";                 //$NON-NLS-1$
                                    if (!valueId.equals("~2clr")) {     //$NON-NLS-1$
                                        values = n.getStringAttr(ANDROID_URI, actionId);
                                        Set<String> newValues = new HashSet<String>();
                                        if (values != null) {
                                            newValues.addAll(Arrays.asList(
                                                    values.split("\\|"))); //$NON-NLS-1$
                                        }
                                        if (newValue) {
                                            newValues.add(valueId);
                                        } else {
                                            newValues.remove(valueId);
                                        }
                                        values = join('|', newValues);
                                    }
                                    n.setAttribute(ANDROID_URI, actionId, values);
                                } else {
                                    // case of an enum
                                    String value = "";                   //$NON-NLS-1$
                                    if (!valueId.equals("~2clr")) {      //$NON-NLS-1$
                                        value = newValue ? valueId : ""; //$NON-NLS-1$
                                    }
                                    n.setAttribute(ANDROID_URI, actionId, value);
                                }
                            }
                        });
                    }
                }
            }

            /**
             * Returns the value (which will ask the user if the value is the special
             * {@link #ZCUSTOM} marker
             */
            private String getValue(String valueId, String defaultValue) {
                if (valueId.equals(ZCUSTOM)) {
                    if (defaultValue == null) {
                        defaultValue = "";
                    }
                    String value = mRulesEngine.displayInput(
                            "Set custom layout attribute value (example: 50dip)",
                            defaultValue, null);
                    if (value != null && value.trim().length() > 0) {
                        return value.trim();
                    } else {
                        return null;
                    }
                }

                return valueId;
            }
        };

        List<MenuAction> list1 = Arrays.asList(new MenuAction[] {
            new MenuAction.Choices("layout_1width", "Layout Width", //$NON-NLS-1$
                    mapify(
                      VALUE_WRAP_CONTENT, "Wrap Content",
                      canMatchParent ? VALUE_MATCH_PARENT : VALUE_FILL_PARENT,
                      canMatchParent ? "Match Parent" : "Fill Parent",
                      custom_w, custom_w,
                      ZCUSTOM, "Other..."
                    ),
                    curr_w,
                    onChange ),
           new MenuAction.Choices("layout_2height", "Layout Height", //$NON-NLS-1$
                   mapify(
                      VALUE_WRAP_CONTENT, "Wrap Content",
                      canMatchParent ? VALUE_MATCH_PARENT : VALUE_FILL_PARENT,
                      canMatchParent ? "Match Parent" : "Fill Parent",
                      custom_h, custom_h,
                      ZCUSTOM, "Other..."
                   ),
                    curr_h,
                    onChange ),
           new MenuAction.Group("properties", "Properties") //$NON-NLS-1$
        });

        // Prepare a list of all simple properties.

        Map<String, Prop> props = mAttributesMap.get(key);
        if (props == null) {
            // Prepare the property map
            props = new HashMap<String, Prop>();
            for (IAttributeInfo attrInfo : selectedNode.getDeclaredAttributes()) {
                String id = attrInfo != null ? attrInfo.getName() : null;
                if (id == null || id.equals(ATTR_LAYOUT_WIDTH) || id.equals(ATTR_LAYOUT_HEIGHT)) {
                    // Layout width/height are already handled at the root level
                    continue;
                }
                Format[] formats = attrInfo != null ? attrInfo.getFormats() : null;
                if (formats == null) {
                    continue;
                }

                String title = prettyName(id);

                if (IAttributeInfo.Format.BOOLEAN.in(formats)) {
                    props.put(id, new Prop(title, true));
                } else if (IAttributeInfo.Format.ENUM.in(formats)) {
                    // Convert each enum into a map id=>title
                    Map<String, String> values = new HashMap<String, String>();
                    if (attrInfo != null) {
                        for (String e : attrInfo.getEnumValues()) {
                            values.put(e, prettyName(e));
                        }
                    }

                    props.put(id, new Prop(title, false, false, values));
                } else if (IAttributeInfo.Format.FLAG.in(formats)) {
                    // Convert each flag into a map id=>title
                    Map<String, String> values = new HashMap<String, String>();
                    if (attrInfo != null) {
                        for (String e : attrInfo.getFlagValues()) {
                            values.put(e, prettyName(e));
                        }
                    }

                    props.put(id, new Prop(title, false, true, values));
                }
            }
            mAttributesMap.put(key, props);
        }

        List<MenuAction> list2 = new ArrayList<MenuAction>();

        for (Map.Entry<String, Prop> entry : props.entrySet()) {
            String id = entry.getKey();
            Prop p = entry.getValue();
            MenuAction a = null;
            if (p.isToggle()) {
                // Toggles are handled as a multiple-choice between true, false
                // and nothing (clear)
                String value = selectedNode.getStringAttr(ANDROID_URI, id);
                if (value != null)
                    value = value.toLowerCase();
                if ("true".equals(value)) {         //$NON-NLS-1$
                    value = "1t";                   //$NON-NLS-1$
                } else if ("false".equals(value)) { //$NON-NLS-1$
                    value = "2f";                   //$NON-NLS-1$
                } else {
                    value = "4clr";                 //$NON-NLS-1$
                }

                a = new MenuAction.Choices(
                    "@prop@" + id,          //$NON-NLS-1$
                    p.getTitle(),
                    mapify(
                        "1t", "True",       //$NON-NLS-1$
                        "2f", "False",      //$NON-NLS-1$
                        "3sep", MenuAction.Choices.SEPARATOR, //$NON-NLS-1$
                        "4clr", "Default"), //$NON-NLS-1$
                    value,
                    "properties",           //$NON-NLS-1$
                    onChange);
            } else {
                // Enum or flags. Their possible values are the multiple-choice
                // items, with an extra "clear" option to remove everything.
                String current = selectedNode.getStringAttr(ANDROID_URI, id);
                if (current == null || current.length() == 0) {
                    current = "~2clr";   //$NON-NLS-1$
                }
                a = new MenuAction.Choices(
                    "@prop@" + id,       //$NON-NLS-1$
                    p.getTitle(),
                    concatenate(
                        p.getChoices(),
                        mapify(
                            "~1sep", MenuAction.Choices.SEPARATOR,  //$NON-NLS-1$
                            "~2clr", "Default"                      //$NON-NLS-1$
                        )
                    ),
                    current,
                    "properties", //$NON-NLS-1$
                    onChange);
            }
            list2.add(a);
        }

        return concatenate(list1, list2);
    }

    /**
     * Returns fill_parent or match_parent, depending on whether the minimum supported
     * platform supports match_parent or not
     *
     * @return match_parent or fill_parent depending on which is supported by the project
     */
    protected String getFillParentValueName() {
        return supportsMatchParent() ? VALUE_MATCH_PARENT : VALUE_FILL_PARENT;
    }

    /**
     * Returns true if the project supports match_parent instead of just fill_parent
     *
     * @return true if the project supports match_parent instead of just fill_parent
     */
    protected boolean supportsMatchParent() {
        // fill_parent was renamed match_parent in API level 8
        return mRulesEngine.getMinApiLevel() >= 8;
    }

    /** Join strings into a single string with the given delimiter */
    static String join(char delimiter, Collection<String> strings) {
        StringBuilder sb = new StringBuilder(100);
        for (String s : strings) {
            if (sb.length() > 0) {
                sb.append(delimiter);
            }
            sb.append(s);
        }
        return sb.toString();
    }

    // Concatenate two menu action lists. Move these utilities into MenuAction
    static List<MenuAction> concatenate(List<MenuAction> pre, List<MenuAction> post) {
        List<MenuAction> result = new ArrayList<MenuAction>(pre.size() + post.size());
        result.addAll(pre);
        result.addAll(post);
        return result;
    }

    static List<MenuAction> concatenate(List<MenuAction> pre, MenuAction post) {
        List<MenuAction> result = new ArrayList<MenuAction>(pre.size() + 1);
        result.addAll(pre);
        result.add(post);
        return result;
    }

    static Map<String, String> concatenate(Map<String, String> pre, Map<String, String> post) {
        Map<String, String> result = new HashMap<String, String>(pre.size() + post.size());
        result.putAll(pre);
        result.putAll(post);
        return result;
    }

    // Quick utility for building up maps declaratively to minimize the diffs
    static Map<String, String> mapify(String... values) {
        Map<String, String> map = new HashMap<String, String>(values.length / 2);
        for (int i = 0; i < values.length; i += 2) {
            String key = values[i];
            if (key == null) {
                continue;
            }
            String value = values[i + 1];
            map.put(key, value);
        }

        return map;
    }

    public static String prettyName(String name) {
        if (name != null && name.length() > 0) {
            name = Character.toUpperCase(name.charAt(0)) + name.substring(1).replace('_', ' ');
        }

        return name;
    }

    // ==== Selection ====

    public List<String> getSelectionHint(INode parentNode, INode childNode) {
        return null;
    }

    // ==== Drag'n'drop support ====

    // By default Views do not accept drag'n'drop.
    public DropFeedback onDropEnter(INode targetNode, IDragElement[] elements) {
        return null;
    }

    public DropFeedback onDropMove(INode targetNode, IDragElement[] elements,
            DropFeedback feedback, Point p) {
        return null;
    }

    public void onDropLeave(INode targetNode, IDragElement[] elements, DropFeedback feedback) {
        // ignore
    }

    public void onDropped(
            INode targetNode,
            IDragElement[] elements,
            DropFeedback feedback,
            Point p) {
        // ignore
    }

    // ==== Paste support ====

    /**
     * Most views can't accept children so there's nothing to paste on them. In
     * this case, defer the call to the parent layout and use the target node as
     * an indication of where to paste.
     */
    public void onPaste(INode targetNode, IDragElement[] elements) {
        //
        INode parent = targetNode.getParent();
        if (parent != null) {
            String parentFqcn = parent.getFqcn();
            IViewRule parentRule = mRulesEngine.loadRule(parentFqcn);

            if (parentRule instanceof BaseLayoutRule) {
                ((BaseLayoutRule) parentRule).onPasteBeforeChild(parent, targetNode, elements);
            }
        }
    }

    /**
     * Support class for the context menu code. Stores state about properties in
     * the context menu.
     */
    private static class Prop {
        private final boolean mToggle;
        private final boolean mFlag;
        private final String mTitle;
        private final Map<String, String> mChoices;

        public Prop(String title, boolean isToggle, boolean isFlag, Map<String, String> choices) {
            this.mTitle = title;
            this.mToggle = isToggle;
            this.mFlag = isFlag;
            this.mChoices = choices;
        }

        public Prop(String title, boolean isToggle) {
            this(title, isToggle, false, null);
        }

        private boolean isToggle() {
            return mToggle;
        }

        private boolean isFlag() {
            return mFlag;
        }

        private String getTitle() {
            return mTitle;
        }

        private Map<String, String> getChoices() {
            return mChoices;
        }
    }

    /**
     * Returns a source attribute value which points to a sample image. This is typically
     * used to provide an initial image shown on ImageButtons, etc. There is no guarantee
     * that the source pointed to by this method actually exists.
     *
     * @return a source attribute to use for sample images, never null
     */
    protected String getSampleImageSrc() {
        // For now, we point to the sample icon which is written into new Android projects
        // created in ADT. We could alternatively look into the project resources folder
        // and try to pick something else, or even return some builtin image resource
        // in the @android namespace.

        return "@drawable/icon"; //$NON-NLS-1$
    }

    public void onCreate(INode node, INode parent, InsertType insertType) {
    }

    public void onChildInserted(INode node, INode parent, InsertType insertType) {
    }
}
