package io.bdeploy.schema;

import io.bdeploy.api.schema.v1.PublicSchemaResource;
import io.bdeploy.interfaces.descriptor.application.ApplicationDescriptor;
import jakarta.inject.Inject;

public class SchemaResourceImpl implements PublicSchemaResource {

    private enum InternalSchema {

        APP_INFO(Schema.appInfoYaml, ApplicationDescriptor.class);

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

    @Inject
    private PublicSchemaGenerator generator;

    @Override
    public String getSchema(Schema schema) {
        InternalSchema s = InternalSchema.get(schema);
        return generator.generateSchema(s.apiClass);
    }

}
