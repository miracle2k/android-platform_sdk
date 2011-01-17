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
import static com.android.ide.common.layout.LayoutConstants.NEW_ID_PREFIX;
import static com.android.ide.common.layout.LayoutConstants.VIEW;
import static com.android.ide.eclipse.adt.AndroidConstants.ANDROID_PKG;
import static com.android.ide.eclipse.adt.AndroidConstants.EXT_XML;
import static com.android.ide.eclipse.adt.AndroidConstants.FN_RESOURCE_BASE;
import static com.android.ide.eclipse.adt.AndroidConstants.FN_RESOURCE_CLASS;
import static com.android.ide.eclipse.adt.internal.editors.resources.descriptors.ResourcesDescriptors.NAME_ATTR;
import static com.android.ide.eclipse.adt.internal.editors.resources.descriptors.ResourcesDescriptors.ROOT_ELEMENT;
import static com.android.sdklib.SdkConstants.FD_VALUES;
import static com.android.sdklib.xml.AndroidManifest.ATTRIBUTE_NAME;
import static com.android.sdklib.xml.AndroidManifest.ATTRIBUTE_PACKAGE;
import static com.android.sdklib.xml.AndroidManifest.NODE_ACTIVITY;
import static com.android.sdklib.xml.AndroidManifest.NODE_SERVICE;

import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.AndroidConstants;
import com.android.ide.eclipse.adt.internal.editors.AndroidXmlEditor;
import com.android.ide.eclipse.adt.internal.editors.layout.LayoutEditor;
import com.android.ide.eclipse.adt.internal.editors.layout.gle2.GraphicalEditorPart;
import com.android.ide.eclipse.adt.internal.resources.ResourceType;
import com.android.ide.eclipse.adt.internal.resources.configurations.FolderConfiguration;
import com.android.ide.eclipse.adt.internal.resources.manager.FolderTypeRelationship;
import com.android.ide.eclipse.adt.internal.resources.manager.ProjectResources;
import com.android.ide.eclipse.adt.internal.resources.manager.ResourceFile;
import com.android.ide.eclipse.adt.internal.resources.manager.ResourceFolder;
import com.android.ide.eclipse.adt.internal.resources.manager.ResourceFolderType;
import com.android.ide.eclipse.adt.internal.resources.manager.ResourceManager;
import com.android.ide.eclipse.adt.internal.sdk.AndroidTargetData;
import com.android.ide.eclipse.adt.internal.sdk.Sdk;
import com.android.ide.eclipse.adt.io.IFileWrapper;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkConstants;
import com.android.sdklib.annotations.VisibleForTesting;
import com.android.sdklib.io.FileWrapper;
import com.android.sdklib.util.Pair;

import org.apache.xerces.parsers.DOMParser;
import org.apache.xerces.xni.Augmentations;
import org.apache.xerces.xni.NamespaceContext;
import org.apache.xerces.xni.QName;
import org.apache.xerces.xni.XMLAttributes;
import org.apache.xerces.xni.XMLLocator;
import org.apache.xerces.xni.XNIException;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.ICodeAssist;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.ui.javaeditor.EditorUtility;
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;
import org.eclipse.jdt.internal.ui.text.JavaWordFinder;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.actions.SelectionDispatchAction;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IStatusLineManager;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.hyperlink.AbstractHyperlinkDetector;
import org.eclipse.jface.text.hyperlink.IHyperlink;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.part.MultiPageEditorPart;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.wst.sse.core.StructuredModelManager;
import org.eclipse.wst.sse.core.internal.encoding.util.Logger;
import org.eclipse.wst.sse.core.internal.provisional.IStructuredModel;
import org.eclipse.wst.sse.core.internal.provisional.IndexedRegion;
import org.eclipse.wst.sse.core.internal.provisional.text.IStructuredDocument;
import org.eclipse.wst.sse.core.internal.provisional.text.IStructuredDocumentRegion;
import org.eclipse.wst.sse.core.internal.provisional.text.ITextRegion;
import org.eclipse.wst.sse.ui.StructuredTextEditor;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMModel;
import org.eclipse.wst.xml.core.internal.regions.DOMRegionContext;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Class containing hyperlink resolvers for XML and Java files to jump to associated
 * resources -- Java Activity and Service classes, XML layout and string declarations,
 * image drawables, etc.
 */
@SuppressWarnings("restriction")
public class Hyperlinks {
    private Hyperlinks() {
        // Not instantiatable. This is a container class containing shared code
        // for the various inner classes that are actual hyperlink resolvers.
    }

    /** Regular expression matching a FQCN for a view class */
    @VisibleForTesting
    /* package */ static final Pattern CLASS_PATTERN = Pattern.compile(
        "(([a-zA-Z_\\$][a-zA-Z0-9_\\$]*)+\\.)+[a-zA-Z_\\$][a-zA-Z0-9_\\$]*"); //$NON-NLS-1$

    /** Determines whether the given attribute <b>name</b> is linkable */
    private static boolean isAttributeNameLink(@SuppressWarnings("unused") XmlContext context) {
        // We could potentially allow you to link to builtin Android properties:
        //   ANDROID_URI.equals(attribute.getNamespaceURI())
        // and then jump into the res/values/attrs.xml document that is available
        // in the SDK data directory (path found via
        // IAndroidTarget.getPath(IAndroidTarget.ATTRIBUTES)).
        //
        // For now, we're not doing that.
        //
        // We could also allow to jump into custom attributes in custom view
        // classes. Not yet implemented.

        return false;
    }

