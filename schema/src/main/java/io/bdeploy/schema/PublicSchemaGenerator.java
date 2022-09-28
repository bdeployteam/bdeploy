package io.bdeploy.schema;

import java.util.List;

import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.module.jackson.JacksonModule;

import io.bdeploy.common.util.JacksonHelper;
import io.bdeploy.interfaces.configuration.dcu.LinkedValueConfiguration;

public class PublicSchemaGenerator {

    private final SchemaGenerator generator;

    public PublicSchemaGenerator() {
        JacksonModule jm = new JacksonModule();

        SchemaGeneratorConfigBuilder cfgBuilder = new SchemaGeneratorConfigBuilder(JacksonHelper.createDefaultObjectMapper(),
                SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON).with(jm);

        // custom definition for LinkedValueConfiguration which allows basic types in addition to the actual type.
        cfgBuilder.forFields().withTargetTypeOverridesResolver(field -> {
            if (!field.getType().isInstanceOf(LinkedValueConfiguration.class)) {
                return null;
            }
            var allowedTypes = List.of(String.class, Boolean.class, Number.class, LinkedValueConfiguration.class);
            return allowedTypes.stream().map(t -> field.getContext().resolve(t)).toList();
        });

        generator = new SchemaGenerator(cfgBuilder.build());
    }

    public String generateSchema(Class<?> clazz) {
        return generator.generateSchema(clazz).toPrettyString();
    }

}
