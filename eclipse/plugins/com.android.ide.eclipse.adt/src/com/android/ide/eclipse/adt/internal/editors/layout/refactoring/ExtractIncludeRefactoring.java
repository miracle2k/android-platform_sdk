/*
 * Copyright (C) 2011 The Android Open Source Project
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
package com.android.ide.eclipse.adt.internal.editors.layout.refactoring;

import static com.android.ide.common.layout.LayoutConstants.ANDROID_NS_PREFIX;
import static com.android.ide.common.layout.LayoutConstants.ANDROID_URI;
import static com.android.ide.common.layout.LayoutConstants.ATTR_ID;
import static com.android.ide.common.layout.LayoutConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.ide.common.layout.LayoutConstants.ATTR_LAYOUT_PREFIX;
import static com.android.ide.common.layout.LayoutConstants.ATTR_LAYOUT_WIDTH;
import static com.android.ide.common.layout.LayoutConstants.ID_PREFIX;
import static com.android.ide.common.layout.LayoutConstants.NEW_ID_PREFIX;
import static com.android.ide.common.layout.LayoutConstants.VALUE_WRAP_CONTENT;
import static com.android.ide.eclipse.adt.AndroidConstants.DOT_XML;
import static com.android.ide.eclipse.adt.AndroidConstants.EXT_XML;
import static com.android.ide.eclipse.adt.AndroidConstants.WS_SEP;
import static com.android.ide.eclipse.adt.internal.editors.descriptors.XmlnsAttributeDescriptor.XMLNS;
import static com.android.ide.eclipse.adt.internal.editors.descriptors.XmlnsAttributeDescriptor.XMLNS_COLON;
import static com.android.resources.ResourceType.LAYOUT;

import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.AndroidConstants;
import com.android.ide.eclipse.adt.internal.editors.layout.LayoutEditor;
import com.android.ide.eclipse.adt.internal.editors.layout.descriptors.LayoutDescriptors;
import com.android.ide.eclipse.adt.internal.editors.layout.gle2.CanvasViewInfo;
import com.android.ide.eclipse.adt.internal.editors.layout.gle2.DomUtilities;
import com.android.ide.eclipse.adt.internal.editors.layout.uimodel.UiViewElementNode;
import com.android.ide.eclipse.adt.internal.resources.ResourceNameValidator;
import com.android.util.Pair;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ITreeSelection;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.NullChange;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.eclipse.swt.widgets.Display;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.wst.sse.core.internal.provisional.IStructuredModel;
import org.eclipse.wst.sse.core.internal.provisional.text.IStructuredDocument;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Extracts the selection and writes it out as a separate layout file, then adds an
 * include to that new layout file. Interactively asks the user for a new name for the
 * layout.
 */
@SuppressWarnings("restriction") // XML model
public class ExtractIncludeRefactoring extends VisualRefactoring {
    private static final String KEY_NAME = "name";                      //$NON-NLS-1$
    private static final String KEY_OCCURRENCES = "all-occurrences";    //$NON-NLS-1$
    private static final String KEY_UPDATE_REFS = "update-refs";        //$NON-NLS-1$
    private String mLayoutName;
    private boolean mReplaceOccurrences;
    private boolean mUpdateReferences;

    /**
     * This constructor is solely used by {@link Descriptor},
     * to replay a previous refactoring.
     * @param arguments argument map created by #createArgumentMap.
     */
    ExtractIncludeRefactoring(Map<String, String> arguments) {
        super(arguments);
        mLayoutName = arguments.get(KEY_NAME);
        mUpdateReferences = Boolean.parseBoolean(arguments.get(KEY_UPDATE_REFS));
        mReplaceOccurrences  = Boolean.parseBoolean(arguments.get(KEY_OCCURRENCES));
    }

    public ExtractIncludeRefactoring(IFile file, LayoutEditor editor, ITextSelection selection,
            ITreeSelection treeSelection) {
        super(file, editor, selection, treeSelection);
    }

