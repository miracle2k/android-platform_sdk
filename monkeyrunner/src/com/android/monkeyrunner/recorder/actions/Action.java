/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.monkeyrunner.recorder.actions;

import com.android.monkeyrunner.MonkeyDevice;

/**
 * All actions that can be recorded must implement this interface.
 */
public interface Action {
    /**
     * Serialize this action into a string.  This method is called to put the list of actions into
     * a file.
     *
     * @return the serialized string
     */
    String serialize();

    /**
     * Get the printable name for this action.  This method is used to show the Action in the UI.
     *
     * @return the display name
     */
    String getDisplayName();

    /**
     * Execute the given action.
     *
     * @param device the device to execute the action on.
     */
    void execute(MonkeyDevice device) throws Exception;
}
