package io.bdeploy.common.data;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.function.Function;

import io.bdeploy.common.cli.data.DataFormat;
import io.bdeploy.common.cli.data.DataTable;
import io.bdeploy.common.cli.data.DataTableCell;
import io.bdeploy.common.cli.data.DataTableColumn;

class DataTableTestUtil extends DataTestUtil {

    DataTableTestUtil(DataFormat dataFormat) {
        super(dataFormat);
    }

    void modifyAndTest(String expectedShort, String expectedLong, Function<DataTable, DataTable> modifications) {
        modifyAndTest(expectedShort, expectedLong, modifications, true);
    }

    void modifyAndTest(String expectedShort, String expectedLong, Function<DataTable, DataTable> modifications, boolean addData) {
        test(modifications, addData, 0, 0, 0, expectedShort);
        test(modifications, addData, 50, 25, 10, expectedLong);
    }

    void test(Function<DataTable, DataTable> mods, boolean addData, int size1, int size2, int size3, String expected) {
        String result;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PrintStream ps = new PrintStream(baos, true, CHARSET)) {
            DataTable table = dataFormat.createTable(ps);
            table.column(new DataTableColumn.Builder("col1").setMinWidth(size1).build());
            table.column(new DataTableColumn.Builder("col2").setMinWidth(size2).build());
            table.column(new DataTableColumn.Builder("col3").setMinWidth(size3).build());
            if (addData) {
                table.row().cell("val1").cell("val2").cell("val3").build();
            }
            mods.apply(table).render();
            result = baos.toString(CHARSET);
        }
        assertEachLine(expected, result);
    }

    void testNullValues(String expected) {
        String result;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PrintStream ps = new PrintStream(baos, true, CHARSET)) {
            DataTable table = dataFormat.createTable(ps);
            table.setCaption(null);
            table.column(new DataTableColumn.Builder("col1").build());
            table.column(new DataTableColumn.Builder(null).build());
            table.column(new DataTableColumn.Builder(null).setMinWidth(20).build());
            table.row().cell("val1").cell("val2").cell("val3").build();
            table.row().cell("val4").cell(null).cell("val5").build();
            table.row().cell(null).cell(null).cell(null).build();
            table.row().cell("val6").cell("val7").cell("val8").build();
            table.addFooter(null);
            table.addFooter("foo1");
            table.addFooter(null);
            table.addFooter("foo2");
            table.render();
            result = baos.toString(CHARSET);
        }
        assertEachLine(expected, result);
    }

    static DataTable addLongTextRow(DataTable table) {
        return table.row().cell("This first cell has a very long text indeed").cell("second one is shorter").cell("third")
                .build();
    }

    static DataTable addShortSpannedTextRows(DataTable table) {
        table.row()//
                .cell(new DataTableCell("cell1", 1))//
                .cell(new DataTableCell("cell2", 1))//
                .cell(new DataTableCell("cell3", 1))//
                .build();
        table.row()//
                .cell(new DataTableCell("cell4", 2))//
                .cell(new DataTableCell("cell5", 1))//
                .build();
        table.row()//
                .cell(new DataTableCell("cell6", 1))//
                .cell(new DataTableCell("cell7", 2))//
                .build();
        table.row()//
                .cell(new DataTableCell("cell8", 3))//
                .build();
        return table;
    }

    static DataTable addLongSpannedTextRows(DataTable table) {
        table.row()//
                .cell(new DataTableCell("the first cell has a really long text", 2))//
                .cell(new DataTableCell("cell1", 1))//
                .build();
        table.row()//
                .cell(new DataTableCell("cell2", 1))//
                .cell(new DataTableCell("the second cell has a really long text", 2))//
                .build();
        table.row()//
                .cell(new DataTableCell("this cell is so long it covers the whole table wohoooooOOOOOOooooow", 3))//
                .build();
        return table;
    }
}
