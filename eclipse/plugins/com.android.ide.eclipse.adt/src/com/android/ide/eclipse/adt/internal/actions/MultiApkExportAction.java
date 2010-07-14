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
import com.android.ide.eclipse.adt.internal.build.PostCompilerHelper;
import com.android.ide.eclipse.adt.internal.project.ProjectHelper;
import com.android.ide.eclipse.adt.internal.sdk.ProjectState;
import com.android.ide.eclipse.adt.internal.sdk.Sdk;
import com.android.ide.eclipse.adt.io.IFolderWrapper;
import com.android.sdklib.SdkConstants;
import com.android.sdklib.internal.export.ApkData;
import com.android.sdklib.internal.export.MultiApkExportHelper;
import com.android.sdklib.internal.export.ProjectConfig;
import com.android.sdklib.internal.export.MultiApkExportHelper.ExportException;
import com.android.sdklib.internal.export.MultiApkExportHelper.Target;
import com.android.sdklib.internal.project.ProjectProperties;
import com.android.sdklib.internal.project.ProjectProperties.PropertyType;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PlatformUI;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Multiple APK export Action.
 * The action is triggered on a project selection, and performs a full APK export based on the
 * content of the export.properties file.
 */
public class MultiApkExportAction implements IObjectActionDelegate {

    private ISelection mSelection;

    public void setActivePart(IAction action, IWorkbenchPart targetPart) {
        // pass
    }

    public void run(IAction action) {
        if (mSelection instanceof IStructuredSelection) {
            for (Iterator<?> it = ((IStructuredSelection)mSelection).iterator(); it.hasNext();) {
                Object element = it.next();
                IProject project = null;
                if (element instanceof IProject) {
                    project = (IProject)element;
                } else if (element instanceof IAdaptable) {
                    project = (IProject)((IAdaptable)element).getAdapter(IProject.class);
                }
                if (project != null) {
                    IWorkbench workbench = PlatformUI.getWorkbench();
                    final IProject fProject = project;
                    try {
                        workbench.getProgressService().busyCursorWhile(new IRunnableWithProgress() {
                            /**
                             * Run the export.
                             * @throws InvocationTargetException
                             * @throws InterruptedException
                             */
                            public void run(IProgressMonitor monitor)
                                    throws InvocationTargetException, InterruptedException {
                                try {
                                    runMultiApkExport(fProject, monitor);
                                } catch (Exception e) {
                                    AdtPlugin.logAndPrintError(e, fProject.getName(),
                                            "Failed to export project: %1$s", e.getMessage());
                                } finally {
                                    monitor.done();
                                }
                            }
                        });
                    } catch (Exception e) {
                        AdtPlugin.logAndPrintError(e, project.getName(),
                                "Failed to export project: %1$s",
                                e.getMessage());
                    }
                }
            }
        }
    }

    public void selectionChanged(IAction action, ISelection selection) {
        mSelection = selection;
    }

