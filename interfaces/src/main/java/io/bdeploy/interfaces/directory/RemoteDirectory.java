package io.bdeploy.interfaces.directory;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a snapshot of an instances DATA directory on a minion.
 */
public class RemoteDirectory {

    /**
     * Name of the minion this directory is residing on.
     */
    public String minion;

    /**
     * ID of the instance this directory belongs to.
     */
    @JsonAlias("uuid")
    public String id;

    /**
     * @deprecated Compat with 4.x
     */
    @Deprecated(forRemoval = true)
    @JsonProperty("uuid")
    public String getUuid() {
        return id;
    }

    /**
     * A description of any problem that happened contacting the minion. <code>null</code> if no problem.
     */
    public String problem;

    /**
     * All directory entries.
     */
    public List<RemoteDirectoryEntry> entries = new ArrayList<>();

}
