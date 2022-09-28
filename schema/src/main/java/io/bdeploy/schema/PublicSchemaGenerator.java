package io.bdeploy.schema;

import java.util.List;

import com.github.victools.jsonschema.generator.Option;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.module.jackson.JacksonModule;
import com.github.victools.jsonschema.module.jackson.JacksonOption;

import io.bdeploy.common.util.JacksonHelper;
import io.bdeploy.interfaces.configuration.dcu.LinkedValueConfiguration;

public class PublicSchemaGenerator {

    private final SchemaGenerator generator;

    public PublicSchemaGenerator() {
        JacksonModule jm = new JacksonModule(JacksonOption.RESPECT_JSONPROPERTY_REQUIRED,
                JacksonOption.INCLUDE_ONLY_JSONPROPERTY_ANNOTATED_METHODS);

        SchemaGeneratorConfigBuilder cfgBuilder = new SchemaGeneratorConfigBuilder(JacksonHelper.createDefaultObjectMapper(),
                SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON).with(jm);

        // include all methods which are there for compatibility mapping of properties. jackson module filters only those with annotation.
        cfgBuilder.with(Option.NONSTATIC_NONVOID_NONGETTER_METHODS, Option.FIELDS_DERIVED_FROM_ARGUMENTFREE_METHODS);

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
