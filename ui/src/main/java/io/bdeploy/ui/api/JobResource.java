package io.bdeploy.ui.api;

import java.util.List;

import io.bdeploy.common.security.RequiredPermission;
import io.bdeploy.common.security.ScopedPermission.Permission;
import io.bdeploy.ui.dto.JobDto;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

@Path("/job")
@RequiredPermission(permission = Permission.ADMIN)
public interface JobResource {

    @GET
    @Path("/list")
    public List<JobDto> list();

    @POST
    @Path("/run")
    public void run(JobDto jobDto);

}
