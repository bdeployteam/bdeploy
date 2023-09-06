package io.bdeploy.common.actions;

/**
 * Defines all server-request-based actions a user can perform. This is used to
 * keep track of actions to avoid duplicate execution of certain actions, and
 * assign permissions to actions (later?).
 */
public enum Actions {

    /// ---- instances
    CREATE_INSTANCE_VERSION(ActionScope.INSTANCE, true, "Create Instance Version"),
    DELETE_INSTANCE_VERSION(ActionScope.VERSION, true, "Delete Instance Version"),

    DELETE_INSTANCE(ActionScope.INSTANCE, true, "Delete Instance"),

    INSTALL(ActionScope.VERSION, true, "Install Instance Version"),
    UNINSTALL(ActionScope.VERSION, true, "Uninstall Instance Version"),
    ACTIVATE(ActionScope.VERSION, true, "Activate Instance Version"),

    START_INSTANCE(ActionScope.INSTANCE, true, "Start Instance"),
    STOP_INSTANCE(ActionScope.INSTANCE, true, "Stop Instance"),

    UPDATE_OVERALL_STATUS(ActionScope.BHIVE, false, "Update Overall Instance Status"),
    UPDATE_PRODUCT_VERSION(ActionScope.INSTANCE, true, "Update Product Version"),

    DOWNLOAD_CLIENT_INSTALLER(ActionScope.PROCESS, false, "Prepare Installer Download"),

    /// ---- group/repo content
    DOWNLOAD_PRODUCT_H(ActionScope.BHIVE, false, "Prepare Product Download"),
    DOWNLOAD_SOFTWARE_H(ActionScope.BHIVE, false, "Prepare Software Download"),

    DOWNLOAD_PRODUCT_C(ActionScope.BHIVE, true, "Prepare Raw Product Download"),
    DOWNLOAD_SOFTWARE_C(ActionScope.BHIVE, true, "Prepare Raw Software Download"),

    DELETE_PRODUCT(ActionScope.BHIVE, true, "Delete Product Version"),
    DELETE_SOFTWARE(ActionScope.BHIVE, true, "Delete Software"),

    IMPORT_PROD_REPO(ActionScope.BHIVE, true, "Import Product from Software Repository"),

    DELETE_GROUP(ActionScope.BHIVE, true, "Delete Instance Group"),

    /// ---- data/files
    READ_DATA_DIRS(ActionScope.INSTANCE, false, "Read Data Directories"),

    /// ---- processes
    START_PROCESS(ActionScope.PROCESS, true, "Start Process"),
    STOP_PROCESS(ActionScope.PROCESS, true, "Stop Process"),

    READ_PROCESS_STATUS(ActionScope.PROCESS, false, "Read Process Status"),

    /// ---- low level
    FSCK_BHIVE(ActionScope.BHIVE, true, "Consistency Check"),
    PRUNE_BHIVE(ActionScope.BHIVE, true, "Prune Objects"),

    /// ---- central/managed
    MANAGED_UPDATE_TRANSFER(ActionScope.BHIVE, true, "Transfer Update to Server"),
    MANAGED_UPDATE_INSTALL(ActionScope.BHIVE, true, "Install Update on Server"),
    TRANSFER_PRODUCT_CENTRAL(ActionScope.BHIVE, true, "Transfer Product to Central"),
    TRANSFER_PRODUCT_MANAGED(ActionScope.BHIVE, true, "Transfer Product to Managed"),
    REMOVE_MANAGED(ActionScope.BHIVE, true, "Remove Managed Server"),

    /// ---- server/node
    ADD_NODE(ActionScope.GLOBAL, true, "Add Node"),
    EDIT_NODE(ActionScope.GLOBAL, true, "Edit Node"),
    REMOVE_NODE(ActionScope.GLOBAL, true, "Remove Node"),
    CONVERT_TO_NODE(ActionScope.GLOBAL, true, "Convert existing Server to Node"),
    REPLACE_NODE(ActionScope.GLOBAL, true, "Replace Node"),

    FSCK_NODE(ActionScope.GLOBAL, true, "Consistency Check on Node"),
    PRUNE_NODE(ActionScope.GLOBAL, true, "Prune on Node"),
    UPDATE_NODE(ActionScope.GLOBAL, true, "Install Update on Node"),

    SYNCHRONIZING(ActionScope.BHIVE, true, "Synchronize Server"),
    DELETE_UPDATES(ActionScope.GLOBAL, true, "Delete System Software"),

    DOWNLOAD_LAUNCHER(ActionScope.GLOBAL, false, "Prepare Launcher Download"),

    CLEANUP_CALCULATE(ActionScope.GLOBAL, false, "Calculate Cleanup"),
    CLEANUP_PERFORM(ActionScope.GLOBAL, true, "Performing Cleanup"),

    LDAP_SYNC(ActionScope.GLOBAL, true, "Synchronizing LDAP Server"),

    STARTING_SERVER(ActionScope.GLOBAL, true, "Starting Up..."),
    RESTART_SERVER(ActionScope.GLOBAL, true, "Restarting Server..."),
    UPDATE(ActionScope.GLOBAL, true, "Installing Server Update...");

    private final ActionScope scope;
    private final boolean exclusive;
    private final String description;

    // TODO: add defaultPermission which can be later be overridden per user?
    private Actions(ActionScope scope, boolean exclusive, String description) {
        this.scope = scope;
        this.exclusive = exclusive;
        this.description = description;
    }

    public ActionScope getScope() {
        return scope;
    }

    public boolean isExclusive() {
        return exclusive;
    }

    public String getDescription() {
        return description;
    }

}
