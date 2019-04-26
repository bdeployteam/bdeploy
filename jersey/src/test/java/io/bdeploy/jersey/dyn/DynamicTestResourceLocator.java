package io.bdeploy.jersey.dyn;

import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

@Path("/resources")
public interface DynamicTestResourceLocator {

    @Path("{name}")
    public DynamicTestResource getNamedResource(@PathParam("name") String name);

}