    @Override
    public RefactoringStatus checkInitialConditions(IProgressMonitor pm) throws CoreException,
            OperationCanceledException {
        RefactoringStatus status = new RefactoringStatus();

        try {
            pm.beginTask("Checking preconditions...", 6);

            if (mSelectionStart == -1 || mSelectionEnd == -1) {
                status.addFatalError("No selection to extract");
                return status;
            }

            // Make sure the selection is contiguous
            if (mTreeSelection != null) {
                // TODO - don't do this if we based the selection on text. In this case,
                // make sure we're -balanced-.
                List<CanvasViewInfo> infos = getSelectedViewInfos();
                if (!validateNotEmpty(infos, status)) {
                    return status;
                }

                if (!validateNotRoot(infos, status)) {
                    return status;
                }

                // Disable if you've selected a single include tag
                if (infos.size() == 1) {
                    UiViewElementNode uiNode = infos.get(0).getUiViewNode();
                    if (uiNode != null) {
                        Node xmlNode = uiNode.getXmlNode();
                        if (xmlNode.getLocalName().equals(LayoutDescriptors.VIEW_INCLUDE)) {
                            status.addWarning("No point in refactoring a single include tag");
                        }
                    }
                }

                // Enforce that the selection is -contiguous-
                if (!validateContiguous(infos, status)) {
                    return status;
                }
            }

            // This also ensures that we have a valid DOM model:
            mElements = getElements();
            if (mElements.size() == 0) {
                status.addFatalError("Nothing to extract");
                return status;
            }

            pm.worked(1);
            return status;

        } finally {
            pm.done();
        }
    }

    @Override
    protected VisualRefactoringDescriptor createDescriptor() {
        String comment = getName();
        return new Descriptor(
                mProject.getName(), //project
                comment, //description
                comment, //comment
                createArgumentMap());
    }

    @Override
    protected Map<String, String> createArgumentMap() {
        Map<String, String> args = super.createArgumentMap();
        args.put(KEY_NAME, mLayoutName);
        args.put(KEY_UPDATE_REFS, Boolean.toString(mUpdateReferences));
        args.put(KEY_OCCURRENCES, Boolean.toString(mReplaceOccurrences));

        return args;
    }

    @Override
    public String getName() {
        return "Extract as Include";
    }

    void setLayoutName(String layoutName) {
        mLayoutName = layoutName;
    }

    void setUpdateReferences(boolean selection) {
        mUpdateReferences = selection;
    }

    void setReplaceOccurrences(boolean selection) {
        mReplaceOccurrences = selection;
    }

    // ---- Actual implementation of Extract as Include modification computation ----

    @Override
    protected List<Change> computeChanges() {
        String extractedText = getExtractedText();

        Pair<String, String> namespace = computeNamespaces();
        String androidNsPrefix = namespace.getFirst();
        String namespaceDeclarations = namespace.getSecond();

        // Insert namespace:
        extractedText = insertNamespace(extractedText, namespaceDeclarations);

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"); //$NON-NLS-1$
        sb.append(extractedText);
        sb.append('\n');

        List<Change> changes = new ArrayList<Change>();

        String newFileName = mLayoutName + DOT_XML;
        IProject project = mEditor.getProject();
        IFile sourceFile = mEditor.getInputFile();

        TextFileChange change = new TextFileChange(sourceFile.getName(), sourceFile);
        MultiTextEdit rootEdit = new MultiTextEdit();
        change.setEdit(rootEdit);
        change.setTextType(EXT_XML);
        changes.add(change);

        String referenceId = getReferenceId();
        // Replace existing elements in the source file and insert <include>
        String include = computeIncludeString(mLayoutName, androidNsPrefix, referenceId);
        int length = mSelectionEnd - mSelectionStart;
        ReplaceEdit replace = new ReplaceEdit(mSelectionStart, length, include);
        rootEdit.addChild(replace);

        // Update any layout references to the old id with the new id
        if (mUpdateReferences && referenceId != null) {
            String rootId = getRootId();
            IStructuredModel model = mEditor.getModelForRead();
            try {
                IStructuredDocument doc = model.getStructuredDocument();
                if (doc != null) {
                    List<TextEdit> replaceIds = replaceIds(doc, mSelectionStart,
                            mSelectionEnd, rootId, referenceId);
                    for (TextEdit edit : replaceIds) {
                        rootEdit.addChild(edit);
                    }
                }
            } finally {
                model.releaseFromRead();
            }
        }

        // Add change to create the new file
        IContainer parent = sourceFile.getParent();
        IPath parentPath = parent.getProjectRelativePath();
        final IFile file = project.getFile(new Path(parentPath + WS_SEP + newFileName));
        TextFileChange addFile = new TextFileChange("Create new separate layout", file);
        addFile.setTextType(AndroidConstants.EXT_XML);
        changes.add(addFile);
        addFile.setEdit(new InsertEdit(0, sb.toString()));

        Change finishHook = createFinishHook(file);
        changes.add(finishHook);

        return changes;
    }

