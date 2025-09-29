package io.bdeploy.interfaces.minion;

import java.net.URI;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;

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
        @JsonEnumDefaultValue
        SERVER,
        MULTI,
        MULTI_RUNTIME,
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
    public MinionNodeType minionNodeType = MinionNodeType.SERVER;

    /**
     * Creates and returns a new minion DTO using the given remote. The OS and the
     * version is taken from the currently running VM.
     *
     * @param isMaster whether or not this is a master minion
     * @param remote the remote service
     */
    public static MinionDto createServerNode(boolean isMaster, RemoteService remote) {
        MinionDto dto = new MinionDto();
        dto.master = isMaster;
        dto.remote = remote;
        dto.version = VersionHelper.getVersion();
        dto.os = OsHelper.getRunningOs();
        dto.minionNodeType = MinionNodeType.SERVER;
        return dto;
    }

    /**
     * Creates and returns a new minion DTO, that represent the configuration of multi-node. This action can only be done from the
     * master node.
     *
     * @param operatingSystem the expected operating system of the physical nodes
     */
    public static MinionDto createMultiNode(OperatingSystem operatingSystem) {
        MinionDto dto = new MinionDto();
        dto.master = false;
        dto.version = VersionHelper.getVersion();
        dto.os = operatingSystem;
        dto.minionNodeType = MinionNodeType.MULTI;
        return dto;
    }

    /**
     * Creates and returns a new minion DTO, that represent the runtime part of a multi-node.
     *
     * @param remote the remote where this node is reachable
     */
    public static MinionDto createMultiNodeRuntime(RemoteService remote) {
        MinionDto dto = new MinionDto();
        dto.master = false;
        dto.remote = remote;
        dto.version = VersionHelper.getVersion();
        dto.os = OsHelper.getRunningOs();
        dto.minionNodeType = MinionNodeType.MULTI_RUNTIME;
        return dto;
    }

    public URI getUriIfDefined() {
        return remote == null ? null : remote.getUri();
    }

    public void clearAuthInformation() {
        remote = new RemoteService(getUriIfDefined());
    }
}
