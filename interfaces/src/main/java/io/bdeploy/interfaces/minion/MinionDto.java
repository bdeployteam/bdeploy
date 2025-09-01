package io.bdeploy.interfaces.minion;

import io.bdeploy.common.Version;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.OsHelper;
import io.bdeploy.common.util.OsHelper.OperatingSystem;
import io.bdeploy.common.util.VersionHelper;

/**
 * Describes a single minion
 */
public class MinionDto {

    public enum MinionNodeType {
        UNKNOWN,
        SERVER,
        MULTI
    }

    /**
     * Indicates whether or not this is the master node.
     */
    public boolean master;

    /**
     * The operating system of the minion
     */
    public OperatingSystem os;

    /**
     * The version of the minion
     */
    public Version version;

    /**
     * Where is the minion running and the certificate
     */
    public RemoteService remote;

    /**
     * The type of node that is attached to this minion
     */
    public MinionNodeType minionNodeType = MinionNodeType.UNKNOWN;

    /**
     * Creates and returns a new minion DTO using the given remote. The OS and the
     * version is taken from the currently running VM.
     *
     * @param isMaster whether or not this is a master minion
     * @param remote the remote service
     * @param minionNodeType the {@link MinionNodeType} of this node
     */
    public static MinionDto create(boolean isMaster, RemoteService remote, MinionNodeType minionNodeType) {
        MinionDto dto = new MinionDto();
        dto.master = isMaster;
        dto.remote = remote;
        dto.version = VersionHelper.getVersion();
        dto.os = OsHelper.getRunningOs();
        dto.minionNodeType = minionNodeType;
        return dto;
    }
}
