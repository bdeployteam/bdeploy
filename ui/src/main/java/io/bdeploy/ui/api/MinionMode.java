package io.bdeploy.ui.api;

/**
 * The mode of the minion, respective any tool which requires a minion root.
 */
public enum MinionMode {

    /**
     * Standalone means that all operations are regarded 'local', meaning the running process is the master.
     */
    STANDALONE,

    /**
     * In local mode, a minion requires to be connected to a central minion. All operations are regarded 'local', same as
     * standalone, but certain operations are not allowed.
     */
    LOCAL,

    /**
     * In central mode, a minion will not host master/slave services, but can only remotely control 'local' minions.
     */
    CENTRAL

}
