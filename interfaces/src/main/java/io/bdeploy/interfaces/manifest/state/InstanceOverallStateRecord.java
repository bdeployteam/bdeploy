package io.bdeploy.interfaces.manifest.state;

import java.util.Collections;
import java.util.List;

public class InstanceOverallStateRecord {

    public enum OverallStatus {
        RUNNING,
        STOPPED,
        WARNING,
    }

    public OverallStatus status = OverallStatus.WARNING;
    public long timestamp;
    public List<String> messages = Collections.singletonList("Status unknown");

}
