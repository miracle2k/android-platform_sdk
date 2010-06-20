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

package com.android.ide.eclipse.adt.internal.editors.layout.gre;

import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.AndroidConstants;
import com.android.ide.eclipse.adt.editors.layout.gscripts.DropFeedback;
import com.android.ide.eclipse.adt.editors.layout.gscripts.IDragElement;
import com.android.ide.eclipse.adt.editors.layout.gscripts.IGraphics;
import com.android.ide.eclipse.adt.editors.layout.gscripts.INode;
import com.android.ide.eclipse.adt.editors.layout.gscripts.IViewRule;
import com.android.ide.eclipse.adt.editors.layout.gscripts.Point;
import com.android.ide.eclipse.adt.internal.editors.descriptors.ElementDescriptor;
import com.android.ide.eclipse.adt.internal.editors.layout.descriptors.ViewElementDescriptor;
import com.android.ide.eclipse.adt.internal.editors.layout.uimodel.UiViewElementNode;
import com.android.ide.eclipse.adt.internal.resources.manager.GlobalProjectMonitor;
import com.android.ide.eclipse.adt.internal.resources.manager.GlobalProjectMonitor.IFolderListener;
import com.android.sdklib.SdkConstants;

import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.Phases;
import org.codehaus.groovy.control.SourceUnit;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;

import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyCodeSource;
import groovy.lang.GroovyResourceLoader;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.security.CodeSource;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/* TODO:
 * - create a logger object and pass it around.
 *
 */

/**
 * The rule engine manages the groovy rules files and interacts with them.
 * There's one {@link RulesEngine} instance per layout editor.
 * Each instance has 2 sets of scripts: the static ADT rules (shared across all instances)
 * and the project specific rules (local to the current instance / layout editor).
 */
public class RulesEngine {

    /**
     * The project folder where the scripts are located.
     * This is for both our unique ADT project folder and the user projects folders.
     */
    private static final String FD_GSCRIPTS = "gscripts";                       //$NON-NLS-1$
    /**
     * The extension we expect for the groovy scripts.
     */
    private static final String SCRIPT_EXT = ".groovy";                         //$NON-NLS-1$
    /**
     * The package we expect for our groovy scripts.
     * User scripts do not need to use the same (and in fact should probably not.)
     */
    private static final String SCRIPT_PACKAGE = "com.android.adt.gscripts";    //$NON-NLS-1$

    private final GroovyClassLoader mClassLoader;
    private final IProject mProject;
    private final Map<Object, IViewRule> mRulesCache = new HashMap<Object, IViewRule>();
    private ProjectFolderListener mProjectFolderListener;


    public RulesEngine(IProject project) {
        mProject = project;
        ClassLoader cl = getClass().getClassLoader();

        // Note: we could use the CompilerConfiguration to add an output log collector
        CompilerConfiguration cc = new CompilerConfiguration();
        cc.setDefaultScriptExtension(SCRIPT_EXT);

        mClassLoader = new GreGroovyClassLoader(cl, cc);

        // Add the project's gscript folder to the classpath, if it exists.
        IResource f = project.findMember(FD_GSCRIPTS);
        if ((f instanceof IFolder) && f.exists()) {
            URI uri = ((IFolder) f).getLocationURI();
            try {
                URL url = uri.toURL();
                mClassLoader.addURL(url);
            } catch (MalformedURLException e) {
                // ignore; it's not a valid URL, we obviously won't use it
                // in the class path.
            }
        }

        mProjectFolderListener = new ProjectFolderListener();
        GlobalProjectMonitor.getMonitor().addFolderListener(
                mProjectFolderListener,
                IResourceDelta.ADDED | IResourceDelta.REMOVED | IResourceDelta.CHANGED);
    }

    /**
     * Called by the owner of the {@link RulesEngine} when it is going to be disposed.
     * This frees some resources, such as the project's folder monitor.
     */
    public void dispose() {
        if (mProjectFolderListener != null) {
            GlobalProjectMonitor.getMonitor().removeFolderListener(mProjectFolderListener);
            mProjectFolderListener = null;
        }
        clearCache();
    }

