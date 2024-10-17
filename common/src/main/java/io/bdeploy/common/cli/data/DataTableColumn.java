package io.bdeploy.common.cli.data;

/**
 * A single column in a {@link DataTable}.
 */
public class DataTableColumn {

    private final String label;
    private final String name;
    private final int minimumWidth;
    private final boolean scaleToContent;

    private DataTableColumn(String label, String name, int minimumWidth, boolean scaleToContent) {
        this.label = label;
        this.name = name;
        this.minimumWidth = minimumWidth;
        this.scaleToContent = scaleToContent;
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

    /**
     * @return The whether this {@link DataTableColumn} should be scaled to its content.
     */
    public boolean getScaleToContent() {
        return scaleToContent;
    }

    public static class Builder {

        private static final int DEFAULT_MINIMUM_WIDTH = 5;
        private static final boolean DEFAULT_SCALE_TO_CONTENT = false;

        private final String label;
        private String name;
        private int minimumWidth = DEFAULT_MINIMUM_WIDTH;
        private boolean scaleToContent = DEFAULT_SCALE_TO_CONTENT;

        /**
         * @param label The human readable label of the {@link DataTableColumn}.
         */
        public Builder(String label) {
            this.label = label != null ? label : "";
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
         * This value is ignored if {@link #setScaleToContent(boolean) scaleToContent} is set to <code>true</code>.
         * <p>
         * The default value is {@value #DEFAULT_MINIMUM_WIDTH}.
         *
         * @return This {@link Builder}, for chaining
         * @see #setScaleToContent(boolean)
         */
        public Builder setMinWidth(int minimumWidth) {
            this.minimumWidth = minimumWidth;
            return this;
        }

        /**
         * If set to <code>true</code>, the width of the column will be set so that it exactly fits its longest content.<br>
         * This value may or may not be respected, depending on the implementation.
         * <p>
         * This flag takes precedence over {@link #setMinWidth(int) the minimum width}.
         * <p>
         * The default value is {@value #DEFAULT_SCALE_TO_CONTENT}.
         *
         * @return This {@link Builder}, for chaining
         * @see #setMinWidth(int)
         */
        public Builder setScaleToContent(boolean scaleToContent) {
            this.scaleToContent = scaleToContent;
            return this;
        }

        public DataTableColumn build() {
            return new DataTableColumn(label, name != null ? name : DataRenderingHelper.calculateName(label), minimumWidth,
                    scaleToContent);
        }
    }
}
