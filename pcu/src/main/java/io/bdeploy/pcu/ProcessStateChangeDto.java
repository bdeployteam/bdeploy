package io.bdeploy.pcu;

import io.bdeploy.interfaces.configuration.pcu.ProcessState;

/**
 * Event send out by the {@linkplain ProcessController} when the state of an process changes.
 */
public class ProcessStateChangeDto {

    public final ProcessState newState;
    public final ProcessState oldState;
    public final String user;

    public ProcessStateChangeDto(ProcessState newState, ProcessState oldState, String user) {
        this.newState = newState;
        this.oldState = oldState;
        this.user = user;
    }
}
