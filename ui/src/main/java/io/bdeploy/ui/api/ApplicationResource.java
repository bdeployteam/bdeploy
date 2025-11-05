package io.bdeploy.ui.api;

import java.util.List;

import io.bdeploy.ui.dto.ApplicationDto;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface ApplicationResource {

    @GET
    public List<ApplicationDto> list();
}
