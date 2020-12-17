package io.bdeploy.common.cli.data;

/**
 * A single column in a {@link DataTable}.
 */
public class DataTableColumn {

    private final String name;
    private final String label;
    private final int preferredWidth;

    /**
     * @param label a human readable label for the column. A machine readable name will be calculated by removing spaces and
     *            lower-camel-casing words.
     * @param preferredWidth the preferred width of the column.
     */
    public DataTableColumn(String label, int preferredWidth) {
        this(calculateName(label), label, preferredWidth);
    }

    static String calculateName(String label) {
        final StringBuilder ret = new StringBuilder(label.length());

        for (String word : label.split("[ _-]")) {
            word = word.replaceAll("[^a-zA-Z0-9]", "");
            if (!word.isEmpty()) {
                ret.append(Character.toUpperCase(word.charAt(0)));
                ret.append(word.substring(1).toLowerCase());
            }
        }

        return ret.toString();
    }

    /**
     * @param name the machine readable name of the column
     * @param label the human readable label of the column
     * @param preferredWidth the preferred width of the column in characters. The preferredWidth is at least the length of the
     *            label.
     */
    public DataTableColumn(String name, String label, int preferredWidth) {
        this.name = name;
        this.label = label;
        this.preferredWidth = Math.max(this.label.length(), preferredWidth);
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
     * @return the preferred width in characters
     */
    public int getPreferredWidth() {
        return preferredWidth;
    }

}
