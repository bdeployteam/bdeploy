package io.bdeploy.ui.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.bdeploy.bhive.model.Manifest;

public class InstanceUpdateEventDto {

    /**
     * Determines whether the instance has been added, removed or just the state (deploy, process, ...) has to be reloaded.
     */
    public enum InstanceUpdateEventType {
        CREATE,
        REMOVE, // reserved for later
        STATE_CHANGE
    }

    public Manifest.Key key;
    public InstanceUpdateEventType type;

    @JsonCreator
    public InstanceUpdateEventDto(@JsonProperty("key") Manifest.Key key, @JsonProperty("type") InstanceUpdateEventType type) {
        this.key = key;
        this.type = type;
    }

}
