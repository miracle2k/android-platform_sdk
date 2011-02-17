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

import static com.android.ide.common.layout.LayoutConstants.ANDROID_URI;
import static com.android.ide.common.layout.LayoutConstants.ATTR_ID;
import static com.android.ide.common.layout.LayoutConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.ide.common.layout.LayoutConstants.ATTR_LAYOUT_WIDTH;
import static com.android.ide.common.layout.LayoutConstants.VALUE_FILL_PARENT;
import static com.android.ide.common.layout.LayoutConstants.VALUE_MATCH_PARENT;
import static com.android.ide.common.layout.LayoutConstants.VALUE_WRAP_CONTENT;
import static com.android.ide.eclipse.adt.AndroidConstants.EXT_XML;

import com.android.ide.eclipse.adt.internal.editors.AndroidXmlEditor;
import com.android.ide.eclipse.adt.internal.editors.layout.LayoutEditor;
import com.android.ide.eclipse.adt.internal.editors.layout.gle2.CanvasViewInfo;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ITreeSelection;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.eclipse.text.edits.DeleteEdit;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.wst.sse.core.internal.provisional.IStructuredModel;
import org.eclipse.wst.sse.core.internal.provisional.IndexedRegion;
import org.eclipse.wst.sse.core.internal.provisional.text.IStructuredDocument;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Inserts a new layout surrounding the current selection, migrates namespace
 * attributes (if wrapping the root node), and optionally migrates layout
 * attributes and updates references elsewhere.
 */
@SuppressWarnings("restriction") // XML model
public class WrapInRefactoring extends VisualRefactoring {
    private static final String KEY_ID = "name";                           //$NON-NLS-1$
    private static final String KEY_TYPE = "type";                         //$NON-NLS-1$
    private static final String KEY_UPDATE_REFS = "update-refs";           //$NON-NLS-1$

    private String mId;
    private String mTypeFqcn;
    private boolean mUpdateReferences;

    /**
     * This constructor is solely used by {@link Descriptor},
     * to replay a previous refactoring.
     * @param arguments argument map created by #createArgumentMap.
     */
    WrapInRefactoring(Map<String, String> arguments) {
        super(arguments);
        mId = arguments.get(KEY_ID);
        mTypeFqcn = arguments.get(KEY_TYPE);
        mUpdateReferences = Boolean.parseBoolean(arguments.get(KEY_UPDATE_REFS));
    }

    public WrapInRefactoring(IFile file, LayoutEditor editor, ITextSelection selection,
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
                status.addFatalError("No selection to wrap");
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

                // Enforce that the selection is -contiguous-
                if (!validateContiguous(infos, status)) {
                    return status;
                }
            }

            // This also ensures that we have a valid DOM model:
            mElements = getElements();
            if (mElements.size() == 0) {
                status.addFatalError("Nothing to wrap");
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
        args.put(KEY_TYPE, mTypeFqcn);
        args.put(KEY_ID, mId);
        args.put(KEY_UPDATE_REFS, Boolean.toString(mUpdateReferences));

        return args;
    }

    @Override
    public String getName() {
        return "Wrap in Container";
    }

    void setId(String id) {
        mId = id;
    }

    void setType(String typeFqcn) {
        mTypeFqcn = typeFqcn;
    }

    void setUpdateReferences(boolean selection) {
        mUpdateReferences = selection;
    }

    @Override
    protected List<Change> computeChanges() {
        // (1) Insert the new container in front of the beginning of the
        //      first wrapped view
        // (2) If the container is the new root, transfer namespace declarations
        //      to it
        // (3) Insert the closing tag of the new container at the end of the
        //      last wrapped view
        // (4) Reindent the wrapped views
        // (5) If the user requested it, update all layout references to the
        //      wrapped views with the new container?
        //   For that matter, does RelativeLayout even require it? Probably not,
        //   it can point inside the current layout...

        // Add indent to all lines between mSelectionStart and mEnd
        // TODO: Figure out the indentation amount?
        // For now, use 4 spaces
        String indentUnit = "    "; //$NON-NLS-1$
        boolean separateAttributes = true;
        IStructuredDocument document = mEditor.getStructuredDocument();
        String startIndent = AndroidXmlEditor.getIndentAtOffset(document, mSelectionStart);

        String viewClass = getViewClass(mTypeFqcn);

        IFile file = mEditor.getInputFile();
        List<Change> changes = new ArrayList<Change>();
        TextFileChange change = new TextFileChange(file.getName(), file);
        MultiTextEdit rootEdit = new MultiTextEdit();
        change.setEdit(rootEdit);
        change.setTextType(EXT_XML);

        String id = ensureNewId(mId);

        // Update any layout references to the old id with the new id
        if (mUpdateReferences && id != null) {
            String rootId = getRootId();
            IStructuredModel model = mEditor.getModelForRead();
            try {
                IStructuredDocument doc = model.getStructuredDocument();
                if (doc != null) {
                    List<TextEdit> replaceIds = replaceIds(doc, mSelectionStart,
                            mSelectionEnd, rootId, id);
                    for (TextEdit edit : replaceIds) {
                        rootEdit.addChild(edit);
                    }
                }
            } finally {
                model.releaseFromRead();
            }
        }

        // Insert namespace elements?
        StringBuilder namespace = null;
        List<DeleteEdit> deletions = new ArrayList<DeleteEdit>();
        Element primary = getPrimaryElement();
        if (primary != null && getDomDocument().getDocumentElement() == primary) {
            namespace = new StringBuilder();

            List<Attr> declarations = findNamespaceAttributes(primary);
            for (Attr attribute : declarations) {
                if (attribute instanceof IndexedRegion) {
                    // Delete the namespace declaration in the node which is no longer the root
                    IndexedRegion region = (IndexedRegion) attribute;
                    int startOffset = region.getStartOffset();
                    int endOffset = region.getEndOffset();
                    String text = getText(startOffset, endOffset);
                    DeleteEdit deletion = new DeleteEdit(startOffset, endOffset - startOffset);
                    deletions.add(deletion);
                    rootEdit.addChild(deletion);
                    text = text.trim();

                    // Insert the namespace declaration in the new root
                    if (separateAttributes) {
                        namespace.append('\n').append(startIndent).append(indentUnit);
                    } else {
                        namespace.append(' ');
                    }
                    namespace.append(text);
                }
            }
        }

        // Insert begin tag: <type ...>
        StringBuilder sb = new StringBuilder();
        sb.append('<');
        sb.append(viewClass);

        if (namespace != null) {
            sb.append(namespace);
        }

        String androidNsPrefix = getAndroidNamespacePrefix();

        // Set the ID if any
        if (id != null) {
            if (separateAttributes) {
                sb.append('\n').append(startIndent).append(indentUnit);
            } else {
                sb.append(' ');
            }
            sb.append(androidNsPrefix).append(':');
            sb.append(ATTR_ID).append('=').append('"').append(id).append('"');
        }

        // If any of the elements are fill/match parent, use that instead
        String width = VALUE_WRAP_CONTENT;
        String height = VALUE_WRAP_CONTENT;

        for (Element element : getElements()) {
            String oldWidth = element.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_WIDTH);
            String oldHeight = element.getAttributeNS(ANDROID_URI, ATTR_LAYOUT_HEIGHT);

            if (VALUE_MATCH_PARENT.equals(oldWidth) || VALUE_FILL_PARENT.equals(oldWidth)) {
                width = oldWidth;
            }
            if (VALUE_MATCH_PARENT.equals(oldHeight) || VALUE_FILL_PARENT.equals(oldHeight)) {
                height = oldHeight;
            }
        }

