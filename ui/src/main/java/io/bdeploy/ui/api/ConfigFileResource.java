package io.bdeploy.ui.api;

import java.util.List;

import io.bdeploy.ui.dto.ConfigFileDto;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface ConfigFileResource {

    @GET
    @Path("/{tag}/{prodName: .+}/{prodTag}")
    public List<ConfigFileDto> listConfigFiles(@PathParam("tag") String tag, @PathParam("prodName") String prodName,
            @PathParam("prodTag") String prodTag);

    /**
     * Read the contents of a configuration file as base64 encoded string.
     */
    @GET
    @Path("/load/{tag}/{file: .+}")
    public String loadConfigFile(@PathParam("tag") String tag, @PathParam("file") String file);

    @GET
    @Path("/loadTemplate/{file: .+}")
    public String loadProductConfigFile(@QueryParam("prodName") String prodName, @QueryParam("prodTag") String prodTag,
            @PathParam("file") String file);
}
