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

import com.android.ide.eclipse.adt.internal.editors.descriptors.ElementDescriptor;
import com.android.ide.eclipse.adt.internal.editors.layout.descriptors.ViewElementDescriptor;

import org.eclipse.swt.dnd.ByteArrayTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.dnd.TransferData;

import java.io.UnsupportedEncodingException;

/**
 * A d'n'd {@link Transfer} class that can transfer a <em>single</em> {@link ElementDescriptor}.
 * <p/>
 * The implementation is based on the {@link ByteArrayTransfer} and what we transfer
 * is actually only the inner XML name of the element, which is unique enough.
 * <p/>
 * Drag source provides an {@link ElementDescriptor} object.
 * Drog receivers get back a {@link String} object representing the
 * {@link ElementDescriptor#getXmlName()}.
 * <p/>
 * Drop receivers can find the corresponding element by using
 * {@link ElementDescriptor#findChildrenDescriptor(String, boolean)} with the
 * XML name returned by this transfer operation and their root descriptor.
 * <p/>
 * Drop receivers must deal with the fact that this XML name may not exist in their
 * own {@link ElementDescriptor} hierarchy -- e.g. if the drag came from a different
 * GLE based on a different SDK platform or using custom widgets. In this case they
 * must refuse the drop.
 */
public class ElementDescTransfer extends ByteArrayTransfer {

    // Reference: http://www.eclipse.org/articles/Article-SWT-DND/DND-in-SWT.html


    private static final String TYPE_NAME = "android.ADT.element.desc.transfer.1";
    private static final int TYPE_ID = registerType(TYPE_NAME);
    private static ElementDescTransfer sInstance = new ElementDescTransfer();

    private ElementDescTransfer() {
        // pass
    }

    public static ElementDescTransfer getInstance() {
        return sInstance;
    }

    @Override
    protected int[] getTypeIds() {
        return new int[] { TYPE_ID };
    }

    @Override
    protected String[] getTypeNames() {
        return new String[] { TYPE_NAME };
    }

    @Override
    protected void javaToNative(Object object, TransferData transferData) {
        if (object == null || !(object instanceof ElementDescriptor)) {
            return;
        }

        if (isSupportedType(transferData)) {
            String data = null;
            ElementDescriptor desc = (ElementDescriptor)object;
            if (desc instanceof ViewElementDescriptor) {
                data = ((ViewElementDescriptor) desc).getFullClassName();
            } else if (desc != null) {
                data = desc.getXmlName();
            }
            if (data != null) {
                try {
                    byte[] buf = data.getBytes("UTF-8");  //$NON-NLS-1$
                    super.javaToNative(buf, transferData);
                } catch (UnsupportedEncodingException e) {
                    // unlikely; ignore
                }
            }
        }
    }

    @Override
    protected Object nativeToJava(TransferData transferData) {
        if (isSupportedType(transferData)) {
            byte[] buf = (byte[]) super.nativeToJava(transferData);
            if (buf != null && buf.length > 0) {
                try {
                    String s = new String(buf, "UTF-8"); //$NON-NLS-1$
                    return s;
                } catch (UnsupportedEncodingException e) {
                    // unlikely to happen, but still possible
                }
            }
        }

        return null;
    }
}
