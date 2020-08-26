package io.bdeploy.ui.dto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HistoryEntryParametersDto {

    public final List<String[]> added = new ArrayList<>();
    public final List<String[]> deleted = new ArrayList<>();
    public final Map<String, String[]> changed = new HashMap<>();
}
