package io.bdeploy.ui.dto;

import java.util.ArrayList;
import java.util.List;

import io.bdeploy.interfaces.manifest.managed.ManagedMasterDto;

public class MinionSyncResultDto {

    public ManagedMasterDto server;

    public List<InstanceOverallStatusDto> states = new ArrayList<>();

}