    /** Determines whether the given attribute <b>value</b> is linkable */
    private static boolean isAttributeValueLink(XmlContext context) {
        // Everything else here is attribute based
        Attr attribute = context.getAttribute();
        if (attribute == null) {
            return false;
        }

        if (isClassAttribute(context) || isActivity(context) || isService(context)) {
            return true;
        }

        String value = attribute.getValue();
        if (value.startsWith("@+")) { //$NON-NLS-1$
            // It's a value -declaration-, nowhere else to jump
            // (though we could consider jumping to the R-file; would that
            // be helpful?)
            return false;
        }

        Pair<ResourceType,String> resource = parseResource(value);
        if (resource != null) {
            ResourceType type = resource.getFirst();
            if (type != null) {
                return true;
            }
        }

        return false;
    }

    /** Determines whether the given element <b>name</b> is linkable */
    private static boolean isElementNameLink(XmlContext context) {
        if (isClassElement(context)) {
            return true;
        }

        return false;
    }

    /**
     * Returns true if this node/attribute pair corresponds to a manifest reference to
     * an activity.
     */
    private static boolean isActivity(XmlContext context) {
        // Is this an <activity> or <service> in an AndroidManifest.xml file? If so, jump
        // to it
        Attr attribute = context.getAttribute();
        String tagName = context.getElement().getTagName();
        if (NODE_ACTIVITY.equals(tagName) && ATTRIBUTE_NAME.equals(attribute.getLocalName())
                && ANDROID_URI.equals(attribute.getNamespaceURI())) {
            return true;
        }

        return false;
    }

    /** Returns true if this represents a {@code <view class="foo.bar.Baz">} class attribute */
    private static boolean isClassAttribute(XmlContext context) {
        Attr attribute = context.getAttribute();
        if (attribute == null) {
            return false;
        }
        String tag = context.getElement().getTagName();
        return ATTR_CLASS.equals(attribute.getLocalName()) && VIEW.equals(tag);
    }

    /** Returns true if this represents a {@code <foo.bar.Baz>} custom view class element */
    private static boolean isClassElement(XmlContext context) {
        if (context.getAttribute() != null) {
            // Don't match the outer element if the user is hovering over a specific attribute
            return false;
        }
        // If the element looks like a fully qualified class name (e.g. it's a custom view
        // element) offer it as a link
        String tag = context.getElement().getTagName();
        return (tag.indexOf('.') != -1 && CLASS_PATTERN.matcher(tag).matches());
    }

    /** Returns the FQCN for a class declaration at the given context */
    private static String getClassFqcn(XmlContext context) {
        if (isClassAttribute(context)) {
            return context.getAttribute().getValue();
        } else if (isClassElement(context)) {
            return context.getElement().getTagName();
        }

        return null;
    }

