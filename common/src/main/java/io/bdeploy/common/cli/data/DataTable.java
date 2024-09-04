package io.bdeploy.common.cli.data;

import java.util.List;

/**
 * A table displaying a series of {@link DataTableCell}s.
 */
public interface DataTable extends RenderableResult {

    /**
     * @param exitCode Indicates whether the operation was successful or had any errors.
     */
    public DataTable setExitCode(ExitCode exitCode);

    /**
     * @param caption The caption of the table. The caption row is hidden if no caption is set.
     */
    public DataTable setCaption(String caption);

    /**
     * Adds a column to the table.
     *
     * @param label The label of the column, used for human readable formats.
     * @param preferredWidth The preferred width in characters.
     */
    public DataTable column(String label, int preferredWidth);

    /**
     * Adds a {@link DataTableColumn column} to the table.
     */
    public DataTable column(DataTableColumn column);

    /**
     * @param cells a series of cells which make up a row in the table.
     */
    public DataTable row(List<DataTableCell> cells);

    /**
     * Creates a builder which will insert a row into the table when its build() method is called.
     */
    public DataTableRowBuilder row();

    /**
     * Add a horizontal ruler row at the current position.
     */
    public DataTable addHorizontalRuler();

    /**
     * @param footer A footer, e.g. a hint for a column label. The footer may be ignored depending on the output format.
     */
    public DataTable addFooter(String footer);

    /**
     * @param hide Whether to hide column headers. This is just a hint and may be ignored depending on the output format.
     */
    public DataTable setHideHeadersHint(boolean hide);

    /**
     * @param hint By how much to indent the rendered table. This is just a hint and may be ignored depending on the output
     *            format.
     */
    public DataTable setIndentHint(int hint);

    /**
     * @param wrap Whether overflow should be cut off or wrapped into an additional line in the table. This is just a hint and may
     *            be ignored depending on the output format.
     */
    public DataTable setLineWrapHint(boolean wrap);

    /**
     * @param allowBreak If <code>true</code> breaking words apart is allowed. If <code>false</code> (default) breaking words is
     *            only allowed if a single word cannot fit within a column.
     */
    public DataTable setWordBreakHint(boolean allowBreak);
}
