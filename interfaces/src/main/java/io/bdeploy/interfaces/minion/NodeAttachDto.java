package io.bdeploy.interfaces.minion;

import io.bdeploy.common.security.RemoteService;

/**
 * A DTO used to attach a new Node to a master. The node's init will produce this file, and it can be dropped
 * to the master UI to pre-fill fields.
 */
public class NodeAttachDto {

    /**
     * The name of the node.
     */
    public String name;

    /**
     * The remote connection information for the node.
     */
    public RemoteService remote;

}
