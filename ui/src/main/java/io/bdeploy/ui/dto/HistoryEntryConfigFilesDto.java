package io.bdeploy.ui.dto;

import java.util.ArrayList;
import java.util.List;

public class HistoryEntryConfigFilesDto {

    public List<String> added = new ArrayList<>();
    public List<String> deleted = new ArrayList<>();
    public List<String> changed = new ArrayList<>();
}
