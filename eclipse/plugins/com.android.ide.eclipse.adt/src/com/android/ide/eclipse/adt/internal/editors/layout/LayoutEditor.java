/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.ide.eclipse.adt.internal.editors.layout;

import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.AndroidConstants;
import com.android.ide.eclipse.adt.internal.editors.AndroidXmlEditor;
import com.android.ide.eclipse.adt.internal.editors.descriptors.DocumentDescriptor;
import com.android.ide.eclipse.adt.internal.editors.descriptors.ElementDescriptor;
import com.android.ide.eclipse.adt.internal.editors.descriptors.IUnknownDescriptorProvider;
import com.android.ide.eclipse.adt.internal.editors.layout.descriptors.CustomViewDescriptorService;
import com.android.ide.eclipse.adt.internal.editors.layout.descriptors.ViewElementDescriptor;
import com.android.ide.eclipse.adt.internal.editors.layout.gle1.GraphicalLayoutEditor;
import com.android.ide.eclipse.adt.internal.editors.layout.gle1.UiContentOutlinePage;
import com.android.ide.eclipse.adt.internal.editors.layout.gle1.UiPropertySheetPage;
import com.android.ide.eclipse.adt.internal.editors.layout.gle2.OutlinePage2;
import com.android.ide.eclipse.adt.internal.editors.layout.gle2.GraphicalEditorPart;
import com.android.ide.eclipse.adt.internal.editors.layout.gle2.PropertySheetPage2;
import com.android.ide.eclipse.adt.internal.editors.ui.tree.UiActions;
import com.android.ide.eclipse.adt.internal.editors.uimodel.UiDocumentNode;
import com.android.ide.eclipse.adt.internal.editors.uimodel.UiElementNode;
import com.android.ide.eclipse.adt.internal.sdk.AndroidTargetData;
import com.android.ide.eclipse.adt.internal.sdk.Sdk;
import com.android.sdklib.IAndroidTarget;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.gef.ui.parts.TreeViewer;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IPartListener;
import org.eclipse.ui.IShowEditorInput;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchPartSite;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.views.contentoutline.IContentOutlinePage;
import org.eclipse.ui.views.properties.IPropertySheetPage;
import org.w3c.dom.Document;

import java.util.HashMap;

/**
 * Multi-page form editor for /res/layout XML files.
 */
public class LayoutEditor extends AndroidXmlEditor implements IShowEditorInput, IPartListener {

    public static final String ID = AndroidConstants.EDITORS_NAMESPACE + ".layout.LayoutEditor"; //$NON-NLS-1$

    /** Root node of the UI element hierarchy */
    private UiDocumentNode mUiRootNode;

    private IGraphicalLayoutEditor mGraphicalEditor;
    private int mGraphicalEditorIndex;
    /**
     * Implementation of the {@link IContentOutlinePage} for this editor.
     * @deprecated Used for backward compatibility with GLE1.
     */
    private UiContentOutlinePage mOutlineForGle1;
    /** Implementation of the {@link IContentOutlinePage} for this editor */
    private IContentOutlinePage mOutline;
    /** Custom implementation of {@link IPropertySheetPage} for this editor */
    private IPropertySheetPage mPropertyPage;

    private UiEditorActions mUiEditorActions;

    private final HashMap<String, ElementDescriptor> mUnknownDescriptorMap =
        new HashMap<String, ElementDescriptor>();


    /**
     * Flag indicating if the replacement file is due to a config change.
     * If false, it means the new file is due to an "open action" from the user.
     */
    private boolean mNewFileOnConfigChange = false;

    /**
     * Creates the form editor for resources XML files.
     */
    public LayoutEditor() {
        super(false /* addTargetListener */);
    }

    /**
     * @return The root node of the UI element hierarchy
     */
    @Override
    public UiDocumentNode getUiRootNode() {
        return mUiRootNode;
    }

    // ---- Base Class Overrides ----

    @Override
    public void dispose() {
        getSite().getPage().removePartListener(this);

        super.dispose();
    }

    /**
     * Save the XML.
     * <p/>
     * The actual save operation is done in the super class by committing
     * all data to the XML model and then having the Structured XML Editor
     * save the XML.
     * <p/>
     * Here we just need to tell the graphical editor that the model has
     * been saved.
     */
    @Override
    public void doSave(IProgressMonitor monitor) {
        super.doSave(monitor);
        if (mGraphicalEditor != null) {
            mGraphicalEditor.doSave(monitor);
        }
    }

    /**
     * Returns whether the "save as" operation is supported by this editor.
     * <p/>
     * Save-As is a valid operation for the ManifestEditor since it acts on a
     * single source file.
     *
     * @see IEditorPart
     */
    @Override
    public boolean isSaveAsAllowed() {
        return true;
    }

