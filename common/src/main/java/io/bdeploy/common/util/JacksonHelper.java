package io.bdeploy.common.util;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class JacksonHelper {

    public static final ObjectMapper JSON_MAPPER = createObjectMapper((JsonFactory) null);
    public static final ObjectMapper YAML_MAPPER = createObjectMapper(new YAMLFactory());

    public enum MapperType {
        JSON,
        YAML
    }

    private static ObjectMapper createObjectMapper(JsonFactory factory) {
        final ObjectMapper result = new ObjectMapper(factory);

        // never ever generate system dependent content (line endings).
        DefaultPrettyPrinter.Indenter i = new DefaultIndenter("  ", "\n");
        DefaultPrettyPrinter pp = new DefaultPrettyPrinter();
        pp.indentArraysWith(i);
        pp.indentObjectsWith(i);
        result.setDefaultPrettyPrinter(pp);

        result.enable(SerializationFeature.INDENT_OUTPUT);

        result.setVisibility(PropertyAccessor.ALL, Visibility.NONE);
        result.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);
        result.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        result.registerModule(new Jdk8Module());
        result.registerModule(new JavaTimeModule());
        return result;
    }

    public static ObjectMapper createObjectMapper(MapperType type) {
        if (type == MapperType.JSON) {
            return JSON_MAPPER;
        } else {
            return YAML_MAPPER;
        }
    }

    public static ObjectMapper createDefaultObjectMapper() {
        return createObjectMapper(MapperType.JSON);
    }

}
