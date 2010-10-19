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
package com.android.monkeyrunner.recorder;

import com.android.monkeyrunner.MonkeyDevice;
import com.android.monkeyrunner.adb.AdbBackend;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.WindowConstants;

/**
 * Helper entry point for MonkeyRecorder.
 */
public class MonkeyRecorder {
    private static final Logger LOG = Logger.getLogger(MonkeyRecorder.class.getName());
    // This lock is used to keep the python process blocked while the frame is runing.
    private static final Object LOCK = new Object();

    /**
     * Jython entry point for MonkeyRecorder.  Meant to be called like this:
     *
     * <code>
     * from com.android.monkeyrunner import MonkeyRunner as mr
     * from com.android.monkeyrunner import MonkeyRecorder
     * MonkeyRecorder.start(mr.waitForConnection())
     * </code>
     *
     * @param device
     */
    public static void start(final MonkeyDevice device) {
        MonkeyRecorderFrame frame = new MonkeyRecorderFrame(device);
        // TODO: this is a hack until the window listener works.
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                device.dispose();
                synchronized (LOCK) {
                    LOCK.notifyAll();
                }
            }
        });

        frame.setVisible(true);
        synchronized (LOCK) {
            try {
                LOCK.wait();
            } catch (InterruptedException e) {
                LOG.log(Level.SEVERE, "Unexpected Exception", e);
            }
        }
    }

    public static void main(String[] args) {
        AdbBackend adb = new AdbBackend();
        MonkeyRecorder.start(adb.waitForConnection());
    }
}
