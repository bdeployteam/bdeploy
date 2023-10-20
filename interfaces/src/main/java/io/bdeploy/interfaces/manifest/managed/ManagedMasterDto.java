package io.bdeploy.interfaces.manifest.managed;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonAlias;

import io.bdeploy.interfaces.minion.MinionConfiguration;

public class ManagedMasterDto {

    @JsonAlias("name") // old name
    public String hostName;
    public String description;
    public String uri;
    public String auth;
    public Instant lastSync;
    public MinionConfiguration minions;
    public MinionUpdateDto update;
    public MinionProductUpdatesDto productUpdates;

}
