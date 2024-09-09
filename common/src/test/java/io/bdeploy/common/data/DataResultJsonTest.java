package io.bdeploy.common.data;

import java.util.function.Function;

import org.junit.jupiter.api.Test;

import io.bdeploy.common.cli.data.DataFormat;

class DataResultJsonTest {

    private static final DataResultTestUtil TEST_UTIL = new DataResultTestUtil(DataFormat.JSON);

    @Test
    void testSimpleResult() {
        String expected = ""//
                + "{\r\n"//
                + "  \"message\": \"Example message\"\r\n"//
                + "}";
        TEST_UTIL.test(expected, Function.identity());
    }

    @Test
    void testResultWithoutMessage() {
        String expected = ""//
                + "{\r\n"//
                + "}";
        TEST_UTIL.test(expected, Function.identity(), false);
    }

    @Test
    void testResultWithField() {
        String expected = ""//
                + "{\r\n"//
                + "  \"message\": \"Example message\",\r\n"//
                + "  \"Fieldname\": \"fieldValue\"\r\n"//
                + "}";
        TEST_UTIL.test(expected, result -> result.addField("fieldName", "fieldValue"));
    }

    @Test
    void testResultWithFields() {
        String expected = ""//
                + "{\r\n"//
                + "  \"message\": \"Example message\",\r\n"//
                + "  \"Key1\": \"value1\",\r\n"//
                + "  \"Key2\": \"value2\",\r\n"//
                + "  \"Key3\": \"value3\"\r\n"//
                + "}";
        TEST_UTIL.test(expected, result -> result//
                .addField("key1", "value1")//
                .addField("key2", "value2")//
                .addField("key3", "value3"));
    }

    @Test
    void testResultWithException() {
        String expected = ""//
                + "{\r\n"//
                + "  \"message\": \"Example message\",\r\n"//
                + "  \"error\": \"Oh no!\"\r\n"//
                + "}";
        TEST_UTIL.test(expected, result -> result.setException(DataResultTestUtil.createTestException()));
    }

    @Test
    void testResultWithNestedException() {
        String expected = ""//
                + "{\r\n"//
                + "  \"message\": \"Example message\",\r\n"//
                + "  \"error\": \"Outer; Inner\"\r\n"//
                + "}";
        TEST_UTIL.test(expected, result -> result.setException(DataResultTestUtil.createNestedTestException()));
    }

    @Test
    void testResultWithFieldsAndNestedException() {
        String expected = ""//
                + "{\r\n"//
                + "  \"message\": \"Example message\",\r\n"//
                + "  \"error\": \"Outer; Inner\"\r\n"//
                + "}";
        TEST_UTIL.test(expected, result -> result.setException(DataResultTestUtil.createNestedTestException())//
                .addField("key1", "value1")//
                .addField("key2", "value2")//
                .addField("key3", "value3"));
    }
}
