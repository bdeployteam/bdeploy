package io.bdeploy.schema;

import io.bdeploy.api.product.v1.ProductDescriptor;
import io.bdeploy.api.product.v1.ProductVersionDescriptor;
import io.bdeploy.api.schema.v1.PublicSchemaResource.Schema;
import io.bdeploy.api.validation.v1.dto.ProductValidationDescriptorApi;
import io.bdeploy.interfaces.descriptor.application.ApplicationDescriptor;
import io.bdeploy.interfaces.descriptor.instance.InstanceVariableDefinitionDescriptor;
import io.bdeploy.interfaces.descriptor.template.ApplicationTemplateDescriptor;
import io.bdeploy.interfaces.descriptor.template.InstanceTemplateDescriptor;
import io.bdeploy.interfaces.descriptor.template.InstanceTemplateReferenceDescriptor;
import io.bdeploy.interfaces.descriptor.template.InstanceVariableTemplateDescriptor;
import io.bdeploy.interfaces.descriptor.template.ParameterTemplateDescriptor;
import io.bdeploy.interfaces.descriptor.template.SystemTemplateDescriptor;

enum InternalSchema {

    APP_INFO(Schema.appInfoYaml, ApplicationDescriptor.class),
    PRODUCT_INFO(Schema.productInfoYaml, ProductDescriptor.class),
    PRODUCT_VERSION(Schema.productVersionYaml, ProductVersionDescriptor.class),
    APPLICATION_TEMPLATE(Schema.applicationTemplateYaml, ApplicationTemplateDescriptor.class),
    INSTANCE_TEMPLATE(Schema.instanceTemplateYaml, InstanceTemplateDescriptor.class),
    PARAMETER_TEMPLATE(Schema.parameterTemplateYaml, ParameterTemplateDescriptor.class),
    INSTANCE_VAR_TEMPLATE(Schema.instanceVariableTemplateYaml, InstanceVariableTemplateDescriptor.class),
    SYSTEM_TEMPLATE(Schema.systemTemplateYaml, SystemTemplateDescriptor.class),
    PRODUCT_VALIDATION(Schema.productValidationYaml, ProductValidationDescriptorApi.class),
    INSTANCE_TEMPLATE_REFERENCE(Schema.instanceTemplateReferenceYaml, InstanceTemplateReferenceDescriptor.class),
    INSTANCE_VAR_DEFINITION(Schema.instanceVariableDefinitionYaml, InstanceVariableDefinitionDescriptor.class);

    public final Schema apiSchema;
    public final Class<?> apiClass;

    private InternalSchema(Schema apiSchema, Class<?> clazz) {
        this.apiSchema = apiSchema;
        this.apiClass = clazz;
    }

    static InternalSchema get(Schema apiSchema) {
        for (var s : InternalSchema.values()) {
            if (s.apiSchema == apiSchema) {
                return s;
            }
        }
        throw new IllegalArgumentException("No internal schema definition for " + apiSchema);
    }

}
