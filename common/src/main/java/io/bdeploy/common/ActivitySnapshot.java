package io.bdeploy.common;

import java.util.List;

import javax.ws.rs.core.GenericType;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A serializable snapshot of an activity on the server.
 */
public final class ActivitySnapshot {

    public static final GenericType<List<ActivitySnapshot>> LIST_TYPE = new GenericType<List<ActivitySnapshot>>() {
    };

    public final String uuid;
    public final String name;
    public final List<String> scope;
    public final long duration;
    public final long max;
    public final long current;
    public final boolean cancel;
    public final String parentUuid;
    public final String user;

    @JsonCreator
    public ActivitySnapshot(@JsonProperty("uuid") String uuid, @JsonProperty("name") String name,
            @JsonProperty("duration") long duration, @JsonProperty("max") long max, @JsonProperty("current") long current,
            @JsonProperty("scope") List<String> scope, @JsonProperty("cancel") boolean cancel,
            @JsonProperty("parentUuid") String parentUuid, @JsonProperty("user") String user) {
        this.uuid = uuid;
        this.name = name + (cancel ? " (cancel requested)" : "");
        this.duration = duration;
        this.max = max;
        this.current = current;
        this.scope = scope;
        this.cancel = cancel;
        this.parentUuid = parentUuid;
        this.user = user;
    }

    @Override
    public String toString() {
        return String.format("[%1$08d] %2$-70s %3$8d/%4$8d", duration, name, current, max);
    }

}