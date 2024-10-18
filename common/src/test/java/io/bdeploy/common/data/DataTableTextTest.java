package io.bdeploy.common.data;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.jupiter.api.Test;

import io.bdeploy.common.cli.data.DataFormat;
import io.bdeploy.common.cli.data.DataTable;
import io.bdeploy.common.cli.data.DataTableColumn;

class DataTableTextTest {

    private static final DataTableTestUtil TEST_UTIL = new DataTableTestUtil(DataFormat.TEXT);

    @Test
    void testSimpleTable() {
        String expectedShort = ""//
                + "┌──────┬──────┬──────┐\n"//
                + "│ col1 │ col2 │ col3 │\n"//
                + "├──────┼──────┼──────┤\n"//
                + "│ val1 │ val2 │ val3 │\n"//
                + "└──────┴──────┴──────┘\n";
        String expectedLong = ""//
                + "┌────────────────────────────────────────────────────┬───────────────────────────┬────────────┐\n"//
                + "│ col1                                               │ col2                      │ col3       │\n"//
                + "├────────────────────────────────────────────────────┼───────────────────────────┼────────────┤\n"//
                + "│ val1                                               │ val2                      │ val3       │\n"//
                + "└────────────────────────────────────────────────────┴───────────────────────────┴────────────┘\n";
        TEST_UTIL.modifyAndTest(expectedShort, expectedLong, table -> table.setMaxTableLengthHint(0));
    }

    @Test
    void testTableWithoutData() {
        String expectedShort = ""//
                + "┌──────┬──────┬──────┐\n"//
                + "│ col1 │ col2 │ col3 │\n"//
                + "├──────┴──────┴──────┤\n"//
                + "└────────────────────┘\n";
        String expectedLong = ""//
                + "┌────────────────────────────────────────────────────┬───────────────────────────┬────────────┐\n"//
                + "│ col1                                               │ col2                      │ col3       │\n"//
                + "├────────────────────────────────────────────────────┴───────────────────────────┴────────────┤\n"//
                + "└─────────────────────────────────────────────────────────────────────────────────────────────┘\n";
        TEST_UTIL.modifyAndTest(expectedShort, expectedLong, table -> table.setMaxTableLengthHint(0), false);
    }

    @Test
    void testTableWithLongText() {
        String expectedShort = ""//
                + "┌─────────────────────────────────────────────┬───────────────────────┬───────┐\n"//
                + "│ col1                                        │ col2                  │ col3  │\n"//
                + "├─────────────────────────────────────────────┼───────────────────────┼───────┤\n"//
                + "│ val1                                        │ val2                  │ val3  │\n"//
                + "│ This first cell has a very long text indeed │ second one is shorter │ third │\n"//
                + "└─────────────────────────────────────────────┴───────────────────────┴───────┘\n";
        String expectedLong = ""//
                + "┌────────────────────────────────────────────────────┬───────────────────────────┬────────────┐\n"//
                + "│ col1                                               │ col2                      │ col3       │\n"//
                + "├────────────────────────────────────────────────────┼───────────────────────────┼────────────┤\n"//
                + "│ val1                                               │ val2                      │ val3       │\n"//
                + "│ This first cell has a very long text indeed        │ second one is shorter     │ third      │\n"//
                + "└────────────────────────────────────────────────────┴───────────────────────────┴────────────┘\n";
        TEST_UTIL.modifyAndTest(expectedShort, expectedLong,
                table -> DataTableTestUtil.addLongTextRow(table).setMaxTableLengthHint(0));
    }

    @Test
    void testTableWithShortSpannedText() {
        String expectedShort = ""//
                + "┌───────┬───────┬───────┐\n"//
                + "│ col1  │ col2  │ col3  │\n"//
                + "├───────┼───────┼───────┤\n"//
                + "│ val1  │ val2  │ val3  │\n"//
                + "│ cell1 │ cell2 │ cell3 │\n"//
                + "│ cell4         │ cell5 │\n"//
                + "│ cell6 │ cell7         │\n"//
                + "│ cell8                 │\n"//
                + "└───────────────────────┘\n";
        String expectedLong = ""//
                + "┌────────────────────────────────────────────────────┬───────────────────────────┬────────────┐\n"//
                + "│ col1                                               │ col2                      │ col3       │\n"//
                + "├────────────────────────────────────────────────────┼───────────────────────────┼────────────┤\n"//
                + "│ val1                                               │ val2                      │ val3       │\n"//
                + "│ cell1                                              │ cell2                     │ cell3      │\n"//
                + "│ cell4                                                                          │ cell5      │\n"//
                + "│ cell6                                              │ cell7                                  │\n"//
                + "│ cell8                                                                                       │\n"//
                + "└─────────────────────────────────────────────────────────────────────────────────────────────┘\n";
        TEST_UTIL.modifyAndTest(expectedShort, expectedLong,
                table -> DataTableTestUtil.addShortSpannedTextRows(table).setMaxTableLengthHint(0));
        TEST_UTIL.modifyAndTest(expectedShort, expectedLong,
                table -> DataTableTestUtil.addShortInfiniteSpannedTextRows(table).setMaxTableLengthHint(0));
    }

