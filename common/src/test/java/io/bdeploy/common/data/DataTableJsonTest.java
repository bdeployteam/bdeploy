package io.bdeploy.common.data;

import java.util.function.Function;

import org.junit.jupiter.api.Test;

import io.bdeploy.common.cli.data.DataFormat;

class DataTableJsonTest {

    private static final DataTableTestUtil TEST_UTIL = new DataTableTestUtil(DataFormat.JSON);

    @Test
    void testSimpleTable() {
        String expected = ""//
                + "[\r\n"//
                + "  { \"Col1\": \"val1\", \"Col2\": \"val2\", \"Col3\": \"val3\" }\r\n"//
                + "]";
        TEST_UTIL.modifyAndTest(expected, expected, Function.identity());
    }

    @Test
    void testTableWithoutData() {
        String expected = ""//
                + "[\r\n"//
                + "]";
        TEST_UTIL.modifyAndTest(expected, expected, Function.identity(), false);
    }

    @Test
    void testTableWithLongText() {
        String expected = ""//
                + "[\r\n"//
                + "  { \"Col1\": \"val1\", \"Col2\": \"val2\", \"Col3\": \"val3\" },\r\n"//
                + "  { \"Col1\": \"This first cell has a very long text indeed\", \"Col2\": \"second one is shorter\", \"Col3\": \"third\" }\r\n"//
                + "]";
        TEST_UTIL.modifyAndTest(expected, expected, table -> DataTableTestUtil.addLongTextRow(table));
    }

    @Test
    void testTableNoHeader() {
        String expected = ""//
                + "[\r\n"//
                + "  { \"Col1\": \"val1\", \"Col2\": \"val2\", \"Col3\": \"val3\" }\r\n"//
                + "]";
        TEST_UTIL.modifyAndTest(expected, expected, table -> table.setHideHeadersHint(true));
    }

    @Test
    void testTableWithCaption() {
        String expected = ""//
                + "[\r\n"//
                + "  { \"Col1\": \"val1\", \"Col2\": \"val2\", \"Col3\": \"val3\" }\r\n"//
                + "]";
        TEST_UTIL.modifyAndTest(expected, expected, table -> table.setCaption("Lorem ipsum dolor sit amet"));
    }

    @Test
    void testTableWithIndentation() {
        String expected = ""//
                + "[\r\n"//
                + "  { \"Col1\": \"val1\", \"Col2\": \"val2\", \"Col3\": \"val3\" }\r\n"//
                + "]";
        TEST_UTIL.modifyAndTest(expected, expected, table -> table.setIndentHint(3));
    }

    @Test
    void testTableWithLineWrap() {
        String expected = ""//
                + "[\r\n"//
                + "  { \"Col1\": \"val1\", \"Col2\": \"val2\", \"Col3\": \"val3\" },\r\n"//
                + "  { \"Col1\": \"This first cell has a very long text indeed\", \"Col2\": \"second one is shorter\", \"Col3\": \"third\" }\r\n"//
                + "]";
        TEST_UTIL.modifyAndTest(expected, expected, table -> DataTableTestUtil.addLongTextRow(table.setLineWrapHint(true)));
    }

    @Test
    void testTableWithHorizontalRulers() {
        String expected = ""//
                + "[\r\n"//
                + "  { \"Col1\": \"val1\", \"Col2\": \"val2\", \"Col3\": \"val3\" },\r\n"//
                + "  { \"Col1\": \"val4\", \"Col2\": \"val5\", \"Col3\": \"val6\" },\r\n"//
                + "  { \"Col1\": \"val7\", \"Col2\": \"val8\", \"Col3\": \"val9\" },\r\n"//
                + "  { \"Col1\": \"AAA\", \"Col2\": \"BBB\", \"Col3\": \"CCC\" }\r\n"//
                + "]";
        TEST_UTIL.modifyAndTest(expected, expected, table -> {
            table.addHorizontalRuler();
            table.row().cell("val4").cell("val5").cell("val6").build();
            table.row().cell("val7").cell("val8").cell("val9").build();
            table.addHorizontalRuler().addHorizontalRuler();
            table.row().cell("AAA").cell("BBB").cell("CCC").build();
            return table;
        });
    }

    @Test
    void testTableWithFooter() {
        String expected = ""//
                + "[\r\n"//
                + "  { \"Col1\": \"val1\", \"Col2\": \"val2\", \"Col3\": \"val3\" }\r\n"//
                + "]";
        TEST_UTIL.modifyAndTest(expected, expected, table -> table.addFooter("What an interesting footer this is"));
    }
}
