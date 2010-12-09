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

package com.android.ide.eclipse.adt.internal.editors.xml;

import static com.android.ide.common.layout.LayoutConstants.ANDROID_URI;
import static com.android.ide.common.layout.LayoutConstants.ATTR_CLASS;
import static com.android.ide.common.layout.LayoutConstants.VIEW;
import static com.android.ide.eclipse.adt.AndroidConstants.EXT_XML;
import static com.android.ide.eclipse.adt.AndroidConstants.WS_RESOURCES;
import static com.android.ide.eclipse.adt.AndroidConstants.WS_SEP;
import static com.android.ide.eclipse.adt.internal.editors.resources.descriptors.ResourcesDescriptors.NAME_ATTR;
import static com.android.ide.eclipse.adt.internal.editors.resources.descriptors.ResourcesDescriptors.ROOT_ELEMENT;
import static com.android.sdklib.xml.AndroidManifest.ATTRIBUTE_NAME;
import static com.android.sdklib.xml.AndroidManifest.ATTRIBUTE_PACKAGE;
import static com.android.sdklib.xml.AndroidManifest.NODE_ACTIVITY;
import static com.android.sdklib.xml.AndroidManifest.NODE_SERVICE;

import com.android.ide.common.layout.Pair;
import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.AndroidConstants;
import com.android.ide.eclipse.adt.internal.editors.AndroidXmlEditor;
import com.android.ide.eclipse.adt.internal.resources.ResourceType;
import com.android.ide.eclipse.adt.internal.resources.manager.FolderTypeRelationship;
import com.android.ide.eclipse.adt.internal.resources.manager.ProjectResources;
import com.android.ide.eclipse.adt.internal.resources.manager.ResourceFolder;
import com.android.ide.eclipse.adt.internal.resources.manager.ResourceFolderType;
import com.android.ide.eclipse.adt.internal.resources.manager.ResourceManager;
import com.android.ide.eclipse.adt.io.IFolderWrapper;
import com.android.sdklib.SdkConstants;
import com.android.sdklib.annotations.VisibleForTesting;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jface.action.IStatusLineManager;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.hyperlink.AbstractHyperlinkDetector;
import org.eclipse.jface.text.hyperlink.IHyperlink;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.eclipse.wst.sse.core.StructuredModelManager;
import org.eclipse.wst.sse.core.internal.encoding.util.Logger;
import org.eclipse.wst.sse.core.internal.provisional.IStructuredModel;
import org.eclipse.wst.sse.core.internal.provisional.IndexedRegion;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMModel;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Resolver to create hyperlinks in Android XML files to jump to associated resources --
 * activities, layouts, strings, etc
 */
@SuppressWarnings("restriction")
public class XmlHyperlinkResolver extends AbstractHyperlinkDetector {

    /** Regular expression matching a FQCN for a view class */
    @VisibleForTesting
    /* package */ static final Pattern CLASS_PATTERN = Pattern.compile(
        "(([a-zA-Z_\\$][a-zA-Z0-9_\\$]*)+\\.)+[a-zA-Z_\\$][a-zA-Z0-9_\\$]*"); // NON-NLS-1$

