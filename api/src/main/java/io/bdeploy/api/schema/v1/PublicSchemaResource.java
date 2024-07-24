package io.bdeploy.api.schema.v1;

import io.bdeploy.jersey.JerseyAuthenticationProvider.Unsecured;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;

@Path("/public/v1/schema")
public interface PublicSchemaResource {

    public enum Schema {
        appInfoYaml,
        productInfoYaml,
        productVersionYaml,
        applicationTemplateYaml,
        instanceTemplateYaml,
        parameterTemplateYaml,
        instanceVariableTemplateYaml,
        systemTemplateYaml,
        productValidationYaml,
        instanceTemplateReferenceYaml,
    }

    @Operation(summary = "Get a YAML Schema", description = "Retrieve a JSON schema for various BDeploy YAML file formats.")
    @GET
    @Unsecured
    @Path("{schema}")
    public String getSchema(@PathParam("schema") Schema schema);

}