    /**
     * Returns true if this node/attribute pair corresponds to a manifest reference to
     * an service.
     */
    private static boolean isService(XmlContext context) {
        Attr attribute = context.getAttribute();
        Element node = context.getElement();

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
    private static String getActivityClassFqcn(XmlContext context) {
        Attr attribute = context.getAttribute();
        Element node = context.getElement();
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
    private static String getServiceClassFqcn(XmlContext context) {
        // Same logic
        return getActivityClassFqcn(context);
    }

    /**
     * Is this a resource that can be defined in any file within the "values" folder?
     */
    private static boolean isValueResource(ResourceType type) {
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
    private static boolean isFileResource(ResourceType type) {
        ResourceFolderType[] folderTypes = FolderTypeRelationship.getRelatedFolders(type);
        for (ResourceFolderType folderType : folderTypes) {
            if (folderType != ResourceFolderType.VALUES) {
                return true;
            }
        }

        return false;
    }

    /**
     * Computes the actual exact location to jump to for a given XML context.
     *
     * @param context the XML context to be opened
     * @return true if the request was handled successfully
     */
    private static boolean open(XmlContext context) {
        IProject project = getProject();
        if (project == null) {
            return false;
        }

        if (isActivity(context)) {
            String fqcn = getActivityClassFqcn(context);
            return openJavaClass(project, fqcn);
        } else if (isService(context)) {
            String fqcn = getServiceClassFqcn(context);
            return openJavaClass(project, fqcn);
        } else if (isClassElement(context) || isClassAttribute(context)) {
            return openJavaClass(project, getClassFqcn(context));
        } else {
            Attr attribute = context.getAttribute();
            return openResourceUrl(project, attribute.getValue());
        }
    }

    /**
     * Opens a given resource url (such as @layout/foo or @string/bar).
     *
     * @param project the project to search in
     * @param url the resource url
     * @return true if the request was handled successfully
     */
    private static boolean openResourceUrl(IProject project, String url) {
        if (url.startsWith("@android")) {
            return openAndroidResource(project, url);
        }

        Pair<ResourceType,String> resource = parseResource(url);
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

                // Id's are handled specially because they are typically defined
                // inline (though they -can- be defined in the values folder above as well,
                // in which case we will prefer that definition)
                if (member == null && type == ResourceType.ID) {
                    Pair<IFile,IRegion> def = findIdDefinition(project, name);
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
                    member = findNonValueFile(project, type, name);
                }

                try {
                    if (member != null && member instanceof IFile) {
                        IFile file = (IFile) member;
                        openFile(file, region);

                        return true;
                    }
                } catch (PartInitException pie) {
                    Logger.log(Logger.WARNING_DEBUG, pie.getMessage(), pie);
                }
            }
        }
        return false;
    }

    /** Opens the given file and shows the given (optional) region */
    private static void openFile(IFile file, IRegion region) throws PartInitException {
        IEditorPart sourceEditor = getEditor();
        IWorkbenchPage page = sourceEditor.getEditorSite().getPage();
        IEditorPart targetEditor = IDE.openEditor(page, file, true);
        if (targetEditor instanceof AndroidXmlEditor) {
            AndroidXmlEditor editor = (AndroidXmlEditor) targetEditor;
            if ((region != null)) {
                editor.show(region.getOffset(), region.getLength());
            } else {
                editor.setActivePage(AndroidXmlEditor.TEXT_EDITOR_ID);
            }
        }
    }

    /** Opens a path (which may not be in the workspace) */
    private static void openPath(IPath filePath, IRegion region, int offset) {
        IEditorPart sourceEditor = getEditor();
        IWorkbenchPage page = sourceEditor.getEditorSite().getPage();
        IWorkspaceRoot workspace = ResourcesPlugin.getWorkspace().getRoot();
        IPath workspacePath = workspace.getLocation();
        IEditorSite editorSite = sourceEditor.getEditorSite();
        if (workspacePath.isPrefixOf(filePath)) {
            IPath relativePath = filePath.makeRelativeTo(workspacePath);
            IResource file = workspace.findMember(relativePath);
            if (file instanceof IFile) {
                try {
                    openFile((IFile)file, region);
                    return;
                } catch (PartInitException ex) {
                    AdtPlugin.log(ex, "Can't open %$1s", filePath); //$NON-NLS-1$
                }
            }
        } else {
            // It's not a path in the workspace; look externally
            // (this is probably an @android: path)
            if (filePath.isAbsolute()) {
                IFileStore fileStore = EFS.getLocalFileSystem().getStore(filePath);
                if (!fileStore.fetchInfo().isDirectory() && fileStore.fetchInfo().exists()) {
                    try {
                        IEditorPart target = IDE.openEditorOnFileStore(page, fileStore);
                        if (target instanceof MultiPageEditorPart) {
                            MultiPageEditorPart part = (MultiPageEditorPart) target;
                            IEditorPart[] editors = part.findEditors(target.getEditorInput());
                            if (editors != null) {
                                for (IEditorPart editor : editors) {
                                    if (editor instanceof StructuredTextEditor) {
                                        StructuredTextEditor ste = (StructuredTextEditor) editor;
                                        part.setActiveEditor(editor);
                                        ste.selectAndReveal(offset, 0);
                                        break;
                                    }
                                }
                            }
                        }

                        return;
                    } catch (PartInitException ex) {
                        AdtPlugin.log(ex, "Can't open %$1s", filePath); //$NON-NLS-1$
                    }
                }
            }
        }

        // Failed: display message to the user
        String message = String.format("Could not find resource %1$s", filePath);
        IStatusLineManager status = editorSite.getActionBars().getStatusLineManager();
        status.setErrorMessage(message);
    }

    /**
     * Opens a framework resource's declaration
     *
     * @param project project to look up the framework for
     * @param url the resource url, such as @android:string/ok, to open the declaration
     *            for
     * @return true if the url was successfully opened.
     */
    private static boolean openAndroidResource(IProject project, String url) {
        Pair<ResourceType,String> parsedUrl = parseResource(url);
        if (parsedUrl == null) {
            return false;
        }

        ResourceType type = parsedUrl.getFirst();
        String name = parsedUrl.getSecond();

        // Attempt to open files, such as layouts and drawables in @android?
        if (isFileResource(type)) {
            ProjectResources frameworkResources = getResources(project, true /* framework */);
            if (frameworkResources == null) {
                return false;
            }
            Map<String, Map<String, ResourceValue>> configuredResources =
                frameworkResources.getConfiguredResources(new FolderConfiguration());

            Set<String> seen = new HashSet<String>();
            seen.add(url);

            // Loop over map lookup since one lookup may yield another attribute to
            // be looked up, e.g. @android:drawable/alert_dark_frame will yield
            // @drawable/popup_full_dark, which when looked up will finally yield
            // the XML path we are looking for
            while (true) {
                Map<String, ResourceValue> typeMap = configuredResources.get(type.getName());
                if (typeMap != null) {
                    ResourceValue value = typeMap.get(name);
                    if (value != null) {
                        String valueStr = value.getValue();
                        if (valueStr.startsWith("?")) { //$NON-NLS-1$
                            // FIXME: It's a reference. We should resolve this properly.
                            return false;
                        } else if (valueStr.startsWith("@")) { //$NON-NLS-1$
                            // Refers to a different resource; resolve it iteratively
                            if (seen.contains(valueStr)) {
                                return false;
                            }
                            seen.add(valueStr);
                            parsedUrl = parseResource(valueStr);
                            type = parsedUrl.getFirst();
                            name = parsedUrl.getSecond();
                            // Continue to iterate map lookup
                        } else {
                            // valueStr may not be a path... if it's not, don't try
                            // to look it up. (For example, it may return the resolved
                            // string value of @android:string/cancel => "Cancel").
                            if (new File(valueStr).exists()) {
                                Path path = new Path(valueStr);
                                openPath(path, null, -1);
                                return true;
                            }
                            break;
                        }
                    } else {
                        break;
                    }
                } else {
                    break;
                }
            }

            // Attempt to find files via ProjectResources.getSourceFiles(); this
            // is done after the above search since this search won't resolve references
            FolderConfiguration configuration = getConfiguration();
            List<ResourceFile> sourceFiles = frameworkResources.getSourceFiles(type, name,
                    configuration);
            for (ResourceFile file : sourceFiles) {
                String location = file.getFile().getOsLocation();
                if (new File(location).exists()) {
                    Path path = new Path(location);
                    openPath(path, null, -1);
                    return true;
                }
            }

        } else if (isValueResource(type)) {
            FolderConfiguration configuration = getConfiguration();
            Pair<File, Integer> match = findFrameworkValueByConfig(project, type, name,
                    configuration);
            if (match == null && configuration != null) {
                match = findFrameworkValueByConfig(project, type, name, null);
            }

            if (match != null) {
                Path path = new Path(match.getFirst().getPath());
                openPath(path, null, match.getSecond());
                return true;
            }
        }

        return false;
    }

    /** Return the set of matching source files for the given resource type and name */
    private static List<ResourceFile> getResourceFiles(IProject project,
            ResourceType type, String name, boolean framework,
            FolderConfiguration configuration) {
        ProjectResources resources = getResources(project, framework);
        if (resources == null) {
            return null;
        }
        List<ResourceFile> sourceFiles = resources.getSourceFiles(type, name, configuration);
        if (sourceFiles != null) {
            if (sourceFiles.size() > 1 && configuration == null) {
                // Sort all the files in the base values/ folder first, followed by
                // everything else
                List<ResourceFile> first = new ArrayList<ResourceFile>();
                List<ResourceFile> second = new ArrayList<ResourceFile>();
                for (ResourceFile file : sourceFiles) {
                    if (FD_VALUES.equals(file.getFolder().getFolder().getName())) {
                        // Found match in value
                        first.add(file);
                    } else {
                        second.add(file);
                    }
                }
                first.addAll(second);
                sourceFiles = first;
            }
        }

        return sourceFiles;
    }

    /** Searches for the given resource for a specific configuration (which may be null) */
    private static Pair<File, Integer> findFrameworkValueByConfig(IProject project,
            ResourceType type, String name, FolderConfiguration configuration) {
        List<ResourceFile> sourceFiles = getResourceFiles(project, type, name, true /* framework*/,
                configuration);
        if (sourceFiles != null) {
            for (ResourceFile resourceFile : sourceFiles) {
                if (resourceFile.getFile() instanceof FileWrapper) {
                    File file = ((FileWrapper) resourceFile.getFile());
                    if (file.getName().endsWith(EXT_XML)) {
                        // Must have an XML extension
                        Pair<File, Integer> match = findValueInXml(type, name, file);
                        if (match != null) {
                            return match;
                        }
                    }
                }
            }
        }

        return null;
    }

    /**
     * Opens a Java class for the given fully qualified class name
     *
     * @param project the project containing the class
     * @param fqcn the fully qualified class name of the class to be opened
     * @return true if the class was opened, false otherwise
     */
    public static boolean openJavaClass(IProject project, String fqcn) {
        if (fqcn == null) {
            return false;
        }

        // Handle inner classes
        if (fqcn.indexOf('$') != -1) {
            fqcn = fqcn.replaceAll("\\$", "."); //$NON-NLS-1$ //$NON-NLS-2$
        }

        try {
            if (project.hasNature(JavaCore.NATURE_ID)) {
                IJavaProject javaProject = JavaCore.create(project);
                IJavaElement result = javaProject.findType(fqcn);
                if (result != null) {
                    return JavaUI.openInEditor(result) != null;
                }
            }
        } catch (Throwable e) {
            AdtPlugin.log(e, "Can't open class %1$s", fqcn); //$NON-NLS-1$
        }

        return false;
    }

    /** Looks up the project member of the given type and the given name */
    private static IResource findNonValueFile(IProject project, ResourceType type, String name) {
        ProjectResources resources = getResources(project, false /* not framework */);
        if (resources == null) {
            return null;
        }
        FolderConfiguration configuration = getConfiguration();
        if (configuration != null) {
            IResource file = findFileByConfig(type, name, resources, configuration);
            if (file != null) {
                return file;
            }
        }

        return findFileByConfig(type, name, resources, null);
    }

    /**
     * Find a file for a given named resource, associated with a given folder
     * configuration
     */
    private static IResource findFileByConfig(ResourceType type, String name,
            ProjectResources resources, FolderConfiguration configuration) {
        List<ResourceFile> sourceFiles = resources.getSourceFiles(type, name, configuration);
        if (sourceFiles != null) {
            for (ResourceFile resourceFile : sourceFiles) {
                if (resourceFile.getFile() instanceof IFileWrapper) {
                    return ((IFileWrapper) resourceFile.getFile()).getIFile();
                }
            }
        }

        return null;
    }

    /**
     * Returns the current configuration, if the associated UI editor has been initialized
     * and has an associated configuration
     *
     * @return the configuration for this file, or null
     */
    private static FolderConfiguration getConfiguration() {
        IEditorPart editor = getEditor();
        if (editor != null) {
            if (editor instanceof LayoutEditor) {
                LayoutEditor layoutEditor = (LayoutEditor) editor;
                GraphicalEditorPart graphicalEditor = layoutEditor.getGraphicalEditor();
                if (graphicalEditor != null) {
                    return graphicalEditor.getConfiguration();
                } else {
                    // TODO: Could try a few more things to get the configuration:
                    // (1) try to look at the file.getPersistentProperty(NAME_CONFIG_STATE)
                    //    which will return previously saved state. This isn't necessary today
                    //    since no editors seem to be lazily initialized.
                    // (2) attempt to use the configuration from any of the other open
                    //    files, especially files in the same directory as this one.
                }
            }

            // Create a configuration from the current file
            IEditorInput editorInput = editor.getEditorInput();
            if (editorInput instanceof FileEditorInput) {
                IFile file = ((FileEditorInput) editorInput).getFile();
                IProject project = file.getProject();
                ProjectResources pr = ResourceManager.getInstance().getProjectResources(project);
                ResourceFolder resFolder = pr.getResourceFolder((IFolder) file.getParent());
                if (resFolder != null) {
                    return resFolder.getConfiguration();
                }
            }
        }

        return null;
    }

    /** Returns the {@link IAndroidTarget} to be used for looking up system resources */
    private static IAndroidTarget getTarget(IProject project) {
        IEditorPart editor = getEditor();
        if (editor != null) {
            if (editor instanceof LayoutEditor) {
                LayoutEditor layoutEditor = (LayoutEditor) editor;
                GraphicalEditorPart graphicalEditor = layoutEditor.getGraphicalEditor();
                if (graphicalEditor != null) {
                    return graphicalEditor.getRenderingTarget();
                }
            }
        }

        Sdk currentSdk = Sdk.getCurrent();
        if (currentSdk == null) {
            return null;
        }

        return currentSdk.getTarget(project);
    }

    /** Return either the project resources or the framework resources (or null) */
    private static ProjectResources getResources(IProject project, boolean framework) {
        if (framework) {
            IAndroidTarget target = getTarget(project);
            if (target == null) {
                return null;
            }
            AndroidTargetData data = Sdk.getCurrent().getTargetData(target);
            if (data == null) {
                return null;
            }
            return data.getFrameworkResources();
        } else {
            return ResourceManager.getInstance().getProjectResources(project);
        }
    }

    /**
     * Finds a definition of an id attribute in layouts. (Ids can also be defined as
     * resources; use {@link #findValueDefinition} to locate it there.)
     */
    private static Pair<IFile, IRegion> findIdDefinition(IProject project, String id) {
        // FIRST look in the same file as the originating request, that's where you usually
        // want to jump
        IFile self = getFile();
        if (self != null && EXT_XML.equals(self.getFileExtension())) {
            Pair<IFile, IRegion> target = findIdInXml(id, self);
            if (target != null) {
                return target;
            }
        }

        // We're currently only searching in the base layout folder.
        // The next step is to add global resource reference tracking (which we already
        // need to detect unused resources etc) and in that case we can quickly offer
        // multiple links, one to each definition.
        String folderPath = AndroidConstants.WS_RESOURCES + AndroidConstants.WS_SEP
                + SdkConstants.FD_LAYOUT;

        IFolder f = project.getFolder(folderPath);
        if (f.exists()) {
            try {
                // Check XML files in values/
                for (IResource resource : f.members()) {
                    if (resource.exists() && !resource.isDerived() && resource instanceof IFile) {
                        IFile file = (IFile) resource;
                        // Must have an XML extension
                        if (EXT_XML.equals(file.getFileExtension())) {
                            Pair<IFile, IRegion> target = findIdInXml(id, file);
                            if (target != null) {
                                return target;
                            }
                        }
                    }
                }
            } catch (CoreException e) {
                AdtPlugin.log(e, ""); //$NON-NLS-1$
            }
        }

        return null;
    }

    /**
     * Searches for a resource of a "multi-file" type (like @string) where the value can
     * be found in any file within the folder containing resource of that type (in the
     * case of @string, "values", and in the case of @color, "colors", etc).
     */
    private static Pair<IFile, IRegion> findValueDefinition(IProject project, ResourceType type,
            String name) {
        // Search within the files in the values folder and find the value which defines
        // the given resource. To be efficient, we will only parse XML files that contain
        // a string match of the given token name.
        FolderConfiguration configuration = getConfiguration();
        Pair<IFile, IRegion> target = findValueByConfig(project, type, name, configuration);
        if (target != null) {
            return target;
        }

        if (configuration != null) {
            // Try searching without configuration too; more potential matches
            return findValueByConfig(project, type, name, configuration);
        }

        return null;
    }

    /** Searches for the given resource for a specific configuration (which may be null) */
    private static Pair<IFile, IRegion> findValueByConfig(IProject project,
            ResourceType type, String name, FolderConfiguration configuration) {
        List<ResourceFile> sourceFiles = getResourceFiles(project, type, name,
                false /* not framework*/, configuration);
        if (sourceFiles != null) {
            for (ResourceFile resourceFile : sourceFiles) {
                if (resourceFile.getFile() instanceof IFileWrapper) {
                    IFile file = ((IFileWrapper) resourceFile.getFile()).getIFile();
                    if (EXT_XML.equals(file.getFileExtension())) {
                        Pair<IFile, IRegion> target = findValueInXml(type, name, file);
                        if (target != null) {
                            return target;
                        }
                    }
                }
            }
        }

        return null;
    }

    /** Parses the given file and locates a definition of the given resource */
    private static Pair<IFile, IRegion> findValueInXml(
            ResourceType type, String name, IFile file) {
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
                return findValueInDocument(type, name, file, document);
            }
        } catch (IOException e) {
            AdtPlugin.log(e, "Can't parse %1$s", file); //$NON-NLS-1$
        } catch (CoreException e) {
            AdtPlugin.log(e, "Can't parse %1$s", file); //$NON-NLS-1$
        } finally {
            if (model != null) {
                model.releaseFromRead();
            }
        }

