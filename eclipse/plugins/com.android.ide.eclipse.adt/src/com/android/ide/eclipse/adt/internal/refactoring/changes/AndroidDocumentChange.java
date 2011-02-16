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

import com.android.ide.eclipse.adt.internal.project.AndroidManifestHelper;
import com.android.ide.eclipse.adt.internal.project.XmlErrorHandler.BasicXmlErrorListener;
import com.android.ide.eclipse.adt.internal.refactoring.core.RefactoringUtil;
import com.android.sdklib.SdkConstants;
import com.android.sdklib.xml.AndroidManifest;

import org.eclipse.core.filebuffers.ITextFileBufferManager;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.ltk.core.refactoring.DocumentChange;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.wst.sse.core.StructuredModelManager;
import org.eclipse.wst.sse.core.internal.provisional.IStructuredModel;
import org.eclipse.wst.sse.core.internal.provisional.text.IStructuredDocument;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMAttr;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMDocument;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMModel;
import org.w3c.dom.Attr;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Map;

/**
 * A text change that operates on android manifest using WTP SSE model.
 * It is base class for Rename Package and Rename Type changes
*/
@SuppressWarnings("restriction")
public class AndroidDocumentChange extends DocumentChange {

    protected IFile mAndroidManifest;

    protected String mAppPackage;

    protected IStructuredModel mModel;

    protected IDocument mDocument;

    protected Map<String, String> mElements;

    protected String mNewName;

    protected String mOldName;

    protected ITextFileBufferManager mManager;

    /**
     * Creates a new <code>AndroidDocumentChange</code> for the given
     * {@link IDocument}.
     *
     * @param document the document this change is working on
     */
    public AndroidDocumentChange(IDocument document) {
        super(SdkConstants.FN_ANDROID_MANIFEST_XML , document);
    }

     @Override
    public RefactoringStatus isValid(IProgressMonitor pm) throws CoreException,
            OperationCanceledException {
        RefactoringStatus status = super.isValid(pm);
        if (mModel == null) {
            status.addFatalError("File " + SdkConstants.FN_ANDROID_MANIFEST_XML + " is invalid.");
        } else {
            mAppPackage = getAppPackage();
            if (mAppPackage == null) {
                status.addFatalError("Invalid package in the "
                        + SdkConstants.FN_ANDROID_MANIFEST_XML + " file.");
            }
        }
        BasicXmlErrorListener errorListener = new BasicXmlErrorListener();
        AndroidManifestHelper.parseForError(mAndroidManifest, errorListener);

        if (errorListener.mHasXmlError == true) {
            status.addFatalError("File " + SdkConstants.FN_ANDROID_MANIFEST_XML + " is invalid.");
        }
        return status;
    }

    /**
     * Finds the attribute with values oldName
     *
     * @param xmlDoc the document
     * @param element the element
     * @param attributeName the attribute
     * @param oldName the value
     *
     * @return the attribute
     */
    private Attr findAttribute(IDOMDocument xmlDoc, String element, String attributeName,
            String oldName) {
        NodeList nodes = xmlDoc.getElementsByTagName(element);
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            NamedNodeMap attributes = node.getAttributes();
            if (attributes != null) {
                Attr attribute = RefactoringUtil.findAndroidAttributes(attributes, attributeName);
                if (attribute != null) {
                    String value = attribute.getValue();
                    if (value != null) {
                        String fullName = AndroidManifest.combinePackageAndClassName(
                                getAppPackage(), value);
                        if (fullName != null && fullName.equals(oldName)) {
                            return attribute;
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Returns the attribute value
     *
     * @param xmlDoc the document
     * @param element the element
     * @param attributeName the attribute
     *
     * @return the attribute value
     */
    protected String getElementAttribute(IDOMDocument xmlDoc, String element,
            String attributeName, boolean useNamespace) {
        NodeList nodes = xmlDoc.getElementsByTagName(element);
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            NamedNodeMap attributes = node.getAttributes();
            if (attributes != null) {
                Attr attribute;
                if (useNamespace) {
                    attribute = RefactoringUtil.findAndroidAttributes(attributes, attributeName);
                } else {
                    attribute = (Attr) attributes.getNamedItem(attributeName);
                }
                if (attribute != null) {
                    return attribute.getValue();
                }
            }
        }
        return null;
    }

    /**
     * Returns the SSE model for a document
     *
     * @param document the document
     * @return the model
     *
     */
    protected IStructuredModel getModel(IDocument document) {
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
    public void setTextType(String type) {
        super.setTextType(mAndroidManifest.getFileExtension());
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
     * Returns the application package
     *
     * @return the package name
     */
    protected String getAppPackage() {
        if (mAppPackage == null) {
            IDOMDocument xmlDoc = getDOMDocument();
            mAppPackage = getElementAttribute(xmlDoc, AndroidManifest.NODE_MANIFEST,
                    AndroidManifest.ATTRIBUTE_PACKAGE, false);
        }
        return mAppPackage;
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
     * and combine package
     *
     * @param elementName the element name
     * @param attributeName the attribute name
     * @param oldValue the old value
     * @param newValue the new value
     *
     * @return the text change
     */
    protected TextEdit createTextEdit(String elementName, String attributeName, String oldValue,
            String newValue) {
        return createTextEdit(elementName, attributeName, oldValue, newValue, true);
    }

    /**
     * Returns the text change that change the value of attribute from oldValue to newValue
     *
     * @param elementName the element name
     * @param attributeName the attribute name
     * @param oldName the old value
     * @param newName the new value
     * @param combinePackage combine package ?
     *
     * @return the text change
     */
    protected TextEdit createTextEdit(String elementName, String attributeName, String oldName,
            String newName, boolean combinePackage) {
        IDOMDocument xmlDoc = getDOMDocument();
        String name = null;
        Attr attr = findAttribute(xmlDoc, elementName, attributeName, oldName);
        if (attr != null) {
            name = attr.getValue();
        }
        if (name != null) {
            String newValue;
            if (combinePackage) {
                newValue = AndroidManifest.extractActivityName(newName, getAppPackage());
            } else {
                newValue = newName;
            }
            if (newValue != null) {
                TextEdit edit = createTextEdit(attr, newValue);
                return edit;
            }
        }
        return null;
    }

}
