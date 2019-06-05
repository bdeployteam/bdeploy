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

import io.bdeploy.ui.dto.FileStatusDto;

@Path("/cfgFiles")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface ConfigFileResource {

    @GET
    @Path("/{tag}")
    public List<String> listConfigFiles(@PathParam("tag") String tag);

    @GET
    @Path("/{tag}/{file: .+}")
    public String loadConfigFile(@PathParam("tag") String tag, @PathParam("file") String file);

    @POST
    public void updateConfigFiles(List<FileStatusDto> updates, @QueryParam("expect") String expectedTag);

}
