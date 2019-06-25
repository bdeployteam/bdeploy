package io.bdeploy.interfaces.directory;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a snapshot of an instances DATA directory on a minion.
 */
public class InstanceDirectory {

    /**
     * Name of the minion this directory is residing on.
     */
    public String minion;

    /**
     * UUID of the instance this directory belongs to.
     */
    public String uuid;

    /**
     * A description of any problem that happened contacting the minion. <code>null</code> if no problem.
     */
    public String problem;

    /**
     * All directory entries.
     */
    public List<InstanceDirectoryEntry> entries = new ArrayList<>();

}
