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
import static com.android.ide.common.layout.LayoutConstants.ANDROID_WIDGET_PREFIX;
import static com.android.ide.common.layout.LayoutConstants.ATTR_ID;
import static com.android.ide.common.layout.LayoutConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.ide.common.layout.LayoutConstants.ATTR_LAYOUT_PREFIX;
import static com.android.ide.common.layout.LayoutConstants.ATTR_LAYOUT_WIDTH;
import static com.android.ide.common.layout.LayoutConstants.ID_PREFIX;
import static com.android.ide.common.layout.LayoutConstants.NEW_ID_PREFIX;
import static com.android.ide.eclipse.adt.internal.editors.descriptors.XmlnsAttributeDescriptor.XMLNS;
import static com.android.ide.eclipse.adt.internal.editors.descriptors.XmlnsAttributeDescriptor.XMLNS_COLON;

import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.internal.editors.AndroidXmlEditor;
import com.android.ide.eclipse.adt.internal.editors.layout.LayoutEditor;
import com.android.ide.eclipse.adt.internal.editors.layout.configuration.ConfigurationComposite;
import com.android.ide.eclipse.adt.internal.editors.layout.descriptors.LayoutDescriptors;
import com.android.ide.eclipse.adt.internal.editors.layout.gle2.CanvasViewInfo;
import com.android.ide.eclipse.adt.internal.editors.layout.gle2.DomUtilities;
import com.android.ide.eclipse.adt.internal.editors.layout.gle2.GraphicalEditorPart;
import com.android.ide.eclipse.adt.internal.editors.layout.uimodel.UiViewElementNode;
import com.android.ide.eclipse.adt.internal.editors.uimodel.UiElementNode;
import com.android.ide.eclipse.adt.internal.preferences.AdtPrefs;
import com.android.util.Pair;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ITreeSelection;
import org.eclipse.jface.viewers.TreePath;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.ChangeDescriptor;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringChangeDescriptor;
import org.eclipse.ltk.core.refactoring.RefactoringDescriptor;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.text.edits.DeleteEdit;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.ide.IDE;
import org.eclipse.wst.sse.core.internal.provisional.IndexedRegion;
import org.eclipse.wst.sse.core.internal.provisional.text.IStructuredDocument;
import org.eclipse.wst.sse.core.internal.provisional.text.IStructuredDocumentRegion;
import org.eclipse.wst.sse.core.internal.provisional.text.ITextRegion;
import org.eclipse.wst.sse.core.internal.provisional.text.ITextRegionList;
import org.eclipse.wst.xml.core.internal.regions.DOMRegionContext;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parent class for the various visual refactoring operations; contains shared
 * implementations needed by most of them
 */
@SuppressWarnings("restriction") // XML model
public abstract class VisualRefactoring extends Refactoring {
    protected static final String KEY_FILE = "file";                      //$NON-NLS-1$
    protected static final String KEY_PROJECT = "proj";                   //$NON-NLS-1$
    protected static final String KEY_SEL_START = "sel-start";            //$NON-NLS-1$
    protected static final String KEY_SEL_END = "sel-end";                //$NON-NLS-1$

    protected IFile mFile;
    protected LayoutEditor mEditor;
    protected IProject mProject;
    protected int mSelectionStart = -1;
    protected int mSelectionEnd = -1;
    protected List<Element> mElements = null;
    protected ITreeSelection mTreeSelection;
    protected ITextSelection mSelection;
    protected List<Change> mChanges;
    private String mAndroidNamespacePrefix;

    /**
     * This constructor is solely used by {@link VisualRefactoringDescriptor},
     * to replay a previous refactoring.
     * @param arguments argument map created by #createArgumentMap.
     */
    VisualRefactoring(Map<String, String> arguments) {
        IPath path = Path.fromPortableString(arguments.get(KEY_PROJECT));
        mProject = (IProject) ResourcesPlugin.getWorkspace().getRoot().findMember(path);
        path = Path.fromPortableString(arguments.get(KEY_FILE));
        mFile = (IFile) ResourcesPlugin.getWorkspace().getRoot().findMember(path);
        mSelectionStart = Integer.parseInt(arguments.get(KEY_SEL_START));
        mSelectionEnd = Integer.parseInt(arguments.get(KEY_SEL_END));
        mEditor = null;
    }

