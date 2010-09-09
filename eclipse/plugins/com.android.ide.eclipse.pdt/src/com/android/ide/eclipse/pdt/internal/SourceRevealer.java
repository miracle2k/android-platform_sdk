/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.ide.eclipse.pdt.internal;

import com.android.ide.eclipse.ddms.ISourceRevealer;

import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.actions.OpenJavaPerspectiveAction;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.texteditor.ITextEditor;

/**
 * Implementation of the com.android.ide.ddms.sourceRevealer extension point.
 */
public class SourceRevealer extends DevTreeProjectProvider implements ISourceRevealer {

    public boolean reveal(String applicationName, String className, int line) {
        IProject project = getProject();

        if (project != null) {
            // Inner classes are pointless: All we need is the enclosing type to find the file,
            // and the line number.
            // Since the anonymous ones will cause IJavaProject#findType to fail, we remove
            // all of them.
            int pos = className.indexOf('$');
            if (pos != -1) {
                className = className.substring(0, pos);
            }

            // get the java project
            IJavaProject javaProject = JavaCore.create(project);

            try {
                // look for the IType matching the class name.
                IType result = javaProject.findType(className);
                if (result != null && result.exists()) {
                    // before we show the type in an editor window, we make sure the current
                    // workbench page has an editor area (typically the ddms perspective doesn't).
                    IWorkbench workbench = PlatformUI.getWorkbench();
                    IWorkbenchWindow window = workbench.getActiveWorkbenchWindow();
                    IWorkbenchPage page = window.getActivePage();
                    if (page.isEditorAreaVisible() == false) {
                        // no editor area? we open the java perspective.
                        new OpenJavaPerspectiveAction().run();
                    }

                    IEditorPart editor = JavaUI.openInEditor(result);
                    if (editor instanceof ITextEditor) {
                        // get the text editor that was just opened.
                        ITextEditor textEditor = (ITextEditor)editor;

                        IEditorInput input = textEditor.getEditorInput();

                        // get the location of the line to show.
                        IDocumentProvider documentProvider = textEditor.getDocumentProvider();
                        IDocument document = documentProvider.getDocument(input);
                        IRegion lineInfo = document.getLineInformation(line - 1);

                        // select and reveal the line.
                        textEditor.selectAndReveal(lineInfo.getOffset(), lineInfo.getLength());
                    }

                    return true;
                }
            } catch (JavaModelException e) {
            } catch (PartInitException e) {
            } catch (BadLocationException e) {
            }
        }

        return false;
    }

}
