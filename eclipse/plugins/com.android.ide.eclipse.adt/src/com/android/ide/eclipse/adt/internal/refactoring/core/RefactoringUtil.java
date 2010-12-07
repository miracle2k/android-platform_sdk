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

package com.android.ide.eclipse.adt.internal.refactoring.core;

import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.sdklib.SdkConstants;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.jface.text.IDocument;
import org.eclipse.wst.sse.core.StructuredModelManager;
import org.eclipse.wst.sse.core.internal.provisional.IStructuredModel;
import org.w3c.dom.Attr;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

/**
 * The utility class for android refactoring
 *
 */
@SuppressWarnings("restriction")
public class RefactoringUtil {

    private static boolean sRefactorAppPackage = false;

    /**
     * Returns the new class name combined with a package name
     * the oldName and newName are class names as found in the manifest
     * (for instance with a leading dot or with a single element,
     * that needs to be recombined with a package name)
     *
     * @param javaPackage the package name
     * @param oldName the old name
     * @param newName the new name
     *
     * @return the new name
     */
    public static String getNewValue(String javaPackage, String oldName, String newName) {
        if (oldName == null || oldName.length() == 0) {
            return null;
        }
        if (javaPackage == null || javaPackage.length() == 0) {
            return null;
        }
        if (newName == null || newName.length() == 0) {
            return null;
        }
        if (!newName.startsWith(javaPackage + ".")) { //$NON-NLS-1$
            return newName;
        } else if (newName.length() > (javaPackage.length() + 1)) {
            String value = newName.substring(javaPackage.length() + 1);
            return value;
        }
        boolean startWithDot = (oldName.charAt(0) == '.');
        boolean hasDot = (oldName.indexOf('.') != -1);
        if (startWithDot || !hasDot) {

            if (startWithDot) {
                return "." + newName;
            } else {
                int lastPeriod = newName.lastIndexOf(".");
                return newName.substring(lastPeriod + 1);
            }
        } else {
            return newName;
        }
    }

    /**
     * Releases SSE read model; saves SSE model if exists edit model
     * Called in dispose method of refactoring change classes
     *
     * @param model the SSE model
     * @param document the document
     */
    public static void fixModel(IStructuredModel model, IDocument document) {
        if (model != null) {
            model.releaseFromRead();
        }
        model = null;
        if (document == null) {
            return;
        }
        try {
            model = StructuredModelManager.getModelManager().getExistingModelForEdit(document);
            if (model != null) {
                model.save();
            }
        } catch (UnsupportedEncodingException e1) {
            // ignore
        } catch (IOException e1) {
            // ignore
        } catch (CoreException e1) {
            // ignore
        } finally {
            if (model != null) {
                model.releaseFromEdit();
            }
        }
    }

    /**
     * Finds attribute by name in android namespace
     *
     * @param attributes the attributes collection
     * @param localName the local part of the qualified name
     *
     * @return the first attribute with this name in android namespace
     */
    public static Attr findAndroidAttributes(final NamedNodeMap attributes,
            final String localName) {
        Attr attribute = null;
        for (int j = 0; j < attributes.getLength(); j++) {
            Node attNode = attributes.item(j);
            if (attNode != null || attNode instanceof Attr) {
                Attr attr = (Attr) attNode;
                String name = attr.getLocalName();
                String namespace = attr.getNamespaceURI();
                if (SdkConstants.NS_RESOURCES.equals(namespace)
                        && name != null
                        && name.equals(localName)) {
                    attribute = attr;
                    break;
                }
            }
        }
        return attribute;
    }

    /**
     * Logs the error message
     *
     * @param message the message
     */
    public static void logError(String message) {
        AdtPlugin.log(IStatus.ERROR, AdtPlugin.PLUGIN_ID, message);
    }

    /**
     * Logs the info message
     *
     * @param message the message
     */
    public static void logInfo(String message) {
        AdtPlugin.log(IStatus.INFO, AdtPlugin.PLUGIN_ID, message);
    }

    /**
     * Logs the the exception
     *
     * @param e the exception
     */
    public static void log(Throwable e) {
        AdtPlugin.log(e, e.getMessage());
    }

    /**
     * @return true if Rename/Move package needs to change the application package
     * default is false
     *
     */
    public static boolean isRefactorAppPackage() {
        return sRefactorAppPackage;
    }

    /**
     * @param refactorAppPackage true if Rename/Move package needs to change the application package
     */
    public static void setRefactorAppPackage(boolean refactorAppPackage) {
        RefactoringUtil.sRefactorAppPackage = refactorAppPackage;
    }
}
