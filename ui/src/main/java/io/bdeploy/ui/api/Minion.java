package io.bdeploy.ui.api;

import java.nio.file.Path;
import java.util.SortedMap;

import javax.inject.Named;

import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.NodeStatus;

/**
 * Represents a master or a slave.
 */
public interface Minion {

    /**
     * {@link Named} injection point for a {@link Boolean} determining whether the current minion hosts a master as well.
     */
    public static final String MASTER = "MASTER";

    /**
     * The default name of the 'master' node. This node is a slave as any other node, but runs th master component as well.
     * <p>
     * The name is just used as a starting point. To determine whether a node is a master, query its {@link NodeStatus} instead of
     * investigating its name.
     */
    public static final String DEFAULT_MASTER_NAME = "master";

    /**
     * Returns the directory where the minion stores temporary files that are served to the client.
     */
    public Path getDownloadDir();

    /**
     * Returns a directory which is suitable to place temporary directories/files within.
     */
    public Path getTempDir();

    /**
     * Retrieve registered slaves. This makes only sense if the current VM hosts a master, otherwise only 'self' is returned.
     */
    public SortedMap<String, RemoteService> getMinions();

    /**
     * Creates and returns a new weak token for the given principal. The weak token
     * is only suitable for fetching fetching by launcher-like applications.
     *
     * @param principal the principal name to issue the token to.
     * @return a "weak" token.
     */
    public String createWeakToken(String principal);

    /**
     * @return the mode the hosting minion is run in.
     */
    public MinionMode getMode();

}
