package io.bdeploy.ui.dto;

import java.util.List;
import java.util.Map;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.interfaces.descriptor.template.ApplicationTemplateDescriptor;
import io.bdeploy.interfaces.descriptor.template.InstanceTemplateDescriptor;
import io.bdeploy.interfaces.manifest.ProductManifest;

public class ProductDto implements Comparable<ProductDto> {

    public String name;
    public String vendor;
    public String product;
    public Manifest.Key key;
    public Map<String, String> labels;
    public ObjectId configTree;
    public List<InstanceTemplateDescriptor> instanceTemplates;
    public List<ApplicationTemplateDescriptor> applicationTemplates;

    public static ProductDto create(ProductManifest manifest) {
        ProductDto dto = new ProductDto();
        dto.name = manifest.getProductDescriptor().name;
        dto.product = manifest.getProduct();
        dto.vendor = manifest.getProductDescriptor().vendor;
        dto.key = manifest.getKey();
        dto.labels = manifest.getLabels();
        dto.configTree = manifest.getConfigTemplateTreeId();
        dto.instanceTemplates = manifest.getInstanceTemplates();
        dto.applicationTemplates = manifest.getApplicationTemplates();
        return dto;
    }

    @Override
    public int compareTo(ProductDto o) {
        return key.compareTo(o.key);
    }

}