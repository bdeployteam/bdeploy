package io.bdeploy.bhive.remote.jersey;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

import org.glassfish.hk2.utilities.Binder;
import org.glassfish.hk2.utilities.binding.AbstractBinder;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.KeyDeserializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;

/**
 * A jackson module which adds logic to (de-) serialize {@link Key}s.
 */
public class BHiveJacksonModule extends SimpleModule {

    private static final long serialVersionUID = 1L;

    public BHiveJacksonModule() {
        addKeySerializer(Manifest.Key.class, new MKS());
        addKeyDeserializer(Manifest.Key.class, new MKD());

        // We absolutely require ordered Sets even over the wire to maintain object orders.
        addAbstractTypeMapping(Set.class, LinkedHashSet.class);
    }

    public Binder binder() {
        return new AbstractBinder() {

            @Override
            protected void configure() {
                bind(BHiveJacksonModule.this).to(com.fasterxml.jackson.databind.Module.class);
            }
        };
    }

    private static class MKS extends JsonSerializer<Manifest.Key> {

        @Override
        public void serialize(Manifest.Key value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeFieldName(value.toString());
        }
    }

    private static class MKD extends KeyDeserializer {

        @Override
        public Object deserializeKey(String key, DeserializationContext ctxt) throws IOException {
            return Manifest.Key.parse(key);
        }
    }

}