    @Test
    void testTableWithLongSpannedText() {
        String expectedShort = ""//
                + "┌────────────────────────────────┬────────────────────────────────┬───────┐\n"//
                + "│ col1                           │ col2                           │ col3  │\n"//
                + "├────────────────────────────────┼────────────────────────────────┼───────┤\n"//
                + "│ val1                           │ val2                           │ val3  │\n"//
                + "│ the first cell has a really long text                           │ cell1 │\n"//
                + "│ cell2                          │ the second cell has a really long text │\n"//
                + "│ this cell is so long it covers the whole table wohoooooOOOOOOooooow     │\n"//
                + "└─────────────────────────────────────────────────────────────────────────┘\n";
        String expectedLong = ""//
                + "┌────────────────────────────────────────────────────┬───────────────────────────┬────────────┐\n"//
                + "│ col1                                               │ col2                      │ col3       │\n"//
                + "├────────────────────────────────────────────────────┼───────────────────────────┼────────────┤\n"//
                + "│ val1                                               │ val2                      │ val3       │\n"//
                + "│ the first cell has a really long text                                          │ cell1      │\n"//
                + "│ cell2                                              │ the second cell has a really long text │\n"//
                + "│ this cell is so long it covers the whole table wohoooooOOOOOOooooow                         │\n"//
                + "└─────────────────────────────────────────────────────────────────────────────────────────────┘\n";//
        TEST_UTIL.modifyAndTest(expectedShort, expectedLong,
                table -> DataTableTestUtil.addLongSpannedTextRows(table).setMaxTableLengthHint(0));
        TEST_UTIL.modifyAndTest(expectedShort, expectedLong,
                table -> DataTableTestUtil.addLongInfiniteSpannedTextRows(table).setMaxTableLengthHint(0));
    }

    @Test
    void testTableNoHeader() {
        String expectedShort = ""//
                + "┌──────┬──────┬──────┐\n"//
                + "│ val1 │ val2 │ val3 │\n"//
                + "└──────┴──────┴──────┘\n";
        String expectedLong = ""//
                + "┌────────────────────────────────────────────────────┬───────────────────────────┬────────────┐\n"//
                + "│ val1                                               │ val2                      │ val3       │\n"//
                + "└────────────────────────────────────────────────────┴───────────────────────────┴────────────┘\n";
        TEST_UTIL.modifyAndTest(expectedShort, expectedLong, table -> table.setHideHeadersHint(true).setMaxTableLengthHint(0));
    }

    @Test
    void testTableWithCaption() {
        String expectedShort = ""//
                + "┌────────────────────────────┐\n"//
                + "│ Lorem ipsum dolor sit amet │\n"//
                + "├──────┬──────┬──────────────┤\n"//
                + "│ col1 │ col2 │ col3         │\n"//
                + "├──────┼──────┼──────────────┤\n"//
                + "│ val1 │ val2 │ val3         │\n"//
                + "└──────┴──────┴──────────────┘\n";
        String expectedLong = ""//
                + "┌─────────────────────────────────────────────────────────────────────────────────────────────┐\n"//
                + "│ Lorem ipsum dolor sit amet                                                                  │\n"//
                + "├────────────────────────────────────────────────────┬───────────────────────────┬────────────┤\n"//
                + "│ col1                                               │ col2                      │ col3       │\n"//
                + "├────────────────────────────────────────────────────┼───────────────────────────┼────────────┤\n"//
                + "│ val1                                               │ val2                      │ val3       │\n"//
                + "└────────────────────────────────────────────────────┴───────────────────────────┴────────────┘\n";
        TEST_UTIL.modifyAndTest(expectedShort, expectedLong,
                table -> table.setCaption("Lorem ipsum dolor sit amet").setMaxTableLengthHint(0));
    }

