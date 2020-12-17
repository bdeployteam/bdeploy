package io.bdeploy.common.cli.data;

/**
 * A single cell of a {@link DataTable}. A cell may span multiple columns. If word wrapping is enabled on the {@link DataTable},
 * contents may span multiple rows.
 */
public class DataTableCell {

    public String data;
    public int span;

    public DataTableCell(String data) {
        this(data, 1);
    }

    public DataTableCell(String data, int span) {
        this.data = data;
        this.span = span;
    }

}