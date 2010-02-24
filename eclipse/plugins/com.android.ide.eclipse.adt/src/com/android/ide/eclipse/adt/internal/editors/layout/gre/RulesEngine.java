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
import com.android.ide.eclipse.adt.editors.layout.gscripts.DropZone;
import com.android.ide.eclipse.adt.editors.layout.gscripts.INodeProxy;
import com.android.ide.eclipse.adt.editors.layout.gscripts.IViewRule;
import com.android.ide.eclipse.adt.editors.layout.gscripts.Point;
import com.android.ide.eclipse.adt.internal.editors.descriptors.ElementDescriptor;
import com.android.ide.eclipse.adt.internal.editors.layout.descriptors.ViewElementDescriptor;
import com.android.ide.eclipse.adt.internal.editors.layout.uimodel.UiViewElementNode;
import com.android.ide.eclipse.adt.internal.resources.manager.GlobalProjectMonitor;
import com.android.ide.eclipse.adt.internal.resources.manager.GlobalProjectMonitor.IFolderListener;

import org.codehaus.groovy.control.CompilationFailedException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;

import groovy.lang.GroovyClassLoader;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * The rule engine manages the groovy rules files and interacts with them.
 * There's one {@link RulesEngine} instance per layout editor.
 * Each instance has 2 sets of scripts: the static ADT rules (shared across all instances)
 * and the project specific rules (local to the current instance / layout editor).
 */
public class RulesEngine {

    private static final String FD_GSCRIPTS = "gscripts";
    private static final String SCRIPT_EXT = ".groovy";  //$NON-NLS-1$

    private final GroovyClassLoader mClassLoader;
    private final IProject mProject;
    private final Map<Object, IViewRule> mRulesCache = new HashMap<Object, IViewRule>();
    private ProjectFolderListener mProjectFolderListener;

    public RulesEngine(IProject project) {
        mProject = project;
        ClassLoader cl = getClass().getClassLoader();
        mClassLoader = new GroovyClassLoader(cl);

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
     * Invokes {@link IViewRule#getDisplayName()} on the rule matching the specified element.
     *
     * @param element The view element to target. Can be null.
     * @return Null if the rule failed, there's no rule or the rule does not want to override
     *   the display name. Otherwise, a string as returned by the groovy script.
     */
    public String getDisplayName(UiViewElementNode element) {
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
     * Invokes {@link IViewRule#dropStart(INodeProxy)} on the rule matching
     * the specified target node.
     *
     * @param targetNode The XML view that is currently the target of the drop.
     * @return Null if the rule failed, there's no rule or the rule does not accept the drop.
     *   Otherwise a list of drop zones valid for this drop.
     */
    public ArrayList<DropZone> dropStart(NodeProxy targetNode) {
        // try to find a rule for this element's FQCN
        IViewRule rule = loadRule(targetNode.getNode());

        if (rule != null) {
            try {
                return rule.dropStart(targetNode);

            } catch (Exception e) {
                logError("%s.dropStart() failed: %s",
                        rule.getClass().getSimpleName(),
                        e.toString());
            }
        }

        return null;
    }

    /**
     * Invokes {@link IViewRule#dropFinish(String, INodeProxy, DropZone, Point)} on the
     * rule matching the specified target node.
     *
     * @param source The {@link ViewElementDescriptor} of the drag source.
     * @param targetNode The XML view that is currently the target of the drop.
     * @param selectedZone One of the drop zones returned by {@link #dropStart(NodeProxy)}.
     */
    public void dropFinish(String viewFqcn,
            NodeProxy targetNode,
            DropZone selectedZone,
            Point where) {
        // try to find a rule for this element's FQCN
        IViewRule rule = loadRule(targetNode.getNode());

        if (rule != null) {
            try {
                rule.dropFinish(viewFqcn, targetNode, selectedZone, where);

            } catch (Exception e) {
                logError("%s.dropFinish() failed: %s",
                        rule.getClass().getSimpleName(),
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
            rule = loadStream(is, realFqcn);
            if (rule != null) {
                return initializeRule(rule, targetFqcn);
            }
        } catch (Exception e) {
            logError("load rule error (%s): %s", filename, e.getMessage());
        }

        // Then look for the file in the project
        IResource r = mProject.findMember(FD_GSCRIPTS);
        if (r != null && r.getType() == IResource.FOLDER) {
            r = ((IFolder) r).findMember(filename);
            if (r != null && r.getType() == IResource.FILE) {
                try {
                    InputStream is = ((IFile) r).getContents();
                    rule = loadStream(is, realFqcn);
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
     * @return A new {@link IViewRule} or null if loading failed for any reason.
     */
    private IViewRule loadStream(InputStream is, String fqcn) {
        try {
            if (is == null) {
                // We handle this case for convenience. It typically means that the
                // input stream couldn't be opened because the file was not found.
                // Since we expect this to be a common case, we don't log it as an error.
                return null;
            }

            // Create a groovy class from it. Can fail to compile.
            Class<?> c = mClassLoader.parseClass(is, fqcn);

            // Get an instance. This might throw ClassCastException.
            return (IViewRule) c.newInstance();

        } catch (CompilationFailedException e) {
            logError("Compilation error in %s.groovy: %s", fqcn, e.toString());
        } catch (ClassCastException e) {
            logError("Script %s.groovy does not implement IViewRule", fqcn);
        } catch (Exception e) {
            logError("Failed to use %s.groovy: %s", fqcn, e.getMessage());
        }

        return null;
    }

    private void logError(String format, Object...params) {
        String s = String.format(format, params);
        AdtPlugin.printErrorToConsole(mProject, s);
    }
}
