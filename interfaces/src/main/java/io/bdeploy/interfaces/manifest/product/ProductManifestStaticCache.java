package io.bdeploy.interfaces.manifest.product;

import java.util.List;
import java.util.SortedSet;

import io.bdeploy.api.product.v1.ProductDescriptor;
import io.bdeploy.bhive.BHiveExecution;
import io.bdeploy.bhive.meta.MetaManifest;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.interfaces.configuration.template.FlattenedApplicationTemplateConfiguration;
import io.bdeploy.interfaces.configuration.template.FlattenedInstanceTemplateConfiguration;
import io.bdeploy.interfaces.descriptor.template.ParameterTemplateDescriptor;
import io.bdeploy.interfaces.descriptor.variable.VariableDescriptor;
import io.bdeploy.interfaces.manifest.ProductManifest;

/**
 * Encapsulates static data of {@link ProductManifest}s, which shall not be re-calculated whenever loading product data.
 */
public class ProductManifestStaticCache {

    private final MetaManifest<ProductManifestStaticCacheRecordV2> meta;
    private final BHiveExecution hive;

    public ProductManifestStaticCache(Manifest.Key product, BHiveExecution hive) {
        this.hive = hive;
        this.meta = new MetaManifest<>(product, true, ProductManifestStaticCacheRecordV2.class);
    }

    /** Reads existing cached information if available. Returns <code>null</code> in case no information is available. */
    public ProductManifestStaticCacheRecordV2 read() {
        return meta.read(hive);
    }

    public void store(SortedSet<Key> appRefs, SortedSet<Key> otherRefs, ProductDescriptor desc, ObjectId cfgEntry,
            List<ObjectId> plugins, List<FlattenedInstanceTemplateConfiguration> templates,
            List<FlattenedApplicationTemplateConfiguration> applicationTemplates,
            List<ParameterTemplateDescriptor> paramTemplates, List<VariableDescriptor> instanceVariables) {
        meta.write(hive, new ProductManifestStaticCacheRecordV2(appRefs, otherRefs, desc, cfgEntry, plugins, templates,
                applicationTemplates, paramTemplates, instanceVariables));
    }

}
