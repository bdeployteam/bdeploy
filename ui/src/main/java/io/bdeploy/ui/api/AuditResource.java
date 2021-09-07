package io.bdeploy.ui.api;

import java.util.List;

import io.bdeploy.jersey.ActivityScope;
import io.bdeploy.ui.dto.AuditLogDto;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/audit")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface AuditResource {

    @GET
    @Path("/hiveAuditLog")
    public List<AuditLogDto> hiveAuditLog(@ActivityScope @QueryParam("hive") String hiveParam,
            @QueryParam("lastInstant") long lastInstant, @QueryParam("lineLimit") int lineLimit);

}
