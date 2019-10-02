package io.bdeploy.interfaces.cleanup;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Groups {@link CleanupAction} under a certain name
 */
public class CleanupGroup {

    /**
     * A "readable" name of the group
     */
    public String name;

    /**
     * The minion name (as registered on the master) this group is 'bound' to (e.g. should be performed on).
     */
    public String minion;

    /**
     * The instance group name this group is 'bound' to (e.g. should be performed on).
     */
    public String instanceGroup;

    /**
     * The list of {@link CleanupAction}s to be performed.
     */
    public List<CleanupAction> actions;

    @JsonCreator
    public CleanupGroup(@JsonProperty("name") String name, @JsonProperty("minion") String minion,
            @JsonProperty("instanceGroup") String instanceGroup, @JsonProperty("actions") List<CleanupAction> actions) {
        this.name = name;
        this.minion = minion;
        this.instanceGroup = instanceGroup;
        this.actions = actions;

        if (minion == null && instanceGroup == null) {
            throw new IllegalStateException("Either minion or instance group must not be null");
        }
    }

}
