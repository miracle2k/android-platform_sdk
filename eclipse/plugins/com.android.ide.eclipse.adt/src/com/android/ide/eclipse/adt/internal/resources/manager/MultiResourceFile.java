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

import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.ValueResourceParser;
import com.android.ide.common.resources.ValueResourceParser.IValueResourceRepository;
import com.android.io.IAbstractFile;
import com.android.io.StreamException;
import com.android.resources.ResourceType;

import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

/**
 * Represents a resource file able to declare multiple resources, which could be of
 * different {@link ResourceType}.
 * <p/>
 * This is typically an XML file inside res/values.
 */
public final class MultiResourceFile extends ResourceFile implements IValueResourceRepository {

    private final static SAXParserFactory sParserFactory = SAXParserFactory.newInstance();

    private final Map<ResourceType, HashMap<String, ResourceValue>> mResourceItems =
        new EnumMap<ResourceType, HashMap<String, ResourceValue>>(ResourceType.class);

    public MultiResourceFile(IAbstractFile file, ResourceFolder folder) {
        super(file, folder);
    }

    @Override
    public ResourceType[] getResourceTypes() {
        update();

        Set<ResourceType> keys = mResourceItems.keySet();

        return keys.toArray(new ResourceType[keys.size()]);
    }

    @Override
    public boolean hasResources(ResourceType type) {
        update();

        HashMap<String, ResourceValue> list = mResourceItems.get(type);
        return (list != null && list.size() > 0);
    }

    @Override
    public Collection<ProjectResourceItem> getResources(ResourceType type,
            ProjectResources projectResources) {
        update();

        HashMap<String, ResourceValue> list = mResourceItems.get(type);

        ArrayList<ProjectResourceItem> items = new ArrayList<ProjectResourceItem>();

        if (list != null) {
            Collection<ResourceValue> values = list.values();
            for (ResourceValue res : values) {
                ProjectResourceItem item = projectResources.findResourceItem(type, res.getName());

                if (item == null) {
                    if (type == ResourceType.ID) {
                        item = new IdResourceItem(res.getName(), false /* isDeclaredInline */);
                    } else {
                        item = new ConfigurableResourceItem(res.getName());
                    }
                    items.add(item);
                }

                item.add(this);
            }
        }

        return items;
    }

    /**
     * Updates the Resource items if necessary.
     */
    private void update() {
        if (isTouched() == true) {
            // reset current content.
            mResourceItems.clear();

            // need to parse the file and find the content.
            parseFile();

            resetTouch();
        }
    }

    /**
     * Parses the file and creates a list of {@link ResourceType}.
     */
    private void parseFile() {
        try {
            SAXParser parser = sParserFactory.newSAXParser();
            parser.parse(getFile().getContents(), new ValueResourceParser(this, isFramework()));
        } catch (ParserConfigurationException e) {
        } catch (SAXException e) {
        } catch (IOException e) {
        } catch (StreamException e) {
        }
    }

    /**
     * Adds a resource item to the list
     * @param resType The type of the resource
     * @param value The value of the resource.
     */
    public void addResourceValue(ResourceType resType, ResourceValue value) {
        HashMap<String, ResourceValue> list = mResourceItems.get(resType);

        // if the list does not exist, create it.
        if (list == null) {
            list = new HashMap<String, ResourceValue>();
            mResourceItems.put(resType, list);
        } else {
            // look for a possible value already existing.
            ResourceValue oldValue = list.get(value.getName());

            if (oldValue != null) {
                oldValue.replaceWith(value);
                return;
            }
        }

        // empty list or no match found? add the given resource
        list.put(value.getName(), value);
    }

    @Override
    public ResourceValue getValue(ResourceType type, String name) {
        update();

        // get the list for the given type
        HashMap<String, ResourceValue> list = mResourceItems.get(type);

        if (list != null) {
            return list.get(name);
        }

        return null;
    }
}
