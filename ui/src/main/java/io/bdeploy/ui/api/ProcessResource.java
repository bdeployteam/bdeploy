package io.bdeploy.ui.api;

import java.util.List;
import java.util.Map;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import io.bdeploy.common.security.RequiredPermission;
import io.bdeploy.common.security.ScopedPermission.Permission;
import io.bdeploy.interfaces.configuration.pcu.ProcessDetailDto;
import io.bdeploy.interfaces.configuration.pcu.ProcessStatusDto;
import io.bdeploy.interfaces.directory.RemoteDirectory;
import io.bdeploy.jersey.ActivityScope;

@Path("/processes")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface ProcessResource {

    @GET
    @Path("/")
    public Map<String, ProcessStatusDto> getStatus();

    @GET
    @Path("/{appId}")
    public ProcessDetailDto getDetails(@ActivityScope @PathParam("appId") String appId);

    @GET
    @Path("/{appId}/start")
    @RequiredPermission(permission = Permission.WRITE)
    public void startProcess(@ActivityScope @PathParam("appId") String appId);

    @GET
    @Path("/{appId}/stop")
    @RequiredPermission(permission = Permission.WRITE)
    public void stopProcess(@ActivityScope @PathParam("appId") String appId);

    @GET
    @Path("/{appId}/restart")
    @RequiredPermission(permission = Permission.WRITE)
    public void restartProcess(@ActivityScope @PathParam("appId") String appId);

    @GET
    @Path("/start")
    @RequiredPermission(permission = Permission.WRITE)
    public void startAll();

    @GET
    @Path("/stop")
    @RequiredPermission(permission = Permission.WRITE)
    public void stopAll();

    @GET
    @Path("/restart")
    @RequiredPermission(permission = Permission.WRITE)
    public void restart();

    @GET
    @Path("/dataDirSnapshot")
    public List<RemoteDirectory> getDataDirSnapshot();

    @POST
    @Path("/{appId}/stdin")
    @Consumes(MediaType.TEXT_PLAIN)
    @RequiredPermission(permission = Permission.WRITE)
    public void writeToStdin(@PathParam("appId") String appId, String data);
}
