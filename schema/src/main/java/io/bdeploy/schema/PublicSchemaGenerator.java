package io.bdeploy.schema;

import java.util.List;
import java.util.Map;

import com.github.victools.jsonschema.generator.Option;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.module.jackson.JacksonModule;
import com.github.victools.jsonschema.module.jackson.JacksonOption;

import io.bdeploy.api.schema.v1.PublicSchemaResource.Schema;
import io.bdeploy.common.util.JacksonHelper;
import io.bdeploy.interfaces.configuration.TemplateableVariableConfiguration;
import io.bdeploy.interfaces.configuration.dcu.LinkedValueConfiguration;
import io.bdeploy.interfaces.descriptor.application.ParameterDescriptor;
import io.bdeploy.interfaces.descriptor.template.TemplateParameter;
import io.bdeploy.interfaces.descriptor.template.TemplateVariable;

public class PublicSchemaGenerator {

    private final SchemaGenerator generator;

    public PublicSchemaGenerator() {
        JacksonModule jm = new JacksonModule(JacksonOption.RESPECT_JSONPROPERTY_REQUIRED,
                JacksonOption.INCLUDE_ONLY_JSONPROPERTY_ANNOTATED_METHODS);

        SchemaGeneratorConfigBuilder cfgBuilder = new SchemaGeneratorConfigBuilder(JacksonHelper.createDefaultObjectMapper(),
                SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON).with(jm);

        // include all methods which are there for compatibility mapping of properties. jackson module filters only those with annotation.
        cfgBuilder.with(Option.NONSTATIC_NONVOID_NONGETTER_METHODS, Option.FIELDS_DERIVED_FROM_ARGUMENTFREE_METHODS);

        // don't allow additional properties apart from those defined in the schema.
        cfgBuilder.with(Option.FORBIDDEN_ADDITIONAL_PROPERTIES_BY_DEFAULT);

        // custom definition for LinkedValueConfiguration which allows basic types in addition to the actual type.
        cfgBuilder.forFields().withTargetTypeOverridesResolver(field -> {
            if (!field.getType().isInstanceOf(LinkedValueConfiguration.class)) {
                return null;
            }
            var allowedTypes = List.of(String.class, Boolean.class, Number.class, LinkedValueConfiguration.class);
            return allowedTypes.stream().map(t -> field.getContext().resolve(t)).toList();
        });

        // respect the @Deprecated annotation on methods (which are compatibility wrappers only right now).
        cfgBuilder.forMethods().withInstanceAttributeOverride((node, method, context) -> {
            if (method.getAnnotation(Deprecated.class) != null) {
                node.put("deprecated", true);
            }
        });

        // and also for fields.
        cfgBuilder.forFields().withInstanceAttributeOverride((node, field, context) -> {
            if (field.getAnnotation(Deprecated.class) != null) {
                node.put("deprecated", true);
            }
        });

        // need to do this fully custom, to make variable id/uid & template "primary keys" required.
        cfgBuilder.forTypesInGeneral().withTypeAttributeOverride((node, scope, context) -> {
            if (scope.getType().isInstanceOf(ParameterDescriptor.class)) {
                var arr = node.putArray("oneOf");
                arr.addObject().putArray("required").add("id");
                arr.addObject().putArray("required").add("uid");
                arr.addObject().putArray("required").add("template");
            }

            if (scope.getType().isInstanceOf(TemplateParameter.class) || scope.getType().isInstanceOf(TemplateVariable.class)) {
                var arr = node.putArray("oneOf");
                arr.addObject().putArray("required").add("id");
                arr.addObject().putArray("required").add("uid");
            }

            if (scope.getType().isInstanceOf(TemplateableVariableConfiguration.class)) {
                var arr = node.putArray("oneOf");
                arr.addObject().putArray("required").add("id");
                arr.addObject().putArray("required").add("template");
            }

            // we have 1, 2 places where Map is used instead of concrete objects to allow partials with config different from the original. This allows that.
            if (scope.getType().isInstanceOf(Map.class)) {
                node.put("additionalProperties", "true");
            }
        });

        generator = new SchemaGenerator(cfgBuilder.build());
    }

    public String generateSchema(Schema schema) {
        return generator.generateSchema(InternalSchema.get(schema).apiClass).toPrettyString();
    }

}
