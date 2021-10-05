package io.bdeploy.jersey.ws.change.msg;

import java.util.Collections;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a change in an object
 */
public class ObjectChangeDto {

    /**
     * The type of the object.
     */
    public String type;

    /**
     * The scope of the object.
     */
    public ObjectScope scope;

    /**
     * The event which happened.
     */
    public ObjectEvent event;

    /**
     * Type and event specific details about the event.
     */
    public Map<String, String> details;

    @JsonCreator
    public ObjectChangeDto(@JsonProperty("type") String type, @JsonProperty("scope") ObjectScope scope,
            @JsonProperty("event") ObjectEvent event, @JsonProperty("details") Map<String, String> details) {
        this.type = type;
        this.scope = scope == null ? ObjectScope.EMPTY : scope;
        this.event = event;
        this.details = details == null ? Collections.emptyMap() : details;
    }

    @Override
    public String toString() {
        return "ObjectChange { type=" + type + ", event=" + event + ", scope=" + scope + ", details=" + details + " }";
    }

}
