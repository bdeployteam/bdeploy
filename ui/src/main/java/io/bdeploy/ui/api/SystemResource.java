package io.bdeploy.ui.api;

import java.util.List;

import io.bdeploy.ui.dto.SystemConfigurationDto;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface SystemResource {

    @GET
    public List<SystemConfigurationDto> list();

    @POST
    public void update(SystemConfigurationDto dto);

    @DELETE
    @Path("{system}")
    public void delete(@PathParam("system") String system);

}
