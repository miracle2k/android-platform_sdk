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

import static com.android.ide.eclipse.adt.AndroidConstants.ANDROID_PKG;
import static com.android.sdklib.resources.Density.DEFAULT_DENSITY;

import com.android.ide.common.rendering.LayoutLibrary;
import com.android.ide.common.rendering.StaticRenderSession;
import com.android.ide.common.rendering.api.Capability;
import com.android.ide.common.rendering.api.ILayoutPullParser;
import com.android.ide.common.rendering.api.LayoutLog;
import com.android.ide.common.rendering.api.Params;
import com.android.ide.common.rendering.api.RenderSession;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.Result;
import com.android.ide.common.rendering.api.Params.RenderingMode;
import com.android.ide.common.sdk.LoadStatus;
import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.internal.editors.IPageImageProvider;
import com.android.ide.eclipse.adt.internal.editors.IconFactory;
import com.android.ide.eclipse.adt.internal.editors.layout.ContextPullParser;
import com.android.ide.eclipse.adt.internal.editors.layout.ExplodedRenderingHelper;
import com.android.ide.eclipse.adt.internal.editors.layout.LayoutEditor;
import com.android.ide.eclipse.adt.internal.editors.layout.LayoutReloadMonitor;
import com.android.ide.eclipse.adt.internal.editors.layout.ProjectCallback;
import com.android.ide.eclipse.adt.internal.editors.layout.UiElementPullParser;
import com.android.ide.eclipse.adt.internal.editors.layout.LayoutReloadMonitor.ChangeFlags;
import com.android.ide.eclipse.adt.internal.editors.layout.LayoutReloadMonitor.ILayoutReloadListener;
import com.android.ide.eclipse.adt.internal.editors.layout.configuration.ConfigurationComposite;
import com.android.ide.eclipse.adt.internal.editors.layout.configuration.LayoutCreatorDialog;
import com.android.ide.eclipse.adt.internal.editors.layout.configuration.ConfigurationComposite.CustomButton;
import com.android.ide.eclipse.adt.internal.editors.layout.configuration.ConfigurationComposite.IConfigListener;
import com.android.ide.eclipse.adt.internal.editors.layout.gle2.IncludeFinder.Reference;
import com.android.ide.eclipse.adt.internal.editors.layout.gre.RulesEngine;
import com.android.ide.eclipse.adt.internal.editors.ui.DecorComposite;
import com.android.ide.eclipse.adt.internal.editors.uimodel.UiDocumentNode;
import com.android.ide.eclipse.adt.internal.editors.uimodel.UiElementNode;
import com.android.ide.eclipse.adt.internal.preferences.AdtPrefs;
import com.android.ide.eclipse.adt.internal.resources.ResourceType;
import com.android.ide.eclipse.adt.internal.resources.configurations.FolderConfiguration;
import com.android.ide.eclipse.adt.internal.resources.manager.ProjectResources;
import com.android.ide.eclipse.adt.internal.resources.manager.ResourceFile;
import com.android.ide.eclipse.adt.internal.resources.manager.ResourceFolderType;
import com.android.ide.eclipse.adt.internal.resources.manager.ResourceManager;
import com.android.ide.eclipse.adt.internal.sdk.AndroidTargetData;
import com.android.ide.eclipse.adt.internal.sdk.Sdk;
import com.android.ide.eclipse.adt.internal.sdk.Sdk.ITargetChangeListener;
import com.android.ide.eclipse.adt.io.IFileWrapper;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkConstants;
import com.android.sdkuilib.internal.widgets.ResolutionChooserDialog;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.draw2d.geometry.Rectangle;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ui.actions.OpenNewClassWizardAction;
import org.eclipse.jdt.ui.wizards.NewClassWizardPage;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.INullSelectionListener;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.part.EditorPart;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.part.IPage;
import org.eclipse.ui.part.PageBookView;
import org.w3c.dom.Node;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
 */
