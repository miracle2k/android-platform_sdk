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

package com.android.ide.eclipse.adt.internal.editors.layout.gre;

import junit.framework.TestCase;

public class RulesEngineTest extends TestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }


    public void testCreate() {
// DISABLED to fix the build. EasyMock dependency not found on the build server,
// will be fixed in next CL.
//        // Creating a RulesEngine from a given project should ask for the location
//        // of the projects' /gscripts folder.
//        IProject projectMock = EasyMock.createMock(IProject.class);
//        EasyMock.expect(projectMock.findMember(RulesEngine.FD_GSCRIPTS)).andReturn(null);
//        EasyMock.replay(projectMock);
//
//        RulesEngine r = new RulesEngine(projectMock);
//        assertNotNull(r);
//
//        EasyMock.verify(projectMock);
    }

    public void testCallGetDisplayName() {
// DISABLED to fix the build. EasyMock dependency not found on the build server,
// will be fixed in next CL.
//        IProject projectMock = EasyMock.createMock(IProject.class);
//        EasyMock.expect(projectMock.findMember(RulesEngine.FD_GSCRIPTS)).andReturn(null);
//        EasyMock.expect(projectMock.getName()).andReturn("unit-test");
//        EasyMock.replay(projectMock);
//
//        RulesEngine r = new RulesEngine(projectMock);
//
//        ViewElementDescriptor ved = new ViewElementDescriptor("view", SdkConstants.CLASS_VIEW);
//        UiViewElementNode uiv = new UiViewElementNode(ved);
//
//        // TODO: this test is not ready. We need a way to override
//        // String result = r.callGetDisplayName(uiv);
//        // assertEquals("com.example.MyJavaClass", result);
    }
}