    /**
     * Runs the multi-apk export.
     * @param exportProject the main "export" project.
     * @param monitor the progress monitor.
     * @throws ExportException
     * @throws CoreException
     */
    private void runMultiApkExport(IProject exportProject, IProgressMonitor monitor)
            throws ExportException, CoreException {

        ProjectProperties props = ProjectProperties.load(new IFolderWrapper(exportProject),
                PropertyType.EXPORT);

        // get some props and make sure their values are valid.

        String appPackage = props.getProperty(ProjectProperties.PROPERTY_PACKAGE);
        if (appPackage == null || appPackage.length() == 0) {
            throw new IllegalArgumentException("Invalid 'package' property values.");
        }

        String version = props.getProperty(ProjectProperties.PROPERTY_VERSIONCODE);
        int versionCode;
        try {
            versionCode = Integer.parseInt(version);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("version value is not a valid integer.", e);
        }

        String projects = props.getProperty(ProjectProperties.PROPERTY_PROJECTS);
        if (projects == null || projects.length() == 0) {
            throw new IllegalArgumentException("Missing project list.");
        }

        // create the multi apk helper to get the list of apk to export.
        MultiApkExportHelper helper = new MultiApkExportHelper(
                exportProject.getLocation().toOSString(),
                appPackage, versionCode, Target.RELEASE, System.out);

        List<ApkData> apks = helper.getApkData(projects);

        // list of projects that have been resolved (ie the IProject has been found from the
        // ProjectConfig) and compiled.
        HashMap<ProjectConfig, ProjectState> resolvedProjects =
                new HashMap<ProjectConfig, ProjectState>();

        IWorkspace ws = ResourcesPlugin.getWorkspace();
        IWorkspaceRoot wsRoot = ws.getRoot();

        // bin folder for the export project
        IFolder binFolder = exportProject.getFolder(SdkConstants.FD_OUTPUT);
        if (binFolder.exists() == false) {
            binFolder.create(true, true, monitor);
        }

        for (ApkData apk : apks) {
            // find the IProject object for this apk.
            ProjectConfig projectConfig = apk.getProjectConfig();
            ProjectState projectState = resolvedProjects.get(projectConfig);
            if (projectState == null) {
                // first time? resolve the project and compile it.
                IPath path = exportProject.getFullPath().append(projectConfig.getRelativePath());

                IResource res = wsRoot.findMember(path);
                if (res.getType() != IResource.PROJECT) {
                    throw new IllegalArgumentException(String.format(
                            "%1$s does not resolve to a project.",
                            projectConfig.getRelativePath()));
                }

                IProject project = (IProject)res;

                projectState = Sdk.getProjectState(project);
                if (projectState == null) {
                    throw new IllegalArgumentException(String.format(
                            "State for project %1$s could not be loaded.",
                            project.getName()));
                }

                if (projectState.isLibrary()) {
                    throw new IllegalArgumentException(String.format(
                            "Project %1$s is a library and cannot be part of a multi-apk export.",
                            project.getName()));
                }

                // build the project, mainly for the java compilation. The rest is handled below.
                project.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, monitor);

                // store the resolved project in the map.
                resolvedProjects.put(projectConfig, projectState);
            }

            Map<String, String> variantMap = apk.getSoftVariantMap();

            if (variantMap.size() > 0) {
                // if there are soft variants, only export those.
                for (Entry<String, String> entry : variantMap.entrySet()) {
                    buildVariant(wsRoot, projectState, appPackage, versionCode, apk, entry,
                            binFolder);
                }
            } else {
                buildVariant(wsRoot, projectState, appPackage, versionCode, apk,
                        null /*soft variant*/, binFolder);
            }
        }

        helper.writeLogs();
    }

    /**
     * Builds a particular variant of an APK
     * @param wsRoot the workspace root
     * @param projectState the project to export
     * @param appPackage the application package
     * @param versionCode the major version code.
     * @param apk the {@link ApkData} describing how the export should happen.
     * @param softVariant an optional soft variant info. The entry contains (name, resource filter).
     * @param binFolder the binFolder where the file must be created.
     * @throws CoreException
     */
    private void buildVariant(IWorkspaceRoot wsRoot, ProjectState projectState, String appPackage,
            int versionCode, ApkData apk, Entry<String, String> softVariant, IFolder binFolder)
            throws CoreException {
        // get the libraries for this project
        IProject[] libProjects = projectState.getFullLibraryProjects();

        IProject project = projectState.getProject();
        IJavaProject javaProject = JavaCore.create(project);

        int compositeVersionCode = apk.getCompositeVersionCode(versionCode);

        // figure out the file names
        String pkgName = project.getName() + "-" + apk.getBuildInfo();
        String finalNameRoot = appPackage + "-" + compositeVersionCode;
        if (softVariant != null) {
            String tmp = "-" + softVariant.getKey();
            pkgName += tmp;
            finalNameRoot += tmp;
        }

        pkgName += ".ap_";
        String outputName = finalNameRoot + "-unsigned.apk";

        PostCompilerHelper helper = new PostCompilerHelper(project, System.out, System.err);

        // get the manifest file
        IFile manifestFile = project.getFile(SdkConstants.FN_ANDROID_MANIFEST_XML);
        // get the project bin folder
        IFolder projectBinFolder = wsRoot.getFolder(javaProject.getOutputLocation());
        String projectBinFolderPath = projectBinFolder.getLocation().toOSString();

        // package the resources
        if (helper.packageResources(manifestFile, libProjects,
                softVariant != null ? softVariant.getValue() : null, compositeVersionCode,
                projectBinFolderPath, pkgName) == false) {
            return;
        }

        apk.setOutputName(softVariant != null ? softVariant.getKey() : null, outputName);

        // do the final export.
        IFile dexFile = projectBinFolder.getFile(SdkConstants.FN_APK_CLASSES_DEX);
        String outputFile = binFolder.getFile(outputName).getLocation().toOSString();

        // get the list of referenced projects.
        IProject[] javaRefs = ProjectHelper.getReferencedProjects(project);
        IJavaProject[] referencedJavaProjects = PostCompilerHelper.getJavaProjects(javaRefs);

        helper.finalPackage(
                new File(projectBinFolderPath, pkgName).getAbsolutePath(),
                dexFile.getLocation().toOSString(),
                outputFile,
                false /*debugSign */,
                javaProject,
                libProjects,
                referencedJavaProjects,
                apk.getAbi(),
                false /*debuggable*/);

    }
}
