package io.bdeploy.common.cli.data;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a table row cell by cell.
 */
public class DataTableRowBuilder {

    private final DataTable target;
    private final List<DataTableCell> cells = new ArrayList<>();

    DataTableRowBuilder(DataTable target) {
        this.target = target;
    }

    /**
     * @param data a cell to add. If the cell is a {@link DataTableCell} it is applied as is, otherwise {@link #toString()} is
     *            called on the data.
     */
    public DataTableRowBuilder cell(Object data) {
        if (data instanceof DataTableCell) {
            cells.add((DataTableCell) data);
        } else {
            cells.add(new DataTableCell(data));
        }
        return this;
    }

    public DataTable build() {
        target.row(cells);
        return target;
    }

}
