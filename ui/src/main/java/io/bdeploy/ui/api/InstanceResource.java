package io.bdeploy.ui.api;

import java.util.List;
import java.util.Map;

import org.glassfish.jersey.media.multipart.FormDataMultiPart;

import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.common.security.RequiredPermission;
import io.bdeploy.common.security.ScopedPermission.Permission;
import io.bdeploy.interfaces.configuration.instance.ApplicationValidationDto;
import io.bdeploy.interfaces.configuration.instance.FileStatusDto;
import io.bdeploy.interfaces.configuration.instance.InstanceActivateCheckDto;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceUpdateDto;
import io.bdeploy.interfaces.configuration.pcu.BulkPortStatesDto;
import io.bdeploy.interfaces.descriptor.client.ClickAndStartDescriptor;
import io.bdeploy.interfaces.directory.RemoteDirectory;
import io.bdeploy.interfaces.directory.RemoteDirectoryEntry;
import io.bdeploy.interfaces.manifest.attributes.CustomAttributesRecord;
import io.bdeploy.interfaces.manifest.banner.InstanceBannerRecord;
import io.bdeploy.interfaces.manifest.state.InstanceStateRecord;
import io.bdeploy.interfaces.manifest.statistics.ClientUsageData;
import io.bdeploy.interfaces.minion.MinionDto;
import io.bdeploy.interfaces.minion.MinionStatusDto;
import io.bdeploy.jersey.JerseyAuthenticationProvider.Unsecured;
import io.bdeploy.jersey.Scope;
import io.bdeploy.ui.dto.HistoryFilterDto;
import io.bdeploy.ui.dto.HistoryResultDto;
import io.bdeploy.ui.dto.InstanceDto;
import io.bdeploy.ui.dto.InstanceNodeConfigurationListDto;
import io.bdeploy.ui.dto.InstanceVersionDto;
import io.bdeploy.ui.dto.ProductUpdateDto;
import io.bdeploy.ui.dto.StringEntryChunkDto;
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
import jakarta.ws.rs.core.Response;

