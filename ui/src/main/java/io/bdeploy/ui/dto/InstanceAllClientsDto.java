package io.bdeploy.ui.dto;

import java.util.ArrayList;
import java.util.List;

public class InstanceAllClientsDto {

    public LauncherDto launchers;
    public List<InstanceClientAppsDto> clients = new ArrayList<>();
    public List<InstanceUiEndpointsDto> endpoints = new ArrayList<>();

}
