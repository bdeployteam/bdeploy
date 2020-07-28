package io.bdeploy.ui.dto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HistoryEntryNodeDto {

    public List<String> added = new ArrayList<>();
    public List<String> deleted = new ArrayList<>();
    public Map<String, HistoryEntryApplicationDto> changed = new HashMap<>();
}
