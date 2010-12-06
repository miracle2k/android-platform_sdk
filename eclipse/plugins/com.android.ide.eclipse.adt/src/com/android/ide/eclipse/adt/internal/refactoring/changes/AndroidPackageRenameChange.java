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

import com.android.ide.eclipse.adt.internal.refactoring.core.FixImportsJob;
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
 * A text change that operates on android manifest when execute Java Rename package refactoring
*/
public class AndroidPackageRenameChange extends AndroidDocumentChange {

    private boolean mIsPackage;

    /**
     * Creates a new <code>AndroidPackageRenameChange</code>
     *
     * @param androidManifest the android manifest file
     * @param manager the text buffer manager
     * @param document the document
     * @param elements the elements
     * @param newName the new name
     * @param oldName the old name
     * @param isPackage is the application package
     */
    public AndroidPackageRenameChange(IFile androidManifest, ITextFileBufferManager manager,
            IDocument document, Map<String, String> elements, String newName, String oldName,
            boolean isPackage) {
        super(document);
        this.mDocument = document;
        this.mIsPackage = isPackage;
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
            this.mAppPackage = getAppPackage();
            addEdits();
        }
    }

    /**
     * Adds text edits for this change
     */
    private void addEdits() {
        MultiTextEdit multiEdit = new MultiTextEdit();

        if (mIsPackage) {
            TextEdit edit = createTextEdit(AndroidManifest.NODE_MANIFEST,
                    AndroidManifest.ATTRIBUTE_PACKAGE, mOldName, mNewName, false);
            if (edit != null) {
                multiEdit.addChild(edit);
            }
        }
        Set<String> keys = mElements.keySet();
        for (String key : keys) {
            String value = mElements.get(key);
            String oldValue = AndroidManifest.combinePackageAndClassName(mAppPackage, value);
            String newValue = oldValue.replaceFirst(mOldName, mNewName);
            TextEdit edit = createTextEdit(key, AndroidManifest.ATTRIBUTE_NAME, oldValue,
                    newValue);
            if (edit != null) {
                multiEdit.addChild(edit);
            }
            if (AndroidManifest.NODE_ACTIVITY.equals(key)) {
                TextEdit alias = createTextEdit(AndroidManifest.NODE_ACTIVITY_ALIAS,
                        AndroidManifest.ATTRIBUTE_TARGET_ACTIVITY, oldValue, newValue);
                if (alias != null) {
                    multiEdit.addChild(alias);
                }
            }
        }
        setEdit(multiEdit);
    }

    @Override
    public Change perform(IProgressMonitor pm) throws CoreException {
        super.perform(pm);
        return new AndroidPackageRenameChange(mAndroidManifest, mManager, mDocument, mElements,
                mOldName, mNewName, mIsPackage);
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
        if (mIsPackage) {
            new FixImportsJob("Fix Rename Package", mAndroidManifest, mNewName).schedule(500);
        }
    }

}
