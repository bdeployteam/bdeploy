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
    public ProductDto productDto; // DTO of instanceConfiguration.product

    public Manifest.Key activeProduct;
    public ProductDto activeProductDto;

    public Manifest.Key latestVersion;
    public Manifest.Key activeVersion;

    public boolean newerVersionAvailable;
    public ManagedMasterDto managedServer;
    public CustomAttributesRecord attributes;
    public InstanceBannerRecord banner;
    public InstanceOverallStateRecord overallState;

    public static InstanceDto create(Manifest.Key instance, InstanceConfiguration instanceConfiguration, ProductDto productDto,
            Manifest.Key activeProduct, ProductDto activeProductDto, boolean newerVersionAvailable,
            ManagedMasterDto managedServer, CustomAttributesRecord attributes, InstanceBannerRecord banner,
            Manifest.Key latestVersion, Manifest.Key activeVersion, InstanceOverallStateRecord overallState) {
        InstanceDto dto = new InstanceDto();
        dto.instance = instance;
        dto.instanceConfiguration = instanceConfiguration;
        dto.productDto = productDto;
        dto.activeProduct = activeProduct;
        dto.activeProductDto = activeProductDto;
        dto.newerVersionAvailable = newerVersionAvailable;
        dto.managedServer = managedServer;
        dto.attributes = attributes;
        dto.banner = banner;
        dto.latestVersion = latestVersion;
        dto.activeVersion = activeVersion;
        dto.overallState = overallState;
        return dto;
    }
}
