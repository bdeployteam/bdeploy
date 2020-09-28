package io.bdeploy.ui.dto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HistoryEntryApplicationDto {

    public Map<String, String[]> properties = new HashMap<>();
    public Map<String, String[]> processControlProperties = new HashMap<>();

    public HistoryEntryParametersDto parameters;

    public List<String> addedEndpoints = new ArrayList<>();
    public List<String> deletedEndpoints = new ArrayList<>();
    public Map<String, HistoryEntryHttpEndpointDto> changedEndpoints = new HashMap<>();
}
