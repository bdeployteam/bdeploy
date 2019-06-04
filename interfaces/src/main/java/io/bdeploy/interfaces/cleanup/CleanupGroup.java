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
     * The list of {@link CleanupAction}s to be performed.
     */
    public List<CleanupAction> actions;

    @JsonCreator
    public CleanupGroup(@JsonProperty("name") String name, @JsonProperty("minion") String minion,
            @JsonProperty("actions") List<CleanupAction> actions) {
        this.name = name;
        this.minion = minion;
        this.actions = actions;
    }

}
