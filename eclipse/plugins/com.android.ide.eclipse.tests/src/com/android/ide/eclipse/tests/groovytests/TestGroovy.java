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

package com.android.ide.eclipse.tests.groovytests;

import org.codehaus.groovy.control.CompilationFailedException;
import org.eclipse.swt.graphics.Rectangle;

import groovy.lang.GroovyClassLoader;

import java.io.FileNotFoundException;
import java.io.InputStream;

import junit.framework.TestCase;

/**
 * Tests we can invoke a groovy script that implements a given interface.
 */
public class TestGroovy extends TestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    /**
     * This is the interface that we want our Groovy script to implement.
     */
    public static interface AdtTestInterface {

        /** Method that returns a boolean. */
        public boolean acceptDrag(String xmlName);
        /** Method that returns an SWT Rectangle. */
        public Rectangle acceptDrop(String xmlName);

        /** Method that returns some Groovy object (opaque for us). */
        public Object returnGroovyObject();
        /** Method that accepts the Groovy object back. */
        public boolean testGroovyObject(Object o);
    }

    /**
     * Loads a groovy script that defines one class that implements the {@link AdtTestInterface}.
     *
     * @param filename The name of the script to load, that must be located in this Java package.
     * @return A non-null instance of the groovy class on success.
     * @throws CompilationFailedException if the groovy script failed to compile (e.g. syntax
     *             errors or it doesn't completely implement the interface.)
     * @throws ClassCastException if the groovy script does not implement our interface.
     * @throws FileNotFoundException if the groovy script does not exist.
     * @throws InstantiationException
     * @throws IllegalAccessException
     */
    @SuppressWarnings("unchecked")
    private AdtTestInterface loadScript(String filename)
        throws CompilationFailedException, ClassCastException,
               InstantiationException, IllegalAccessException,
               FileNotFoundException {
        // Get the input source from the sources or the JAR.
        InputStream myGroovyStream = getClass().getResourceAsStream(filename);

        // The stream is null if the file does not exists.
        if (myGroovyStream == null) {
            throw new FileNotFoundException(filename);
        }

        // Create a groovy class from it. Can fail to compile.
        ClassLoader cl = getClass().getClassLoader();
        GroovyClassLoader gcl = new GroovyClassLoader(cl);
        Class gClass = gcl.parseClass(myGroovyStream, filename);

        // Get an instance. This might throw ClassCastException.
        return (AdtTestInterface) gClass.newInstance();
    }

    /**
     * Tests that a {@link FileNotFoundException} is thrown if when trying
     * to load a missing script.
     */
    public void testMissingScript() throws Exception {
        try {
            @SuppressWarnings("unused")
            AdtTestInterface instance = loadScript("not_an_existing_script.groovy");
            fail("loadScript should not succeed, FileNotFoundException expected.");
        } catch (FileNotFoundException e) {
            assertEquals("not_an_existing_script.groovy", e.getMessage());
            return; // succeed
        }

        fail("Script failed to throw an exception on missing groovy file.");
    }

    /**
     * Tests that a {@link ClassCastException} is thrown if the script does not
     * implement our interface.
     */
    public void testInvalidInterface() throws Exception {
        try {
            @SuppressWarnings("unused")
            AdtTestInterface instance = loadScript("invalid_interface.groovy");
            fail("loadScript should not succeed, ClassCastException expected.");
        } catch(ClassCastException e) {
            // This has to fail because the script does not implement our interface
            // The message explains why but we're not harcoding the message in the test.
            assertNotNull(e.getMessage());
            return; // succeed
        }

        fail("Script failed to throw a ClassCastException.");
    }

    /**
     * Tests that a {@link CompilationFailedException} is thrown if the script
     * is not valid.
     */
    public void testCompilationError() throws Exception {
        try {
            @SuppressWarnings("unused")
            AdtTestInterface instance = loadScript("compile_error.groovy");
            fail("loadScript should not succeed, CompilationFailedException expected.");
        } catch (CompilationFailedException e) {
            // This script does not compile, the message explains why but we're not
            // harcoding the message in the test.
            assertNotNull(e.getMessage());
            return; // succeed
        }

        fail("Script failed to throw a compilation error.");
    }

    /**
     * Tests a valid script scenario with only some basic methods
     */
    public void testSimpleMethods() throws Exception {
        AdtTestInterface instance = loadScript("simple_test.groovy");

        assertTrue(instance.acceptDrag("LinearLayout"));
        assertFalse(instance.acceptDrag("RelativeLayout"));
        assertNull(instance.acceptDrop("none"));

        Rectangle r = instance.acceptDrop("LinearLayout");
        assertNotNull(r);
        assertEquals(new Rectangle(1, 2, 3, 4), r);
    }

    /**
     * Tests a valid script scenario with some methods providing some callbacks.
     */
    public void testCallback() throws Exception {
        AdtTestInterface instance = loadScript("simple_test.groovy");

        // The groovy method returns an object. We should treat it as an opaque object
        // which purpose is just to give it back to groovy later.
        Object o = instance.returnGroovyObject();
        assertNotNull(o);

        // Let the groovy script get back the object and play with it
        assertTrue(instance.testGroovyObject(o));
    }


}
