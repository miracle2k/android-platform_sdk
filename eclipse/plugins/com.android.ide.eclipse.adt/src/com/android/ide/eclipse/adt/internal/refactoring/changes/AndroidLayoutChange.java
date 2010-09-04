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

import com.android.ide.eclipse.adt.internal.refactoring.core.IConstants;
import com.android.ide.eclipse.adt.internal.refactoring.core.RefactoringUtil;

import org.eclipse.core.filebuffers.ITextFileBufferManager;
import org.eclipse.core.filebuffers.LocationKind;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.ltk.core.refactoring.DocumentChange;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.wst.sse.core.StructuredModelManager;
import org.eclipse.wst.sse.core.internal.provisional.IStructuredModel;
import org.eclipse.wst.sse.core.internal.provisional.text.IStructuredDocument;
import org.eclipse.wst.sse.core.internal.provisional.text.IStructuredDocumentRegion;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMAttr;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMDocument;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMElement;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMModel;
import org.w3c.dom.Attr;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A text change that operates on android layout using WTP SSE model.
 * It is base class for Rename Package and Rename Type changes
*/
public class AndroidLayoutChange extends DocumentChange {

    private IDocument mDocument;

    private ITextFileBufferManager mManager;

    private IFile mFile;

    private IStructuredModel mModel;

    private Set<AndroidLayoutChangeDescription> mChanges;

    /**
     * Creates a new <code>AndroidLayoutChange</code>
     *
     * @param file the layout file
     * @param document the document
     * @param manager the buffer manager
     * @param changes the list of changes
     * @param androidLayoutChangeDescription
     */
    public AndroidLayoutChange(IFile file, IDocument document, ITextFileBufferManager manager,
            Set<AndroidLayoutChangeDescription> changes) {
        super("", document); //$NON-NLS-1$
        this.mFile = file;
        this.mDocument = document;
        this.mManager = manager;
        this.mChanges = changes;
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
        for (AndroidLayoutChangeDescription change : mChanges) {
            if (!change.isStandalone()) {
                TextEdit edit = createTextEdit(IConstants.ANDROID_LAYOUT_VIEW_ELEMENT,
                        IConstants.ANDROID_LAYOUT_CLASS_ARGUMENT, change.getClassName(),
                        change.getNewName());
                if (edit != null) {
                    multiEdit.addChild(edit);
                }
            } else {
                List<TextEdit> edits = createElementTextEdit(change.getClassName(),
                        change.getNewName());
                for (TextEdit edit : edits) {
                    multiEdit.addChild(edit);
                }
            }
        }
        setEdit(multiEdit);
    }

    /**
     * (non-Javadoc) Returns the text changes which change class (custom layout viewer) in layout file
     *
     * @param className the class name
     * @param newName the new class name
     *
     * @return list of text changes
     */
    private List<TextEdit> createElementTextEdit(String className, String newName) {
        IDOMDocument xmlDoc = getDOMDocument();
        List<TextEdit> edits = new ArrayList<TextEdit>();
        NodeList nodes = xmlDoc.getElementsByTagName(className);
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof IDOMElement) {
                IDOMElement domNode = (IDOMElement) node;
                IStructuredDocumentRegion firstRegion = domNode.getFirstStructuredDocumentRegion();
                if (firstRegion != null) {
                    int offset = firstRegion.getStartOffset();
                    edits.add(new ReplaceEdit(offset + 1, className.length(), newName));
                }
                IStructuredDocumentRegion endRegion = domNode.getEndStructuredDocumentRegion();
                if (endRegion != null) {
                    int offset = endRegion.getStartOffset();
                    edits.add(new ReplaceEdit(offset + 2, className.length(), newName));
                }
            }

        }
        return edits;
    }

    /**
     * Returns the SSE DOM document
     *
     * @return the attribute value
     */
    protected IDOMDocument getDOMDocument() {
        IDOMModel xmlModel = (IDOMModel) mModel;
        IDOMDocument xmlDoc = xmlModel.getDocument();
        return xmlDoc;
    }

    /**
     * Returns the text change that set new value of attribute
     *
     * @param attribute the attribute
     * @param newValue the new value
     *
     * @return the text change
     */
    protected TextEdit createTextEdit(Attr attribute, String newValue) {
        if (attribute == null)
            return null;

        if (attribute instanceof IDOMAttr) {
            IDOMAttr domAttr = (IDOMAttr) attribute;
            String region = domAttr.getValueRegionText();
            int offset = domAttr.getValueRegionStartOffset();
            if (region != null && region.length() >= 2) {
                return new ReplaceEdit(offset + 1, region.length() - 2, newValue);
            }
        }
        return null;
    }


    /**
     * Returns the text change that change the value of attribute from oldValue to newValue
     *
     * @param elementName the element name
     * @param attributeName the attribute name
     * @param oldValue the old value
     * @param newValue the new value
     *
     * @return the text change
     */
    protected TextEdit createTextEdit(String elementName, String argumentName, String oldName,
            String newName) {
        IDOMDocument xmlDoc = getDOMDocument();
        String name = null;
        Attr attr = findAttribute(xmlDoc, elementName, argumentName, oldName);
        if (attr != null) {
            name = attr.getValue();
        }
        if (name != null && newName != null) {
            TextEdit edit = createTextEdit(attr, newName);
            return edit;
        }
        return null;
    }

    /**
     * (non-Javadoc) Finds the attribute with values oldName
     *
     * @param xmlDoc the document
     * @param element the element
     * @param attributeName the attribute
     * @param oldValue the value
     *
     * @return the attribute
     */
    public Attr findAttribute(IDOMDocument xmlDoc, String element, String attributeName,
            String oldValue) {
        NodeList nodes = xmlDoc.getElementsByTagName(element);
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            NamedNodeMap attributes = node.getAttributes();
            if (attributes != null) {
                Attr attribute = RefactoringUtil.findAndroidAttributes(attributes, attributeName);
                if (attribute != null) {
                    String value = attribute.getValue();
                    if (value != null && value.equals(oldValue)) {
                        return attribute;
                    }
                }
            }
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return mFile.getName();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RefactoringStatus isValid(IProgressMonitor pm) throws CoreException,
            OperationCanceledException {
        RefactoringStatus status = super.isValid(pm);
        if (mModel == null) {
            status.addFatalError("Invalid the " + getName() + " file.");
        }
        return status;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setTextType(String type) {
        super.setTextType(mFile.getFileExtension());
    }

    /**
     * Returns the SSE model for a document
     *
     * @param document the document
     *
     * @return the model
     *
     */
    protected IStructuredModel getModel(IDocument document) throws IOException, CoreException {
        if (mModel != null) {
            return mModel;
        }
        IStructuredModel model;
        model = StructuredModelManager.getModelManager().getExistingModelForRead(document);
        if (model == null) {
            if (document instanceof IStructuredDocument) {
                IStructuredDocument structuredDocument = (IStructuredDocument) document;
                model = StructuredModelManager.getModelManager()
                        .getModelForRead(structuredDocument);
            }
        }
        return model;
    }

    @Override
    public void dispose() {
        super.dispose();
        RefactoringUtil.fixModel(mModel, mDocument);

        if (mManager != null) {
            try {
                mManager.disconnect(mFile.getFullPath(), LocationKind.NORMALIZE,
                        new NullProgressMonitor());
            } catch (CoreException e) {
                RefactoringUtil.log(e);
            }
        }
    }
}
