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
 * Action to drag the "finger" across the device.
 */
public class DragAction implements Action {
    private final long timeMs;
    private final int steps;
    private final int startx;
    private final int starty;
    private final int endx;
    private final int endy;
    private final Direction dir;

    public enum Direction {
        NORTH, SOUTH, EAST, WEST;

        private static String[] names;
        static {
            Direction[] values = Direction.values();
            names = new String[values.length];
            for (int x = 0; x < values.length; x++) {
                names[x] = values[x].name();
            }
        }

        public static String[] getNames() {
            return names;
        }
    }

    public DragAction(Direction dir,
            int startx, int starty, int endx, int endy,
            int numSteps, long millis) {
        this.dir = dir;
        this.startx = startx;
        this.starty = starty;
        this.endx = endx;
        this.endy = endy;
        steps = numSteps;
        timeMs = millis;
    }

    @Override
    public String getDisplayName() {
        return String.format("Fling %s", dir.name().toLowerCase());
    }

    @Override
    public String serialize() {
        float duration = timeMs / 1000.0f;

        String pydict = PyDictUtilBuilder.newBuilder().
        addTuple("start", startx, starty).
        addTuple("end", endx, endy).
        add("duration", duration).
        add("steps", steps).
        build();
        return "DRAG|" + pydict;
    }

    @Override
    public void execute(MonkeyDevice device) {
        device.drag(startx, starty, endx, endy, steps, timeMs);
    }
}