public class GraphicalEditorPart extends EditorPart
    implements IPageImageProvider, ISelectionListener, INullSelectionListener {

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

    /**
     * Session-property on files which specifies the initial config state to be used on
     * this file
     */
    public final static QualifiedName NAME_INITIAL_STATE =
        new QualifiedName(AdtPlugin.PLUGIN_ID, "initialstate");//$NON-NLS-1$

    /**
     * Session-property on files which specifies the inclusion-context (reference to another layout
     * which should be "including" this layout) when the file is opened
     */
    public final static QualifiedName NAME_INCLUDE =
        new QualifiedName(AdtPlugin.PLUGIN_ID, "includer");//$NON-NLS-1$

    /** Reference to the layout editor */
    private final LayoutEditor mLayoutEditor;

    /** Reference to the file being edited. Can also be used to access the {@link IProject}. */
    private IFile mEditedFile;

    /** The configuration composite at the top of the layout editor. */
    private ConfigurationComposite mConfigComposite;

    /** The sash that splits the palette from the canvas. */
    private SashForm mSashPalette;

    /** The sash that splits the palette from the error view.
     * The error view is shown only when needed. */
    private SashForm mSashError;

    /** The palette displayed on the left of the sash. */
    private PaletteControl mPalette;

    /** The layout canvas displayed to the right of the sash. */
    private LayoutCanvasViewer mCanvasViewer;

    /** The Rules Engine associated with this editor. It is project-specific. */
    private RulesEngine mRulesEngine;

    /** Styled text displaying the most recent error in the error view. */
    private StyledText mErrorLabel;

    /**
     * The resource reference to a file that should surround this file (e.g. include this file
     * visually), or null if not applicable
     */
    private Reference mIncludedWithin;

    private Map<String, Map<String, ResourceValue>> mConfiguredFrameworkRes;
    private Map<String, Map<String, ResourceValue>> mConfiguredProjectRes;
    private ProjectCallback mProjectCallback;
    private LayoutLog mLog;

    private boolean mNeedsRecompute = false;

    private TargetListener mTargetListener;

    private ConfigListener mConfigListener;

    private ReloadListener mReloadListener;

    private boolean mUseExplodeMode;

    private CustomButton mZoomRealSizeButton;
    private CustomButton mZoomOutButton;
    private CustomButton mZoomResetButton;
    private CustomButton mZoomInButton;

    private CustomButton mClippingButton;

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

    public Image getPageImage() {
        return IconFactory.getInstance().getIcon("editor_page_design");  //$NON-NLS-1$
    }

    @Override
    public void createPartControl(Composite parent) {

        Display d = parent.getDisplay();

        GridLayout gl = new GridLayout(1, false);
        parent.setLayout(gl);
        gl.marginHeight = gl.marginWidth = 0;

        // create the top part for the configuration control
        CustomButton[][] customButtons = new CustomButton[][] {
                new CustomButton[] {
                    mZoomRealSizeButton = new CustomButton(
                            "*",
                            null, //image
                            "Emulate real size",
                            true /*isToggle*/,
                            false /*defaultValue*/
                            ) {
                        @Override
                        public void onSelected(boolean newState) {
                            if (rescaleToReal(newState)) {
                                mZoomOutButton.setEnabled(!newState);
                                mZoomResetButton.setEnabled(!newState);
                                mZoomInButton.setEnabled(!newState);
                            } else {
                                mZoomRealSizeButton.setSelection(!newState);
                            }
                        }
                    },
                    mZoomOutButton = new CustomButton(
                            "-",
                            null, //image
                            "Canvas zoom out."
                            ) {
                        @Override
                        public void onSelected(boolean newState) {
                            rescale(-1);
                        }
                    },
                    mZoomResetButton = new CustomButton(
                            "100%",
                            null, //image
                            "Reset Canvas to 100%"
                            ) {
                        @Override
                        public void onSelected(boolean newState) {
                            resetScale();
                        }
                    },
                    mZoomInButton = new CustomButton(
                            "+",
                            null, //image
                            "Canvas zoom in."
                            ) {
                        @Override
                        public void onSelected(boolean newState) {
                            rescale(+1);
                        }
                    },
                },
                new CustomButton[] {
                    new CustomButton(
                            null, //text
                            IconFactory.getInstance().getIcon("explode"), //$NON-NLS-1$
                            "Displays extra margins in the layout.",
                            true /*toggle*/,
                            false /*defaultValue*/
                            ) {
                        @Override
                        public void onSelected(boolean newState) {
                            mUseExplodeMode = newState;
                            recomputeLayout();
                        }
                    },
                    new CustomButton(
                            null, //text
                            IconFactory.getInstance().getIcon("outline"), //$NON-NLS-1$
                            "Shows the outline of all views in the layout.",
                            true /*toggle*/,
                            false /*defaultValue*/
                            ) {
                        @Override
                        public void onSelected(boolean newState) {
                            mCanvasViewer.getCanvas().setShowOutline(newState);
                        }
                    },
                    mClippingButton =  new CustomButton(
                            null, //text
                            IconFactory.getInstance().getIcon("clipping"), //$NON-NLS-1$
                            "Toggles screen clipping on/off",
                            true /*toggle*/,
                            true /*defaultValue*/
                            ) {
                        @Override
                        public void onSelected(boolean newState) {
                            recomputeLayout();
                        }
                    }
                }
        };

        mConfigListener = new ConfigListener();

        // Check whether somebody has requested an initial state for the newly opened file.
        // The initial state is a serialized version of the state compatible with
        // {@link ConfigurationComposite#CONFIG_STATE}.
        String initialState = null;
        if (mEditedFile != null) {
            try {
                initialState = (String) mEditedFile.getSessionProperty(NAME_INITIAL_STATE);
                if (initialState != null) {
                    // Only use once
                    mEditedFile.setSessionProperty(NAME_INITIAL_STATE, null);
                }
            } catch (CoreException e) {
                AdtPlugin.log(e, "Can't read session property %1$s", NAME_INITIAL_STATE);
            }
        }

        mConfigComposite = new ConfigurationComposite(mConfigListener, customButtons, parent,
                SWT.BORDER, initialState);

        mSashPalette = new SashForm(parent, SWT.HORIZONTAL);
        mSashPalette.setLayoutData(new GridData(GridData.FILL_BOTH));

        DecorComposite paleteDecor = new DecorComposite(mSashPalette, SWT.BORDER);
        paleteDecor.setContent(new PaletteControl.PaletteDecor(this));
        mPalette = (PaletteControl) paleteDecor.getContentControl();

        mSashError = new SashForm(mSashPalette, SWT.VERTICAL | SWT.BORDER);
        mSashError.setLayoutData(new GridData(GridData.FILL_BOTH));

        mCanvasViewer = new LayoutCanvasViewer(mLayoutEditor, mRulesEngine, mSashError, SWT.NONE);

        mErrorLabel = new StyledText(mSashError, SWT.READ_ONLY);
        mErrorLabel.setEditable(false);
        mErrorLabel.setBackground(d.getSystemColor(SWT.COLOR_INFO_BACKGROUND));
        mErrorLabel.setForeground(d.getSystemColor(SWT.COLOR_INFO_FOREGROUND));
        mErrorLabel.addMouseListener(new ErrorLabelListener());

        mSashPalette.setWeights(new int[] { 20, 80 });
        mSashError.setWeights(new int[] { 80, 20 });
        mSashError.setMaximizedControl(mCanvasViewer.getControl());

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
            if (part instanceof PageBookView) {
                PageBookView pbv = (PageBookView) part;
                IPage currentPage = pbv.getCurrentPage();
                if (currentPage instanceof OutlinePage) {
                    LayoutCanvas canvas = getCanvasControl();
                    if (canvas != null && canvas.getOutlinePage() != currentPage) {
                        // The notification is not for this view; ignore
                        // (can happen when there are multiple pages simultaneously
                        // visible)
                        return;
                    }
                }
            }
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
            s = s * 1.2;
        } else {
            s = s / 1.2;
        }

        // Some operations are faster if the zoom is EXACTLY 1.0 rather than ALMOST 1.0.
        // (This is because there is a fast-path when image copying and the scale is 1.0;
        // in that case it does not have to do any scaling).
        //
        // If you zoom out 10 times and then back in 10 times, small rounding errors mean
        // that you end up with a scale=1.0000000000000004. In the cases, when you get close
        // to 1.0, just make the zoom an exact 1.0.
        if (Math.abs(s-1.0) < 0.0001) {
            s = 1.0;
        }

        mCanvasViewer.getCanvas().setScale(s, true /*redraw*/);

    }

    /**
     * Reset the canvas scale to 100%
     */
    private void resetScale() {
        mCanvasViewer.getCanvas().setScale(1, true /*redraw*/);
    }

    private boolean rescaleToReal(boolean real) {
        if (real) {
            return computeAndSetRealScale(true /*redraw*/);
        } else {
            // reset the scale to 100%
            mCanvasViewer.getCanvas().setScale(1, true /*redraw*/);
            return true;
        }
    }

    private boolean computeAndSetRealScale(boolean redraw) {
        // compute average dpi of X and Y
        float dpi = (mConfigComposite.getXDpi() + mConfigComposite.getYDpi()) / 2.f;

        // get the monitor dpi
        float monitor = AdtPrefs.getPrefs().getMonitorDensity();
        if (monitor == 0.f) {
            ResolutionChooserDialog dialog = new ResolutionChooserDialog(
                    mConfigComposite.getShell());
            if (dialog.open() == Window.OK) {
                monitor = dialog.getDensity();
                AdtPrefs.getPrefs().setMonitorDensity(monitor);
            } else {
                return false;
            }
        }

        mCanvasViewer.getCanvas().setScale(monitor / dpi, redraw);
        return true;
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

        if (mCanvasViewer != null) {
            mCanvasViewer.dispose();
            mCanvasViewer = null;
        }
        super.dispose();
    }

    /**
     * Select the visual element corresponding to the given XML node
     * @param xmlNode The Node whose element we want to select
     */
    public void select(Node xmlNode) {
        mCanvasViewer.getCanvas().getSelectionManager().select(xmlNode);
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

        public void onCreate() {
            LayoutCreatorDialog dialog = new LayoutCreatorDialog(mConfigComposite.getShell(),
                    mEditedFile.getName(), mConfigComposite.getCurrentConfig());
            if (dialog.open() == Window.OK) {
                final FolderConfiguration config = new FolderConfiguration();
                dialog.getConfiguration(config);

                createAlternateLayout(config);
            }
        }

        public void onRenderingTargetPreChange(IAndroidTarget oldTarget) {
            preRenderingTargetChangeCleanUp(oldTarget);
        }

        public void onRenderingTargetPostChange(IAndroidTarget target) {
            AndroidTargetData targetData = Sdk.getCurrent().getTargetData(target);
            updateCapabilities(targetData);
        }

        public Map<String, Map<String, ResourceValue>> getConfiguredFrameworkResources() {
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

        public Map<String, Map<String, ResourceValue>> getConfiguredProjectResources() {
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
         * Returns a {@link ProjectResources} for the framework resources based on the current
         * configuration selection.
         * @return the framework resources or null if not found.
         */
        public ProjectResources getFrameworkResources() {
            return getFrameworkResources(getRenderingTarget());
        }

        /**
         * Returns a {@link ProjectResources} for the framework resources of a given
         * target.
         * @param target the target for which to return the framework resources.
         * @return the framework resources or null if not found.
         */
        public ProjectResources getFrameworkResources(IAndroidTarget target) {
            if (target != null) {
                AndroidTargetData data = Sdk.getCurrent().getTargetData(target);

                if (data != null) {
                    return data.getFrameworkResources();
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

        public void onTargetLoaded(IAndroidTarget loadedTarget) {
            IAndroidTarget target = getRenderingTarget();
            if (target != null && target.equals(loadedTarget)) {
                updateEditor();
            }
        }

        public void onSdkLoaded() {
            // get the current rendering target to unload it
            IAndroidTarget oldTarget = getRenderingTarget();
            preRenderingTargetChangeCleanUp(oldTarget);

            // get the project target
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

    /**
     * Returns the currently edited file
     *
     * @return the currently edited file, or null
     */
    public IFile getEditedFile() {
        return mEditedFile;
    }

    /**
     * Returns the project for the currently edited file, or null
     *
     * @return the project containing the edited file, or null
     */
    public IProject getProject() {
        if (mEditedFile != null) {
            return mEditedFile.getProject();
        } else {
            return null;
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
        if (mNeedsRecompute) {
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
            mRulesEngine = new RulesEngine(this, mEditedFile.getProject());
            if (mCanvasViewer != null) {
                mCanvasViewer.getCanvas().setRulesEngine(mRulesEngine);
            }
        }

        // Pick up hand-off data: somebody requesting this file to be opened may have
        // requested that it should be opened as included within another file
        if (mEditedFile != null) {
            try {
                mIncludedWithin = (Reference) mEditedFile.getSessionProperty(NAME_INCLUDE);
                if (mIncludedWithin != null) {
                    // Only use once
                    mEditedFile.setSessionProperty(NAME_INCLUDE, null);
                }
            } catch (CoreException e) {
                AdtPlugin.log(e, "Can't access session property %1$s", NAME_INCLUDE);
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

    /**
     * Responds to a target change for the project of the edited file
     */
    public void onTargetChange() {
        AndroidTargetData targetData = mConfigComposite.onXmlModelLoaded();
        updateCapabilities(targetData);

        mConfigListener.onConfigurationChange();
    }

    /** Updates the capabilities for the given target data (which may be null) */
    private void updateCapabilities(AndroidTargetData targetData) {
        if (targetData != null) {
            LayoutLibrary layoutLib = targetData.getLayoutLibrary();
            setClippingSupport(layoutLib.supports(Capability.UNBOUND_RENDERING));

            if (mIncludedWithin != null &&  !layoutLib.supports(Capability.EMBEDDED_LAYOUT)) {
                showIn(null);
            }
        }
    }

    public LayoutEditor getLayoutEditor() {
        return mLayoutEditor;
    }

    /**
     * Returns the {@link RulesEngine} associated with this editor
     *
     * @return the {@link RulesEngine} associated with this editor, never null
     */
    public RulesEngine getRulesEngine() {
        return mRulesEngine;
    }

    /**
     * Return the {@link LayoutCanvas} associated with this editor
     *
     * @return the associated {@link LayoutCanvas}
     */
    public LayoutCanvas getCanvasControl() {
        if (mCanvasViewer != null) {
            return mCanvasViewer.getCanvas();
        }
        return null;
    }

    public UiDocumentNode getModel() {
        return mLayoutEditor.getUiRootNode();
    }

    /**
     * Callback for XML model changed. Only update/recompute the layout if the editor is visible
     */
    public void onXmlModelChanged() {
        // To optimize the rendering when the user is editing in the XML pane, we don't
        // refresh the editor if it's not the active part.
        //
        // This behavior is acceptable when the editor is the single "full screen" part
        // (as in this case active means visible.)
        // Unfortunately this breaks in 2 cases:
        // - when performing a drag'n'drop from one editor to another, the target is not
        //   properly refreshed before it becomes active.
        // - when duplicating the editor window and placing both editors side by side (xml in one
        //   and canvas in the other one), the canvas may not be refreshed when the XML is edited.
        //
        // TODO find a way to really query whether the pane is visible, not just active.

        if (mLayoutEditor.isGraphicalEditorActive()) {
            recomputeLayout();
        } else {
            // Remember we want to recompute as soon as the editor becomes active.
            mNeedsRecompute = true;
        }
    }

    public void recomputeLayout() {
        try {
            if (!ensureFileValid()) {
                return;
            }

            UiDocumentNode model = getModel();
            if (!ensureModelValid(model)) {
                // Although we display an error, we still treat an empty document as a
                // successful layout result so that we can drop new elements in it.
                //
                // For that purpose, create a special LayoutScene that has no image,
                // no root view yet indicates success and then update the canvas with it.

                mCanvasViewer.getCanvas().setSession(
                        new StaticRenderSession(
                                Result.Status.SUCCESS.createResult(),
                                null /*rootViewInfo*/, null /*image*/),
                        null /*explodeNodes*/);
                return;
            }

            LayoutLibrary layoutLib = getReadyLayoutLib(true /*displayError*/);

            if (layoutLib != null) {
                // if drawing in real size, (re)set the scaling factor.
                if (mZoomRealSizeButton.getSelection()) {
                    computeAndSetRealScale(false /* redraw */);
                }

                IProject iProject = mEditedFile.getProject();
                renderWithBridge(iProject, model, layoutLib);
            }
        } finally {
            // no matter the result, we are done doing the recompute based on the latest
            // resource/code change.
            mNeedsRecompute = false;
        }
    }

    public void reloadPalette() {
        if (mPalette != null) {
            mPalette.reloadPalette(mLayoutEditor.getTargetData());
        }
    }

    /**
     * Renders the given model, using this editor's theme and screen settings, and returns
     * the result as a {@link LayoutScene}. Any error messages will be written to the
     * editor's error area.
     *
     * @param model the model to be rendered, which can be different than the editor's own
     *            {@link #getModel()}.
     * @param width the width to use for the layout, or -1 to use the width of the screen
     *            associated with this editor
     * @param height the height to use for the layout, or -1 to use the height of the screen
     *            associated with this editor
     * @param explodeNodes a set of nodes to explode, or null for none
     * @param transparentBackground If true, the rendering will <b>not</b> paint the
     *            normal background requested by the theme, and it will instead paint the
     *            background using a fully transparent background color
     * @return the resulting rendered image wrapped in an {@link LayoutScene}
     */
    public RenderSession render(UiDocumentNode model, int width, int height,
            Set<UiElementNode> explodeNodes, boolean transparentBackground) {
        if (!ensureFileValid()) {
            return null;
        }
        if (!ensureModelValid(model)) {
            return null;
        }
        LayoutLibrary layoutLib = getReadyLayoutLib(true /*displayError*/);

        IProject iProject = mEditedFile.getProject();
        return renderWithBridge(iProject, model, layoutLib, width, height, explodeNodes,
                transparentBackground);
    }

    /**
     * Returns the {@link LayoutLibrary} associated with this editor, if it has
     * been initialized already. May return null if it has not been initialized (or has
     * not finished initializing).
     *
     * @return The {@link LayoutLibrary}, or null
     */
    public LayoutLibrary getLayoutLibrary() {
        return getReadyLayoutLib(false /*displayError*/);
    }

    /**
     * Returns the current bounds of the Android device screen, in canvas control pixels.
     *
     * @return the bounds of the screen, never null
     */
    public Rectangle getScreenBounds() {
        return mConfigComposite.getScreenBounds();
    }

    /**
     * Returns the scale to multiply pixels in the layout coordinate space with to obtain
     * the corresponding dip (device independent pixel)
     *
     * @return the scale to multiple layout coordinates with to obtain the dip position
     */
    public float getDipScale() {
        return DEFAULT_DENSITY / (float) mConfigComposite.getDensity().getDpiValue();
    }

    // --- private methods ---

    private void setClippingSupport(boolean b) {
        mClippingButton.setEnabled(b);
        if (b) {
            mClippingButton.setToolTipText("Toggles screen clipping on/off");
        } else {
            mClippingButton.setSelection(true);
            mClippingButton.setToolTipText("Non clipped rendering is not supported");
        }
    }

    /**
     * Ensure that the file associated with this editor is valid (exists and is
     * synchronized). Any reasons why it is not are displayed in the editor's error area.
     *
     * @return True if the editor is valid, false otherwise.
     */
    private boolean ensureFileValid() {
        // check that the resource exists. If the file is opened but the project is closed
        // or deleted for some reason (changed from outside of eclipse), then this will
        // return false;
        if (mEditedFile.exists() == false) {
            displayError("Resource '%1$s' does not exist.",
                         mEditedFile.getFullPath().toString());
            return false;
        }

        if (mEditedFile.isSynchronized(IResource.DEPTH_ZERO) == false) {
            String message = String.format("%1$s is out of sync. Please refresh.",
                    mEditedFile.getName());

            displayError(message);

            // also print it in the error console.
            IProject iProject = mEditedFile.getProject();
            AdtPlugin.printErrorToConsole(iProject.getName(), message);
            return false;
        }

        return true;
    }

    /**
     * Returns a {@link LayoutLibrary} that is ready for rendering, or null if the bridge
     * is not available or not ready yet (due to SDK loading still being in progress etc).
     * If enabled, any reasons preventing the bridge from being returned are displayed to the
     * editor's error area.
     *
     * @param displayError whether to display the loading error or not.
     *
     * @return LayoutBridge the layout bridge for rendering this editor's scene
     */
    private LayoutLibrary getReadyLayoutLib(boolean displayError) {
        Sdk currentSdk = Sdk.getCurrent();
        if (currentSdk != null) {
            IAndroidTarget target = getRenderingTarget();

            if (target != null) {
                AndroidTargetData data = currentSdk.getTargetData(target);
                if (data != null) {
                    LayoutLibrary layoutLib = data.getLayoutLibrary();

                    if (layoutLib.getStatus() == LoadStatus.LOADED) {
                        return layoutLib;
                    } else if (displayError) { // getBridge() == null
                        // SDK is loaded but not the layout library!

                        // check whether the bridge managed to load, or not
                        if (layoutLib.getStatus() == LoadStatus.LOADING) {
                            displayError("Eclipse is loading framework information and the layout library from the SDK folder.\n%1$s will refresh automatically once the process is finished.",
                                         mEditedFile.getName());
                        } else {
                            String message = layoutLib.getLoadMessage();
                            displayError("Eclipse failed to load the framework information and the layout library!" +
                                    message != null ? "\n" + message : "");
                        }
                    }
                } else { // data == null
                    // It can happen that the workspace refreshes while the SDK is loading its
                    // data, which could trigger a redraw of the opened layout if some resources
                    // changed while Eclipse is closed.
                    // In this case data could be null, but this is not an error.
                    // We can just silently return, as all the opened editors are automatically
                    // refreshed once the SDK finishes loading.
                    LoadStatus targetLoadStatus = currentSdk.checkAndLoadTargetData(target, null);

                    // display error is asked.
                    if (displayError) {
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
                    }
                }

            } else if (displayError) { // target == null
                displayError("The project target is not set.");
            }
        } else if (displayError) { // currentSdk == null
            displayError("Eclipse is loading the SDK.\n%1$s will refresh automatically once the process is finished.",
                         mEditedFile.getName());
        }

        return null;
    }

    /**
     * Returns the {@link IAndroidTarget} used for the rendering.
     * <p/>
     * This first looks for the rendering target setup in the config UI, and if nothing has
     * been setup yet, returns the target of the project.
     *
     * @return an IAndroidTarget object or null if no target is setup and the project has no
     * target set.
     *
     */
    private IAndroidTarget getRenderingTarget() {
        // if the SDK is null no targets are loaded.
        Sdk currentSdk = Sdk.getCurrent();
        if (currentSdk == null) {
            return null;
        }

        assert mConfigComposite.getDisplay().getThread() == Thread.currentThread();

        // attempt to get a target from the configuration selector.
        IAndroidTarget renderingTarget = mConfigComposite.getRenderingTarget();
        if (renderingTarget != null) {
            return renderingTarget;
        }

        // fall back to the project target
        if (mEditedFile != null) {
            return currentSdk.getTarget(mEditedFile.getProject());
        }

        return null;
    }

    /**
     * Returns whether the current rendering target supports the given capability
     *
     * @param capability the capability to be looked up
     * @return true if the current rendering target supports the given capability
     */
    public boolean renderingSupports(Capability capability) {
        IAndroidTarget target = getRenderingTarget();
        if (target != null) {
            AndroidTargetData targetData = Sdk.getCurrent().getTargetData(target);
            LayoutLibrary layoutLib = targetData.getLayoutLibrary();
            return layoutLib.supports(capability);
        }

        return false;
    }

    private boolean ensureModelValid(UiDocumentNode model) {
        // check there is actually a model (maybe the file is empty).
        if (model.getUiChildren().size() == 0) {
            displayError(
                    "No XML content. Please add a root view or layout to your document.");
            return false;
        }

        return true;
    }

    private void renderWithBridge(IProject iProject, UiDocumentNode model,
            LayoutLibrary layoutLib) {
        LayoutCanvas canvas = getCanvasControl();
        Set<UiElementNode> explodeNodes = canvas.getNodesToExplode();

        // Compute the layout
        Rectangle rect = getScreenBounds();

        int width = rect.width;
        int height = rect.height;

        RenderSession session = renderWithBridge(iProject, model, layoutLib, width, height,
                explodeNodes, false);

        canvas.setSession(session, explodeNodes);

        // update the UiElementNode with the layout info.
        if (session.getResult().isSuccess() == false) {
            // An error was generated. Print it.
            displayError(session.getResult().getErrorMessage());

        } else {
            // Success means there was no exception. But we might have detected
            // some missing classes and swapped them by a mock view.
            Set<String> missingClasses = mProjectCallback.getMissingClasses();
            Set<String> brokenClasses = mProjectCallback.getUninstantiatableClasses();
            if (missingClasses.size() > 0 || brokenClasses.size() > 0) {
                displayFailingClasses(missingClasses, brokenClasses);
            } else {
                // Nope, no missing or broken classes. Clear success, congrats!
                hideError();
            }
        }

        model.refreshUi();
    }

    private RenderSession renderWithBridge(IProject iProject, UiDocumentNode model,
            LayoutLibrary layoutLib, int width, int height, Set<UiElementNode> explodeNodes,
            boolean transparentBackground) {
        ResourceManager resManager = ResourceManager.getInstance();

        ProjectResources projectRes = resManager.getProjectResources(iProject);
        if (projectRes == null) {
            displayError("Missing project resources.");
            return null;
        }

        // Get the resources of the file's project.
        Map<String, Map<String, ResourceValue>> configuredProjectRes =
            mConfigListener.getConfiguredProjectResources();

        // Get the framework resources
        Map<String, Map<String, ResourceValue>> frameworkResources =
            mConfigListener.getConfiguredFrameworkResources();

        // Abort the rendering if the resources are not found.
        if (configuredProjectRes == null) {
            displayError("Missing project resources for current configuration.");
            return null;
        }

        if (frameworkResources == null) {
            displayError("Missing framework resources.");
            return null;
        }

        // Lazily create the project callback the first time we need it
        if (mProjectCallback == null) {
            mProjectCallback = new ProjectCallback(
                    layoutLib.getClassLoader(), projectRes, iProject);
        } else {
            // Also clears the set of missing classes prior to rendering
            mProjectCallback.getMissingClasses().clear();
        }

        // Lazily create the logger the first time we need it
        if (mLog == null) {
            mLog = new LayoutLog() {
                @Override
                public void error(String tag, String message) {
                    AdtPlugin.printErrorToConsole(mEditedFile.getName(), message);
                }

                @Override
                public void error(String tag, Throwable throwable) {
                    String message = throwable.getMessage();
                    if (message == null) {
                        message = throwable.getClass().getName();
                    }

                    PrintStream ps = new PrintStream(AdtPlugin.getErrorStream());
                    throwable.printStackTrace(ps);
                }

                @Override
                public void error(String tag, String message, Throwable throwable) {
                    AdtPlugin.printErrorToConsole(mEditedFile.getName(), message);

                    PrintStream ps = new PrintStream(AdtPlugin.getErrorStream());
                    throwable.printStackTrace(ps);
                }

                @Override
                public void warning(String tag, String message) {
                    AdtPlugin.printToConsole(mEditedFile.getName(), message);
                }

                @Override
                public void fidelityWarning(String tag, String message, Throwable throwable) {
                    AdtPlugin.printToConsole(mEditedFile.getName(), message);
                }
            };
        }

        // get the selected theme
        String theme = mConfigComposite.getTheme();
        if (theme == null) {
            displayError("Missing theme.");
            return null;
        }

        if (mUseExplodeMode) {
            // compute how many padding in x and y will bump the screen size
            List<UiElementNode> children = model.getUiChildren();
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

        ILayoutPullParser modelParser = new UiElementPullParser(model,
                mUseExplodeMode, explodeNodes, density, xdpi, iProject);
        ILayoutPullParser topParser = modelParser;

        // Code to support editing included layout

        // Outer layout name:
        if (mIncludedWithin != null) {
            String contextLayoutName = mIncludedWithin.getName();

            // Find the layout file.
            Map<String, ResourceValue> layouts = configuredProjectRes.get(
                    ResourceType.LAYOUT.getName());
            ResourceValue contextLayout = layouts.get(contextLayoutName);
            if (contextLayout != null) {
                File layoutFile = new File(contextLayout.getValue());
                if (layoutFile.isFile()) {
                    try {
                        // Get the name of the layout actually being edited, without the extension
                        // as it's what IXmlPullParser.getParser(String) will receive.
                        String queryLayoutName = getLayoutResourceName();
                        topParser = new ContextPullParser(queryLayoutName, modelParser);
                        topParser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
                        topParser.setInput(new FileReader(layoutFile));
                    } catch (XmlPullParserException e) {
                        AdtPlugin.log(e, ""); //$NON-NLS-1$
                    } catch (FileNotFoundException e) {
                        // this will not happen since we check above.
                    }
                }
            }
        }

        RenderingMode renderingMode = RenderingMode.NORMAL;
        if (mClippingButton.getSelection() == false) {
            renderingMode = RenderingMode.FULL_EXPAND;
        } else {
            // FIXME set the rendering mode using ViewRule or something.
            List<UiElementNode> children = model.getUiChildren();
            if (children.size() > 0 &&
                    children.get(0).getDescriptor().getXmlLocalName().equals("ScrollView")) {
                renderingMode = RenderingMode.V_SCROLL;
            }
        }

        Params params = new Params(
                topParser,
                iProject /* projectKey */,
                width, height,
                renderingMode,
                density, xdpi, ydpi,
                theme, isProjectTheme,
                configuredProjectRes, frameworkResources, mProjectCallback,
                mLog);

        if (transparentBackground) {
            // It doesn't matter what the background color is as long as the alpha
            // is 0 (fully transparent). We're using red to make it more obvious if
            // for some reason the background is painted when it shouldn't be.
            params.setOverrideBgColor(0x00FF0000);
        }

        // set the Image Overlay as the image factory.
        params.setImageFactory(getCanvasControl().getImageOverlay());

        return layoutLib.createSession(params);
    }

    /**
     * Returns the resource name of this layout, NOT including the @layout/ prefix
     *
     * @return the resource name of this layout, NOT including the @layout/ prefix
     */
    public String getLayoutResourceName() {
        String name = mEditedFile.getName();
        int dotIndex = name.indexOf('.');
        if (dotIndex != -1) {
            name = name.substring(0, dotIndex);
        }
        return name;
    }

    /**
     * Cleans up when the rendering target is about to change
     * @param oldTarget the old rendering target.
     */
    private void preRenderingTargetChangeCleanUp(IAndroidTarget oldTarget) {
        // first clear the caches related to this file in the old target
        Sdk currentSdk = Sdk.getCurrent();
        if (currentSdk != null) {
            AndroidTargetData data = currentSdk.getTargetData(oldTarget);
            if (data != null) {
                LayoutLibrary layoutLib = data.getLayoutLibrary();

                // layoutLib can never be null.
                layoutLib.clearCaches(mEditedFile.getProject());
            }
        }

        // FIXME: get rid of the current LayoutScene if any.
    }

    private class ReloadListener implements ILayoutReloadListener {
        /**
         * Called when the file changes triggered a redraw of the layout
         */
        public void reloadLayout(final ChangeFlags flags, final boolean libraryChanged) {
            Display display = mConfigComposite.getDisplay();
            display.asyncExec(new Runnable() {
                public void run() {
                    reloadLayoutSwt(flags, libraryChanged);
                }
            });
        }

        /** Reload layout. <b>Must be called on the SWT thread</b> */
        private void reloadLayoutSwt(ChangeFlags flags, boolean libraryChanged) {
            assert mConfigComposite.getDisplay().getThread() == Thread.currentThread();

            boolean recompute = false;
            if (flags.rClass) {
                recompute = true;
                if (mEditedFile != null) {
                    ResourceManager manager = ResourceManager.getInstance();
                    ProjectResources projectRes = manager.getProjectResources(
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
                mConfigComposite.updateLocales();
            }

            // if a resources was modified.
            // also, if a layout in a library was modified.
            if (flags.resources || (libraryChanged && flags.layout)) {
                recompute = true;

                // TODO: differentiate between single and multi resource file changed, and whether
                // the resource change affects the cache.

                // force a reparse in case a value XML file changed.
                mConfiguredProjectRes = null;

                // clear the cache in the bridge in case a bitmap/9-patch changed.
                LayoutLibrary layoutLib = getReadyLayoutLib(true /*displayError*/);
                if (layoutLib != null) {
                    layoutLib.clearCaches(mEditedFile.getProject());
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
                if (mLayoutEditor.isGraphicalEditorActive()) {
                    recomputeLayout();
                } else {
                    mNeedsRecompute = true;
                }
            }
        }
    }

    // ---- Error handling ----

    /**
     * Switches the sash to display the error label.
     *
     * @param errorFormat The new error to display if not null.
     * @param parameters String.format parameters for the error format.
     */
    private void displayError(String errorFormat, Object...parameters) {
        if (errorFormat != null) {
            mErrorLabel.setText(String.format(errorFormat, parameters));
        } else {
            mErrorLabel.setText("");
        }
        mSashError.setMaximizedControl(null);
    }

    /** Displays the canvas and hides the error label. */
    private void hideError() {
        mErrorLabel.setText("");
        mSashError.setMaximizedControl(mCanvasViewer.getControl());
    }

    /**
     * Switches the sash to display the error label to show a list of
     * missing classes and give options to create them.
     */
    private void displayFailingClasses(Set<String> missingClasses, Set<String> brokenClasses) {
        mErrorLabel.setText("");
        if (missingClasses.size() > 0) {
            addText(mErrorLabel, "The following classes could not be found:\n");
            for (String clazz : missingClasses) {
                addText(mErrorLabel, "- ");
                addClassLink(mErrorLabel, clazz);
                addText(mErrorLabel, "\n");
            }
        }
        if (brokenClasses.size() > 0) {
            addText(mErrorLabel, "The following classes could not be instantiated:\n");

            // Do we have a custom class (not an Android or add-ons class)
            boolean haveCustomClass = false;

            for (String clazz : brokenClasses) {
                addText(mErrorLabel, "- ");
                addClassLink(mErrorLabel, clazz);
                addText(mErrorLabel, "\n");

                if (!(clazz.startsWith("android.") || //$NON-NLS-1$
                        clazz.startsWith("com.google."))) { //$NON-NLS-1$
                    haveCustomClass = true;
                }
            }

            addText(mErrorLabel, "See the Error Log (Window > Show View) for more details.\n");

            if (haveCustomClass) {
                addText(mErrorLabel, "Tip: Use View.isInEditMode() in your custom views "
                        + "to skip code when shown in Eclipse");
            }
        }

        mSashError.setMaximizedControl(null);
    }

    /** Add a normal line of text to the styled text widget. */
    private void addText(StyledText styledText, String...string) {
        for (String s : string) {
            styledText.append(s);
        }
    }

    /**
     * Add a URL-looking link to the styled text widget.
     * <p/>
     * A mouse-click listener is setup and it interprets the link as being a missing class name.
     * The logic *must* be changed if this is used later for a different purpose.
     */
    private void addClassLink(StyledText styledText, String link) {
        String s = styledText.getText();
        int start = (s == null ? 0 : s.length());
        styledText.append(link);

        StyleRange sr = new ClassLinkStyleRange();
        sr.start = start;
        sr.length = link.length();
        sr.fontStyle = SWT.NORMAL;
        // We want to use SWT.UNDERLINE_LINK but the constant is only
        // available when using SWT from Eclipse 3.5+
        int version = SWT.getVersion();
        if (version > 3500) {
            sr.underlineStyle = 4 /*SWT.UNDERLINE_LINK*/;
        }
        sr.underline = true;
        styledText.setStyleRange(sr);
    }

    /**
     * Looks up the resource file corresponding to the given type
     *
     * @param type The type of resource to look up, such as {@link ResourceType#LAYOUT}
     * @param name The name of the resource (not including ".xml")
     * @param isFrameworkResource if true, the resource is a framework resource, otherwise
     *            it's a project resource
     * @return the resource file defining the named resource, or null if not found
     */
    public IPath findResourceFile(ResourceType type, String name, boolean isFrameworkResource) {
        // FIXME: This code does not handle theme value resolution.
        // There is code to handle this, but it's in layoutlib; we should
        // expose that and use it here.

        Map<String, Map<String, ResourceValue>> map;
        map = isFrameworkResource ? mConfiguredFrameworkRes : mConfiguredProjectRes;
        if (map == null) {
            // Not yet configured
            return null;
        }

        Map<String, ResourceValue> layoutMap = map.get(type.getName());
        if (layoutMap != null) {
            ResourceValue value = layoutMap.get(name);
            if (value != null) {
                String valueStr = value.getValue();
                if (valueStr.startsWith("?")) { //$NON-NLS-1$
                    // FIXME: It's a reference. We should resolve this properly.
                    return null;
                }
                return new Path(valueStr);
            }
        }

        return null;
    }

    /**
     * Looks up the path to the file corresponding to the given attribute value, such as
     * @layout/foo, which will return the foo.xml file in res/layout/. (The general format
     * of the resource url is {@literal @[<package_name>:]<resource_type>/<resource_name>}.
     *
     * @param url the attribute url
     * @return the path to the file defining this attribute, or null if not found
     */
    public IPath findResourceFile(String url) {
        if (!url.startsWith("@")) { //$NON-NLS-1$
            return null;
        }
        int typeEnd = url.indexOf('/', 1);
        if (typeEnd == -1) {
            return null;
        }
        int nameBegin = typeEnd + 1;
        int typeBegin = 1;
        int colon = url.lastIndexOf(':', typeEnd);
        boolean isFrameworkResource = false;
        if (colon != -1) {
            // The URL contains a package name.
            // While the url format technically allows other package names,
            // the platform apparently only supports @android for now (or if it does,
            // there are no usages in the current code base so this is not common).
            String packageName = url.substring(typeBegin, colon);
            if (ANDROID_PKG.equals(packageName)) {
                isFrameworkResource = true;
            }

            typeBegin = colon + 1;
        }

        String typeName = url.substring(typeBegin, typeEnd);
        ResourceType type = ResourceType.getEnum(typeName);
        if (type == null) {
            return null;
        }

        String name = url.substring(nameBegin);
        return findResourceFile(type, name, isFrameworkResource);
    }

    /** This StyleRange represents a missing class link that the user can click */
    private static class ClassLinkStyleRange extends StyleRange {}

    /**
     * Monitor clicks on the error label.
     * If the click happens on a style range created by
     * {@link GraphicalEditorPart#addClassLink(StyledText, String)}, we assume it's about
     * a missing class and we then proceed to display the standard Eclipse class creator wizard.
     */
    private class ErrorLabelListener extends MouseAdapter {

        @Override
        public void mouseUp(MouseEvent event) {
            super.mouseUp(event);

            if (event.widget != mErrorLabel) {
                return;
            }

            int offset = mErrorLabel.getCaretOffset();

            StyleRange r = null;
            StyleRange[] ranges = mErrorLabel.getStyleRanges();
            if (ranges != null && ranges.length > 0) {
                for (StyleRange sr : ranges) {
                    if (sr.start <= offset && sr.start + sr.length > offset) {
                        r = sr;
                        break;
                    }
                }
            }

            if (r instanceof ClassLinkStyleRange) {
                String link = mErrorLabel.getText(r.start, r.start + r.length - 1);
                createNewClass(link);
            }
        }

        private void createNewClass(String fqcn) {

            int pos = fqcn.lastIndexOf('.');
            String packageName = pos < 0 ? "" : fqcn.substring(0, pos);  //$NON-NLS-1$
            String className = pos <= 0 || pos >= fqcn.length() ? "" : fqcn.substring(pos + 1); //$NON-NLS-1$

            // create the wizard page for the class creation, and configure it
            NewClassWizardPage page = new NewClassWizardPage();

            // set the parent class
            page.setSuperClass(SdkConstants.CLASS_VIEW, true /* canBeModified */);

            // get the source folders as java elements.
            IPackageFragmentRoot[] roots = getPackageFragmentRoots(mLayoutEditor.getProject(),
                    true /*include_containers*/);

            IPackageFragmentRoot currentRoot = null;
            IPackageFragment currentFragment = null;
            int packageMatchCount = -1;

            for (IPackageFragmentRoot root : roots) {
                // Get the java element for the package.
                // This method is said to always return a IPackageFragment even if the
                // underlying folder doesn't exist...
                IPackageFragment fragment = root.getPackageFragment(packageName);
                if (fragment != null && fragment.exists()) {
                    // we have a perfect match! we use it.
                    currentRoot = root;
                    currentFragment = fragment;
                    packageMatchCount = -1;
                    break;
                } else {
                    // we don't have a match. we look for the fragment with the best match
                    // (ie the closest parent package we can find)
                    try {
                        IJavaElement[] children;
                        children = root.getChildren();
                        for (IJavaElement child : children) {
                            if (child instanceof IPackageFragment) {
                                fragment = (IPackageFragment)child;
                                if (packageName.startsWith(fragment.getElementName())) {
                                    // its a match. get the number of segments
                                    String[] segments = fragment.getElementName().split("\\."); //$NON-NLS-1$
                                    if (segments.length > packageMatchCount) {
                                        packageMatchCount = segments.length;
                                        currentFragment = fragment;
                                        currentRoot = root;
                                    }
                                }
                            }
                        }
                    } catch (JavaModelException e) {
                        // Couldn't get the children: we just ignore this package root.
                    }
                }
            }

            ArrayList<IPackageFragment> createdFragments = null;

            if (currentRoot != null) {
                // if we have a perfect match, we set it and we're done.
                if (packageMatchCount == -1) {
                    page.setPackageFragmentRoot(currentRoot, true /* canBeModified*/);
                    page.setPackageFragment(currentFragment, true /* canBeModified */);
                } else {
                    // we have a partial match.
                    // create the package. We have to start with the first segment so that we
                    // know what to delete in case of a cancel.
                    try {
                        createdFragments = new ArrayList<IPackageFragment>();

                        int totalCount = packageName.split("\\.").length; //$NON-NLS-1$
                        int count = 0;
                        int index = -1;
                        // skip the matching packages
                        while (count < packageMatchCount) {
                            index = packageName.indexOf('.', index+1);
                            count++;
                        }

                        // create the rest of the segments, except for the last one as indexOf will
                        // return -1;
                        while (count < totalCount - 1) {
                            index = packageName.indexOf('.', index+1);
                            count++;
                            createdFragments.add(currentRoot.createPackageFragment(
                                    packageName.substring(0, index),
                                    true /* force*/, new NullProgressMonitor()));
                        }

                        // create the last package
                        createdFragments.add(currentRoot.createPackageFragment(
                                packageName, true /* force*/, new NullProgressMonitor()));

                        // set the root and fragment in the Wizard page
                        page.setPackageFragmentRoot(currentRoot, true /* canBeModified*/);
                        page.setPackageFragment(createdFragments.get(createdFragments.size()-1),
                                true /* canBeModified */);
                    } catch (JavaModelException e) {
                        // If we can't create the packages, there's a problem.
                        // We revert to the default package
                        for (IPackageFragmentRoot root : roots) {
                            // Get the java element for the package.
                            // This method is said to always return a IPackageFragment even if the
                            // underlying folder doesn't exist...
                            IPackageFragment fragment = root.getPackageFragment(packageName);
                            if (fragment != null && fragment.exists()) {
                                page.setPackageFragmentRoot(root, true /* canBeModified*/);
                                page.setPackageFragment(fragment, true /* canBeModified */);
                                break;
                            }
                        }
                    }
                }
            } else if (roots.length > 0) {
                // if we haven't found a valid fragment, we set the root to the first source folder.
                page.setPackageFragmentRoot(roots[0], true /* canBeModified*/);
            }

            // if we have a starting class name we use it
            if (className != null) {
                page.setTypeName(className, true /* canBeModified*/);
            }

            // create the action that will open it the wizard.
            OpenNewClassWizardAction action = new OpenNewClassWizardAction();
            action.setConfiguredWizardPage(page);
            action.run();
            IJavaElement element = action.getCreatedElement();

            if (element == null) {
                // lets delete the packages we created just for this.
                // we need to start with the leaf and go up
                if (createdFragments != null) {
                    try {
                        for (int i = createdFragments.size() - 1 ; i >= 0 ; i--) {
                            createdFragments.get(i).delete(true /* force*/,
                                                           new NullProgressMonitor());
                        }
                    } catch (JavaModelException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        /**
         * Computes and return the {@link IPackageFragmentRoot}s corresponding to the source
         * folders of the specified project.
         *
         * @param project the project
         * @param include_containers True to include containers
         * @return an array of IPackageFragmentRoot.
         */
        private IPackageFragmentRoot[] getPackageFragmentRoots(IProject project,
                boolean include_containers) {
            ArrayList<IPackageFragmentRoot> result = new ArrayList<IPackageFragmentRoot>();
            try {
                IJavaProject javaProject = JavaCore.create(project);
                IPackageFragmentRoot[] roots = javaProject.getPackageFragmentRoots();
                for (int i = 0; i < roots.length; i++) {
                    IClasspathEntry entry = roots[i].getRawClasspathEntry();
                    if (entry.getEntryKind() == IClasspathEntry.CPE_SOURCE ||
                            (include_containers &&
                                    entry.getEntryKind() == IClasspathEntry.CPE_CONTAINER)) {
                        result.add(roots[i]);
                    }
                }
            } catch (JavaModelException e) {
            }

            return result.toArray(new IPackageFragmentRoot[result.size()]);
        }
    }

    /**
     * Reopens this file as included within the given file (this assumes that the given
     * file has an include tag referencing this view, and the set of views that have this
     * property can be found using the {@link IncludeFinder}.
     *
     * @param includeWithin reference to a file to include as a surrounding context,
     *   or null to show the file standalone
     */
    public void showIn(Reference includeWithin) {
        mIncludedWithin = includeWithin;

        if (includeWithin != null) {
            IFile file = includeWithin.getFile();

            // Update configuration
            if (file != null) {
                mConfigComposite.resetConfigFor(file);
            }
        }
        recomputeLayout();
    }

    /**
     * Returns the resource name of the file that is including this current layout, if any
     * (may be null)
     *
     * @return the resource name of an including layout, or null
     */
    public Reference getIncludedWithin() {
        return mIncludedWithin;
    }
}
