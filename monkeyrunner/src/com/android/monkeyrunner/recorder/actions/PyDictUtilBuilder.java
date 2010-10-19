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

/**
 * Utility class to create Python Dictionary Strings.
 *
 * {'key': 'value'}
 */
public class PyDictUtilBuilder {
    private StringBuilder sb = new StringBuilder();

    public PyDictUtilBuilder() {
        sb.append("{");
    }

    public static PyDictUtilBuilder newBuilder() {
        return new PyDictUtilBuilder();
    }

    private void addHelper(String key, String value) {
        sb.append("'").append(key).append("'");
        sb.append(":").append(value).append(",");
    }

    public PyDictUtilBuilder add(String key, int value) {
        addHelper(key, Integer.toString(value));
        return this;
    }

    public PyDictUtilBuilder add(String key, float value) {
        addHelper(key, Float.toString(value));
        return this;
    }

    public PyDictUtilBuilder add(String key, String value) {
        addHelper(key, "'" + value + "'");
        return this;
    }

    public String build() {
        sb.append("}");
        return sb.toString();
    }

    public PyDictUtilBuilder addTuple(String key, int x, int y) {
        String valuestr = new StringBuilder().append("(").append(x).append(",").append(y).append(")").toString();
        addHelper(key, valuestr);
        return this;
    }
}
