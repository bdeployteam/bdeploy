package io.bdeploy.ui.dto;

import io.bdeploy.jersey.ws.change.msg.ObjectChangeDto;

/**
 * The type of object changed in a certain {@link ObjectChangeDto}.
 */
public enum ObjectChangeType {
    ACTIVITIES,
    INSTANCE_GROUP,
    PRODUCT,
    SOFTWARE_REPO,
    SOFTWARE_PACKAGE,
    INSTANCE,
    MANAGED_MASTER_ATTACH,
    USER,
    PLUGIN
}
