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

package com.android.ant;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.taskdefs.ExecTask;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.types.Path;
import org.apache.tools.ant.types.PatternSet.NameEntry;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * Task to execute aidl.
 * <p>
 * It expects 3 attributes:<br>
 * 'executable' ({@link Path} with a single path) for the location of the aidl executable<br>
 * 'framework' ({@link Path} with a single path) for the "preprocessed" file containing all the
 *     parcelables exported by the framework<br>
 * 'genFolder' ({@link Path} with a single path) for the location of the gen folder.
 *
 * It also expects one or more inner elements called "source" which are identical to {@link Path}
 * elements.
 */
public class RenderScriptTask extends Task {

    private String mExecutable;
    private String mFramework;
    private String mGenFolder;
    private String mResFolder;
    private final List<Path> mPaths = new ArrayList<Path>();

    /**
     * Sets the value of the "executable" attribute.
     * @param executable the value.
     */
    public void setExecutable(Path executable) {
        mExecutable = TaskHelper.checkSinglePath("executable", executable);
    }

    public void setFramework(Path value) {
        mFramework = TaskHelper.checkSinglePath("framework", value);
    }

    public void setGenFolder(Path value) {
        mGenFolder = TaskHelper.checkSinglePath("genFolder", value);
    }

    public void setResFolder(Path value) {
        mResFolder = TaskHelper.checkSinglePath("resFolder", value);
    }

    public Path createSource() {
        Path p = new Path(getProject());
        mPaths.add(p);
        return p;
    }

    @Override
    public void execute() throws BuildException {
        if (mExecutable == null) {
            throw new BuildException("RenderScriptTask's 'executable' is required.");
        }
        if (mFramework == null) {
            throw new BuildException("RenderScriptTask's 'framework' is required.");
        }
        if (mGenFolder == null) {
            throw new BuildException("RenderScriptTask's 'genFolder' is required.");
        }
        if (mResFolder == null) {
            throw new BuildException("RenderScriptTask's 'resFolder' is required.");
        }

        Project taskProject = getProject();

        // build a list of all the source folders
        ArrayList<String> sourceFolders = new ArrayList<String>();
        for (Path p : mPaths) {
            String[] values = p.list();
            if (values != null) {
                sourceFolders.addAll(Arrays.asList(values));
            }
        }

        File exe = new File(mExecutable);
        String execTaskName = exe.getName();

        // now loop on all the source folders to find all the renderscript to compile
        // and compile them
        for (String sourceFolder : sourceFolders) {
            // create a fileset to find all the aidl files in the current source folder
            FileSet fs = new FileSet();
            fs.setProject(taskProject);
            fs.setDir(new File(sourceFolder));
            NameEntry include = fs.createInclude();
            include.setName("**/*.rs");

            // loop through the results of the file set
            Iterator<?> iter = fs.iterator();
            while (iter.hasNext()) {
                Object next = iter.next();

                ExecTask task = new ExecTask();
                task.setTaskName(execTaskName);
                task.setProject(taskProject);
                task.setOwningTarget(getOwningTarget());
                task.setExecutable(mExecutable);
                task.setFailonerror(true);

                task.createArg().setValue("-I");
                task.createArg().setValue(mFramework);
                task.createArg().setValue("-p");
                task.createArg().setValue(mGenFolder);
                task.createArg().setValue("-o");
                task.createArg().setValue(mResFolder);
                task.createArg().setValue(next.toString());

                // execute it.
                task.execute();
            }
        }
    }
}
