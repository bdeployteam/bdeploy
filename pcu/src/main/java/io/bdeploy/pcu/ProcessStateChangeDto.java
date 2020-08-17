package io.bdeploy.pcu;

import io.bdeploy.interfaces.configuration.pcu.ProcessState;

public class ProcessStateChangeDto {

    public ProcessState state;
    public String user;
    public String pid;

    public ProcessStateChangeDto(ProcessState state, String user, String pid) {
        this.state = state;
        this.user = user;
        this.pid = pid;
    }
}
