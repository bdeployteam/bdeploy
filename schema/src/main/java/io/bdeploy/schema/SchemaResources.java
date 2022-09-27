package io.bdeploy.schema;

import org.glassfish.hk2.utilities.binding.AbstractBinder;

import io.bdeploy.jersey.RegistrationTarget;
import jakarta.inject.Singleton;

public class SchemaResources {

    private SchemaResources() {
    }

    public static void register(RegistrationTarget server) {
        server.register(SchemaResourceImpl.class);

        server.register(new AbstractBinder() {

            @Override
            protected void configure() {
                bind(PublicSchemaGenerator.class).in(Singleton.class).to(PublicSchemaGenerator.class);
            }
        });
    }

}
