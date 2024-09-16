package io.bdeploy.common.cli.data;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Base class for {@link DataTable} implementations.
 * <p>
 * <b>NOTE:</b> Implementations of this class <b>do not</b> guarantee to be thread save!
 */
abstract class DataTableBase implements DataTable {

    protected final PrintStream output;
    protected final List<DataTableColumn> columns = new ArrayList<>();
    protected final List<List<DataTableCell>> rows = new ArrayList<>();
    protected final List<String> footers = new ArrayList<>();
    protected String caption;

    private ExitCode exitCode = ExitCode.OK;

    /**
     * @param output The output to render to.
     */
    protected DataTableBase(PrintStream output) {
        this.output = output;
    }

    /**
     * Renders the table to the predefined {@link #output}.
     */
    @Override
    public void render() {
        int columnCount = columns.size();
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).stream().map(DataTableCell::getSpan).reduce(0, Integer::sum) != columnCount) {
                throw new IllegalStateException(
                        "Number of cells in row " + (i + 1) + " does not match the column count of " + columnCount);
            }
        }
        doRender();
    }

    @Override
    public ExitCode getExitCode() {
        return this.exitCode;
    }

    @Override
    public DataTable setExitCode(ExitCode exitCode) {
        this.exitCode = exitCode;
        return this;
    }

    @Override
    public DataTable setCaption(String caption) {
        this.caption = caption;
        return this;
    }

    @Override
    public DataTable column(DataTableColumn column) {
        columns.add(column);
        return this;
    }

    @Override
    public DataTable row(List<DataTableCell> list) {
        rows.add(list);
        return this;
    }

    @Override
    public DataTableRowBuilder row() {
        return new DataTableRowBuilder(this);
    }

    @Override
    public DataTable addFooter(String footer) {
        footers.add(footer);
        return this;
    }

    abstract void doRender();
}
