package io.bdeploy.ui.dto;

import java.util.List;
import java.util.Map;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.interfaces.descriptor.template.InstanceTemplateDescriptor;
import io.bdeploy.interfaces.manifest.ProductManifest;

public class ProductDto {

    public String name;
    public String vendor;
    public String product;
    public Manifest.Key key;
    public Map<String, String> labels;
    public ObjectId configTree;
    public List<InstanceTemplateDescriptor> templates;

    public static ProductDto create(ProductManifest manifest) {
        ProductDto dto = new ProductDto();
        dto.name = manifest.getProductDescriptor().name;
        dto.product = manifest.getProduct();
        dto.vendor = manifest.getProductDescriptor().vendor;
        dto.key = manifest.getKey();
        dto.labels = manifest.getLabels();
        dto.configTree = manifest.getConfigTemplateTreeId();
        dto.templates = manifest.getInstanceTemplates();
        return dto;
    }

}