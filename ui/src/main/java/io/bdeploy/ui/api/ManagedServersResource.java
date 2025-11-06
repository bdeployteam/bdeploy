package io.bdeploy.ui.api;

import java.util.List;
import java.util.Map;
import java.util.SortedSet;

import io.bdeploy.common.Version;
import io.bdeploy.common.security.RequiredPermission;
import io.bdeploy.common.security.ScopedPermission.Permission;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration;
import io.bdeploy.interfaces.manifest.managed.ManagedMasterDto;
import io.bdeploy.interfaces.manifest.managed.MinionUpdateDto;
import io.bdeploy.interfaces.minion.MinionStatusDto;
import io.bdeploy.jersey.Scope;
import io.bdeploy.ui.dto.MinionSyncResultDto;
import io.bdeploy.ui.dto.ProductDto;
import io.bdeploy.ui.dto.ProductTransferDto;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

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
    public void tryAutoAttach(@Scope @PathParam("group") String groupName, ManagedMasterDto target);

    /**
     * Used on a central server to manually (force) attach the given server without verification
     */
    @PUT
    @Path("/manual-attach/{group}")
    @RequiredPermission(scope = "group", permission = Permission.ADMIN)
    public void manualAttach(@Scope @PathParam("group") String groupName, ManagedMasterDto target);

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
    @RequiredPermission(permission = Permission.ADMIN)
    public String manualAttachCentral(String central);

    /**
     * Used on a central server to retrieve an identification for the given instance group to manually attach on a managed server
     * <p>
     * The return value is an encrypted and signed CentralIdentDto.
     */
    @POST
    @Path("/central-ident/{group}")
    @RequiredPermission(scope = "group", permission = Permission.ADMIN)
    public String getCentralIdent(@Scope @PathParam("group") String group, ManagedMasterDto target);

    /**
     * Retrieve all available managed servers for an instance group on the central server
     */
    @GET
    @Path("/list/{group}")
    @RequiredPermission(scope = "group", permission = Permission.CLIENT)
    public List<ManagedMasterDto> getManagedServers(@Scope @PathParam("group") String instanceGroup);

    /**
     * Retrieve the controlling managed server on the central server.
     */
    @GET
    @Path("/controlling-server/{group}/{instanceId}")
    @RequiredPermission(scope = "group", permission = Permission.READ)
    public ManagedMasterDto getServerForInstance(@Scope @PathParam("group") String instanceGroup,
            @PathParam("instanceId") String instanceId, @QueryParam("instanceTag") String instanceTag);

    /**
     * Find all instances controlled by the given server.
     */
    @GET
    @Path("/controlled-instances/{group}/{server:.+}")
    @RequiredPermission(scope = "group", permission = Permission.READ)
    public List<InstanceConfiguration> getInstancesControlledBy(@Scope @PathParam("group") String groupName,
            @PathParam("server") String serverName);

    @DELETE
    @Path("/delete-server/{group}/{server:.+}")
    @Consumes(MediaType.TEXT_PLAIN)
    @RequiredPermission(scope = "group", permission = Permission.ADMIN)
    public void deleteManagedServer(@Scope @PathParam("group") String groupName, @PathParam("server") String serverName);

    @POST
    @Path("/update-server/{group}/{server:.+}")
    @RequiredPermission(scope = "group", permission = Permission.ADMIN)
    public void updateManagedServer(@Scope @PathParam("group") String groupName, @PathParam("server") String serverName,
            ManagedMasterDto update);

    @GET
    @Path("/minion-state/{group}/{server:.+}")
    @Consumes(MediaType.TEXT_PLAIN)
    @RequiredPermission(scope = "group", permission = Permission.WRITE)
    public Map<String, MinionStatusDto> getMinionStateOfManagedServer(@Scope @PathParam("group") String groupName,
            @PathParam("server") String serverName);

    @GET
    @Consumes(MediaType.TEXT_PLAIN)
    @Path("/synchronize/{group}/{server:.+}")
    @RequiredPermission(scope = "group", permission = Permission.READ)
    public MinionSyncResultDto synchronize(@Scope @PathParam("group") String groupName, @PathParam("server") String serverName);

    @GET
    @Path("/list-products/{group}/{server:.+}")
    @RequiredPermission(scope = "group", permission = Permission.WRITE)
    public List<ProductDto> listProducts(@Scope @PathParam("group") String groupName, @PathParam("server") String serverName);

    @POST
    @Path("/transfer-products/{group}")
    @RequiredPermission(scope = "group", permission = Permission.WRITE)
    public void transferProducts(@Scope @PathParam("group") String groupName, ProductTransferDto transfer);

    @GET
    @Path("/active-transfers/{group}")
    @RequiredPermission(scope = "group", permission = Permission.WRITE)
    public SortedSet<ProductDto> getActiveTransfers(@Scope @PathParam("group") String groupName);

    @POST
    @Path("/minion-transfer-updates/{group}/{server:.+}")
    @RequiredPermission(scope = "group", permission = Permission.ADMIN)
    public void transferUpdate(@Scope @PathParam("group") String groupName, @PathParam("server") String serverName,
            MinionUpdateDto dto);

    @POST
    @Path("/minion-install-updates/{group}/{server:.+}")
    @RequiredPermission(scope = "group", permission = Permission.ADMIN)
    public void installUpdate(@Scope @PathParam("group") String groupName, @PathParam("server") String serverName,
            MinionUpdateDto dto);

    @GET
    @Consumes(MediaType.TEXT_PLAIN)
    @Path("/minion-ping/{group}/{server:.+}")
    @RequiredPermission(scope = "group", permission = Permission.ADMIN)
    public Version pingServer(@Scope @PathParam("group") String groupName, @PathParam("server") String serverName);

}
