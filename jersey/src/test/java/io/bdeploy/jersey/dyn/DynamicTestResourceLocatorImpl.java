package io.bdeploy.jersey.dyn;

import java.util.Map;
import java.util.TreeMap;

import jakarta.inject.Singleton;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response.Status;

@Singleton
public class DynamicTestResourceLocatorImpl implements DynamicTestResourceLocator {

    private final Map<String, String> namedValues = new TreeMap<>();

    public void register(String key, String value) {
        namedValues.put(key, value);
    }

    @Override
    public DynamicTestResource getNamedResource(String name) {
        if (!namedValues.containsKey(name)) {
            throw new WebApplicationException(Status.NOT_FOUND);
        }
        return new DynamicTestResourceImpl(namedValues.get(name));
    }

}