    /**
     * Eventually all rules are going to try to load the base android.view.View rule.
     * Clients can request to preload it to make the first call faster.
     */
    public void preloadAndroidView() {
        loadRule(SdkConstants.CLASS_VIEW, SdkConstants.CLASS_VIEW);
    }

    /**
     * Invokes {@link IViewRule#getDisplayName()} on the rule matching the specified element.
     *
     * @param element The view element to target. Can be null.
     * @return Null if the rule failed, there's no rule or the rule does not want to override
     *   the display name. Otherwise, a string as returned by the groovy script.
     */
    public String callGetDisplayName(UiViewElementNode element) {
        // try to find a rule for this element's FQCN
        IViewRule rule = loadRule(element);

        if (rule != null) {
            try {
                return rule.getDisplayName();

            } catch (Exception e) {
                logError("%s.getDisplayName() failed: %s",
                        rule.getClass().getSimpleName(),
                        e.toString());
            }
        }

        return null;
    }

    /**
     * Invokes {@link IViewRule#onSelected(IGraphics, INode, String, boolean)}
     * on the rule matching the specified element.
     *
     * @param gc An {@link IGraphics} instance, to perform drawing operations.
     * @param selectedNode The node selected. Never null.
     * @param displayName The name to display, as returned by {@link IViewRule#getDisplayName()}.
     * @param isMultipleSelection A boolean set to true if more than one element is selected.
     */
    public void callOnSelected(IGraphics gc, NodeProxy selectedNode,
            String displayName, boolean isMultipleSelection) {
        // try to find a rule for this element's FQCN
        IViewRule rule = loadRule(selectedNode.getNode());

        if (rule != null) {
            try {
                rule.onSelected(gc, selectedNode, displayName, isMultipleSelection);

            } catch (Exception e) {
                logError("%s.onSelected() failed: %s",
                        rule.getClass().getSimpleName(),
                        e.toString());
            }
        }
    }

    /**
     * Invokes {@link IViewRule#onChildSelected(IGraphics, INode, INode)}
     * on the rule matching the specified element.
     *
     * @param gc An {@link IGraphics} instance, to perform drawing operations.
     * @param parentNode The parent of the node selected. Never null.
     * @param childNode The child node that was selected. Never null.
     */
    public void callOnChildSelected(IGraphics gc, NodeProxy parentNode, NodeProxy childNode) {
        // try to find a rule for this element's FQCN
        IViewRule rule = loadRule(parentNode.getNode());

        if (rule != null) {
            try {
                rule.onChildSelected(gc, parentNode, childNode);

            } catch (Exception e) {
                logError("%s.onChildSelected() failed: %s",
                        rule.getClass().getSimpleName(),
                        e.toString());
            }
        }
    }


    /**
     * Called when the d'n'd starts dragging over the target node.
     * If interested, returns a DropFeedback passed to onDrop/Move/Leave/Paint.
     * If not interested in drop, return false.
     * Followed by a paint.
     */
    public DropFeedback callOnDropEnter(NodeProxy targetNode,
            IDragElement[] elements) {
        // try to find a rule for this element's FQCN
        IViewRule rule = loadRule(targetNode.getNode());

        if (rule != null) {
            try {
                return rule.onDropEnter(targetNode, elements);

            } catch (Exception e) {
                logError("%s.onDropEnter() failed: %s",
                        rule.getClass().getSimpleName(),
                        e.toString());
            }
        }

        return null;
    }

    /**
     * Called after onDropEnter.
     * Returns a DropFeedback passed to onDrop/Move/Leave/Paint (typically same
     * as input one).
     */
    public DropFeedback callOnDropMove(NodeProxy targetNode,
            IDragElement[] elements,
            DropFeedback feedback,
            Point where) {
        // try to find a rule for this element's FQCN
        IViewRule rule = loadRule(targetNode.getNode());

        if (rule != null) {
            try {
                return rule.onDropMove(targetNode, elements, feedback, where);

            } catch (Exception e) {
                logError("%s.onDropMove() failed: %s",
                        rule.getClass().getSimpleName(),
                        e.toString());
            }
        }

        return null;
    }

