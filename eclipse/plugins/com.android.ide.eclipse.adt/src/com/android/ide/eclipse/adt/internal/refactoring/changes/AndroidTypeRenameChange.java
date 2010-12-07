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

package com.android.ide.eclipse.adt.internal.refactoring.changes;

import com.android.ide.eclipse.adt.internal.refactoring.core.RefactoringUtil;
import com.android.sdklib.xml.AndroidManifest;

import org.eclipse.core.filebuffers.ITextFileBufferManager;
import org.eclipse.core.filebuffers.LocationKind;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.text.IDocument;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.TextEdit;

import java.util.Map;
import java.util.Set;

/**
 * A text change that operates on android manifest when execute Java Rename type refactoring
*/
public class AndroidTypeRenameChange extends AndroidDocumentChange {

    /**
     * * Creates a new <code>AndroidTypeRenameChange</code>
     *
     * @param androidManifest the android manifest file
     * @param manager the text buffer manager
     * @param document the document
     * @param elements the elements
     * @param newName the new name
     * @param oldName the old name
     */
    public AndroidTypeRenameChange(IFile androidManifest, ITextFileBufferManager manager,
            IDocument document, Map<String, String> elements, String newName, String oldName) {
        super(document);
        this.mDocument = document;
        this.mElements = elements;
        this.mNewName = newName;
        this.mOldName = oldName;
        this.mManager = manager;
        this.mAndroidManifest = androidManifest;
        try {
            this.mModel = getModel(document);
        } catch (Exception ignore) {
        }
        if (mModel != null) {
            addEdits();
        }
    }

    /**
     * Adds text edits for this change
     */
    private void addEdits() {
        MultiTextEdit multiEdit = new MultiTextEdit();
        Set<String> keys = mElements.keySet();
        for (String key : keys) {
            TextEdit edit = createTextEdit(key, AndroidManifest.ATTRIBUTE_NAME, mOldName,
                    mNewName);
            if (edit != null) {
                multiEdit.addChild(edit);
            }
            if (AndroidManifest.NODE_ACTIVITY.equals(key)) {
                TextEdit alias = createTextEdit(AndroidManifest.NODE_ACTIVITY_ALIAS,
                        AndroidManifest.ATTRIBUTE_TARGET_ACTIVITY, mOldName, mNewName);
                if (alias != null) {
                    multiEdit.addChild(alias);
                }
                TextEdit manageSpaceActivity = createTextEdit(
                        AndroidManifest.NODE_APPLICATION,
                        AndroidManifest.ATTRIBUTE_MANAGE_SPACE_ACTIVITY, mOldName, mNewName);
                if (manageSpaceActivity != null) {
                    multiEdit.addChild(manageSpaceActivity);
                }
            }
        }
        setEdit(multiEdit);
    }

    @Override
    public Change perform(IProgressMonitor pm) throws CoreException {
        super.perform(pm);
        return new AndroidTypeRenameChange(mAndroidManifest, mManager, mDocument, mElements,
                mOldName, mNewName);
    }

    @Override
    public void dispose() {
        super.dispose();
        RefactoringUtil.fixModel(mModel, mDocument);

        if (mManager != null) {
            try {
                mManager.disconnect(mAndroidManifest.getFullPath(), LocationKind.NORMALIZE,
                        new NullProgressMonitor());
            } catch (CoreException e) {
                RefactoringUtil.log(e);
            }
        }
    }

}
