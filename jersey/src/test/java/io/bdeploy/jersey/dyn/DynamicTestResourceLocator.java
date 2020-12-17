package io.bdeploy.jersey.dyn;

import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;

@Path("/resources")
public interface DynamicTestResourceLocator {

    @Path("{name}")
    public DynamicTestResource getNamedResource(@PathParam("name") String name);

}
