package io.bdeploy.common.cli.data;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import io.bdeploy.common.util.StringHelper;

/**
 * Base class for {@link DataTable} implementations.
 */
public abstract class DataTableBase implements DataTable {

    private final PrintStream output;

    private String caption;

    private final List<DataTableColumn> columns = new ArrayList<>();
    private final List<List<DataTableCell>> rows = new ArrayList<>();
    private final List<String> footers = new ArrayList<>();

    /**
     * @param output the output to render to.
     */
    protected DataTableBase(PrintStream output) {
        this.output = output;
    }

    /**
     * @return the output to render to.
     */
    protected PrintStream out() {
        return output;
    }

    /**
     * @return the set caption or <code>null</code> if there should be no caption.
     */
    protected String getCaption() {
        return caption;
    }

    /**
     * @return all currently defined data rows. Each row contains a series of cells whos accumulated span must equal the number of
     *         columns.
     */
    protected List<List<DataTableCell>> getRows() {
        return rows;
    }

    /**
     * @return all curreclty defined footers.
     */
    protected List<String> getFooters() {
        return footers;
    }

    /**
     * @param data the original data
     * @param limit the length limit
     * @return the original data limited to the given length.
     */
    protected String limit(String data, int limit) {
        if (data.length() <= limit) {
            return data;
        }
        return data.substring(0, limit);
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
        return data.substring(0, limit - 3) + "...";
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
    public List<DataTableColumn> getColumns() {
        return columns;
    }

    @Override
    public DataTable row(List<DataTableCell> list) {
        if (list.stream().map(i -> i.span).reduce(0, Integer::sum) != columns.size()) {
            throw new IllegalStateException("Column count mismatch in data table");
        }
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

}
