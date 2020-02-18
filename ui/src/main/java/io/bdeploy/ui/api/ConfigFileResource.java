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

}