    /**
     * Create the various form pages.
     */
    @Override
    protected void createFormPages() {
        try {
            // The graphical layout editor is now enabled by default.
            // In case there's an issue we provide a way to disable it using an
            // env variable.
            if (System.getenv("ANDROID_DISABLE_LAYOUT") == null) {      //$NON-NLS-1$
                // get the file being edited so that it can be passed to the layout editor.
                IFile editedFile = null;
                IEditorInput input = getEditorInput();
                if (input instanceof FileEditorInput) {
                    FileEditorInput fileInput = (FileEditorInput)input;
                    editedFile = fileInput.getFile();
                } else {
                    AdtPlugin.log(IStatus.ERROR,
                            "Input is not of type FileEditorInput: %1$s",  //$NON-NLS-1$
                            input.toString());
                }

                // It is possible that the Layout Editor already exits if a different version
                // of the same layout is being opened (either through "open" action from
                // the user, or through a configuration change in the configuration selector.)
                if (mGraphicalEditor == null) {

                    String useGle2 = System.getenv("USE_GLE2");     //$NON-NLS-1$

                    if (useGle2 != null && !useGle2.equals("0")) {  //$NON-NLS-1$
                        mGraphicalEditor = new GraphicalEditorPart(this);
                    } else {
                        mGraphicalEditor = new GraphicalLayoutEditor(this);
                    }

                    mGraphicalEditorIndex = addPage(mGraphicalEditor, getEditorInput());
                    setPageText(mGraphicalEditorIndex, mGraphicalEditor.getTitle());

                    mGraphicalEditor.openFile(editedFile);
                } else {
                    if (mNewFileOnConfigChange) {
                        mGraphicalEditor.changeFileOnNewConfig(editedFile);
                        mNewFileOnConfigChange = false;
                    } else {
                        mGraphicalEditor.replaceFile(editedFile);
                    }
                }

                // put in place the listener to handle layout recompute only when needed.
                getSite().getPage().addPartListener(this);
            }
        } catch (PartInitException e) {
            AdtPlugin.log(e, "Error creating nested page"); //$NON-NLS-1$
        }
     }

    @Override
    protected void postCreatePages() {
        super.postCreatePages();

        // This is called after the createFormPages() and createTextPage() methods have
        // been called. Usually we select the first page (e.g. the GLE here) but right
        // now we're going to temporarily select the last page (the XML text editor) if
        // GLE1 is being used. That's because GLE1 is mostly useless and being deprecated.
        //
        // Note that this sets the default page. Eventually a default page might be
        // restored by selectDefaultPage() later based on the last page used by the user.
        //
        // TODO revert this once GLE2 becomes useful and is the default.

        if (mGraphicalEditor instanceof GraphicalLayoutEditor) {
            setActivePage(getPageCount() - 1);
        }
    }

    /* (non-java doc)
     * Change the tab/title name to include the name of the layout.
     */
    @Override
    protected void setInput(IEditorInput input) {
        super.setInput(input);
        handleNewInput(input);
    }

    /*
     * (non-Javadoc)
     * @see org.eclipse.ui.part.EditorPart#setInputWithNotify(org.eclipse.ui.IEditorInput)
     */
    @Override
    protected void setInputWithNotify(IEditorInput input) {
        super.setInputWithNotify(input);
        handleNewInput(input);
    }

    /**
     * Called to replace the current {@link IEditorInput} with another one.
     * <p/>This is used when {@link MatchingStrategy} returned <code>true</code> which means we're
     * opening a different configuration of the same layout.
     */
    public void showEditorInput(IEditorInput editorInput) {
        // save the current editor input.
        doSave(new NullProgressMonitor());

        // get the current page
        int currentPage = getActivePage();

        // remove the pages, except for the graphical editor, which will be dynamically adapted
        // to the new model.
        // page after the graphical editor:
        int count = getPageCount();
        for (int i = count - 1 ; i > mGraphicalEditorIndex ; i--) {
            removePage(i);
        }
        // pages before the graphical editor
        for (int i = mGraphicalEditorIndex - 1 ; i >= 0 ; i--) {
            removePage(i);
        }

        // set the current input.
        setInputWithNotify(editorInput);

        // re-create or reload the pages with the default page shown as the previous active page.
        createAndroidPages();
        selectDefaultPage(Integer.toString(currentPage));

        // update the GLE1 outline. The GLE2 outline doesn't need this call anymore.
        if (mOutlineForGle1 != null) {
            mOutlineForGle1.reloadModel();
        }
    }

