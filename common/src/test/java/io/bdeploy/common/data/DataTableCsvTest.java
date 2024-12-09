package io.bdeploy.common.data;

import java.util.function.Function;

import org.junit.jupiter.api.Test;

import io.bdeploy.common.cli.data.DataFormat;

class DataTableCsvTest {

    private static final DataTableTestUtil TEST_UTIL = new DataTableTestUtil(DataFormat.CSV);

    @Test
    void testSimpleTable() {
        String expected = ""//
                + "\"col1\";\"col2\";\"col3\"\n"//
                + "\"val1\";\"val2\";\"val3\"";
        TEST_UTIL.modifyAndTest(expected, expected, Function.identity());
    }

    @Test
    void testTableWithoutData() {
        String expected = ""//
                + "\"col1\";\"col2\";\"col3\"";
        TEST_UTIL.modifyAndTest(expected, expected, Function.identity(), false);
    }

    @Test
    void testTableWithLongText() {
        String expected = ""//
                + "\"col1\";\"col2\";\"col3\"\n" + "\"val1\";\"val2\";\"val3\"\n"
                + "\"This first cell has a very long text indeed\";\"second one is shorter\";\"third\"";
        TEST_UTIL.modifyAndTest(expected, expected, table -> DataTableTestUtil.addLongTextRow(table));
    }

    @Test
    void testTableWithShortSpannedText() {
        String expected = ""//
                + "\"col1\";\"col2\";\"col3\"\n"//
                + "\"val1\";\"val2\";\"val3\"\n"//
                + "\"cell1\";\"cell2\";\"cell3\"\n"//
                + "\"cell4\";;\"cell5\"\n"//
                + "\"cell6\";\"cell7\"\n"//
                + "\"cell8\"";
        TEST_UTIL.modifyAndTest(expected, expected, table -> DataTableTestUtil.addShortSpannedTextRows(table));
        TEST_UTIL.modifyAndTest(expected, expected, table -> DataTableTestUtil.addShortInfiniteSpannedTextRows(table));
    }

    @Test
    void testTableWithLongSpannedText() {
        String expected = ""//
                + "\"col1\";\"col2\";\"col3\"\n"//
                + "\"val1\";\"val2\";\"val3\"\n"//
                + "\"the first cell has a really long text\";;\"cell1\"\n"//
                + "\"cell2\";\"the second cell has a really long text\"\n"//
                + "\"this cell is so long it covers the whole table wohoooooOOOOOOooooow\"";
        TEST_UTIL.modifyAndTest(expected, expected, table -> DataTableTestUtil.addLongSpannedTextRows(table));
        TEST_UTIL.modifyAndTest(expected, expected, table -> DataTableTestUtil.addLongInfiniteSpannedTextRows(table));
    }

    @Test
    void testTableNoHeader() {
        String expected = ""//
                + "\"col1\";\"col2\";\"col3\"\n"//
                + "\"val1\";\"val2\";\"val3\"";
        TEST_UTIL.modifyAndTest(expected, expected, table -> table.setHideHeadersHint(true));
    }

    @Test
    void testTableWithCaption() {
        String expected = ""//
                + "\"col1\";\"col2\";\"col3\"\n"//
                + "\"val1\";\"val2\";\"val3\"";
        TEST_UTIL.modifyAndTest(expected, expected, table -> table.setCaption("Lorem ipsum dolor sit amet"));
    }

    @Test
    void testTableWithIndentation() {
        String expected = ""//
                + "\"col1\";\"col2\";\"col3\"\n"//
                + "\"val1\";\"val2\";\"val3\"";
        TEST_UTIL.modifyAndTest(expected, expected, table -> table.setIndentHint(3));
    }

    @Test
    void testTableWithLineWrap() {
        String expected = ""//
                + "\"col1\";\"col2\";\"col3\"\n"//
                + "\"val1\";\"val2\";\"val3\"\n"//
                + "\"This first cell has a very long text indeed\";\"second one is shorter\";\"third\"";
        TEST_UTIL.modifyAndTest(expected, expected, table -> DataTableTestUtil.addLongTextRow(table.setLineWrapHint(true)));
    }

    @Test
    void testTableWithHorizontalRulers() {
        String expected = ""//
                + "\"col1\";\"col2\";\"col3\"\n"//
                + "\"val1\";\"val2\";\"val3\"\n"//
                + "\"val4\";\"val5\";\"val6\"\n"//
                + "\"val7\";\"val8\";\"val9\"\n"//
                + "\"AAA\";\"BBB\";\"CCC\"";
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
                + "\"col1\";\"col2\";\"col3\"\n"//
                + "\"val1\";\"val2\";\"val3\"";
        TEST_UTIL.modifyAndTest(expected, expected, table -> table.addFooter("What an interesting footer this is"));
    }

    @Test
    void testTableWithoutColumns() {
        TEST_UTIL.testTableWithoutColumns();
    }

    @Test
    void testNullValues() {
        String expected = ""//
                + "\"col1\";\"\";\"\"\r\n"//
                + "\"val1\";\"val2\";\"val3\"\n"//
                + "\"val4\";\"\";\"val5\"\n"//
                + "\"\";\"\";\"\"\n"//
                + "\"val6\";\"val7\";\"val8\"\n"//
                + "\"\";;\"\"\n"//
                + "\"loooooooOOOOOOOooooooong text\";;\"\"\n"//
                + "\"\";;\"short text\"";
        TEST_UTIL.testNullValues(expected);
    }
}
