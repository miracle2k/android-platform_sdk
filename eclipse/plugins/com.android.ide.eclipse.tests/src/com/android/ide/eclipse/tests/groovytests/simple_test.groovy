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

import com.android.ide.eclipse.tests.groovytests.TestGroovy.AdtTestInterface;

import org.eclipse.swt.graphics.Rectangle;


class AdtGroovyTest implements AdtTestInterface {

    /** Returns a true if the argument is LinearLayout. */
    public boolean acceptDrag(String xmlName) {
        if (xmlName == "LinearLayout") {
            return true;
        }

        return false;
    }

    /** Returns a new SWT Rectangle if LinearLayout or null. */
    public Rectangle acceptDrop(String xmlName) {
        if (xmlName == "LinearLayout") {
            return new Rectangle(1, 2, 3, 4);
        }

        return null;
    }

    /** Always throw an assert. */
    public void testAssert() {
        assert true == false
    }

    /**
     * Returns some Groovy object, in this case a map with some info and a closure.
     * The caller will return this object to testGroovyObject.
     */
    public Object returnGroovyObject() {

        return [
            TheInstance: this,
            SomeRect: new Rectangle(1, 2, 3, 4),
            SomeClosure: { int x, int y -> x + y }
            ]
    }

    /** Returns true if the object is the same as the one created by returnGroovyObject. */
    public boolean testGroovyObject(Object o) {
        // Input argument should be a map
        assert o.getClass() == LinkedHashMap

        // We expected these keys
        assert o.containsKey("TheInstance")
        assert o.containsKey("SomeRect")
        assert o.containsKey("SomeClosure")

        // Check the values
        assert o.TheInstance.is(this)   // check identity, not equality
        assert o.SomeRect == new Rectangle(1, 2, 3, 4)
        assert o.SomeClosure != null

        // Execute the closure
        assert o.SomeClosure(42, 3) == 45

        // Everything succeeded
        return true
    }

}

