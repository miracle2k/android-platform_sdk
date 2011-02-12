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

package com.android.ide.eclipse.adt.internal.editors.layout.gle2;

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
import static com.android.ide.eclipse.adt.AndroidConstants.WS_SEP;
import static com.android.ide.eclipse.adt.internal.editors.descriptors.XmlnsAttributeDescriptor.XMLNS;
import static com.android.ide.eclipse.adt.internal.editors.descriptors.XmlnsAttributeDescriptor.XMLNS_COLON;
import static com.android.resources.ResourceType.LAYOUT;

import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.internal.editors.AndroidXmlEditor;
import com.android.ide.eclipse.adt.internal.editors.descriptors.DescriptorsUtils;
import com.android.ide.eclipse.adt.internal.editors.layout.LayoutEditor;
import com.android.ide.eclipse.adt.internal.editors.layout.configuration.ConfigurationComposite;
import com.android.ide.eclipse.adt.internal.editors.layout.descriptors.LayoutDescriptors;
import com.android.ide.eclipse.adt.internal.editors.layout.uimodel.UiViewElementNode;
import com.android.ide.eclipse.adt.internal.editors.uimodel.UiElementNode;
import com.android.ide.eclipse.adt.internal.preferences.AdtPrefs;
import com.android.ide.eclipse.adt.internal.wizards.newxmlfile.ResourceNameValidator;
import com.android.util.Pair;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.window.Window;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.ide.IDE;
import org.eclipse.wst.sse.core.internal.provisional.IStructuredModel;
import org.eclipse.wst.sse.core.internal.provisional.IndexedRegion;
import org.eclipse.wst.sse.core.internal.provisional.text.IStructuredDocument;
import org.eclipse.wst.sse.core.internal.provisional.text.IStructuredDocumentRegion;
import org.eclipse.wst.sse.core.internal.provisional.text.ITextRegion;
import org.eclipse.wst.sse.core.internal.provisional.text.ITextRegionList;
import org.eclipse.wst.xml.core.internal.regions.DOMRegionContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Extracts the selection and writes it out as a separate layout file, then adds an
 * include to that new layout file. Interactively asks the user for a new name for the
 * layout.
 */
@SuppressWarnings("restriction") // For XML model
public class ExtractIncludeAction extends Action {
    private LayoutCanvas mCanvas;

    public ExtractIncludeAction(LayoutCanvas canvas) {
        super("Extract as Include...", IAction.AS_PUSH_BUTTON);
        mCanvas = canvas;
    }

