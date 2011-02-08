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

package com.android.ide.common.api;

import com.android.annotations.Nullable;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A menu action represents one item in the context menu displayed by the GLE canvas.
 * <p/>
 * Each action should have a reasonably unique ID. By default actions are stored using
 * the lexicographical order of the IDs.
 * Duplicated IDs will be ignored -- that is the first one found will be used.
 * <p/>
 * When the canvas has a multiple selection, only actions that are present in <em>all</em>
 * the selected nodes are shown. Moreover, for a given ID all actions must be equal, for
 * example they must have the same title and choice but not necessarily the same selection. <br/>
 * This allows the canvas to only display compatible actions that will work on all selected
 * elements.
 * <p/>
 * Actions can be grouped in sub-menus if necessary. Whether groups (sub-menus) can contain
 * other groups is implementation dependent. Currently the canvas does not support this, but
 * we may decide to change this behavior later if deemed useful.
 * <p/>
 * All actions and groups are sorted by their ID, using String's natural sorting order.
 * The only way to change this sorting is by choosing the IDs so they the result end up
 * sorted as you want it.
 * <p/>
 * The {@link MenuAction} is abstract. Users should instantiate either {@link Toggle},
 * {@link Choices} or {@link Group} instead. These classes are immutable.
 * <p>
 * <b>NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.</b>
 * </p>
 */
public abstract class MenuAction implements Comparable<MenuAction> {

    /**
     * The unique id of the action.
     * @see #getId()
     */
    private final String mId;
    /**
     * The UI-visible title of the action.
     */
    private final String mTitle;

    /** A URL pointing to an icon, or null */
    private URL mIconUrl;

    /**
     * The sorting priority of this item; actions can be sorted according to these
     */
    protected int mSortPriority;

    // Factories

    public static MenuAction createSeparator() {
        return new Separator(sNextSortPriority++);
    }

    public static MenuAction createSeparator(int sortPriority) {
        return new Separator(sortPriority);
    }


    public static MenuAction createAction(String id, String title, String groupId,
            IMenuCallback callback) {
        MenuAction.Action action = new MenuAction.Action(id, title, groupId, callback);
        action.setSortPriority(sNextSortPriority++);
        return action;
    }

    public static MenuAction createAction(String id, String title, String groupId,
            IMenuCallback callback, URL iconUrl, int sortPriority) {
        MenuAction action = new MenuAction.Action(id, title, groupId, callback);
        action.setIconUrl(iconUrl);
        action.setSortPriority(sortPriority);
        return action;
    }

    public static MenuAction createToggle(String id, String title, boolean isChecked,
            IMenuCallback callback) {
        Toggle action = new Toggle(id, title, isChecked, callback);
        action.setSortPriority(sNextSortPriority++);
        return action;
    }

    public static MenuAction createToggle(String id, String title, boolean isChecked,
            IMenuCallback callback, URL iconUrl, int sortPriority) {
        Toggle toggle = new Toggle(id, title, isChecked, callback);
        toggle.setIconUrl(iconUrl);
        toggle.setSortPriority(sortPriority);
        return toggle;
    }

    public static MenuAction createChoices(String id, String title, String groupId,
            IMenuCallback callback, List<String> titles, List<URL> iconUrls, List<String> ids,
            String current) {
        OrderedChoices action = new OrderedChoices(id, title, groupId, callback, titles, iconUrls,
                ids, current);
        action.setSortPriority(sNextSortPriority++);
        return action;
    }

    public static OrderedChoices createChoices(String id, String title, String groupId,
            IMenuCallback callback, List<String> titles, List<URL> iconUrls, List<String> ids,
            String current, URL iconUrl, int sortPriority) {
        OrderedChoices choices = new OrderedChoices(id, title, groupId, callback, titles, iconUrls,
                ids, current);
        choices.setIconUrl(iconUrl);
        choices.setSortPriority(sortPriority);
        return choices;
    }

    public static OrderedChoices createChoices(String id, String title, String groupId,
            IMenuCallback callback, ChoiceProvider provider,
            String current, URL iconUrl, int sortPriority) {
        OrderedChoices choices = new DelayedOrderedChoices(id, title, groupId, callback,
                current, provider);
        choices.setIconUrl(iconUrl);
        choices.setSortPriority(sortPriority);
        return choices;
    }

    /**
     * Creates a new {@link MenuAction} with the given id and the given title.
     * Actions which have the same id and the same title are deemed equivalent.
     *
     * @param id The unique id of the action, which must be similar for all actions that
     *           perform the same task. Cannot be null.
     * @param title The UI-visible title of the action.
     */
    private MenuAction(String id, String title) {
        mId = id;
        mTitle = title;
    }

