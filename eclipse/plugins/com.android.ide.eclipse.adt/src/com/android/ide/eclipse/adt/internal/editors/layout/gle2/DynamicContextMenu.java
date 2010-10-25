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

import com.android.ide.common.api.IMenuCallback;
import com.android.ide.common.api.IViewRule;
import com.android.ide.common.api.MenuAction;
import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.internal.editors.IconFactory;
import com.android.ide.eclipse.adt.internal.editors.layout.LayoutEditor;
import com.android.ide.eclipse.adt.internal.editors.layout.gre.NodeProxy;
import com.android.ide.eclipse.adt.internal.editors.layout.gre.RulesEngine;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IContributionItem;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.regex.Pattern;


/**
 * Helper class that is responsible for adding and managing the dynamic menu items
 * contributed by the {@link IViewRule} instances, based on the current selection
 * on the {@link LayoutCanvas}.
 * <p/>
 * This class is tied to a specific {@link LayoutCanvas} instance and a root {@link MenuManager}.
 * <p/>
 * Two instances of this are used: one created by {@link LayoutCanvas} and the other one
 * created by {@link OutlinePage2}. Different root {@link MenuManager}s are populated, however
 * they are both linked to the current selection state of the {@link LayoutCanvas}.
 */
/* package */ class DynamicContextMenu {

    /** The XML layout editor that contains the canvas that uses this menu. */
    private final LayoutEditor mEditor;

    /** The layout canvas that displays this context menu. */
    private final LayoutCanvas mCanvas;

    /** The root menu manager of the context menu. */
    private final MenuManager mMenuManager;


    /**
     * Creates a new helper responsible for adding and managing the dynamic menu items
     * contributed by the {@link IViewRule} instances, based on the current selection
     * on the {@link LayoutCanvas}.
     *
     * @param canvas The {@link LayoutCanvas} providing the selection, the node factory and
     *   the rules engine.
     * @param rootMenu The root of the context menu displayed. In practice this may be the
     *   context menu manager of the {@link LayoutCanvas} or the one from {@link OutlinePage2}.
     */
    public DynamicContextMenu(LayoutEditor editor, LayoutCanvas canvas, MenuManager rootMenu) {
        mEditor = editor;
        mCanvas = canvas;
        mMenuManager = rootMenu;

        setupDynamicMenuActions();
    }

    /**
     * Setups the menu manager to receive dynamic menu contributions from the {@link IViewRule}s
     * when it's about to be shown.
     */
    private void setupDynamicMenuActions() {
        // Remember how many static actions we have. Then each time the menu is
        // shown, find dynamic contributions based on the current selection and insert
        // them at the beginning of the menu.
        final int numStaticActions = mMenuManager.getSize();
        mMenuManager.addMenuListener(new IMenuListener() {
            public void menuAboutToShow(IMenuManager manager) {

                // Remove any previous dynamic contributions to keep only the
                // default static items.
                int n = mMenuManager.getSize() - numStaticActions;
                if (n > 0) {
                    IContributionItem[] items = mMenuManager.getItems();
                    for (int i = 0; i < n; i++) {
                        mMenuManager.remove(items[i]);
                    }
                }

                // Now add all the dynamic menu actions depending on the current selection.
                populateDynamicContextMenu();
            }
        });

    }

    /**
     * This is invoked by <code>menuAboutToShow</code> on {@link #mMenuManager}.
     * All previous dynamic menu actions have been removed and this method can now insert
     * any new actions that depend on the current selection.
     */
    private void populateDynamicContextMenu() {
        // Map action-id => action object (one per selected view that defined it)
        final TreeMap<String /*id*/, ArrayList<MenuAction>> actionsMap =
            new TreeMap<String, ArrayList<MenuAction>>();

        // Map group-id => actions to place in this group.
        TreeMap<String /*id*/, MenuAction.Group> groupsMap =
            new TreeMap<String, MenuAction.Group>();

        int maxMenuSelection = collectDynamicMenuActions(actionsMap, groupsMap);

        // Now create the actual menu contributions
        String endId = mMenuManager.getItems()[0].getId();

        Separator sep = new Separator();
        sep.setId("-dyn-gle-sep");  //$NON-NLS-1$
        mMenuManager.insertBefore(endId, sep);
        endId = sep.getId();

        // First create the groups
        Map<String, MenuManager> menuGroups = new HashMap<String, MenuManager>();
        for (MenuAction.Group group : groupsMap.values()) {
            String id = group.getId();
            MenuManager submenu = new MenuManager(group.getTitle(), id);
            menuGroups.put(id, submenu);
            mMenuManager.insertBefore(endId, submenu);
            endId = id;
        }

        boolean needGroupSep = !menuGroups.isEmpty();

        // Now fill in the actions
        for (ArrayList<MenuAction> actions : actionsMap.values()) {
            // Filter actions... if we have a multiple selection, only accept actions
            // which are common to *all* the selection which actually returned at least
            // one menu action.
            if (actions == null ||
                    actions.isEmpty() ||
                    actions.size() != maxMenuSelection) {
                continue;
            }

            if (!(actions.get(0) instanceof MenuAction.Action)) {
                continue;
            }

            // Arbitrarily select the first action, as all the actions with the same id
            // should have the same constant attributes such as id and title.
            final MenuAction.Action firstAction = (MenuAction.Action) actions.get(0);

            IContributionItem contrib = null;

            if (firstAction instanceof MenuAction.Toggle) {
                contrib = createDynamicMenuToggle((MenuAction.Toggle) firstAction, actionsMap);

            } else if (firstAction instanceof MenuAction.Choices) {
                Map<String, String> choiceMap = ((MenuAction.Choices) firstAction).getChoices();
                if (choiceMap != null && !choiceMap.isEmpty()) {
                    contrib = createDynamicChoices(
                            (MenuAction.Choices)firstAction, choiceMap, actionsMap);
                }
            }

            if (contrib != null) {
                MenuManager groupMenu = menuGroups.get(firstAction.getGroupId());
                if (groupMenu != null) {
                    groupMenu.add(contrib);
                } else {
                    if (needGroupSep) {
                        needGroupSep = false;

                        sep = new Separator();
                        sep.setId("-dyn-gle-sep2");  //$NON-NLS-1$
                        mMenuManager.insertBefore(endId, sep);
                        endId = sep.getId();
                    }
                    mMenuManager.insertBefore(endId, contrib);
                }
            }
        }
    }

    /**
     * Collects all the {@link MenuAction} contributed by the {@link IViewRule} of the
     * current selection.
     * This is the first step of {@link #populateDynamicContextMenu()}.
     *
     * @param outActionsMap Map that collects all the contributed actions.
     * @param outGroupsMap Map that collects all the contributed groups (sub-menus).
     * @return The max number of selected items that contributed the same action ID.
     *   This is used later to filter on multiple selections so that we can display only
     *   actions that are common to all selected items that contributed at least one action.
     */
    private int collectDynamicMenuActions(
            final TreeMap<String, ArrayList<MenuAction>> outActionsMap,
            final TreeMap<String, MenuAction.Group> outGroupsMap) {
        int maxMenuSelection = 0;
        for (CanvasSelection selection : mCanvas.getCanvasSelections()) {
            List<MenuAction> viewActions = null;
            if (selection != null) {
                CanvasViewInfo vi = selection.getViewInfo();
                if (vi != null) {
                    viewActions = getMenuActions(vi);
                }
            }
            if (viewActions == null) {
                continue;
            }

            boolean foundAction = false;
            for (MenuAction action : viewActions) {
                if (action.getId() == null || action.getTitle() == null) {
                    // TODO Log verbose error for invalid action.
                    continue;
                }

                String id = action.getId();

                if (action instanceof MenuAction.Group) {
                    if (!outGroupsMap.containsKey(id)) {
                        outGroupsMap.put(id, (MenuAction.Group) action);
                    }
                    continue;
                }

                ArrayList<MenuAction> actions = outActionsMap.get(id);
                if (actions == null) {
                    actions = new ArrayList<MenuAction>();
                    outActionsMap.put(id, actions);
                }

                // All the actions for the same id should have be equal
                if (!actions.isEmpty()) {
                    if (!action.equals(actions.get(0))) {
                        // TODO Log verbose error for invalid type mismatch.
                        continue;
                    }
                }

                actions.add(action);
                foundAction = true;
            }

            if (foundAction) {
                maxMenuSelection++;
            }
        }
        return maxMenuSelection;
    }

    /**
     * Returns the menu actions computed by the rule associated with this view.
     */
    public List<MenuAction> getMenuActions(CanvasViewInfo vi) {
        if (vi == null) {
            return null;
        }

        NodeProxy node = mCanvas.getNodeFactory().create(vi);
        if (node == null) {
            return null;
        }

        List<MenuAction> actions = mCanvas.getRulesEngine().callGetContextMenu(node);
        if (actions == null || actions.size() == 0) {
            return null;
        }

        return actions;
    }

    /**
     * Invoked by {@link #populateDynamicContextMenu()} to create a new menu item
     * for a {@link MenuAction.Toggle}.
     * <p/>
     * Toggles are represented by a checked menu item.
     *
     * @param firstAction The toggle action to convert to a menu item. In the case of a
     *   multiple selection, this is the first of many similar actions.
     * @param actionsMap Map of all contributed actions.
     * @return a new {@link IContributionItem} to add to the context menu
     */
    private IContributionItem createDynamicMenuToggle(
            final MenuAction.Toggle firstAction,
            final TreeMap<String, ArrayList<MenuAction>> actionsMap) {

        final boolean isChecked = firstAction.isChecked();

        Action a = new Action(firstAction.getTitle(), IAction.AS_CHECK_BOX) {
            @Override
            public void run() {
                final List<MenuAction> actions = actionsMap.get(firstAction.getId());
                if (actions == null || actions.isEmpty()) {
                    return;
                }

                String label = String.format("Toggle attribute %s", actions.get(0).getTitle());
                if (actions.size() > 1) {
                    label += String.format(" (%d elements)", actions.size());
                }

                if (mEditor.isEditXmlModelPending()) {
                    // This should not be happening.
                    logError("Action '%s' failed: XML changes pending, document might be corrupt.", //$NON-NLS-1$
                             label);
                    return;
                }

                mEditor.wrapUndoEditXmlModel(label, new Runnable() {
                    public void run() {
                        // Invoke the closures of all the actions using the same action-id
                        for (MenuAction a2 : actions) {
                            if (a2 instanceof MenuAction.Action) {
                                IMenuCallback c = ((MenuAction.Action) a2).getCallback();
                                if (c != null) {
                                    try {
                                        c.action(a2, null /* no valueId for a toggle */, !isChecked);
                                    } catch (Exception e) {
                                        RulesEngine gre = mCanvas.getRulesEngine();
                                        gre.logError("XML edit operation failed: %s", e.toString());
                                    }
                                }
                            }
                        }
                    }
                });
            }
        };
        a.setId(firstAction.getId());
        a.setChecked(isChecked);

        return new ActionContributionItem(a);
    }

    /**
     * Invoked by {@link #populateDynamicContextMenu()} to create a new menu item
     * for a {@link MenuAction.Choices}.
     * <p/>
     * Multiple-choices are represented by a sub-menu containing checked items.
     *
     * @param firstAction The choices action to convert to a menu item. In the case of a
     *   multiple selection, this is the first of many similar actions.
     * @param actionsMap Map of all contributed actions.
     * @return a new {@link IContributionItem} to add to the context menu
     */
    private IContributionItem createDynamicChoices(
            final MenuAction.Choices firstAction,
            Map<String, String> choiceMap,
            final TreeMap<String, ArrayList<MenuAction>> actionsMap) {

        IconFactory factory = IconFactory.getInstance();
        MenuManager submenu = new MenuManager(firstAction.getTitle(), firstAction.getId());

        // Convert to a tree map as needed so that keys be naturally ordered.
        if (!(choiceMap instanceof TreeMap<?, ?>)) {
            choiceMap = new TreeMap<String, String>(choiceMap);
        }

        String sepPattern = Pattern.quote(MenuAction.Choices.CHOICE_SEP);

        for (Entry<String, String> entry : choiceMap.entrySet() ) {
            final String key = entry.getKey();
            String title = entry.getValue();

            if (key == null || title == null) {
                continue;
            }

            if (MenuAction.Choices.SEPARATOR.equals(title)) {
                submenu.add(new Separator());
                continue;
            }

            final List<MenuAction> actions = actionsMap.get(firstAction.getId());

            if (actions == null || actions.isEmpty()) {
                continue;
            }

            // Are all actions for this id checked, unchecked, or in a mixed state?
            int numOff = 0;
            int numOn = 0;
            for (MenuAction a2 : actions) {
                MenuAction.Choices choice = (MenuAction.Choices) a2;
                String current = choice.getCurrent();
                boolean found = false;

                if (current.indexOf(MenuAction.Choices.CHOICE_SEP) >= 0) {
                    // current choice has a separator, so it's a flag with multiple values
                    // selected. Compare keys with the split values.
                    if (current.indexOf(key) >= 0) {
                        for(String value : current.split(sepPattern)) {
                            if (key.equals(value)) {
                                found = true;
                                break;
                            }
                        }
                    }
                } else {
                    // current choice has no separator, simply compare to the key
                    found = key.equals(current);
                }

                if (found) {
                    numOn++;
                } else {
                    numOff++;
                }
            }

            // We consider the item to be checked if all actions are all checked.
            // This means a mixed item will be first toggled from off to on by all the closures.
            final boolean isChecked = numOff == 0 && numOn > 0;
            boolean isMixed = numOff > 0 && numOn > 0;

            if (isMixed) {
                title += String.format(" (%1$d/%2$d)", numOn, numOff + numOn);
            }

            Action a = new Action(title, IAction.AS_CHECK_BOX) {
                @Override
                public void run() {

                    String label =
                        String.format("Change attribute %1$s", actions.get(0).getTitle());
                    if (actions.size() > 1) {
                        label += String.format(" (%1$d elements)", actions.size());
                    }

                    if (mEditor.isEditXmlModelPending()) {
                        // This should not be happening.
                        logError("Action '%1$s' failed: XML changes pending, document might be corrupt.", //$NON-NLS-1$
                                 label);
                        return;
                    }

                    mEditor.wrapUndoEditXmlModel(label, new Runnable() {
                        public void run() {
                            // Invoke the closures of all the actions using the same action-id
                            for (MenuAction a2 : actions) {
                                if (a2 instanceof MenuAction.Action) {
                                    try {
                                        ((MenuAction.Action) a2).getCallback().action(a2, key,
                                            !isChecked);
                                    } catch (Exception e) {
                                        RulesEngine gre = mCanvas.getRulesEngine();
                                        gre.logError("XML edit operation failed: %s", e.toString());
                                    }
                                }
                            }
                        }
                    });
                }
            };
            a.setId(String.format("%1$s_%2$s", firstAction.getId(), key));          //$NON-NLS-1$
            a.setChecked(isChecked);
            if (isMixed) {
                a.setImageDescriptor(factory.getImageDescriptor("match_multiple")); //$NON-NLS-1$
            }

            submenu.add(a);
        }

        return submenu;
    }

    private void logError(String format, Object...args) {
        AdtPlugin.logAndPrintError(
                null, // exception
                mCanvas.getRulesEngine().getProject().getName(), // tag
                format, args);
    }

}