    /**
     * Processes the new XML Model, which XML root node is given.
     *
     * @param xml_doc The XML document, if available, or null if none exists.
     */
    @Override
    protected void xmlModelChanged(Document xml_doc) {
        // init the ui root on demand
        initUiRootNode(false /*force*/);

        mUiRootNode.loadFromXmlNode(xml_doc);

        // update the model first, since it is used by the viewers.
        super.xmlModelChanged(xml_doc);

        if (mGraphicalEditor != null) {
            mGraphicalEditor.onXmlModelChanged();
        }

        // update the GLE1 outline. The GLE2 outline doesn't need this call anymore.
        if (mOutlineForGle1 != null) {
            mOutlineForGle1.reloadModel();
        }
    }

    /**
     * Returns the custom IContentOutlinePage or IPropertySheetPage when asked for it.
     */
    @SuppressWarnings("unchecked")
    @Override
    public Object getAdapter(Class adapter) {
        // for the outline, force it to come from the Graphical Editor.
        // This fixes the case where a layout file is opened in XML view first and the outline
        // gets stuck in the XML outline.
        if (IContentOutlinePage.class == adapter && mGraphicalEditor != null) {

            if (mOutline == null && mGraphicalEditor instanceof GraphicalLayoutEditor) {
                // Create the GLE1 outline. We need to keep a specific reference to it in order
                // to call its reloadModel() method. The GLE2 outline no longer relies on this
                // and can be casted to the base interface.
                mOutlineForGle1 = new UiContentOutlinePage(
                        (GraphicalLayoutEditor) mGraphicalEditor,
                        new TreeViewer());
                mOutline = mOutlineForGle1;

            } else if (mOutline == null && mGraphicalEditor instanceof GraphicalEditorPart) {
                mOutline = new OutlinePage2();
            }

            return mOutline;
        }

        if (IPropertySheetPage.class == adapter && mGraphicalEditor != null) {
            if (mPropertyPage == null && mGraphicalEditor instanceof GraphicalLayoutEditor) {
                mPropertyPage = new UiPropertySheetPage();

            } else if (mPropertyPage == null && mGraphicalEditor instanceof GraphicalEditorPart) {
                mPropertyPage = new PropertySheetPage2();
            }

            return mPropertyPage;
        }

        // return default
        return super.getAdapter(adapter);
    }

    @Override
    protected void pageChange(int newPageIndex) {
        super.pageChange(newPageIndex);

        if (mGraphicalEditor != null) {
            if (newPageIndex == mGraphicalEditorIndex) {
                mGraphicalEditor.activated();
            } else {
                mGraphicalEditor.deactivated();
            }
        }
    }

    // ----- IPartListener Methods ----

    public void partActivated(IWorkbenchPart part) {
        if (part == this) {
            if (mGraphicalEditor != null) {
                if (getActivePage() == mGraphicalEditorIndex) {
                    mGraphicalEditor.activated();
                } else {
                    mGraphicalEditor.deactivated();
                }
            }
        }
    }

    public void partBroughtToTop(IWorkbenchPart part) {
        partActivated(part);
    }

    public void partClosed(IWorkbenchPart part) {
        // pass
    }

    public void partDeactivated(IWorkbenchPart part) {
        if (part == this) {
            if (mGraphicalEditor != null && getActivePage() == mGraphicalEditorIndex) {
                mGraphicalEditor.deactivated();
            }
        }
    }

    public void partOpened(IWorkbenchPart part) {
        /*
         * We used to automatically bring the outline and the property sheet to view
         * when opening the editor. This behavior has always been a mixed bag and not
         * exactly satisfactory. GLE1 is being useless/deprecated and GLE2 will need to
         * improve on that, so right now let's comment this out.
         */
        //EclipseUiHelper.showView(EclipseUiHelper.CONTENT_OUTLINE_VIEW_ID, false /* activate */);
        //EclipseUiHelper.showView(EclipseUiHelper.PROPERTY_SHEET_VIEW_ID, false /* activate */);
    }

    public class UiEditorActions extends UiActions {

        @Override
        protected UiDocumentNode getRootNode() {
            return mUiRootNode;
        }

        // Select the new item
        @Override
        protected void selectUiNode(UiElementNode uiNodeToSelect) {
            mGraphicalEditor.selectModel(uiNodeToSelect);
        }

        @Override
        public void commitPendingXmlChanges() {
            // Pass. There is nothing to commit before the XML is changed here.
        }
    }

    public UiEditorActions getUiEditorActions() {
        if (mUiEditorActions == null) {
            mUiEditorActions = new UiEditorActions();
        }
        return mUiEditorActions;
    }

