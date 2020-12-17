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

import io.bdeploy.common.security.RequiredPermission;
import io.bdeploy.common.security.ScopedPermission.Permission;
import io.bdeploy.interfaces.configuration.instance.FileStatusDto;
import io.bdeploy.ui.dto.ConfigFileDto;

@Path("/cfgFiles")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface ConfigFileResource {

    @GET
    @Path("/{tag}")
    public List<ConfigFileDto> listConfigFiles(@PathParam("tag") String tag);

    /**
     * Read the contents of a configuration file as base64 encoded string.
     */
    @GET
    @Path("/{tag}/{file: .+}")
    public String loadConfigFile(@PathParam("tag") String tag, @PathParam("file") String file);

    @POST
    @RequiredPermission(permission = Permission.WRITE)
    public void updateConfigFiles(List<FileStatusDto> updates, @QueryParam("expect") String expectedTag);

    @GET
    @Path("/{iTag}/{pName : .+}/{pTag}/syncConfig")
    public List<ConfigFileDto> syncConfigFiles(@PathParam("iTag") String iTag, @PathParam("pName") String pName,
            @PathParam("pTag") String pTag);

}
