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

package com.android.ide.eclipse.adt.internal.actions;

import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.AndroidConstants;
import com.android.ide.eclipse.adt.internal.project.AndroidManifestHelper;
import com.android.sdklib.SdkConstants;
import com.android.sdklib.xml.AndroidManifest;
import com.android.sdklib.xml.ManifestData;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.window.Window;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.TextEditChangeGroup;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.eclipse.ltk.ui.refactoring.RefactoringWizard;
import org.eclipse.ltk.ui.refactoring.RefactoringWizardOpenOperation;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.text.edits.TextEditGroup;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWorkbenchWindowActionDelegate;
import org.eclipse.wst.sse.core.StructuredModelManager;
import org.eclipse.wst.sse.core.internal.provisional.IModelManager;
import org.eclipse.wst.sse.core.internal.provisional.text.IStructuredDocument;
import org.eclipse.wst.sse.core.internal.provisional.text.IStructuredDocumentRegion;
import org.eclipse.wst.sse.core.internal.provisional.text.ITextRegion;
import org.eclipse.wst.sse.core.internal.provisional.text.ITextRegionList;
import org.eclipse.wst.xml.core.internal.regions.DOMRegionContext;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Refactoring steps:
 * <ol>
 * <li>Update the "package" attribute of the &lt;manifest&gt; tag with the new
 * name.</li>
 * <li>Replace all values for the "android:name" attribute in the
 * &lt;application&gt; and "component class" (&lt;activity&gt;, &lt;service&gt;,
 * &lt;receiver&gt;, and &lt;provider&gt;) tags with the non-shorthand version
 * of the class name</li>
 * <li>Replace package resource imports (*.R) in .java files</li>
 * <li>Update package name in the namespace declarations (e.g. "xmlns:app")
 * used for custom styleable attributes in layout resource files</li>
 * </ol>
 * Caveat: Sometimes it is necessary to perform a project-wide
 * "Organize Imports" afterwards. (CTRL+SHIFT+O when a project has active
 * selection)
 */
public class RenamePackageAction implements IObjectActionDelegate {

    private ISelection mSelection;

    private Name mOldPackageName, mNewPackageName;

    public final static String[] MAIN_COMPONENT_TYPES = {
        AndroidManifest.NODE_ACTIVITY, AndroidManifest.NODE_SERVICE,
        AndroidManifest.NODE_RECEIVER, AndroidManifest.NODE_PROVIDER,
        AndroidManifest.NODE_APPLICATION
    };

    List<String> MAIN_COMPONENT_TYPES_LIST = Arrays.asList(MAIN_COMPONENT_TYPES);

    public final static String ANDROID_NS_URI = SdkConstants.NS_RESOURCES;

    public final static String NAMESPACE_DECLARATION_PREFIX = "xmlns:"; //$NON-NLS-1$

    IWorkbenchPart mTargetPart;

    /**
     * @see IObjectActionDelegate#setActivePart(IAction, IWorkbenchPart)
     */
    public void setActivePart(IAction action, IWorkbenchPart targetPart) {
        mTargetPart = targetPart;
    }

    public void selectionChanged(IAction action, ISelection selection) {
        mSelection = selection;
    }

    /**
     * @see IWorkbenchWindowActionDelegate#init
     */
    public void init(IWorkbenchWindow window) {
        // pass
    }

    public void run(IAction action) {

        // Prompt for refactoring on the selected project
        if (mSelection instanceof IStructuredSelection) {
            for (Iterator<?> it = ((IStructuredSelection) mSelection).iterator(); it.hasNext();) {
                Object element = it.next();
                IProject project = null;
                if (element instanceof IProject) {
                    project = (IProject) element;
                } else if (element instanceof IAdaptable) {
                    project = (IProject) ((IAdaptable) element).getAdapter(IProject.class);
                }
                if (project != null) {
                    // TODO/FIXME Uncomment this when support for Eclipse 3.4 is dropped!
                    /*
                    // It is advisable that the user saves before proceeding,
                    // revealing any compilation errors. The following lines
                    // enforce a save as a convenience.
                    RefactoringSaveHelper save_helper = new RefactoringSaveHelper(
                            RefactoringSaveHelper.SAVE_ALL_ALWAYS_ASK);
                    if (save_helper.saveEditors(AdtPlugin.getDisplay().getActiveShell())) {
                        promptNewName(project);
                    }
                     */

                    promptNewName(project);
                }
            }
        }
    }

