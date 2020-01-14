package io.bdeploy.ui.api;

import java.io.InputStream;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.glassfish.jersey.media.multipart.FormDataParam;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.common.security.RequiredCapability;
import io.bdeploy.common.security.ScopedCapability.Capability;
import io.bdeploy.jersey.JerseyAuthenticationProvider.Unsecured;
import io.bdeploy.ui.dto.LauncherDto;

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
    @RequiredCapability(capability = Capability.ADMIN)
    public void updateSelf(List<Manifest.Key> target);

    @GET
    @Path("/launcher")
    public List<Manifest.Key> getLauncherVersions();

    @GET
    @Path("/launcherLatest")
    public LauncherDto getLatestLaunchers();

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public List<Manifest.Key> uploadSoftware(@FormDataParam("file") InputStream inputStream);

    @POST // DELETE does not accept body for batch delete.
    @RequiredCapability(capability = Capability.ADMIN)
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

}
