package io.bdeploy.ui.api;

import java.util.List;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

import io.bdeploy.common.security.RequiredCapability;
import io.bdeploy.common.security.ScopedCapability.Capability;
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
    @RequiredCapability(capability = Capability.WRITE)
    public void startProcess(@ActivityScope @PathParam("processId") String processId);

    @GET
    @Path("/{processId}/stop")
    @RequiredCapability(capability = Capability.WRITE)
    public void stopProcess(@ActivityScope @PathParam("processId") String processId);

    @GET
    @Path("/{processId}/restart")
    @RequiredCapability(capability = Capability.WRITE)
    public void restartProcess(@ActivityScope @PathParam("processId") String processId);

    @GET
    @Path("/start")
    @RequiredCapability(capability = Capability.WRITE)
    public void startAll();

    @GET
    @Path("/stop")
    @RequiredCapability(capability = Capability.WRITE)
    public void stopAll();

    @GET
    @Path("/restart")
    @RequiredCapability(capability = Capability.WRITE)
    public void restart();

    @GET
    @Path("/dataDirSnapshot")
    public List<InstanceDirectory> getDataDirSnapshot();
}
