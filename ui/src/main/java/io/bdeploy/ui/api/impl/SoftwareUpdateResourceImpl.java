package io.bdeploy.ui.api.impl;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import org.glassfish.jersey.media.multipart.ContentDisposition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.op.ExportOperation;
import io.bdeploy.bhive.op.ManifestDeleteOperation;
import io.bdeploy.bhive.op.ManifestListOperation;
import io.bdeploy.bhive.op.ManifestLoadOperation;
import io.bdeploy.bhive.op.PruneOperation;
import io.bdeploy.bhive.op.TreeEntryLoadOperation;
import io.bdeploy.bhive.remote.jersey.BHiveRegistry;
import io.bdeploy.bhive.remote.jersey.JerseyRemoteBHive;
import io.bdeploy.bhive.util.VersionComparator;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.OsHelper;
import io.bdeploy.common.util.OsHelper.OperatingSystem;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.common.util.TemplateHelper;
import io.bdeploy.common.util.UuidHelper;
import io.bdeploy.common.util.ZipHelper;
import io.bdeploy.interfaces.ScopedManifestKey;
import io.bdeploy.interfaces.UpdateHelper;
import io.bdeploy.ui.api.Minion;
import io.bdeploy.ui.api.SoftwareUpdateResource;
import io.bdeploy.ui.branding.Branding;
import io.bdeploy.ui.branding.BrandingConfig;
import io.bdeploy.ui.dto.LauncherDto;

public class SoftwareUpdateResourceImpl implements SoftwareUpdateResource {

    private static final Logger log = LoggerFactory.getLogger(SoftwareUpdateResourceImpl.class);

    @Inject
    private SecurityContext context;

    @Inject
    private BHiveRegistry reg;

    @Inject
    private Minion minion;

    @Context
    private UriInfo info;

    @Context
    private ResourceContext rc;

    private BHive getHive() {
        return reg.get(JerseyRemoteBHive.DEFAULT_NAME);
    }

    @Override
    public List<Key> getBDeployVersions() {
        return getHive().execute(new ManifestListOperation().setManifestName(BDEPLOY_MF_NAME)).stream()
                .sorted(VersionComparator.BY_TAG_NEWEST_LAST).collect(Collectors.toList());
    }

    @Override
    public void updateSelf(List<Key> target) {
        UpdateHelper.update(minion.getSelf(), context, OsHelper.getRunningOs(), target, false);
    }

    @Override
    public List<Key> getLauncherVersions() {
        return getHive().execute(new ManifestListOperation().setManifestName(LAUNCHER_MF_NAME)).stream()
                .sorted(VersionComparator.BY_TAG_NEWEST_LAST).collect(Collectors.toList());
    }

    @Override
    public List<Key> uploadSoftware(InputStream inputStream) {
        String tmpHiveName = UuidHelper.randomId() + ".zip";
        Path targetFile = minion.getDownloadDir().resolve(tmpHiveName);
        Path unpackTmp = minion.getTempDir().resolve(tmpHiveName + "_unpack");
        try {
            // Download the hive to a temporary location
            Files.copy(inputStream, targetFile);
            return UpdateHelper.importUpdate(targetFile, unpackTmp, getHive());
        } catch (IOException e) {
            throw new WebApplicationException("Failed to upload file: " + e.getMessage(), Status.BAD_REQUEST);
        } finally {
            PathHelper.deleteRecursive(unpackTmp);
            PathHelper.deleteRecursive(targetFile);
        }
    }

    @Override
    public void deleteVersions(List<Manifest.Key> keys) {
        BHive hive = getHive();
        keys.forEach(k -> hive.execute(new ManifestDeleteOperation().setToDelete(k)));
        hive.execute(new PruneOperation());
    }

