package io.bdeploy.common.data;

import java.util.function.Function;

import org.junit.jupiter.api.Test;

import io.bdeploy.common.cli.data.DataFormat;

class DataTableCsvTest {

    private static final DataTableTestUtil TEST_UTIL = new DataTableTestUtil(DataFormat.CSV);

    @Test
    void testSimpleTable() {
        String expected = ""//
                + "\"col1\",\"col2\",\"col3\"\n"//
                + "\"val1\",\"val2\",\"val3\"";
        TEST_UTIL.modifyAndTest(expected, expected, Function.identity());
    }

    @Test
    void testTableWithoutData() {
        String expected = ""//
                + "\"col1\",\"col2\",\"col3\"";
        TEST_UTIL.modifyAndTest(expected, expected, Function.identity(), false);
    }

    @Test
    void testTableWithLongText() {
        String expected = ""//
                + "\"col1\",\"col2\",\"col3\"\n" + "\"val1\",\"val2\",\"val3\"\n"
                + "\"This first cell has a very long text indeed\",\"second one is shorter\",\"third\"";
        TEST_UTIL.modifyAndTest(expected, expected, table -> DataTableTestUtil.addLongTextRow(table));
    }

    @Test
    void testTableWithSpannedText() {
        String expected = ""//
                + "\"col1\",\"col2\",\"col3\"\n"//
                + "\"val1\",\"val2\",\"val3\"\n"//
                + "\"This first cell has a very long text indeed\",,\"third\"";
        TEST_UTIL.modifyAndTest(expected, expected, table -> DataTableTestUtil.addSpannedTextRow(table));
    }

    @Test
    void testTableNoHeader() {
        String expected = ""//
                + "\"col1\",\"col2\",\"col3\"\n"//
                + "\"val1\",\"val2\",\"val3\"";
        TEST_UTIL.modifyAndTest(expected, expected, table -> table.setHideHeadersHint(true));
    }

    @Test
    void testTableWithCaption() {
        String expected = ""//
                + "\"col1\",\"col2\",\"col3\"\n"//
                + "\"val1\",\"val2\",\"val3\"";
        TEST_UTIL.modifyAndTest(expected, expected, table -> table.setCaption("Lorem ipsum dolor sit amet"));
    }

    @Test
    void testTableWithIndentation() {
        String expected = ""//
                + "\"col1\",\"col2\",\"col3\"\n"//
                + "\"val1\",\"val2\",\"val3\"";
        TEST_UTIL.modifyAndTest(expected, expected, table -> table.setIndentHint(3));
    }

    @Test
    void testTableWithLineWrap() {
        String expected = ""//
                + "\"col1\",\"col2\",\"col3\"\n"//
                + "\"val1\",\"val2\",\"val3\"\n"//
                + "\"This first cell has a very long text indeed\",\"second one is shorter\",\"third\"";
        TEST_UTIL.modifyAndTest(expected, expected, table -> DataTableTestUtil.addLongTextRow(table.setLineWrapHint(true)));
    }

    @Test
    void testTableWithHorizontalRulers() {
        String expected = ""//
                + "\"col1\",\"col2\",\"col3\"\n"//
                + "\"val1\",\"val2\",\"val3\"\n"//
                + "\"val4\",\"val5\",\"val6\"\n"//
                + "\"val7\",\"val8\",\"val9\"\n"//
                + "\"AAA\",\"BBB\",\"CCC\"";
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
                + "\"col1\",\"col2\",\"col3\"\n"//
                + "\"val1\",\"val2\",\"val3\"";
        TEST_UTIL.modifyAndTest(expected, expected, table -> table.addFooter("What an interesting footer this is"));
    }
}