    /**
     * Determines whether the given node/attribute corresponds to a target we can link to.
     *
     * @param node the node surrounding the cursor
     * @param attribute the attribute surrounding the cursor
     * @return true if the given node/attribute pair is a link target
     */
    private boolean isLinkable(Node node, Attr attribute) {
        if (isClassReference(node, attribute)) {
            return true;
        }

        // Everything else here is attribute based
        if (attribute == null) {
            return false;
        }

        if (isActivity(node, attribute) || isService(node, attribute)) {
            return true;
        }

        Pair<ResourceType,String> resource = getResource(attribute.getValue());
        if (resource != null) {
            ResourceType type = resource.getFirst();
            if (type != null) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns true if this node/attribute pair corresponds to a manifest reference to
     * an activity.
     */
    private boolean isActivity(Node node, Attr attribute) {
        // Is this an <activity> or <service> in an AndroidManifest.xml file? If so, jump
        // to it
        String nodeName = node.getNodeName();
        if (NODE_ACTIVITY.equals(nodeName) && ATTRIBUTE_NAME.equals(attribute.getLocalName())
                && ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return true;
        }

        return false;
    }

    private boolean isClassReference(Node node, Attr attribute) {
        String tag = node.getNodeName();
        if (attribute != null && ATTR_CLASS.equals(attribute.getLocalName()) && VIEW.equals(tag)) {
            return true;
        }

        // If the element looks like a fully qualified class name (e.g. it's a custom view
        // element) offer it as a link
        if (tag.indexOf('.') != -1 && CLASS_PATTERN.matcher(tag).matches()) {
            return true;
        }

        return false;
    }

    private String getClassFqcn(Node node, Attr attribute) {
        String tag = node.getNodeName();
        if (attribute != null && ATTR_CLASS.equals(attribute.getLocalName()) && VIEW.equals(tag)) {
            return attribute.getValue();
        }

        if (tag.indexOf('.') != -1 && CLASS_PATTERN.matcher(tag).matches()) {
            return tag;
        }

        return null;
    }

    /**
     * Returns true if this node/attribute pair corresponds to a manifest reference to
     * an service.
     */
    private boolean isService(Node node, Attr attribute) {
        // Is this an <activity> or <service> in an AndroidManifest.xml file? If so, jump to it
        String nodeName = node.getNodeName();
        if (NODE_SERVICE.equals(nodeName) && ATTRIBUTE_NAME.equals(attribute.getLocalName())
                && ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return true;
        }

        return false;
    }

    /**
     * Returns the fully qualified class name of an activity referenced by the given
     * AndroidManifest.xml node
     */
    private String getActivityClassFqcn(Node node, Attr attribute) {
        StringBuilder sb = new StringBuilder();
        Element root = node.getOwnerDocument().getDocumentElement();
        String pkg = root.getAttribute(ATTRIBUTE_PACKAGE);
        sb.append(pkg);
        String className = attribute.getValue();
        sb.append(className);
        return sb.toString();
    }

    /**
     * Returns the fully qualified class name of a service referenced by the given
     * AndroidManifest.xml node
     */
    private String getServiceClassFqcn(Node node, Attr attribute) {
        // Same logic
        return getActivityClassFqcn(node, attribute);
    }

    /**
     * Is this a resource that can be defined in any file within the "values" folder?
     */
    private boolean isValueResource(ResourceType type) {
        ResourceFolderType[] folderTypes = FolderTypeRelationship.getRelatedFolders(type);
        for (ResourceFolderType folderType : folderTypes) {
            if (folderType == ResourceFolderType.VALUES) {
                return true;
            }
        }

        return false;
    }

    /**
     * Is this a resource that resides in a file whose name is determined by the
     * resource name?
     */
    private boolean isFileResource(ResourceType type) {
        ResourceFolderType[] folderTypes = FolderTypeRelationship.getRelatedFolders(type);
        for (ResourceFolderType folderType : folderTypes) {
            if (folderType != ResourceFolderType.VALUES) {
                return true;
            }
        }

        return false;
    }

    /**
     * Computes the actual exact location to jump to for a given node+attribute
     * combination. Can optionally return an offset range within a file to highlight.
     *
     * @param node surrounding node
     * @param attribute surrounding attribute
     * @return a pair of a file location and an optional (or null) region within that file
     */
    private boolean open(Node node, Attr attribute) {
        IProject project = getProject();
        if (project == null) {
            return false;
        }

        if (isActivity(node, attribute)) {
            String fqcn = getActivityClassFqcn(node, attribute);
            return openJavaClass(project, fqcn);
        } else if (isService(node, attribute)) {
            String fqcn = getServiceClassFqcn(node, attribute);
            return openJavaClass(project, fqcn);
        } else if (isClassReference(node, attribute)) {
            return openJavaClass(project, getClassFqcn(node, attribute));
        } else {
            Pair<ResourceType,String> resource = getResource(attribute.getValue());
            if (resource != null) {
                ResourceType type = resource.getFirst();
                if (type != null) {
                    String name = resource.getSecond();
                    IResource member = null;
                    IRegion region = null;

                    // Is this something found in a values/ folder?
                    if (isValueResource(type)) {
                        Pair<IFile,IRegion> def = findValueDefinition(project, type, name);
                        if (def != null) {
                            member = def.getFirst();
                            region = def.getSecond();
                        }
                    }

                    // Is this something found in a file identified by the name?
                    // (Some URLs can be both -- for example, a color can be both
                    // listed in an xml files in values/ as well as under /res/color/).
                    if (member == null && isFileResource(type)) {
                        // It's a single file resource, like @layout/foo; open
                        // layout/foo.xml
                        member = findMember(project, type, name);
                    }

                    try {
                        if (member != null && member instanceof IFile) {
                            IFile file = (IFile) member;
                            IEditorPart sourceEditor = getEditor();
                            IWorkbenchPage page = sourceEditor.getEditorSite().getPage();
                            IEditorPart targetEditor = IDE.openEditor(page, file, true);
                            if ((region != null) && (targetEditor instanceof AndroidXmlEditor)) {
                                ((AndroidXmlEditor) targetEditor).show(region.getOffset(),
                                        region.getLength());
                            }

                            return true;
                        }
                    } catch (PartInitException pie) {
                        Logger.log(Logger.WARNING_DEBUG, pie.getMessage(), pie);
                    }
                }
            }
        }

        return false;
    }

    /** Opens a Java class for the given fully qualified class name */
    private boolean openJavaClass(IProject project, String fqcn) {
        if (fqcn == null) {
            return false;
        }

        // Handle inner classes
        if (fqcn.indexOf('$') != -1) {
            fqcn = fqcn.replaceAll("\\$", "."); //NON-NLS-1$ //NON-NLS-2$
        }

        try {
            if (project.hasNature(JavaCore.NATURE_ID)) {
                IJavaProject javaProject = JavaCore.create(project);
                IJavaElement result = javaProject.findType(fqcn);
                if (result != null) {
                    return JavaUI.openInEditor(result) != null;
                }
            }
        } catch (PartInitException e) {
            AdtPlugin.log(e, "Can't open class %1$s", fqcn); // NON-NLS-1$
        } catch (JavaModelException e) {
            AdtPlugin.log(e, "Can't open class %1$s", fqcn); // NON-NLS-1$
            Display.getCurrent().beep();
        } catch (CoreException e) {
            AdtPlugin.log(e, "Can't open class %1$s", fqcn); // NON-NLS-1$
        }

        return false;
    }

    /** Looks up the project member of the given type and the given name */
    private IResource findMember(IProject project, ResourceType type, String name) {
        String relativePath;
        IResource member;
        String folder = WS_RESOURCES + WS_SEP + type.getName();
        relativePath = folder + WS_SEP + name + '.' + EXT_XML;
        member = project.findMember(relativePath);
        if (member == null) {
            // Search for any file in the directory with the given basename;
            // this is necessary for files like drawables that don't have
            // .xml files. It's an error to have conflicts in basenames for
            // these resources types so this is safe.
            IResource d = project.findMember(folder);
            if (d instanceof IFolder) {
                IFolder dir = (IFolder) d;
                member = findInFolder(name, dir);
            }

            if (member == null) {
                // Still couldn't find the member; it must not be defined in a "base" directory
                // like "layout"; look in various variations
                ResourceManager manager = ResourceManager.getInstance();
                ProjectResources resources = manager.getProjectResources(project);

                ResourceFolderType[] folderTypes = FolderTypeRelationship.getRelatedFolders(type);
                for (ResourceFolderType folderType : folderTypes) {
                    if (folderType != ResourceFolderType.VALUES) {
                        List<ResourceFolder> folders = resources.getFolders(folderType);
                        for (ResourceFolder resourceFolder : folders) {
                            if (resourceFolder.getFolder() instanceof IFolderWrapper) {
                                IFolderWrapper wrapper =
                                    (IFolderWrapper) resourceFolder.getFolder();
                                IFolder iFolder = wrapper.getIFolder();
                                member = findInFolder(name, iFolder);
                                if (member != null) {
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }

        return member;
    }

    /**
     * Finds a resource of the given name in the given folder, searching for possible file
     * extensions
     */
    private IResource findInFolder(String name, IFolder dir) {
        try {
            for (IResource child : dir.members()) {
                String fileName = child.getName();
                int index = fileName.indexOf('.');
                if (index != -1) {
                    fileName = fileName.substring(0, index);
                }
                if (fileName.equals(name)) {
                    return child;
                }
            }
        } catch (CoreException e) {
            AdtPlugin.log(e, ""); // NON-NLS-1$
        }

        return null;
    }

    /**
     * Search for a resource of a "multi-file" type (like @string) where the value can be
     * found in any file within the folder containing resource of that type (in the case
     * of @string, "values", and in the case of @color, "colors", etc).
     */
    private Pair<IFile, IRegion> findValueDefinition(IProject project, ResourceType type,
            String name) {
        // Search within the files in the values folder and find the value which defines
        // the given resource. To be efficient, we will only parse XML files that contain
        // a string match of the given token name.

        String values = AndroidConstants.WS_RESOURCES + AndroidConstants.WS_SEP
                + SdkConstants.FD_VALUES;
        IFolder f = project.getFolder(values);
        if (f.exists()) {
            try {
                // Check XML files in values/
                for (IResource resource : f.members()) {
                    if (resource.exists() && !resource.isDerived() && resource instanceof IFile) {
                        IFile file = (IFile) resource;
                        // Must have an XML extension
                        if (EXT_XML.equals(file.getFileExtension())) {
                            Pair<IFile, IRegion> target = findInXml(type, name, file);
                            if (target != null) {
                                return target;
                            }
                        }
                    }
                }
            } catch (CoreException e) {
                // pass
            }
        }
        return null;
    }

    /** Parses the given file and locates a definition of the given resource */
    private Pair<IFile, IRegion> findInXml(ResourceType type, String name, IFile file) {
        IStructuredModel model = null;
        try {
            model = StructuredModelManager.getModelManager().getExistingModelForRead(file);
            if (model == null) {
                // There is no open or cached model for the file; see if the file looks
                // like it's interesting (content contains the String name we are looking for)
                if (AdtPlugin.fileContains(file, name)) {
                    // Yes, so parse content
                    model = StructuredModelManager.getModelManager().getModelForRead(file);
                }
            }
            if (model instanceof IDOMModel) {
                IDOMModel domModel = (IDOMModel) model;
                Document document = domModel.getDocument();
                return findInDocument(type, name, file, document);
            }
        } catch (IOException e) {
            AdtPlugin.log(e, "Can't parse %1$s", file); // NON-NLS-1$
        } catch (CoreException e) {
            AdtPlugin.log(e, "Can't parse %1$s", file); // NON-NLS-1$
        } finally {
            if (model != null) {
                model.releaseFromRead();
            }
        }

        return null;
    }

    /** Looks within an XML DOM document for the given resource name and returns it */
    private Pair<IFile, IRegion> findInDocument(ResourceType type, String name, IFile file,
            Document document) {
        Element root = document.getDocumentElement();
        if (root.getTagName().equals(ROOT_ELEMENT)) {
            NodeList children = root.getChildNodes();
            for (int i = 0, n = children.getLength(); i < n; i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    Element element = (Element)child;
                    if (element.getTagName().equals(type.getName())) {
                        String elementName = element.getAttribute(NAME_ATTR);
                        if (elementName.equals(name)) {
                            IRegion region = null;
                            if (element instanceof IndexedRegion) {
                                IndexedRegion r = (IndexedRegion) element;
                                // IndexedRegion.getLength() returns bogus values
                                int length = r.getEndOffset() - r.getStartOffset();
                                region = new Region(r.getStartOffset(), length);
                            }

                            return Pair.of(file, region);
                        }
                    }
                }
            }
        }

        return null;
    }

    /** Return the resource type of the given url, and the resource name */
    private Pair<ResourceType,String> getResource(String url) {
        if (!url.startsWith("@")) { //$NON-NLS-1$
            return null;
        }
        int typeEnd = url.indexOf('/', 1);
        if (typeEnd == -1) {
            return null;
        }
        int nameBegin = typeEnd + 1;

        // Skip @ and @+
        int typeBegin = url.startsWith("@+") ? 2 : 1; // NON-NLS-1$

        int colon = url.lastIndexOf(':', typeEnd);
        if (colon != -1) {
            typeBegin = colon + 1;
        }
        String typeName = url.substring(typeBegin, typeEnd);
        ResourceType type = ResourceType.getEnum(typeName);
        if (type == null) {
            return null;
        }
        String name = url.substring(nameBegin);

        return Pair.of(type, name);
    }

    // ----- Implements IHyperlinkDetector -----

    public IHyperlink[] detectHyperlinks(ITextViewer textViewer, IRegion region,
            boolean canShowMultipleHyperlinks) {

        if (region == null || textViewer == null) {
            return null;
        }

        IDocument document = textViewer.getDocument();
        Node currentNode = getCurrentNode(document, region.getOffset());
        if (currentNode == null || currentNode.getNodeType() != Node.ELEMENT_NODE) {
            return null;
        }

        Attr currentAttr = getCurrentAttrNode(currentNode, region.getOffset());
        IRegion range = null;
        if (currentAttr == null) {
            if (currentNode instanceof IndexedRegion) {
                IndexedRegion r = (IndexedRegion) currentNode;
                range = new Region(r.getStartOffset() + 1, currentNode.getNodeName().length());
            }
        } else if (currentAttr instanceof IndexedRegion) {
            IndexedRegion r = (IndexedRegion) currentAttr;
            range = new Region(r.getStartOffset(), r.getLength());
        }

        if (isLinkable(currentNode, currentAttr) && range != null) {
            IHyperlink hyperlink = new DeferredResolutionLink(currentNode, currentAttr, range);
            if (hyperlink != null) {
                return new IHyperlink[] {
                    hyperlink
                };
            }
        }

        return null;
    }

    /** Returns the editor applicable to this hyperlink detection */
    private IEditorPart getEditor() {
        // I would like to be able to find this via getAdapter(TextEditor.class) but
        // couldn't find a way to initialize the editor context from
        // AndroidSourceViewerConfig#getHyperlinkDetectorTargets (which only has
        // a TextViewer, not a TextEditor, instance).
        //
        // Therefore, for now, use a hack. This hack is reasonable because hyperlink
        // resolvers are only run for the front-most visible window in the active
        // workbench.
        IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
        if (window != null) {
            IWorkbenchPage page = window.getActivePage();
            if (page != null) {
                return page.getActiveEditor();
            }
        }

        return null;
    }

    /** Returns the project applicable to this hyperlink detection */
    private IProject getProject() {
        IEditorPart editor = getEditor();
        if (editor != null) {
            IEditorInput input = editor.getEditorInput();
            if (input instanceof IFileEditorInput) {
                IFileEditorInput fileInput = (IFileEditorInput) input;
                return fileInput.getFile().getProject();
            }
        }

        return null;
    }

    /**
     * Hyperlink implementation which delays computing the actual file and offset target
     * until it is asked to open the hyperlink
     */
    class DeferredResolutionLink implements IHyperlink {
        private Node mNode;
        private Attr mAttribute;
        private IRegion mRegion;

        public DeferredResolutionLink(Node mNode, Attr mAttribute, IRegion mRegion) {
            super();
            this.mNode = mNode;
            this.mAttribute = mAttribute;
            this.mRegion = mRegion;
        }

        public IRegion getHyperlinkRegion() {
            return mRegion;
        }

        public String getHyperlinkText() {
            return null;
        }

        public String getTypeLabel() {
            return null;
        }

        public void open() {
            // Lazily compute the location to open
            if (!XmlHyperlinkResolver.this.open(mNode, mAttribute)) {
                // Failed: display message to the user
                String message = String.format("Could not open %1$s", mAttribute.getValue());
                IEditorSite editorSite = getEditor().getEditorSite();
                IStatusLineManager status = editorSite.getActionBars().getStatusLineManager();
                status.setErrorMessage(message);
            }
        }
    }

    // The below code are private utility methods copied from the XMLHyperlinkDetector
    // in the Eclipse WTP.

    /**
     * Returns the attribute node within node at offset.
     * <p>
     * Copy of Eclipse's XMLHyperlinkDetector#getCurrentAttrNode
     */
    private Attr getCurrentAttrNode(Node node, int offset) {
        if ((node instanceof IndexedRegion) && ((IndexedRegion) node).contains(offset)
                && (node.hasAttributes())) {
            NamedNodeMap attrs = node.getAttributes();
            // go through each attribute in node and if attribute contains
            // offset, return that attribute
            for (int i = 0; i < attrs.getLength(); ++i) {
                // assumption that if parent node is of type IndexedRegion,
                // then its attributes will also be of type IndexedRegion
                IndexedRegion attRegion = (IndexedRegion) attrs.item(i);
                if (attRegion.contains(offset)) {
                    return (Attr) attrs.item(i);
                }
            }
        }

        return null;
    }

    /**
     * Returns the node the cursor is currently on in the document. null if no node is
     * selected
     * <p>
     * Copy of Eclipse's XMLHyperlinkDetector#getCurrentNode
     */
    private Node getCurrentNode(IDocument document, int offset) {
        // get the current node at the offset (returns either: element,
        // doc type, text)
        IndexedRegion inode = null;
        IStructuredModel sModel = null;
        try {
            sModel = StructuredModelManager.getModelManager().getExistingModelForRead(document);
            if (sModel != null) {
                inode = sModel.getIndexedRegion(offset);
                if (inode == null) {
                    inode = sModel.getIndexedRegion(offset - 1);
                }
            }
        } finally {
            if (sModel != null) {
                sModel.releaseFromRead();
            }
        }

        if (inode instanceof Node) {
            return (Node) inode;
        }
        return null;
    }
}
