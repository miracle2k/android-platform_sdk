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

package com.android.hierarchyviewerlib.device;

import com.android.ddmlib.IDevice;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.SocketChannel;

/**
 * This class is used for connecting to a device in debug mode running the view
 * server.
 */
public class DeviceConnection {

    // Now a socket channel, since socket channels are friendly with interrupts.
    private SocketChannel mSocketChannel;

    private BufferedReader mIn;

    private BufferedWriter mOut;

    public DeviceConnection(IDevice device) throws IOException {
        mSocketChannel = SocketChannel.open();
        int port = DeviceBridge.getDeviceLocalPort(device);

        if (port == -1) {
            throw new IOException();
        }

        mSocketChannel.connect(new InetSocketAddress("127.0.0.1", port)); //$NON-NLS-1$
        mSocketChannel.socket().setSoTimeout(40000);
    }

    public BufferedReader getInputStream() throws IOException {
        if (mIn == null) {
            mIn = new BufferedReader(new InputStreamReader(mSocketChannel.socket().getInputStream()));
        }
        return mIn;
    }

    public BufferedWriter getOutputStream() throws IOException {
        if (mOut == null) {
            mOut =
                    new BufferedWriter(new OutputStreamWriter(mSocketChannel.socket()
                            .getOutputStream()));
        }
        return mOut;
    }

    public Socket getSocket() {
        return mSocketChannel.socket();
    }

    public void sendCommand(String command) throws IOException {
        BufferedWriter out = getOutputStream();
        out.write(command);
        out.newLine();
        out.flush();
    }

    public void close() {
        try {
            if (mIn != null) {
                mIn.close();
            }
        } catch (IOException e) {
        }
        try {
            if (mOut != null) {
                mOut.close();
            }
        } catch (IOException e) {
        }
        try {
            mSocketChannel.close();
        } catch (IOException e) {
        }
    }
}