    /*
     * Validate the new package name and start the refactoring wizard
     */
    private void promptNewName(final IProject project) {

        ManifestData manifestData = AndroidManifestHelper.parseForData(project);
        if (manifestData == null) {
            return;
        }

        final String old_package_name_string = manifestData.getPackage();

        final AST ast_validator = AST.newAST(AST.JLS3);
        mOldPackageName = ast_validator.newName(old_package_name_string);

        IInputValidator validator = new IInputValidator() {

            public String isValid(String newText) {
                try {
                    ast_validator.newName(newText);
                } catch (IllegalArgumentException e) {
                    return "Illegal package name.";
                }

                if (newText.equals(old_package_name_string))
                    return "No change.";
                else
                    return null;
            }
        };

        InputDialog dialog = new InputDialog(AdtPlugin.getDisplay().getActiveShell(),
                "Rename Application Package", "Enter new package name:", old_package_name_string,
                validator);

        if (dialog.open() == Window.OK) {
            mNewPackageName = ast_validator.newName(dialog.getValue());
            initiateAndroidPackageRefactoring(project);
        }
    }


    private void initiateAndroidPackageRefactoring(final IProject project) {

        Refactoring package_name_refactoring = new ApplicationPackageNameRefactoring(project);

        ApplicationPackageNameRefactoringWizard wizard =
            new ApplicationPackageNameRefactoringWizard(package_name_refactoring);
        RefactoringWizardOpenOperation op = new RefactoringWizardOpenOperation(wizard);
        try {
            op.run(AdtPlugin.getDisplay().getActiveShell(), package_name_refactoring.getName());
        } catch (InterruptedException e) {
            Status s = new Status(Status.ERROR, AdtPlugin.PLUGIN_ID, e.getMessage(), e);
            AdtPlugin.getDefault().getLog().log(s);
        }
    }

    TextEdit updateJavaFileImports(CompilationUnit cu) {

        ImportVisitor import_visitor = new ImportVisitor(cu.getAST());
        cu.accept(import_visitor);
        TextEdit rewritten_imports = import_visitor.getTextEdit();

        // If the import of R was potentially implicit, insert an import statement
        if (cu.getPackage().getName().getFullyQualifiedName()
                .equals(mOldPackageName.getFullyQualifiedName())) {

            ImportRewrite irw = ImportRewrite.create(cu, true);
            irw.addImport(mNewPackageName.getFullyQualifiedName() + '.'
                    + AndroidConstants.FN_RESOURCE_BASE);

            try {
                rewritten_imports.addChild( irw.rewriteImports(null) );
            } catch (MalformedTreeException e) {
                Status s = new Status(Status.ERROR, AdtPlugin.PLUGIN_ID, e.getMessage(), e);
                AdtPlugin.getDefault().getLog().log(s);
            } catch (CoreException e) {
                Status s = new Status(Status.ERROR, AdtPlugin.PLUGIN_ID, e.getMessage(), e);
                AdtPlugin.getDefault().getLog().log(s);
            }
        }

        return rewritten_imports;
    }

    // XML utility functions
    private String stripQuotes(String text) {
        int len = text.length();
        if (len >= 2 && text.charAt(0) == '"' && text.charAt(len - 1) == '"') {
            return text.substring(1, len - 1);
        } else if (len >= 2 && text.charAt(0) == '\'' && text.charAt(len - 1) == '\'') {
            return text.substring(1, len - 1);
        }
        return text;
    }

    private String addQuotes(String text) {
        return '"' + text + '"';
    }

