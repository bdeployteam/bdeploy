package io.bdeploy.ui.api;

import java.util.List;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import io.bdeploy.jersey.ActivityScope;
import io.bdeploy.ui.dto.AuditLogDto;

@Path("/audit")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface AuditResource {

    @GET
    @Path("/hiveAuditLog")
    public List<AuditLogDto> hiveAuditLog(@ActivityScope @QueryParam("hive") String hiveParam,
            @QueryParam("lastInstant") long lastInstant, @QueryParam("lineLimit") int lineLimit);

    @GET
    @Path("/auditLog")
    public List<AuditLogDto> auditLog(@QueryParam("lastInstant") long lastInstant, @QueryParam("lineLimit") int lineLimit);

}
