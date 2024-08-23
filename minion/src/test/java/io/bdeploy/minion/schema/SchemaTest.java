package io.bdeploy.minion.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.bdeploy.api.schema.v1.PublicSchemaResource;
import io.bdeploy.api.schema.v1.PublicSchemaResource.Schema;
import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.interfaces.descriptor.application.ApplicationDescriptor;
import io.bdeploy.interfaces.descriptor.application.ExecutableDescriptor;
import io.bdeploy.minion.TestMinion;
import io.bdeploy.schema.PublicSchemaValidator;

@ExtendWith(TestMinion.class)
class SchemaTest {

    @Test
    void testGenerateSchemas(PublicSchemaResource rsrc) {
        for (Schema s : Schema.values()) {
            assertNotNull(rsrc.getSchema(s));
        }
    }

    @Test
    void testValidateSchema() throws IOException {
        ApplicationDescriptor desc = new ApplicationDescriptor();
        desc.name = "Test";

        Path tmpFile = Files.createTempFile("junit-", ".yaml");
        Files.write(tmpFile, StorageHelper.toRawYamlBytes(desc));

        PublicSchemaValidator validator = new PublicSchemaValidator();
        List<String> messages = validator.validate(Schema.appInfoYaml, tmpFile);

        assertEquals(1, messages.size());
        assertTrue(messages.get(0).contains("startCommand")); // missing start command

        desc.startCommand = new ExecutableDescriptor();
        desc.startCommand.launcherPath = "test";

        Files.write(tmpFile, StorageHelper.toRawYamlBytes(desc));
        messages = validator.validate(Schema.appInfoYaml, tmpFile);

        assertEquals(0, messages.size());
    }

}
