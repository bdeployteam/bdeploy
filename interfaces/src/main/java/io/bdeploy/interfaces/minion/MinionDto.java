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

    /**
     * Indicates whether or not this is the master node.
     */
    public boolean master;

    /**
     * The operating system of the OS
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
     * Creates and returns a new minion DTO using the given remote.
     * The OS and the version is taken from the currently running VM.
     *
     * @param isMaster
     *            whether or not this is a master minion
     * @param remote the remote service
     */
    public static MinionDto create(boolean isMaster, RemoteService remote) {
        MinionDto dto = new MinionDto();
        dto.master = isMaster;
        dto.remote = remote;
        dto.version = VersionHelper.tryParse(VersionHelper.readVersion());
        dto.os = OsHelper.getRunningOs();
        return dto;
    }

}
