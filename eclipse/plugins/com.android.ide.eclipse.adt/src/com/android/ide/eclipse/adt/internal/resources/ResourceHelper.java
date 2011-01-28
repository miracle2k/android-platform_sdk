/*
 * Copyright (C) 2011 The Android Open Source Project
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

import com.android.resources.ResourceType;


public class ResourceHelper {

    /**
     * Returns a formatted string usable in an XML to use the specified {@link ResourceItem}.
     * @param resourceItem The resource item.
     * @param system Whether this is a system resource or a project resource.
     * @return a string in the format @[type]/[name]
     */
    public static String getXmlString(ResourceType type, ResourceItem resourceItem,
            boolean system) {
        if (type == ResourceType.ID && resourceItem instanceof IIdResourceItem) {
            IIdResourceItem idResource = (IIdResourceItem)resourceItem;
            if (idResource.isDeclaredInline()) {
                return (system?"@android:":"@+") + type.getName() + "/" + resourceItem.getName(); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            }
        }

        return (system?"@android:":"@") + type.getName() + "/" + resourceItem.getName(); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

}
