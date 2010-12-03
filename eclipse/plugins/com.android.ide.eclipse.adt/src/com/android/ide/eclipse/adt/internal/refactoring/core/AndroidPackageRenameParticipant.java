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

import com.android.ide.eclipse.adt.AndroidConstants;
import com.android.ide.eclipse.adt.internal.project.AndroidManifestHelper;
import com.android.ide.eclipse.adt.internal.refactoring.changes.AndroidLayoutChange;
import com.android.ide.eclipse.adt.internal.refactoring.changes.AndroidLayoutChangeDescription;
import com.android.ide.eclipse.adt.internal.refactoring.changes.AndroidLayoutFileChanges;
import com.android.ide.eclipse.adt.internal.refactoring.changes.AndroidPackageRenameChange;
import com.android.sdklib.SdkConstants;
import com.android.sdklib.xml.AndroidManifest;
import com.android.sdklib.xml.ManifestData;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.filebuffers.ITextFileBufferManager;
import org.eclipse.core.filebuffers.LocationKind;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.eclipse.jdt.internal.corext.refactoring.changes.RenamePackageChange;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jface.text.IDocument;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.wst.sse.core.StructuredModelManager;
import org.eclipse.wst.sse.core.internal.provisional.IStructuredModel;
import org.eclipse.wst.sse.core.internal.provisional.text.IStructuredDocument;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMDocument;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMModel;
import org.w3c.dom.Attr;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A participant to participate in refactorings that rename a package in an Android project.
 * The class updates android manifest and the layout file
 * The user can suppress refactoring by disabling the "Update references" checkbox
 * <p>
 * Rename participants are registered via the extension point <code>
 * org.eclipse.ltk.core.refactoring.renameParticipants</code>.
 * Extensions to this extension point must therefore extend
 * <code>org.eclipse.ltk.core.refactoring.participants.RenameParticipant</code>.
 * </p>
 */
public class AndroidPackageRenameParticipant extends AndroidRenameParticipant {

    private IPackageFragment mPackageFragment;

    private boolean mIsPackage;

    private Set<AndroidLayoutFileChanges> mFileChanges = new HashSet<AndroidLayoutFileChanges>();

    /*
     * (non-Javadoc)
     * @see
     * org.eclipse.ltk.core.refactoring.participants.RefactoringParticipant#
     * createChange(org.eclipse.core.runtime.IProgressMonitor)
     */
    @Override
    public Change createChange(IProgressMonitor pm) throws CoreException,
            OperationCanceledException {
        if (pm.isCanceled()) {
            return null;
        }
        if (!getArguments().getUpdateReferences())
            return null;
        IPath pkgPath = mPackageFragment.getPath();
        IJavaProject javaProject = (IJavaProject) mPackageFragment
                .getAncestor(IJavaElement.JAVA_PROJECT);
        IProject project = javaProject.getProject();
        IPath genPath = project.getFullPath().append(SdkConstants.FD_GEN_SOURCES);
        if (genPath.isPrefixOf(pkgPath)) {
            RefactoringUtil.logInfo(getName() + ": Cannot rename generated package.");
            return null;
        }
        CompositeChange result = new CompositeChange(getName());
        if (mAndroidManifest.exists()) {
            if (mAndroidElements.size() > 0 || mIsPackage) {
                getDocument();
                Change change = new AndroidPackageRenameChange(mAndroidManifest, mManager,
                        mDocument, mAndroidElements, mNewName, mOldName, mIsPackage);
                if (change != null) {
                    result.add(change);
                }
            }
            if (mIsPackage) {
                Change genChange = getGenPackageChange(pm);
                if (genChange != null) {
                    result.add(genChange);
                }
            }
            // add layoutChange
            for (AndroidLayoutFileChanges fileChange : mFileChanges) {
                IFile file = fileChange.getFile();
                ITextFileBufferManager lManager = FileBuffers.getTextFileBufferManager();
                lManager.connect(file.getFullPath(), LocationKind.NORMALIZE,
                        new NullProgressMonitor());
                ITextFileBuffer buffer = lManager.getTextFileBuffer(file.getFullPath(),
                        LocationKind.NORMALIZE);
                IDocument lDocument = buffer.getDocument();
                Change layoutChange = new AndroidLayoutChange(file, lDocument, lManager,
                        fileChange.getChanges());
                if (layoutChange != null) {
                    result.add(layoutChange);
                }
            }
        }
        return (result.getChildren().length == 0) ? null : result;
    }

