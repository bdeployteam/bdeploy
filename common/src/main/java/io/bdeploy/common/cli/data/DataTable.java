package io.bdeploy.common.cli.data;

import java.util.List;

/**
 * A table displaying a series of {@link DataTableCell}s.
 */
public interface DataTable extends RenderableResult {

    /**
     * @param caption The caption of the table. The caption row is hidden if no caption is set.
     */
    public DataTable setCaption(String caption);

    /**
     * Adds a column to the table. All columns must be added before adding actual data.
     *
     * @param label the label of the column, used for human readable formats.
     * @param preferredWidth the preferred width in characters.
     */
    public DataTable column(String label, int preferredWidth);

    /**
     * Adds a column to the table. All columns must be added before adding actual data.
     *
     * @param column the column definition.
     */
    public DataTable column(DataTableColumn column);

    /**
     * @param dataPoints a series of cells which make up a row in the table.
     */
    public DataTable row(List<DataTableCell> dataPoints);

    /**
     * Creates a builder which will insert a row into the table when its build() method is called.
     */
    public DataTableRowBuilder row();

    /**
     * @return all previously registered columns.
     */
    public List<DataTableColumn> getColumns();

    /**
     * @param footer a footer, e.g. a hint for a column label. Footers are only rendered in human readable formats.
     */
    public DataTable addFooter(String footer);

    /**
     * Add a horizontal ruler row at the current position.
     */
    public DataTable addHorizontalRuler();

    /**
     * @param hide whether to hide column headers. This is just a hint and may be ignored depending on the output format.
     */
    public DataTable setHideHeadersHint(boolean hide);

    /**
     * @param hint by how much to indent the rendered table. This is just a hint and may be ignored depending on the output
     *            format.
     */
    public DataTable setIndentHint(int hint);

    /**
     * @param wrap whether overflow should be cut off or wrapped into an additional line in the table. This is just a hint and may
     *            be ignored depending on the output format.
     */
    public DataTable setLineWrapHint(boolean wrap);

    /**
     * @param allowBreak if <code>true</code> breaking words apart is allowed. If <code>false</code> (default) breaking words is
     *            only allowed if a single word cannot fit within a column.
     */
    public DataTable setWordBreakHint(boolean allowBreak);

}
