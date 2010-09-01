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

/**
 * Common IViewRule processing to all view and layout classes.
 */
public class BaseView implements IViewRule {

    /**
     * Namespace for the Android resource XML,
     * i.e. "http://schemas.android.com/apk/res/android"
     */
    public static String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    // Some common Android layout attribute names used by the view rules.
    // All these belong to the attribute namespace ANDROID_URI.
    public static String ATTR_ID = "id";
    public static String ATTR_TEXT = "text";
    public static String ATTR_LAYOUT_WIDTH = "layout_width";
    public static String ATTR_LAYOUT_HEIGHT = "layout_height";

    // Some common Android layout attribute values used by the view rules.
    public static String VALUE_FILL_PARENT = "fill_parent";
    public static String VALUE_MATCH_PARENT = "match_parent"; // like fill_parent for API 8
    public static String VALUE_WRAP_CONTENT = "wrap_content";

    // Cache of attributes. Key is FQCN of a node mixed with its view hierarchy parent.
    // Values are a custom map as needed by getContextMenu.
    public Map mAttributesMap = [:];

    public boolean onInitialize(String fqcn) {
        // This base rule can handle any class so we don't need to filter on FQCN.
        // Derived classes should do so if they can handle some subclasses.

        // For debugging and as an example of how to use the injected _rules_engine property.
        // _rules_engine.debugPrintf("Initialize() of %s", _rules_engine.getFqcn());

        // If onInitialize returns false, it means it can't handle the given FQCN and
        // will be unloaded.
        return true;
    }

    public void onDispose() {
        // Nothing to dispose.
    }

    public String getDisplayName() {
        // Default is to not override the selection display name.
        return null;
    }

    public Map<?, ?> getDefaultAttributes() {
        // The base rule does not have any custom default attributes.
        return null;
    }

    // === Context Menu ===

    /**
     * Generate custom actions for the context menu: <br/>
     * - Explicit layout_width and layout_height attributes.
     * - List of all other simple toggle attributes.
     */
    public List<MenuAction> getContextMenu(INode selectedNode) {

        // Compute the key for mAttributesMap. This depends on the type of this node and
        // its parent in the view hierarchy.
        def key = selectedNode.getFqcn() + "_";
        def parent = selectedNode.getParent();
        if (parent) key = key + parent.getFqcn();

        def custom_w = "Custom...";
        def curr_w = selectedNode.getStringAttr(ANDROID_URI, ATTR_LAYOUT_WIDTH);

        if (curr_w == VALUE_FILL_PARENT) {
            curr_w = VALUE_MATCH_PARENT;
        } else if (curr_w != VALUE_WRAP_CONTENT && curr_w != VALUE_MATCH_PARENT) {
            curr_w = "zcustom";
            if (!!curr_w) {
                custom_w = "Custom: ${curr_w}"
            }
        }

        def custom_h = "Custom...";
        def curr_h = selectedNode.getStringAttr(ANDROID_URI, ATTR_LAYOUT_HEIGHT);

        if (curr_h == VALUE_FILL_PARENT) {
            curr_h = VALUE_MATCH_PARENT;
        } else if (curr_h != VALUE_WRAP_CONTENT && curr_h != VALUE_MATCH_PARENT) {
            curr_h = "zcustom";
            if (!!curr_h) {
                custom_h = "Custom: ${curr_h}"
            }
        }

        def onChange = { MenuAction.Action action, String valueId, Boolean newValue ->
            def actionId = action.getId();
            def node = selectedNode;

            switch (actionId) {
                case "layout_1width":
                    if (!valueId.startsWith("z")) {
                        node.editXml("Change attribute " + ATTR_LAYOUT_WIDTH) {
                            node.setAttribute(ANDROID_URI, ATTR_LAYOUT_WIDTH, valueId);
                        }
                    }
                    return;
                case "layout_2height":
                    if (!valueId.startsWith("z")) {
                        node.editXml("Change attribute " + ATTR_LAYOUT_HEIGHT) {
                            node.setAttribute(ANDROID_URI, ATTR_LAYOUT_HEIGHT, valueId);
                        }
                    }
                    return;
            }

            if (actionId.startsWith("@prop@")) {
                actionId = actionId.substring(6);

                def props = mAttributesMap[key];
                def prop = props?.get(actionId);

                if (prop) {
                    node.editXml("Change attribute " + actionId) {
                        if (prop.isToggle) {
                            // case of toggle
                            String value = "";
                            switch(valueId) {
                                case "1t":
                                    value = newValue ? "true" : "";
                                    break;
                                case "2f":
                                    value = newValue ? "false" : "";
                                    break;
                            }
                            node.setAttribute(ANDROID_URI, actionId, value);
                        } else if (prop.isFlag) {
                            // case of a flag
                            def values = "";
                            if (valueId != "~2clr") {
                                values = node.getStringAttr(ANDROID_URI, actionId);
                                if (!values) {
                                    values = [] as Set;
                                } else {
                                    values = ([] as Set) + (values.split("\\|") as Set);
                                }
                                if (newValue) {
                                    values << valueId;
                                } else {
                                    values = values - valueId;
                                }
                                values = values.join("|");
                            }
                            node.setAttribute(ANDROID_URI, actionId, values);
                        } else {
                            // case of an enum
                            def value = "";
                            if (valueId != "~2clr") {
                                value = newValue ? valueId : "";
                            }
                            node.setAttribute(ANDROID_URI, actionId, value);
                        }
                    }
                }
            }
        }

        def list1 = [
             new MenuAction.Choices("layout_1width", "Layout Width",
                       [ "wrap_content": "Wrap Content",
                         "match_parent": "Match Parent",
                         "zcustom": custom_w ],
                       curr_w,
                       onChange ),
            new MenuAction.Choices("layout_2height", "Layout Height",
                       [ "wrap_content": "Wrap Content",
                         "match_parent": "Match Parent",
                         "zcustom": custom_h ],
                       curr_h,
                       onChange ),
            new MenuAction.Group("properties", "Properties"),
        ];

        // Prepare a list of all simple properties.

        def props = mAttributesMap[key];
        if (props == null) {
            // Prepare the property map
            props = [:]
            for (attrInfo in selectedNode.getDeclaredAttributes()) {
                def id = attrInfo?.getName();
                if (id == null || id == ATTR_LAYOUT_WIDTH || id == ATTR_LAYOUT_HEIGHT) {
                    // Layout width/height are already handled at the root level
                    continue;
                }
                def formats = attrInfo?.getFormats();
                if (formats == null) {
                    continue;
                }

                def title = prettyName(id);

                if (IAttributeInfo.Format.BOOLEAN in formats) {
                    props[id] = [ isToggle: true, title: title ];

                } else if (IAttributeInfo.Format.ENUM in formats) {
                    // Convert each enum into a map id=>title
                    def values = [:];
                    attrInfo.getEnumValues().each { e -> values[e] = prettyName(e) }

                    props[id]= [ isToggle: false,
                                 isFlag: false,
                                 title: title,
                                 choices: values ];

                } else if (IAttributeInfo.Format.FLAG in formats) {
                    // Convert each flag into a map id=>title
                    def values = [:];
                    attrInfo.getFlagValues().each { e -> values[e] = prettyName(e) };

                    props[id] = [ isToggle: false,
                                 isFlag: true,
                                 title: title,
                                 choices: values ];
                }
            }
            mAttributesMap[key] = props;
        }

        def list2 = [];

        props.each { id, p ->
            def a = null;
            if (p.isToggle) {
                // Toggles are handled as a multiple-choice between true, false and nothing (clear)
                def value = selectedNode.getStringAttr(ANDROID_URI, id);
                if (value != null) value = value.toLowerCase();
                switch(value) {
                    case "true":
                        value = "1t";
                        break;
                    case "false":
                        value = "2f";
                        break;
                    default:
                        value = "4clr";
                        break;
                }

                a = new MenuAction.Choices(
                            "@prop@" + id,
                            p.title,
                            [ "1t": "True",
                              "2f": "False",
                              "3sep": MenuAction.Choices.SEPARATOR,
                              "4clr": "Clear" ],
                            value,
                            "properties",
                            onChange);
            } else {
                // Enum or flags. Their possible values are the multiple-choice items,
                // with an extra "clear" option to remove everything.
                def current = selectedNode.getStringAttr(ANDROID_URI, id);
                if (!current) {
                    current = "~2clr";
                }
                a = new MenuAction.Choices(
                            "@prop@" + id,
                            p.title,
                            p.choices + [ "~1sep": MenuAction.Choices.SEPARATOR,
                                          "~2clr": "Clear " + (p.isFlag ? "flag" : "enum") ],
                            current,
                            "properties",
                            onChange);
            }
            if (a) list2.add(a);
        }

        return list1 + list2;
    }

