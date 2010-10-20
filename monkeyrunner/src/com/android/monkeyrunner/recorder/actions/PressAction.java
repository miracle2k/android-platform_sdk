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

import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;

import com.android.monkeyrunner.MonkeyDevice;

/**
 * Action to press a certain button.
 */
public class PressAction implements Action {
    public static String[] KEYS = {
        "MENU", "HOME", "SEARCH",
    };

    public static final BiMap<String, String> DOWNUP_FLAG_MAP =
        ImmutableBiMap.of(MonkeyDevice.DOWN_AND_UP, "Press",
                MonkeyDevice.DOWN, "Down",
                MonkeyDevice.UP, "Up");

    private final String key;
    private final String downUpFlag;

    public PressAction(String key, String downUpFlag) {
        this.key = key;
        this.downUpFlag = downUpFlag;
    }

    public PressAction(String key) {
        this(key, MonkeyDevice.DOWN_AND_UP);
    }

    @Override
    public String getDisplayName() {
        return String.format("%s button %s",
                DOWNUP_FLAG_MAP.get(downUpFlag), key);
    }

    @Override
    public String serialize() {
        String pydict = PyDictUtilBuilder.newBuilder().
        add("name", key).
        add("type", downUpFlag).build();
        return "PRESS|" + pydict;
    }

    @Override
    public void execute(MonkeyDevice device) {
        device.press(key,
                MonkeyDevice.TOUCH_NAME_TO_ENUM.get(downUpFlag));
    }
}
