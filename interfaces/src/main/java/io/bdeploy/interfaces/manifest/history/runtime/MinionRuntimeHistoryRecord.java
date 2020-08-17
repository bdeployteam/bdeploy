package io.bdeploy.interfaces.manifest.history.runtime;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.bdeploy.interfaces.configuration.pcu.ProcessState;

public class MinionRuntimeHistoryRecord {

    public String pid;
    public ProcessState state;
    public String user;
    public long timestamp;

    @JsonCreator
    public MinionRuntimeHistoryRecord(@JsonProperty("pid") String pid, @JsonProperty("state") ProcessState state,
            @JsonProperty("user") String user, @JsonProperty("timestamp") long timestamp) {
        this.state = state;
        this.timestamp = timestamp;
        this.pid = pid;
        this.user = user;
    }
}