    /**
     * Called when drop leaves the target without actually dropping
     */
    public void callOnDropLeave(NodeProxy targetNode,
            IDragElement[] elements,
            DropFeedback feedback) {
        // try to find a rule for this element's FQCN
        IViewRule rule = loadRule(targetNode.getNode());

        if (rule != null) {
            try {
                rule.onDropLeave(targetNode, elements, feedback);

            } catch (Exception e) {
                logError("%s.onDropLeave() failed: %s",
                        rule.getClass().getSimpleName(),
                        e.toString());
            }
        }
    }

    /**
     * Called when drop is released over the target to perform the actual drop.
     */
    public void callOnDropped(NodeProxy targetNode,
            IDragElement[] elements,
            DropFeedback feedback,
            Point where) {
        // try to find a rule for this element's FQCN
        IViewRule rule = loadRule(targetNode.getNode());

        if (rule != null) {
            try {
                rule.onDropped(targetNode, elements, feedback, where);

            } catch (Exception e) {
                logError("%s.onDropped() failed: %s",
                        rule.getClass().getSimpleName(),
                        e.toString());
            }
        }
    }

    /**
     * Called when a paint has been requested via DropFeedback.
     * @param targetNode
     */
    public void callDropFeedbackPaint(IGraphics gc,
            NodeProxy targetNode,
            DropFeedback feedback) {
        if (gc != null && feedback != null && feedback.paintClosure != null) {
            try {
                feedback.paintClosure.call(new Object[] { gc, targetNode, feedback });
            } catch (Exception e) {
                logError("DropFeedback.paintClosure failed: %s",
                        e.toString());
            }
        }
    }

    // ---- private ---

    private class ProjectFolderListener implements IFolderListener {
        public void folderChanged(IFolder folder, int kind) {
            if (folder.getProject() == mProject &&
                    FD_GSCRIPTS.equals(folder.getName())) {
                // Clear our whole rules cache, to not have to deal with dependencies.
                clearCache();
            }
        }
    }

    /**
     * Clear the Rules cache. Calls onDispose() on each rule.
     */
    private void clearCache() {
        // The cache can contain multiple times the same rule instance for different
        // keys (e.g. the UiViewElementNode key vs. the FQCN string key.) So transfer
        // all values to a unique set.
        HashSet<IViewRule> rules = new HashSet<IViewRule>(mRulesCache.values());

        mRulesCache.clear();

        for (IViewRule rule : rules) {
            if (rule != null) {
                try {
                    rule.onDispose();
                } catch (Exception e) {
                    logError("%s.onDispose() failed: %s",
                            rule.getClass().getSimpleName(),
                            e.toString());
                }
            }
        }
    }

    /**
     * Load a rule using its descriptor. This will try to first load the rule using its
     * actual FQCN and if that fails will find the first parent that works in the view
     * hierarchy.
     */
    private IViewRule loadRule(UiViewElementNode element) {
        if (element == null) {
            return null;
        } else {
            // sanity check. this can't fail.
            ElementDescriptor d = element.getDescriptor();
            if (d == null || !(d instanceof ViewElementDescriptor)) {
                return null;
            }
        }

        String targetFqcn = null;
        ViewElementDescriptor targetDesc = (ViewElementDescriptor) element.getDescriptor();

        // Return the rule if we find it in the cache, even if it was stored as null
        // (which means we didn't find it earlier, so don't look for it again)
        IViewRule rule = mRulesCache.get(targetDesc);
        if (rule != null || mRulesCache.containsKey(targetDesc)) {
            return rule;
        }

        // Get the descriptor and loop through the super class hierarchy
        for (ViewElementDescriptor desc = targetDesc;
                desc != null;
                desc = desc.getSuperClassDesc()) {

            // Get the FQCN of this View
            String fqcn = desc.getFullClassName();
            if (fqcn == null) {
                return null;
            }

            // The first time we keep the FQCN around as it's the target class we were
            // initially trying to load. After, as we move through the hierarchy, the
            // target FQCN remains constant.
            if (targetFqcn == null) {
                targetFqcn = fqcn;
            }

            // Try to find a rule matching the "real" FQCN. If we find it, we're done.
            // If not, the for loop will move to the parent descriptor.
            rule = loadRule(fqcn, targetFqcn);
            if (rule != null) {
                // We found one.
                // As a side effect, loadRule() also cached the rule using the target FQCN.
                return rule;
            }
        }

        // Memorize in the cache that we couldn't find a rule for this descriptor
        mRulesCache.put(targetDesc, null);
        return null;
    }

