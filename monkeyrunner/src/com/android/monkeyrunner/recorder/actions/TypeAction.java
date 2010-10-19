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
 * Action to type in a string on the device.
 */
public class TypeAction implements Action {
    private final String whatToType;

    public TypeAction(String whatToType) {
        this.whatToType = whatToType;
    }

    @Override
    public String getDisplayName() {
        return String.format("Type \"%s\"", whatToType);
    }

    @Override
    public String serialize() {
        String pydict = PyDictUtilBuilder.newBuilder().add("message", whatToType).build();
        return "TYPE|" + pydict;
    }

    @Override
    public void execute(MonkeyDevice device) {
        device.type(whatToType);
    }
}
