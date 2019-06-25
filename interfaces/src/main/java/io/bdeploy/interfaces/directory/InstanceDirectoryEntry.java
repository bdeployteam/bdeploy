package io.bdeploy.interfaces.directory;

import io.bdeploy.interfaces.variables.DeploymentPathProvider.SpecialDirectory;

/**
 * A single entry in an instance {@link SpecialDirectory}.
 */
public class InstanceDirectoryEntry {

    /**
     * The path relative to the {@link SpecialDirectory} directory of the hosting instance.
     */
    public String path;

    /**
     * The source {@link SpecialDirectory}.
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
     * The instance UUID this entry belongs to
     */
    public String uuid;

    /**
     * The instance tag this entry belongs to
     */
    public String tag;

}