    @Override
    public boolean isEnabled() {
        List<SelectionItem> selection = mCanvas.getSelectionManager().getSelections();
        if (selection.size() == 0) {
            return false;
        }

        // Can't extract the root -- wouldn't that be pointless? (or maybe not always)
        for (SelectionItem item : selection) {
            if (item.isRoot()) {
                return false;
            }
        }

        // Disable if you've selected a single include tag
        if (selection.size() == 1) {
            UiViewElementNode uiNode = selection.get(0).getViewInfo().getUiViewNode();
            if (uiNode != null) {
                Node xmlNode = uiNode.getXmlNode();
                if (xmlNode.getLocalName().equals(LayoutDescriptors.VIEW_INCLUDE)) {
                    return false;
                }
            }
        }

        // Enforce that the selection is -contiguous-
        if (selection.size() > 1) {
            // All elements must be siblings (e.g. same parent)
            List<UiViewElementNode> nodes = new ArrayList<UiViewElementNode>(selection.size());
            for (SelectionItem item : selection) {
                UiViewElementNode node = item.getViewInfo().getUiViewNode();
                if (node != null) {
                    nodes.add(node);
                }
            }
            if (nodes.size() == 0) {
                return false;
            }

            UiElementNode parent = nodes.get(0).getUiParent();
            for (UiViewElementNode node : nodes) {
                if (parent != node.getUiParent()) {
                    return false;
                }
            }
            // Ensure that the siblings are contiguous; no gaps.
            // If we've selected all the children of the parent then we don't need to look.
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
                        return false;
                    }
                }
            }
        }

        return true;
    }

    private String inputName() {
        IProject project = mCanvas.getLayoutEditor().getProject();
        IInputValidator validator = ResourceNameValidator.create(true, project, LAYOUT);

        String defaultName = ""; //$NON-NLS-1$
        Element primaryNode = getPrimaryNode();
        if (primaryNode != null) {
            String id = primaryNode.getAttributeNS(ANDROID_URI, ATTR_ID);
            // id null check for https://bugs.eclipse.org/bugs/show_bug.cgi?id=272378
            if (id != null && (id.startsWith(ID_PREFIX) || id.startsWith(NEW_ID_PREFIX))) {
                // Use everything following the id/, and make it lowercase since that is
                // the convention for layouts
                defaultName = id.substring(id.indexOf('/') + 1).toLowerCase();
                if (validator.isValid(defaultName) != null) { // Already exists?
                    defaultName = ""; //$NON-NLS-1$
                }
            }
        }

        InputDialog d = new InputDialog(AdtPlugin.getDisplay().getActiveShell(),
                "Extract As Include", // title
                "New Layout Name", defaultName, validator);

        if (d.open() != Window.OK) {
            return null;
        }

        return d.getValue().trim();
    }

    @Override
    public void run() {
        String newName = inputName();
        if (newName == null) {
            // User canceled
            return;
        }

        // Create extracted content
        // In order to ensure that we preserve as much of the user's original formatting
        // and attribute order as possible, we will just snip out the exact element ranges
        // from the current source editor and reindent them in the new file
        Pair<Integer, Integer> range = computeExtractRange();
        if (range == null) {
            return;
        }
        int start = range.getFirst();
        int end = range.getSecond();
        String extractedText = getExtractedText(start, end);

        Pair<String, String> namespace = computeNamespaces();
        String androidNsPrefix = namespace.getFirst();
        String namespaceDeclarations = namespace.getSecond();

        // Insert namespace:
        extractedText = insertNamespace(extractedText, namespaceDeclarations);

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"); //$NON-NLS-1$
        sb.append(extractedText);
        sb.append('\n');

        String newFileName = newName + DOT_XML;
        IProject project = mCanvas.getLayoutEditor().getProject();
        IContainer parent = mCanvas.getLayoutEditor().getInputFile().getParent();
        IPath parentPath = parent.getProjectRelativePath();
        IFile file = project.getFile(new Path(parentPath + WS_SEP + newFileName));

        writeFile(file, sb.toString());

        // Force refresh to pick up the newly available @layout/<newName>
        LayoutEditor editor = mCanvas.getLayoutEditor();
        editor.getGraphicalEditor().refreshProjectResources();

        String referenceId = getReferenceId();
        List<Edit> edits = new ArrayList<Edit>();

        // Replace existing elements in the source file and insert <include>
        String include = computeIncludeString(newName, androidNsPrefix, referenceId);
        edits.add(new Edit(start, end, include));

        // Update any layout references to the old id with the new id
        if (referenceId != null) {
            String rootId = getRootId();
            IStructuredModel model = editor.getModelForRead();
            try {
                IStructuredDocument doc = model.getStructuredDocument();
                if (doc != null) {
                    List<Edit> replaceIds = replaceIds(doc, start, end, rootId, referenceId);
                    edits.addAll(replaceIds);
                }
            } finally {
                model.releaseFromRead();
            }
        }

        // Open extracted file. This seems to trigger refreshing of ProjectResources
        // such that the @layout/<newName> reference from the new <include> we're adding
        // will work; without this we get file reference errors
        openFile(file);

        // Perform edits
        applyEdits("Extract As Include", edits);
    }

    /** Produce a list of edits to replace references to the given id with the given new id */
    private List<Edit> replaceIds(IStructuredDocument doc, int skipStart, int skipEnd,
            String rootId, String referenceId) {

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

        String namePrefix = ANDROID_NS_PREFIX + ':' + ATTR_LAYOUT_PREFIX;
        List<Edit> edits = new ArrayList<Edit>();

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
                        edits.add(new Edit(start, end, referenceId));
                    }
                }
            }
        }

        return edits;
    }

    /** Returns the id to be used for the include tag itself (may be null) */
    private String getReferenceId() {
        String rootId = getRootId();
        if (rootId != null) {
            return rootId + "_ref";
        }

        return null;
    }

    /** Get the id of the root selected element, if any */
    private String getRootId() {
        Element primaryNode = getPrimaryNode();
        if (primaryNode != null) {
            String oldId = primaryNode.getAttributeNS(ANDROID_URI, ATTR_ID);
            // id null check for https://bugs.eclipse.org/bugs/show_bug.cgi?id=272378
            if (oldId != null && oldId.length() > 0) {
                return oldId;
            }
        }

        return null;
    }

    private boolean writeFile(IFile file, String content) {
        // Write out the content into the new XML file
        try {
            byte[] buf = content.getBytes("UTF8"); //$NON-NLS-1$
            InputStream stream = new ByteArrayInputStream(buf);
            file.create(stream, true /* force */, null /* progress */);
            return true;
        } catch (Exception e) {
            String message = e.getMessage();
            String error = String.format("Failed to generate %1$s: %2$s", file.getName(), message);
            AdtPlugin.displayError("Extract As Include", error);
            return false;
        }
    }

    private Pair<String, String> computeNamespaces() {
        String androidNsPrefix = null;
        String namespaceDeclarations = null;

        Document document = getDocument();
        if (document != null) {
            StringBuilder sb = new StringBuilder();
            Element root = document.getDocumentElement();
            NamedNodeMap attributes = root.getAttributes();
            for (int i = 0, n = attributes.getLength(); i < n; i++) {
                Node attributeNode = attributes.item(i);

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
                    sb.append(DescriptorsUtils.toXmlAttributeValue(value));
                    sb.append('"');
                }
            }

            namespaceDeclarations = sb.toString();
        }

        if (androidNsPrefix == null) {
            androidNsPrefix = ANDROID_NS_PREFIX;
        }
        if (namespaceDeclarations == null) {
            StringBuilder sb = new StringBuilder();
            sb.append(' ');
            sb.append(XMLNS_COLON);
            sb.append(ANDROID_NS_PREFIX);
            sb.append('=').append('"');
            sb.append(ANDROID_URI);
            sb.append('"');
            namespaceDeclarations = sb.toString();
        }

        return Pair.of(androidNsPrefix, namespaceDeclarations);
    }

    private String insertNamespace(String xmlText, String namespaceDeclarations) {
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

    private void openFile(IFile file) {
        LayoutEditor editor = mCanvas.getLayoutEditor();
        GraphicalEditorPart graphicalEditor = editor.getGraphicalEditor();
        IFile leavingFile = graphicalEditor.getEditedFile();

        try {
            // Duplicate the current state into the newly created file
            QualifiedName qname = ConfigurationComposite.NAME_CONFIG_STATE;
            String state = AdtPlugin.getFileProperty(leavingFile, qname);
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
            IEditorPart part = IDE.openEditor(editor.getEditorSite().getPage(), file);
            if (part instanceof AndroidXmlEditor && AdtPrefs.getPrefs().getFormatXml()) {
                AndroidXmlEditor newEditor = (AndroidXmlEditor) part;
                newEditor.reformatDocument();
            }
        } catch (PartInitException e) {
            AdtPlugin.log(e, "Can't open new included layout");
        }
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
        // TODO: Use refactoring infrastructure to handle this part

        // I should move all the layout_ attributes as well
        // I also need to duplicate and modify the id and then replace
        // everything else in the file with this new id...

        // HACK: see issue 13494: We must duplicate the width/height attributes on the
        // <include> statement for designtime rendering only
        Element primaryNode = getPrimaryNode();
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
            sb.append(DescriptorsUtils.toXmlAttributeValue(width));
            sb.append('"');
        }
        if (height != null) {
            sb.append(' ');
            sb.append(androidNsPrefix);
            sb.append(':');
            sb.append(ATTR_LAYOUT_HEIGHT);
            sb.append('=').append('"');
            sb.append(DescriptorsUtils.toXmlAttributeValue(height));
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
                    sb.append(DescriptorsUtils.toXmlAttributeValue(attr.getNodeValue()));
                    sb.append('"');
                }
            }
        }

        sb.append("/>");
        return sb.toString();
    }

    /** Return the text in the document in the range start to end */
    private String getExtractedText(int start, int end) {
        LayoutEditor editor = mCanvas.getLayoutEditor();

        IStructuredModel model = editor.getModelForRead();
        try {
            IStructuredDocument document = editor.getStructuredDocument();
            String xml = document.get(start, end - start);
            Element primaryNode = getPrimaryNode();
            xml = stripTopLayoutAttributes(primaryNode, start, xml);
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
        } catch (BadLocationException e) {
            // the region offset was invalid. ignore.
            return null;
        } finally {
            model.releaseFromRead();
        }
    }

    /** Remove sections of the document that correspond to top level layout attributes;
     * these are placed on the include element instead */
    private String stripTopLayoutAttributes(Element primaryNode, int start, String xml) {
        if (primaryNode != null) {
            // List of attributes to remove
            //IndexedRegion attRegion = (IndexedRegion) attrs.item(i);
            List<IndexedRegion> skip = new ArrayList<IndexedRegion>();
            NamedNodeMap attributes = primaryNode.getAttributes();
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

    private static String getIndent(String line, int max) {
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

    private static String dedent(String xml) {
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

    private Element getPrimaryNode() {
        List<SelectionItem> selection = mCanvas.getSelectionManager().getSelections();
        if (selection.size() == 1) {
            UiViewElementNode node = selection.get(0).getViewInfo().getUiViewNode();
            if (node != null) {
                Node xmlNode = node.getXmlNode();
                if (xmlNode instanceof Element) {
                    return (Element) xmlNode;
                }
            }
        }

        return null;
    }

    private Document getDocument() {
        List<SelectionItem> selection = mCanvas.getSelectionManager().getSelections();
        for (SelectionItem item : selection) {
            UiViewElementNode node = item.getViewInfo().getUiViewNode();
            if (node != null) {
                Node xmlNode = node.getXmlNode();
                if (xmlNode != null) {
                    return xmlNode.getOwnerDocument();
                }
            }
        }

        return null;
    }

    private Pair<Integer, Integer> computeExtractRange() {
        List<SelectionItem> selection = mCanvas.getSelectionManager().getSelections();
        if (selection.size() == 0) {
            return null;
        }
        int end = Integer.MIN_VALUE;
        int start = Integer.MAX_VALUE;
        for (SelectionItem item : selection) {
            CanvasViewInfo viewInfo = item.getViewInfo();
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
        if (start < 0) {
            return null;
        }

        return Pair.of(start, end);
    }

    /** Apply edits into document under an undo lock */
    private void applyEdits(String label, final List<Edit> edits) {
        // Process the edits in reverse document position order to ensure
        // that the offsets aren't affected by other edits
        Collections.sort(edits);
        final LayoutEditor editor = mCanvas.getLayoutEditor();
        editor.wrapUndoEditXmlModel(label, new Runnable() {
            public void run() {
                IStructuredDocument document = editor.getStructuredDocument();
                if (document != null) {
                    try {
                        for (Edit edit : edits) {
                            edit.apply(document);
                        }
                    } catch (BadLocationException e) {
                        AdtPlugin.log(e, "Cannot insert <include> tag");
                        return;
                    }
                }
            }
        });

        // Save file to trigger include finder scanning (as well as making the
        // actual show-include feature work since it relies on reading files from
        // disk, not a live buffer)
        IEditorPart editorPart = editor;
        IWorkbenchPage page = editorPart.getEditorSite().getPage();
        page.saveEditor(editorPart, false);
    }

    /**
     * Edit operation (insert, delete, replace) at a given offset - a collection of these
     * can be sorted in reverse offset order such that they can be applied successively
     * without having to updated offsets
     * <p>
     * TODO: When rewriting this extract operation to the refactoring framework, use
     * refactoring framework's change list instead
     */
    private static class Edit implements Comparable<Edit> {
        private final int mStart;
        private final int mEnd;
        private final String mReplaceWith;

        public Edit(int start, int end, String replaceWith) {
            super();
            mStart = start;
            mEnd = end;
            mReplaceWith = replaceWith;
        }

        void apply(IStructuredDocument document) throws BadLocationException {
            document.replace(mStart, mEnd - mStart, mReplaceWith);
        }

        public int compareTo(Edit o) {
            // Sort in *reverse* offset order
            return o.mStart - mStart;
        }

        @Override
        public String toString() {
            return "Edit [start=" + mStart + ", end=" + mEnd + ", replaceWith=" + mReplaceWith
                    + "]";
        }
    }
}
