package io.bdeploy.ui.dto.ports;

import io.bdeploy.interfaces.configuration.pcu.ProcessState;

public class PortStateDto {

    public String serverNode;
    public boolean isUsed;
    // this value is duplicated in each portState (each process can have multiple ports),
    // but this is here for convenience; this is 2 levels down and here we use it
    public ProcessState processState;
    public String runningTag;

    public boolean getRating() {
        // if running, it should be open.
        if (processState.isRunning()) {
            return isUsed;
        }

        // otherwise it should not be open.
        return !isUsed;
    }

}
