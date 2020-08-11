package io.bdeploy.ui.dto;

public class HistoryEntryDto {

    public String title = "";
    public int version;
    public String type = "";
    public String user = "";
    public String email = "";
    public HistoryEntryVersionDto content = null;
    public HistoryEntryRuntimeDto runtimeEvent = null;
    public long timestamp;

    public HistoryEntryDto(long timestamp, int version) {
        this.timestamp = timestamp;
        this.version = version;
    }
}
