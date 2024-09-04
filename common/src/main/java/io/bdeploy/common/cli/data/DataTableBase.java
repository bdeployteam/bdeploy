package io.bdeploy.common.cli.data;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import io.bdeploy.common.util.StringHelper;

/**
 * Base class for {@link DataTable} implementations.
 */
abstract class DataTableBase implements DataTable {

    protected final PrintStream output;
    protected final List<DataTableColumn> columns = new ArrayList<>();
    protected final List<List<DataTableCell>> rows = new ArrayList<>();
    protected final List<String> footers = new ArrayList<>();
    protected String caption;

    private ExitCode exitCode = ExitCode.OK;

    /**
     * @param output the output to render to.
     */
    protected DataTableBase(PrintStream output) {
        this.output = output;
    }

    /**
     * @param data the original data
     * @param limit the length limit
     * @return the original data limited to the given length minus 3 characters plus '...' appended.
     */
    protected String ellipsis(String data, int limit) {
        if (data.length() <= limit) {
            return data;
        }

        String ellipsis = "...";
        int ellipsisLength = ellipsis.length();
        if (limit <= ellipsisLength) {
            return data.substring(0, limit);
        }
        return data.substring(0, limit - ellipsisLength) + ellipsis;
    }

    /**
     * @param data the original data
     * @param width the width to expand the string to.
     * @return the original data in a string of exactly <code>width</code> length. The data is either filled up with spaces, or
     *         shortened according to {@link #ellipsis(String, int)}.
     */
    protected String expand(String data, int width) {
        String limited = ellipsis(data, width);
        if (limited.length() < width) {
            return limited + StringHelper.repeat(" ", width - limited.length());
        }
        return limited;
    }

    @Override
    public DataTable setCaption(String caption) {
        this.caption = caption;
        return this;
    }

    @Override
    public DataTable setIndentHint(int hint) {
        return this;
    }

    @Override
    public DataTable setLineWrapHint(boolean wrap) {
        return this;
    }

    @Override
    public DataTable setWordBreakHint(boolean allowBreak) {
        return this;
    }

    @Override
    public DataTable setHideHeadersHint(boolean hide) {
        return this;
    }

    @Override
    public DataTable addHorizontalRuler() {
        return this;
    }

    @Override
    public DataTable column(String label, int preferredWidth) {
        columns.add(new DataTableColumn(label, preferredWidth));
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

    @Override
    public DataTable setExitCode(ExitCode exitCode) {
        this.exitCode = exitCode;
        return this;
    }

    @Override
    public ExitCode getExitCode() {
        return this.exitCode;
    }

    @Override
    public final void render() {
        int columnCount = columns.size();
        for (int i = 0; i < rows.size(); i++) {
            List<DataTableCell> row = rows.get(i);
            if (row.stream().map(DataTableCell::getSpan).reduce(0, Integer::sum) != columnCount) {
                throw new IllegalStateException(
                        "Number of cells in row " + (i + 1) + " does not match the column count of " + columnCount);
            }
        }
        doRender();
    }

    abstract void doRender();
}
