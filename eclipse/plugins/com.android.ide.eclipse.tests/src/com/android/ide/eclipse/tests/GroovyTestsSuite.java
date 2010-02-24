/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the "License"); you
 * may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.eclipse.org/org/documents/epl-v10.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.ide.eclipse.tests;

import com.android.ide.eclipse.tests.groovytests.TestGroovy;

import junit.framework.TestSuite;

/**
 * Container TestSuite for groovy tests to be run
 */

public class GroovyTestsSuite extends TestSuite {

    static final String GROOVY_TEST_PACKAGE = "com.android.ide.eclipse.tests.groovytests";

    public GroovyTestsSuite() {

    }

    /**
     * Returns a suite of test cases to be run.
     * Needed for JUnit3 compliant command line test runner
     */
    public static TestSuite suite() {
        TestSuite suite = new TestSuite();

        suite.addTestSuite(TestGroovy.class);

        return suite;
    }

}
