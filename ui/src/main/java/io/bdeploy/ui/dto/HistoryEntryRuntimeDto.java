package io.bdeploy.ui.dto;

import io.bdeploy.interfaces.configuration.pcu.ProcessState;

public class HistoryEntryRuntimeDto {

    public String node;
    public String PID;
    public ProcessState state;

    public HistoryEntryRuntimeDto(String node, String PID, ProcessState state) {
        this.node = node;
        this.PID = PID;
        this.state = state;
    }

}
