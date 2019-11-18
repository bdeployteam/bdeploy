package io.bdeploy.ui.dto;

import java.time.Instant;

import io.bdeploy.interfaces.minion.MinionConfiguration;

public class ManagedMasterDto {

    public String hostName;
    public String description;
    public String uri;
    public String auth;
    public Instant lastSync;
    public MinionConfiguration minions;

}