    @Override
    public Response downloadLatestLauncherFor(String osName) {
        OperatingSystem os = OperatingSystem.valueOf(osName.toUpperCase());
        LauncherDto latestLaunchers = getLatestLaunchers();
        Manifest.Key key = latestLaunchers.launchers.get(os);

        URI target = UriBuilder.fromUri(info.getBaseUri()).path(SoftwareUpdateResource.ROOT_PATH)
                .path(SoftwareUpdateResource.DOWNLOAD_PATH).build(new Object[] { key.getName(), key.getTag() }, false);

        return Response.temporaryRedirect(target).build();
    }

    @Override
    public Response downloadSoftware(String name, String tag) {
        Manifest.Key key = new Manifest.Key(name, tag);
        Path targetFile = minion.getTempDir().resolve(key.directoryFriendlyName() + ".zip");

        File file = targetFile.toFile();
        if (!file.isFile()) {
            try {
                // build ZIP from key.
                Path tmpFile = Files.createTempFile(minion.getTempDir(), "sw-", ".zip");
                Path tmpFolder = minion.getTempDir().resolve(key.directoryFriendlyName());

                try {
                    // add once more the directoryFriendlyName, as it should be included in the ZIP!
                    getHive().execute(
                            new ExportOperation().setManifest(key).setTarget(tmpFolder.resolve(key.directoryFriendlyName())));

                    ZipHelper.zip(tmpFile, tmpFolder);
                    Files.copy(tmpFile, targetFile);
                } finally {
                    Files.deleteIfExists(tmpFile);
                    PathHelper.deleteRecursive(tmpFolder);
                }
            } catch (IOException e) {
                throw new WebApplicationException("Error packaging download", e);
            }
        }

        // Build a response with the stream
        ResponseBuilder responeBuilder = Response.ok(new StreamingOutput() {

            @Override
            public void write(OutputStream output) throws IOException {
                try (InputStream is = Files.newInputStream(targetFile)) {
                    is.transferTo(output);
                } catch (IOException ioe) {
                    if (log.isDebugEnabled()) {
                        log.debug("Could not fully write output", ioe);
                    } else {
                        log.warn("Could not fully write output: {}", ioe);
                    }
                }
            }
        }, MediaType.APPLICATION_OCTET_STREAM);

        // Load and attach metadata to give the file a nice name
        ContentDisposition contentDisposition = ContentDisposition.type("attachement").size(file.length())
                .fileName(targetFile.getFileName().toString()).build();
        responeBuilder.header("Content-Disposition", contentDisposition);
        responeBuilder.header("Content-Length", file.length());
        return responeBuilder.build();
    }

    @Override
    public LauncherDto getLatestLaunchers() {
        LauncherDto dto = new LauncherDto();
        for (OperatingSystem os : OperatingSystem.values()) {
            ScopedManifestKey scopedKey = getNewestLauncher(os);
            if (scopedKey == null) {
                continue;
            }
            dto.launchers.put(os, scopedKey.getKey());
        }
        return dto;
    }

    @Override
    public String createLauncherInstallerFor(String osName) {
        OperatingSystem os = OperatingSystem.valueOf(osName.toUpperCase());
        ScopedManifestKey launcherKey = getNewestLauncher(os);

        // Request a new file where we can store the launcher
        DownloadServiceImpl ds = rc.initResource(new DownloadServiceImpl());
        String token = ds.createNewToken();
        Path installerPath = ds.getStoragePath(token);

        UriBuilder launcherUri = UriBuilder.fromUri(info.getBaseUri());
        launcherUri.path(SoftwareUpdateResource.ROOT_PATH);
        launcherUri.path(SoftwareUpdateResource.DOWNLOAD_LATEST_PATH);
        URI launcherLocation = launcherUri.build(new Object[] { os.name().toLowerCase() }, false);

        String fileName = null;
        if (os == OperatingSystem.WINDOWS) {
            String installerName = "BDeploy Click & Start - Installer";
            fileName = installerName + ".exe";
            createWindowsInstaller(installerName, installerPath, launcherKey, launcherLocation);
        } else if (os == OperatingSystem.LINUX || os == OperatingSystem.MACOS) {
            fileName = "BDeploy-Click-and-Start-Installer.run";
            createLinuxInstaller(installerPath, launcherKey, launcherLocation);
        } else {
            throw new WebApplicationException("MAC OS Installer not yet supported");
        }

        // Register the file for downloading
        ds.registerForDownload(token, fileName);
        return token;
    }

