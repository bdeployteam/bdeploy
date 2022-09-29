package io.bdeploy.schema;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion.VersionFlag;
import com.networknt.schema.ValidationMessage;

import io.bdeploy.api.schema.v1.PublicSchemaResource.Schema;
import io.bdeploy.common.util.JacksonHelper;
import io.bdeploy.common.util.JacksonHelper.MapperType;

public class PublicSchemaValidator {

    private final PublicSchemaGenerator generator = new PublicSchemaGenerator();

    public List<String> validate(Schema schema, Path file) {
        try (InputStream is = Files.newInputStream(file)) {
            return validate(schema, is);
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot validate file " + file, e);
        }
    }

    private List<String> validate(Schema schema, InputStream data) {
        ObjectMapper om = JacksonHelper.createObjectMapper(MapperType.YAML);
        JsonNode node;
        try {
            node = om.readTree(data);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read YAML from stream", e);
        }

        JsonSchema jsonSchema;
        try {
            String rawSchema = generator.generateSchema(schema);
            jsonSchema = JsonSchemaFactory.getInstance(VersionFlag.V202012).getSchema(rawSchema);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read JSON schema for " + schema);
        }

        jsonSchema.initializeValidators();

        return jsonSchema.validate(node).stream().map(this::messageToString).toList();
    }

    private String messageToString(ValidationMessage m) {
        return "[" + m.getType() + "] " + m.getMessage();
    }

}
