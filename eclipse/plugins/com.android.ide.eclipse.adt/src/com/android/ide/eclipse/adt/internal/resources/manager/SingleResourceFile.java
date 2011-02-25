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

package com.android.ide.eclipse.adt.internal.resources.manager;

import com.android.ide.common.rendering.api.DensityBasedResourceValue;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.eclipse.adt.internal.resources.configurations.PixelDensityQualifier;
import com.android.io.IAbstractFile;
import com.android.resources.ResourceType;

import java.util.ArrayList;
import java.util.Collection;

import javax.xml.parsers.SAXParserFactory;

/**
 * Represents a resource file describing a single resource.
 * <p/>
 * This is typically an XML file inside res/anim, res/layout, or res/menu or an image file
 * under res/drawable.
 */
public class SingleResourceFile extends ResourceFile {

    private final static SAXParserFactory sParserFactory = SAXParserFactory.newInstance();
    static {
        sParserFactory.setNamespaceAware(true);
    }

    private String mResourceName;
    private ResourceType mType;
    private ResourceValue mValue;

    public SingleResourceFile(IAbstractFile file, ResourceFolder folder) {
        super(file, folder);

        // we need to infer the type of the resource from the folder type.
        // This is easy since this is a single Resource file.
        ResourceType[] types = FolderTypeRelationship.getRelatedResourceTypes(folder.getType());
        mType = types[0];

        // compute the resource name
        mResourceName = getResourceName(mType);

        // test if there's a density qualifier associated with the resource
        PixelDensityQualifier qualifier = folder.getConfiguration().getPixelDensityQualifier();

        if (qualifier == null) {
            mValue = new ResourceValue(mType, getResourceName(mType),
                    file.getOsLocation(), isFramework());
        } else {
            mValue = new DensityBasedResourceValue(
                    mType,
                    getResourceName(mType),
                    file.getOsLocation(),
                    qualifier.getValue(),
                    isFramework());
        }
    }

    @Override
    public ResourceType[] getResourceTypes() {
        return FolderTypeRelationship.getRelatedResourceTypes(getFolder().getType());
    }

    @Override
    public boolean hasResources(ResourceType type) {
        return FolderTypeRelationship.match(type, getFolder().getType());
    }

    @Override
    public Collection<ProjectResourceItem> getResources(ResourceType type,
            ProjectResources projectResources) {

        // looking for an existing ResourceItem with this name and type
        ProjectResourceItem item = projectResources.findResourceItem(type, mResourceName);

        ArrayList<ProjectResourceItem> items = new ArrayList<ProjectResourceItem>();

        if (item == null) {
            item = new ConfigurableResourceItem(mResourceName);
            items.add(item);
        }

        // add this ResourceFile to the ResourceItem
        item.add(this);

        return items;
    }

    /*
     * (non-Javadoc)
     * @see com.android.ide.eclipse.editors.resources.manager.ResourceFile#getValue(com.android.ide.eclipse.common.resources.ResourceType, java.lang.String)
     *
     * This particular implementation does not care about the type or name since a
     * SingleResourceFile represents a file generating only one resource.
     * The value returned is the full absolute path of the file in OS form.
     */
    @Override
    public ResourceValue getValue(ResourceType type, String name) {
        return mValue;
    }

    /**
     * Returns the name of the resources.
     */
    private String getResourceName(ResourceType type) {
        // get the name from the filename.
        String name = getFile().getName();

        int pos = name.indexOf('.');
        if (pos != -1) {
            name = name.substring(0, pos);
        }

        return name;
    }
}
