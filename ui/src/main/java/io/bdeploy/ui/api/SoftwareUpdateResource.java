package io.bdeploy.ui.api;

import java.util.List;

import org.glassfish.jersey.media.multipart.FormDataMultiPart;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.common.security.RequiredPermission;
import io.bdeploy.common.security.ScopedPermission.Permission;
import io.bdeploy.jersey.JerseyAuthenticationProvider.Unsecured;
import io.bdeploy.ui.dto.LauncherDto;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Provides API to remote-update the master as well as the launcher software.
 */
@Path(SoftwareUpdateResource.ROOT_PATH)
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface SoftwareUpdateResource {

    /**
     * Name and path of the native Windows installer stored in the launcher ZIP
     */
    public static final String INSTALLER_EXE = "bin/Installer.bin";

    /**
     * Name and path of the native Linux (shell script) installer template stored in the launcher ZIP
     */
    public static final String INSTALLER_SH = "bin/installer.tpl";

    /**
     * The name of the manifest used to store bdeploy versions.
     */
    public static final String BDEPLOY_MF_NAME = "meta/bdeploy";

    /**
     * The name of the manifest used to store launcher versions.
     */
    public static final String LAUNCHER_MF_NAME = "meta/launcher";

    public static final String ROOT_PATH = "/swup";
    public static final String DOWNLOAD_PATH = "/download/{name : .+}/{tag}";
    public static final String DOWNLOAD_LATEST_PATH = "/download/latest/{os}";

    @GET
    @Path("/bdeploy")
    public List<Manifest.Key> getBDeployVersions();

    @POST
    @Path("/selfUpdate")
    @RequiredPermission(permission = Permission.ADMIN)
    public void updateSelf(List<Manifest.Key> target);

    @GET
    @Path("/launcher")
    public List<Manifest.Key> getLauncherVersions();

    @GET
    @Path("/launcherLatest")
    public LauncherDto getLatestLaunchers();

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public List<Manifest.Key> uploadSoftware(FormDataMultiPart fdmp);

    @POST // DELETE does not accept body for batch delete.
    @RequiredPermission(permission = Permission.ADMIN)
    public void deleteVersions(List<Manifest.Key> keys);

    @GET
    @Unsecured
    @Path(DOWNLOAD_LATEST_PATH)
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response downloadLatestLauncherFor(@PathParam("os") String osName);

    @GET
    @Unsecured
    @Path(DOWNLOAD_PATH)
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response downloadSoftware(@PathParam("name") String name, @PathParam("tag") String tag);

    @GET
    @Path("/createLauncherInstaller")
    @Produces(MediaType.TEXT_PLAIN)
    public String createLauncherInstallerFor(@QueryParam("os") String osName);

    @GET
    @Path("/restart")
    public void restartServer();

    @GET
    @Path("/stackdump")
    public void createStackDump();

}
