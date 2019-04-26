package io.bdeploy.jersey.inject;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

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
