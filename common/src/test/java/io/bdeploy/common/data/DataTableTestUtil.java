package io.bdeploy.common.data;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.function.Function;

import io.bdeploy.common.cli.data.DataFormat;
import io.bdeploy.common.cli.data.DataTable;
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

    private void test(Function<DataTable, DataTable> mods, boolean addData, int size1, int size2, int size3, String expected) {
        String result;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PrintStream ps = new PrintStream(baos, true, CHARSET)) {
            DataTable table = dataFormat.createTable(ps);
            table//
                    .column(new DataTableColumn("col1", size1))//
                    .column(new DataTableColumn("col2", size2))//
                    .column(new DataTableColumn("col3", size3));
            if (addData) {
                table.row().cell("val1").cell("val2").cell("val3").build();
            }
            mods.apply(table).render();
            result = baos.toString(CHARSET);
        }
        assertEachLine(expected, result);
    }

    static DataTable addLongTextRow(DataTable table) {
        return table.row().cell("This first cell has a very long text indeed").cell("second one is shorter").cell("third")
                .build();
    }
}