    /*
     * Make the appropriate package name changes to a resource file,
     * e.g. .xml files in res/layout. This entails updating the namespace
     * declarations for custom styleable attributes.  The namespace prefix
     * is user-defined and may be declared in any element where or parent
     * element of where the prefix is used.
     */
    TextFileChange editXmlResourceFile(IFile file) {

        IModelManager modelManager = StructuredModelManager.getModelManager();
        IStructuredDocument sdoc = null;
        try {
            sdoc = modelManager.createStructuredDocumentFor(file);
        } catch (IOException e) {
            Status s = new Status(Status.ERROR, AdtPlugin.PLUGIN_ID, e.getMessage(), e);
            AdtPlugin.getDefault().getLog().log(s);
        } catch (CoreException e) {
            Status s = new Status(Status.ERROR, AdtPlugin.PLUGIN_ID, e.getMessage(), e);
            AdtPlugin.getDefault().getLog().log(s);
        }

        TextFileChange xmlChange = new TextFileChange("XML resource file edit", file);
        xmlChange.setTextType(AndroidConstants.EXT_XML);

        MultiTextEdit multiEdit = new MultiTextEdit();
        ArrayList<TextEditGroup> editGroups = new ArrayList<TextEditGroup>();

        final String oldAppNamespaceString = String.format(AndroidConstants.NS_CUSTOM_RESOURCES,
                mOldPackageName.getFullyQualifiedName());
        final String newAppNamespaceString = String.format(AndroidConstants.NS_CUSTOM_RESOURCES,
                mNewPackageName.getFullyQualifiedName());

        // Prepare the change set
        for (IStructuredDocumentRegion region : sdoc.getStructuredDocumentRegions()) {

            if (!DOMRegionContext.XML_TAG_NAME.equals(region.getType())) {
                continue;
            }

            int nb = region.getNumberOfRegions();
            ITextRegionList list = region.getRegions();
            String lastAttrName = null;

            for (int i = 0; i < nb; i++) {
                ITextRegion subRegion = list.get(i);
                String type = subRegion.getType();

                if (DOMRegionContext.XML_TAG_ATTRIBUTE_NAME.equals(type)) {
                    // Memorize the last attribute name seen
                    lastAttrName = region.getText(subRegion);

                } else if (DOMRegionContext.XML_TAG_ATTRIBUTE_VALUE.equals(type)) {
                    // Check this is the attribute and the original string

                    if (lastAttrName.startsWith(NAMESPACE_DECLARATION_PREFIX)) {

                        String lastAttrValue = region.getText(subRegion);
                        if (oldAppNamespaceString.equals(stripQuotes(lastAttrValue))) {

                            // Found an occurrence. Create a change for it.
                            TextEdit edit = new ReplaceEdit(region.getStartOffset()
                                    + subRegion.getStart(), subRegion.getTextLength(),
                                    addQuotes(newAppNamespaceString));
                            TextEditGroup editGroup = new TextEditGroup(
                                    "Replace package name in custom namespace prefix", edit);

                            multiEdit.addChild(edit);
                            editGroups.add(editGroup);
                        }
                    }
                }
            }
        }

        if (multiEdit.hasChildren()) {
            xmlChange.setEdit(multiEdit);
            for (TextEditGroup group : editGroups) {
                xmlChange.addTextEditChangeGroup(new TextEditChangeGroup(xmlChange, group));
            }

            return xmlChange;
        }
        return null;
    }

