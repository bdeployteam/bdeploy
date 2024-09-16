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
     * @param caption The caption of the {@link DataTable}. The caption row is hidden if no caption is set.
     */
    public DataTable setCaption(String caption);

    /**
     * Adds a {@link DataTableColumn column} to the {@link DataTable}.
     */
    public DataTable column(DataTableColumn column);

    /**
     * @param cells A series of {@link DataTableCell cells} which make up a row in the {@link DataTable}.
     */
    public DataTable row(List<DataTableCell> cells);

    /**
     * Creates a {@link DataTableRowBuilder builder} which will insert a row into the table when its
     * {@link DataTableRowBuilder#build() #build} method is called.
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
     * @param wrap Whether overflow should be cut off or wrapped into an additional line in the {@link DataTable}. This is just a
     *            hint and may be ignored depending on the output format.
     */
    public DataTable setLineWrapHint(boolean wrap);

    /**
     * @param allowBreak If <code>true</code> breaking words apart is allowed. If <code>false</code> (default) breaking words is
     *            only allowed if a single word cannot fit within a column.
     */
    public DataTable setWordBreakHint(boolean allowBreak);

    /**
     * @param indent By how much to indent the rendered {@link DataTable}. This is just a hint and may be ignored depending on the
     *            output format.
     */
    public DataTable setIndentHint(int indent);

    /**
     * @param maxTableLength The maximum line length of the rendered {@link DataTable}. This is just a hint and may be ignored
     *            depending on the output format. Also note that the line might still be longer if an {@link #setIndentHint(int)
     *            indent} has been set.
     */
    public DataTable setMaxTableLengthHint(int maxTableLength);
}
