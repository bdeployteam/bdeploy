package io.bdeploy.ui.api;

import java.util.Map;

import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.security.RequiredPermission;
import io.bdeploy.common.security.ScopedPermission.Permission;
import io.bdeploy.interfaces.minion.MinionStatusDto;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/node-admin")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface NodeManagementResource {

    @GET
    @Path("/nodes")
    public Map<String, MinionStatusDto> getNodes();

    @PUT
    @Path("/nodes/{name}")
    @RequiredPermission(permission = Permission.ADMIN)
    public void addNode(@PathParam("name") String name, RemoteService node);

    @DELETE
    @Path("/nodes/{name}")
    @RequiredPermission(permission = Permission.ADMIN)
    public void removeNode(@PathParam("name") String name);

}