    private void createLinuxInstaller(Path installerPath, ScopedManifestKey launcherKey, URI launcherLocation) {
        BHive rootHive = getHive();
        Manifest mf = rootHive.execute(new ManifestLoadOperation().setManifest(launcherKey.getKey()));
        TreeEntryLoadOperation findInstallerOp = new TreeEntryLoadOperation().setRootTree(mf.getRoot())
                .setRelativePath(INSTALLER_SH);
        String template;
        try (InputStream in = rootHive.execute(findInstallerOp); ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            in.transferTo(os);
            template = os.toString(StandardCharsets.UTF_8);
        } catch (IOException ioe) {
            throw new WebApplicationException("Cannot create linux installer.", ioe);
        }

        // must match the values required in the installer.tpl file
        Map<String, String> values = new TreeMap<>();
        values.put("LAUNCHER_URL", launcherLocation.toString());
        RemoteService rs = createRemoteService();
        values.put("REMOTE_SERVICE_URL", rs.getUri().toString());
        values.put("REMOTE_SERVICE_TOKEN", rs.getAuthPack());
        values.put("ICON_URL", "");
        values.put("APP_UID", "");
        values.put("APP_NAME", "");
        values.put("BDEPLOY_FILE", "");

        String content = TemplateHelper.process(template, values::get);
        try {
            Files.write(installerPath, content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new WebApplicationException("Cannot write installer " + installerPath, e);
        }
    }

    private void createWindowsInstaller(String installerName, Path installerPath, ScopedManifestKey launcherKey,
            URI launcherLocation) {
        File installer = installerPath.toFile();

        // Try to load the installer stored in the manifest tree
        BHive rootHive = getHive();
        Manifest mf = rootHive.execute(new ManifestLoadOperation().setManifest(launcherKey.getKey()));
        TreeEntryLoadOperation findInstallerOp = new TreeEntryLoadOperation().setRootTree(mf.getRoot())
                .setRelativePath(INSTALLER_EXE);
        try (InputStream in = rootHive.execute(findInstallerOp); OutputStream os = Files.newOutputStream(installerPath)) {
            in.transferTo(os);
        } catch (IOException ioe) {
            throw new WebApplicationException("Cannot create windows installer.", ioe);
        }

        // Brand the executable and embed the required information
        try {
            BrandingConfig config = new BrandingConfig();
            config.launcherUrl = launcherLocation.toString();
            config.remoteService = createRemoteService();

            Branding branding = new Branding(installer);
            branding.updateConfig(config);
            branding.write(installer);
        } catch (Exception ioe) {
            throw new WebApplicationException("Cannot apply branding to windows installer.", ioe);
        }

        // Now sign the executable with our certificate
        minion.signExecutable(installer, installerName, info.getBaseUri().toString());
    }

    /**
     * Creates and returns a remote service with a weak token to download files provided by the minion.
     */
    private RemoteService createRemoteService() {
        String userName = context.getUserPrincipal().getName();
        return new RemoteService(info.getBaseUri(), minion.createWeakToken(userName));
    }

    /**
     * Returns the manifest key under which the newest launcher for the given OS is stored.
     *
     * @param os the desired OS
     * @return {@code null} if there is no launcher or the launcher key
     */
    public ScopedManifestKey getNewestLauncher(OperatingSystem os) {
        List<Key> versions = getLauncherVersions();
        return versions.stream().map(ScopedManifestKey::parse).filter(smk -> smk.getOperatingSystem() == os)
                .reduce((first, second) -> second).orElse(null);
    }

}
