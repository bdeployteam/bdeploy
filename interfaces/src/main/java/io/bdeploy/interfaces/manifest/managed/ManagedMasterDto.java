package io.bdeploy.interfaces.manifest.managed;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonAlias;

import io.bdeploy.interfaces.minion.MinionConfiguration;
import io.bdeploy.interfaces.nodes.NodeListDto;

public class ManagedMasterDto {

    @JsonAlias("name") // old name
    public String hostName;
    public String description;
    public String uri;
    public String auth;
    public Instant lastSync;
    public Instant lastMessageReceived;

    /**
     * @deprecated use {@link #nodes} instead.
     */
    @Deprecated(since = "7.8.0", forRemoval = true)
    public MinionConfiguration minions;

    public NodeListDto nodes;
    public MinionUpdateDto update;
    public MinionProductUpdatesDto productUpdates;

}
