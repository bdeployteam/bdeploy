package io.bdeploy.ui.dto;

import java.util.ArrayList;
import java.util.List;

public class InstanceOverallStatusDto {

    public enum OverallStatus {
        RUNNING,
        STOPPED,
        WARNING,
    }

    public String uuid;
    public OverallStatus status;
    public List<String> messages = new ArrayList<>();

}