        // Add in width/height.
        if (separateAttributes) {
            sb.append('\n').append(startIndent).append(indentUnit);
        } else {
            sb.append(' ');
        }
        sb.append(androidNsPrefix).append(':');
        sb.append(ATTR_LAYOUT_WIDTH).append('=').append('"').append(width).append('"');

        if (separateAttributes) {
            sb.append('\n').append(startIndent).append(indentUnit);
        } else {
            sb.append(' ');
        }
        sb.append(androidNsPrefix).append(':');
        sb.append(ATTR_LAYOUT_HEIGHT).append('=').append('"').append(height).append('"');

        // Transfer layout_ attributes (other than width and height)
        if (mUpdateReferences) {
            List<Attr> layoutAttributes = findLayoutAttributes(primary);
            for (Attr attribute : layoutAttributes) {
                String name = attribute.getLocalName();
                if ((name.equals(ATTR_LAYOUT_WIDTH) || name.equals(ATTR_LAYOUT_HEIGHT))
                        && ANDROID_URI.equals(attribute.getNamespaceURI())) {
                    // Already handled specially
                    continue;
                }

                if (attribute instanceof IndexedRegion) {
                    IndexedRegion region = (IndexedRegion) attribute;
                    int startOffset = region.getStartOffset();
                    int endOffset = region.getEndOffset();
                    String text = getText(startOffset, endOffset);
                    DeleteEdit deletion = new DeleteEdit(startOffset, endOffset - startOffset);
                    rootEdit.addChild(deletion);
                    deletions.add(deletion);

                    if (separateAttributes) {
                        sb.append('\n').append(startIndent).append(indentUnit);
                    } else {
                        sb.append(' ');
                    }
                    sb.append(text.trim());
                }
            }
        }

        // Finish open tag:
        sb.append('>');
        sb.append('\n').append(startIndent).append(indentUnit);

        InsertEdit beginEdit = new InsertEdit(mSelectionStart, sb.toString());
        rootEdit.addChild(beginEdit);

        String nested = getText(mSelectionStart, mSelectionEnd);
        int index = 0;
        while (index != -1) {
            index = nested.indexOf('\n', index);
            if (index != -1) {
                index++;
                InsertEdit newline = new InsertEdit(mSelectionStart + index, indentUnit);
                // Some of the deleted namespaces may have had newlines - be careful
                // not to overlap edits
                boolean covered = false;
                for (DeleteEdit deletion : deletions) {
                    if (deletion.covers(newline)) {
                        covered = true;
                        break;
                    }
                }
                if (!covered) {
                    rootEdit.addChild(newline);
                }
            }
        }

        // Insert end tag: </type>
        sb.setLength(0);
        sb.append('\n').append(startIndent);
        sb.append('<').append('/').append(viewClass).append('>');
        InsertEdit endEdit = new InsertEdit(mSelectionEnd, sb.toString());
        rootEdit.addChild(endEdit);

        changes.add(change);
        return changes;
    }

    public static class Descriptor extends VisualRefactoringDescriptor {
        public Descriptor(String project, String description, String comment,
                Map<String, String> arguments) {
            super("com.android.ide.eclipse.adt.refactoring.wrapin", //$NON-NLS-1$
                    project, description, comment, arguments);
        }

        @Override
        protected Refactoring createRefactoring(Map<String, String> args) {
            return new WrapInRefactoring(args);
        }
    }
}
