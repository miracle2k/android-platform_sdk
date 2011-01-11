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

import com.android.sdklib.annotations.Nullable;

/**
 * A Client Rules Engine is a set of methods that {@link IViewRule}s can use to
 * access the client public API of the Rules Engine. Rules can access it via
 * the property "_rules_engine" which is dynamically added to {@link IViewRule}
 * instances on creation.
 * <p>
 * <b>NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.</b>
 * </p>
 */
public interface IClientRulesEngine {

    /**
     * Returns the FQCN for which the rule was loaded.
     *
     * @return the fully qualified name of the rule
     */
    String getFqcn();

    /**
     * Prints a debug line in the Eclipse console using the ADT formatter.
     *
     * @param msg A String format message.
     * @param params Optional parameters for the message.
     */
    void debugPrintf(String msg, Object...params);

    /**
     * Loads and returns an {@link IViewRule} for the given FQCN.
     *
     * @param fqcn A non-null, non-empty FQCN for the rule to load.
     * @return The rule that best matches the given FQCN according to the
     *   inheritance chain. Rules are cached and requesting the same FQCN twice
     *   is fast and will return the same rule instance.
     */
    IViewRule loadRule(String fqcn);

    /**
     * Returns the metadata associated with the given fully qualified class name.
     *
     * @param fqcn a fully qualified class name for an Android view class
     * @return the metadata associated with the given fully qualified class name.
     */
    IViewMetadata getMetadata(String fqcn);

    /**
     * Displays the given message string in an alert dialog with an "OK" button.
     *
     * @param message the message to be shown
     */
    void displayAlert(String message);

    /**
     * Displays a simple input alert dialog with an OK and Cancel buttons.
     *
     * @param message The message to display in the alert dialog.
     * @param value The initial value to display in the input field. Can be null.
     * @param filter An optional filter to validate the input. Specify null (or
     *            a validator which always returns true) if you do not want
     *            input validation.
     * @return Null if canceled by the user. Otherwise the possibly-empty input string.
     * @null Return value is null if dialog was canceled by the user.
     */
    @Nullable
    String displayInput(String message, @Nullable String value, @Nullable IValidator filter);

    /**
     * Returns the minimum API level that the current Android project is targeting.
     *
     * @return the minimum API level to be supported, or -1 if it cannot be determined
     */
    int getMinApiLevel();

    /**
     * Returns a resource name validator for the current project
     *
     * @return an {@link IValidator} for validating new resource name in the current
     *         project
     */
    IValidator getResourceValidator();

    /**
     * Displays an input dialog where the user can enter an Android reference value
     *
     * @param currentValue the current reference to select
     * @return the reference selected by the user, or null
     */
    String displayReferenceInput(String currentValue);

    /**
     * Displays an input dialog where the user can enter an Android resource name of the
     * given resource type ("id", "string", "drawable", and so on.)
     *
     * @param currentValue the current reference to select
     * @param resourceTypeName resource type, such as "id", "string", and so on (never
     *            null)
     * @return the resource selected by the user, or null
     */
    String displayResourceInput(String resourceTypeName, String currentValue);
}