    /**
     * Returns Android gen package text change
     *
     * @param pm the progress monitor
     *
     * @return Android gen package text change
     * @throws CoreException
     * @throws OperationCanceledException
     */
    public Change getGenPackageChange(IProgressMonitor pm) throws CoreException,
            OperationCanceledException {
        if (mIsPackage) {
            IPackageFragment genJavaPackageFragment = getGenPackageFragment();
            if (genJavaPackageFragment != null && genJavaPackageFragment.exists()) {
                return new RenamePackageChange(genJavaPackageFragment, mNewName, true);
            }
        }
        return null;
    }

    /*
     * (non-Javadoc) return the gen package fragment
     *
     */
    private IPackageFragment getGenPackageFragment() throws JavaModelException {
        IJavaProject javaProject = (IJavaProject) mPackageFragment
                .getAncestor(IJavaElement.JAVA_PROJECT);
        if (javaProject != null && javaProject.isOpen()) {
            IProject project = javaProject.getProject();
            IFolder genFolder = project.getFolder(SdkConstants.FD_GEN_SOURCES);
            if (genFolder.exists()) {
                String javaPackagePath = mAppPackage.replace(".", "/");
                IPath genJavaPackagePath = genFolder.getFullPath().append(javaPackagePath);
                IPackageFragment genPackageFragment = javaProject
                        .findPackageFragment(genJavaPackagePath);
                return genPackageFragment;
            }
        }
        return null;
    }

    /*
     * (non-Javadoc)
     * @see
     * org.eclipse.ltk.core.refactoring.participants.RefactoringParticipant#
     * getName()
     */
    @Override
    public String getName() {
        return "Android Package Rename";
    }

    /*
     * (non-Javadoc)
     * @see
     * org.eclipse.ltk.core.refactoring.participants.RefactoringParticipant#
     * initialize(java.lang.Object)
     */
    @Override
    protected boolean initialize(final Object element) {
        mIsPackage = false;
        try {
            if (element instanceof IPackageFragment) {
                mPackageFragment = (IPackageFragment) element;
                if (!mPackageFragment.containsJavaResources())
                    return false;
                IJavaProject javaProject = (IJavaProject) mPackageFragment
                        .getAncestor(IJavaElement.JAVA_PROJECT);
                IProject project = javaProject.getProject();
                IResource manifestResource = project.findMember(AndroidConstants.WS_SEP
                        + SdkConstants.FN_ANDROID_MANIFEST_XML);

                if (manifestResource == null || !manifestResource.exists()
                        || !(manifestResource instanceof IFile)) {
                    RefactoringUtil.logInfo("Invalid or missing the "
                            + SdkConstants.FN_ANDROID_MANIFEST_XML + " in the " + project.getName()
                            + " project.");
                    return false;
                }
                mAndroidManifest = (IFile) manifestResource;
                String packageName = mPackageFragment.getElementName();
                ManifestData manifestData;
                manifestData = AndroidManifestHelper.parseForData(mAndroidManifest);
                if (manifestData == null) {
                    return false;
                }
                mAppPackage = manifestData.getPackage();
                mOldName = packageName;
                mNewName = getArguments().getNewName();
                if (mOldName == null || mNewName == null) {
                    return false;
                }

                if (RefactoringUtil.isRefactorAppPackage()
                        && mAppPackage != null
                        && mAppPackage.equals(packageName)) {
                    mIsPackage = true;
                }
                mAndroidElements = addAndroidElements();
                try {
                    final IType type = javaProject.findType(SdkConstants.CLASS_VIEW);
                    SearchPattern pattern = SearchPattern.createPattern("*",
                            IJavaSearchConstants.TYPE, IJavaSearchConstants.DECLARATIONS,
                            SearchPattern.R_REGEXP_MATCH);
                    IJavaSearchScope scope =SearchEngine.createJavaSearchScope(
                            new IJavaElement[] { mPackageFragment });
                    final HashSet<IType> elements = new HashSet<IType>();
                    SearchRequestor requestor = new SearchRequestor() {

                        @Override
                        public void acceptSearchMatch(SearchMatch match) throws CoreException {
                            Object elem = match.getElement();
                            if (elem instanceof IType) {
                                IType eType = (IType) elem;
                                IType[] superTypes = JavaModelUtil.getAllSuperTypes(eType,
                                        new NullProgressMonitor());
                                for (int i = 0; i < superTypes.length; i++) {
                                    if (superTypes[i].equals(type)) {
                                        elements.add(eType);
                                        break;
                                    }
                                }
                            }

                        }
                    };
                    SearchEngine searchEngine = new SearchEngine();
                    searchEngine.search(pattern, new SearchParticipant[] {
                        SearchEngine.getDefaultSearchParticipant()
                    }, scope, requestor, null);
                    List<String> views = new ArrayList<String>();
                    for (IType elem : elements) {
                        views.add(elem.getFullyQualifiedName());
                    }
                    if (views.size() > 0) {
                        String[] classNames = views.toArray(new String[0]);
                        addLayoutChanges(project, classNames);
                    }
                } catch (CoreException e) {
                    RefactoringUtil.log(e);
                }

                return mIsPackage || mAndroidElements.size() > 0 || mFileChanges.size() > 0;
            }
        } catch (JavaModelException ignore) {
        }
        return false;
    }

