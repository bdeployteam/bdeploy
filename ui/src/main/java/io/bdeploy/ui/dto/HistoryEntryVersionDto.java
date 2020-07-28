package io.bdeploy.ui.dto;

import java.util.HashMap;
import java.util.Map;

public class HistoryEntryVersionDto {

    public Map<String, String[]> properties = new HashMap<>();
    public Map<String, HistoryEntryNodeDto> nodes = new HashMap<>();
    public HistoryEntryConfigFilesDto configFiles;
}
