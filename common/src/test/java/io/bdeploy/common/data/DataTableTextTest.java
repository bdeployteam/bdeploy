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
                + "┌───────────────────────────┬──────────────┬───────┐\n"//
                + "│ col1                      │ col2         │ col3  │\n"//
                + "├───────────────────────────┼──────────────┼───────┤\n"//
                + "│ val1                      │ val2         │ val3  │\n"//
                + "└───────────────────────────┴──────────────┴───────┘\n";
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
                + "┌──────┬──────┬──────┐\n"//
                + "│ col1 │ col2 │ col3 │\n"//
                + "├──────┼──────┼──────┤\n"//
                + "│ val1 │ val2 │ val3 │\n"//
                + "│ T... │ s... │ t... │\n"//
                + "└──────┴──────┴──────┘\n";
        String expectedLong = ""//
                + "┌─────────────────────────────────────────────┬───────────────────────┬───────┐\n"//
                + "│ col1                                        │ col2                  │ col3  │\n"//
                + "├─────────────────────────────────────────────┼───────────────────────┼───────┤\n"//
                + "│ val1                                        │ val2                  │ val3  │\n"//
                + "│ This first cell has a very long text indeed │ second one is shorter │ third │\n"//
                + "└─────────────────────────────────────────────┴───────────────────────┴───────┘\n";
        TEST_UTIL.modifyAndTest(expectedShort, expectedLong, table -> DataTableTestUtil.addLongTextRow(table));
    }

    @Test
    void testTableNoHeader() {
        String expectedShort = ""//
                + "┌──────┬──────┬──────┐\n"//
                + "│ val1 │ val2 │ val3 │\n"//
                + "└──────┴──────┴──────┘\n";
        String expectedLong = ""//
                + "┌───────────────────────────┬──────────────┬───────┐\n"//
                + "│ val1                      │ val2         │ val3  │\n"//
                + "└───────────────────────────┴──────────────┴───────┘\n";
        TEST_UTIL.modifyAndTest(expectedShort, expectedLong, table -> table.setHideHeadersHint(true));
    }

    @Test
    void testTableWithCaption() {
        String expectedShort = ""//
                + "┌────────────────────┐\n"//
                + "│ Lorem ipsum dol... │\n"//
                + "├──────┬──────┬──────┤\n"//
                + "│ col1 │ col2 │ col3 │\n"//
                + "├──────┼──────┼──────┤\n"//
                + "│ val1 │ val2 │ val3 │\n"//
                + "└──────┴──────┴──────┘\n";
        String expectedLong = ""//
                + "┌──────────────────────────────────────────────────┐\n"//
                + "│ Lorem ipsum dolor sit amet                       │\n"//
                + "├───────────────────────────┬──────────────┬───────┤\n"//
                + "│ col1                      │ col2         │ col3  │\n"//
                + "├───────────────────────────┼──────────────┼───────┤\n"//
                + "│ val1                      │ val2         │ val3  │\n"//
                + "└───────────────────────────┴──────────────┴───────┘\n";
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
                + "   ┌───────────────────────────┬──────────────┬───────┐\n"//
                + "   │ col1                      │ col2         │ col3  │\n"//
                + "   ├───────────────────────────┼──────────────┼───────┤\n"//
                + "   │ val1                      │ val2         │ val3  │\n"//
                + "   └───────────────────────────┴──────────────┴───────┘\n";
        TEST_UTIL.modifyAndTest(expectedShort, expectedLong, table -> table.setIndentHint(3));
    }

    @Test
    void testTableWithLineWrap() {
        String expectedShort = ""//
                + "┌──────┬──────┬──────┐\n"//
                + "│ col1 │ col2 │ col3 │\n"//
                + "├──────┼──────┼──────┤\n"//
                + "│ val1 │ val2 │ val3 │\n"//
                + "│ This │ seco │ thir │\n"//
                + "│ firs │ nd   │ d    │\n"//
                + "│ t    │ one  │      │\n"//
                + "│ cell │ is   │      │\n"//
                + "│ has  │ shor │      │\n"//
                + "│ a    │ ter  │      │\n"//
                + "│ very │      │      │\n"//
                + "│ long │      │      │\n"//
                + "│ text │      │      │\n"//
                + "│ inde │      │      │\n"//
                + "│ ed   │      │      │\n"//
                + "└──────┴──────┴──────┘\n";
        String expectedLong = ""//
                + "┌─────────────────────────────────────────────┬───────────────────────┬───────┐\n"//
                + "│ col1                                        │ col2                  │ col3  │\n"//
                + "├─────────────────────────────────────────────┼───────────────────────┼───────┤\n"//
                + "│ val1                                        │ val2                  │ val3  │\n"//
                + "│ This first cell has a very long text indeed │ second one is shorter │ third │\n"//
                + "└─────────────────────────────────────────────┴───────────────────────┴───────┘\n";
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
                + "┌───────────────────────────┬──────────────┬───────┐\n"//
                + "│ col1                      │ col2         │ col3  │\n"//
                + "├───────────────────────────┼──────────────┼───────┤\n"//
                + "│ val1                      │ val2         │ val3  │\n"//
                + "├───────────────────────────┼──────────────┼───────┤\n"//
                + "│ val4                      │ val5         │ val6  │\n"//
                + "│ val7                      │ val8         │ val9  │\n"//
                + "├───────────────────────────┴──────────────┴───────┤\n"//
                + "├───────────────────────────┬──────────────┬───────┤\n"//
                + "│ AAA                       │ BBB          │ CCC   │\n"//
                + "└───────────────────────────┴──────────────┴───────┘\n";
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
                + "┌──────┬──────┬──────┐\n"//
                + "│ col1 │ col2 │ col3 │\n"//
                + "├──────┼──────┼──────┤\n"//
                + "│ val1 │ val2 │ val3 │\n"//
                + "├──────┴──────┴──────┤\n"//
                + "│ What an interes... │\n"//
                + "└────────────────────┘\n";
        String expectedLong = ""//
                + "┌───────────────────────────┬──────────────┬───────┐\n"//
                + "│ col1                      │ col2         │ col3  │\n"//
                + "├───────────────────────────┼──────────────┼───────┤\n"//
                + "│ val1                      │ val2         │ val3  │\n"//
                + "├───────────────────────────┴──────────────┴───────┤\n"//
                + "│ What an interesting footer this is               │\n"//
                + "└──────────────────────────────────────────────────┘\n";
        TEST_UTIL.modifyAndTest(expectedShort, expectedLong, table -> table.addFooter("What an interesting footer this is"));
    }
}
