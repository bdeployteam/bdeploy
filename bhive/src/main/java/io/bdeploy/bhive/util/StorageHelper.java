/*
 * Copyright (c) SSI Schaefer IT Solutions GmbH
 */
package io.bdeploy.bhive.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.bdeploy.bhive.model.Tree;
import io.bdeploy.bhive.remote.jersey.BHiveJacksonModule;
import io.bdeploy.common.util.JacksonHelper;
import io.bdeploy.common.util.JacksonHelper.MapperType;

/**
 * Provides functionality to persist and load model objects from persistent
 * storage.
 * <p>
 * Methods are required to produce reproducible results per object.
 * <p>
 * ATTENTION: The underlying implementation may NOT be changed without exact
 * knowledge of the impact. The objects may be hashed and stored in an object
 * database, which means that changing the algorithm will basically break any
 * hive which used the existing algorithm.
 */
public class StorageHelper {

    public interface CustomMapper {

        public byte[] write(Object obj);

        public Object read(InputStream is);
    }

    private static final Map<Class<?>, CustomMapper> customMappers;
    static {
        customMappers = new HashMap<>();
        customMappers.put(Tree.class, new SimpleTreeMapper());
    }

    /**
     * Serializes any in-memory Object to a stable storage-friendly byte[].
     */
    public static byte[] toRawBytes(Object o) {
        CustomMapper m = customMappers.get(o.getClass());
        if (m != null) {
            return m.write(o);
        }
        try {
            return getMapper().writeValueAsBytes(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot write JSON value", e);
        }
    }

    /**
     * Serializes any in-memory Object to a stable storage-friendly YAML style byte[].
     */
    public static byte[] toRawYamlBytes(Object o) {
        try {
            return getMapper(MapperType.YAML).writeValueAsBytes(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot write JSON value", e);
        }
    }

    /**
     * De-serializes an Object of given type from a byte[].
     */
    public static <T> T fromRawBytes(byte[] bytes, Class<T> clazz) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes)) {
            return fromStream(bis, clazz);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read JSON value", e);
        }
    }

    /**
     * De-serializes an Object of given type from a given file
     */
    public static <T> T fromPath(Path path, Class<T> clazz) {
        try (InputStream is = Files.newInputStream(path)) {
            return StorageHelper.fromStream(is, clazz);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read file " + path, e);
        }
    }

    /**
     * De-serializes an Object of given type from a stream into memory.
     */
    @SuppressWarnings("unchecked")
    public static <T> T fromStream(InputStream is, Class<T> clazz) {
        CustomMapper m = customMappers.get(clazz);
        if (m != null) {
            return (T) m.read(is);
        }
        try (InputStreamReader r = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            return getMapper().readValue(is, clazz);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read JSON value", e);
        }
    }

    /**
     * De-serializes an Object of given type from a YAML content stream into memory.
     */
    public static <T> T fromYamlStream(InputStream is, Class<T> clazz) {
        try (InputStreamReader r = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            return getMapper(MapperType.YAML).readValue(is, clazz);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read JSON value", e);
        }
    }

    private static ObjectMapper getMapper() {
        return getMapper(MapperType.JSON);
    }

    private static ObjectMapper getMapper(MapperType type) {
        ObjectMapper dm = JacksonHelper.createObjectMapper(type);

        dm.registerModule(new BHiveJacksonModule());

        return dm;
    }

}
