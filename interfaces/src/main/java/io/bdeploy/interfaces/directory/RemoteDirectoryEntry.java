package io.bdeploy.interfaces.directory;

import com.fasterxml.jackson.annotation.JsonAlias;

import io.bdeploy.interfaces.variables.DeploymentPathProvider.SpecialDirectory;

/**
 * A single remote directory entry (path).
 */
public class RemoteDirectoryEntry {

    /**
     * The path relative to the {@link SpecialDirectory} directory of the hosting instance.
     */
    public String path;

    /**
     * The source {@link SpecialDirectory}.
     * <p>
     * If the root is not given, the path is resolved relative to the minions data root.
     */
    public SpecialDirectory root;

    /**
     * The last modified date of the entry.
     */
    public long lastModified;

    /**
     * The size in bytes of the file backing this entry.
     */
    public long size;

    /**
     * The instance ID this entry belongs to
     */
    @JsonAlias("uuid")
    public String id;

    /**
     * The instance tag this entry belongs to
     */
    public String tag;

}