    /*
     * Replace all instances of the package name in AndroidManifest.xml.
     * This includes expanding shorthand paths for each Component (Activity,
     * Service, etc.) and of course updating the application package name.
     * The namespace prefix might not be "android", so we resolve it
     * dynamically.
     */
    TextFileChange editAndroidManifest(IFile file) {

        IModelManager modelManager = StructuredModelManager.getModelManager();
        IStructuredDocument sdoc = null;
        try {
            sdoc = modelManager.createStructuredDocumentFor(file);
        } catch (IOException e) {
            Status s = new Status(Status.ERROR, AdtPlugin.PLUGIN_ID, e.getMessage(), e);
            AdtPlugin.getDefault().getLog().log(s);
        } catch (CoreException e) {
            Status s = new Status(Status.ERROR, AdtPlugin.PLUGIN_ID, e.getMessage(), e);
            AdtPlugin.getDefault().getLog().log(s);
        }

        TextFileChange xmlChange = new TextFileChange("Make Manifest edits", file);
        xmlChange.setTextType(AndroidConstants.EXT_XML);

        MultiTextEdit multiEdit = new MultiTextEdit();
        ArrayList<TextEditGroup> editGroups = new ArrayList<TextEditGroup>();

        // The namespace prefix is guaranteed to be resolved before
        // the first use of this attribute
        String android_name_attribute = null;

        // Prepare the change set
        for (IStructuredDocumentRegion region : sdoc.getStructuredDocumentRegions()) {

            // Only look at XML "top regions"
            if (!DOMRegionContext.XML_TAG_NAME.equals(region.getType())) {
                continue;
            }

            int nb = region.getNumberOfRegions();
            ITextRegionList list = region.getRegions();
            String lastTagName = null, lastAttrName = null;

            for (int i = 0; i < nb; i++) {
                ITextRegion subRegion = list.get(i);
                String type = subRegion.getType();

                if (DOMRegionContext.XML_TAG_NAME.equals(type)) {
                    // Memorize the last tag name seen
                    lastTagName = region.getText(subRegion);

                } else if (DOMRegionContext.XML_TAG_ATTRIBUTE_NAME.equals(type)) {
                    // Memorize the last attribute name seen
                    lastAttrName = region.getText(subRegion);

                } else if (DOMRegionContext.XML_TAG_ATTRIBUTE_VALUE.equals(type)) {

                    String lastAttrValue = region.getText(subRegion);
                    if (lastAttrName.startsWith(NAMESPACE_DECLARATION_PREFIX)) {

                        // Resolves the android namespace prefix for this file
                        if (ANDROID_NS_URI.equals(stripQuotes(lastAttrValue))) {
                            String android_namespace_prefix = lastAttrName
                                .substring(NAMESPACE_DECLARATION_PREFIX.length());
                            android_name_attribute = android_namespace_prefix + ':'
                                + AndroidManifest.ATTRIBUTE_NAME;
                        }
                    }

                    else if (AndroidManifest.NODE_MANIFEST.equals(lastTagName)
                            && AndroidManifest.ATTRIBUTE_PACKAGE.equals(lastAttrName)) {

                        // Found an occurrence. Create a change for it.
                        TextEdit edit = new ReplaceEdit(region.getStartOffset()
                                + subRegion.getStart(), subRegion.getTextLength(),
                                addQuotes(mNewPackageName.getFullyQualifiedName()));

                        multiEdit.addChild(edit);
                        editGroups.add(new TextEditGroup("Change Android package name", edit));

                    } else if (MAIN_COMPONENT_TYPES_LIST.contains(lastTagName)
                            && lastAttrName.equals(android_name_attribute)) {

                        String package_path = stripQuotes(lastAttrValue);
                        String old_package_name_string = mOldPackageName.getFullyQualifiedName();

                        String absolute_path = AndroidManifest.combinePackageAndClassName(
                                old_package_name_string, package_path);

                        TextEdit edit = new ReplaceEdit(region.getStartOffset()
                                + subRegion.getStart(), subRegion.getTextLength(),
                                addQuotes(absolute_path));

                        multiEdit.addChild(edit);

                        editGroups.add(new TextEditGroup("Update component path", edit));
                    }
                }
            }
        }

        if (multiEdit.hasChildren()) {
            xmlChange.setEdit(multiEdit);
            for (TextEditGroup group : editGroups) {
                xmlChange.addTextEditChangeGroup(new TextEditChangeGroup(xmlChange, group));
            }

            return xmlChange;
        }
        return null;
    }


    /*
     * Iterates through all project files, taking distinct actions based on
     * whether the file is:
     * 1) a .java file (replaces or inserts the "import" statements)
     * 2) a .xml layout file (updates namespace declarations)
     * 3) the AndroidManifest.xml
     */
    class JavaFileVisitor implements IResourceVisitor {

        final List<TextFileChange> mChanges = new ArrayList<TextFileChange>();

        final ASTParser mParser = ASTParser.newParser(AST.JLS3);

        public CompositeChange getChange() {

            Collections.reverse(mChanges);
            CompositeChange change = new CompositeChange("Refactoring Application package name",
                    mChanges.toArray(new Change[mChanges.size()]));
            return change;
        }

