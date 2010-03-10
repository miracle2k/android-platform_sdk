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

package com.android.ddmlib.testrunner;

import java.util.Collection;

/**
 * Interface for running a Android test command remotely and reporting result to a listener.
 */
public interface IRemoteAndroidTestRunner {

    /**
     * Returns the application package name.
     */
    public String getPackageName();

    /**
     * Returns the runnerName.
     */
    public String getRunnerName();

    /**
     * Sets to run only tests in this class
     * Must be called before 'run'.
     *
     * @param className fully qualified class name (eg x.y.z)
     */
    public void setClassName(String className);

    /**
     * Sets to run only tests in the provided classes
     * Must be called before 'run'.
     * <p>
     * If providing more than one class, requires a InstrumentationTestRunner that supports
     * the multiple class argument syntax.
     *
     * @param classNames array of fully qualified class names (eg x.y.z)
     */
    public void setClassNames(String[] classNames);

    /**
     * Sets to run only specified test method
     * Must be called before 'run'.
     *
     * @param className fully qualified class name (eg x.y.z)
     * @param testName method name
     */
    public void setMethodName(String className, String testName);

    /**
     * Sets to run all tests in specified package
     * Must be called before 'run'.
     *
     * @param packageName fully qualified package name (eg x.y.z)
     */
    public void setTestPackageName(String packageName);

    /**
     * Adds a argument to include in instrumentation command.
     * <p/>
     * Must be called before 'run'. If an argument with given name has already been provided, it's
     * value will be overridden.
     *
     * @param name the name of the instrumentation bundle argument
     * @param value the value of the argument
     */
    public void addInstrumentationArg(String name, String value);

    /**
     * Adds a boolean argument to include in instrumentation command.
     * <p/>
     * @see RemoteAndroidTestRunner#addInstrumentationArg
     *
     * @param name the name of the instrumentation bundle argument
     * @param value the value of the argument
     */
    public void addBooleanArg(String name, boolean value);

    /**
     * Sets this test run to log only mode - skips test execution.
     */
    public void setLogOnly(boolean logOnly);

    /**
     * Sets this debug mode of this test run. If true, the Android test runner will wait for a
     * debugger to attach before proceeding with test execution.
     */
    public void setDebug(boolean debug);

    /**
     * Sets this code coverage mode of this test run.
     */
    public void setCoverage(boolean coverage);

    /**
     * Execute this test run.
     * <p/>
     * Convenience method for {@link #run(Collection)}.
     *
     * @param listeners listens for test results
     */
    public void run(ITestRunListener... listeners);

    /**
     * Execute this test run.
     *
     * @param listeners collection of listeners for test results
     */
    public void run(Collection<ITestRunListener> listeners);

    /**
     * Requests cancellation of this test run.
     */
    public void cancel();

}
