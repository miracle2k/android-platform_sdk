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

import com.android.ide.eclipse.adt.editors.layout.gscripts.IViewRule;
import com.android.ide.eclipse.adt.editors.layout.gscripts.MenuAction;
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

import groovy.lang.Closure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.regex.Pattern;


/**
 * Helper class that is responsible for adding and managing the dynamic menu items
 * contributed by the {@link IViewRule} groovy instances, based on the current selection
 * on the {@link LayoutCanvas}.
 * <p/>
 * This class is tied to a specific {@link LayoutCanvas} instance and a root {@link MenuManager}.
 * <p/>
 * Two instances of this are used: one created by {@link LayoutCanvas} and the other one
 * created by {@link OutlinePage2}. Different root {@link MenuManager}s are populated, however
 * they are both linked to the current selection state of the {@link LayoutCanvas}.
 */
/* package */ class DynamicContextMenu {

    private final LayoutCanvas mCanvas;

    /** The root menu manager of the context menu. */
    private final MenuManager mMenuManager;

    /**
     * Creates a new helper responsible for adding and managing the dynamic menu items
     * contributed by the {@link IViewRule} groovy instances, based on the current selection
     * on the {@link LayoutCanvas}.
     *
     * @param canvas The {@link LayoutCanvas} providing the selection, the node factory and
     *   the rules engine.
     * @param rootMenu The root of the context menu displayed. In practice this may be the
     *   context menu manager of the {@link LayoutCanvas} or the one from {@link OutlinePage2}.
     */
    public DynamicContextMenu(LayoutCanvas canvas, MenuManager rootMenu) {
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

            final MenuAction.Action action = (MenuAction.Action) actions.get(0);

            IContributionItem contrib = null;

            if (action instanceof MenuAction.Toggle) {
                contrib = createDynamicMenuToggle((MenuAction.Toggle) action, actionsMap);

            } else if (action instanceof MenuAction.Choices) {
                Map<String, String> choiceMap = ((MenuAction.Choices) action).getChoices();
                if (choiceMap != null && !choiceMap.isEmpty()) {
                    contrib = createDynamicChoices(
                            (MenuAction.Choices)action, choiceMap, actionsMap);
                }
            }

            if (contrib != null) {
                MenuManager groupMenu = menuGroups.get(action.getGroupId());
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
     * Returns the menu actions computed by the groovy rule associated with this view.
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
     * @param action The toggle action to convert to a menu item.
     * @param actionsMap Map of all contributed actions.
     * @return a new {@link IContributionItem} to add to the context menu
     */
    private IContributionItem createDynamicMenuToggle(
            final MenuAction.Toggle action,
            final TreeMap<String, ArrayList<MenuAction>> actionsMap) {

        final RulesEngine gre = mCanvas.getRulesEngine();
        final boolean isChecked = action.isChecked();
        Action a = new Action(action.getTitle(), IAction.AS_CHECK_BOX) {
            @Override
            public void run() {
                // Invoke the closures of all the actions using the same action-id
                for (MenuAction a2 : actionsMap.get(action.getId())) {
                    if (a2 instanceof MenuAction.Action) {
                        Closure c = ((MenuAction.Action) a2).getAction();
                        if (c != null) {
                            gre.callClosure(
                                    ((MenuAction.Action) a2).getAction(),
                                    // Closure parameters are action, valueId, newValue
                                    action,
                                    null, // no valueId for a toggle
                                    !isChecked);
                        }
                    }
                }
            }
        };
        a.setId(action.getId());
        a.setChecked(isChecked);

        return new ActionContributionItem(a);
    }

    /**
     * Invoked by {@link #populateDynamicContextMenu()} to create a new menu item
     * for a {@link MenuAction.Choices}.
     * <p/>
     * Multiple-choices are represented by a sub-menu containing checked items.
     *
     * @param action The choices action to convert to a menu item.
     * @param actionsMap Map of all contributed actions.
     * @return a new {@link IContributionItem} to add to the context menu
     */
    private IContributionItem createDynamicChoices(
            final MenuAction.Choices action,
            Map<String, String> choiceMap,
            final TreeMap<String, ArrayList<MenuAction>> actionsMap) {

        final RulesEngine gre = mCanvas.getRulesEngine();
        MenuManager submenu = new MenuManager(action.getTitle(), action.getId());

        // Convert to a tree map as needed so that keys be naturally ordered.
        if (!(choiceMap instanceof TreeMap<?, ?>)) {
            choiceMap = new TreeMap<String, String>(choiceMap);
        }

        String current = action.getCurrent();
        Set<String> currents = null;
        if (current.indexOf(MenuAction.Choices.CHOICE_SEP) >= 0) {
            currents = new HashSet<String>(
                    Arrays.asList(current.split(
                            Pattern.quote(MenuAction.Choices.CHOICE_SEP))));
            current = null;
        }

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

            final boolean isChecked =
                (currents != null && currents.contains(key)) ||
                key.equals(current);

            Action a = new Action(title, IAction.AS_CHECK_BOX) {
                @Override
                public void run() {
                    // Invoke the closures of all the actions using the same action-id
                    for (MenuAction a2 : actionsMap.get(action.getId())) {
                        if (a2 instanceof MenuAction.Action) {
                            gre.callClosure(
                                    ((MenuAction.Action) a2).getAction(),
                                    // Closure parameters are action, valueId, newValue
                                    action,
                                    key,
                                    !isChecked);
                        }
                    }
                }
            };
            a.setId(String.format("%s_%s", action.getId(), key));     //$NON-NLS-1$
            a.setChecked(isChecked);
            submenu.add(a);
        }

        return submenu;
    }
}
