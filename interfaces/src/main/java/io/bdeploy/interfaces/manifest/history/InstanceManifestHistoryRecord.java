package io.bdeploy.interfaces.manifest.history;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.bdeploy.interfaces.manifest.history.InstanceManifestHistory.Action;

/**
 * A single record in the history of an instance.
 */
public class InstanceManifestHistoryRecord {

    public Action action;
    public long timestamp;
    public String user;
    public String comment;

    @JsonCreator
    public InstanceManifestHistoryRecord(@JsonProperty("action") Action action, @JsonProperty("timestamp") long timestamp,
            @JsonProperty("user") String user, @JsonProperty("comment") String comment) {
        this.action = action;
        this.timestamp = timestamp;
        this.user = user;
        this.comment = comment;
    }

}