    @Test
    void testTableWithIndentation() {
        String expectedShort = ""//
                + "   ┌──────┬──────┬──────┐\n"//
                + "   │ col1 │ col2 │ col3 │\n"//
                + "   ├──────┼──────┼──────┤\n"//
                + "   │ val1 │ val2 │ val3 │\n"//
                + "   └──────┴──────┴──────┘\n";
        String expectedLong = ""//
                + "   ┌────────────────────────────────────────────────────┬───────────────────────────┬────────────┐\n"//
                + "   │ col1                                               │ col2                      │ col3       │\n"//
                + "   ├────────────────────────────────────────────────────┼───────────────────────────┼────────────┤\n"//
                + "   │ val1                                               │ val2                      │ val3       │\n"//
                + "   └────────────────────────────────────────────────────┴───────────────────────────┴────────────┘\n";
        TEST_UTIL.modifyAndTest(expectedShort, expectedLong, table -> table.setIndentHint(3).setMaxTableLengthHint(0));
    }

    @Test
    void testTableWithLineWrap() {
        String expectedShort = ""//
                + "┌─────────────────────────────────────────────┬───────────────────────┬───────┐\n"//
                + "│ col1                                        │ col2                  │ col3  │\n"//
                + "├─────────────────────────────────────────────┼───────────────────────┼───────┤\n"//
                + "│ val1                                        │ val2                  │ val3  │\n"//
                + "│ This first cell has a very long text indeed │ second one is shorter │ third │\n"//
                + "└─────────────────────────────────────────────┴───────────────────────┴───────┘\n";
        String expectedLong = ""//
                + "┌────────────────────────────────────────────────────┬───────────────────────────┬────────────┐\n"//
                + "│ col1                                               │ col2                      │ col3       │\n"//
                + "├────────────────────────────────────────────────────┼───────────────────────────┼────────────┤\n"//
                + "│ val1                                               │ val2                      │ val3       │\n"//
                + "│ This first cell has a very long text indeed        │ second one is shorter     │ third      │\n"//
                + "└────────────────────────────────────────────────────┴───────────────────────────┴────────────┘\n";
        TEST_UTIL.modifyAndTest(expectedShort, expectedLong,
                table -> DataTableTestUtil.addLongTextRow(table.setLineWrapHint(true).setMaxTableLengthHint(0)));
    }

