package io.bdeploy.schema;

import io.bdeploy.api.schema.v1.PublicSchemaResource;
import jakarta.inject.Inject;

public class SchemaResourceImpl implements PublicSchemaResource {

    @Inject
    private PublicSchemaGenerator generator;

    @Override
    public String getSchema(Schema schema) {
        return generator.generateSchema(schema);
    }

}
