package io.bdeploy.launcher.cli.ui.browser.table;

import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration.InstancePurpose;

/**
 * Each value represents a columns of the browser dialog table. The order of the values is relevant because
 * {@link BrowserDialogTableColumn#ordinal() #ordinal()} is used to determine the index of the column in the table.
 */
public enum BrowserDialogTableColumn {

    APP("Application", String.class),
    IG("Instance Group", String.class),
    INSTANCE("Instance", String.class),
    PURPOSE("Purpose", InstancePurpose.class),
    PRODUCT("Product", String.class),
    REMOTE("Remote", String.class),
    SERVER_VERSION("Server Version", String.class),
    AUTOSTART("Autostart", Boolean.class),
    START_SCRIPT("Start Script", String.class),
    FILE_ASSOC_EXTENSION("File Association", String.class);

    private final String columnName;
    private final Class<?> columnClass;

    private BrowserDialogTableColumn(String columnName, Class<?> columnClass) {
        this.columnName = columnName;
        this.columnClass = columnClass;
    }

    public String getColumnName() {
        return columnName;
    }

    public Class<?> getColumnClass() {
        return columnClass;
    }

    public static BrowserDialogTableColumn fromIndex(int i) {
        return BrowserDialogTableColumn.values()[i];
    }
}
