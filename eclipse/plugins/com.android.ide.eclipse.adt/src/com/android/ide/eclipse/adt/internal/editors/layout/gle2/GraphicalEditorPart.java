/*
 * Copyright (C) 2009 The Android Open Source Project
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

import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.internal.editors.layout.ExplodedRenderingHelper;
import com.android.ide.eclipse.adt.internal.editors.layout.IGraphicalLayoutEditor;
import com.android.ide.eclipse.adt.internal.editors.layout.LayoutEditor;
import com.android.ide.eclipse.adt.internal.editors.layout.LayoutReloadMonitor;
import com.android.ide.eclipse.adt.internal.editors.layout.ProjectCallback;
import com.android.ide.eclipse.adt.internal.editors.layout.UiElementPullParser;
import com.android.ide.eclipse.adt.internal.editors.layout.LayoutReloadMonitor.ChangeFlags;
import com.android.ide.eclipse.adt.internal.editors.layout.LayoutReloadMonitor.ILayoutReloadListener;
import com.android.ide.eclipse.adt.internal.editors.layout.configuration.ConfigurationComposite;
import com.android.ide.eclipse.adt.internal.editors.layout.configuration.LayoutCreatorDialog;
import com.android.ide.eclipse.adt.internal.editors.layout.configuration.ConfigurationComposite.CustomToggle;
import com.android.ide.eclipse.adt.internal.editors.layout.configuration.ConfigurationComposite.IConfigListener;
import com.android.ide.eclipse.adt.internal.editors.layout.gre.RulesEngine;
import com.android.ide.eclipse.adt.internal.editors.layout.parts.ElementCreateCommand;
import com.android.ide.eclipse.adt.internal.editors.uimodel.UiDocumentNode;
import com.android.ide.eclipse.adt.internal.editors.uimodel.UiElementNode;
import com.android.ide.eclipse.adt.internal.resources.configurations.FolderConfiguration;
import com.android.ide.eclipse.adt.internal.resources.manager.ProjectResources;
import com.android.ide.eclipse.adt.internal.resources.manager.ResourceFile;
import com.android.ide.eclipse.adt.internal.resources.manager.ResourceFolderType;
import com.android.ide.eclipse.adt.internal.resources.manager.ResourceManager;
import com.android.ide.eclipse.adt.internal.sdk.AndroidTargetData;
import com.android.ide.eclipse.adt.internal.sdk.LoadStatus;
import com.android.ide.eclipse.adt.internal.sdk.Sdk;
import com.android.ide.eclipse.adt.internal.sdk.AndroidTargetData.LayoutBridge;
import com.android.ide.eclipse.adt.internal.sdk.Sdk.ITargetChangeListener;
import com.android.ide.eclipse.adt.io.IFileWrapper;
import com.android.layoutlib.api.ILayoutBridge;
import com.android.layoutlib.api.ILayoutLog;
import com.android.layoutlib.api.ILayoutResult;
import com.android.layoutlib.api.IProjectCallback;
import com.android.layoutlib.api.IResourceValue;
import com.android.layoutlib.api.IXmlPullParser;
import com.android.sdklib.IAndroidTarget;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.draw2d.geometry.Rectangle;
import org.eclipse.gef.ui.parts.SelectionSynchronizer;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.INullSelectionListener;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.part.EditorPart;
import org.eclipse.ui.part.FileEditorInput;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;

/**
 * Graphical layout editor part, version 2.
 * <p/>
 * The main component of the editor part is the {@link LayoutCanvasViewer}, which
 * actually delegates its work to the {@link LayoutCanvas} control.
 * <p/>
 * The {@link LayoutCanvasViewer} is set as the site's {@link ISelectionProvider}:
 * when the selection changes in the canvas, it is thus broadcasted to anyone listening
 * on the site's selection service.
 * <p/>
 * This part is also an {@link ISelectionListener}. It listens to the site's selection
 * service and thus receives selection changes from itself as well as the associated
 * outline and property sheet (these are registered by {@link LayoutEditor#getAdapter(Class)}).
 *
 * @since GLE2
 *
 * TODO List:
 * - display error icon
 * - finish palette (see palette's todo list)
 * - finish canvas (see canvas' todo list)
 * - completly rethink the property panel
 */