    /**
     * (non-Javadoc) Adds layout changes for project
     *
     * @param project the Android project
     * @param classNames the layout classes
     *
     */
    private void addLayoutChanges(IProject project, String[] classNames) {
        try {
            IFolder resFolder = project.getFolder(SdkConstants.FD_RESOURCES);
            IResource[] layoutMembers = resFolder.members();
            for (int j = 0; j < layoutMembers.length; j++) {
                IResource resource = layoutMembers[j];
                if (resource instanceof IFolder
                        && resource.exists()
                        && resource.getName().startsWith(SdkConstants.FD_LAYOUT)) {
                    IFolder layoutFolder = (IFolder) resource;
                    IResource[] members = layoutFolder.members();
                    for (int i = 0; i < members.length; i++) {
                           IResource member = members[i];
                           if ((member instanceof IFile)
                                   && member.exists()
                                   && member.getName().endsWith(".xml")) { //$NON-NLS-1$
                               IFile file = (IFile) member;
                               Set<AndroidLayoutChangeDescription> changes =
                                   parse(file, classNames);
                               if (changes.size() > 0) {
                                   AndroidLayoutFileChanges fileChange =
                                       new AndroidLayoutFileChanges(file);
                                   fileChange.getChanges().addAll(changes);
                                   mFileChanges.add(fileChange);
                               }
                           }
                    }
                }
            }
        } catch (CoreException e) {
            RefactoringUtil.log(e);
        }
    }

    /**
     * (non-Javadoc) Searches the layout file for classes
     *
     * @param file the Android layout file
     * @param classNames the layout classes
     *
     */
    private Set<AndroidLayoutChangeDescription> parse(IFile file, String[] classNames) {
        Set<AndroidLayoutChangeDescription> changes =
            new HashSet<AndroidLayoutChangeDescription>();
        ITextFileBufferManager lManager = null;
        try {
            lManager = FileBuffers.getTextFileBufferManager();
            lManager.connect(file.getFullPath(),
                    LocationKind.NORMALIZE, new NullProgressMonitor());
            ITextFileBuffer buffer = lManager.getTextFileBuffer(file.getFullPath(),
                    LocationKind.NORMALIZE);
            IDocument lDocument = buffer.getDocument();
            IStructuredModel model = null;
            try {
                model = StructuredModelManager.getModelManager().
                    getExistingModelForRead(lDocument);
                if (model == null) {
                    if (lDocument instanceof IStructuredDocument) {
                        IStructuredDocument structuredDocument = (IStructuredDocument) lDocument;
                        model = StructuredModelManager.getModelManager().getModelForRead(
                                structuredDocument);
                    }
                }
                if (model != null) {
                    IDOMModel xmlModel = (IDOMModel) model;
                    IDOMDocument xmlDoc = xmlModel.getDocument();
                    NodeList nodes = xmlDoc
                            .getElementsByTagName(IConstants.ANDROID_LAYOUT_VIEW_ELEMENT);
                    for (int i = 0; i < nodes.getLength(); i++) {
                        Node node = nodes.item(i);
                        NamedNodeMap attributes = node.getAttributes();
                        if (attributes != null) {
                            Node attributeNode = attributes
                                    .getNamedItem(IConstants.ANDROID_LAYOUT_CLASS_ARGUMENT);
                            if (attributeNode != null || attributeNode instanceof Attr) {
                                Attr attribute = (Attr) attributeNode;
                                String value = attribute.getValue();
                                if (value != null) {
                                    for (int j = 0; j < classNames.length; j++) {
                                        String className = classNames[j];
                                        if (value.equals(className)) {
                                            String newClassName = getNewClassName(className);
                                            AndroidLayoutChangeDescription layoutChange =
                                                new AndroidLayoutChangeDescription(
                                                    className, newClassName,
                                                    AndroidLayoutChangeDescription.VIEW_TYPE);
                                            changes.add(layoutChange);
                                        }
                                    }
                                }
                            }
                        }
                    }
                    for (int j = 0; j < classNames.length; j++) {
                        String className = classNames[j];
                        nodes = xmlDoc.getElementsByTagName(className);
                        for (int i = 0; i < nodes.getLength(); i++) {
                            String newClassName = getNewClassName(className);
                            AndroidLayoutChangeDescription layoutChange =
                                new AndroidLayoutChangeDescription(
                                    className, newClassName,
                                    AndroidLayoutChangeDescription.STANDALONE_TYPE);
                            changes.add(layoutChange);
                        }
                    }
                }
            } finally {
                if (model != null) {
                    model.releaseFromRead();
                }
            }

        } catch (CoreException ignore) {
        } finally {
            if (lManager != null) {
                try {
                    lManager.disconnect(file.getFullPath(), LocationKind.NORMALIZE,
                            new NullProgressMonitor());
                } catch (CoreException ignore) {
                }
            }
        }
        return changes;
    }

