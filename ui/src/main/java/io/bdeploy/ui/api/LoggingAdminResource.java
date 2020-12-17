package io.bdeploy.ui.api;

import java.util.List;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import io.bdeploy.common.security.RequiredPermission;
import io.bdeploy.common.security.ScopedPermission.Permission;
import io.bdeploy.interfaces.directory.RemoteDirectory;
import io.bdeploy.interfaces.directory.RemoteDirectoryEntry;
import io.bdeploy.jersey.JerseyAuthenticationProvider.Unsecured;
import io.bdeploy.ui.dto.StringEntryChunkDto;

@Path("/logging-admin")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RequiredPermission(permission = Permission.ADMIN)
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

    /**
     * @return the base64 encoded contents of the config file
     */
    @GET
    @Path("/config")
    public String getLogConfig();

    /**
     * @param config the base64 new contents of the config file
     */
    @POST
    @Path("/config")
    @Consumes(MediaType.TEXT_PLAIN)
    public void setLogConfig(String config);

}
