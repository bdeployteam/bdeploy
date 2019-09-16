package io.bdeploy.ui.dto;

import java.util.Map;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.interfaces.manifest.ProductManifest;

public class ProductDto {

    public String name;
    public String vendor;
    public String description;
    public Manifest.Key key;
    public Map<String, String> labels;
    public ObjectId configTree;

    public static ProductDto create(ProductManifest manifest) {
        ProductDto dto = new ProductDto();
        dto.name = manifest.getProduct();
        dto.description = manifest.getProductDescriptor().name;
        dto.vendor = manifest.getProductDescriptor().vendor;
        dto.key = manifest.getKey();
        dto.labels = manifest.getLabels();
        dto.configTree = manifest.getConfigTemplateTreeId();
        return dto;
    }

}