package io.bdeploy.common.data;

import java.util.function.Function;

import org.junit.jupiter.api.Test;

import io.bdeploy.common.cli.data.DataFormat;

class DataResultCsvTest {

    private static final DataResultTestUtil TEST_UTIL = new DataResultTestUtil(DataFormat.CSV);

    @Test
    void testSimpleResult() {
        String expected = ""//
                + "\"Result Field\";\"Result Value\"\n"//
                + "\"Message\";\"Example message\"";
        TEST_UTIL.test(expected, Function.identity());
    }

    @Test
    void testResultWithoutMessage() {
        String expected = ""//
                + "\"Result Field\";\"Result Value\"";
        TEST_UTIL.test(expected, Function.identity(), false);
    }

    @Test
    void testResultWithField() {
        String expected = ""//
                + "\"Result Field\";\"Result Value\"\n"//
                + "\"Message\";\"Example message\"\n"//
                + "\"fieldName\";\"fieldValue\"";
        TEST_UTIL.test(expected, result -> result.addField("fieldName", "fieldValue"));
    }

    @Test
    void testResultWithFields() {
        String expected = ""//
                + "\"Result Field\";\"Result Value\"\n"//
                + "\"Message\";\"Example message\"\n"//
                + "\"key1\";\"value1\"\n"//
                + "\"key2\";\"value2\"\n"//
                + "\"key3\";\"value3\"";
        TEST_UTIL.test(expected,
                result -> result.addField("key1", "value1").addField("key2", "value2").addField("key3", "value3"));
    }

    @Test
    void testResultWithException() {
        String expected = ""//
                + "\"Result Field\";\"Result Value\"\n"//
                + "\"Message\";\"Example message\"\n"//
                + "\"Error\";\"Oh no!\"";
        TEST_UTIL.test(expected, result -> result.setException(DataResultTestUtil.createTestException()));
    }

    @Test
    void testResultWithNestedException() {
        String expected = ""//
                + "\"Result Field\";\"Result Value\"\n"//
                + "\"Message\";\"Example message\"\n"//
                + "\"Error\";\"Outer; Inner\"";
        TEST_UTIL.test(expected, result -> result.setException(DataResultTestUtil.createNestedTestException()));
    }

    @Test
    void testResultWithFieldsAndNestedException() {
        String expected = ""//
                + "\"Result Field\";\"Result Value\"\n"//
                + "\"Message\";\"Example message\"\n"//
                + "\"Error\";\"Outer; Inner\"";
        TEST_UTIL.test(expected, result -> result.setException(DataResultTestUtil.createNestedTestException())//
                .addField("key1", "value1").addField("key2", "value2").addField("key3", "value3"));
    }
}
