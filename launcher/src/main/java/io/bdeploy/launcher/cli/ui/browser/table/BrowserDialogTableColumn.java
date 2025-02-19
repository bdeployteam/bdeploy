package io.bdeploy.launcher.cli.ui.browser.table;

import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration.InstancePurpose;

/**
 * Each value represents a columns of the browser dialog table. The order of the values is relevant because
 * {@link BrowserDialogTableColumn#ordinal() #ordinal()} is used to determine the index of the column in the table.
 */
public enum BrowserDialogTableColumn {

    APP(String.class, "Application",//
            "The name of the client application"),
    IG(String.class, "Instance Group",//
            "The name of the instance group on the BDeploy minion"),
    INSTANCE(String.class, "Instance",//
            "The name of the instance on the BDeploy minion"),
    PURPOSE(InstancePurpose.class, "Purpose",//
            "The purpose of this application"),
    PRODUCT(String.class, "Product V.",//
            "The product version number of the instance on the remote BDeploy server"),
    REMOTE(String.class, "Remote",//
            "The URL to the BDeploy minion"),
    SERVER_VERSION(String.class, "Server Version",//
            "The version number of the BDeploy minion"),
    AUTOSTART(Boolean.class, "Auto.",//
            "Whether the application will be launched whenever the underlying OS is started"),
    START_SCRIPT(String.class, "Start Script",//
            "The name of the start script of this application"),
    FILE_ASSOC_EXTENSION(String.class, "File Association",//
            "The file ending that this application is associated with"),
    OFFLINE_LAUNCHABLE(Boolean.class, "Offl.",//
            "Whether this application supports launching without contacting the BDeploy minion first");

    public final Class<?> columnClass;
    public final String columnName;
    public final String columnHint;

    private BrowserDialogTableColumn(Class<?> columnClass, String columnName, String columnHint) {
        this.columnClass = columnClass;
        this.columnName = columnName;
        this.columnHint = columnHint;
    }

    public static BrowserDialogTableColumn fromIndex(int i) {
        return BrowserDialogTableColumn.values()[i];
    }
}
