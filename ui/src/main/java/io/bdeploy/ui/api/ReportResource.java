package io.bdeploy.ui.api;

import java.util.List;

import io.bdeploy.common.security.RequiredPermission;
import io.bdeploy.common.security.ScopedPermission.Permission;
import io.bdeploy.interfaces.report.ReportDescriptor;
import io.bdeploy.interfaces.report.ReportRequestDto;
import io.bdeploy.interfaces.report.ReportResponseDto;
import io.bdeploy.jersey.Scope;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/report")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface ReportResource {

    @GET
    public List<ReportDescriptor> list();

    @POST
    @Path("/{report}")
    @RequiredPermission(permission = Permission.READ, scope = "report")
    public ReportResponseDto generateReport(@Scope @PathParam("report") String report, ReportRequestDto request);

    @Path("/{report}/parameter-options")
    @RequiredPermission(permission = Permission.READ, scope = "report")
    public ReportParameterOptionResource getReportParameterOptionResource(@Scope @PathParam("report") String report);

}
