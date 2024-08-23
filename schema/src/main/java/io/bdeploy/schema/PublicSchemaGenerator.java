package io.bdeploy.schema;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.victools.jsonschema.generator.Option;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerationContext;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaKeyword;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.module.jackson.JacksonModule;
import com.github.victools.jsonschema.module.jackson.JacksonOption;

import io.bdeploy.api.schema.v1.PublicSchemaResource.Schema;
import io.bdeploy.common.util.JacksonHelper;
import io.bdeploy.interfaces.JsonSchemaAllowedTypes;
import io.bdeploy.interfaces.configuration.TemplateableVariableConfiguration;
import io.bdeploy.interfaces.configuration.dcu.ApplicationConfiguration;
import io.bdeploy.interfaces.configuration.dcu.LinkedValueConfiguration;
import io.bdeploy.interfaces.configuration.dcu.ParameterConfiguration;
import io.bdeploy.interfaces.configuration.pcu.ProcessConfiguration;
import io.bdeploy.interfaces.descriptor.application.ParameterDescriptor;
import io.bdeploy.interfaces.descriptor.template.TemplateParameter;
import io.bdeploy.interfaces.descriptor.template.TemplateVariable;

public class PublicSchemaGenerator {

    private static final String PROP_DEPRECATED = "deprecated";

    private final SchemaGenerator generator;

    public PublicSchemaGenerator() {
        JacksonModule jm = new JacksonModule(JacksonOption.RESPECT_JSONPROPERTY_REQUIRED,
                JacksonOption.INCLUDE_ONLY_JSONPROPERTY_ANNOTATED_METHODS);

        SchemaGeneratorConfigBuilder cfgBuilder = new SchemaGeneratorConfigBuilder(JacksonHelper.getDefaultJsonObjectMapper(),
                SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON).with(jm);

        // include all methods which are there for compatibility mapping of properties. jackson module filters only those with annotation.
        cfgBuilder.with(Option.NONSTATIC_NONVOID_NONGETTER_METHODS, Option.FIELDS_DERIVED_FROM_ARGUMENTFREE_METHODS);

        // don't allow additional properties apart from those defined in the schema.
        cfgBuilder.with(Option.FORBIDDEN_ADDITIONAL_PROPERTIES_BY_DEFAULT);

        // custom definition for LinkedValueConfiguration which allows basic types in addition to the actual type.
        cfgBuilder.forFields().withTargetTypeOverridesResolver(field -> {
            JsonSchemaAllowedTypes allowed = field
                    .getAnnotationConsideringFieldAndGetterIfSupported(JsonSchemaAllowedTypes.class);
            if (allowed != null) {
                return Arrays.asList(allowed.value()).stream().map(c -> field.getContext().resolve(c)).toList();
            }

            if (!field.getType().isInstanceOf(LinkedValueConfiguration.class)) {
                return null;
            }
            var allowedTypes = List.of(String.class, Boolean.class, Number.class, LinkedValueConfiguration.class);
            return allowedTypes.stream().map(t -> field.getContext().resolve(t)).toList();
        });

        // respect the @Deprecated annotation on methods (which are compatibility wrappers only right now).
        cfgBuilder.forMethods().withInstanceAttributeOverride((node, method, context) -> {
            if (method.getAnnotation(Deprecated.class) != null) {
                node.put(PROP_DEPRECATED, true);
            }
        });

        // and also for fields.
        cfgBuilder.forFields().withInstanceAttributeOverride((node, field, context) -> {
            if (field.getAnnotation(Deprecated.class) != null) {
                node.put(PROP_DEPRECATED, true);
            }
        });

        // need to do this fully custom, to make variable id/uid & template "primary keys" required.
        String id = "id";
        String template = "template";
        cfgBuilder.forTypesInGeneral().withTypeAttributeOverride((node, scope, context) -> {
            if (scope.getType().isInstanceOf(ParameterDescriptor.class)) {
                var arr = node.putArray(context.getKeyword(SchemaKeyword.TAG_ONEOF));
                exlusiveField(arr, context, id, template);
                exlusiveField(arr, context, template, id);

                // Compat with old files...
                exlusiveField(arr, context, "uid", "__dummy");
            }

            if (scope.getType().isInstanceOf(TemplateableVariableConfiguration.class)) {
                var arr = node.putArray(context.getKeyword(SchemaKeyword.TAG_ONEOF));
                exlusiveField(arr, context, id, template);
                exlusiveField(arr, context, template, id);
            }

            // we have 1, 2 places where Map is used instead of concrete objects to allow partials with config different from the original. This allows that.
            if (scope.getType().isInstanceOf(Map.class)) {
                node.put(context.getKeyword(SchemaKeyword.TAG_ADDITIONAL_PROPERTIES), true);
            }

            // for compatibility with existing files, we need to properly allow 'uid' properties.
            // this is different from the exclusivity of the 'id', 'uid' and 'template fields above.
            if (scope.getType().isInstanceOf(ParameterDescriptor.class)
                    || scope.getType().isInstanceOf(ParameterConfiguration.class)
                    || scope.getType().isInstanceOf(ApplicationConfiguration.class)
                    || scope.getType().isInstanceOf(ProcessConfiguration.class)
                    || scope.getType().isInstanceOf(TemplateParameter.class)
                    || scope.getType().isInstanceOf(TemplateVariable.class)) {
                ObjectNode on = (ObjectNode) node.get(context.getKeyword(SchemaKeyword.TAG_PROPERTIES));
                ObjectNode compatUid = on.putObject("uid");
                compatUid.put("type", "string");
                compatUid.put("description", "DEPRECATED: Compatibility with 'id' property.");
            }

            // add aliases
            for (Field field : scope.getType().getErasedType().getFields()) {
                JsonAlias jsonAlias = field.getAnnotation(JsonAlias.class);
                // ignore id field, special use case that is handled above
                if ("id".equals(field.getName()) || jsonAlias == null) {
                    continue;
                }
                ObjectNode propertiesNode = (ObjectNode) node.get(context.getKeyword(SchemaKeyword.TAG_PROPERTIES));
                JsonNode fieldNode = propertiesNode.get(field.getName());
                for (String alias : jsonAlias.value()) {
                    propertiesNode.set(alias, fieldNode);
                }
            }
        });

        generator = new SchemaGenerator(cfgBuilder.build());
    }

    private void exlusiveField(ArrayNode oneOf, SchemaGenerationContext context, String prop, String... excludes) {
        ObjectNode obj = oneOf.addObject();
        obj.putArray(context.getKeyword(SchemaKeyword.TAG_REQUIRED)).add(prop);
        var excl = obj.putObject(context.getKeyword(SchemaKeyword.TAG_PROPERTIES));
        Arrays.stream(excludes).forEach(e -> excl.put(e, false));
    }

    public String generateSchema(Schema schema) {
        return generator.generateSchema(InternalSchema.get(schema).apiClass).toPrettyString();
    }

}
