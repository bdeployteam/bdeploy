package io.bdeploy.interfaces.manifest.managed;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAlias;

import io.bdeploy.interfaces.minion.MinionDto;

public class ManagedMasterDto {

    @JsonAlias("name") // old name
    public String hostName;
    public String description;
    public String uri;
    public String auth;
    public Instant lastSync;
    public Map<String, MinionDto> minions;
    public MinionUpdateDto update;

}
