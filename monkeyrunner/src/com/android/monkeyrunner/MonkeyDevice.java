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
package com.android.monkeyrunner;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import org.python.core.ArgParser;
import org.python.core.ClassDictInit;
import org.python.core.Py;
import org.python.core.PyDictionary;
import org.python.core.PyException;
import org.python.core.PyObject;
import org.python.core.PyTuple;

import com.android.monkeyrunner.doc.MonkeyRunnerExported;
import com.google.common.base.Functions;
import com.google.common.base.Preconditions;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableMap;

/*
 * Abstract base class that represents a single connected Android
 * Device and provides MonkeyRunner API methods for interacting with
 * that device.  Each backend will need to create a concrete
 * implementation of this class.
 */
@MonkeyRunnerExported(doc = "Represents a device attached to the system.")
public abstract class MonkeyDevice extends PyObject implements ClassDictInit {
    public static void classDictInit(PyObject dict) {
        JythonUtils.convertDocAnnotationsForClass(MonkeyDevice.class, dict);
    }

    @MonkeyRunnerExported(doc = "Sends a DOWN event when used with touch() or press().")
    public static final String DOWN = "down";
    @MonkeyRunnerExported(doc = "Sends an UP event when used with touch() or press().")
    public static final String UP = "up";
    @MonkeyRunnerExported(doc = "Sends a DOWN event, immediately followed by an UP event when used with touch() or press()")
    public static final String DOWN_AND_UP = "downAndUp";

    public enum TouchPressType {
        DOWN, UP, DOWN_AND_UP,
    }

    public static final Map<String, TouchPressType> TOUCH_NAME_TO_ENUM =
        ImmutableMap.of(MonkeyDevice.DOWN, TouchPressType.DOWN,
                MonkeyDevice.UP, TouchPressType.UP,
                MonkeyDevice.DOWN_AND_UP, TouchPressType.DOWN_AND_UP);

    private static final Set<String> VALID_DOWN_UP_TYPES = TOUCH_NAME_TO_ENUM.keySet();

    /**
     * Create a MonkeyMananger for talking to this device.
     *
     * NOTE: This is not part of the jython API.
     *
     * @return the MonkeyManager
     */
    public abstract MonkeyManager getManager();

    /**
     * Dispose of any native resoureces this device may have taken hold of.
     *
     *  NOTE: This is not part of the jython API.
     */
    public abstract void dispose();

    @MonkeyRunnerExported(doc =
    "Gets the device's screen buffer, yielding a screen capture of the entire display.",
            returns = "A MonkeyImage object (a bitmap wrapper)")
    public abstract MonkeyImage takeSnapshot();

    @MonkeyRunnerExported(doc = "Given the name of a variable on the device, " +
            "returns the variable's value",
            args = {"key"},
            argDocs = {"The name of the variable. The available names are listed in " +
            "http://developer.android.com/guide/topics/testing/monkeyrunner.html."},
            returns = "The variable's value")
    public String getProperty(PyObject[] args, String[] kws) {
        ArgParser ap = JythonUtils.createArgParser(args, kws);
        Preconditions.checkNotNull(ap);

        return getProperty(ap.getString(0));
    }

    @MonkeyRunnerExported(doc = "Synonym for getProperty()",
            args = {"key"},
            argDocs = {"The name of the system variable."},
            returns = "The variable's value.")
    public String getSystemProperty(PyObject[] args, String[] kws) {
        ArgParser ap = JythonUtils.createArgParser(args, kws);
        Preconditions.checkNotNull(ap);
        return getSystemProperty(ap.getString(0));
    }

    @MonkeyRunnerExported(doc = "Sends a touch event at the specified location",
            args = { "x", "y", "type" },
            argDocs = { "x coordinate in pixels",
                        "y coordinate in pixels",
                        "touch event type as returned by TouchPressType()"})
    public void touch(PyObject[] args, String[] kws) {
        ArgParser ap = JythonUtils.createArgParser(args, kws);
        Preconditions.checkNotNull(ap);

        int x = ap.getInt(0);
        int y = ap.getInt(1);

        // Default
        String type = MonkeyDevice.DOWN_AND_UP;
        try {
            String tmpType = ap.getString(1);
            if (VALID_DOWN_UP_TYPES.contains(tmpType)) {
                type = tmpType;
            } else {
                // not a valid type
                type = MonkeyDevice.DOWN_AND_UP;
            }
        } catch (PyException e) {
            // bad stuff was passed in, just use the already specified default value
            type = MonkeyDevice.DOWN_AND_UP;
        }
        touch(x, y, TOUCH_NAME_TO_ENUM.get(type));
    }

