package io.bdeploy.ui.dto;

import io.bdeploy.jersey.ws.change.msg.ObjectChangeDto;

/**
 * The type of object changed in a certain {@link ObjectChangeDto}.
 * <p>
 * The 'Bridged' comment means that 'create' for this type is handled using the generic ManifestSpawnToChangeEventBridge instead
 * of hand-crafted code.
 */
public enum ObjectChangeType {
    ACTIVITIES,
    INSTANCE_GROUP,         // Bridged
    PRODUCT,                // Bridged
    SOFTWARE_REPO,          // Bridged
    SOFTWARE_PACKAGE,
    INSTANCE,               // Bridged
    MANAGED_MASTER_ATTACH,
    USER,
    PLUGIN,
    NODES,                  // Bridged
}