    String getInitialName() {
        String defaultName = ""; //$NON-NLS-1$
        Element primary = getPrimaryElement();
        if (primary != null) {
            String id = primary.getAttributeNS(ANDROID_URI, ATTR_ID);
            // id null check for https://bugs.eclipse.org/bugs/show_bug.cgi?id=272378
            if (id != null && (id.startsWith(ID_PREFIX) || id.startsWith(NEW_ID_PREFIX))) {
                // Use everything following the id/, and make it lowercase since that is
                // the convention for layouts
                defaultName = id.substring(id.indexOf('/') + 1).toLowerCase();

                IInputValidator validator = ResourceNameValidator.create(true, mProject, LAYOUT);

                if (validator.isValid(defaultName) != null) { // Already exists?
                    defaultName = ""; //$NON-NLS-1$
                }
            }
        }

        return defaultName;
    }

    private Change createFinishHook(final IFile file) {
        return new NullChange("Open extracted layout and refresh resources") {
            @Override
            public Change perform(IProgressMonitor pm) throws CoreException {
                Display display = AdtPlugin.getDisplay();
                display.asyncExec(new Runnable() {
                    public void run() {
                        openFile(file);
                        mEditor.getGraphicalEditor().refreshProjectResources();
                        // Save file to trigger include finder scanning (as well as making
                        // the
                        // actual show-include feature work since it relies on reading
                        // files from
                        // disk, not a live buffer)
                        IWorkbenchPage page = mEditor.getEditorSite().getPage();
                        page.saveEditor(mEditor, false);
                    }
                });

                // Not undoable: just return null instead of an undo-change.
                return null;
            }
        };
    }

    private Pair<String, String> computeNamespaces() {
        String androidNsPrefix = null;
        String namespaceDeclarations = null;

        StringBuilder sb = new StringBuilder();
        List<Attr> attributeNodes = findNamespaceAttributes();
        for (Node attributeNode : attributeNodes) {
            String prefix = attributeNode.getPrefix();
            if (XMLNS.equals(prefix)) {
                sb.append(' ');
                String name = attributeNode.getNodeName();
                sb.append(name);
                sb.append('=').append('"');

                String value = attributeNode.getNodeValue();
                if (value.equals(ANDROID_URI)) {
                    androidNsPrefix = name;
                    if (androidNsPrefix.startsWith(XMLNS_COLON)) {
                        androidNsPrefix = androidNsPrefix.substring(XMLNS_COLON.length());
                    }
                }
                sb.append(DomUtilities.toXmlAttributeValue(value));
                sb.append('"');
            }
        }
        namespaceDeclarations = sb.toString();

        if (androidNsPrefix == null) {
            androidNsPrefix = ANDROID_NS_PREFIX;
        }

        if (namespaceDeclarations.length() == 0) {
            sb.setLength(0);
            sb.append(' ');
            sb.append(XMLNS_COLON);
            sb.append(androidNsPrefix);
            sb.append('=').append('"');
            sb.append(ANDROID_URI);
            sb.append('"');
            namespaceDeclarations = sb.toString();
        }

        return Pair.of(androidNsPrefix, namespaceDeclarations);
    }

    /** Returns the id to be used for the include tag itself (may be null) */
    private String getReferenceId() {
        String rootId = getRootId();
        if (rootId != null) {
            return rootId + "_ref";
        }

        return null;
    }