    @MonkeyRunnerExported(doc = "Simulates dragging (touch, hold, and move) on the device screen.",
            args = { "start", "end", "duration", "steps"},
            argDocs = { "The starting point for the drag (a tuple (x,y) in pixels)",
            "The end point for the drag (a tuple (x,y) in pixels",
            "Duration of the drag in seconds (default is 1.0 seconds)",
            "The number of steps to take when interpolating points. (default is 10)"})
    public void drag(PyObject[] args, String[] kws) {
        ArgParser ap = JythonUtils.createArgParser(args, kws);
        Preconditions.checkNotNull(ap);

        PyObject startObject = ap.getPyObject(0);
        if (!(startObject instanceof PyTuple)) {
            throw Py.TypeError("Agrument 0 is not a tuple");
        }
        PyObject endObject = ap.getPyObject(1);
        if (!(endObject instanceof PyTuple)) {
            throw Py.TypeError("Agrument 1 is not a tuple");
        }

        PyTuple start = (PyTuple) startObject;
        PyTuple end = (PyTuple) endObject;

        int startx = (Integer) start.__getitem__(0).__tojava__(Integer.class);
        int starty = (Integer) start.__getitem__(1).__tojava__(Integer.class);
        int endx = (Integer) end.__getitem__(0).__tojava__(Integer.class);
        int endy = (Integer) end.__getitem__(1).__tojava__(Integer.class);

        double seconds = JythonUtils.getFloat(ap, 2, 1.0);
        long ms = (long) (seconds * 1000.0);

        int steps = ap.getInt(3, 10);

        drag(startx, starty, endx, endy, steps, ms);
    }

    @MonkeyRunnerExported(doc = "Send a key event to the specified key",
            args = { "name", "type" },
            argDocs = { "the keycode of the key to press (see android.view.KeyEvent)",
            "touch event type as returned by TouchPressType(). To simulate typing a key, " +
            "send DOWN_AND_UP"})
    public void press(PyObject[] args, String[] kws) {
        ArgParser ap = JythonUtils.createArgParser(args, kws);
        Preconditions.checkNotNull(ap);

        String name = ap.getString(0);

        // Default
        String type = MonkeyDevice.DOWN_AND_UP;
        try {
            String tmpType = ap.getString(1);
            if (VALID_DOWN_UP_TYPES.contains(tmpType)) {
                type = tmpType;
            } else {
                // not a valid type
                type = MonkeyDevice.DOWN_AND_UP;
            }
        } catch (PyException e) {
            // bad stuff was passed in, just use the already specified default value
            type = MonkeyDevice.DOWN_AND_UP;
        }
        press(name, TOUCH_NAME_TO_ENUM.get(type));
    }

    @MonkeyRunnerExported(doc = "Types the specified string on the keyboard. This is " +
            "equivalent to calling press(keycode,DOWN_AND_UP) for each character in the string.",
            args = { "message" },
            argDocs = { "The string to send to the keyboard." })
    public void type(PyObject[] args, String[] kws) {
        ArgParser ap = JythonUtils.createArgParser(args, kws);
        Preconditions.checkNotNull(ap);

        String message = ap.getString(0);
        type(message);
    }

    @MonkeyRunnerExported(doc = "Executes an adb shell command and returns the result, if any.",
            args = { "cmd"},
            argDocs = { "The adb shell command to execute." },
            returns = "The output from the command.")
    public String shell(PyObject[] args, String[] kws) {
        ArgParser ap = JythonUtils.createArgParser(args, kws);
        Preconditions.checkNotNull(ap);

        String cmd = ap.getString(0);
        return shell(cmd);
    }

    @MonkeyRunnerExported(doc = "Reboots the specified device into a specified bootloader.",
            args = { "into" },
            argDocs = { "the bootloader to reboot into: bootloader, recovery, or None"})
    public void reboot(PyObject[] args, String[] kws) {
        ArgParser ap = JythonUtils.createArgParser(args, kws);
        Preconditions.checkNotNull(ap);

        String into = ap.getString(0, null);

        reboot(into);
    }

    @MonkeyRunnerExported(doc = "Installs the specified Android package (.apk file) " +
            "onto the device. If the package already exists on the device, it is replaced.",
            args = { "path" },
            argDocs = { "The package's path and filename on the host filesystem." },
            returns = "True if the install succeeded")
    public boolean installPackage(PyObject[] args, String[] kws) {
        ArgParser ap = JythonUtils.createArgParser(args, kws);
        Preconditions.checkNotNull(ap);

        String path = ap.getString(0);
        return installPackage(path);
    }

    @MonkeyRunnerExported(doc = "Deletes the specified package from the device, including its " +
            "associated data and cache.",
            args = { "package"},
            argDocs = { "The name of the package to delete."},
            returns = "True if remove succeeded")
    public boolean removePackage(PyObject[] args, String[] kws) {
        ArgParser ap = JythonUtils.createArgParser(args, kws);
        Preconditions.checkNotNull(ap);

        String packageName = ap.getString(0);
        return removePackage(packageName);
    }

    @MonkeyRunnerExported(doc = "Starts an Activity on the device by sending an Intent " +
            "constructed from the specified parameters.",
            args = { "uri", "action", "data", "mimetype", "categories", "extras",
                     "component", "flags" },
            argDocs = { "The URI for the Intent.",
                        "The action for the Intent.",
                        "The data URI for the Intent",
                        "The mime type for the Intent.",
                        "A Python iterable containing the category names for the Intent.",
                        "A dictionary of extras to add to the Intent. Types of these extras " +
                        "are inferred from the python types of the values.",
                        "The component of the Intent.",
                        "An iterable of flags for the Intent." +
                        "All arguments are optional. The default value for each argument is null." +
                        "(see android.content.Intent)"})