@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface InstanceResource {

    public static final String PATH_DOWNLOAD_APP_ICON = "/{instance}/{applicationId}/icon";
    public static final String PATH_DOWNLOAD_APP_SPLASH = "/{instance}/{applicationId}/splash";

    @GET
    public List<InstanceDto> list();

    @GET
    @Path("/{instance}/update-check")
    public ProductUpdateDto getProductUpdates(@Scope @PathParam("instance") String instanceId);

    @GET
    @Path("/{instance}/versions")
    public List<InstanceVersionDto> listVersions(@Scope @PathParam("instance") String instanceId);

    @PUT
    @RequiredPermission(permission = Permission.WRITE)
    public void create(InstanceConfiguration config, @QueryParam("managedServer") String managedServer);

    @GET
    @Path("/{instance}")
    public InstanceDto read(@Scope @PathParam("instance") String instanceId);

    @GET
    @Path("/{instance}/{versionTag}")
    public InstanceConfiguration readVersion(@Scope @PathParam("instance") String instanceId,
            @PathParam("versionTag") String versionTag);

    @POST
    @Path("/{instance}/update")
    @RequiredPermission(permission = Permission.WRITE)
    public void update(@Scope @PathParam("instance") String instanceId, InstanceUpdateDto config,
            @QueryParam("managedServer") String managedServer, @QueryParam("expect") String expectedTag);

    @DELETE
    @Path("/{instance}/delete")
    @RequiredPermission(permission = Permission.ADMIN)
    public void delete(@Scope @PathParam("instance") String instanceId);

    @DELETE
    @Path("/{instance}/deleteVersion/{tag}")
    @RequiredPermission(permission = Permission.ADMIN)
    public void deleteVersion(@Scope @PathParam("instance") String instanceId, @PathParam("tag") String tag);

    @GET
    @Path("/{instance}/{tag}/nodeConfiguration")
    public InstanceNodeConfigurationListDto getNodeConfigurations(@Scope @PathParam("instance") String instanceId,
            @PathParam("tag") String versionTag);

    @GET
    @Path("/{instance}/{tag}/minionConfiguration")
    @RequiredPermission(permission = Permission.READ)
    public Map<String, MinionDto> getMinionConfiguration(@Scope @PathParam("instance") String instanceId,
            @PathParam("tag") String versionTag);

    @GET
    @Path("/{instance}/{tag}/minionState")
    @RequiredPermission(permission = Permission.READ)
    public Map<String, MinionStatusDto> getMinionState(@Scope @PathParam("instance") String instanceId,
            @PathParam("tag") String versionTag);

    @GET
    @Path("/{instance}/{tag}/install")
    @RequiredPermission(permission = Permission.WRITE)
    public void install(@Scope @PathParam("instance") String instanceId, @Scope @PathParam("tag") String tag);

    @GET
    @Path("/{instance}/{tag}/uninstall")
    @RequiredPermission(permission = Permission.WRITE)
    public void uninstall(@Scope @PathParam("instance") String instanceId, @Scope @PathParam("tag") String tag);

    @GET
    @Path("/{instance}/installNewest")
    @RequiredPermission(permission = Permission.WRITE)
    public void installNewest(@Scope @PathParam("instance") String instanceId);

    @GET
    @Path("/{instance}/{tag}/pre-activate")
    public InstanceActivateCheckDto preActivate(@Scope @PathParam("instance") String instanceId,
            @Scope @PathParam("tag") String tag);

    @GET
    @Path("/{instance}/{tag}/activate")
    @RequiredPermission(permission = Permission.WRITE)
    public void activate(@Scope @PathParam("instance") String instanceId, @Scope @PathParam("tag") String tag,
            @QueryParam("force") boolean force);

    @GET
    @Path("/{instance}/activateNewest")
    @RequiredPermission(permission = Permission.WRITE)
    public void activateNewest(@Scope @PathParam("instance") String instanceId, @QueryParam("force") boolean force);

    @POST
    @Path("/{instance}/updateProductVersion/{target}")
    @RequiredPermission(permission = Permission.WRITE)
    public InstanceUpdateDto updateProductVersion(@Scope @PathParam("instance") String instanceId,
            @PathParam("target") String productTag, InstanceUpdateDto state);

    @POST
    @Path("/{instance}/validate")
    @RequiredPermission(permission = Permission.WRITE)
    public List<ApplicationValidationDto> validate(@Scope @PathParam("instance") String instanceId, InstanceUpdateDto state);

    @GET
    @Path("/{instance}/state")
    @RequiredPermission(permission = Permission.READ)
    public InstanceStateRecord getDeploymentStates(@Scope @PathParam("instance") String instanceId);

    @Path("/{instance}/processes")
    @RequiredPermission(permission = Permission.READ)
    public ProcessResource getProcessResource(@Scope @PathParam("instance") String instanceId);

    @Path("/bulk")
    @RequiredPermission(permission = Permission.WRITE)
    public InstanceBulkResource getBulkResource();

    @Path("/templates")
    @RequiredPermission(permission = Permission.WRITE)
    public InstanceTemplateResource getTemplateResource();

    @Path("/{instance}/cfgFiles")
    @RequiredPermission(permission = Permission.READ)
    public ConfigFileResource getConfigResource(@Scope @PathParam("instance") String instanceId);

    @GET
    @Path("/{instance}/{applicationId}/clickAndStart")
    public ClickAndStartDescriptor getClickAndStartDescriptor(@PathParam("instance") String instanceId,
            @PathParam("applicationId") String applicationId);

    @GET
    @Path("/{instance}/{applicationId}/installer/zip")
    @Produces(MediaType.TEXT_PLAIN)
    public String createClientInstaller(@PathParam("instance") String instanceId,
            @PathParam("applicationId") String applicationId);

    @GET
    @Unsecured
    @Path(PATH_DOWNLOAD_APP_ICON)
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response downloadIcon(@PathParam("instance") String instanceId, @PathParam("applicationId") String applicationId);

    @GET
    @Unsecured
    @Path(PATH_DOWNLOAD_APP_SPLASH)
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response downloadSplash(@PathParam("instance") String instanceId, @PathParam("applicationId") String applicationId);

    @GET
    @Path("/{instance}/export/{tag}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @RequiredPermission(permission = Permission.READ)
    public Response exportInstance(@Scope @PathParam("instance") String instanceId, @PathParam("tag") String tag);

    @POST
    @Path("/{instance}/import")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @RequiredPermission(permission = Permission.WRITE)
    public List<Key> importInstance(FormDataMultiPart fdmp, @Scope @PathParam("instance") String instanceId);

    @GET
    @Path("/{instance}/output/{tag}/{app}")
    @RequiredPermission(permission = Permission.READ)
    public RemoteDirectory getOutputEntry(@Scope @PathParam("instance") String instanceId, @Scope @PathParam("tag") String tag,
            @PathParam("app") String app);

    @POST
    @Path("/{instance}/content/{minion}")
    @RequiredPermission(permission = Permission.READ)
    public StringEntryChunkDto getContentChunk(@Scope @PathParam("instance") String instanceId,
            @PathParam("minion") String minion, RemoteDirectoryEntry entry, @QueryParam("offset") long offset,
            @QueryParam("limit") long limit);

    @POST
    @Path("/{instance}/request/{minion}")
    @RequiredPermission(permission = Permission.READ)
    public String getContentStreamRequest(@Scope @PathParam("instance") String instanceId, @PathParam("minion") String minion,
            RemoteDirectoryEntry entry);

    @POST
    @Path("/{instance}/requestMultiZip/{minion}")
    @RequiredPermission(permission = Permission.READ)
    public String getContentMultiZipStreamRequest(@Scope @PathParam("instance") String instanceId,
            @PathParam("minion") String minion, List<RemoteDirectoryEntry> entry);

    @POST
    @Path("/{instance}/data/update/{minion}")
    @RequiredPermission(permission = Permission.WRITE)
    public void updateDataFiles(@Scope @PathParam("instance") String instanceId, @PathParam("minion") String minion,
            List<FileStatusDto> updates);

    @POST
    @Path("/{instance}/delete/{minion}")
    @RequiredPermission(permission = Permission.WRITE)
    public void deleteDataFiles(@Scope @PathParam("instance") String instanceId, @PathParam("minion") String minion,
            List<RemoteDirectoryEntry> entries);

    @GET
    @Unsecured
    @Path("/{instance}/stream/{token}")
    @RequiredPermission(permission = Permission.READ)
    public Response getContentStream(@Scope @PathParam("instance") String instanceId, @PathParam("token") String token);

    @GET
    @Unsecured
    @Path("/{instance}/streamMultiZip/{token}")
    @RequiredPermission(permission = Permission.READ)
    public Response getContentMultiZipStream(@Scope @PathParam("instance") String instanceId, @PathParam("token") String token);

    @POST
    @Path("/{instance}/check-ports/{minion}")
    @RequiredPermission(permission = Permission.READ)
    @Deprecated(since = "7.8.0")
    public Map<Integer, Boolean> getPortStates(@Scope @PathParam("instance") String instanceId,
            @PathParam("minion") String minion, List<Integer> ports);

    @POST
    @Path("/{instance}/check-ports-bulk")
    @RequiredPermission(permission = Permission.READ)
    public BulkPortStatesDto getPortStatesBulk(@Scope @PathParam("instance") String instanceId,
            Map<String, List<Integer>> node2ports);

    @GET
    @Path("/{instance}/banner")
    public InstanceBannerRecord getBanner(@Scope @PathParam("instance") String instanceId);

    @POST
    @Path("/{instance}/banner")
    @RequiredPermission(permission = Permission.WRITE)
    public void updateBanner(@Scope @PathParam("instance") String instanceId, InstanceBannerRecord instanceBannerRecord);

    @POST
    @Path("/{instance}/history")
    @RequiredPermission(permission = Permission.READ)
    public HistoryResultDto getInstanceHistory(@Scope @PathParam("instance") String instanceId, HistoryFilterDto filter);

    @GET
    @Path("/{instance}/attributes")
    @RequiredPermission(permission = Permission.READ)
    public CustomAttributesRecord getAttributes(@Scope @PathParam("instance") String instanceId);

    @POST
    @Path("/{instance}/attributes")
    @RequiredPermission(permission = Permission.WRITE)
    public void updateAttributes(@Scope @PathParam("instance") String instanceId, CustomAttributesRecord attributes);

    @GET
    @Path("/{instance}/clientUsage")
    @RequiredPermission(permission = Permission.CLIENT)
    public ClientUsageData getClientUsageData(@Scope @PathParam("instance") String instanceId);

    @GET
    @Path("/{instance}/uiDirect/{app}/{ep : [^/]+}")
    @RequiredPermission(permission = Permission.CLIENT)
    public String getUiDirectUrl(@Scope @PathParam("instance") String instance, @PathParam("app") String application,
            @PathParam("ep") String endpoint);
}