    public String prettyName(String name) {
        if (name) {
            def c = name[0];
            name = name.replaceFirst(c, c.toUpperCase());
            name = name.replace("_", " ");
        }
        return name;
    }

    // ==== Selection ====

    public void onSelected(IGraphics gc, INode selectedNode,
                String displayName, boolean isMultipleSelection) {
        Rect r = selectedNode.getBounds();

        if (!r.isValid()) {
            return;
        }

        gc.setLineWidth(1);
        gc.setLineStyle(IGraphics.LineStyle.LINE_SOLID);
        gc.drawRect(r);

        if (displayName == null || isMultipleSelection) {
            return;
        }

        int xs = r.x + 2;
        int ys = r.y - gc.getFontHeight();
        if (ys < 0) {
            ys = r.y + r.h;
        }
        gc.drawString(displayName, xs, ys);
    }

    public void onChildSelected(IGraphics gc, INode parentNode, INode childNode) {
        Rect rp = parentNode.getBounds();
        Rect rc = childNode.getBounds();

        if (rp.isValid() && rc.isValid()) {
            gc.setLineWidth(1);
            gc.setLineStyle(IGraphics.LineStyle.LINE_DOT);

            // top line
            int m = rc.x + rc.w / 2;
            gc.drawLine(m, rc.y, m, rp.y);
            // bottom line
            gc.drawLine(m, rc.y + rc.h, m, rp.y + rp.h);
            // left line
            m = rc.y + rc.h / 2;
            gc.drawLine(rc.x, m, rp.x, m);
            // right line
            gc.drawLine(rc.x + rc.w, m, rp.x + rp.w, m);
        }
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

    public void onDropped(INode targetNode, IDragElement[] elements, DropFeedback feedback, Point p) {
        // ignore
    }

    // ==== Paste support ====

    /**
     * Most views can't accept children so there's nothing to paste on them.
     * In this case, defer the call to the parent layout and use the target node as
     * an indication of where to paste.
     */
    public void onPaste(INode targetNode, IDragElement[] elements) {
        //
        def parent = targetNode.getParent();
        def parentFqcn = parent?.getFqcn();
        def parentRule = _rules_engine.loadRule(parentFqcn);

        if (parentRule instanceof BaseLayout) {
            parentRule.onPasteBeforeChild(parent, targetNode, elements);
        }
    }

}
