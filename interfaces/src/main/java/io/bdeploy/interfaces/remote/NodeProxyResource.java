package io.bdeploy.interfaces.remote;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/proxy")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface NodeProxyResource {

    @POST
    ProxiedResponseWrapper forward(ProxiedRequestWrapper wrapper);

}
