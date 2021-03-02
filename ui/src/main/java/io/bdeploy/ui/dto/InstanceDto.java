package io.bdeploy.ui.dto;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration;
import io.bdeploy.interfaces.manifest.managed.ManagedMasterDto;

public class InstanceDto {

    public InstanceConfiguration instanceConfiguration;
    public ProductDto productDto; // DTO of instanceConfiguration.product

    public Manifest.Key activeProduct;
    public ProductDto activeProductDto;

    public boolean newerVersionAvailable;
    public ManagedMasterDto managedServer;

    public static InstanceDto create(InstanceConfiguration instanceConfiguration, ProductDto productDto,
            Manifest.Key activeProduct, ProductDto activeProductDto, boolean newerVersionAvailable,
            ManagedMasterDto managedServer) {
        InstanceDto dto = new InstanceDto();
        dto.instanceConfiguration = instanceConfiguration;
        dto.productDto = productDto;
        dto.activeProduct = activeProduct;
        dto.activeProductDto = activeProductDto;
        dto.newerVersionAvailable = newerVersionAvailable;
        dto.managedServer = managedServer;
        return dto;
    }
}
