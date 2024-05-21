package io.bdeploy.common.cli.data;

/**
 * A single column in a {@link DataTable}.
 */
public class DataTableColumn {

    private final String name;
    private final String label;
    private final int preferredWidth;
    private int width = -1;

    /**
     * @param label a human readable label for the column. A machine readable name will be calculated by removing spaces and
     *            lower-camel-casing words.
     * @param preferredWidth the preferred width of the column.
     */
    public DataTableColumn(String label, int preferredWidth) {
        this(DataRenderingHelper.calculateName(label), label, preferredWidth);
    }

    /**
     * @param name the machine readable name of the column
     * @param label the human readable label of the column
     * @param preferredWidth the preferred width of the column in characters.
     */
    public DataTableColumn(String name, String label, int preferredWidth) {
        this.name = name;
        this.label = label;
        this.preferredWidth = preferredWidth;
    }

    /**
     * @return a human readable label for the column
     */
    public String getLabel() {
        return label;
    }

    /**
     * @return a machine readable name for the column
     */
    public String getName() {
        return name;
    }

    /**
     * @return the width in characters
     */
    public int getWidth() {
        return width == -1 ? Math.max(preferredWidth, label.length()) : width;
    }

    public void setMaxCellLength(int maxCellLength) {
        width = maxCellLength; // stretch column width as much as necessary
        width = Math.min(width, preferredWidth * 2); // width can't be bigger than twice the preferred width
        width = Math.max(width, preferredWidth / 2); // width can't be smaller than half the preferred width
        width = Math.max(width, label.length()); // width can't be smaller than label length
    }
}
