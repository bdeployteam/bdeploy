package io.bdeploy.interfaces.manifest.product;

import java.util.List;
import java.util.SortedSet;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.bdeploy.api.product.v1.ProductDescriptor;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.interfaces.configuration.template.FlattenedApplicationTemplateConfiguration;
import io.bdeploy.interfaces.configuration.template.FlattenedInstanceTemplateConfiguration;
import io.bdeploy.interfaces.descriptor.template.ParameterTemplateDescriptor;

/**
 * Caches static information for products.
 */
public class ProductManifestStaticCacheRecordV2 {

    public final SortedSet<Key> appRefs;
    public final SortedSet<Key> otherRefs;
    public final ProductDescriptor desc;
    public final ObjectId cfgEntry;
    public final List<ObjectId> plugins;
    public final List<FlattenedInstanceTemplateConfiguration> templates;
    public final List<FlattenedApplicationTemplateConfiguration> applicationTemplates;
    public final List<ParameterTemplateDescriptor> paramTemplates;

    @JsonCreator
    public ProductManifestStaticCacheRecordV2(@JsonProperty("appRefs") SortedSet<Key> appRefs,
            @JsonProperty("otherRefs") SortedSet<Key> otherRefs, @JsonProperty("desc") ProductDescriptor desc,
            @JsonProperty("cfgEntry") ObjectId cfgEntry, @JsonProperty("plugins") List<ObjectId> plugins,
            @JsonProperty("templates") List<FlattenedInstanceTemplateConfiguration> templates,
            @JsonProperty("applicationTemplates") List<FlattenedApplicationTemplateConfiguration> applicationTemplates,
            @JsonProperty("paramTemplates") List<ParameterTemplateDescriptor> paramTemplates) {
        this.appRefs = appRefs;
        this.otherRefs = otherRefs;
        this.desc = desc;
        this.cfgEntry = cfgEntry;
        this.plugins = plugins;
        this.templates = templates;
        this.applicationTemplates = applicationTemplates;
        this.paramTemplates = paramTemplates;
    }

}
