package io.bdeploy.jersey.sse;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import io.bdeploy.jersey.ActivityScope;

@Path("/produce")
@Produces(MediaType.APPLICATION_JSON)
public interface SseActivityProducingResource {

    @GET
    String something(@ActivityScope @QueryParam("test") String test);

}
