package io.bdeploy.ui.dto;

import io.bdeploy.common.security.RemoteService;
import io.bdeploy.ui.api.MinionMode;

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
     * The source mode - in case this is NOT *NODE*, we need to perform a migration.
     */
    public MinionMode sourceMode = MinionMode.NODE;

    /**
     * The remote connection information for the node.
     */
    public RemoteService remote;

}
