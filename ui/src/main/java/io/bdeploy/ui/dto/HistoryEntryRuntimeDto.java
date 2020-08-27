package io.bdeploy.ui.dto;

import io.bdeploy.interfaces.configuration.pcu.ProcessState;

public class HistoryEntryRuntimeDto {

    public final String node;
    public final long pid;
    public final int exitCode;
    public final ProcessState state;

    public HistoryEntryRuntimeDto(String node, long pid, int exitCode, ProcessState state) {
        this.node = node;
        this.pid = pid;
        this.exitCode = exitCode;
        this.state = state;
    }

}