    /**
     * Compute the actual {@code <include>} string to be inserted in place of the old
     * selection
     */
    private String computeIncludeString(String newName, String androidNsPrefix,
            String referenceId) {
        StringBuilder sb = new StringBuilder();
        sb.append("<include layout=\"@layout/"); //$NON-NLS-1$
        sb.append(newName);
        sb.append('"');
        sb.append(' ');

        // Create new id for the include itself
        if (referenceId != null) {
            sb.append(androidNsPrefix);
            sb.append(':');
            sb.append(ATTR_ID);
            sb.append('=').append('"');
            sb.append(referenceId);
            sb.append('"').append(' ');
        }

        // Add id string, unless it's a <merge>, since we may need to adjust any layout
        // references to apply to the <include> tag instead

        // I should move all the layout_ attributes as well
        // I also need to duplicate and modify the id and then replace
        // everything else in the file with this new id...

        // HACK: see issue 13494: We must duplicate the width/height attributes on the
        // <include> statement for designtime rendering only
        Element primaryNode = getPrimaryElement();
        String width = null;
        String height = null;
        if (primaryNode == null) {
            // Multiple selection - in that case we will be creating an outer <merge>
            // so we need to set our own width/height on it
            width = height = VALUE_WRAP_CONTENT;
        } else {
            if (!primaryNode.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH)) {
                width = VALUE_WRAP_CONTENT;
            } else {
                width = primaryNode.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
            }
            if (!primaryNode.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT)) {
                height = VALUE_WRAP_CONTENT;
            } else {
                height = primaryNode.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);
            }
        }
        if (width != null) {
            sb.append(' ');
            sb.append(androidNsPrefix);
            sb.append(':');
            sb.append(ATTR_LAYOUT_WIDTH);
            sb.append('=').append('"');
            sb.append(DomUtilities.toXmlAttributeValue(width));
            sb.append('"');
        }
        if (height != null) {
            sb.append(' ');
            sb.append(androidNsPrefix);
            sb.append(':');
            sb.append(ATTR_LAYOUT_HEIGHT);
            sb.append('=').append('"');
            sb.append(DomUtilities.toXmlAttributeValue(height));
            sb.append('"');
        }

        // Duplicate all the other layout attributes as well
        if (primaryNode != null) {
            NamedNodeMap attributes = primaryNode.getAttributes();
            for (int i = 0, n = attributes.getLength(); i < n; i++) {
                Node attr = attributes.item(i);
                String name = attr.getLocalName();
                if (name.startsWith(ATTR_LAYOUT_PREFIX)
                        && ANDROID_URI.equals(attr.getNamespaceURI())) {
                    if (name.equals(ATTR_LAYOUT_WIDTH) || name.equals(ATTR_LAYOUT_HEIGHT)) {
                        // Already handled
                        continue;
                    }

                    sb.append(' ');
                    sb.append(androidNsPrefix);
                    sb.append(':');
                    sb.append(name);
                    sb.append('=').append('"');
                    sb.append(DomUtilities.toXmlAttributeValue(attr.getNodeValue()));
                    sb.append('"');
                }
            }
        }

        sb.append("/>");
        return sb.toString();
    }

    /** Return the text in the document in the range start to end */
    private String getExtractedText() {
        String xml = getText(mSelectionStart, mSelectionEnd);
        Element primaryNode = getPrimaryElement();
        xml = stripTopLayoutAttributes(primaryNode, mSelectionStart, xml);
        xml = dedent(xml);

        // Wrap siblings in <merge>?
        if (primaryNode == null) {
            StringBuilder sb = new StringBuilder();
            sb.append("<merge>\n"); //$NON-NLS-1$
            // indent an extra level
            for (String line : xml.split("\n")) { //$NON-NLS-1$
                sb.append("    "); //$NON-NLS-1$
                sb.append(line).append('\n');
            }
            sb.append("</merge>\n"); //$NON-NLS-1$
            xml = sb.toString();
        }

        return xml;
    }

    public static class Descriptor extends VisualRefactoringDescriptor {
        public Descriptor(String project, String description, String comment,
                Map<String, String> arguments) {
            super("com.android.ide.eclipse.adt.refactoring.extract.include", //$NON-NLS-1$
                    project, description, comment, arguments);
        }

        @Override
        protected Refactoring createRefactoring(Map<String, String> args) {
            return new ExtractIncludeRefactoring(args);
        }
    }
}
