package io.bdeploy.ui.api;

import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

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
