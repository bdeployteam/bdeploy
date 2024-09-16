package io.bdeploy.common.data;

import java.util.function.Function;

import org.junit.jupiter.api.Test;

import io.bdeploy.common.cli.data.DataFormat;

class DataResultJsonTest {

    private static final DataResultTestUtil TEST_UTIL = new DataResultTestUtil(DataFormat.JSON);

    @Test
    void testSimpleResult() {
        String expected = ""//
                + "{\n"//
                + "  \"message\": \"Example message\"\n"//
                + "}";
        TEST_UTIL.test(expected, Function.identity());
    }

    @Test
    void testResultWithoutMessage() {
        String expected = ""//
                + "{\n"//
                + "}";
        TEST_UTIL.test(expected, Function.identity(), false);
    }

    @Test
    void testResultWithField() {
        String expected = ""//
                + "{\n"//
                + "  \"message\": \"Example message\",\n"//
                + "  \"Fieldname\": \"fieldValue\"\n"//
                + "}";
        TEST_UTIL.test(expected, result -> result.addField("fieldName", "fieldValue"));
    }

    @Test
    void testResultWithFields() {
        String expected = ""//
                + "{\n"//
                + "  \"message\": \"Example message\",\n"//
                + "  \"Key1\": \"value1\",\n"//
                + "  \"Key2\": \"value2\",\n"//
                + "  \"Key3\": \"value3\"\n"//
                + "}";
        TEST_UTIL.test(expected, result -> result//
                .addField("key1", "value1")//
                .addField("key2", "value2")//
                .addField("key3", "value3"));
    }

    @Test
    void testResultWithException() {
        String expected = ""//
                + "{\n"//
                + "  \"message\": \"Example message\",\n"//
                + "  \"error\": \"Oh no!\"\n"//
                + "}";
        TEST_UTIL.test(expected, result -> result.setException(DataResultTestUtil.createTestException()));
    }

    @Test
    void testResultWithNestedException() {
        String expected = ""//
                + "{\n"//
                + "  \"message\": \"Example message\",\n"//
                + "  \"error\": \"Outer; Inner\"\n"//
                + "}";
        TEST_UTIL.test(expected, result -> result.setException(DataResultTestUtil.createNestedTestException()));
    }

    @Test
    void testResultWithFieldsAndNestedException() {
        String expected = ""//
                + "{\n"//
                + "  \"message\": \"Example message\",\n"//
                + "  \"error\": \"Outer; Inner\"\n"//
                + "}";
        TEST_UTIL.test(expected, result -> result.setException(DataResultTestUtil.createNestedTestException())//
                .addField("key1", "value1")//
                .addField("key2", "value2")//
                .addField("key3", "value3"));
    }
}
