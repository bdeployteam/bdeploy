package io.bdeploy.jersey.dyn;

import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

@Path("/inject-locator")
public interface DynamicInjectionTestResourceLocator {

    @Path("{name}")
    public DynamicTestResource getNamed(@PathParam("name") String name);

}
