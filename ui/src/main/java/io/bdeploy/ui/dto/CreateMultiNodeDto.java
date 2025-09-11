package io.bdeploy.ui.dto;

import io.bdeploy.interfaces.minion.MultiNodeDto;

/**
 * A DTO used to create a configuration for a multi-node.
 * This will be created on master so that physical nodes can then connect to it.
 */
public class CreateMultiNodeDto {

    /**
     * The name of the node.
     */
    public String name;

    /**
     * The data required to define a multi-node
     */
    public MultiNodeDto config;

}