        public boolean visit(IResource resource) throws CoreException {
            if (resource instanceof IFile) {
                IFile file = (IFile) resource;
                if (AndroidConstants.EXT_JAVA.equals(file.getFileExtension())) {

                    ICompilationUnit icu = JavaCore.createCompilationUnitFrom(file);

                    mParser.setSource(icu);
                    CompilationUnit cu = (CompilationUnit) mParser.createAST(null);

                    TextEdit text_edit = updateJavaFileImports(cu);
                    if (text_edit.hasChildren()) {
                        MultiTextEdit edit = new MultiTextEdit();
                        edit.addChild(text_edit);

                        TextFileChange text_file_change = new TextFileChange(file.getName(), file);
                        text_file_change.setTextType(AndroidConstants.EXT_JAVA);
                        text_file_change.setEdit(edit);
                        mChanges.add(text_file_change);
                    }

                    // XXX Partially taken from ExtractStringRefactoring.java
                    // Check this a Layout XML file and get the selection and
                    // its context.
                } else if (AndroidConstants.EXT_XML.equals(file.getFileExtension())) {

                    if (SdkConstants.FN_ANDROID_MANIFEST_XML.equals(file.getName())) {

                        TextFileChange manifest_change = editAndroidManifest(file);
                        mChanges.add(manifest_change);

                    } else {

                        // Currently we only support Android resource XML files,
                        // so they must have a path
                        // similar to
                        // project/res/<type>[-<configuration>]/*.xml
                        // There is no support for sub folders, so the segment
                        // count must be 4.
                        // We don't need to check the type folder name because
                        // a/ we only accept
                        // an AndroidXmlEditor source and b/ aapt generates a
                        // compilation error for
                        // unknown folders.
                        IPath path = file.getFullPath();
                        // check if we are inside the project/res/* folder.
                        if (path.segmentCount() == 4) {
                            if (path.segment(1).equalsIgnoreCase(SdkConstants.FD_RESOURCES)) {


                                TextFileChange xmlChange = editXmlResourceFile(file);
                                if (xmlChange != null) {
                                    mChanges.add(xmlChange);
                                }
                            }
                        }
                    }
                }

                return false;

            } else if (resource instanceof IFolder) {
                return !SdkConstants.FD_GEN_SOURCES.equals(resource.getName());
            }

            return true;
        }
    }

    class ImportVisitor extends ASTVisitor {

        final AST mAst;
        final ASTRewrite mRewriter;

        ImportVisitor(AST ast) {
            mAst = ast;
            mRewriter = ASTRewrite.create(ast);
        }

        public TextEdit getTextEdit() {
            try {
                return this.mRewriter.rewriteAST();
            } catch (JavaModelException e) {
                Status s = new Status(Status.ERROR, AdtPlugin.PLUGIN_ID, e.getMessage(), e);
                AdtPlugin.getDefault().getLog().log(s);
            } catch (IllegalArgumentException e) {
                Status s = new Status(Status.ERROR, AdtPlugin.PLUGIN_ID, e.getMessage(), e);
                AdtPlugin.getDefault().getLog().log(s);
            }
            return null;
        }

        @Override
        public boolean visit(ImportDeclaration id) {

            Name import_name = id.getName();
            if (import_name.isQualifiedName()) {
                QualifiedName qualified_import_name = (QualifiedName) import_name;

                if (qualified_import_name.getName().getIdentifier()
                        .equals(AndroidConstants.FN_RESOURCE_BASE)) {
                    mRewriter.replace(qualified_import_name.getQualifier(), mNewPackageName,
                            null);
                }
            }

            return true;
        }
    }

    class ApplicationPackageNameRefactoringWizard extends RefactoringWizard {

        public ApplicationPackageNameRefactoringWizard(Refactoring refactoring) {
            super(refactoring, 0);
        }

        @Override
        protected void addUserInputPages() {
        }
    }

    /*
     *  Wrapper class defining the stages of the refactoring process
     */
    class ApplicationPackageNameRefactoring extends Refactoring {

        IProject mProject;

        ApplicationPackageNameRefactoring(final IProject project) {
            mProject = project;
        }

        @Override
        public RefactoringStatus checkFinalConditions(IProgressMonitor pm) throws CoreException,
        OperationCanceledException {

            return new RefactoringStatus();
        }

        @Override
        public RefactoringStatus checkInitialConditions(IProgressMonitor pm) throws CoreException,
        OperationCanceledException {

            // Accurate refactoring of the "shorthand" names in
            // AndroidManifest.xml
            // depends on not having compilation errors.
            if (mProject.findMaxProblemSeverity(
                    IMarker.PROBLEM,
                    true,
                    IResource.DEPTH_INFINITE) == IMarker.SEVERITY_ERROR) {
                return RefactoringStatus
                .createFatalErrorStatus("Fix the errors in your project, first.");
            }

            return new RefactoringStatus();
        }

        @Override
        public Change createChange(IProgressMonitor pm) throws CoreException,
        OperationCanceledException {

            // Traverse all files in the project, building up a list of changes
            JavaFileVisitor file_visitor = new JavaFileVisitor();
            mProject.accept(file_visitor);
            return file_visitor.getChange();
        }

        @Override
        public String getName() {
            return "AndroidPackageNameRefactoring"; //$NON-NLS-1$
        }
    }
}
