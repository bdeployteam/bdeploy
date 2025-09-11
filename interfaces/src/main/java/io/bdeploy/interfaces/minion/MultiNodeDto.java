package io.bdeploy.interfaces.minion;

import static io.bdeploy.common.util.OsHelper.OperatingSystem;

/**
 * A DTO that holds the required data to configure a multi-node.
 */
public class MultiNodeDto {

    /**
     * The operating system we are expecting on the machines that are part of this node
     */
    public OperatingSystem operatingSystem;

}