    /**
     * Returns the unique id of the action. In the context of a multiple selection,
     * actions which have the same id are collapsed together and must represent the same
     * action. Cannot be null.
     */
    public String getId() {
        return mId;
    }

    /**
     * Returns the UI-visible title of the action, shown in the context menu.
     * Cannot be null.
     */
    public String getTitle() {
        return mTitle;
    }

    /**
     * Actions which have the same id and the same title are deemed equivalent.
     */
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof MenuAction) {
            MenuAction rhs = (MenuAction) obj;

            if (mId != rhs.mId && !(mId != null && mId.equals(rhs.mId))) return false;
            if (mTitle != rhs.mTitle &&
                    !(mTitle != null && mTitle.equals(rhs.mTitle))) return false;
            return true;
        }
        return false;
    }

    /**
     * Actions which have the same id and the same title have the same hash code.
     */
    @Override
    public int hashCode() {
        int h = mId == null ? 0 : mId.hashCode();
        h = h ^ (mTitle == null ? 0 : mTitle.hashCode());
        return h;
    }

    /**
     * Gets a URL pointing to an icon to use for this action, if any.
     *
     * @return a URL pointing to an icon to use for this action, or null
     */
    public URL getIconUrl() {
        return mIconUrl;
    }

    /**
     * Sets a URL pointing to an icon to use for this action, if any.
     *
     * @param iconUrl a URL pointing to an icon to use for this action, or null
     */
    public void setIconUrl(URL iconUrl) {
        mIconUrl = iconUrl;
    }

    /**
     * Sets a priority used for sorting this action
     *
     * @param sortPriority a priority used for sorting this action
     */
    public void setSortPriority(int sortPriority) {
        mSortPriority = sortPriority;
    }

    private static int sNextSortPriority = 0;

    /**
     * Return a priority used for sorting this action
     *
     * @return a priority used for sorting this action
     */
    public int getSortPriority() {
        return mSortPriority;
    }

    // Implements Comparable<MenuAciton>
    public int compareTo(MenuAction other) {
        if (mSortPriority != other.mSortPriority) {
            return mSortPriority - other.mSortPriority;
        }

        return mTitle.compareTo(other.mTitle);
    }

    /**
     * A group of actions, displayed in a sub-menu.
     * <p/>
     * Note that group can be seen as a "group declaration": the group does not hold a list
     * actions that it will contain. This merely let the canvas create a sub-menu with the
     * given title and actions that define this group-id will be placed in the sub-menu.
     * <p/>
     * The current canvas has the following implementation details: <br/>
     * - There's only one level of sub-menu.
     *   That is you can't have a sub-menu inside another sub-menu.
     *   This is expressed by the fact that groups do not have a parent group-id. <br/>
     * - It is not currently necessary to define a group before defining actions that refer
     *   to that group. Moreover, in the context of a multiple selection, one view could
     *   contribute actions to any group even if created by another view. Both practices
     *   are discouraged. <br/>
     * - Actions which group-id do not match any known group will simply be placed in the
     *   root context menu. <br/>
     * - Empty groups do not create visible sub-menus. <br/>
     * These implementations details may change in the future and should not be relied upon.
     */
    public static class Group extends MenuAction {

        /**
         * Constructs a new group of actions.
         *
         * @param id The id of the group. Must be unique. Cannot be null.
         * @param title The UI-visible title of the group, shown in the sub-menu.
         */
        public Group(String id, String title) {
            super(id, title);
        }
    }

    /**
     * The base class for {@link Toggle} and {@link Choices}.
     */
    public static class Action extends MenuAction {

        /**
         * A callback executed when the action is selected in the context menu.
         */
        private final IMenuCallback mCallback;

        /**
         * An optional group id, to place the action in a given sub-menu.
         * @null This value can be null.
         */
        @Nullable
        private final String mGroupId;

        /**
         * Constructs a new base {@link MenuAction} with its ID, title and action callback.
         *
         * @param id The unique ID of the action. Must not be null.
         * @param title The title of the action. Must not be null.
         * @param groupId The optional group id, to place the action in a given sub-menu.
         *                Can be null.
         * @param callback The callback executed when the action is selected.
         *            Must not be null.
         */
        public Action(String id, String title, String groupId, IMenuCallback callback) {
            super(id, title);
            mGroupId = groupId;
            mCallback = callback;
        }

        /**
         * Returns the callback executed when the action is selected in the
         * context menu. Cannot be null.
         */
        public IMenuCallback getCallback() {
            return mCallback;
        }

        /**
         * Returns the optional id of an existing group or null
         * @null This value can be null.
         */
        @Nullable
        public String getGroupId() {
            return mGroupId;
        }

        /**
         * Two actions are equal if the have the same id, title and group-id.
         */
        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Action && super.equals(obj)) {
                Action rhs = (Action) obj;
                return mGroupId == rhs.mGroupId ||
                       (mGroupId != null && mGroupId.equals(rhs.mGroupId));
            }
            return false;
        }

        /**
         * Two actions have the same hash code if the have the same id, title and group-id.
         */
        @Override
        public int hashCode() {
            int h = super.hashCode();
            h = h ^ (mGroupId == null ? 0 : mGroupId.hashCode());
            return h;
        }
    }

    /** A separator to display between actions */
    public static class Separator extends MenuAction {
        /** Construct using the factory {@link #createSeparator(int)} */
        private Separator(int sortPriority) {
            super("_separator", ""); //$NON-NLS-1$ //$NON-NLS-2$
            mSortPriority = sortPriority;
        }
    }

    /**
     * A toggle is a simple on/off action, displayed as an item in a context menu
     * with a check mark if the item is checked.
     * <p/>
     * Two toggles are equal if they have the same id, title and group-id.
     * It is expected for the checked state and action callback to be different.
     */
    public static class Toggle extends Action {
        /**
         * True if the item is displayed with a check mark.
         */
        private final boolean mIsChecked;

        /**
         * Creates a new immutable toggle action.
         * This action has no group-id and will show up in the root of the context menu.
         *
         * @param id The unique id of the action. Cannot be null.
         * @param title The UI-visible title of the context menu item. Cannot be null.
         * @param isChecked Whether the context menu item has a check mark.
         * @param callback A callback to execute when the context menu item is
         *            selected.
         */
        public Toggle(String id, String title, boolean isChecked, IMenuCallback callback) {
            this(id, title, isChecked, null /* group-id */, callback);
        }

        /**
         * Creates a new immutable toggle action.
         *
         * @param id The unique id of the action. Cannot be null.
         * @param title The UI-visible title of the context menu item. Cannot be null.
         * @param isChecked Whether the context menu item has a check mark.
         * @param groupId The optional group id, to place the action in a given sub-menu.
         *                Can be null.
         * @param callback A callback to execute when the context menu item is
         *            selected.
         */
        public Toggle(String id, String title, boolean isChecked, String groupId,
                IMenuCallback callback) {
            super(id, title, groupId, callback);
            mIsChecked = isChecked;
        }

        /**
         * Returns true if the item is displayed with a check mark.
         */
        public boolean isChecked() {
            return mIsChecked;
        }

        /**
         * Two toggles are equal if they have the same id, title and group-id.
         * It is acceptable for the checked state and action callback to be different.
         */
        @Override
        public boolean equals(Object obj) {
            return super.equals(obj);
        }

        /**
         * Two toggles have the same hash code if they have the same id, title and group-id.
         */
        @Override
        public int hashCode() {
            return super.hashCode();
        }
    }

    /**
     * Like {@link Choices}, but with an explicit ordering among the children, and with
     * optional icons on each child choice
     */
    public static class OrderedChoices extends Action {
        protected List<String> mTitles;
        protected List<URL> mIconUrls;
        protected List<String> mIds;
        private boolean mRadio;

        /**
         * One or more id for the checked choice(s) that will be check marked.
         * Can be null. Can be an id not present in the choices map.
         */
        protected final String mCurrent;

        public OrderedChoices(String id, String title, String groupId, IMenuCallback callback,
                List<String> titles, List<URL> iconUrls, List<String> ids, String current) {
            super(id, title, groupId, callback);
            mTitles = titles;
            mIconUrls = iconUrls;
            mIds = ids;
            mCurrent = current;
        }

        public List<URL> getIconUrls() {
            return mIconUrls;
        }

        public List<String> getIds() {
            return mIds;
        }

        public List<String> getTitles() {
            return mTitles;
        }

        public String getCurrent() {
            return mCurrent;
        }

        /**
         * Set whether this choice list is best visualized as a radio group (instead of a
         * dropdown)
         *
         * @param radio true if this choice list should be visualized as a radio group
         */
        public void setRadio(boolean radio) {
            mRadio = radio;
        }

        /**
         * Returns true if this choice list is best visualized as a radio group (instead
         * of a dropdown)
         *
         * @return true if this choice list should be visualized as a radio group
         */
        public boolean isRadio() {
            return mRadio;
        }
    }

    /** Provides the set of choices associated with an {@link OrderedChoices} object
        when they are needed. Useful for lazy initialization of context menus and popup menus
        until they are actually needed. */
    public interface ChoiceProvider {
        /**
         * Adds in the needed titles, iconUrls (if any) and ids.
         *
         * @param titles a list of titles that the provider should append to
         * @param iconUrls a list of icon URLs that the provider should append to
         * @param ids a list of ids that the provider should append to
         */
        public void addChoices(List<String> titles, List<URL> iconUrls, List<String> ids);
    }

    /** Like {@link OrderedChoices}, but the set of choices is computed lazily */
    private static class DelayedOrderedChoices extends OrderedChoices {
        private final ChoiceProvider mProvider;

        public DelayedOrderedChoices(String id, String title, String groupId,
                IMenuCallback callback, String current, ChoiceProvider provider) {
            super(id, title, groupId, callback, null, null, null, current);
            mProvider = provider;
        }

        private void ensureInitialized() {
            if (mTitles == null) {
                mTitles = new ArrayList<String>();
                mIconUrls = new ArrayList<URL>();
                mIds = new ArrayList<String>();

                mProvider.addChoices(mTitles, mIconUrls, mIds);
            }
        }

        @Override
        public List<URL> getIconUrls() {
            ensureInitialized();
            return mIconUrls;
        }

        @Override
        public List<String> getIds() {
            ensureInitialized();
            return mIds;
        }

        @Override
        public List<String> getTitles() {
            ensureInitialized();
            return mTitles;
        }
    }

    /**
     * A "choices" is a one-out-of-many-choices action, displayed as a sub-menu with one or more
     * items, with either zero or more of them being checked.
     * <p/>
     * Implementation detail: empty choices will not be displayed in the context menu.
     * <p/>
     * Choice items are sorted by their id, using String's natural sorting order.
     * <p/>
     * Two multiple choices are equal if they have the same id, title, group-id and choices.
     * It is expected for the current state and action callback to be different.
     */
    public static class Choices extends Action {

        /**
         * Special value which will insert a separator in the choices' submenu.
         */
        public final static String SEPARATOR = "----";

        /**
         * Character used to split multiple checked choices, see {@link #getCurrent()}.
         * The pipe character "|" is used, to natively match Android resource flag separators.
         */
        public final static String CHOICE_SEP = "|";

        /**
         * A non-null map of id=>choice-title. The map could be empty but not null.
         */
        private final Map<String, String> mChoices;
        /**
         * One or more id for the checked choice(s) that will be check marked.
         * Can be null. Can be an id not present in the choices map.
         * If more than one choice, they must be separated by {@link #CHOICE_SEP}.
         */
        private final String mCurrent;

        /**
         * Creates a new immutable multiple-choice action.
         * This action has no group-id and will show up in the root of the context menu.
         *
         * @param id The unique id of the action. Cannot be null.
         * @param title The UI-visible title of the context menu item. Cannot be null.
         * @param choices A map id=>title for all the multiple-choice items. Cannot be null.
         * @param current The id(s) of the current choice(s) that will be check marked.
         *                Can be null. Can be an id not present in the choices map.
         *                There can be more than one id separated by {@link #CHOICE_SEP}.
         * @param callback A callback to execute when the context menu item is
         *            selected.
         */
        public Choices(String id, String title,
                Map<String, String> choices,
                String current,
                IMenuCallback callback) {
            this(id, title, choices, current, null /* group-id */, callback);
        }

        /**
         * Creates a new immutable multiple-choice action.
         *
         * @param id The unique id of the action. Cannot be null.
         * @param title The UI-visible title of the context menu item. Cannot be null.
         * @param choices A map id=>title for all the multiple-choice items. Cannot be null.
         * @param current The id(s) of the current choice(s) that will be check marked.
         *                Can be null. Can be an id not present in the choices map.
         *                There can be more than one id separated by {@link #CHOICE_SEP}.
         * @param groupId The optional group id, to place the action in a given sub-menu.
         *                Can be null.
         * @param callback A callback to execute when the context menu item is
         *            selected.
         */
        public Choices(String id, String title,
                Map<String, String> choices,
                String current,
                String groupId,
                IMenuCallback callback) {
            super(id, title, groupId, callback);
            mChoices = choices;
            mCurrent = current;
        }

        /**
         * Return the map of id=>choice-title. The map could be empty but not null.
         */
        public Map<String, String> getChoices() {
            return mChoices;
        }

        /**
         * Returns the id(s) of the current choice(s) that are check marked.
         * Can be null. Can be an id not present in the choices map.
         * There can be more than one id separated by {@link #CHOICE_SEP}.
         */
        public String getCurrent() {
            return mCurrent;
        }

        /**
         * Two multiple choices are equal if they have the same id, title, group-id and choices.
         * It is acceptable for the current state and action callback to be different.
         */
        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Choices && super.equals(obj)) {
                Choices rhs = (Choices) obj;
                return mChoices.equals(rhs.mChoices);
            }
            return false;
        }

        /**
         * Two multiple choices have the same hash code if they have the same id, title,
         * group-id and choices.
         */
        @Override
        public int hashCode() {
            int h = super.hashCode();

            if (mChoices != null) {
                h = h ^ mChoices.hashCode();
            }
            return h;
        }
    }
}
