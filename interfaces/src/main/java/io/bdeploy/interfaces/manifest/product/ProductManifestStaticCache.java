package io.bdeploy.interfaces.manifest.product;

import java.util.List;
import java.util.SortedSet;

import io.bdeploy.api.product.v1.ProductDescriptor;
import io.bdeploy.bhive.BHiveExecution;
import io.bdeploy.bhive.meta.MetaManifest;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.interfaces.descriptor.template.ApplicationTemplateDescriptor;
import io.bdeploy.interfaces.descriptor.template.InstanceTemplateDescriptor;
import io.bdeploy.interfaces.descriptor.template.ParameterTemplateDescriptor;
import io.bdeploy.interfaces.manifest.ProductManifest;

/**
 * Encapsulates static data of {@link ProductManifest}s, which shall not be re-calculated whenever loading product data.
 */
public class ProductManifestStaticCache {

    private final MetaManifest<ProductManifestStaticCacheRecord> meta;
    private final BHiveExecution hive;

    public ProductManifestStaticCache(Manifest.Key product, BHiveExecution hive) {
        this.hive = hive;
        this.meta = new MetaManifest<>(product, true, ProductManifestStaticCacheRecord.class);
    }

    /** Reads existing cached information if available. Returns <code>null</code> in case no information is available. */
    public ProductManifestStaticCacheRecord read() {
        ProductManifestStaticCacheRecord stored = meta.read(hive);
        if (stored == null) {
            return null;
        }
        return stored;
    }

    public void store(SortedSet<Key> appRefs, SortedSet<Key> otherRefs, ProductDescriptor desc, ObjectId cfgEntry,
            List<ObjectId> plugins, List<InstanceTemplateDescriptor> templates,
            List<ApplicationTemplateDescriptor> applicationTemplates, List<ParameterTemplateDescriptor> paramTemplates) {
        meta.write(hive, new ProductManifestStaticCacheRecord(appRefs, otherRefs, desc, cfgEntry, plugins, templates,
                applicationTemplates, paramTemplates));
    }

}
