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

import com.google.common.base.Functions;
import com.google.common.base.Preconditions;
import com.google.common.collect.Collections2;

import com.android.monkeyrunner.doc.MonkeyRunnerExported;

import org.python.core.ArgParser;
import org.python.core.ClassDictInit;
import org.python.core.PyException;
import org.python.core.PyObject;

import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JOptionPane;

/**
 * This is the main interface class into the jython bindings.
 */
@MonkeyRunnerExported(doc = "Main entry point for MonkeyRunner")
public class MonkeyRunner extends PyObject implements ClassDictInit {
    private static final Logger LOG = Logger.getLogger(MonkeyRunner.class.getCanonicalName());
    private static MonkeyRunnerBackend backend;

    public static void classDictInit(PyObject dict) {
        JythonUtils.convertDocAnnotationsForClass(MonkeyRunner.class, dict);
    }

    /**
     * Set the backend MonkeyRunner is using.
     *
     * @param backend the backend to use.
     */
    /* package */ static void setBackend(MonkeyRunnerBackend backend) {
        MonkeyRunner.backend = backend;
    }

    @MonkeyRunnerExported(doc = "Waits for the workstation to connect to the device.",
            args = {"timeout", "deviceId"},
            argDocs = {"The timeout in seconds to wait. The default is to wait indefinitely.",
            "A regular expression that specifies the device name. See the documentation " +
            "for 'adb' in the Developer Guide to learn more about device names."},
            returns = "A MonkeyDevice object representing the connected device.")
    public static MonkeyDevice waitForConnection(PyObject[] args, String[] kws) {
        ArgParser ap = JythonUtils.createArgParser(args, kws);
        Preconditions.checkNotNull(ap);

        long timeoutMs;
        try {
            double timeoutInSecs = JythonUtils.getFloat(ap, 0);
            timeoutMs = (long) (timeoutInSecs * 1000.0);
        } catch (PyException e) {
            timeoutMs = Long.MAX_VALUE;
        }

        return backend.waitForConnection(timeoutMs,
                ap.getString(1, ".*"));
    }

    @MonkeyRunnerExported(doc = "Pause the currently running program for the specified " +
            "number of seconds.",
            args = {"seconds"},
            argDocs = {"The number of seconds to pause."})
    public static void sleep(PyObject[] args, String[] kws) {
        ArgParser ap = JythonUtils.createArgParser(args, kws);
        Preconditions.checkNotNull(ap);

        double seconds = JythonUtils.getFloat(ap, 0);

        long ms = (long) (seconds * 1000.0);

        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            LOG.log(Level.SEVERE, "Error sleeping", e);
        }
    }

    @MonkeyRunnerExported(doc = "Format and display the API reference for MonkeyRunner.",
            args = { "format" },
            argDocs = {"The desired format for the output, either 'text' for plain text or " +
            "'html' for HTML markup."},
            returns = "A string containing the help text in the desired format.")
    public static String help(PyObject[] args, String[] kws) {
        ArgParser ap = JythonUtils.createArgParser(args, kws);
        Preconditions.checkNotNull(ap);

        String format = ap.getString(0, "text");

        return MonkeyRunnerHelp.helpString(format);
    }

    @MonkeyRunnerExported(doc = "Display an alert dialog to the process running the current " +
            "script.  The dialog is modal, so the script stops until the user dismisses the " +
            "dialog.",
            args = { "message", "title", "okTitle" },
            argDocs = {
            "The message to display in the dialog.",
            "The dialog's title. The default value is 'Alert'.",
            "The text to use in the dialog button. The default value is 'OK'."
    })
    public static void alert(PyObject[] args, String[] kws) {
        ArgParser ap = JythonUtils.createArgParser(args, kws);
        Preconditions.checkNotNull(ap);

        String message = ap.getString(0);
        String title = ap.getString(1, "Alert");
        String buttonTitle = ap.getString(2, "OK");

        alert(message, title, buttonTitle);
    }

    @MonkeyRunnerExported(doc = "Display a dialog that accepts input. The dialog is ," +
            "modal, so the script stops until the user clicks one of the two dialog buttons. To " +
            "enter a value, the user enters the value and clicks the 'OK' button. To quit the " +
            "dialog without entering a value, the user clicks the 'Cancel' button. Use the " +
            "supplied arguments for this method to customize the text for these buttons.",
            args = {"message", "initialValue", "title", "okTitle", "cancelTitle"},
            argDocs = {
            "The prompt message to display in the dialog.",
            "The initial value to supply to the user. The default is an empty string)",
            "The dialog's title. The default is 'Input'",
            "The text to use in the dialog's confirmation button. The default is 'OK'." +
            "The text to use in the dialog's 'cancel' button. The default is 'Cancel'."
    },
    returns = "The test entered by the user, or None if the user canceled the input;"
    )
    public static String input(PyObject[] args, String[] kws) {
        ArgParser ap = JythonUtils.createArgParser(args, kws);
        Preconditions.checkNotNull(ap);

        String message = ap.getString(0);
        String initialValue = ap.getString(1, "");
        String title = ap.getString(2, "Input");

        return input(message, initialValue, title);
    }

    @MonkeyRunnerExported(doc = "Display a choice dialog that allows the user to select a single " +
            "item from a list of items.",
            args = {"message", "choices", "title"},
            argDocs = {
            "The prompt message to display in the dialog.",
            "An iterable Python type containing a list of choices to display",
            "The dialog's title. The default is 'Input'" },
            returns = "The 0-based numeric offset of the selected item in the iterable.")
    public static int choice(PyObject[] args, String kws[]) {
        ArgParser ap = JythonUtils.createArgParser(args, kws);
        Preconditions.checkNotNull(ap);

        String message = ap.getString(0);
        Collection<String> choices = Collections2.transform(JythonUtils.getList(ap, 1),
                Functions.toStringFunction());
        String title = ap.getString(2, "Input");

        return choice(message, title, choices);
    }

    /**
     * Display an alert dialog.
     *
     * @param message the message to show.
     * @param title the title of the dialog box.
     * @param okTitle the title of the button.
     */
    public static void alert(String message, String title, String okTitle) {
        Object[] options = { okTitle };
        JOptionPane.showOptionDialog(null, message, title, JOptionPane.DEFAULT_OPTION,
                JOptionPane.INFORMATION_MESSAGE, null, options, options[0]);
    }

    /**
     * Display a dialog allow the user to pick a choice from a list of choices.
     *
     * @param message the message to show.
     * @param title the title of the dialog box.
     * @param choices the list of the choices to display.
     * @return the index of the selected choice, or -1 if nothing was chosen.
     */
    public static int choice(String message, String title, Collection<String> choices) {
        Object[] possibleValues = choices.toArray();
        Object selectedValue = JOptionPane.showInputDialog(null, message, title,
                JOptionPane.QUESTION_MESSAGE, null, possibleValues, possibleValues[0]);

        for (int x = 0; x < possibleValues.length; x++) {
            if (possibleValues[x].equals(selectedValue)) {
                return x;
            }
        }
        // Error
        return -1;
    }

    /**
     * Display a dialog that allows the user to input a text string.
     *
     * @param message the message to show.
     * @param initialValue the initial value to display in the dialog
     * @param title the title of the dialog box.
     * @return the entered string, or null if cancelled
     */
    public static String input(String message, String initialValue, String title) {
        return (String) JOptionPane.showInputDialog(null, message, title,
                JOptionPane.QUESTION_MESSAGE, null, null, initialValue);
    }
}
