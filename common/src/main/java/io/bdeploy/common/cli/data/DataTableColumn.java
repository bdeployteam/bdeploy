package io.bdeploy.common.cli.data;

/**
 * A single column in a {@link DataTable}.
 */
public class DataTableColumn {

    private final String label;
    private final String name;
    private final int minimumWidth;

    private DataTableColumn(String label, String name, int minimumWidth) {
        this.label = label;
        this.name = name;
        this.minimumWidth = minimumWidth;
    }

    /**
     * @return The human readable label of this {@link DataTableColumn}.
     */
    public String getLabel() {
        return label;
    }

    /**
     * @return The machine readable name of this {@link DataTableColumn}.
     */
    public String getName() {
        return name;
    }

    /**
     * @return The minimumWidth of this {@link DataTableColumn}.
     */
    public int getMinimumWidth() {
        return minimumWidth;
    }

    public static class Builder {

        private static final int DEFAULT_MINIMUM_WIDTH = 5;

        private final String label;
        private String name;
        private int minimumWidth = DEFAULT_MINIMUM_WIDTH;

        /**
         * @param label The human readable lable of the {@link DataTableColumn}.
         */
        public Builder(String label) {
            this.label = label;
        }

        /**
         * Sets the machine readable name {@link DataTableColumn}<br>
         * If not set, a default value will be calculated from the given label.
         *
         * @return This {@link Builder}, for chaining
         */
        public Builder setName(String name) {
            this.name = name;
            return this;
        }

        /**
         * Sets the minimum width of the {@link DataTableColumn} in characters.<br>
         * This value may or may not be respected, depending on the implementation.
         * <p>
         * The default value is {@value #DEFAULT_MINIMUM_WIDTH}.
         *
         * @return This {@link Builder}, for chaining
         */
        public Builder setMinWidth(int minimumWidth) {
            this.minimumWidth = minimumWidth;
            return this;
        }

        public DataTableColumn build() {
            return new DataTableColumn(label, name != null ? name : DataRenderingHelper.calculateName(label), minimumWidth);
        }
    }
}
