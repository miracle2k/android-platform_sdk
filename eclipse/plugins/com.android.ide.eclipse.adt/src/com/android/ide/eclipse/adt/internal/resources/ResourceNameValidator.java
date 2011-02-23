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

package com.android.ide.eclipse.adt.internal.resources;

import static com.android.ide.eclipse.adt.AndroidConstants.DOT_XML;

import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.AndroidConstants;
import com.android.ide.eclipse.adt.internal.resources.manager.FolderTypeRelationship;
import com.android.ide.eclipse.adt.internal.resources.manager.ProjectResourceItem;
import com.android.ide.eclipse.adt.internal.resources.manager.ProjectResources;
import com.android.ide.eclipse.adt.internal.resources.manager.ResourceFolderType;
import com.android.ide.eclipse.adt.internal.resources.manager.ResourceManager;
import com.android.resources.ResourceType;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.jdt.core.JavaConventions;
import org.eclipse.jface.dialogs.IInputValidator;

import java.util.HashSet;
import java.util.Set;

/**
 * Validator which ensures that new Android resource names are valid.
 */
public class ResourceNameValidator implements IInputValidator {
    /** Set of existing names to check for conflicts with */
    private Set<String> mExisting;

    /**
     * True if the resource name being considered is a "file" based resource (where the
     * resource name is the actual file name, rather than just a value attribute inside an
     * XML file name of arbitrary name
     */
    private boolean mIsFileType;

    /** If true, allow .xml as a name suffix */
    private boolean mAllowXmlExtension;

    private ResourceNameValidator(boolean allowXmlExtension, Set<String> existing,
            boolean isFileType) {
        mAllowXmlExtension = allowXmlExtension;
        mExisting = existing;
        mIsFileType = isFileType;
    }

    public String isValid(String newText) {
        // IValidator has the same interface as SWT's IInputValidator
        try {
            if (newText == null || newText.trim().length() == 0) {
                return "Enter a new name";
            }

            if (mAllowXmlExtension && newText.endsWith(DOT_XML)) {
                newText = newText.substring(0, newText.length() - DOT_XML.length());
            }

            if (newText.indexOf('.') != -1 && !newText.endsWith(AndroidConstants.DOT_XML)) {
                return String.format("The filename must end with %1$s.", DOT_XML);
            }

            // Resource names must be valid Java identifiers, since they will
            // be represented as Java identifiers in the R file:
            if (!Character.isJavaIdentifierStart(newText.charAt(0))) {
                return "The layout name must begin with a character";
            }
            for (int i = 1, n = newText.length(); i < n; i++) {
                char c = newText.charAt(i);
                if (!Character.isJavaIdentifierPart(c)) {
                    return String.format("'%1$c' is not a valid resource name character", c);
                }
            }

            if (mIsFileType) {
                // AAPT only allows lowercase+digits+_:
                // "%s: Invalid file name: must contain only [a-z0-9_.]","
                for (int i = 0, n = newText.length(); i < n; i++) {
                    char c = newText.charAt(i);
                    if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_')) {
                        return String.format(
                          "File-based resource names must contain only lowercase a-z, 0-9, or _.");
                    }
                }
            }

            String level = "1.5"; //$NON-NLS-1$
            IStatus validIdentifier = JavaConventions.validateIdentifier(newText, level, level);
            if (!validIdentifier.isOK()) {
                return String.format("%1$s is not a valid name (reserved Java keyword)", newText);
            }

            if (mExisting != null && mExisting.contains(newText)) {
                return String.format("%1$s already exists", newText);
            }

            return null;
        } catch (Exception e) {
            AdtPlugin.log(e, "Validation failed: %s", e.toString());
            return ""; //$NON-NLS-1$
        }
    }

    /**
     * Creates a new {@link ResourceNameValidator}
     *
     * @param allowXmlExtension if true, allow .xml to be entered as a suffix for the
     *            resource name
     * @param type the resource type of the resource name being validated
     * @return a new {@link ResourceNameValidator}
     */
    public static ResourceNameValidator create(boolean allowXmlExtension,
            ResourceFolderType type) {
        boolean isFileType = type != ResourceFolderType.VALUES;
        return new ResourceNameValidator(allowXmlExtension, null, isFileType);
    }

    /**
     * Creates a new {@link ResourceNameValidator}
     *
     * @param allowXmlExtension if true, allow .xml to be entered as a suffix for the
     *            resource name
     * @param existing An optional set of names that already exist (and therefore will not
     *            be considered valid if entered as the new name)
     * @param type the resource type of the resource name being validated
     * @return a new {@link ResourceNameValidator}
     */
    public static ResourceNameValidator create(boolean allowXmlExtension, Set<String> existing,
            ResourceType type) {
        boolean isFileType = isFileBasedResourceType(type);
        return new ResourceNameValidator(allowXmlExtension, existing, isFileType);
    }

    /**
     * Creates a new {@link ResourceNameValidator}
     *
     * @param allowXmlExtension if true, allow .xml to be entered as a suffix for the
     *            resource name
     * @param project the project to validate new resource names for
     * @param type the resource type of the resource name being validated
     * @return a new {@link ResourceNameValidator}
     */
    public static ResourceNameValidator create(boolean allowXmlExtension, IProject project,
            ResourceType type) {
        Set<String> existing = new HashSet<String>();
        ResourceManager manager = ResourceManager.getInstance();
        ProjectResources projectResources = manager.getProjectResources(project);
        ProjectResourceItem[] resources = projectResources.getResources(type);
        for (ProjectResourceItem resource : resources) {
            existing.add(resource.getName());
        }

        boolean isFileType = isFileBasedResourceType(type);
        return new ResourceNameValidator(allowXmlExtension, existing, isFileType);
    }

    /**
     * Is this a resource that is defined in a file named by the resource plus the XML
     * extension?
     * <p>
     * Some resource types can be defined <b>both</b> as a separate XML file as well as
     * defined within a value XML file along with other properties. This method will
     * return true for these resource types as well. In other words, a ResourceType can
     * return true for both {@link #isValueBasedResourceType} and
     * {@link #isFileBasedResourceType}.
     *
     * @param type the resource type to check
     * @return true if the given resource type is stored in a file named by the resource
     */
    public static boolean isFileBasedResourceType(ResourceType type) {
        ResourceFolderType[] folderTypes = FolderTypeRelationship.getRelatedFolders(type);
        for (ResourceFolderType folderType : folderTypes) {
            if (folderType != ResourceFolderType.VALUES) {
                return true;
            }
        }

        return false;
    }

    /**
     * Is this a resource that can be defined in any file within the "values" folder?
     * <p>
     * Some resource types can be defined <b>both</b> as a separate XML file as well
     * as defined within a value XML file. This method will return true for these types
     * as well. In other words, a ResourceType can return true for both
     * {@link #isValueBasedResourceType} and {@link #isFileBasedResourceType}.
     *
     * @param type the resource type to check
     * @return true if the given resource type can be represented as a value under the
     *         values/ folder
     */
    public static boolean isValueBasedResourceType(ResourceType type) {
        ResourceFolderType[] folderTypes = FolderTypeRelationship.getRelatedFolders(type);
        for (ResourceFolderType folderType : folderTypes) {
            if (folderType == ResourceFolderType.VALUES) {
                return true;
            }
        }

        return false;
    }
}
