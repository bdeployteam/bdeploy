package io.bdeploy.api.schema.v1;

import io.bdeploy.jersey.JerseyAuthenticationProvider.Unsecured;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;

@Path("/schema")
public interface PublicSchemaResource {

    public enum Schema {
        appInfoYaml
    }

    @GET
    @Unsecured
    @Path("{schema}")
    public String getSchema(@PathParam("schema") Schema schema);

}