    /**
     * Try to load a rule given a specific FQCN. This looks for an exact match in either
     * the ADT scripts or the project scripts and does not look at parent hierarchy.
     * <p/>
     * Once a rule is found (or not), it is stored in a cache using its target FQCN
     * so we don't try to reload it.
     * <p/>
     * The real FQCN is the actual groovy filename we're loading, e.g. "android.view.View.groovy"
     * where target FQCN is the class we were initially looking for, which might be the same as
     * the real FQCN or might be a derived class, e.g. "android.widget.TextView".
     *
     * @param realFqcn The FQCN of the groovy rule actually being loaded.
     * @param targetFqcn The FQCN of the class actually processed, which might be different from
     *          the FQCN of the rule being loaded.
     */
    private IViewRule loadRule(String realFqcn, String targetFqcn) {
        if (realFqcn == null || targetFqcn == null) {
            return null;
        }

        // Return the rule if we find it in the cache, even if it was stored as null
        // (which means we didn't find it earlier, so don't look for it again)
        IViewRule rule = mRulesCache.get(realFqcn);
        if (rule != null || mRulesCache.containsKey(realFqcn)) {
            return rule;
        }

        // Look for the file in ADT first.
        // That means a project can't redefine any of the rules we define.
        String filename = realFqcn + SCRIPT_EXT;

        try {
            InputStream is = AdtPlugin.readEmbeddedFileAsStream(
                    FD_GSCRIPTS + AndroidConstants.WS_SEP + filename);
            rule = loadStream(is, realFqcn, "ADT");     //$NON-NLS-1$
            if (rule != null) {
                return initializeRule(rule, targetFqcn);
            }
        } catch (Exception e) {
            logError("load rule error (%s): %s", filename, e.toString());
        }


        // Then look for the file in the project
        IResource r = mProject.findMember(FD_GSCRIPTS);
        if (r != null && r.getType() == IResource.FOLDER) {
            r = ((IFolder) r).findMember(filename);
            if (r != null && r.getType() == IResource.FILE) {
                try {
                    InputStream is = ((IFile) r).getContents();
                    rule = loadStream(is, realFqcn, mProject.getName());
                    if (rule != null) {
                        return initializeRule(rule, targetFqcn);
                    }
                } catch (Exception e) {
                    logError("load rule error (%s): %s", filename, e.getMessage());
                }
            }
        }

        // Memorize in the cache that we couldn't find a rule for this real FQCN
        mRulesCache.put(realFqcn, null);
        return null;
    }

    /**
     * Initialize a rule we just loaded. The rule has a chance to examine the target FQCN
     * and bail out.
     * <p/>
     * Contract: the rule is not in the {@link #mRulesCache} yet and this method will
     * cache it using the target FQCN if the rule is accepted.
     * <p/>
     * The real FQCN is the actual groovy filename we're loading, e.g. "android.view.View.groovy"
     * where target FQCN is the class we were initially looking for, which might be the same as
     * the real FQCN or might be a derived class, e.g. "android.widget.TextView".
     *
     * @param rule A rule freshly loaded.
     * @param targetFqcn The FQCN of the class actually processed, which might be different from
     *          the FQCN of the rule being loaded.
     * @return The rule if accepted, or null if the rule can't handle that FQCN.
     */
    private IViewRule initializeRule(IViewRule rule, String targetFqcn) {

        try {
            if (rule.onInitialize(targetFqcn)) {
                // Add it to the cache and return it
                mRulesCache.put(targetFqcn, rule);
                return rule;
            } else {
                rule.onDispose();
            }
        } catch (Exception e) {
            logError("%s.onInit() failed: %s",
                    rule.getClass().getSimpleName(),
                    e.toString());
        }

        return null;
    }

