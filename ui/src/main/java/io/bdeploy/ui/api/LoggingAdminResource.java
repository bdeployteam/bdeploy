package io.bdeploy.ui.api;

import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import io.bdeploy.interfaces.directory.RemoteDirectory;
import io.bdeploy.interfaces.directory.RemoteDirectoryEntry;
import io.bdeploy.jersey.JerseyAuthenticationProvider.Unsecured;
import io.bdeploy.ui.dto.StringEntryChunkDto;

@Path("/logging-admin")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface LoggingAdminResource {

    @GET
    @Path("/logDirs")
    public List<RemoteDirectory> getLogDirectories();

    @POST
    @Path("/content/{minion}")
    public StringEntryChunkDto getLogContentChunk(@PathParam("minion") String minion, RemoteDirectoryEntry entry,
            @QueryParam("offset") long offset, @QueryParam("limit") long limit);

    @POST
    @Path("/request/{minion}")
    public String getLogContentStreamRequest(@PathParam("minion") String minion, RemoteDirectoryEntry entry);

    @GET
    @Unsecured
    @Path("/stream/{token}")
    public Response getLogContentStream(@PathParam("token") String token);

}
