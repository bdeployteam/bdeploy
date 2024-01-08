package io.bdeploy.ui.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.bdeploy.interfaces.manifest.state.InstanceOverallStateRecord;
import io.bdeploy.interfaces.manifest.state.InstanceOverallStateRecord.OverallStatus;

public class InstanceOverallStatusDto {

    @JsonAlias("uuid")
    public String id;

    public OverallStatus status;
    public long timestamp;
    public List<String> messages;

    public InstanceOverallStatusDto(String id, InstanceOverallStateRecord rec) {
        this.id = id;
        this.status = rec.status;
        this.timestamp = rec.timestamp;
        this.messages = rec.messages;
    }

    @JsonCreator
    public InstanceOverallStatusDto(@JsonProperty("id") String id, @JsonProperty("status") OverallStatus status,
            @JsonProperty("timestamp") long timestamp, @JsonProperty("messages") List<String> messages) {

        this.id = id;
        this.status = status;
        this.timestamp = timestamp;
        this.messages = messages;
    }

}
