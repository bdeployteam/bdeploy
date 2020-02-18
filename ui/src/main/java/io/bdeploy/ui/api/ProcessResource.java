package io.bdeploy.ui.api;

import java.util.List;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

import io.bdeploy.common.security.RequiredPermission;
import io.bdeploy.common.security.ScopedPermission.Permission;
import io.bdeploy.interfaces.configuration.pcu.ProcessStatusDto;
import io.bdeploy.interfaces.directory.InstanceDirectory;
import io.bdeploy.jersey.ActivityScope;

@Path("/processes")
public interface ProcessResource {

    @GET
    @Path("/{processId}")
    public ProcessStatusDto getStatus(@ActivityScope @PathParam("processId") String processId);

    @GET
    @Path("/")
    public Map<String, ProcessStatusDto> getStatus();

    @GET
    @Path("/{processId}/start")
    @RequiredPermission(permission = Permission.WRITE)
    public void startProcess(@ActivityScope @PathParam("processId") String processId);

    @GET
    @Path("/{processId}/stop")
    @RequiredPermission(permission = Permission.WRITE)
    public void stopProcess(@ActivityScope @PathParam("processId") String processId);

    @GET
    @Path("/{processId}/restart")
    @RequiredPermission(permission = Permission.WRITE)
    public void restartProcess(@ActivityScope @PathParam("processId") String processId);

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
    public List<InstanceDirectory> getDataDirSnapshot();
}
