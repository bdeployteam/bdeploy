package io.bdeploy.ui.dto;

import io.bdeploy.interfaces.configuration.pcu.ProcessState;

public class HistoryEntryRuntimeDto {

    public String node;
    public String pid;
    public ProcessState state;

    public HistoryEntryRuntimeDto(String node, String pid, ProcessState state) {
        this.node = node;
        this.pid = pid;
        this.state = state;
    }

}
