package io.bdeploy.ui.api;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

/**
 * The mode of the minion, respective any tool which requires a minion root.
 */
public enum MinionMode {

    /**
     * Standalone means that all operations are regarded 'local', meaning the
     * running process is the master.
     */
    @JsonEnumDefaultValue
    STANDALONE,

    /**
     * In managed mode, a minion requires to be connected to a central minion. All
     * operations are regarded 'local', same as standalone, but certain operations
     * are not allowed.
     */
    MANAGED,

    /**
     * In central mode, a minion will not host node services, but can only remotely
     * control 'managed' master nodes.
     */
    CENTRAL,

    /**
     * The minion is a non-master node - the node must be attached and is controlled
     * by a master node (managed or standalone).
     */
    NODE,

}
