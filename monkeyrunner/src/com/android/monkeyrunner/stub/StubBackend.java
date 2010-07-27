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
package com.android.monkeyrunner.stub;

import com.android.monkeyrunner.MonkeyDevice;
import com.android.monkeyrunner.MonkeyManager;
import com.android.monkeyrunner.MonkeyRunnerBackend;

public class StubBackend implements MonkeyRunnerBackend {

    public MonkeyManager createManager(String address, int port) {
        // TODO Auto-generated method stub
        return null;
    }

    public MonkeyDevice waitForConnection(long timeout, String deviceId) {
        // TODO Auto-generated method stub
        return null;
    }

    public void shutdown() {
        // We're stub - we've got nothing to do.
    }
}
