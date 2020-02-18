package io.bdeploy.ui.api;

import java.util.List;
import java.util.Map;
import java.util.SortedSet;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import io.bdeploy.common.Version;
import io.bdeploy.common.security.RequiredPermission;
import io.bdeploy.common.security.ScopedPermission.Permission;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration;
import io.bdeploy.interfaces.manifest.managed.ManagedMasterDto;
import io.bdeploy.interfaces.minion.MinionDto;
import io.bdeploy.interfaces.minion.MinionStatusDto;
import io.bdeploy.ui.dto.MinionUpdateDto;
import io.bdeploy.ui.dto.ProductDto;
import io.bdeploy.ui.dto.ProductTransferDto;

@Path("/managed-servers")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface ManagedServersResource {

    /**
     * Used on a central server to auto-attach a managed server
     */
    @PUT
    @Path("/auto-attach/{group}")
    @RequiredPermission(scope = "group", permission = Permission.ADMIN)
    public void tryAutoAttach(@PathParam("group") String groupName, ManagedMasterDto target);

    /**
     * Used on a central server to manually (force) attach the given server without verification
     */
    @PUT
    @Path("/manual-attach/{group}")
    @RequiredPermission(scope = "group", permission = Permission.ADMIN)
    public void manualAttach(@PathParam("group") String groupName, ManagedMasterDto target);

    /**
     * Used on a managed server to manually (force) attach an instance group from a central server using it's encrypted
     * identification.
     * <p>
     * The central identification must be a string obtained using {@link #getCentralIdent(String, ManagedMasterDto)} on the
     * central server.
     *
     * @return the name of the created (attached) instance group.
     */
    @PUT
    @Path("/manual-attach-central")
    @Consumes(MediaType.TEXT_PLAIN)
    public String manualAttachCentral(String central);

    /**
     * Used on a central server to retrieve an identification for the given instance group to manually attach on a managed server
     * <p>
     * The return value is an encrypted and signed CentralIdentDto.
     */
    @POST
    @Path("/central-ident/{group}")
    @RequiredPermission(scope = "group", permission = Permission.ADMIN)
    public String getCentralIdent(@PathParam("group") String group, ManagedMasterDto target);

    /**
     * Retrieve all available managed servers for an instance group on the central server
     */
    @GET
    @Path("/list/{group}")
    @RequiredPermission(scope = "group", permission = Permission.ADMIN)
    public List<ManagedMasterDto> getManagedServers(@PathParam("group") String instanceGroup);

    /**
     * Retrieve the controlling managed server on the central server.
     */
    @GET
    @Path("/controlling-server/{group}/{instanceId}")
    @RequiredPermission(scope = "group", permission = Permission.READ)
    public ManagedMasterDto getServerForInstance(@PathParam("group") String instanceGroup,
            @PathParam("instanceId") String instanceId, @QueryParam("instanceTag") String instanceTag);

    /**
     * Find all instances controlled by the given server.
     */
    @GET
    @Path("/controlled-instances/{group}/{server}")
    @RequiredPermission(scope = "group", permission = Permission.READ)
    public List<InstanceConfiguration> getInstancesControlledBy(@PathParam("group") String groupName,
            @PathParam("server") String serverName);

    @POST
    @Path("/delete-server/{group}/{server}")
    @Consumes(MediaType.TEXT_PLAIN)
    @RequiredPermission(scope = "group", permission = Permission.ADMIN)
    public void deleteManagedServer(@PathParam("group") String groupName, @PathParam("server") String serverName);

    @GET
    @Path("/minion-config/{group}/{server}")
    @Consumes(MediaType.TEXT_PLAIN)
    @RequiredPermission(scope = "group", permission = Permission.ADMIN)
    public Map<String, MinionDto> getMinionsOfManagedServer(@PathParam("group") String groupName,
            @PathParam("server") String serverName);

    @GET
    @Path("/minion-state/{group}/{server}")
    @Consumes(MediaType.TEXT_PLAIN)
    @RequiredPermission(scope = "group", permission = Permission.ADMIN)
    public Map<String, MinionStatusDto> getMinionStateOfManagedServer(@PathParam("group") String groupName,
            @PathParam("server") String serverName);

    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Path("/synchronize/{group}/{server}")
    @RequiredPermission(scope = "group", permission = Permission.WRITE)
    public ManagedMasterDto synchronize(@PathParam("group") String groupName, @PathParam("server") String serverName);

    @GET
    @Path("/list-products/{group}/{server}")
    @RequiredPermission(scope = "group", permission = Permission.ADMIN)
    public List<ProductDto> listProducts(@PathParam("group") String groupName, @PathParam("server") String serverName);

    @POST
    @Path("/transfer-products/{group}")
    @RequiredPermission(scope = "group", permission = Permission.ADMIN)
    public void transferProducts(@PathParam("group") String groupName, ProductTransferDto transfer);

    @GET
    @Path("/active-transfers/{group}")
    @RequiredPermission(scope = "group", permission = Permission.ADMIN)
    public SortedSet<ProductDto> getActiveTransfers(@PathParam("group") String groupName);

    @GET
    @Path("/minion-updates/{group}/{server}")
    @RequiredPermission(scope = "group", permission = Permission.WRITE)
    public MinionUpdateDto getUpdates(@PathParam("group") String groupName, @PathParam("server") String serverName);

    @POST
    @Path("/minion-transfer-updates/{group}/{server}")
    @RequiredPermission(scope = "group", permission = Permission.ADMIN)
    public void transferUpdate(@PathParam("group") String groupName, @PathParam("server") String serverName, MinionUpdateDto dto);

    @POST
    @Path("/minion-install-updates/{group}/{server}")
    @RequiredPermission(scope = "group", permission = Permission.ADMIN)
    public void installUpdate(@PathParam("group") String groupName, @PathParam("server") String serverName, MinionUpdateDto dto);

    @GET
    @Consumes(MediaType.TEXT_PLAIN)
    @Path("/minion-ping/{group}/{server}")
    @RequiredPermission(scope = "group", permission = Permission.ADMIN)
    public Version pingServer(@PathParam("group") String groupName, @PathParam("server") String serverName);

}
