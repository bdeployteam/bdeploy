package io.bdeploy.interfaces.configuration.pcu;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ProcessProbeResultDto {

    public enum ProcessProbeType {
        STARTUP,
        @JsonAlias("LIFENESS")
        LIVENESS
    }

    public ProcessProbeType type;
    public int status;
    public String message;
    public long time;

    @JsonCreator
    public ProcessProbeResultDto(@JsonProperty("type") ProcessProbeType type, @JsonProperty("status") int status,
            @JsonProperty("message") String message, @JsonProperty("time") long time) {
        this.type = type;
        this.status = status;
        this.message = message;
        this.time = time;
    }
}
