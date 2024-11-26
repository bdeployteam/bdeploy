package io.bdeploy.interfaces.manifest.state;

import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

public class InstanceOverallStateRecord {

    public enum OverallStatus {
        RUNNING,
        STOPPED,
        @JsonEnumDefaultValue
        WARNING,
        INDETERMINATE,
    }

    public OverallStatus status = OverallStatus.WARNING;
    public long timestamp;
    public List<String> messages = Collections.singletonList("Status unknown");

}