    public VisualRefactoring(IFile file, LayoutEditor editor, ITextSelection selection,
            ITreeSelection treeSelection) {
        mFile = file;
        mEditor = editor;
        mProject = file.getProject();
        mSelection = selection;
        mTreeSelection = treeSelection;

        // Initialize mSelectionStart and mSelectionEnd based on the selection context, which
        // is either a treeSelection (when invoked from the layout editor or the outline), or
        // a selection (when invoked from an XML editor)
        if (treeSelection != null) {
            int end = Integer.MIN_VALUE;
            int start = Integer.MAX_VALUE;
            for (TreePath path : treeSelection.getPaths()) {
                Object lastSegment = path.getLastSegment();
                if (lastSegment instanceof CanvasViewInfo) {
                    CanvasViewInfo viewInfo = (CanvasViewInfo) lastSegment;
                    UiViewElementNode uiNode = viewInfo.getUiViewNode();
                    if (uiNode == null) {
                        continue;
                    }
                    Node xmlNode = uiNode.getXmlNode();
                    if (xmlNode instanceof IndexedRegion) {
                        IndexedRegion region = (IndexedRegion) xmlNode;

                        start = Math.min(start, region.getStartOffset());
                        end = Math.max(end, region.getEndOffset());
                    }
                }
            }
            if (start >= 0) {
                mSelectionStart = start;
                mSelectionEnd = end;
            }
        } else if (selection != null) {
            // TODO: update selection to boundaries!
            mSelectionStart = selection.getOffset();
            mSelectionEnd = mSelectionStart + selection.getLength();
        }
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

                List<CanvasViewInfo> infos = new ArrayList<CanvasViewInfo>();
                for (TreePath path : mTreeSelection.getPaths()) {
                    Object lastSegment = path.getLastSegment();
                    if (lastSegment instanceof CanvasViewInfo) {
                        infos.add((CanvasViewInfo) lastSegment);
                    }
                }

                if (infos.size() == 0) {
                    status.addFatalError("No selection to extract");
                    return status;
                }

                // Can't extract the root -- wouldn't that be pointless? (or maybe not
                // always)
                for (CanvasViewInfo info : infos) {
                    if (info.isRoot()) {
                        status.addFatalError("Cannot refactor the root");
                        return status;
                    }
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
                if (infos.size() > 1) {
                    // All elements must be siblings (e.g. same parent)
                    List<UiViewElementNode> nodes = new ArrayList<UiViewElementNode>(infos
                            .size());
                    for (CanvasViewInfo info : infos) {
                        UiViewElementNode node = info.getUiViewNode();
                        if (node != null) {
                            nodes.add(node);
                        }
                    }
                    if (nodes.size() == 0) {
                        status.addFatalError("No selected views");
                        return status;
                    }

                    UiElementNode parent = nodes.get(0).getUiParent();
                    for (UiViewElementNode node : nodes) {
                        if (parent != node.getUiParent()) {
                            status.addFatalError("The selected elements must be adjacent");
                            return status;
                        }
                    }
                    // Ensure that the siblings are contiguous; no gaps.
                    // If we've selected all the children of the parent then we don't need
                    // to look.
                    List<UiElementNode> siblings = parent.getUiChildren();
                    if (siblings.size() != nodes.size()) {
                        Set<UiViewElementNode> nodeSet = new HashSet<UiViewElementNode>(nodes);
                        boolean inRange = false;
                        int remaining = nodes.size();
                        for (UiElementNode node : siblings) {
                            boolean in = nodeSet.contains(node);
                            if (in) {
                                remaining--;
                                if (remaining == 0) {
                                    break;
                                }
                                inRange = true;
                            } else if (inRange) {
                                status.addFatalError("The selected elements must be adjacent");
                                return status;
                            }
                        }
                    }
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

    protected abstract List<Change> computeChanges();

    @Override
    public RefactoringStatus checkFinalConditions(IProgressMonitor monitor) throws CoreException,
            OperationCanceledException {
        RefactoringStatus status = new RefactoringStatus();
        mChanges = new ArrayList<Change>();
        try {
            monitor.beginTask("Checking post-conditions...", 5);
            List<Change> changes = computeChanges();
            mChanges.addAll(changes);

            monitor.worked(1);
        } finally {
            monitor.done();
        }

        return status;
    }

    @Override
    public Change createChange(IProgressMonitor monitor) throws CoreException,
            OperationCanceledException {
        try {
            monitor.beginTask("Applying changes...", 1);

            CompositeChange change = new CompositeChange(
                    getName(),
                    mChanges.toArray(new Change[mChanges.size()])) {
                @Override
                public ChangeDescriptor getDescriptor() {
                    VisualRefactoringDescriptor desc = createDescriptor();
                    return new RefactoringChangeDescriptor(desc);
                }
            };

            monitor.worked(1);
            return change;

        } finally {
            monitor.done();
        }
    }

    protected abstract VisualRefactoringDescriptor createDescriptor();

    protected Map<String, String> createArgumentMap() {
        HashMap<String, String> args = new HashMap<String, String>();
        args.put(KEY_PROJECT, mProject.getFullPath().toPortableString());
        args.put(KEY_FILE, mFile.getFullPath().toPortableString());
        args.put(KEY_SEL_START, Integer.toString(mSelectionStart));
        args.put(KEY_SEL_END, Integer.toString(mSelectionEnd));

        return args;
    }

    // ---- Shared functionality ----


    protected void openFile(IFile file) {
        GraphicalEditorPart graphicalEditor = mEditor.getGraphicalEditor();
        IFile leavingFile = graphicalEditor.getEditedFile();

        try {
            // Duplicate the current state into the newly created file
            QualifiedName qname = ConfigurationComposite.NAME_CONFIG_STATE;
            String state = AdtPlugin.getFileProperty(leavingFile, qname);

            // TODO: Look for a ".NoTitleBar.Fullscreen" theme version of the current
            // theme to show.

            file.setSessionProperty(GraphicalEditorPart.NAME_INITIAL_STATE, state);
        } catch (CoreException e) {
            // pass
        }

        /* TBD: "Show Included In" if supported.
         * Not sure if this is a good idea.
        if (graphicalEditor.renderingSupports(Capability.EMBEDDED_LAYOUT)) {
            try {
                Reference include = Reference.create(graphicalEditor.getEditedFile());
                file.setSessionProperty(GraphicalEditorPart.NAME_INCLUDE, include);
            } catch (CoreException e) {
                // pass - worst that can happen is that we don't start with inclusion
            }
        }
        */

        try {
            IEditorPart part = IDE.openEditor(mEditor.getEditorSite().getPage(), file);
            if (part instanceof AndroidXmlEditor && AdtPrefs.getPrefs().getFormatXml()) {
                AndroidXmlEditor newEditor = (AndroidXmlEditor) part;
                newEditor.reformatDocument();
            }
        } catch (PartInitException e) {
            AdtPlugin.log(e, "Can't open new included layout");
        }
    }


    /** Produce a list of edits to replace references to the given id with the given new id */
    protected List<TextEdit> replaceIds(IStructuredDocument doc, int skipStart, int skipEnd,
            String rootId, String referenceId) {
        if (rootId == null) {
            return Collections.emptyList();
        }

        // We need to search for either @+id/ or @id/
        String match1 = rootId;
        String match2;
        if (match1.startsWith(ID_PREFIX)) {
            match2 = '"' + NEW_ID_PREFIX + match1.substring(ID_PREFIX.length()) + '"';
            match1 = '"' + match1 + '"';
        } else if (match1.startsWith(NEW_ID_PREFIX)) {
            match2 = '"' + ID_PREFIX + match1.substring(NEW_ID_PREFIX.length()) + '"';
            match1 = '"' + match1 + '"';
        } else {
            return Collections.emptyList();
        }

        String namePrefix = getAndroidNamespacePrefix() + ':' + ATTR_LAYOUT_PREFIX;
        List<TextEdit> edits = new ArrayList<TextEdit>();

        IStructuredDocumentRegion region = doc.getFirstStructuredDocumentRegion();
        for (; region != null; region = region.getNext()) {
            ITextRegionList list = region.getRegions();
            int regionStart = region.getStart();

            // Look at all attribute values and look for an id reference match
            String attributeName = ""; //$NON-NLS-1$
            for (int j = 0; j < region.getNumberOfRegions(); j++) {
                ITextRegion subRegion = list.get(j);
                String type = subRegion.getType();
                if (DOMRegionContext.XML_TAG_ATTRIBUTE_NAME.equals(type)) {
                    attributeName = region.getText(subRegion);
                } else if (DOMRegionContext.XML_TAG_ATTRIBUTE_VALUE.equals(type)) {
                    // Only replace references in layout attributes
                    if (!attributeName.startsWith(namePrefix)) {
                        continue;
                    }
                    // Skip occurrences in the given skip range
                    int subRegionStart = regionStart + subRegion.getStart();
                    if (subRegionStart >= skipStart && subRegionStart <= skipEnd) {
                        continue;
                    }

                    String attributeValue = region.getText(subRegion);
                    if (attributeValue.equals(match1) || attributeValue.equals(match2)) {
                        int start = subRegionStart + 1; // skip quote
                        int end = start + rootId.length();

                        edits.add(new ReplaceEdit(start, end - start, referenceId));
                    }
                }
            }
        }

        return edits;
    }

    /** Get the id of the root selected element, if any */
    protected String getRootId() {
        Element primary = getPrimaryElement();
        if (primary != null) {
            String oldId = primary.getAttributeNS(ANDROID_URI, ATTR_ID);
            // id null check for https://bugs.eclipse.org/bugs/show_bug.cgi?id=272378
            if (oldId != null && oldId.length() > 0) {
                return oldId;
            }
        }

        return null;
    }

    protected String getAndroidNamespacePrefix() {
        if (mAndroidNamespacePrefix == null) {
            List<Attr> attributeNodes = findNamespaceAttributes();
            for (Node attributeNode : attributeNodes) {
                String prefix = attributeNode.getPrefix();
                if (XMLNS.equals(prefix)) {
                    String name = attributeNode.getNodeName();
                    String value = attributeNode.getNodeValue();
                    if (value.equals(ANDROID_URI)) {
                        mAndroidNamespacePrefix = name;
                        if (mAndroidNamespacePrefix.startsWith(XMLNS_COLON)) {
                            mAndroidNamespacePrefix =
                                mAndroidNamespacePrefix.substring(XMLNS_COLON.length());
                        }
                    }
                }
            }

            if (mAndroidNamespacePrefix == null) {
                mAndroidNamespacePrefix = ANDROID_NS_PREFIX;
            }
        }

        return mAndroidNamespacePrefix;
    }

    protected List<Attr> findNamespaceAttributes() {
        Document document = getDomDocument();
        if (document != null) {
            Element root = document.getDocumentElement();
            return findNamespaceAttributes(root);
        }

        return Collections.emptyList();
    }

    protected List<Attr> findNamespaceAttributes(Node root) {
        List<Attr> result = new ArrayList<Attr>();
        NamedNodeMap attributes = root.getAttributes();
        for (int i = 0, n = attributes.getLength(); i < n; i++) {
            Node attributeNode = attributes.item(i);

            String prefix = attributeNode.getPrefix();
            if (XMLNS.equals(prefix)) {
                result.add((Attr) attributeNode);
            }
        }

        return result;
    }

    protected List<Attr> findLayoutAttributes(Node root) {
        List<Attr> result = new ArrayList<Attr>();
        NamedNodeMap attributes = root.getAttributes();
        for (int i = 0, n = attributes.getLength(); i < n; i++) {
            Node attributeNode = attributes.item(i);

            String name = attributeNode.getLocalName();
            if (name.startsWith(ATTR_LAYOUT_PREFIX)
                    && ANDROID_URI.equals(attributeNode.getNamespaceURI())) {
                result.add((Attr) attributeNode);
            }
        }

        return result;
    }

    protected String insertNamespace(String xmlText, String namespaceDeclarations) {
        // Insert namespace declarations into the extracted XML fragment
        int firstSpace = xmlText.indexOf(' ');
        int elementEnd = xmlText.indexOf('>');
        int insertAt;
        if (firstSpace != -1 && firstSpace < elementEnd) {
            insertAt = firstSpace;
        } else {
            insertAt = elementEnd;
        }
        xmlText = xmlText.substring(0, insertAt) + namespaceDeclarations
                + xmlText.substring(insertAt);

        return xmlText;
    }

    /** Remove sections of the document that correspond to top level layout attributes;
     * these are placed on the include element instead */
    protected String stripTopLayoutAttributes(Element primary, int start, String xml) {
        if (primary != null) {
            // List of attributes to remove
            List<IndexedRegion> skip = new ArrayList<IndexedRegion>();
            NamedNodeMap attributes = primary.getAttributes();
            for (int i = 0, n = attributes.getLength(); i < n; i++) {
                Node attr = attributes.item(i);
                String name = attr.getLocalName();
                if (name.startsWith(ATTR_LAYOUT_PREFIX)
                        && ANDROID_URI.equals(attr.getNamespaceURI())) {
                    if (name.equals(ATTR_LAYOUT_WIDTH) || name.equals(ATTR_LAYOUT_HEIGHT)) {
                        // These are special and are left in
                        continue;
                    }

                    if (attr instanceof IndexedRegion) {
                        skip.add((IndexedRegion) attr);
                    }
                }
            }
            if (skip.size() > 0) {
                Collections.sort(skip, new Comparator<IndexedRegion>() {
                    // Sort in start order
                    public int compare(IndexedRegion r1, IndexedRegion r2) {
                        return r1.getStartOffset() - r2.getStartOffset();
                    }
                });

                // Successively cut out the various layout attributes
                // TODO remove adjacent whitespace too (but not newlines, unless they
                // are newly adjacent)
                StringBuilder sb = new StringBuilder(xml.length());
                int nextStart = 0;

                // Copy out all the sections except the skip sections
                for (IndexedRegion r : skip) {
                    int regionStart = r.getStartOffset();
                    // Adjust to string offsets since we've copied the string out of
                    // the document
                    regionStart -= start;

                    sb.append(xml.substring(nextStart, regionStart));

                    nextStart = regionStart + r.getLength();
                }
                if (nextStart < xml.length()) {
                    sb.append(xml.substring(nextStart));
                }

                return sb.toString();
            }
        }

        return xml;
    }

    protected static String getIndent(String line, int max) {
        int i = 0;
        int n = Math.min(max, line.length());
        for (; i < n; i++) {
            char c = line.charAt(i);
            if (!Character.isWhitespace(c)) {
                return line.substring(0, i);
            }
        }

        if (n < line.length()) {
            return line.substring(0, n);
        } else {
            return line;
        }
    }

    protected static String dedent(String xml) {
        String[] lines = xml.split("\n"); //$NON-NLS-1$
        if (lines.length < 2) {
            // The first line never has any indentation since we copy it out from the
            // element start index
            return xml;
        }

        String indentPrefix = getIndent(lines[1], lines[1].length());
        for (int i = 2, n = lines.length; i < n; i++) {
            String line = lines[i];

            // Ignore blank lines
            if (line.trim().length() == 0) {
                continue;
            }

            indentPrefix = getIndent(line, indentPrefix.length());

            if (indentPrefix.length() == 0) {
                return xml;
            }
        }

        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            if (line.startsWith(indentPrefix)) {
                sb.append(line.substring(indentPrefix.length()));
            } else {
                sb.append(line);
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    protected String getText(int start, int end) {
        try {
            IStructuredDocument document = mEditor.getStructuredDocument();
            return document.get(start, end - start);
        } catch (BadLocationException e) {
            // the region offset was invalid. ignore.
            return null;
        }
    }

    protected List<Element> getElements() {
        if (mElements == null) {
            List<Element> nodes = new ArrayList<Element>();

            AndroidXmlEditor editor = mEditor;
            IStructuredDocument doc = editor.getStructuredDocument();
            Pair<Element, Element> range = DomUtilities.getElementRange(doc,
                    mSelectionStart, mSelectionEnd);
            if (range != null) {
                Element first = range.getFirst();
                Element last = range.getSecond();

                if (first == last) {
                    nodes.add(first);
                } else if (first.getParentNode() == last.getParentNode()) {
                    // Add the range
                    Node node = first;
                    while (node != null) {
                        if (node instanceof Element) {
                            nodes.add((Element) node);
                        }
                        if (node == last) {
                            break;
                        }
                        node = node.getNextSibling();
                    }
                } else {
                    // Different parents: this means we have an uneven selection, selecting
                    // elements from different levels. We can't extract ranges like that.
                }
            }
            mElements = nodes;
        }

        return mElements;
    }

    protected Element getPrimaryElement() {
        List<Element> elements = getElements();
        if (elements != null && elements.size() == 1) {
            return elements.get(0);
        }

        return null;
    }

    protected Document getDomDocument() {
        return mEditor.getUiRootNode().getXmlDocument();
    }

    protected List<CanvasViewInfo> getSelectedViewInfos() {
        List<CanvasViewInfo> infos = new ArrayList<CanvasViewInfo>();
        if (mTreeSelection != null) {
            for (TreePath path : mTreeSelection.getPaths()) {
                Object lastSegment = path.getLastSegment();
                if (lastSegment instanceof CanvasViewInfo) {
                    infos.add((CanvasViewInfo) lastSegment);
                }
            }
        }
        return infos;
    }

    protected boolean validateNotEmpty(List<CanvasViewInfo> infos, RefactoringStatus status) {
        if (infos.size() == 0) {
            status.addFatalError("No selection to extract");
            return false;
        }

        return true;
    }

    protected boolean validateNotRoot(List<CanvasViewInfo> infos, RefactoringStatus status) {
        for (CanvasViewInfo info : infos) {
            if (info.isRoot()) {
                status.addFatalError("Cannot refactor the root");
                return false;
            }
        }

        return true;
    }

    protected boolean validateContiguous(List<CanvasViewInfo> infos, RefactoringStatus status) {
        if (infos.size() > 1) {
            // All elements must be siblings (e.g. same parent)
            List<UiViewElementNode> nodes = new ArrayList<UiViewElementNode>(infos
                    .size());
            for (CanvasViewInfo info : infos) {
                UiViewElementNode node = info.getUiViewNode();
                if (node != null) {
                    nodes.add(node);
                }
            }
            if (nodes.size() == 0) {
                status.addFatalError("No selected views");
                return false;
            }

            UiElementNode parent = nodes.get(0).getUiParent();
            for (UiViewElementNode node : nodes) {
                if (parent != node.getUiParent()) {
                    status.addFatalError("The selected elements must be adjacent");
                    return false;
                }
            }
            // Ensure that the siblings are contiguous; no gaps.
            // If we've selected all the children of the parent then we don't need
            // to look.
            List<UiElementNode> siblings = parent.getUiChildren();
            if (siblings.size() != nodes.size()) {
                Set<UiViewElementNode> nodeSet = new HashSet<UiViewElementNode>(nodes);
                boolean inRange = false;
                int remaining = nodes.size();
                for (UiElementNode node : siblings) {
                    boolean in = nodeSet.contains(node);
                    if (in) {
                        remaining--;
                        if (remaining == 0) {
                            break;
                        }
                        inRange = true;
                    } else if (inRange) {
                        status.addFatalError("The selected elements must be adjacent");
                        return false;
                    }
                }
            }
        }

        return true;
    }

    protected IndexedRegion getRegion(Node node) {
        if (node instanceof IndexedRegion) {
            return (IndexedRegion) node;
        }

        return null;
    }

    protected String ensureHasId(MultiTextEdit rootEdit, Element element) {
        if (!element.hasAttributeNS(ANDROID_URI, ATTR_ID)) {
            String id = DomUtilities.getFreeWidgetId(element);
            id = NEW_ID_PREFIX + id;
            addAttributeDeclaration(rootEdit, element, getAndroidNamespacePrefix(), ATTR_ID, id);
            return id;
        }

        return getId(element);
    }

    protected int getFirstAttributeOffset(Element element) {
        IndexedRegion region = getRegion(element);
        if (region != null) {
            int startOffset = region.getStartOffset();
            int endOffset = region.getEndOffset();
            String text = getText(startOffset, endOffset);
            String name = element.getLocalName();
            int nameOffset = text.indexOf(name);
            if (nameOffset != -1) {
                return startOffset + nameOffset + name.length();
            }
        }

        return -1;
    }

    protected static String getId(Element element) {
        return element.getAttributeNS(ANDROID_URI, ATTR_ID);
    }

    protected String ensureNewId(String id) {
        if (id != null && id.length() > 0) {
            if (id.startsWith(ID_PREFIX)) {
                id = NEW_ID_PREFIX + id.substring(ID_PREFIX.length());
            } else if (!id.startsWith(NEW_ID_PREFIX)) {
                id = NEW_ID_PREFIX + id;
            }
        } else {
            id = null;
        }

        return id;
    }

    protected String getViewClass(String fqcn) {
        // Don't include android.widget. as a package prefix in layout files
        if (fqcn.startsWith(ANDROID_WIDGET_PREFIX)) {
            fqcn = fqcn.substring(ANDROID_WIDGET_PREFIX.length());
        }

        return fqcn;
    }

    protected void addAttributeDeclaration(MultiTextEdit rootEdit, Element element,
            String attributePrefix, String attributeName, String attributeValue) {
        int offset = getFirstAttributeOffset(element);
        if (offset != -1) {
            addAttributeDeclaration(rootEdit, offset, attributePrefix, attributeName,
                    attributeValue);
        }
    }

    protected void addAttributeDeclaration(MultiTextEdit rootEdit, int offset,
            String attributePrefix, String attributeName, String attributeValue) {
        StringBuilder sb = new StringBuilder();
        sb.append(' ').append(attributePrefix).append(':');
        sb.append(attributeName).append('=').append('"');
        sb.append(attributeValue).append('"');

        InsertEdit setAttribute = new InsertEdit(offset, sb.toString());
        rootEdit.addChild(setAttribute);
    }

    /** Strips out the given attribute, if defined */
    protected void removeAttribute(MultiTextEdit rootEdit, Element element, String uri,
            String attributeName) {
        if (element.hasAttributeNS(uri, attributeName)) {
            Attr attribute = element.getAttributeNodeNS(uri, attributeName);
            IndexedRegion region = getRegion(attribute);
            if (region != null) {
                int startOffset = region.getStartOffset();
                int endOffset = region.getEndOffset();
                DeleteEdit deletion = new DeleteEdit(startOffset, endOffset - startOffset);
                rootEdit.addChild(deletion);
            }
        }
    }

    public abstract static class VisualRefactoringDescriptor extends RefactoringDescriptor {
        private final Map<String, String> mArguments;

        public VisualRefactoringDescriptor(
                String id, String project, String description, String comment,
                Map<String, String> arguments) {
            super(id, project, description, comment, STRUCTURAL_CHANGE | MULTI_CHANGE);
            mArguments = arguments;
        }

        public Map<String, String> getArguments() {
            return mArguments;
        }

        protected abstract Refactoring createRefactoring(Map<String, String> args);

        @Override
        public Refactoring createRefactoring(RefactoringStatus status) throws CoreException {
            try {
                return createRefactoring(mArguments);
            } catch (NullPointerException e) {
                status.addFatalError("Failed to recreate refactoring from descriptor");
                return null;
            }
        }
    }
}
