package io.bdeploy.jersey.stream;

import java.nio.file.Path;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@javax.ws.rs.Path("/stream")
@Produces(MediaType.APPLICATION_OCTET_STREAM)
@Consumes(MediaType.APPLICATION_OCTET_STREAM)
public interface StreamTestResource {

    @GET
    public Path download();

    @PUT
    public void upload(Path path);

}
