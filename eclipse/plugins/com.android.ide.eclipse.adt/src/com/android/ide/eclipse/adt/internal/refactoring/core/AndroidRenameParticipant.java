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

package com.android.ide.eclipse.adt.internal.refactoring.core;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.filebuffers.ITextFileBufferManager;
import org.eclipse.core.filebuffers.LocationKind;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.participants.CheckConditionsContext;
import org.eclipse.ltk.core.refactoring.participants.RenameParticipant;

import java.util.Map;

/**
 * The abstract class for Rename Package and Rename type participants
 *
 */
public abstract class AndroidRenameParticipant extends RenameParticipant {

    protected IFile mAndroidManifest;

    protected ITextFileBufferManager mManager;

    protected String mOldName;

    protected String mNewName;

    protected IDocument mDocument;

    protected String mAppPackage;

    protected Map<String, String> mAndroidElements;

    @Override
    public RefactoringStatus checkConditions(IProgressMonitor pm, CheckConditionsContext context)
            throws OperationCanceledException {
        return new RefactoringStatus();
    }

    /**
     * @return the document
     * @throws CoreException
     */
    public IDocument getDocument() throws CoreException {
        if (mDocument == null) {
            mManager = FileBuffers.getTextFileBufferManager();
            mManager.connect(mAndroidManifest.getFullPath(), LocationKind.NORMALIZE,
                    new NullProgressMonitor());
            ITextFileBuffer buffer = mManager.getTextFileBuffer(mAndroidManifest.getFullPath(),
                    LocationKind.NORMALIZE);
            mDocument = buffer.getDocument();
        }
        return mDocument;
    }

    /**
     * @return the android manifest file
     */
    public IFile getAndroidManifest() {
        return mAndroidManifest;
    }

}
