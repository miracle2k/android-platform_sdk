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
 * Action to touch the touchscreen at a certain location.
 */
public class TouchAction implements Action {
    public static final BiMap<String, String> DOWNUP_FLAG_MAP =
        ImmutableBiMap.of(MonkeyDevice.DOWN_AND_UP, "Tap",
                MonkeyDevice.DOWN, "Down",
                MonkeyDevice.UP, "Up");

    private final int x;
    private final int y;
    private final String direction;

    public TouchAction(int x, int y, String direction) {
        this.x = x;
        this.y = y;
        this.direction = direction;
    }

    @Override
    public String getDisplayName() {
        return String.format("%s touchscreen at (%d, %d)",
                DOWNUP_FLAG_MAP.get(direction), x, y);
    }

    @Override
    public void execute(MonkeyDevice device) throws Exception {
        device.touch(x, y,
                MonkeyDevice.TOUCH_NAME_TO_ENUM.get(direction));
    }

    @Override
    public String serialize() {
        String pydict = PyDictUtilBuilder.newBuilder().
        add("x", x).
        add("y", y).
        add("type", direction).build();
        return "TOUCH|" + pydict;
    }
}
