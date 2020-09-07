package io.bdeploy.ui.api;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.glassfish.jersey.media.multipart.FormDataParam;

import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.common.security.RequiredPermission;
import io.bdeploy.common.security.ScopedPermission.Permission;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration.InstancePurpose;
import io.bdeploy.interfaces.configuration.instance.InstanceConfigurationDto;
import io.bdeploy.interfaces.descriptor.client.ClickAndStartDescriptor;
import io.bdeploy.interfaces.directory.InstanceDirectory;
import io.bdeploy.interfaces.directory.InstanceDirectoryEntry;
import io.bdeploy.interfaces.manifest.state.InstanceStateRecord;
import io.bdeploy.interfaces.minion.MinionDto;
import io.bdeploy.interfaces.minion.MinionStatusDto;
import io.bdeploy.jersey.ActivityScope;
import io.bdeploy.jersey.JerseyAuthenticationProvider.Unsecured;
import io.bdeploy.ui.dto.HistoryEntryDto;
import io.bdeploy.ui.dto.HistoryEntryVersionDto;
import io.bdeploy.ui.dto.InstanceDto;
import io.bdeploy.ui.dto.InstanceManifestHistoryDto;
import io.bdeploy.ui.dto.InstanceNodeConfigurationListDto;
import io.bdeploy.ui.dto.InstanceVersionDto;
import io.bdeploy.ui.dto.StringEntryChunkDto;