        return null;
    }

    /** Looks within an XML DOM document for the given resource name and returns it */
    private static Pair<IFile, IRegion> findValueInDocument(
            ResourceType type, String name, IFile file, Document document) {
        String targetTag = type.getName();
        if (type == ResourceType.ID) {
            // Ids are recorded in <item> tags instead of <id> tags
            targetTag = "item"; //$NON-NLS-1$
        }
        Element root = document.getDocumentElement();
        if (root.getTagName().equals(ROOT_ELEMENT)) {
            NodeList children = root.getChildNodes();
            for (int i = 0, n = children.getLength(); i < n; i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    Element element = (Element)child;
                    if (element.getTagName().equals(targetTag)) {
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

    /** Parses the given file and locates a definition of the given resource */
    private static Pair<IFile, IRegion> findIdInXml(String id, IFile file) {
        IStructuredModel model = null;
        try {
            model = StructuredModelManager.getModelManager().getExistingModelForRead(file);
            if (model == null) {
                // There is no open or cached model for the file; see if the file looks
                // like it's interesting (content contains the String name we are looking for)
                if (AdtPlugin.fileContains(file, id)) {
                    // Yes, so parse content
                    model = StructuredModelManager.getModelManager().getModelForRead(file);
                }
            }
            if (model instanceof IDOMModel) {
                IDOMModel domModel = (IDOMModel) model;
                Document document = domModel.getDocument();
                return findIdInDocument(id, file, document);
            }
        } catch (IOException e) {
            AdtPlugin.log(e, "Can't parse %1$s", file); //$NON-NLS-1$
        } catch (CoreException e) {
            AdtPlugin.log(e, "Can't parse %1$s", file); //$NON-NLS-1$
        } finally {
            if (model != null) {
                model.releaseFromRead();
            }
        }

        return null;
    }

    /** Looks within an XML DOM document for the given resource name and returns it */
    private static Pair<IFile, IRegion> findIdInDocument(String id, IFile file,
            Document document) {
        String targetAttribute = NEW_ID_PREFIX + id;
        return findIdInElement(document.getDocumentElement(), file, targetAttribute);
    }

    private static Pair<IFile, IRegion> findIdInElement(
            Element root, IFile file, String targetAttribute) {
        NamedNodeMap attributes = root.getAttributes();
        for (int i = 0, n = attributes.getLength(); i < n; i++) {
            Node item = attributes.item(i);
            if (item instanceof Attr) {
                Attr attribute = (Attr)item;
                String value = attribute.getValue();
                if (value.equals(targetAttribute)) {
                    // Select the element -containing- the id rather than the attribute itself
                    IRegion region = null;
                    Node element = attribute.getOwnerElement();
                    //if (attribute instanceof IndexedRegion) {
                    if (element instanceof IndexedRegion) {
                        IndexedRegion r = (IndexedRegion) element;
                        int length = r.getEndOffset() - r.getStartOffset();
                        region = new Region(r.getStartOffset(), length);
                    }

                    return Pair.of(file, region);
                }
            }
        }

        NodeList children = root.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element)child;
                Pair<IFile, IRegion> result = findIdInElement(element, file, targetAttribute);
                if (result != null) {
                    return result;
                }
            }
        }

        return null;
    }

    /** Return the resource type of the given url, and the resource name */
    private static Pair<ResourceType,String> parseResource(String url) {
        if (!url.startsWith("@")) { //$NON-NLS-1$
            return null;
        }
        int typeEnd = url.indexOf('/', 1);
        if (typeEnd == -1) {
            return null;
        }
        int nameBegin = typeEnd + 1;

        // Skip @ and @+
        int typeBegin = url.startsWith("@+") ? 2 : 1; //$NON-NLS-1$

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

    /** Parses the given file and locates a definition of the given resource */
    private static Pair<File, Integer> findValueInXml(ResourceType type, String name, File file) {
        // We can't use the StructureModelManager on files outside projects
        // There is no open or cached model for the file; see if the file looks
        // like it's interesting (content contains the String name we are looking for)
        if (AdtPlugin.fileContains(file, name)) {
            try {
                InputSource is = new InputSource(new FileInputStream(file));
                OffsetTrackingParser parser = new OffsetTrackingParser();
                parser.parse(is);
                Document document = parser.getDocument();

                return findValueInDocument(type, name, file, parser, document);
            } catch (SAXException e) {
                // pass -- ignore files we can't parse
            } catch (IOException e) {
                // pass -- ignore files we can't parse
            }
        }

        return null;
    }

    /** Looks within an XML DOM document for the given resource name and returns it */
    private static Pair<File, Integer> findValueInDocument(ResourceType type, String name,
            File file, OffsetTrackingParser parser, Document document) {
        String targetTag = type.getName();
        if (type == ResourceType.ID) {
            // Ids are recorded in <item> tags instead of <id> tags
            targetTag = "item"; //$NON-NLS-1$
        } else if (type == ResourceType.ATTR) {
            // Attributes seem to be defined in <public> tags
            targetTag = "public"; //$NON-NLS-1$
        }
        Element root = document.getDocumentElement();
        if (root.getTagName().equals(ROOT_ELEMENT)) {
            NodeList children = root.getChildNodes();
            for (int i = 0, n = children.getLength(); i < n; i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    Element element = (Element) child;
                    if (element.getTagName().equals(targetTag)) {
                        String elementName = element.getAttribute(NAME_ATTR);
                        if (elementName.equals(name)) {

                            return Pair.of(file, parser.getOffset(element));
                        }
                    }
                }
            }
        }

        return null;
    }

    /** Detector for finding Android references in XML files */
   public static class XmlResolver extends AbstractHyperlinkDetector {

        public IHyperlink[] detectHyperlinks(ITextViewer textViewer, IRegion region,
                boolean canShowMultipleHyperlinks) {

            if (region == null || textViewer == null) {
                return null;
            }

            IDocument document = textViewer.getDocument();

            XmlContext context = XmlContext.find(document, region.getOffset());
            if (context == null) {
                return null;
            }

            IRegion range = context.getInnerRange(document);
            boolean isLinkable = false;
            String type = context.getInnerRegion().getType();
            if (type == DOMRegionContext.XML_TAG_ATTRIBUTE_VALUE) {
                if (isAttributeValueLink(context)) {
                    isLinkable = true;
                    // Strip out quotes
                    range = new Region(range.getOffset() + 1, range.getLength() - 2);
                }
            } else if (type == DOMRegionContext.XML_TAG_ATTRIBUTE_NAME) {
                if (isAttributeNameLink(context)) {
                    isLinkable = true;
                }
            } else if (type == DOMRegionContext.XML_TAG_NAME) {
                if (isElementNameLink(context)) {
                    isLinkable = true;
                }
            }

            if (isLinkable) {
                IHyperlink hyperlink = new DeferredResolutionLink(null, context, range);
                if (hyperlink != null) {
                    return new IHyperlink[] {
                        hyperlink
                    };
                }
            }

            return null;
        }
    }

    /** Detector for finding Android references in Java files */
    public static class JavaResolver extends AbstractHyperlinkDetector {

        public IHyperlink[] detectHyperlinks(ITextViewer textViewer, IRegion region,
                boolean canShowMultipleHyperlinks) {
            // Most of this is identical to the builtin JavaElementHyperlinkDetector --
            // everything down to the Android R filtering below

            ITextEditor textEditor = (ITextEditor) getAdapter(ITextEditor.class);
            if (region == null || !(textEditor instanceof JavaEditor))
                return null;

            IAction openAction = textEditor.getAction("OpenEditor"); //$NON-NLS-1$
            if (!(openAction instanceof SelectionDispatchAction))
                return null;

            int offset = region.getOffset();

            IJavaElement input = EditorUtility.getEditorInputJavaElement(textEditor, false);
            if (input == null)
                return null;

            try {
                IDocument document = textEditor.getDocumentProvider().getDocument(
                        textEditor.getEditorInput());
                IRegion wordRegion = JavaWordFinder.findWord(document, offset);
                if (wordRegion == null || wordRegion.getLength() == 0)
                    return null;

                IJavaElement[] elements = null;
                elements = ((ICodeAssist) input).codeSelect(wordRegion.getOffset(), wordRegion
                        .getLength());

                // Specific Android R class filtering:
                if (elements.length > 0) {
                    IJavaElement element = elements[0];
                    if (element.getElementType() == IJavaElement.FIELD) {
                        IJavaElement unit = element.getAncestor(IJavaElement.COMPILATION_UNIT);
                        if (unit == null) {
                            // Probably in a binary; see if this is an android.R resource
                            IJavaElement type = element.getAncestor(IJavaElement.TYPE);
                            if (type != null && type.getParent() != null) {
                                IJavaElement parentType = type.getParent();
                                if (parentType.getElementType() == IJavaElement.CLASS_FILE) {
                                    String pn = parentType.getElementName();
                                    String prefix = FN_RESOURCE_BASE + "$"; //$NON-NLS-1$
                                    if (pn.startsWith(prefix)) {
                                        return createTypeLink(element, type, wordRegion, true);
                                    }
                                }
                            }
                        } else if (FN_RESOURCE_CLASS.equals(unit.getElementName())) {
                            // Yes, we're referencing the project R class.
                            // Offer hyperlink navigation to XML resource files for
                            // the various definitions
                            IJavaElement type = element.getAncestor(IJavaElement.TYPE);
                            if (type != null) {
                                return createTypeLink(element, type, wordRegion, false);
                            }
                        }
                    }

                }
                return null;
            } catch (JavaModelException e) {
                return null;
            }
        }

        private IHyperlink[] createTypeLink(IJavaElement element, IJavaElement type,
                IRegion wordRegion, boolean isFrameworkResource) {
            String typeName = type.getElementName();
            // typeName will be "id", "layout", "string", etc
            if (isFrameworkResource) {
                typeName = ANDROID_PKG + ':' + typeName;
            }
            String elementName = element.getElementName();
            String url = '@' + typeName + '/' + elementName;
            return new IHyperlink[] {
                new DeferredResolutionLink(url, null, wordRegion)
            };
        }
    }

    /** Returns the editor applicable to this hyperlink detection */
    private static IEditorPart getEditor() {
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

    /** Returns the file where the link request originated */
    private static IFile getFile() {
        IEditorPart editor = getEditor();
        if (editor != null) {
            IEditorInput input = editor.getEditorInput();
            if (input instanceof IFileEditorInput) {
                IFileEditorInput fileInput = (IFileEditorInput) input;
                return fileInput.getFile();
            }
        }

        return null;
    }

    /** Returns the project applicable to this hyperlink detection */
    private static IProject getProject() {
        IFile file = getFile();
        if (file != null) {
            return file.getProject();
        }

        return null;
    }

    /**
     * Hyperlink implementation which delays computing the actual file and offset target
     * until it is asked to open the hyperlink
     */
    private static class DeferredResolutionLink implements IHyperlink {
        private String mResourceUrl;
        private XmlContext mXmlContext;
        private IRegion mRegion;

        public DeferredResolutionLink(String resourceName,
                XmlContext xmlContext, IRegion mRegion) {
            super();
            this.mResourceUrl = resourceName;
            this.mXmlContext = xmlContext;
            this.mRegion = mRegion;
        }

        public IRegion getHyperlinkRegion() {
            return mRegion;
        }

        public String getHyperlinkText() {
            return "Open XML Declaration";
        }

        public String getTypeLabel() {
            return null;
        }

        public void open() {
            // Lazily compute the location to open
            IProject project = Hyperlinks.getProject();
            if (mResourceUrl != null) {
                if (!openResourceUrl(project, mResourceUrl)) {
                    // Failed: display message to the user
                    String message = String.format("Could not open %1$s", mResourceUrl);
                    IEditorSite editorSite = Hyperlinks.getEditor().getEditorSite();
                    IStatusLineManager status = editorSite.getActionBars().getStatusLineManager();
                    status.setErrorMessage(message);
                }
                return;
            }

            if (!Hyperlinks.open(mXmlContext)) {
                // Failed: display message to the user
                String message = String.format("Could not open link");
                IEditorSite editorSite = getEditor().getEditorSite();
                IStatusLineManager status = editorSite.getActionBars().getStatusLineManager();
                status.setErrorMessage(message);
            }
        }
    }

    /**
     * XML context containing node, potentially attribute, and text regions surrounding a
     * particular caret offset
     */
    private static class XmlContext {
        private final Element mElement;
        private final Attr mAttribute;
        private final IStructuredDocumentRegion mOuterRegion;
        private final ITextRegion mInnerRegion;

        public XmlContext(Element element, Attr attribute, IStructuredDocumentRegion outerRegion,
                ITextRegion innerRegion) {
            super();
            mElement = element;
            mAttribute = attribute;
            mOuterRegion = outerRegion;
            mInnerRegion = innerRegion;
        }

        /**
         * Gets the current node, never null
         *
         * @return the surrounding node
         */
        public Element getElement() {
            return mElement;
        }

        /**
         * Returns the current attribute, or null if we are not over an attribute
         *
         * @return the attribute, or null
         */
        public Attr getAttribute() {
            return mAttribute;
        }

        /**
         * Gets the region of the element
         *
         * @return the region of the surrounding element, never null
         */
        @SuppressWarnings("unused")
        public ITextRegion getElementRegion() {
            return mOuterRegion;
        }

        /**
         * Gets the inner region, which can be the tag name, an attribute name, an
         * attribute value, or some other portion of an XML element
         * @return the inner region, never null
         */
        public ITextRegion getInnerRegion() {
            return mInnerRegion;
        }

        /**
         * Returns a range with suffix whitespace stripped out
         *
         * @param document the document containing the regions
         * @return the range of the inner region, minus any whitespace at the end
         */
        public IRegion getInnerRange(IDocument document) {
            int start = mOuterRegion.getStart() + mInnerRegion.getStart();
            int length = mInnerRegion.getLength();
            try {
                String s = document.get(start, length);
                for (int i = s.length() - 1; i >= 0; i--) {
                    if (Character.isWhitespace(s.charAt(i))) {
                        length--;
                    }
                }
            } catch (BadLocationException e) {
                AdtPlugin.log(e, ""); //$NON-NLS-1$
            }
            return new Region(start, length);
        }

        /**
         * Returns the node the cursor is currently on in the document. null if no node is
         * selected
         */
        private static XmlContext find(IDocument document, int offset) {
            // Loosely based on getCurrentNode and getCurrentAttr in the WST's
            // XMLHyperlinkDetector.
            IndexedRegion inode = null;
            IStructuredModel model = null;
            try {
                model = StructuredModelManager.getModelManager().getExistingModelForRead(document);
                if (model != null) {
                    inode = model.getIndexedRegion(offset);
                    if (inode == null) {
                        inode = model.getIndexedRegion(offset - 1);
                    }

                    if (inode instanceof Element) {
                        Element element = (Element) inode;
                        Attr attribute = null;
                        if (element.hasAttributes()) {
                            NamedNodeMap attrs = element.getAttributes();
                            // go through each attribute in node and if attribute contains
                            // offset, return that attribute
                            for (int i = 0; i < attrs.getLength(); ++i) {
                                // assumption that if parent node is of type IndexedRegion,
                                // then its attributes will also be of type IndexedRegion
                                IndexedRegion attRegion = (IndexedRegion) attrs.item(i);
                                if (attRegion.contains(offset)) {
                                    attribute = (Attr) attrs.item(i);
                                    break;
                                }
                            }
                        }

                        IStructuredDocument doc = model.getStructuredDocument();
                        IStructuredDocumentRegion region = doc.getRegionAtCharacterOffset(offset);
                        if (region != null
                                && DOMRegionContext.XML_TAG_NAME.equals(region.getType())) {
                            ITextRegion subRegion = region.getRegionAtCharacterOffset(offset);
                            return new XmlContext(element, attribute, region, subRegion);
                        }
                    }
                }
            } finally {
                if (model != null) {
                    model.releaseFromRead();
                }
            }

            return null;
        }
    }

    /**
     * DOM parser which records offsets in the element nodes such that it can return
     * offsets for elements later
     */
    private static final class OffsetTrackingParser extends DOMParser {

        private static final String KEY_OFFSET = "offset"; //$NON-NLS-1$

        private static final String KEY_NODE =
            "http://apache.org/xml/properties/dom/current-element-node"; //$NON-NLS-1$

        private XMLLocator mLocator;

        public OffsetTrackingParser() throws SAXException {
            this.setFeature("http://apache.org/xml/features/dom/defer-node-expansion",//$NON-NLS-1$
                    false);
        }

        public int getOffset(Node node) {
            Integer offset = (Integer) node.getUserData(KEY_OFFSET);
            if (offset != null) {
                return offset;
            }

            return -1;
        }

        @Override
        public void startElement(QName elementQName, XMLAttributes attrList, Augmentations augs)
                throws XNIException {
            int offset = mLocator.getCharacterOffset();
            super.startElement(elementQName, attrList, augs);

            try {
                Node node = (Node) this.getProperty(KEY_NODE);
                if (node != null) {
                    node.setUserData(KEY_OFFSET, offset, null);
                }
            } catch (org.xml.sax.SAXException ex) {
                AdtPlugin.log(ex, ""); //$NON-NLS-1$
            }
        }

        @Override
        public void startDocument(XMLLocator locator, String encoding,
                NamespaceContext namespaceContext, Augmentations augs) throws XNIException {
            super.startDocument(locator, encoding, namespaceContext, augs);
            mLocator = locator;
        }
    }
}