    /**
     * Actually load a groovy script and instantiate an {@link IViewRule} from it.
     * On error, outputs (hopefully meaningful) groovy error messages.
     *
     * @param is The input stream for the groovy script. Can be null.
     * @param fqcn The class name, for display purposes only.
     * @param codeBase A string eventually passed to {@link CodeSource} to define some kind
     *                 of security permission. Quite irrelevant in our case since it all
     *                 comes from an input stream. However this method uses it to print
     *                 the origin of the source in the exception errors.
     * @return A new {@link IViewRule} or null if loading failed for any reason.
     */
    private IViewRule loadStream(InputStream is, String fqcn, String codeBase) {
        try {
            if (is == null) {
                // We handle this case for convenience. It typically means that the
                // input stream couldn't be opened because the file was not found.
                // Since we expect this to be a common case, we don't log it as an error.
                return null;
            }

            // We don't really now the character encoding, we're going to assume UTF-8.
            InputStreamReader reader = new InputStreamReader(is, Charset.forName("UTF-8"));
            GroovyCodeSource source = new GroovyCodeSource(reader, fqcn, codeBase);

            // Create a groovy class from it. Can fail to compile.
            Class<?> c = mClassLoader.parseClass(source);

            // Get an instance. This might throw ClassCastException.
            return (IViewRule) c.newInstance();

        } catch (CompilationFailedException e) {
            logError("Compilation error in %1$s:%2$s.groovy: %3$s", codeBase, fqcn, e.toString());
        } catch (ClassCastException e) {
            logError("Script %1$s:%2$s.groovy does not implement IViewRule", codeBase, fqcn);
        } catch (Exception e) {
            logError("Failed to use %1$s:%2$s.groovy: %3$s", codeBase, fqcn, e.toString());
        }

        return null;
    }

    private void logError(String format, Object...params) {
        String s = String.format(format, params);
        AdtPlugin.printErrorToConsole(mProject, s);
    }

    // -----

    /**
     * A custom {@link GroovyClassLoader} that lets us override the {@link CompilationUnit}
     * and the {@link GroovyResourceLoader}.
     */
    private static class GreGroovyClassLoader extends GroovyClassLoader {

        public GreGroovyClassLoader(ClassLoader cl, CompilerConfiguration cc) {
            super(cl, cc);

            // Override the resource loader: when a class is not found, we try to find a class
            // defined in our internal ADT groovy script, assuming it has our special package.
            // Note that these classes do not have to implement IViewRule. That means we can
            // create utility classes in groovy used by the other groovy rules.
            final GroovyResourceLoader resLoader = getResourceLoader();
            setResourceLoader(new GroovyResourceLoader() {
                public URL loadGroovySource(String filename) throws MalformedURLException {
                    URL url = resLoader.loadGroovySource(filename);
                    if (url == null) {
                        // We only try to load classes in our own groovy script package
                        String p = SCRIPT_PACKAGE + ".";      //$NON-NLS-1$

                        if (filename.startsWith(p)) {
                            filename = filename.substring(p.length());

                            // This will return null if the file doesn't exists.
                            // The groovy resolver will actually load and verify the class
                            // implemented matches the one it was expecting in the first place,
                            // so we don't have anything to do here besides returning the URL to
                            // the source file.
                            url = AdtPlugin.getEmbeddedFileUrl(
                                    AndroidConstants.WS_SEP +
                                    FD_GSCRIPTS +
                                    AndroidConstants.WS_SEP +
                                    filename +
                                    SCRIPT_EXT);
                        }
                    }
                    return url;
                }
            });
        }

        @Override
        protected CompilationUnit createCompilationUnit(
                CompilerConfiguration config,
                CodeSource source) {
            return new GreCompilationUnit(config, source, this);
        }
    }

    /**
     * A custom {@link CompilationUnit} that lets us add default import for our base classes
     * using the base package of {@link IViewRule} (e.g. "import com.android...gscripts.*")
     */
    private static class GreCompilationUnit extends CompilationUnit {

        public GreCompilationUnit(
                CompilerConfiguration config,
                CodeSource source,
                GroovyClassLoader loader) {
            super(config, source, loader);

            SourceUnitOperation op = new SourceUnitOperation() {
                @Override
                public void call(SourceUnit source) throws CompilationFailedException {
                    // add the equivalent of "import com.android...gscripts.*" to the source.
                    String p = IViewRule.class.getPackage().getName();
                    source.getAST().addStarImport(p + ".");  //$NON-NLS-1$
                }
            };

            addPhaseOperation(op, Phases.CONVERSION);
        }
    }

}
