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

/**
 * Callback interface for {@link MenuAction}s. The callback performs the actual
 * work of the menu.
 * <p>
 * <b>NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.</b>
 * </p>
 */
public interface IMenuCallback {
    /**
     * Performs the actual work promised by the {@link MenuAction}.
     *
     * @param action The MenuAction being applied.
     * @param valueId For a Choices action, the string id of the selected choice
     * @param newValue For a toggle or for a flag, true if the item is being
     *            checked, false if being unchecked. For enums this is not
     *            useful; however for flags it allows one to add or remove items
     *            to the flag's choices.
     */
    void action(MenuAction menuAction, String valueId, Boolean newValue);
}
