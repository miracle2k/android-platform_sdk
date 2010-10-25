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

package com.android.ide.eclipse.adt.internal.editors.manifest.descriptors;

import com.android.ide.common.api.IAttributeInfo;
import com.android.ide.eclipse.adt.internal.editors.descriptors.DescriptorsUtils;
import com.android.ide.eclipse.adt.internal.editors.descriptors.ITextAttributeCreator;
import com.android.ide.eclipse.adt.internal.editors.descriptors.TextAttributeDescriptor;
import com.android.ide.eclipse.adt.internal.editors.manifest.model.UiClassAttributeNode;
import com.android.ide.eclipse.adt.internal.editors.uimodel.UiAttributeNode;
import com.android.ide.eclipse.adt.internal.editors.uimodel.UiElementNode;

/**
 * Describes an 'Application' class XML attribute. It is displayed by a
 * {@link UiClassAttributeNode}, that restricts creation and selection to classes
 * inheriting from android.app.Application.
 * <p/>
 * Used by the override for application/name in {@link AndroidManifestDescriptors}.
 */
public class ApplicationAttributeDescriptor extends TextAttributeDescriptor {

    /**
     * Used by {@link DescriptorsUtils} to create instances of this descriptor.
     */
    public static final ITextAttributeCreator CREATOR = new ITextAttributeCreator() {
        public TextAttributeDescriptor create(String xmlLocalName,
                String uiName, String nsUri, String tooltip,
                IAttributeInfo attrInfo) {
            return new ApplicationAttributeDescriptor(
                    xmlLocalName, uiName, nsUri, tooltip, attrInfo);
        }
    };

    public ApplicationAttributeDescriptor(String xmlLocalName, String uiName,
            String nsUri, String tooltip, IAttributeInfo attrInfo) {
        super(xmlLocalName, uiName, nsUri, tooltip, attrInfo);
    }

    /**
     * @return A new {@link UiClassAttributeNode} linked to this descriptor.
     */
    @Override
    public UiAttributeNode createUiNode(UiElementNode uiParent) {
        return new UiClassAttributeNode("android.app.Application", //$NON-NLS-1$
                null /* postCreationAction */, false /* mandatory */, this, uiParent,
                true /*defaultToProjectOnly*/);
    }
}
