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

import org.eclipse.core.filebuffers.ITextFileBufferManager;
import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.IDocument;

import java.util.Map;

/**
 * A text change that operates on android manifest when execute Java Move type refactoring
*/
public class AndroidTypeMoveChange extends AndroidTypeRenameChange {

    /**
     * Creates a new <code>AndroidTypeMoveChange</code>
     *
     * @param androidManifest the android manifest file
     * @param manager the text buffer manager
     * @param document the document
     * @param elements the elements
     * @param newName the new name
     * @param oldName the old name
     */
    public AndroidTypeMoveChange(IFile androidManifest, ITextFileBufferManager manager,
            IDocument document, Map<String, String> elements, String newName, String oldName) {
        super(androidManifest, manager, document, elements, newName, oldName);
    }

}
