package io.bdeploy.jersey.resources;

import io.bdeploy.jersey.JerseyAuthenticationProvider.Unsecured;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;

@Path("/")
public interface RedirectOnApiRootAccess {

    @GET
    @Unsecured
    public Response redirectMe();

}
