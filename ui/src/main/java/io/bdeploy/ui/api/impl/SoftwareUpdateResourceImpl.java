package io.bdeploy.ui.api.impl;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;
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
import io.bdeploy.bhive.op.PruneOperation;
import io.bdeploy.bhive.remote.jersey.BHiveRegistry;
import io.bdeploy.bhive.remote.jersey.JerseyRemoteBHive;
import io.bdeploy.common.util.OsHelper;
import io.bdeploy.common.util.OsHelper.OperatingSystem;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.common.util.UuidHelper;
import io.bdeploy.common.util.ZipHelper;
import io.bdeploy.interfaces.NodeStatus;
import io.bdeploy.interfaces.ScopedManifestKey;
import io.bdeploy.interfaces.UpdateHelper;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.ui.api.Minion;
import io.bdeploy.ui.api.SoftwareUpdateResource;
import io.bdeploy.ui.dto.LauncherDto;

public class SoftwareUpdateResourceImpl implements SoftwareUpdateResource {

    private static final Logger log = LoggerFactory.getLogger(SoftwareUpdateResourceImpl.class);

    private static final String BDEPLOY_MF_NAME = "meta/bdeploy";
    private static final String LAUNCHER_MF_NAME = "meta/launcher";
    private static final Comparator<Key> BY_TAG_NEWEST_LAST = (a, b) -> a.getTag().compareTo(b.getTag());

    @Inject
    private MasterRootResource master;

    @Inject
    private BHiveRegistry reg;

    @Inject
    private Minion minion;

    @Context
    private UriInfo info;

    private BHive getHive() {
        return reg.get(JerseyRemoteBHive.DEFAULT_NAME);
    }

    @Override
    public List<Key> getBDeployVersions() {
        return getHive().execute(new ManifestListOperation().setManifestName(BDEPLOY_MF_NAME)).stream().sorted(BY_TAG_NEWEST_LAST)
                .collect(Collectors.toList());
    }

    @Override
    public List<NodeStatus> getMinionNodes() {
        return new ArrayList<>(master.getMinions().values());
    }

    @Override
    public void updateSelf(List<Key> target) {
        // delegate to the actual master resource
        target.stream().map(ScopedManifestKey::parse).sorted((a, b) -> {
            if (a.getOperatingSystem() != b.getOperatingSystem()) {
                // put own OS last.
                return a.getOperatingSystem() == OsHelper.getRunningOs() ? 1 : -1;
            }
            return a.getKey().toString().compareTo(b.getKey().toString());
        }).forEach(k -> master.update(k.getKey(), false));
    }

    @Override
    public List<Key> getLauncherVersions() {
        return getHive().execute(new ManifestListOperation().setManifestName(LAUNCHER_MF_NAME)).stream()
                .sorted(BY_TAG_NEWEST_LAST).collect(Collectors.toList());
    }

    @Override
    public List<Key> uploadSoftware(InputStream inputStream) {
        String tmpHiveName = UuidHelper.randomId() + ".zip";
        Path targetFile = minion.getDownloadDir().resolve(tmpHiveName);
        Path unpackTmp = minion.getTempDir().resolve(tmpHiveName + "_unpack");
        try {
            // Download the hive to a temporary location
            Files.copy(inputStream, targetFile);
            return Collections.singletonList(UpdateHelper.importUpdate(targetFile, unpackTmp, getHive()));
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
