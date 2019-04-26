package io.bdeploy.jersey.security;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import io.bdeploy.jersey.JerseyAuthenticationProvider.Unsecured;

@Path("/security")
@Produces(MediaType.TEXT_PLAIN)
@Consumes(MediaType.TEXT_PLAIN)
public interface SecurityTestResource {

    @GET
    @Unsecured
    @Path("/unsecured")
    public String testUnsecured();

    @GET
    @Path("/secured")
    public String testSecured();

}
