package io.bdeploy.ui.dto;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration;
import io.bdeploy.interfaces.manifest.attributes.CustomAttributesRecord;
import io.bdeploy.interfaces.manifest.banner.InstanceBannerRecord;
import io.bdeploy.interfaces.manifest.managed.ManagedMasterDto;
import io.bdeploy.interfaces.manifest.state.InstanceOverallStateRecord;

public class InstanceDto {

    public Manifest.Key instance;

    public InstanceConfiguration instanceConfiguration;

    public Manifest.Key activeProduct;

    public Manifest.Key latestVersion;
    public Manifest.Key activeVersion;

    public ManagedMasterDto managedServer;
    public CustomAttributesRecord attributes;
    public InstanceBannerRecord banner;
    public InstanceOverallStateRecord overallState;

    public ConfigDirDto configRoot;

    public static InstanceDto create(Manifest.Key instance, InstanceConfiguration instanceConfiguration,
            Manifest.Key activeProduct, ManagedMasterDto managedServer, CustomAttributesRecord attributes,
            InstanceBannerRecord banner, Manifest.Key latestVersion, Manifest.Key activeVersion,
            InstanceOverallStateRecord overallState, ConfigDirDto configRoot) {
        InstanceDto dto = new InstanceDto();
        dto.instance = instance;
        dto.instanceConfiguration = instanceConfiguration;
        dto.activeProduct = activeProduct;
        dto.managedServer = managedServer;
        dto.attributes = attributes;
        dto.banner = banner;
        dto.latestVersion = latestVersion;
        dto.activeVersion = activeVersion;
        dto.overallState = overallState;
        dto.configRoot = configRoot;
        return dto;
    }
}
