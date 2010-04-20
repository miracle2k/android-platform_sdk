/*
 * Copyright (C) 2008 The Android Open Source Project
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

import com.android.ddmlib.testrunner.ITestRunListener.TestFailure;

import junit.framework.TestCase;


/**
 * Tests InstrumentationResultParser.
 */
public class InstrumentationResultParserTest extends TestCase {

    private InstrumentationResultParser mParser;
    private VerifyingTestResult mTestResult;

    // static dummy test names to use for validation
    private static final String CLASS_NAME = "com.test.FooTest";
    private static final String TEST_NAME = "testFoo";
    private static final String STACK_TRACE = "java.lang.AssertionFailedException";

    /**
     * @param name - test name
     */
    public InstrumentationResultParserTest(String name) {
        super(name);
    }

    /**
     * @see junit.framework.TestCase#setUp()
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mTestResult = new VerifyingTestResult();
        mParser = new InstrumentationResultParser(mTestResult);
    }

    /**
     * Tests that the test run started and test start events is sent on first
     * bundle received.
     */
    public void testTestStarted() {
        StringBuilder output = buildCommonResult();
        addStartCode(output);

        injectTestString(output.toString());
        assertCommonAttributes();
    }

    /**
     * Tests that a single successful test execution.
     */
    public void testTestSuccess() {
        StringBuilder output = createSuccessTest();

        injectTestString(output.toString());
        assertCommonAttributes();
        assertEquals(1, mTestResult.mNumTestsRun);
        assertEquals(null, mTestResult.mTestStatus);
    }

    /**
     * Create instrumentation output for a successful single test case execution.
     */
    private StringBuilder createSuccessTest() {
        StringBuilder output = buildCommonResult();
        addStartCode(output);
        addCommonStatus(output);
        addSuccessCode(output);
        return output;
    }

    /**
     * Test basic parsing of failed test case.
     */
    public void testTestFailed() {
        StringBuilder output = buildCommonResult();
        addStartCode(output);
        addCommonStatus(output);
        addStackTrace(output);
        addFailureCode(output);

        injectTestString(output.toString());
        assertCommonAttributes();

        assertEquals(1, mTestResult.mNumTestsRun);
        assertEquals(ITestRunListener.TestFailure.FAILURE, mTestResult.mTestStatus);
        assertEquals(STACK_TRACE, mTestResult.mTrace);
    }

    /**
     * Test basic parsing and conversion of time from output.
     */
    public void testTimeParsing() {
        StringBuilder output = createSuccessTest();
        output.append("Time: 4.9");
        injectTestString(output.toString());
        assertEquals(4900, mTestResult.mTestTime);
    }

    /**
     * Test basic parsing of a test run failure.
     */
    public void testRunFailed() {
        StringBuilder output = new StringBuilder();
        final String errorMessage = "Unable to find instrumentation info";
        addStatusKey(output, "Error", errorMessage);
        addStatusCode(output, "-1");
        output.append("INSTRUMENTATION_FAILED: com.dummy/android.test.InstrumentationTestRunner");
        addLineBreak(output);

        injectTestString(output.toString());

        assertEquals(errorMessage, mTestResult.mRunFailedMessage);
    }

    /**
     * Test parsing of a test run failure, where an instrumentation component failed to load
     * Parsing input takes the from of INSTRUMENTATION_RESULT: fff
     */
    public void testRunFailedResult() {
        StringBuilder output = new StringBuilder();
        final String errorMessage = "Unable to instantiate instrumentation";
        output.append("INSTRUMENTATION_RESULT: shortMsg=");
        output.append(errorMessage);
        addLineBreak(output);
        output.append("INSTRUMENTATION_CODE: 0");
        addLineBreak(output);

        injectTestString(output.toString());

        assertEquals(errorMessage, mTestResult.mRunFailedMessage);
    }

    /**
     * Test parsing of a test run that did not complete. This can occur if device spontaneously
     * reboots, or if test method could not be found
     */
    public void testRunIncomplete() {
        StringBuilder output = new StringBuilder();
        // add a start test sequence, but without an end test sequence
        addCommonStatus(output);
        addStartCode(output);

        injectTestString(output.toString());

        assertTrue(mTestResult.mRunFailedMessage.startsWith("Test run incomplete."));
        // ensure test is marked as failed
        assertEquals(TestFailure.ERROR, mTestResult.mTestStatus);
    }

    /**
     * Test parsing of a test run that did not start due to incorrect syntax supplied to am.
     */
    public void testRunAmFailed() {
        StringBuilder output = new StringBuilder();
        addLine(output, "usage: am [subcommand] [options]");
        addLine(output, "start an Activity: am start [-D] [-W] <INTENT>");
        addLine(output, "-D: enable debugging");
        addLine(output, "-W: wait for launch to complete");
        addLine(output, "start a Service: am startservice <INTENT>");
        addLine(output, "Error: Bad component name: wfsdafddfasasdf");

        injectTestString(output.toString());

        assertEquals(InstrumentationResultParser.NO_TEST_RESULTS_MSG,
                mTestResult.mRunFailedMessage);
    }

