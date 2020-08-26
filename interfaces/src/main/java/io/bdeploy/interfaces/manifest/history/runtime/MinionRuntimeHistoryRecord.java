package io.bdeploy.interfaces.manifest.history.runtime;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.bdeploy.interfaces.configuration.pcu.ProcessState;

public class MinionRuntimeHistoryRecord {

    public final long pid;
    public final int exitCode;
    public final ProcessState state;
    public final String user;
    public final long timestamp;

    @JsonCreator
    public MinionRuntimeHistoryRecord(@JsonProperty("pid") long pid, @JsonProperty("exitCode") int exitCode,
            @JsonProperty("state") ProcessState state, @JsonProperty("user") String user,
            @JsonProperty("timestamp") long timestamp) {
        this.state = state;
        this.exitCode = exitCode;
        this.timestamp = timestamp;
        this.pid = pid;
        this.user = user;
    }
}
