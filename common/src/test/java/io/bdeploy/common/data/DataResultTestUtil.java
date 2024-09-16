package io.bdeploy.common.data;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.function.Function;

import io.bdeploy.common.cli.data.DataFormat;
import io.bdeploy.common.cli.data.DataResult;

class DataResultTestUtil extends DataTestUtil {

    DataResultTestUtil(DataFormat dataFormat) {
        super(dataFormat);
    }

    void test(String expected, Function<DataResult, DataResult> modifications) {
        test(expected, modifications, true);
    }

    void test(String expected, Function<DataResult, DataResult> modifications, boolean addMessage) {
        String result;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PrintStream ps = new PrintStream(baos, true, CHARSET)) {
            DataResult testResult = dataFormat.createResult(ps);
            if (addMessage) {
                testResult.setMessage("Example message");
            }
            modifications.apply(testResult).render();
            result = baos.toString(CHARSET);
        }
        System.out.print(result);
        assertEachLine(expected, result);
    }

    static Exception createTestException() {
        Exception exception = new Exception("Oh no!");
        exception.setStackTrace(new StackTraceElement[] {//
                new StackTraceElement("class1", "method1", "file1", 1),//
                new StackTraceElement("class2", "method2", "file2", 2),//
                new StackTraceElement("class3", "method3", "file3", 3),//
                new StackTraceElement("class4", "method4", "file4", 4),//
                new StackTraceElement("class5", "method5", "file5", 5) });
        return exception;
    }

    static Exception createNestedTestException() {
        Exception outerException = new Exception("Outer");
        outerException.setStackTrace(new StackTraceElement[] {//
                new StackTraceElement("oclass1", "omethod1", "ofile1", 1),//
                new StackTraceElement("oclass2", "omethod2", "ofile2", 2),//
                new StackTraceElement("oclass3", "omethod3", "ofile3", 3),//
                new StackTraceElement("oclass4", "omethod4", "ofile4", 4),//
                new StackTraceElement("oclass5", "omethod5", "ofile5", 5) });
        Exception innerException = new Exception("Inner");
        innerException.setStackTrace(new StackTraceElement[] {//
                new StackTraceElement("iclass1", "imethod1", "ifile1", 1),//
                new StackTraceElement("iclass2", "imethod2", "ifile2", 2),//
                new StackTraceElement("iclass3", "imethod3", "ifile3", 3),//
                new StackTraceElement("iclass4", "imethod4", "ifile4", 4),//
                new StackTraceElement("iclass5", "imethod5", "ifile5", 5) });
        outerException.initCause(innerException);
        return outerException;
    }
}
