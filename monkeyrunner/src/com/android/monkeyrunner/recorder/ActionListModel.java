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
package com.android.monkeyrunner.recorder;

import com.google.common.collect.Lists;

import com.android.monkeyrunner.recorder.actions.Action;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.List;

import javax.swing.AbstractListModel;

/**
 * List model for managing actions.
 */
public class ActionListModel extends AbstractListModel {
    private List<Action> actionList = Lists.newArrayList();

    /**
     * Add the specified action to the end of the list
     * @param a the action to add.
     */
    public void add(Action a) {
        actionList.add(a);
        int newIndex = actionList.size() - 1;
        this.fireIntervalAdded(this, newIndex, newIndex);
    }

    @Override
    public Object getElementAt(int arg0) {
        return actionList.get(arg0).getDisplayName();
    }


    @Override
    public int getSize() {
        return actionList.size();
    }

    /**
     * Serialize all the stored actions to the specified file.
     *
     * @param selectedFile the file to write to
     * @throws FileNotFoundException if the file can't be created.
     */
    public void export(File selectedFile) throws FileNotFoundException {
        PrintWriter out = new PrintWriter(selectedFile);
        for (Action a : actionList) {
            out.println(a.serialize());
        }
        out.close();
    }
}