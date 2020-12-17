package io.bdeploy.jersey.stream;

import java.nio.file.Path;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@jakarta.ws.rs.Path("/stream")
@Produces(MediaType.APPLICATION_OCTET_STREAM)
@Consumes(MediaType.APPLICATION_OCTET_STREAM)
public interface StreamTestResource {

    @GET
    public Path download();

    @PUT
    public void upload(Path path);

}
