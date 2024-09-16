package io.bdeploy.common.data;

import java.util.function.Function;

import org.junit.jupiter.api.Test;

import io.bdeploy.common.cli.data.DataFormat;

class DataResultTextTest {

    private static final DataResultTestUtil TEST_UTIL = new DataResultTestUtil(DataFormat.TEXT);

    @Test
    void testSimpleResult() {
        String expected = ""//
                + "┌───────────────────────────┬────────────────────────────────────────────────────┐\n"//
                + "│ Result Field              │ Result Value                                       │\n"//
                + "├───────────────────────────┼────────────────────────────────────────────────────┤\n"//
                + "│ Message                   │ Example message                                    │\n"//
                + "└───────────────────────────┴────────────────────────────────────────────────────┘\n";
        TEST_UTIL.test(expected, Function.identity());
    }

    @Test
    void testResultWithoutMessage() {
        String expected = ""//
                + "┌───────────────────────────┬────────────────────────────────────────────────────┐\n"//
                + "│ Result Field              │ Result Value                                       │\n"//
                + "├───────────────────────────┴────────────────────────────────────────────────────┤\n"//
                + "└────────────────────────────────────────────────────────────────────────────────┘\n";
        TEST_UTIL.test(expected, Function.identity(), false);
    }

    @Test
    void testResultWithField() {
        String expected = ""//
                + "┌───────────────────────────┬────────────────────────────────────────────────────┐\n"//
                + "│ Result Field              │ Result Value                                       │\n"//
                + "├───────────────────────────┼────────────────────────────────────────────────────┤\n"//
                + "│ Message                   │ Example message                                    │\n"//
                + "│ fieldName                 │ fieldValue                                         │\n"//
                + "└───────────────────────────┴────────────────────────────────────────────────────┘\n";
        TEST_UTIL.test(expected, result -> result.addField("fieldName", "fieldValue"));
    }

    @Test
    void testResultWithFields() {
        String expected = ""//
                + "┌───────────────────────────┬────────────────────────────────────────────────────┐\n"//
                + "│ Result Field              │ Result Value                                       │\n"//
                + "├───────────────────────────┼────────────────────────────────────────────────────┤\n"//
                + "│ Message                   │ Example message                                    │\n"//
                + "│ key1                      │ value1                                             │\n"//
                + "│ key2                      │ value2                                             │\n"//
                + "│ key3                      │ value3                                             │\n"//
                + "└───────────────────────────┴────────────────────────────────────────────────────┘\n";
        TEST_UTIL.test(expected, result -> result//
                .addField("key1", "value1")//
                .addField("key2", "value2")//
                .addField("key3", "value3"));
    }

    @Test
    void testResultWithException() {
        String expected = ""//
                + "┌──────────────────────────────────────────────────────────────────────────────────────────────────────┐\n"//
                + "│ Example message                                                                                      │\n"//
                + "├──────────────────────────────────────────────────────────────────────────────────────────────────────┤\n"//
                + "│ java.lang.Exception: Oh no!                                                                          │\n"//
                + "│     at class1.method1(file1:1)                                                                       │\n"//
                + "│     at class2.method2(file2:2)                                                                       │\n"//
                + "│     at class3.method3(file3:3)                                                                       │\n"//
                + "│     at class4.method4(file4:4)                                                                       │\n"//
                + "│     at class5.method5(file5:5)                                                                       │\n"//
                + "└──────────────────────────────────────────────────────────────────────────────────────────────────────┘\n";
        TEST_UTIL.test(expected, result -> result.setException(DataResultTestUtil.createTestException()));
    }

    @Test
    void testResultWithNestedException() {
        String expected = ""//
                + "┌──────────────────────────────────────────────────────────────────────────────────────────────────────┐\n"//
                + "│ Example message                                                                                      │\n"//
                + "├──────────────────────────────────────────────────────────────────────────────────────────────────────┤\n"//
                + "│ java.lang.Exception: Outer                                                                           │\n"//
                + "│     at oclass1.omethod1(ofile1:1)                                                                    │\n"//
                + "│     at oclass2.omethod2(ofile2:2)                                                                    │\n"//
                + "│     at oclass3.omethod3(ofile3:3)                                                                    │\n"//
                + "│     at oclass4.omethod4(ofile4:4)                                                                    │\n"//
                + "│     at oclass5.omethod5(ofile5:5)                                                                    │\n"//
                + "│ Caused by: java.lang.Exception: Inner                                                                │\n"//
                + "│     at iclass1.imethod1(ifile1:1)                                                                    │\n"//
                + "│     at iclass2.imethod2(ifile2:2)                                                                    │\n"//
                + "│     at iclass3.imethod3(ifile3:3)                                                                    │\n"//
                + "│     at iclass4.imethod4(ifile4:4)                                                                    │\n"//
                + "│     at iclass5.imethod5(ifile5:5)                                                                    │\n"//
                + "└──────────────────────────────────────────────────────────────────────────────────────────────────────┘\n";

        TEST_UTIL.test(expected, result -> result.setException(DataResultTestUtil.createNestedTestException()));
    }

    @Test
    void testResultWithFieldsAndNestedException() {
        String expected = ""//
                + "┌──────────────────────────────────────────────────────────────────────────────────────────────────────┐\n"//
                + "│ Example message                                                                                      │\n"//
                + "├──────────────────────────────────────────────────────────────────────────────────────────────────────┤\n"//
                + "│ java.lang.Exception: Outer                                                                           │\n"//
                + "│     at oclass1.omethod1(ofile1:1)                                                                    │\n"//
                + "│     at oclass2.omethod2(ofile2:2)                                                                    │\n"//
                + "│     at oclass3.omethod3(ofile3:3)                                                                    │\n"//
                + "│     at oclass4.omethod4(ofile4:4)                                                                    │\n"//
                + "│     at oclass5.omethod5(ofile5:5)                                                                    │\n"//
                + "│ Caused by: java.lang.Exception: Inner                                                                │\n"//
                + "│     at iclass1.imethod1(ifile1:1)                                                                    │\n"//
                + "│     at iclass2.imethod2(ifile2:2)                                                                    │\n"//
                + "│     at iclass3.imethod3(ifile3:3)                                                                    │\n"//
                + "│     at iclass4.imethod4(ifile4:4)                                                                    │\n"//
                + "│     at iclass5.imethod5(ifile5:5)                                                                    │\n"//
                + "└──────────────────────────────────────────────────────────────────────────────────────────────────────┘\n";
        TEST_UTIL.test(expected, result -> result.setException(DataResultTestUtil.createNestedTestException())//
                .addField("key1", "value1")//
                .addField("key2", "value2")//
                .addField("key3", "value3"));
    }
}