    public void startActivity(PyObject[] args, String[] kws) {
        ArgParser ap = JythonUtils.createArgParser(args, kws);
        Preconditions.checkNotNull(ap);

        String uri = ap.getString(0, null);
        String action = ap.getString(1, null);
        String data = ap.getString(2, null);
        String mimetype = ap.getString(3, null);
        Collection<String> categories = Collections2.transform(JythonUtils.getList(ap, 4),
                Functions.toStringFunction());
        Map<String, Object> extras = JythonUtils.getMap(ap, 5);
        String component = ap.getString(6, null);
        int flags = ap.getInt(7, 0);

        startActivity(uri, action, data, mimetype, categories, extras, component, flags);
    }

    @MonkeyRunnerExported(doc = "Sends a broadcast intent to the device.",
            args = { "uri", "action", "data", "mimetype", "categories", "extras",
                     "component", "flags" },
                     argDocs = { "The URI for the Intent.",
                             "The action for the Intent.",
                             "The data URI for the Intent",
                             "The mime type for the Intent.",
                             "An iterable of category names for the Intent.",
                             "A dictionary of extras to add to the Intent. Types of these extras " +
                             "are inferred from the python types of the values.",
                             "The component of the Intent.",
                             "An iterable of flags for the Intent." +
                             "All arguments are optional. " + "" +
                             "The default value for each argument is null." +
                             "(see android.content.Context.sendBroadcast(Intent))"})
    public void broadcastIntent(PyObject[] args, String[] kws) {
        ArgParser ap = JythonUtils.createArgParser(args, kws);
        Preconditions.checkNotNull(ap);

        String uri = ap.getString(0, null);
        String action = ap.getString(1, null);
        String data = ap.getString(2, null);
        String mimetype = ap.getString(3, null);
        Collection<String> categories = Collections2.transform(JythonUtils.getList(ap, 4),
                Functions.toStringFunction());
        Map<String, Object> extras = JythonUtils.getMap(ap, 5);
        String component = ap.getString(6, null);
        int flags = ap.getInt(7, 0);

        broadcastIntent(uri, action, data, mimetype, categories, extras, component, flags);
    }

    @MonkeyRunnerExported(doc = "Run the specified package with instrumentation and return " +
            "the output it generates. Use this to run a test package using " +
            "InstrumentationTestRunner.",
            args = { "className", "args" },
            argDocs = { "The class to run with instrumentation. The format is " +
                        "packagename/classname. Use packagename to specify the Android package " +
                        "to run, and classname to specify the class to run within that package. " +
                        "For test packages, this is usually " +
                        "testpackagename/InstrumentationTestRunner",
                        "A map of strings to objects containing the arguments to pass to this " +
                        "instrumentation (default value is None)." },
            returns = "A map of strings to objects for the output from the package. " +
                      "For a test package, contains a single key-value pair: the key is 'stream' " +
                      "and the value is a string containing the test output.")

    public PyDictionary instrument(PyObject[] args, String[] kws) {
        ArgParser ap = JythonUtils.createArgParser(args, kws);
        Preconditions.checkNotNull(ap);

        String packageName = ap.getString(0);
        Map<String, Object> instrumentArgs = JythonUtils.getMap(ap, 1);
        if (instrumentArgs == null) {
            instrumentArgs = Collections.emptyMap();
        }

        Map<String, Object> result = instrument(packageName, instrumentArgs);
        return JythonUtils.convertMapToDict(result);
    }

    @MonkeyRunnerExported(doc = "Wake up the screen on the device")
    public void wake(PyObject[] args, String[] kws) {
        ArgParser ap = JythonUtils.createArgParser(args, kws);
        Preconditions.checkNotNull(ap);

        wake();
    }

    /**
     * Reboot the device.
     *
     * @param into which bootloader to boot into.  Null means default reboot.
     */
    public abstract void reboot(@Nullable String into);

    public abstract String getProperty(String key);
    public abstract String getSystemProperty(String key);
    public abstract void touch(int x, int y, TouchPressType type);
    public abstract void press(String keyName, TouchPressType type);
    public abstract void drag(int startx, int starty, int endx, int endy, int steps, long ms);
    public abstract void type(String string);
    public abstract String shell(String cmd);
    public abstract boolean installPackage(String path);
    public abstract boolean removePackage(String packageName);
    public abstract void startActivity(@Nullable String uri, @Nullable String action,
            @Nullable String data, @Nullable String mimetype,
            Collection<String> categories, Map<String, Object> extras, @Nullable String component,
            int flags);
    public abstract void broadcastIntent(@Nullable String uri, @Nullable String action,
            @Nullable String data, @Nullable String mimetype,
            Collection<String> categories, Map<String, Object> extras, @Nullable String component,
            int flags);
    public abstract Map<String, Object> instrument(String packageName,
            Map<String, Object> args);
    public abstract void wake();
}
