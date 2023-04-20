package io.bdeploy.ui.dto;

import io.bdeploy.jersey.ws.change.msg.ObjectChangeDto;

/**
 * Potential detail fields in {@link ObjectChangeDto}.
 */
public enum ObjectChangeDetails {
    KEY_NAME,
    KEY_TAG,
    CHANGE_HINT,
    ACTIVITIES,
    USER_NAME,
    USER_GROUP_ID,
    ID,
    NODE,
}
