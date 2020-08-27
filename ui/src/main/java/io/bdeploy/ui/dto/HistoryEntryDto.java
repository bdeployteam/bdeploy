package io.bdeploy.ui.dto;

public class HistoryEntryDto {

    public final long timestamp;
    public final int version;

    public String title = "";
    public HistoryEntryType type;
    public String user = "";
    public String email = "";
    public HistoryEntryVersionDto content = null;
    public HistoryEntryRuntimeDto runtimeEvent = null;

    public HistoryEntryDto(long timestamp, int version) {
        this.timestamp = timestamp;
        this.version = version;
    }

    public enum HistoryEntryType {
        CREATE,
        DEPLOYMENT,
        RUNTIME
    }
}
