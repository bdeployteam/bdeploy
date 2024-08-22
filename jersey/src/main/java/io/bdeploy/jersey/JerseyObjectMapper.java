package io.bdeploy.jersey;

import java.util.Collections;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.bdeploy.common.util.JacksonHelper;
import io.bdeploy.common.util.JacksonHelper.MapperType;
import jakarta.inject.Inject;
import jakarta.ws.rs.ext.ContextResolver;
import jakarta.ws.rs.ext.Provider;

/**
 * Provides a properly configured {@link ObjectMapper} used for
 * (de-)serialization of JSON objects.
 */
@Provider
public class JerseyObjectMapper implements ContextResolver<ObjectMapper> {

    @Inject
    private Iterable<Module> additionalModules;

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
    public JerseyObjectMapper(Iterable<Module> additional) {
        additionalModules = additional;
        mapper = createMapper();
    }

    @Override
    public ObjectMapper getContext(Class<?> type) {
        return mapper;
    }

    private ObjectMapper createMapper() {
        final ObjectMapper result = JacksonHelper.createObjectMapper(MapperType.JSON);

        for (Module m : additionalModules) {
            result.registerModule(m);
        }

        return result;
    }

}
