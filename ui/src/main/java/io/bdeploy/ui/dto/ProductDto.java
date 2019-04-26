package io.bdeploy.ui.dto;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.interfaces.manifest.ProductManifest;

public class ProductDto {

    public String name;
    public String description;
    public Manifest.Key key;

    public static ProductDto create(ProductManifest manifest) {
        ProductDto dto = new ProductDto();
        dto.name = manifest.getProduct();
        dto.description = manifest.getProductDescriptor().name;
        dto.key = manifest.getKey();
        return dto;
    }

}