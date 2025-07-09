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
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.glassfish.jersey.media.multipart.ContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.api.product.v1.impl.ScopedManifestKey;
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
import io.bdeploy.common.Version;
import io.bdeploy.common.actions.Actions;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.OsHelper.OperatingSystem;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.common.util.TemplateHelper;
import io.bdeploy.common.util.UuidHelper;
import io.bdeploy.common.util.VersionHelper;
import io.bdeploy.common.util.ZipHelper;
import io.bdeploy.interfaces.UpdateHelper;
import io.bdeploy.interfaces.remote.CommonUpdateResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.jersey.actions.ActionFactory;
import io.bdeploy.jersey.actions.ActionService.ActionHandle;
import io.bdeploy.ui.FormDataHelper;
import io.bdeploy.ui.api.BackendInfoResource;
import io.bdeploy.ui.api.Minion;
import io.bdeploy.ui.api.SoftwareUpdateResource;
import io.bdeploy.ui.dto.LauncherDto;
import io.bdeploy.ui.utils.WindowsInstaller;
import io.bdeploy.ui.utils.WindowsInstallerConfig;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.ResponseBuilder;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.StreamingOutput;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;

public class SoftwareUpdateResourceImpl implements SoftwareUpdateResource {

    private static final Logger log = LoggerFactory.getLogger(SoftwareUpdateResourceImpl.class);

    @Inject
    private SecurityContext context;

    @Inject
    private BHiveRegistry reg;

    @Inject
    private Minion minion;

    @Inject
    private ActionFactory af;

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
                .sorted(VersionComparator.BY_TAG_NEWEST_LAST).toList();
    }

    @Override
    public void updateSelf(List<Key> target) {
        // check if all minions are online.
        BackendInfoResource bi = rc.initResource(new BackendInfoResourceImpl());
        Set<OperatingSystem> required = EnumSet.noneOf(OperatingSystem.class);
        for (var entry : bi.getNodeStatus().entrySet()) {
            required.add(entry.getValue().config.os);
        }

        // check if we have all required update packages for each OS.
        Set<OperatingSystem> provided = target.stream().map(ScopedManifestKey::parse).filter(Objects::nonNull)
                .map(ScopedManifestKey::getOperatingSystem).collect(Collectors.toSet());
        for (var reqOs : required) {
            if (!provided.contains(reqOs)) {
                throw new WebApplicationException(
                        "Missing update package for required operating system " + reqOs + ", cannot update",
                        Status.PRECONDITION_FAILED);
            }
        }

        UpdateHelper.update(minion.getSelf(), target, false, context);
    }

    @Override
    public List<Key> getLauncherVersions() {
        return getHive().execute(new ManifestListOperation().setManifestName(LAUNCHER_MF_NAME)).stream()
                .sorted(VersionComparator.BY_TAG_NEWEST_LAST).toList();
    }

    @Override
    public List<Key> uploadSoftware(FormDataMultiPart fdmp) {
        String tmpHiveName = UuidHelper.randomId() + ".zip";
        Path targetFile = minion.getDownloadDir().resolve(tmpHiveName);
        Path unpackTmp = minion.getTempDir().resolve(tmpHiveName + "_unpack");
        try {
            // Download the hive to a temporary location
            Files.copy(FormDataHelper.getStreamFromMultiPart(fdmp), targetFile);
            return UpdateHelper.importUpdate(targetFile, unpackTmp, getHive());
        } catch (IOException e) {
            throw new WebApplicationException("Failed to upload file: " + e.getMessage(), e, Status.BAD_REQUEST);
        } finally {
            PathHelper.deleteRecursiveRetry(unpackTmp);
            PathHelper.deleteRecursiveRetry(targetFile);
        }
    }

    @Override
    public void deleteVersions(List<Manifest.Key> keys) {
        try (ActionHandle h = af.runMulti(Actions.DELETE_UPDATES, null, null,
                keys.stream().map(Manifest.Key::getTag).distinct().toList())) {
            BHive hive = getHive();
            keys.forEach(k -> hive.execute(new ManifestDeleteOperation().setToDelete(k)));
            hive.execute(new PruneOperation());
        }
    }

    @Override
    public Response downloadLatestLauncherFor(String osName) {
        OperatingSystem os = OperatingSystem.valueOf(osName.toUpperCase());
        LauncherDto latestLaunchers = getLatestLaunchers();
        Manifest.Key key = latestLaunchers.launchers.get(os);

        if (key == null) {
            throw new WebApplicationException("Cannot find launcher for " + os, Status.NOT_FOUND);
        }

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
                    try {
                        PathHelper.deleteIfExistsRetry(tmpFile);
                        PathHelper.deleteRecursiveRetry(tmpFolder);
                    } catch (Exception e) {
                        log.warn("Cannot clean temporary files after packaging download", e);
                    }
                }
            } catch (IOException e) {
                throw new WebApplicationException("Error packaging download", e);
            }
        }

        // Build a response with the stream
        ResponseBuilder responeBuilder = Response.ok((StreamingOutput) output -> {
            try (InputStream is = Files.newInputStream(targetFile)) {
                is.transferTo(output);
            } catch (IOException ioe) {
                log.warn("Could not fully write output: {}", ioe.toString());
                if (log.isDebugEnabled()) {
                    log.debug("Exception", ioe);
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
        try (ActionHandle h = af.run(Actions.DOWNLOAD_LAUNCHER)) {
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
            switch (os) {
                case WINDOWS:
                    fileName = "BDeploy Click & Start - Installer.exe";
                    createWindowsInstaller(installerPath, launcherKey, launcherLocation);
                    break;
                case LINUX, LINUX_AARCH64:
                    fileName = "BDeploy-Click-and-Start-Installer.run";
                    createLinuxInstaller(installerPath, launcherKey, launcherLocation);
                    break;
                default:
                    throw new WebApplicationException(os.name() + " installer not yet supported.");
            }

            // Register the file for downloading
            ds.registerForDownload(token, fileName);
            return token;
        }
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

    private void createWindowsInstaller(Path installerPath, ScopedManifestKey launcherKey, URI launcherLocation) {
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
            WindowsInstallerConfig config = new WindowsInstallerConfig();
            config.launcherUrl = launcherLocation.toString();
            config.remoteService = createRemoteService();
            WindowsInstaller.embedConfig(installerPath, config);
        } catch (Exception ioe) {
            throw new WebApplicationException("Cannot embed configuration into windows installer.", ioe);
        }
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
        return versions.stream().map(ScopedManifestKey::parse).filter(Objects::nonNull)
                .filter(smk -> smk.getOperatingSystem() == os).filter(SoftwareUpdateResourceImpl::matchesRunningVersion)
                .reduce((first, second) -> second).orElse(null);
    }

    /** Returns whether or not this version matches the running version */
    private static boolean matchesRunningVersion(ScopedManifestKey smk) {
        // Development environment: Take latest available launcher
        if (VersionHelper.isRunningUndefined()) {
            return true;
        }
        // Otherwise take the manifest only if the version is matching
        Version running = VersionHelper.getVersion();
        Version launcherVersion = VersionHelper.tryParse(smk.getTag());
        return VersionHelper.equals(launcherVersion, running);
    }

    @Override
    public void restartServer() {
        CommonUpdateResource root = ResourceProvider.getResource(minion.getSelf(), CommonUpdateResource.class, context);
        log.warn("Explicit restart by user");
        root.restartServer();
    }

    @Override
    public void createStackDump() {
        CommonUpdateResource root = ResourceProvider.getResource(minion.getSelf(), CommonUpdateResource.class, context);
        root.createStackDump();
    }

}
