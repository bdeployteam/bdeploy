package io.bdeploy.interfaces.cleanup;

import java.nio.file.Path;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;

/**
 * Represents a single action to be performed during cleanup.
 */
public class CleanupAction {

    public enum CleanupType {
        DELETE_MANIFEST,
        DELETE_FOLDER
    }

    /**
     * The type of cleanup to perform.
     */
    public CleanupType type;

    /**
     * What to operate on. Might be a representation of a {@link Manifest} {@link Key} or a {@link Path} depending on the
     * {@link #type}.
     */
    public String what;

    /**
     * Human readable additional description to tell the user what is happening.
     */
    public String description;

    @JsonCreator
    public CleanupAction(@JsonProperty("type") CleanupType type, @JsonProperty("what") String what,
            @JsonProperty("description") String description) {
        this.type = type;
        this.what = what;
        this.description = description;
    }

    @Override
    public String toString() {
        return type.name() + " " + what;
    }

}