    @Test
    void testTableWithHorizontalRulers() {
        String expectedShort = ""//
                + "┌──────┬──────┬──────┐\n"//
                + "│ col1 │ col2 │ col3 │\n"//
                + "├──────┼──────┼──────┤\n"//
                + "│ val1 │ val2 │ val3 │\n"//
                + "├──────┼──────┼──────┤\n"//
                + "│ val4 │ val5 │ val6 │\n"//
                + "│ val7 │ val8 │ val9 │\n"//
                + "├──────┴──────┴──────┤\n"//
                + "├──────┬──────┬──────┤\n"//
                + "│ AAA  │ BBB  │ CCC  │\n"//
                + "└──────┴──────┴──────┘\n";
        String expectedLong = ""//
                + "┌────────────────────────────────────────────────────┬───────────────────────────┬────────────┐\n"//
                + "│ col1                                               │ col2                      │ col3       │\n"//
                + "├────────────────────────────────────────────────────┼───────────────────────────┼────────────┤\n"//
                + "│ val1                                               │ val2                      │ val3       │\n"//
                + "├────────────────────────────────────────────────────┼───────────────────────────┼────────────┤\n"//
                + "│ val4                                               │ val5                      │ val6       │\n"//
                + "│ val7                                               │ val8                      │ val9       │\n"//
                + "├────────────────────────────────────────────────────┴───────────────────────────┴────────────┤\n"//
                + "├────────────────────────────────────────────────────┬───────────────────────────┬────────────┤\n"//
                + "│ AAA                                                │ BBB                       │ CCC        │\n"//
                + "└────────────────────────────────────────────────────┴───────────────────────────┴────────────┘\n";
        TEST_UTIL.modifyAndTest(expectedShort, expectedLong, table -> {
            table.setMaxTableLengthHint(0);
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
        String expectedShort = ""//
                + "┌──────┬──────┬──────────────────────┐\n"//
                + "│ col1 │ col2 │ col3                 │\n"//
                + "├──────┼──────┼──────────────────────┤\n"//
                + "│ val1 │ val2 │ val3                 │\n"//
                + "├──────┴──────┴──────────────────────┤\n"//
                + "│ What an interesting footer this is │\n"//
                + "└────────────────────────────────────┘\n";
        String expectedLong = ""//
                + "┌────────────────────────────────────────────────────┬───────────────────────────┬────────────┐\n"//
                + "│ col1                                               │ col2                      │ col3       │\n"//
                + "├────────────────────────────────────────────────────┼───────────────────────────┼────────────┤\n"//
                + "│ val1                                               │ val2                      │ val3       │\n"//
                + "├────────────────────────────────────────────────────┴───────────────────────────┴────────────┤\n"//
                + "│ What an interesting footer this is                                                          │\n"//
                + "└─────────────────────────────────────────────────────────────────────────────────────────────┘\n";
        TEST_UTIL.modifyAndTest(expectedShort, expectedLong,
                table -> table.addFooter("What an interesting footer this is").setMaxTableLengthHint(0));
    }

    @Test
    void testNullValues() {
        String expected = ""//
                + "┌───────────────────────┬───────┬──────────────────────┐\n"//
                + "│ col1                  │       │                      │\n"//
                + "├───────────────────────┼───────┼──────────────────────┤\n"//
                + "│ val1                  │ val2  │ val3                 │\n"//
                + "│ val4                  │       │ val5                 │\n"//
                + "│                       │       │                      │\n"//
                + "│ val6                  │ val7  │ val8                 │\n"//
                + "│                               │                      │\n"//
                + "│ loooooooOOOOOOOooooooong text │                      │\n"//
                + "│                               │ short text           │\n"//
                + "├───────────────────────────────┴──────────────────────┤\n"//
                + "│ foo1                                                 │\n"//
                + "│ foo2                                                 │\n"//
                + "└──────────────────────────────────────────────────────┘\n";
        TEST_UTIL.testNullValues(expected);
    }

    @Test
    void testTableWithMaxTableLengthSimple() {
        String expectedLong = ""//
                + "┌────────────────────────────────────────────────────┬───────────────────────────┬────────────┐\n"//
                + "│ col1                                               │ col2                      │ col3       │\n"//
                + "├────────────────────────────────────────────────────┼───────────────────────────┼────────────┤\n"//
                + "│ val1                                               │ val2                      │ val3       │\n"//
                + "└────────────────────────────────────────────────────┴───────────────────────────┴────────────┘\n";

        String expectedShort = ""//
                + "┌──────┬──────┬──────┐\n"//
                + "│ col1 │ col2 │ col3 │\n"//
                + "├──────┼──────┼──────┤\n"//
                + "│ val1 │ val2 │ val3 │\n"//
                + "└──────┴──────┴──────┘\n";
        TEST_UTIL.modifyAndTest(expectedShort, expectedLong, table -> table.setMaxTableLengthHint(100));
        TEST_UTIL.modifyAndTest(expectedShort, expectedLong, table -> table.setMaxTableLengthHint(80));
        TEST_UTIL.modifyAndTest(expectedShort, expectedLong, table -> table.setMaxTableLengthHint(0));
    }

    @Test
    void testTableWithMaxTableLengthWithPeripherals() {
        String caption = "Cool and very very very VEEEEERRRY looooooooong caption";
        String expectedLong = ""//
                + "┌─────────────────────────────────────────────────────────────────────────────────────────────┐\n"//
                + "│ Cool and very very very VEEEEERRRY looooooooong caption                                     │\n"//
                + "├────────────────────────────────────────────────────┬───────────────────────────┬────────────┤\n"//
                + "│ col1                                               │ col2                      │ col3       │\n"//
                + "├────────────────────────────────────────────────────┼───────────────────────────┼────────────┤\n"//
                + "│ val1                                               │ val2                      │ val3       │\n"//
                + "├────────────────────────────────────────────────────┴───────────────────────────┴────────────┤\n"//
                + "│ Footer 1                                                                                    │\n"//
                + "│ Footer 2                                                                                    │\n"//
                + "└─────────────────────────────────────────────────────────────────────────────────────────────┘\n";

        String expectedShort1 = ""//
                + "┌─────────────────────────────────────────────────────────┐\n"//
                + "│ Cool and very very very VEEEEERRRY looooooooong caption │\n"//
                + "├──────┬──────┬───────────────────────────────────────────┤\n"//
                + "│ col1 │ col2 │ col3                                      │\n"//
                + "├──────┼──────┼───────────────────────────────────────────┤\n"//
                + "│ val1 │ val2 │ val3                                      │\n"//
                + "├──────┴──────┴───────────────────────────────────────────┤\n"//
                + "│ Footer 1                                                │\n"//
                + "│ Footer 2                                                │\n"//
                + "└─────────────────────────────────────────────────────────┘\n";
        TEST_UTIL.modifyAndTest(expectedShort1, expectedLong,//
                table -> table.setMaxTableLengthHint(100).setCaption(caption).addFooter("Footer 1").addFooter("Footer 2"));

        String expectedShort2 = ""//
                + "┌─────────────────────────────────────────────────────────┐\n"//
                + "│ Cool and very very very VEEEEERRRY looooooooong caption │\n"//
                + "├──────┬──────┬───────────────────────────────────────────┤\n"//
                + "│ col1 │ col2 │ col3                                      │\n"//
                + "├──────┼──────┼───────────────────────────────────────────┤\n"//
                + "│ val1 │ val2 │ val3                                      │\n"//
                + "├──────┴──────┴───────────────────────────────────────────┤\n"//
                + "│ Footer 1                                                │\n"//
                + "│ Footer 2                                                │\n"//
                + "└─────────────────────────────────────────────────────────┘\n";
        TEST_UTIL.modifyAndTest(expectedShort2, expectedLong,//
                table -> table.setMaxTableLengthHint(80).setCaption(caption).addFooter("Footer 1").addFooter("Footer 2"));

        String expectedShort3 = ""//
                + "┌─────────────────────────────────────────────────────────┐\n"//
                + "│ Cool and very very very VEEEEERRRY looooooooong caption │\n"//
                + "├──────┬──────┬───────────────────────────────────────────┤\n"//
                + "│ col1 │ col2 │ col3                                      │\n"//
                + "├──────┼──────┼───────────────────────────────────────────┤\n"//
                + "│ val1 │ val2 │ val3                                      │\n"//
                + "├──────┴──────┴───────────────────────────────────────────┤\n"//
                + "│ Footer 1                                                │\n"//
                + "│ Footer 2                                                │\n"//
                + "└─────────────────────────────────────────────────────────┘\n";
        TEST_UTIL.modifyAndTest(expectedShort3, expectedLong,//
                table -> table.setMaxTableLengthHint(0).setCaption(caption).addFooter("Footer 1").addFooter("Footer 2"));

        String expectedShort4 = ""//
                + "┌─────────────────────────────────────────────────────────┐\n"//
                + "│ Cool and very very very VEEEEERRRY looooooooong caption │\n"//
                + "├──────┬──────┬───────────────────────────────────────────┤\n"//
                + "│ col1 │ col2 │ col3                                      │\n"//
                + "├──────┼──────┼───────────────────────────────────────────┤\n"//
                + "│ val1 │ val2 │ val3                                      │\n"//
                + "├──────┴──────┴───────────────────────────────────────────┤\n"//
                + "│ Footer 1                                                │\n"//
                + "│ Footer 2                                                │\n"//
                + "└─────────────────────────────────────────────────────────┘\n";
        TEST_UTIL.modifyAndTest(expectedShort4, expectedLong,//
                table -> table.setMaxTableLengthHint(0).setCaption(caption).addFooter("Footer 1").addFooter("Footer 2"));
    }

    @Test
    void testTableWithMaxTableLengthWithCustomMinWidths() {
        String expected1 = ""//
                + "┌─────────────────────────────────────────────┬───────────────────────┬──────────────────────┐\n"//
                + "│ col1                                        │ col2                  │ col3                 │\n"//
                + "├─────────────────────────────────────────────┼───────────────────────┼──────────────────────┤\n"//
                + "│ val1                                        │ val2                  │ val3                 │\n"//
                + "│ This first cell has a very long text indeed │ second one is shorter │ third                │\n"//
                + "└─────────────────────────────────────────────┴───────────────────────┴──────────────────────┘\n";
        TEST_UTIL.test(table -> DataTableTestUtil.addLongTextRow(table.setMaxTableLengthHint(100)), true, 10, 5, 20, expected1);

        String expected2 = ""//
                + "┌─────────────────────────────────────────────┬────────┬──────────────────────┐\n"//
                + "│ col1                                        │ col2   │ col3                 │\n"//
                + "├─────────────────────────────────────────────┼────────┼──────────────────────┤\n"//
                + "│ val1                                        │ val2   │ val3                 │\n"//
                + "│ This first cell has a very long text indeed │ sec... │ third                │\n"//
                + "└─────────────────────────────────────────────┴────────┴──────────────────────┘\n";
        TEST_UTIL.test(table -> DataTableTestUtil.addLongTextRow(table.setMaxTableLengthHint(80)), true, 10, 5, 20, expected2);

        String expected3 = ""//
                + "┌─────────────────────────────────────────┬────────────┬──────────────────────┐\n"//
                + "│ col1                                    │ col2       │ col3                 │\n"//
                + "├─────────────────────────────────────────┼────────────┼──────────────────────┤\n"//
                + "│ val1                                    │ val2       │ val3                 │\n"//
                + "│ This first cell has a very long text... │ second ... │ third                │\n"//
                + "└─────────────────────────────────────────┴────────────┴──────────────────────┘\n";
        TEST_UTIL.test(table -> DataTableTestUtil.addLongTextRow(table.setMaxTableLengthHint(80)), true, 10, 10, 20, expected3);

        String expected4 = ""//
                + "┌─────────────────────────────────────────────┬───────────────────────┬──────────────────────┐\n"//
                + "│ col1                                        │ col2                  │ col3                 │\n"//
                + "├─────────────────────────────────────────────┼───────────────────────┼──────────────────────┤\n"//
                + "│ val1                                        │ val2                  │ val3                 │\n"//
                + "│ This first cell has a very long text indeed │ second one is shorter │ third                │\n"//
                + "└─────────────────────────────────────────────┴───────────────────────┴──────────────────────┘\n";
        TEST_UTIL.test(table -> DataTableTestUtil.addLongTextRow(table.setMaxTableLengthHint(0)), true, 10, 5, 20, expected4);

        String expected5 = ""//
                + "┌─────────────────────────────────────────────┬───────────────────────┬──────────────────────┐\n"//
                + "│ col1                                        │ col2                  │ col3                 │\n"//
                + "├─────────────────────────────────────────────┼───────────────────────┼──────────────────────┤\n"//
                + "│ val1                                        │ val2                  │ val3                 │\n"//
                + "│ This first cell has a very long text indeed │ second one is shorter │ third                │\n"//
                + "└─────────────────────────────────────────────┴───────────────────────┴──────────────────────┘\n";
        TEST_UTIL.test(table -> DataTableTestUtil.addLongTextRow(table.setMaxTableLengthHint(0)), true, 10, 0, 20, expected5);

        String expected6 = ""//
                + "┌─────────────────────────────────────────────┬────────┬──────────────────────┐\n"//
                + "│ col1                                        │ col2   │ col3                 │\n"//
                + "├─────────────────────────────────────────────┼────────┼──────────────────────┤\n"//
                + "│ val1                                        │ val2   │ val3                 │\n"//
                + "│ This first cell has a very long text indeed │ sec... │ third                │\n"//
                + "└─────────────────────────────────────────────┴────────┴──────────────────────┘\n";
        TEST_UTIL.test(table -> DataTableTestUtil.addLongTextRow(table.setMaxTableLengthHint(1)), true, 10, 0, 20, expected6);
    }

    @Test
    void testTableWithMaxTableLengthWithContentSizeFlag() {
        String expected = ""//
                + "┌─────────────────────────────────────────────┬────────┬──────────────────────┐\n"//
                + "│ col1                                        │ col2   │ col3                 │\n"//
                + "├─────────────────────────────────────────────┼────────┼──────────────────────┤\n"//
                + "│ val1                                        │ val2   │ val3                 │\n"//
                + "│ This first cell has a very long text indeed │ sec... │ third                │\n"//
                + "└─────────────────────────────────────────────┴────────┴──────────────────────┘\n";

        String result;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PrintStream ps = new PrintStream(baos, true, DataTableTestUtil.CHARSET)) {
            DataTable table = TEST_UTIL.dataFormat.createTable(ps);
            table.setMaxTableLengthHint(80);
            table.column(new DataTableColumn.Builder("col1").setMinWidth(10).setScaleToContent(true).build());
            table.column(new DataTableColumn.Builder("col2").setMinWidth(5).build());
            table.column(new DataTableColumn.Builder("col3").setMinWidth(20).build());
            table.row().cell("val1").cell("val2").cell("val3").build();
            DataTableTestUtil.addLongTextRow(table);
            table.render();
            result = baos.toString(DataTableTestUtil.CHARSET);
        }
        DataTableTestUtil.assertEachLine(expected, result);
    }
}