    /**
     * Test parsing of a test run that has no tests.
     * <p/>
     * Expect run to be reported as success.
     */
    public void testRunNoResults() {
        StringBuilder output = new StringBuilder();
        addLine(output, "INSTRUMENTATION_RESULT: stream=");
        addLine(output, "Test results for InstrumentationTestRunner=");
        addLine(output, "Time: 0.001");
        addLine(output, "OK (0 tests)");
        addLine(output, "INSTRUMENTATION_CODE: -1");

        injectTestString(output.toString());

        assertEquals(0, mTestResult.mTestCount);
        assertNull(mTestResult.mRunFailedMessage);
        assertEquals(1, mTestResult.mTestTime);
        assertFalse(mTestResult.mStopped);
    }

    /**
     * Builds a common test result using TEST_NAME and TEST_CLASS.
     */
    private StringBuilder buildCommonResult() {
        StringBuilder output = new StringBuilder();
        // add test start bundle
        addCommonStatus(output);
        addStatusCode(output, "1");
        // add end test bundle, without status
        addCommonStatus(output);
        return output;
    }

    /**
     * Adds common status results to the provided output.
     */
    private void addCommonStatus(StringBuilder output) {
        addStatusKey(output, "stream", "\r\n" + CLASS_NAME);
        addStatusKey(output, "test", TEST_NAME);
        addStatusKey(output, "class", CLASS_NAME);
        addStatusKey(output, "current", "1");
        addStatusKey(output, "numtests", "1");
        addStatusKey(output, "id", "InstrumentationTestRunner");
    }

    /**
     * Adds a stack trace status bundle to output.
     */
    private void addStackTrace(StringBuilder output) {
        addStatusKey(output, "stack", STACK_TRACE);
    }

    /**
     * Helper method to add a status key-value bundle.
     */
    private void addStatusKey(StringBuilder outputBuilder, String key,
            String value) {
        outputBuilder.append("INSTRUMENTATION_STATUS: ");
        outputBuilder.append(key);
        outputBuilder.append('=');
        outputBuilder.append(value);
        addLineBreak(outputBuilder);
    }

    /**
     * Append a line to output.
     */
    private void addLine(StringBuilder outputBuilder, String lineContent) {
        outputBuilder.append(lineContent);
        addLineBreak(outputBuilder);
    }

    /**
     * Append line break characters to output
     */
    private void addLineBreak(StringBuilder outputBuilder) {
        outputBuilder.append("\r\n");
    }

    private void addStartCode(StringBuilder outputBuilder) {
        addStatusCode(outputBuilder, "1");
    }

    private void addSuccessCode(StringBuilder outputBuilder) {
        addStatusCode(outputBuilder, "0");
    }

    private void addFailureCode(StringBuilder outputBuilder) {
        addStatusCode(outputBuilder, "-2");
    }

    private void addStatusCode(StringBuilder outputBuilder, String value) {
        outputBuilder.append("INSTRUMENTATION_STATUS_CODE: ");
        outputBuilder.append(value);
        addLineBreak(outputBuilder);
    }

    /**
     * inject a test string into the result parser.
     *
     * @param result
     */
    private void injectTestString(String result) {
        byte[] data = result.getBytes();
        mParser.addOutput(data, 0, data.length);
        mParser.flush();
    }

    private void assertCommonAttributes() {
        assertEquals(CLASS_NAME, mTestResult.mSuiteName);
        assertEquals(1, mTestResult.mTestCount);
        assertEquals(TEST_NAME, mTestResult.mTestName);
    }

    /**
     * A specialized test listener that stores a single test events.
     */
    private class VerifyingTestResult implements ITestRunListener {

        String mSuiteName;
        int mTestCount;
        int mNumTestsRun;
        String mTestName;
        long mTestTime;
        TestFailure mTestStatus;
        String mTrace;
        boolean mStopped;
        /** stores the error message provided to testRunFailed */
        String mRunFailedMessage;

        VerifyingTestResult() {
            mNumTestsRun = 0;
            mTestStatus = null;
            mStopped = false;
            mRunFailedMessage = null;
        }

        public void testEnded(TestIdentifier test) {
            mNumTestsRun++;
            assertEquals("Unexpected class name", mSuiteName, test.getClassName());
            assertEquals("Unexpected test ended", mTestName, test.getTestName());

        }

        public void testFailed(TestFailure status, TestIdentifier test, String trace) {
            mTestStatus = status;
            mTrace = trace;
            assertEquals("Unexpected class name", mSuiteName, test.getClassName());
            assertEquals("Unexpected test ended", mTestName, test.getTestName());
        }

        public void testRunEnded(long elapsedTime) {
            mTestTime = elapsedTime;

        }

        public void testRunStarted(int testCount) {
            mTestCount = testCount;
        }

        public void testRunStopped(long elapsedTime) {
            mTestTime = elapsedTime;
            mStopped = true;
        }

        public void testStarted(TestIdentifier test) {
            mSuiteName = test.getClassName();
            mTestName = test.getTestName();
        }

        public void testRunFailed(String errorMessage) {
            mRunFailedMessage = errorMessage;
        }
    }
}