    // ---- Local Methods ----

    /**
     * Returns true if the Graphics editor page is visible. This <b>must</b> be
     * called from the UI thread.
     */
    public boolean isGraphicalEditorActive() {
        IWorkbenchPartSite workbenchSite = getSite();
        IWorkbenchPage workbenchPage = workbenchSite.getPage();

        // check if the editor is visible in the workbench page
        if (workbenchPage.isPartVisible(this) && workbenchPage.getActiveEditor() == this) {
            // and then if the page of the editor is visible (not to be confused with
            // the workbench page)
            return mGraphicalEditorIndex == getActivePage();
        }

        return false;
    }

    @Override
    public void initUiRootNode(boolean force) {
        // The root UI node is always created, even if there's no corresponding XML node.
        if (mUiRootNode == null || force) {
            // get the target data from the opened file (and its project)
            AndroidTargetData data = getTargetData();

            Document doc = null;
            if (mUiRootNode != null) {
                doc = mUiRootNode.getXmlDocument();
            }

            DocumentDescriptor desc;
            if (data == null) {
                desc = new DocumentDescriptor("temp", null /*children*/);
            } else {
                desc = data.getLayoutDescriptors().getDescriptor();
            }

            // get the descriptors from the data.
            mUiRootNode = (UiDocumentNode) desc.createUiNode();
            mUiRootNode.setEditor(this);

            mUiRootNode.setUnknownDescriptorProvider(new IUnknownDescriptorProvider() {

                public ElementDescriptor getDescriptor(String xmlLocalName) {

                    ElementDescriptor desc = mUnknownDescriptorMap.get(xmlLocalName);

                    if (desc == null) {
                        desc = createUnknownDescriptor(xmlLocalName);
                        mUnknownDescriptorMap.put(xmlLocalName, desc);
                    }

                    return desc;
                }
            });

            onDescriptorsChanged(doc);
        }
    }

    /**
     * Creates a new {@link ElementDescriptor} for an unknown XML local name
     * (i.e. one that was not mapped by the current descriptors).
     * <p/>
     * Since we deal with layouts, we returns either a descriptor for a custom view
     * or one for the base View.
     *
     * @param xmlLocalName The XML local name to match.
     * @return A non-null {@link ElementDescriptor}.
     */
    private ElementDescriptor createUnknownDescriptor(String xmlLocalName) {
        IEditorInput editorInput = getEditorInput();
        if (editorInput instanceof IFileEditorInput) {
            IFileEditorInput fileInput = (IFileEditorInput)editorInput;
            IProject project = fileInput.getFile().getProject();

            // Check if we can find a custom view specific to this project.
            ElementDescriptor desc = CustomViewDescriptorService.getInstance().getDescriptor(
                    project, xmlLocalName);

            if (desc != null) {
                return desc;
            }

            // If we didn't find a custom view, reuse the base View descriptor.
            // This is a layout after all, so every XML node should represent
            // a view.

            Sdk currentSdk = Sdk.getCurrent();
            if (currentSdk != null) {
                IAndroidTarget target = currentSdk.getTarget(project);
                if (target != null) {
                    AndroidTargetData data = currentSdk.getTargetData(target);
                    if (data != null) {
                        // data can be null when the target is still loading
                        desc = data.getLayoutDescriptors().getBaseViewDescriptor();
                    }
                }
            }

            if (desc != null) {
                return desc;
            }
        }

        // We get here if the editor input is not of the right type or if the
        // SDK hasn't finished loading. In either case, return something just
        // because we should not return null.
        return new ViewElementDescriptor(xmlLocalName, xmlLocalName);
    }

    private void onDescriptorsChanged(Document document) {

        mUnknownDescriptorMap.clear();

        if (document != null) {
            mUiRootNode.loadFromXmlNode(document);
        } else {
            mUiRootNode.reloadFromXmlNode(mUiRootNode.getXmlDocument());
        }

        if (mOutlineForGle1 != null) {
            mOutlineForGle1.reloadModel();
        }

        if (mGraphicalEditor != null) {
            mGraphicalEditor.onTargetChange();
            mGraphicalEditor.reloadPalette();
        }
    }

    /**
     * Handles a new input, and update the part name.
     * @param input the new input.
     */
    private void handleNewInput(IEditorInput input) {
        if (input instanceof FileEditorInput) {
            FileEditorInput fileInput = (FileEditorInput) input;
            IFile file = fileInput.getFile();
            setPartName(String.format("%1$s",
                    file.getName()));
        }
    }

    public void setNewFileOnConfigChange(boolean state) {
        mNewFileOnConfigChange = state;
    }
}
