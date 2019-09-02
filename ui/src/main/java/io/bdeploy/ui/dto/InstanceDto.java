package io.bdeploy.ui.dto;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration;

public class InstanceDto {

    public InstanceConfiguration instanceConfiguration;
    public ProductDto productDto; // DTO of instanceConfiguration.product

    public Manifest.Key activeProduct;
    public ProductDto activeProductDto;

    public static InstanceDto create(InstanceConfiguration instanceConfiguration, ProductDto productDto,
            Manifest.Key activeProduct, ProductDto activeProductDto) {
        InstanceDto dto = new InstanceDto();
        dto.instanceConfiguration = instanceConfiguration;
        dto.productDto = productDto;
        dto.activeProduct = activeProduct;
        dto.activeProductDto = activeProductDto;
        return dto;
    }
}
