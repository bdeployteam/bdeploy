package io.bdeploy.ui.dto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HistoryEntryNodeDto {

    public final List<String> added = new ArrayList<>();
    public final List<String> deleted = new ArrayList<>();
    public final Map<String, HistoryEntryApplicationDto> changed = new HashMap<>();
}