@Path("/")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface InstanceResource {

    public static final String PATH_DOWNLOAD_APP_ICON = "/{instance}/{applicationId}/icon";
    public static final String PATH_DOWNLOAD_APP_SPLASH = "/{instance}/{applicationId}/splash";

    @GET
    public List<InstanceDto> list();

    @GET
    @Path("/{instance}/versions")
    public List<InstanceVersionDto> listVersions(@ActivityScope @PathParam("instance") String instanceId);

    @PUT
    @RequiredPermission(permission = Permission.ADMIN)
    public void create(InstanceConfiguration config, @QueryParam("managedServer") String managedServer);

    @GET
    @Path("/{instance}")
    public InstanceConfiguration read(@ActivityScope @PathParam("instance") String instanceId);

    @GET
    @Path("/{instance}/{versionTag}")
    public InstanceConfiguration readVersion(@ActivityScope @PathParam("instance") String instanceId,
            @PathParam("versionTag") String versionTag);

    @POST
    @Path("/{instance}")
    @RequiredPermission(permission = Permission.WRITE)
    public void update(@ActivityScope @PathParam("instance") String instanceId, InstanceConfigurationDto config,
            @QueryParam("managedServer") String managedServer, @QueryParam("expect") String expectedTag);

    @DELETE
    @Path("/{instance}")
    @RequiredPermission(permission = Permission.ADMIN)
    public void delete(@ActivityScope @PathParam("instance") String instanceId);

    @GET
    @Path("/{instance}/{tag}/nodeConfiguration")
    public InstanceNodeConfigurationListDto getNodeConfigurations(@ActivityScope @PathParam("instance") String instanceId,
            @PathParam("tag") String versionTag);

    @GET
    @Path("/{instance}/{tag}/minionConfiguration")
    public Map<String, MinionDto> getMinionConfiguration(@ActivityScope @PathParam("instance") String instanceId,
            @PathParam("tag") String versionTag);

    @GET
    @Path("/{instance}/{tag}/minionState")
    public Map<String, MinionStatusDto> getMinionState(@ActivityScope @PathParam("instance") String instanceId,
            @PathParam("tag") String versionTag);

    @GET
    @Path("/{instance}/{tag}/install")
    @RequiredPermission(permission = Permission.WRITE)
    public void install(@ActivityScope @PathParam("instance") String instanceId, @ActivityScope @PathParam("tag") String tag);

    @GET
    @Path("/{instance}/{tag}/uninstall")
    @RequiredPermission(permission = Permission.WRITE)
    public void uninstall(@ActivityScope @PathParam("instance") String instanceId, @ActivityScope @PathParam("tag") String tag);

    @GET
    @Path("/{instance}/{tag}/activate")
    @RequiredPermission(permission = Permission.WRITE)
    public void activate(@ActivityScope @PathParam("instance") String instanceId, @ActivityScope @PathParam("tag") String tag);

    @GET
    @Path("/{instance}/updateTo")
    @RequiredPermission(permission = Permission.WRITE)
    public void updateTo(@ActivityScope @PathParam("instance") String instanceId, @QueryParam("productTag") String productTag);

    @GET
    @Path("/{instance}/{tag}/history")
    public InstanceManifestHistoryDto getHistory(@ActivityScope @PathParam("instance") String instanceId,
            @ActivityScope @PathParam("tag") String tag);

    @GET
    @Path("/{instance}/state")
    public InstanceStateRecord getDeploymentStates(@ActivityScope @PathParam("instance") String instanceId);

    @GET
    @Path("/purposes")
    public List<InstancePurpose> getPurposes();

    @Path("/{instance}/processes")
    public ProcessResource getProcessResource(@ActivityScope @PathParam("instance") String instanceId);

    @Path("/{instance}/cfgFiles")
    public ConfigFileResource getConfigResource(@ActivityScope @PathParam("instance") String instanceId);

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
    @Unsecured
    @Path("/{instance}/export/{tag}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response exportInstance(@ActivityScope @PathParam("instance") String instanceId, @PathParam("tag") String tag);

    @POST
    @Path("/{instance}/import")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @RequiredPermission(permission = Permission.WRITE)
    public List<Key> importInstance(@FormDataParam("file") InputStream inputStream,
            @ActivityScope @PathParam("instance") String instanceId);

    @GET
    @Path("/{instance}/output/{tag}/{app}")
    public InstanceDirectory getOutputEntry(@ActivityScope @PathParam("instance") String instanceId,
            @ActivityScope @PathParam("tag") String tag, @PathParam("app") String app);

    @POST
    @Path("/{instance}/content/{minion}")
    public StringEntryChunkDto getContentChunk(@ActivityScope @PathParam("instance") String instanceId,
            @PathParam("minion") String minion, InstanceDirectoryEntry entry, @QueryParam("offset") long offset,
            @QueryParam("limit") long limit);

    @POST
    @Path("/{instance}/request/{minion}")
    public String getContentStreamRequest(@ActivityScope @PathParam("instance") String instanceId,
            @PathParam("minion") String minion, InstanceDirectoryEntry entry);

    @POST
    @Path("/{instance}/delete/{minion}")
    @RequiredPermission(permission = Permission.ADMIN)
    public void deleteDataFile(@ActivityScope @PathParam("instance") String instanceId, @PathParam("minion") String minion,
            InstanceDirectoryEntry entry);

    @GET
    @Unsecured
    @Path("/{instance}/stream/{token}")
    public Response getContentStream(@ActivityScope @PathParam("instance") String instanceId, @PathParam("token") String token);

    @POST
    @Path("/{instance}/check-ports/{minion}")
    public Map<Integer, Boolean> getPortStates(@ActivityScope @PathParam("instance") String instanceId,
            @PathParam("minion") String minion, List<Integer> ports);

    @GET
    @Path("/{instance}/history")
    public List<HistoryEntryDto> getInstanceHistory(@ActivityScope @PathParam("instance") String instanceId,
            @QueryParam("amount") int amount);

    @GET
    @Path("/{instance}/more-history")
    public List<HistoryEntryDto> getMoreInstanceHistory(@ActivityScope @PathParam("instance") String instanceId,
            @QueryParam("amount") int amount, @QueryParam("offset") int offset);

    @GET
    @Path("/{instance}/compare-versions")
    public HistoryEntryVersionDto compareInstanceHistory(@ActivityScope @PathParam("instance") String instanceId,
            @QueryParam("a") int versionA, @QueryParam("b") int versionB);

    @GET
    @Path("{instance}/filter-history")
    public List<HistoryEntryDto> filterInstanceHistory(@ActivityScope @PathParam("instance") String instanceId,
            @QueryParam("amount") int amount, @QueryParam("offset") int offset, @QueryParam("filter") String filter);
}
