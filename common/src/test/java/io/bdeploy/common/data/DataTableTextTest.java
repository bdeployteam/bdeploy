package io.bdeploy.common.data;

import java.util.function.Function;

import org.junit.jupiter.api.Test;

import io.bdeploy.common.cli.data.DataFormat;

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
        TEST_UTIL.modifyAndTest(expectedShort, expectedLong, Function.identity());
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
        TEST_UTIL.modifyAndTest(expectedShort, expectedLong, Function.identity(), false);
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
        TEST_UTIL.modifyAndTest(expectedShort, expectedLong, table -> DataTableTestUtil.addLongTextRow(table));
    }

    @Test
    void testTableWithSpannedText() {
        String expectedShort = ""//
                + "┌──────────────────────────────────────┬──────┬───────┐\n"//
                + "│ col1                                 │ col2 │ col3  │\n"//
                + "├──────────────────────────────────────┼──────┼───────┤\n"//
                + "│ val1                                 │ val2 │ val3  │\n"//
                + "│ This first cell has a very long text indeed │ third │\n"//
                + "└─────────────────────────────────────────────┴───────┘\n";
        String expectedLong = ""//
                + "┌────────────────────────────────────────────────────┬───────────────────────────┬────────────┐\n"//
                + "│ col1                                               │ col2                      │ col3       │\n"//
                + "├────────────────────────────────────────────────────┼───────────────────────────┼────────────┤\n"//
                + "│ val1                                               │ val2                      │ val3       │\n"//
                + "│ This first cell has a very long text indeed                                    │ third      │\n"//
                + "└────────────────────────────────────────────────────────────────────────────────┴────────────┘\n";
        TEST_UTIL.modifyAndTest(expectedShort, expectedLong, table -> DataTableTestUtil.addSpannedTextRow(table));
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
        TEST_UTIL.modifyAndTest(expectedShort, expectedLong, table -> table.setHideHeadersHint(true));
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
        TEST_UTIL.modifyAndTest(expectedShort, expectedLong, table -> table.setCaption("Lorem ipsum dolor sit amet"));
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
        TEST_UTIL.modifyAndTest(expectedShort, expectedLong, table -> table.setIndentHint(3));
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
                table -> DataTableTestUtil.addLongTextRow(table.setLineWrapHint(true)));
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
        TEST_UTIL.modifyAndTest(expectedShort, expectedLong, table -> table.addFooter("What an interesting footer this is"));
    }

    @Test
    void testTableWithMaxTableLengthSimple() {
        String expectedLong = ""//
                + "┌────────────────────────────────────────────────────┬───────────────────────────┬────────────┐\n"//
                + "│ col1                                               │ col2                      │ col3       │\n"//
                + "├────────────────────────────────────────────────────┼───────────────────────────┼────────────┤\n"//
                + "│ val1                                               │ val2                      │ val3       │\n"//
                + "└────────────────────────────────────────────────────┴───────────────────────────┴────────────┘\n";

        String expectedShort1 = ""//
                + "┌──────┬──────┬──────┐\n"//
                + "│ col1 │ col2 │ col3 │\n"//
                + "├──────┼──────┼──────┤\n"//
                + "│ val1 │ val2 │ val3 │\n"//
                + "└──────┴──────┴──────┘\n";
        TEST_UTIL.modifyAndTest(expectedShort1, expectedLong, table -> table.setMaxTableLengthHint(100));
        TEST_UTIL.modifyAndTest(expectedShort1, expectedLong, table -> table.setMaxTableLengthHint(80));
        TEST_UTIL.modifyAndTest(expectedShort1, expectedLong, table -> table.setMaxTableLengthHint(-1));

        String expectedShort2 = ""//
                + "┌──┬──┬──┐\n"//
                + "│  │  │  │\n"//
                + "├──┼──┼──┤\n"//
                + "│  │  │  │\n"//
                + "└──┴──┴──┘\n";
        TEST_UTIL.modifyAndTest(expectedShort2, expectedLong, table -> table.setMaxTableLengthHint(0));
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
                table -> table.setMaxTableLengthHint(-1).setCaption(caption).addFooter("Footer 1").addFooter("Footer 2"));

        String expectedShort4 = ""//
                + "┌────────┐\n"//
                + "│ Coo... │\n"//
                + "├──┬──┬──┤\n"//
                + "│  │  │  │\n"//
                + "├──┼──┼──┤\n"//
                + "│  │  │  │\n"//
                + "├──┴──┴──┤\n"//
                + "│ Foo... │\n"//
                + "│ Foo... │\n"//
                + "└────────┘\n";
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
                + "┌───────────────────────────────┬───────────────────────┬──────────────────────┐\n"//
                + "│ col1                          │ col2                  │ col3                 │\n"//
                + "├───────────────────────────────┼───────────────────────┼──────────────────────┤\n"//
                + "│ val1                          │ val2                  │ val3                 │\n"//
                + "│ This first cell has a very... │ second one is shorter │ third                │\n"//
                + "└───────────────────────────────┴───────────────────────┴──────────────────────┘\n";
        TEST_UTIL.test(table -> DataTableTestUtil.addLongTextRow(table.setMaxTableLengthHint(80)), true, 10, 5, 20, expected2);

        String expected3 = ""//
                + "┌───────────────────────────────┬───────────────────────┬──────────────────────┐\n"//
                + "│ col1                          │ col2                  │ col3                 │\n"//
                + "├───────────────────────────────┼───────────────────────┼──────────────────────┤\n"//
                + "│ val1                          │ val2                  │ val3                 │\n"//
                + "│ This first cell has a very... │ second one is shorter │ third                │\n"//
                + "└───────────────────────────────┴───────────────────────┴──────────────────────┘\n";
        TEST_UTIL.test(table -> DataTableTestUtil.addLongTextRow(table.setMaxTableLengthHint(80)), true, 10, 10, 20, expected3);

        String expected4 = ""//
                + "┌─────────────────────────────────────────────┬───────────────────────┬──────────────────────┐\n"//
                + "│ col1                                        │ col2                  │ col3                 │\n"//
                + "├─────────────────────────────────────────────┼───────────────────────┼──────────────────────┤\n"//
                + "│ val1                                        │ val2                  │ val3                 │\n"//
                + "│ This first cell has a very long text indeed │ second one is shorter │ third                │\n"//
                + "└─────────────────────────────────────────────┴───────────────────────┴──────────────────────┘\n";
        TEST_UTIL.test(table -> DataTableTestUtil.addLongTextRow(table.setMaxTableLengthHint(-1)), true, 10, 5, 20, expected4);

        String expected5 = ""//
                + "┌─────────────────────────────────────────────┬───────────────────────┬──────────────────────┐\n"//
                + "│ col1                                        │ col2                  │ col3                 │\n"//
                + "├─────────────────────────────────────────────┼───────────────────────┼──────────────────────┤\n"//
                + "│ val1                                        │ val2                  │ val3                 │\n"//
                + "│ This first cell has a very long text indeed │ second one is shorter │ third                │\n"//
                + "└─────────────────────────────────────────────┴───────────────────────┴──────────────────────┘\n";
        TEST_UTIL.test(table -> DataTableTestUtil.addLongTextRow(table.setMaxTableLengthHint(-1)), true, 10, 0, 20, expected5);

        String expected6 = ""//
                + "┌────────────┬──┬──────────────────────┐\n"//
                + "│ col1       │  │ col3                 │\n"//
                + "├────────────┼──┼──────────────────────┤\n"//
                + "│ val1       │  │ val3                 │\n"//
                + "│ This fi... │  │ third                │\n"//
                + "└────────────┴──┴──────────────────────┘\n";
        TEST_UTIL.test(table -> DataTableTestUtil.addLongTextRow(table.setMaxTableLengthHint(0)), true, 10, 0, 20, expected6);
    }
}
