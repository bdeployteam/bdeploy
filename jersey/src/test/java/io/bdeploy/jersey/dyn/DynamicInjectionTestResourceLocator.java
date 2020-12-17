package io.bdeploy.jersey.dyn;

import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;

@Path("/inject-locator")
public interface DynamicInjectionTestResourceLocator {

    @Path("{name}")
    public DynamicTestResource getNamed(@PathParam("name") String name);

}
