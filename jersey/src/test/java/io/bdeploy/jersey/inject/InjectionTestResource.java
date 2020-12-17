package io.bdeploy.jersey.inject;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/inject")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface InjectionTestResource {

    static final String INJECTED_STRING = "InjectedString";

    @GET
    @Path("/simple")
    public String retrieveInjected();

    @GET
    @Path("/provider")
    public String retrieveInjectedProvider();

    @GET
    @Path("/user")
    public String retrieveUserFromToken();

}
