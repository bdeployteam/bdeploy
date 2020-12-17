package io.bdeploy.jersey;

import jakarta.inject.Inject;
import jakarta.ws.rs.ext.ContextResolver;
import jakarta.ws.rs.ext.Provider;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.bdeploy.common.util.JacksonHelper;

/**
 * Provides a properly configured {@link ObjectMapper} used for
 * (de-)serialization of JSON objects.
 */
@Provider
public class JerseyObjectMapper implements ContextResolver<ObjectMapper> {

    @Inject
    Iterable<Module> additionalModules;

    /**
     * Constructor used on the server with injection
     */
    public JerseyObjectMapper() {
        // nothing
    }

    /**
     * Injection not available in the same way on the client - manual workaround.
     */
    public JerseyObjectMapper(Iterable<Module> additional) {
        additionalModules = additional;
    }

    @Override
    public ObjectMapper getContext(Class<?> type) {
        return createDefaultMapper();
    }

    private ObjectMapper createDefaultMapper() {
        final ObjectMapper result = JacksonHelper.createDefaultObjectMapper();

        for (Module m : additionalModules) {
            result.registerModule(m);
        }

        return result;
    }

}
