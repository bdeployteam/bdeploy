package io.bdeploy.ui.api;

import java.util.List;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

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
    public void startProcess(@ActivityScope @PathParam("processId") String processId);

    @GET
    @Path("/{processId}/stop")
    public void stopProcess(@ActivityScope @PathParam("processId") String processId);

    @GET
    @Path("/{processId}/restart")
    public void restartProcess(@ActivityScope @PathParam("processId") String processId);

    @GET
    @Path("/start")
    public void startAll();

    @GET
    @Path("/stop")
    public void stopAll();

    @GET
    @Path("/restart")
    public void restart();

    @GET
    @Path("/dataDirSnapshot")
    public List<InstanceDirectory> getDataDirSnapshot();
}