public class GraphicalEditorPart extends EditorPart
    implements IGraphicalLayoutEditor, ISelectionListener, INullSelectionListener {

    /*
     * Useful notes:
     * To understand Drag'n'drop:
     *   http://www.eclipse.org/articles/Article-Workbench-DND/drag_drop.html
     *
     * To understand the site's selection listener, selection provider, and the
     * confusion of different-yet-similarly-named interfaces, consult this:
     *   http://www.eclipse.org/articles/Article-WorkbenchSelections/article.html
     *
     * To summarize the selection mechanism:
     * - The workbench site selection service can be seen as "centralized"
     *   service that registers selection providers and selection listeners.
     * - The editor part and the outline are selection providers.
     * - The editor part, the outline and the property sheet are listeners
     *   which all listen to each others indirectly.
     */

    /** Reference to the layout editor */
    private final LayoutEditor mLayoutEditor;

    /** Reference to the file being edited. Can also be used to access the {@link IProject}. */
    private IFile mEditedFile;

    /** The current clipboard. Must be disposed later. */
    private Clipboard mClipboard;

    /** The configuration composite at the top of the layout editor. */
    private ConfigurationComposite mConfigComposite;

    /** The sash that splits the palette from the canvas. */
    private SashForm mSashPalette;

    /** The sash that splits the palette from the error view.
     * The error view is shown only when needed. */
    private SashForm mSashError;

    /** The palette displayed on the left of the sash. */
    private PaletteComposite mPalette;

    /** The layout canvas displayed to the right of the sash. */
    private LayoutCanvasViewer mCanvasViewer;

    /** The Groovy Rules Engine associated with this editor. It is project-specific. */
    private RulesEngine mRulesEngine;

    /** Styled text displaying the most recent error in the error view. */
    private StyledText mErrorLabel;

    private Map<String, Map<String, IResourceValue>> mConfiguredFrameworkRes;
    private Map<String, Map<String, IResourceValue>> mConfiguredProjectRes;
    private ProjectCallback mProjectCallback;
    private ILayoutLog mLogger;

    private boolean mNeedsXmlReload = false;
    private boolean mNeedsRecompute = false;

    private TargetListener mTargetListener;

    private ConfigListener mConfigListener;

    private ReloadListener mReloadListener;

    private boolean mUseExplodeMode;


    public GraphicalEditorPart(LayoutEditor layoutEditor) {
        mLayoutEditor = layoutEditor;
        setPartName("Graphical Layout");
    }

    // ------------------------------------
    // Methods overridden from base classes
    //------------------------------------

    /**
     * Initializes the editor part with a site and input.
     * {@inheritDoc}
     */
    @Override
    public void init(IEditorSite site, IEditorInput input) throws PartInitException {
        setSite(site);
        useNewEditorInput(input);

        if (mTargetListener == null) {
            mTargetListener = new TargetListener();
            AdtPlugin.getDefault().addTargetListener(mTargetListener);
        }
    }

    private void useNewEditorInput(IEditorInput input) throws PartInitException {
        // The contract of init() mentions we need to fail if we can't understand the input.
        if (!(input instanceof FileEditorInput)) {
            throw new PartInitException("Input is not of type FileEditorInput: " +  //$NON-NLS-1$
                    input == null ? "null" : input.toString());                     //$NON-NLS-1$
        }
    }

    @Override
    public void createPartControl(Composite parent) {

        Display d = parent.getDisplay();
        mClipboard = new Clipboard(d);

        GridLayout gl = new GridLayout(1, false);
        parent.setLayout(gl);
        gl.marginHeight = gl.marginWidth = 0;

        // create the top part for the configuration control

        CustomToggle[] toggles = new CustomToggle[] {
                new CustomToggle(
                        "-",
                        null, //image
                        "Canvas zoom out."
                        ) {
                    @Override
                    public void onSelected(boolean newState) {
                        rescale(-1);
                    }
                },
                new CustomToggle(
                        "+",
                        null, //image
                        "Canvas zoom in."
                        ) {
                    @Override
                    public void onSelected(boolean newState) {
                        rescale(+1);
                    }
                },
                new CustomToggle(
                        "Explode",
                        null, //image
                        "Displays extra margins in the layout."
                        ) {
                    @Override
                    public void onSelected(boolean newState) {
                        mUseExplodeMode = newState;
                        recomputeLayout();
                    }
                },
                new CustomToggle(
                        "Outline",
                        null, //image
                        "Shows the of all views in the layout."
                        ) {
                    @Override
                    public void onSelected(boolean newState) {
                        mCanvasViewer.getCanvas().setShowOutline(newState);
                    }
                }
        };

        mConfigListener = new ConfigListener();
        mConfigComposite = new ConfigurationComposite(mConfigListener, toggles, parent, SWT.BORDER);

        mSashPalette = new SashForm(parent, SWT.HORIZONTAL);
        mSashPalette.setLayoutData(new GridData(GridData.FILL_BOTH));

        mPalette = new PaletteComposite(mSashPalette);

        mSashError = new SashForm(mSashPalette, SWT.VERTICAL | SWT.BORDER);
        mSashError.setLayoutData(new GridData(GridData.FILL_BOTH));

        mCanvasViewer = new LayoutCanvasViewer(mLayoutEditor, mRulesEngine, mSashError, SWT.NONE);

        mErrorLabel = new StyledText(mSashError, SWT.READ_ONLY);
        mErrorLabel.setEditable(false);
        mErrorLabel.setBackground(d.getSystemColor(SWT.COLOR_INFO_BACKGROUND));
        mErrorLabel.setForeground(d.getSystemColor(SWT.COLOR_INFO_FOREGROUND));

        mSashPalette.setWeights(new int[] { 20, 80 });
        mSashError.setWeights(new int[] { 80, 20 });
        mSashError.setMaximizedControl(mCanvasViewer.getControl());

        setupEditActions();

        // Initialize the state
        reloadPalette();

        getSite().setSelectionProvider(mCanvasViewer);
        getSite().getPage().addSelectionListener(this);
    }

    /**
     * Listens to workbench selections that does NOT come from {@link LayoutEditor}
     * (those are generated by ourselves).
     * <p/>
     * Selection can be null, as indicated by this class implementing
     * {@link INullSelectionListener}.
     */
    public void selectionChanged(IWorkbenchPart part, ISelection selection) {
        if (!(part instanceof LayoutEditor)) {
            mCanvasViewer.setSelection(selection);
        }
    }

    /**
     * Rescales canvas.
     * @param direction +1 for zoom in, -1 for zoom out
     */
    private void rescale(int direction) {
        double s = mCanvasViewer.getCanvas().getScale();

        if (direction > 0) {
            s = s * 2;
        } else {
            s = s / 2;
        }

        mCanvasViewer.getCanvas().setScale(s);

    }

    private void setupEditActions() {

        IActionBars actionBars = getEditorSite().getActionBars();

        actionBars.setGlobalActionHandler(ActionFactory.COPY.getId(), new Action("Copy") {
            @Override
            public void run() {
                // TODO enable copy only when there's a selection
                mCanvasViewer.getCanvas().onCopy(mClipboard);
            }
        });

        actionBars.setGlobalActionHandler(ActionFactory.CUT.getId(), new Action("Cut") {
            @Override
            public void run() {
                // TODO enable cut only when there's a selection
                mCanvasViewer.getCanvas().onCut(mClipboard);
            }
        });

        actionBars.setGlobalActionHandler(ActionFactory.PASTE.getId(), new Action("Paste") {
            @Override
            public void run() {
                mCanvasViewer.getCanvas().onPaste(mClipboard);
            }
        });

        actionBars.setGlobalActionHandler(ActionFactory.SELECT_ALL.getId(),
                new Action("Select All") {
            @Override
            public void run() {
                mCanvasViewer.getCanvas().onSelectAll();
            }
        });
    }

    /**
     * Switches the stack to display the error label and hide the canvas.
     * @param errorFormat The new error to display if not null.
     * @param parameters String.format parameters for the error format.
     */
    private void displayError(String errorFormat, Object...parameters) {
        if (errorFormat != null) {
            mErrorLabel.setText(String.format(errorFormat, parameters));
        }
        mSashError.setMaximizedControl(null);
    }

    /** Displays the canvas and hides the error label. */
    private void hideError() {
        mSashError.setMaximizedControl(mCanvasViewer.getControl());
    }

    @Override
    public void dispose() {

        getSite().getPage().removeSelectionListener(this);
        getSite().setSelectionProvider(null);

        if (mTargetListener != null) {
            AdtPlugin.getDefault().removeTargetListener(mTargetListener);
            mTargetListener = null;
        }

        if (mReloadListener != null) {
            LayoutReloadMonitor.getMonitor().removeListener(mReloadListener);
            mReloadListener = null;
        }

        if (mClipboard != null) {
            mClipboard.dispose();
            mClipboard = null;
        }

        super.dispose();
    }

    /**
     * Listens to changes from the Configuration UI banner and triggers layout rendering when
     * changed. Also provide the Configuration UI with the list of resources/layout to display.
     */
    private class ConfigListener implements IConfigListener {

        /**
         * Looks for a file matching the new {@link FolderConfiguration} and attempts to open it.
         * <p/>If there is no match, notify the user.
         */
        public void onConfigurationChange() {
            mConfiguredFrameworkRes = mConfiguredProjectRes = null;

            if (mEditedFile == null || mConfigComposite.getEditedConfig() == null) {
                return;
            }

            // Before doing the normal process, test for the following case.
            // - the editor is being opened (or reset for a new input)
            // - the file being opened is not the best match for any possible configuration
            // - another random compatible config was chosen in the config composite.
            // The result is that 'match' will not be the file being edited, but because this is not
            // due to a config change, we should not trigger opening the actual best match (also,
            // because the editor is still opening the MatchingStrategy woudln't answer true
            // and the best match file would open in a different editor).
            // So the solution is that if the editor is being created, we just call recomputeLayout
            // without looking for a better matching layout file.
            if (mLayoutEditor.isCreatingPages()) {
                recomputeLayout();
            } else {
                // get the resources of the file's project.
                ProjectResources resources = ResourceManager.getInstance().getProjectResources(
                        mEditedFile.getProject());

                // from the resources, look for a matching file
                ResourceFile match = null;
                if (resources != null) {
                    match = resources.getMatchingFile(mEditedFile.getName(),
                                                      ResourceFolderType.LAYOUT,
                                                      mConfigComposite.getCurrentConfig());
                }

                if (match != null) {
                    // since this is coming from Eclipse, this is always an instance of IFileWrapper
                    IFileWrapper iFileWrapper = (IFileWrapper) match.getFile();
                    IFile iFile = iFileWrapper.getIFile();
                    if (iFile.equals(mEditedFile) == false) {
                        try {
                            // tell the editor that the next replacement file is due to a config
                            // change.
                            mLayoutEditor.setNewFileOnConfigChange(true);

                            // ask the IDE to open the replacement file.
                            IDE.openEditor(getSite().getWorkbenchWindow().getActivePage(), iFile);

                            // we're done!
                            return;
                        } catch (PartInitException e) {
                            // FIXME: do something!
                        }
                    }

                    // at this point, we have not opened a new file.

                    // Store the state in the current file
                    mConfigComposite.storeState();

                    // Even though the layout doesn't change, the config changed, and referenced
                    // resources need to be updated.
                    recomputeLayout();
                } else {
                    // display the error.
                    FolderConfiguration currentConfig = mConfigComposite.getCurrentConfig();
                    displayError(
                            "No resources match the configuration\n \n\t%1$s\n \nChange the configuration or create:\n \n\tres/%2$s/%3$s\n \nYou can also click the 'Create' button above.",
                            currentConfig.toDisplayString(),
                            currentConfig.getFolderName(ResourceFolderType.LAYOUT),
                            mEditedFile.getName());
                }
            }
        }

        public void onThemeChange() {
            // Store the state in the current file
            mConfigComposite.storeState();

            recomputeLayout();
        }

        public void onClippingChange() {
            recomputeLayout();
        }

        public void onCreate() {
            LayoutCreatorDialog dialog = new LayoutCreatorDialog(mConfigComposite.getShell(),
                    mEditedFile.getName(), mConfigComposite.getCurrentConfig());
            if (dialog.open() == Dialog.OK) {
                final FolderConfiguration config = new FolderConfiguration();
                dialog.getConfiguration(config);

                createAlternateLayout(config);
            }
        }

        public Map<String, Map<String, IResourceValue>> getConfiguredFrameworkResources() {
            if (mConfiguredFrameworkRes == null && mConfigComposite != null) {
                ProjectResources frameworkRes = getFrameworkResources();

                if (frameworkRes == null) {
                    AdtPlugin.log(IStatus.ERROR, "Failed to get ProjectResource for the framework");
                } else {
                    // get the framework resource values based on the current config
                    mConfiguredFrameworkRes = frameworkRes.getConfiguredResources(
                            mConfigComposite.getCurrentConfig());
                }
            }

            return mConfiguredFrameworkRes;
        }

        public Map<String, Map<String, IResourceValue>> getConfiguredProjectResources() {
            if (mConfiguredProjectRes == null && mConfigComposite != null) {
                ProjectResources project = getProjectResources();

                // make sure they are loaded
                project.loadAll();

                // get the project resource values based on the current config
                mConfiguredProjectRes = project.getConfiguredResources(
                        mConfigComposite.getCurrentConfig());
            }

            return mConfiguredProjectRes;
        }

        /**
         * Returns a {@link ProjectResources} for the framework resources.
         * @return the framework resources or null if not found.
         */
        public ProjectResources getFrameworkResources() {
            if (mEditedFile != null) {
                Sdk currentSdk = Sdk.getCurrent();
                if (currentSdk != null) {
                    IAndroidTarget target = currentSdk.getTarget(mEditedFile.getProject());

                    if (target != null) {
                        AndroidTargetData data = currentSdk.getTargetData(target);

                        if (data != null) {
                            return data.getFrameworkResources();
                        }
                    }
                }
            }

            return null;
        }

        public ProjectResources getProjectResources() {
            if (mEditedFile != null) {
                ResourceManager manager = ResourceManager.getInstance();
                return manager.getProjectResources(mEditedFile.getProject());
            }

            return null;
        }

        /**
         * Creates a new layout file from the specified {@link FolderConfiguration}.
         */
        private void createAlternateLayout(final FolderConfiguration config) {
            new Job("Create Alternate Resource") {
                @Override
                protected IStatus run(IProgressMonitor monitor) {
                    // get the folder name
                    String folderName = config.getFolderName(ResourceFolderType.LAYOUT);
                    try {

                        // look to see if it exists.
                        // get the res folder
                        IFolder res = (IFolder)mEditedFile.getParent().getParent();
                        String path = res.getLocation().toOSString();

                        File newLayoutFolder = new File(path + File.separator + folderName);
                        if (newLayoutFolder.isFile()) {
                            // this should not happen since aapt would have complained
                            // before, but if one disable the automatic build, this could
                            // happen.
                            String message = String.format("File 'res/%1$s' is in the way!",
                                    folderName);

                            AdtPlugin.displayError("Layout Creation", message);

                            return new Status(IStatus.ERROR, AdtPlugin.PLUGIN_ID, message);
                        } else if (newLayoutFolder.exists() == false) {
                            // create it.
                            newLayoutFolder.mkdir();
                        }

                        // now create the file
                        File newLayoutFile = new File(newLayoutFolder.getAbsolutePath() +
                                    File.separator + mEditedFile.getName());

                        newLayoutFile.createNewFile();

                        InputStream input = mEditedFile.getContents();

                        FileOutputStream fos = new FileOutputStream(newLayoutFile);

                        byte[] data = new byte[512];
                        int count;
                        while ((count = input.read(data)) != -1) {
                            fos.write(data, 0, count);
                        }

                        input.close();
                        fos.close();

                        // refreshes the res folder to show up the new
                        // layout folder (if needed) and the file.
                        // We use a progress monitor to catch the end of the refresh
                        // to trigger the edit of the new file.
                        res.refreshLocal(IResource.DEPTH_INFINITE, new IProgressMonitor() {
                            public void done() {
                                mConfigComposite.getDisplay().asyncExec(new Runnable() {
                                    public void run() {
                                        onConfigurationChange();
                                    }
                                });
                            }

                            public void beginTask(String name, int totalWork) {
                                // pass
                            }

                            public void internalWorked(double work) {
                                // pass
                            }

                            public boolean isCanceled() {
                                // pass
                                return false;
                            }

                            public void setCanceled(boolean value) {
                                // pass
                            }

                            public void setTaskName(String name) {
                                // pass
                            }

                            public void subTask(String name) {
                                // pass
                            }

                            public void worked(int work) {
                                // pass
                            }
                        });
                    } catch (IOException e2) {
                        String message = String.format(
                                "Failed to create File 'res/%1$s/%2$s' : %3$s",
                                folderName, mEditedFile.getName(), e2.getMessage());

                        AdtPlugin.displayError("Layout Creation", message);

                        return new Status(IStatus.ERROR, AdtPlugin.PLUGIN_ID,
                                message, e2);
                    } catch (CoreException e2) {
                        String message = String.format(
                                "Failed to create File 'res/%1$s/%2$s' : %3$s",
                                folderName, mEditedFile.getName(), e2.getMessage());

                        AdtPlugin.displayError("Layout Creation", message);

                        return e2.getStatus();
                    }

                    return Status.OK_STATUS;

                }
            }.schedule();
        }
    }

    /**
     * Listens to target changed in the current project, to trigger a new layout rendering.
     */
    private class TargetListener implements ITargetChangeListener {

        public void onProjectTargetChange(IProject changedProject) {
            if (changedProject != null && changedProject.equals(getProject())) {
                updateEditor();
            }
        }

        public void onTargetLoaded(IAndroidTarget target) {
            IProject project = getProject();
            if (target != null && target.equals(Sdk.getCurrent().getTarget(project))) {
                updateEditor();
            }
        }

        public void onSdkLoaded() {
            Sdk currentSdk = Sdk.getCurrent();
            if (currentSdk != null) {
                IAndroidTarget target = currentSdk.getTarget(mEditedFile.getProject());
                if (target != null) {
                    mConfigComposite.onSdkLoaded(target);
                    mConfigListener.onConfigurationChange();
                }
            }
        }

        private void updateEditor() {
            mLayoutEditor.commitPages(false /* onSave */);

            // because the target changed we must reset the configured resources.
            mConfiguredFrameworkRes = mConfiguredProjectRes = null;

            // make sure we remove the custom view loader, since its parent class loader is the
            // bridge class loader.
            mProjectCallback = null;

            // recreate the ui root node always, this will also call onTargetChange
            // on the config composite
            mLayoutEditor.initUiRootNode(true /*force*/);
        }

        private IProject getProject() {
            return getLayoutEditor().getProject();
        }
    }

    // ----------------

    /**
     * Save operation in the Graphical Editor Part.
     * <p/>
     * In our workflow, the model is owned by the Structured XML Editor.
     * The graphical layout editor just displays it -- thus we don't really
     * save anything here.
     * <p/>
     * This must NOT call the parent editor part. At the contrary, the parent editor
     * part will call this *after* having done the actual save operation.
     * <p/>
     * The only action this editor must do is mark the undo command stack as
     * being no longer dirty.
     */
    @Override
    public void doSave(IProgressMonitor monitor) {
        // TODO implement a command stack
//        getCommandStack().markSaveLocation();
//        firePropertyChange(PROP_DIRTY);
    }

    /**
     * Save operation in the Graphical Editor Part.
     * <p/>
     * In our workflow, the model is owned by the Structured XML Editor.
     * The graphical layout editor just displays it -- thus we don't really
     * save anything here.
     */
    @Override
    public void doSaveAs() {
        // pass
    }

    /**
     * In our workflow, the model is owned by the Structured XML Editor.
     * The graphical layout editor just displays it -- thus we don't really
     * save anything here.
     */
    @Override
    public boolean isDirty() {
        return false;
    }

    /**
     * In our workflow, the model is owned by the Structured XML Editor.
     * The graphical layout editor just displays it -- thus we don't really
     * save anything here.
     */
    @Override
    public boolean isSaveAsAllowed() {
        return false;
    }

    @Override
    public void setFocus() {
        // TODO Auto-generated method stub

    }

    /**
     * Responds to a page change that made the Graphical editor page the activated page.
     */
    public void activated() {
        if (mNeedsRecompute || mNeedsXmlReload) {
            recomputeLayout();
        }
    }

    /**
     * Responds to a page change that made the Graphical editor page the deactivated page
     */
    public void deactivated() {
        // nothing to be done here for now.
    }

    /**
     * Opens and initialize the editor with a new file.
     * @param file the file being edited.
     */
    public void openFile(IFile file) {
        mEditedFile = file;
        mConfigComposite.setFile(mEditedFile);

        if (mReloadListener == null) {
            mReloadListener = new ReloadListener();
            LayoutReloadMonitor.getMonitor().addListener(mEditedFile.getProject(), mReloadListener);
        }

        if (mRulesEngine == null) {
            mRulesEngine = new RulesEngine(mEditedFile.getProject());
            if (mCanvasViewer != null) {
                mCanvasViewer.getCanvas().setRulesEngine(mRulesEngine);
            }
        }
    }

    /**
     * Resets the editor with a replacement file.
     * @param file the replacement file.
     */
    public void replaceFile(IFile file) {
        mEditedFile = file;
        mConfigComposite.replaceFile(mEditedFile);
    }

    /**
     * Resets the editor with a replacement file coming from a config change in the config
     * selector.
     * @param file the replacement file.
     */
    public void changeFileOnNewConfig(IFile file) {
        mEditedFile = file;
        mConfigComposite.changeFileOnNewConfig(mEditedFile);
    }

    public void onTargetChange() {
        mConfigComposite.onXmlModelLoaded();
        mConfigListener.onConfigurationChange();
    }

    public void onSdkChange() {
        Sdk currentSdk = Sdk.getCurrent();
        if (currentSdk != null) {
            IAndroidTarget target = currentSdk.getTarget(mEditedFile.getProject());
            if (target != null) {
                mConfigComposite.onSdkLoaded(target);
                mConfigListener.onConfigurationChange();
            }
        }
    }

    public Clipboard getClipboard() {
        return mClipboard;
    }

    public LayoutEditor getLayoutEditor() {
        return mLayoutEditor;
    }

    public UiDocumentNode getModel() {
        return mLayoutEditor.getUiRootNode();
    }

    public SelectionSynchronizer getSelectionSynchronizer() {
        // TODO Auto-generated method stub
        return null;
    }

    /**
     * Callback for XML model changed. Only update/recompute the layout if the editor is visible
     */
    public void onXmlModelChanged() {
        if (mLayoutEditor.isGraphicalEditorActive()) {
            doXmlReload(true /* force */);
            recomputeLayout();
        } else {
            mNeedsXmlReload = true;
        }
    }

    /**
     * Actually performs the XML reload
     * @see #onXmlModelChanged()
     */
    private void doXmlReload(boolean force) {
        if (force || mNeedsXmlReload) {

            // TODO : update the mLayoutCanvas, preserving the current selection if possible.

//            GraphicalViewer viewer = getGraphicalViewer();
//
//            // try to preserve the selection before changing the content
//            SelectionManager selMan = viewer.getSelectionManager();
//            ISelection selection = selMan.getSelection();
//
//            try {
//                viewer.setContents(getModel());
//            } finally {
//                selMan.setSelection(selection);
//            }

            mNeedsXmlReload = false;
        }
    }

    public void recomputeLayout() {
        doXmlReload(false /* force */);
        try {
            // check that the resource exists. If the file is opened but the project is closed
            // or deleted for some reason (changed from outside of eclipse), then this will
            // return false;
            if (mEditedFile.exists() == false) {
                displayError("Resource '%1$s' does not exist.",
                             mEditedFile.getFullPath().toString());
                return;
            }

            IProject iProject = mEditedFile.getProject();

            if (mEditedFile.isSynchronized(IResource.DEPTH_ZERO) == false) {
                String message = String.format("%1$s is out of sync. Please refresh.",
                        mEditedFile.getName());

                displayError(message);

                // also print it in the error console.
                AdtPlugin.printErrorToConsole(iProject.getName(), message);
                return;
            }

            Sdk currentSdk = Sdk.getCurrent();
            if (currentSdk != null) {
                IAndroidTarget target = currentSdk.getTarget(mEditedFile.getProject());
                if (target == null) {
                    displayError("The project target is not set.");
                    return;
                }

                AndroidTargetData data = currentSdk.getTargetData(target);
                if (data == null) {
                    // It can happen that the workspace refreshes while the SDK is loading its
                    // data, which could trigger a redraw of the opened layout if some resources
                    // changed while Eclipse is closed.
                    // In this case data could be null, but this is not an error.
                    // We can just silently return, as all the opened editors are automatically
                    // refreshed once the SDK finishes loading.
                    LoadStatus targetLoadStatus = currentSdk.checkAndLoadTargetData(target, null);
                    switch (targetLoadStatus) {
                        case LOADING:
                            displayError("The project target (%1$s) is still loading.\n%2$s will refresh automatically once the process is finished.",
                                    target.getName(), mEditedFile.getName());

                            break;
                        case FAILED: // known failure
                        case LOADED: // success but data isn't loaded?!?!
                            displayError("The project target (%s) was not properly loaded.",
                                    target.getName());
                            break;
                    }

                    return;
                }

                // check there is actually a model (maybe the file is empty).
                UiDocumentNode model = getModel();

                if (model.getUiChildren().size() == 0) {
                    displayError("No Xml content. Go to the Outline view and add nodes.");
                    return;
                }

                LayoutBridge bridge = data.getLayoutBridge();

                if (bridge.bridge != null) { // bridge can never be null.
                    ResourceManager resManager = ResourceManager.getInstance();

                    ProjectResources projectRes = resManager.getProjectResources(iProject);
                    if (projectRes == null) {
                        displayError("Missing project resources.");
                        return;
                    }

                    // get the resources of the file's project.
                    Map<String, Map<String, IResourceValue>> configuredProjectRes =
                        mConfigListener.getConfiguredProjectResources();

                    // get the framework resources
                    Map<String, Map<String, IResourceValue>> frameworkResources =
                        mConfigListener.getConfiguredFrameworkResources();

                    if (configuredProjectRes != null && frameworkResources != null) {
                        if (mProjectCallback == null) {
                            mProjectCallback = new ProjectCallback(
                                    bridge.classLoader, projectRes, iProject);
                        }

                        if (mLogger == null) {
                            mLogger = new ILayoutLog() {
                                public void error(String message) {
                                    AdtPlugin.printErrorToConsole(mEditedFile.getName(), message);
                                }

                                public void error(Throwable error) {
                                    String message = error.getMessage();
                                    if (message == null) {
                                        message = error.getClass().getName();
                                    }

                                    PrintStream ps = new PrintStream(AdtPlugin.getErrorStream());
                                    error.printStackTrace(ps);
                                }

                                public void warning(String message) {
                                    AdtPlugin.printToConsole(mEditedFile.getName(), message);
                                }
                            };
                        }

                        // get the selected theme
                        String theme = mConfigComposite.getTheme();
                        if (theme != null) {
                            // Compute the layout
                            Rectangle rect = getBounds();

                            int width = rect.width;
                            int height = rect.height;
                            if (mUseExplodeMode) {
                                // compute how many padding in x and y will bump the screen size
                                List<UiElementNode> children = getModel().getUiChildren();
                                if (children.size() == 1) {
                                    ExplodedRenderingHelper helper = new ExplodedRenderingHelper(
                                            children.get(0).getXmlNode(), iProject);

                                    // there are 2 paddings for each view
                                    // left and right, or top and bottom.
                                    int paddingValue = ExplodedRenderingHelper.PADDING_VALUE * 2;

                                    width += helper.getWidthPadding() * paddingValue;
                                    height += helper.getHeightPadding() * paddingValue;
                                }
                            }

                            int density = mConfigComposite.getDensity().getDpiValue();
                            float xdpi = mConfigComposite.getXDpi();
                            float ydpi = mConfigComposite.getYDpi();
                            boolean isProjectTheme = mConfigComposite.isProjectTheme();

                            UiElementPullParser parser = new UiElementPullParser(getModel(),
                                    mUseExplodeMode, density, xdpi, iProject);

                            ILayoutResult result = computeLayout(bridge, parser,
                                    iProject /* projectKey */,
                                    width, height, !mConfigComposite.getClipping(),
                                    density, xdpi, ydpi,
                                    theme, isProjectTheme,
                                    configuredProjectRes, frameworkResources, mProjectCallback,
                                    mLogger);

                            // post rendering clean up
                            bridge.cleanUp();

                            mCanvasViewer.getCanvas().setResult(result);

                            // update the UiElementNode with the layout info.
                            if (result.getSuccess() == ILayoutResult.SUCCESS) {
                                hideError();
                            } else {
                                displayError(result.getErrorMessage());
                            }

                            model.refreshUi();
                        }
                    }
                } else {
                    // SDK is loaded but not the layout library!

                    // check whether the bridge managed to load, or not
                    if (bridge.status == LoadStatus.LOADING) {
                        displayError("Eclipse is loading framework information and the layout library from the SDK folder.\n%1$s will refresh automatically once the process is finished.",
                                     mEditedFile.getName());
                    } else {
                        displayError("Eclipse failed to load the framework information and the layout library!");
                    }
                }
            } else {
                displayError("Eclipse is loading the SDK.\n%1$s will refresh automatically once the process is finished.",
                             mEditedFile.getName());
            }
        } finally {
            // no matter the result, we are done doing the recompute based on the latest
            // resource/code change.
            mNeedsRecompute = false;
        }
    }

    /**
     * Computes a layout by calling the correct computeLayout method of ILayoutBridge based on
     * the implementation API level.
     *
     * Implementation detail: the bridge's computeLayout() method already returns a newly
     * allocated ILayoutResult.
     */
    @SuppressWarnings("deprecation")
    private static ILayoutResult computeLayout(LayoutBridge bridge,
            IXmlPullParser layoutDescription, Object projectKey,
            int screenWidth, int screenHeight, boolean renderFullSize,
            int density, float xdpi, float ydpi,
            String themeName, boolean isProjectTheme,
            Map<String, Map<String, IResourceValue>> projectResources,
            Map<String, Map<String, IResourceValue>> frameworkResources,
            IProjectCallback projectCallback, ILayoutLog logger) {

        if (bridge.apiLevel >= ILayoutBridge.API_CURRENT) {
            // newest API with support for "render full height"
            // TODO: link boolean to UI.
            return bridge.bridge.computeLayout(layoutDescription,
                    projectKey, screenWidth, screenHeight, renderFullSize,
                    density, xdpi, ydpi,
                    themeName, isProjectTheme,
                    projectResources, frameworkResources, projectCallback,
                    logger);
        } else if (bridge.apiLevel == 3) {
            // newer api with density support.
            return bridge.bridge.computeLayout(layoutDescription,
                    projectKey, screenWidth, screenHeight, density, xdpi, ydpi,
                    themeName, isProjectTheme,
                    projectResources, frameworkResources, projectCallback,
                    logger);
        } else if (bridge.apiLevel == 2) {
            // api with boolean for separation of project/framework theme
            return bridge.bridge.computeLayout(layoutDescription,
                    projectKey, screenWidth, screenHeight, themeName, isProjectTheme,
                    projectResources, frameworkResources, projectCallback,
                    logger);
        } else {
            // oldest api with no density/dpi, and project theme boolean mixed
            // into the theme name.

            // change the string if it's a custom theme to make sure we can
            // differentiate them
            if (isProjectTheme) {
                themeName = "*" + themeName; //$NON-NLS-1$
            }

            return bridge.bridge.computeLayout(layoutDescription,
                    projectKey, screenWidth, screenHeight, themeName,
                    projectResources, frameworkResources, projectCallback,
                    logger);
        }
    }

    public Rectangle getBounds() {
        return mConfigComposite.getScreenBounds();
    }

    public void reloadPalette() {
        if (mPalette != null) {
            mPalette.reloadPalette(mLayoutEditor.getTargetData());
        }
    }

    /**
     * Used by LayoutEditor.UiEditorActions.selectUiNode to select a new UI Node
     * created by {@link ElementCreateCommand#execute()}.
     *
     * @param uiNodeModel The {@link UiElementNode} to select.
     */
    public void selectModel(UiElementNode uiNodeModel) {

        // TODO this method was useful for GLE1. We may not need it anymore now.

//        GraphicalViewer viewer = getGraphicalViewer();
//
//        // Give focus to the graphical viewer (in case the outline has it)
//        viewer.getControl().forceFocus();
//
//        Object editPart = viewer.getEditPartRegistry().get(uiNodeModel);
//
//        if (editPart instanceof EditPart) {
//            viewer.select((EditPart)editPart);
//        }
    }

    private class ReloadListener implements ILayoutReloadListener {
        /*
         * Called when the file changes triggered a redraw of the layout
         */
        public void reloadLayout(ChangeFlags flags, boolean libraryChanged) {
            boolean recompute = false;

            if (flags.rClass) {
                recompute = true;
                if (mEditedFile != null) {
                    ProjectResources projectRes = ResourceManager.getInstance().getProjectResources(
                            mEditedFile.getProject());

                    if (projectRes != null) {
                        projectRes.resetDynamicIds();
                    }
                }
            }

            if (flags.localeList) {
                // the locale list *potentially* changed so we update the locale in the
                // config composite.
                // However there's no recompute, as it could not be needed
                // (for instance a new layout)
                // If a resource that's not a layout changed this will trigger a recompute anyway.
                mCanvasViewer.getControl().getDisplay().asyncExec(new Runnable() {
                    public void run() {
                        mConfigComposite.updateLocales();
                    }
                });
            }

            // if a resources was modified.
            // also, if a layout in a library was modified.
            if (flags.resources || (libraryChanged && flags.layout)) {
                recompute = true;

                // TODO: differentiate between single and multi resource file changed, and whether the resource change affects the cache.

                // force a reparse in case a value XML file changed.
                mConfiguredProjectRes = null;

                // clear the cache in the bridge in case a bitmap/9-patch changed.
                IAndroidTarget target = Sdk.getCurrent().getTarget(mEditedFile.getProject());
                if (target != null) {

                    AndroidTargetData data = Sdk.getCurrent().getTargetData(target);
                    if (data != null) {
                        LayoutBridge bridge = data.getLayoutBridge();

                        if (bridge.bridge != null) {
                            bridge.bridge.clearCaches(mEditedFile.getProject());
                        }
                    }
                }
            }

            if (flags.code) {
                // only recompute if the custom view loader was used to load some code.
                if (mProjectCallback != null && mProjectCallback.isUsed()) {
                    mProjectCallback = null;
                    recompute = true;
                }
            }

            if (recompute) {
                mCanvasViewer.getControl().getDisplay().asyncExec(new Runnable() {
                    public void run() {
                        if (mLayoutEditor.isGraphicalEditorActive()) {
                            recomputeLayout();
                        } else {
                            mNeedsRecompute = true;
                        }
                    }
                });
            }
        }
    }
}
