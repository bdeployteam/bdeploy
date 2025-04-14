package io.bdeploy.ui.dto;

import java.util.List;
import java.util.Map;
import java.util.SortedSet;

import javax.annotation.Generated;

import io.bdeploy.api.product.v1.ProductDescriptor;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.common.Version;
import io.bdeploy.common.util.VersionHelper;
import io.bdeploy.interfaces.configuration.template.FlattenedApplicationTemplateConfiguration;
import io.bdeploy.interfaces.configuration.template.FlattenedInstanceTemplateConfiguration;
import io.bdeploy.interfaces.descriptor.variable.VariableDescriptor;
import io.bdeploy.interfaces.manifest.ProductManifest;

public class ProductDto implements Comparable<ProductDto> {

    public String name;
    public String vendor;
    public Version minMinionVersion;
    public String product;
    public Manifest.Key key;
    public Map<String, String> labels;
    public ObjectId configTree;
    public List<FlattenedInstanceTemplateConfiguration> instanceTemplates;
    public List<FlattenedApplicationTemplateConfiguration> applicationTemplates;
    public SortedSet<Manifest.Key> references;
    public List<VariableDescriptor> instanceVariables;

    public static ProductDto create(ProductManifest manifest) {
        ProductDescriptor productDescriptor = manifest.getProductDescriptor();

        ProductDto dto = new ProductDto();
        dto.name = productDescriptor.name;
        dto.vendor = productDescriptor.vendor;
        dto.minMinionVersion = VersionHelper.tryParse(productDescriptor.minMinionVersion);
        dto.product = manifest.getProduct();
        dto.key = manifest.getKey();
        dto.labels = manifest.getLabels();
        dto.configTree = manifest.getConfigTemplateTreeId();
        dto.instanceTemplates = manifest.getInstanceTemplates();
        dto.applicationTemplates = manifest.getApplicationTemplates(); // no parameter templates intentionally - they are expanded in ApplicationManifest
        dto.references = manifest.getReferences();
        dto.instanceVariables = manifest.getInstanceVariables();
        return dto;
    }

    @Override
    public int compareTo(ProductDto o) {
        return key.compareTo(o.key);
    }

    @Generated("Eclipse")
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((key == null) ? 0 : key.hashCode());
        return result;
    }

    @Generated("Eclipse")
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        ProductDto other = (ProductDto) obj;
        if (key == null) {
            if (other.key != null) {
                return false;
            }
        } else if (!key.equals(other.key)) {
            return false;
        }
        return true;
    }
}
