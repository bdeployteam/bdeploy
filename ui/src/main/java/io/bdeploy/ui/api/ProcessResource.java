package io.bdeploy.ui.api;

import java.util.List;
import java.util.Map;

import io.bdeploy.common.security.RequiredPermission;
import io.bdeploy.common.security.ScopedPermission.Permission;
import io.bdeploy.interfaces.VerifyOperationResultDto;
import io.bdeploy.interfaces.configuration.pcu.ProcessDetailDto;
import io.bdeploy.interfaces.directory.RemoteDirectory;
import io.bdeploy.jersey.Scope;
import io.bdeploy.ui.dto.InstanceProcessStatusDto;
import io.bdeploy.ui.dto.MappedInstanceProcessStatusDto;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface ProcessResource {

    @GET
    @Path("/")
    @Deprecated(since = "7.8.0")
    public InstanceProcessStatusDto getStatus();

    @GET
    @Path("/mapped")
    public MappedInstanceProcessStatusDto getMappedStatus();

    @GET
    @Path("/{runtimeNode}/{appId}")
    public ProcessDetailDto getDetails(@Scope @PathParam("runtimeNode") String runtimeNode,
            @Scope @PathParam("appId") String appId);

    @POST
    @Path("/start")
    @RequiredPermission(permission = Permission.WRITE)
    public void startProcesses(List<String> appId);

    @POST
    @Path("/start/mapped")
    @RequiredPermission(permission = Permission.WRITE)
    public void startProcesses(Map<String, List<String>> node2Applications);

    @POST
    @Path("/stop")
    @RequiredPermission(permission = Permission.WRITE)
    public void stopProcesses(List<String> appId);

    @POST
    @Path("/stop/mapped")
    @RequiredPermission(permission = Permission.WRITE)
    public void stopProcesses(Map<String, List<String>> node2Applications);

    @POST
    @Path("/restart")
    @RequiredPermission(permission = Permission.WRITE)
    public void restartProcesses(List<String> appId);

    @POST
    @Path("/restart/mapped")
    @RequiredPermission(permission = Permission.WRITE)
    public void restartProcesses(Map<String, List<String>> node2Applications);

    @POST
    @Path("/verify/{appId}")
    @RequiredPermission(permission = Permission.WRITE)
    public VerifyOperationResultDto verify(@PathParam("appId") String appId, @QueryParam("n") String runtimeNode);

    @POST
    @Path("/reinstall/{appId}")
    @RequiredPermission(permission = Permission.WRITE)
    public void reinstall(@PathParam("appId") String appId, @QueryParam("n") String runtimeNode);

    @GET
    @Path("/startAll")
    @RequiredPermission(permission = Permission.WRITE)
    public void startAll();

    @GET
    @Path("/stopAll")
    @RequiredPermission(permission = Permission.WRITE)
    public void stopAll();

    @GET
    @Path("/restartAll")
    @RequiredPermission(permission = Permission.WRITE)
    public void restartAll();

    @GET
    @Path("/dataDirSnapshot")
    public List<RemoteDirectory> getDataDirSnapshot();

    @GET
    @Path("/logDataDirSnapshot")
    public List<RemoteDirectory> getLogDataDirSnapshot();

    @POST
    @Path("/{appId}/stdin")
    @Consumes(MediaType.TEXT_PLAIN)
    @RequiredPermission(permission = Permission.WRITE)
    public void writeToStdin(@PathParam("appId") String appId, @QueryParam("n") String runtimeNode, String data);
}
