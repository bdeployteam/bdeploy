package io.bdeploy.ui.api.impl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.op.ManifestDeleteOperation;
import io.bdeploy.bhive.op.ManifestListOperation;
import io.bdeploy.bhive.op.PruneOperation;
import io.bdeploy.bhive.remote.jersey.BHiveRegistry;
import io.bdeploy.bhive.remote.jersey.JerseyRemoteBHive;
import io.bdeploy.common.util.OsHelper;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.common.util.UuidHelper;
import io.bdeploy.interfaces.NodeStatus;
import io.bdeploy.interfaces.ScopedManifestKey;
import io.bdeploy.interfaces.UpdateHelper;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.ui.api.Minion;
import io.bdeploy.ui.api.SoftwareUpdateResource;

public class SoftwareUpdateResourceImpl implements SoftwareUpdateResource {

    private static final String BDEPLOY_MF_NAME = "meta/bdeploy";
    private static final String LAUNCHER_MF_NAME = "meta/launcher";
    private static final Comparator<Key> BY_TAG_NEWEST_LAST = (a, b) -> a.getTag().compareTo(b.getTag());

    @Inject
    private MasterRootResource master;

    @Inject
    private BHiveRegistry reg;

    @Inject
    private Minion minion;

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
        }).forEach(k -> {
            master.update(k.getKey(), false);
        });
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
        // TODO: delete from nodes!
        BHive hive = getHive();
        keys.forEach(k -> hive.execute(new ManifestDeleteOperation().setToDelete(k)));
        hive.execute(new PruneOperation());
    }

}
