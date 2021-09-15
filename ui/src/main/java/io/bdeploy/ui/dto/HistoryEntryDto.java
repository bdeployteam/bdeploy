package io.bdeploy.ui.dto;

public class HistoryEntryDto {

    public final long timestamp;
    public String instanceTag;

    public String title = "";
    public HistoryEntryType type;
    public String user = "";
    public String email = "";
    public HistoryEntryRuntimeDto runtimeEvent = null;

    public HistoryEntryDto(long timestamp, String instanceTag) {
        this.timestamp = timestamp;
        this.instanceTag = instanceTag;
    }

    public enum HistoryEntryType {
        CREATE,
        DEPLOYMENT,
        RUNTIME
    }
}
