package io.bdeploy.jersey;

import java.util.Collection;
import java.util.Collections;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.bdeploy.common.util.JacksonHelper;
import io.bdeploy.common.util.JacksonHelper.MapperType;
import jakarta.ws.rs.ext.ContextResolver;
import jakarta.ws.rs.ext.Provider;

/**
 * Provides a properly configured {@link ObjectMapper} used for
 * (de-)serialization of JSON objects.
 */
@Provider
public class JerseyObjectMapper implements ContextResolver<ObjectMapper> {

    private final Collection<Module> additionalModules;
    private final ObjectMapper mapper;

    /**
     * Constructor used on the server with injection
     */
    public JerseyObjectMapper() {
        this(Collections.emptyList());
    }

    /**
     * Injection not available in the same way on the client - manual workaround.
     */
    public JerseyObjectMapper(Collection<Module> additional) {
        additionalModules = additional;
        mapper = createMapper();
    }

    @Override
    public ObjectMapper getContext(Class<?> type) {
        return mapper;
    }

    private ObjectMapper createMapper() {
        if (additionalModules.isEmpty()) {
            return JacksonHelper.getDefaultJsonObjectMapper();
        }

        // don't use blackbird here. we're creating objectmappers "rather" frequently, and blackbird
        // uses anonymous lambdas to speed up serialization/deserialization. With short-lived mappers
        // this does not hold a performance benefit at all. only long-lived mappers profit.
        final ObjectMapper result = JacksonHelper.createObjectMapper(MapperType.JSON, false);

        for (Module m : additionalModules) {
            result.registerModule(m);
        }

        return result;
    }
}