    /**
     * (non-Javadoc) Returns the new class name
     *
     * @param className the class name
     * @return the new class name
     *
     */
    private String getNewClassName(String className) {
        int lastDot = className.lastIndexOf("."); //$NON-NLS-1$
        if (lastDot < 0) {
            return mNewName;
        }
        String name = className.substring(lastDot, className.length());
        String newClassName = mNewName + name;
        return newClassName;
    }

    /**
     * (non-Javadoc) Returns the elements (activity, receiver, service ...)
     * which have to be renamed
     *
     * @return the android elements
     *
     */
    private Map<String, String> addAndroidElements() {
        Map<String, String> androidElements = new HashMap<String, String>();

        IDocument document;
        try {
            document = getDocument();
        } catch (CoreException e) {
            RefactoringUtil.log(e);
            if (mManager != null) {
                try {
                    mManager.disconnect(mAndroidManifest.getFullPath(), LocationKind.NORMALIZE,
                            new NullProgressMonitor());
                } catch (CoreException e1) {
                    RefactoringUtil.log(e1);
                }
            }
            document = null;
            return androidElements;
        }

        IStructuredModel model = null;
        try {
            model = StructuredModelManager.getModelManager().getExistingModelForRead(document);
            if (model == null) {
                if (document instanceof IStructuredDocument) {
                    IStructuredDocument structuredDocument = (IStructuredDocument) document;
                    model = StructuredModelManager.getModelManager().getModelForRead(
                            structuredDocument);
                }
            }
            if (model != null) {
                IDOMModel xmlModel = (IDOMModel) model;
                IDOMDocument xmlDoc = xmlModel.getDocument();
                add(xmlDoc, androidElements, AndroidManifest.NODE_ACTIVITY,
                        AndroidManifest.ATTRIBUTE_NAME);
                add(xmlDoc, androidElements, AndroidManifest.NODE_APPLICATION,
                        AndroidManifest.ATTRIBUTE_NAME);
                add(xmlDoc, androidElements, AndroidManifest.NODE_PROVIDER,
                        AndroidManifest.ATTRIBUTE_NAME);
                add(xmlDoc, androidElements, AndroidManifest.NODE_RECEIVER,
                        AndroidManifest.ATTRIBUTE_NAME);
                add(xmlDoc, androidElements, AndroidManifest.NODE_SERVICE,
                        AndroidManifest.ATTRIBUTE_NAME);
            }
        } finally {
            if (model != null) {
                model.releaseFromRead();
            }
        }

        return androidElements;
    }

    /**
     * (non-Javadoc) Adds the element  (activity, receiver, service ...) to the map
     *
     * @param xmlDoc the document
     * @param androidElements the map
     * @param element the element
     */
    private void add(IDOMDocument xmlDoc, Map<String, String> androidElements, String element,
            String argument) {
        NodeList nodes = xmlDoc.getElementsByTagName(element);
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            NamedNodeMap attributes = node.getAttributes();
            if (attributes != null) {
                Attr attribute = RefactoringUtil.findAndroidAttributes(attributes, argument);
                if (attribute != null) {
                    String value = attribute.getValue();
                    if (value != null) {
                        String fullName = AndroidManifest.combinePackageAndClassName(mAppPackage,
                                value);
                        if (RefactoringUtil.isRefactorAppPackage()) {
                            if (fullName != null && fullName.startsWith(mAppPackage)) {
                                boolean startWithDot = (value.charAt(0) == '.');
                                boolean hasDot = (value.indexOf('.') != -1);
                                if (!startWithDot && hasDot) {
                                    androidElements.put(element, value);
                                }
                            }
                        } else {
                            if (fullName != null) {
                                androidElements.put(element, value);
                            }
                        }
                    }
                }
            }
        }
    }

}
