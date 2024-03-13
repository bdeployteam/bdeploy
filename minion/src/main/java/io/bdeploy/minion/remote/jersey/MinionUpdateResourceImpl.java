package io.bdeploy.minion.remote.jersey;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.api.product.v1.impl.ScopedManifestKey;
import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.op.ExportOperation;
import io.bdeploy.bhive.op.ManifestDeleteOperation;
import io.bdeploy.bhive.op.ManifestListOperation;
import io.bdeploy.bhive.op.ManifestLoadOperation;
import io.bdeploy.bhive.op.ObjectListOperation;
import io.bdeploy.bhive.op.ObjectSizeOperation;
import io.bdeploy.bhive.op.TreeEntryLoadOperation;
import io.bdeploy.common.Version;
import io.bdeploy.common.actions.Actions;
import io.bdeploy.common.util.VersionHelper;
import io.bdeploy.interfaces.UpdateHelper;
import io.bdeploy.interfaces.remote.MinionUpdateResource;
import io.bdeploy.jersey.actions.ActionFactory;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.ui.api.MinionMode;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response.Status;

public class MinionUpdateResourceImpl implements MinionUpdateResource {

    private static final Logger log = LoggerFactory.getLogger(MinionUpdateResourceImpl.class);

    @Inject
    private MinionRoot root;

    @Inject
    private ActionFactory actions;

    @Override
    public void convertToNode() {
        // convert to type node.
        root.modifyState(s -> s.mode = MinionMode.NODE);

        // we cannot delete (or rename) them here and now, as still in use.
        // after re-launch we can no longer determine whether (and what) we should move/delete.
        log.warn("Storage locations of the server are now unused and can be deleted:");
        for (Path p : root.getStorageLocations()) {
            log.warn(" * {}", p);
        }

        // never-ending restart-server action which will notify the web-ui of pending restart.
        actions.run(Actions.RESTART_SERVER);
        root.getRestartManager().performRestart(1_000);
    }

    @Override
    public void update(Manifest.Key key) {
        Path updateTarget = root.getUpdateDir().resolve(UpdateHelper.UPDATE_DIR);

        if (!Files.isDirectory(updateTarget)) {
            throw new WebApplicationException("Update has not been prepared, missing " + updateTarget,
                    Status.PRECONDITION_FAILED);
        }

        // never-ending restart-server action which will notify the web-ui of pending restart.
        actions.run(Actions.RESTART_SERVER);
        root.getRestartManager().performRestart(1_000);
    }

    @Override
    public void prepare(Key key, boolean clean) {
        // cannot prevent at LEAST for unit tests.
        Version currentVersion = VersionHelper.getVersion();
        if (VersionHelper.isRunningUndefined()) {
            log.warn("Running version cannot be determined.");
        }

        BHive h = root.getHive();

        Set<Key> mfs = h.execute(new ManifestListOperation().setManifestName(key.toString()));
        if (!mfs.contains(key)) {
            throw new WebApplicationException("Cannot find version to update to: " + key, Status.NOT_FOUND);
        }

        // We need to make sure here that the update is compatible with the running server version.
        checkCompatiblity(key, h);

        Path updateDir = root.getUpdateDir();
        Path updateTarget = UpdateHelper.prepareUpdateDirectory(updateDir);

        checkDiscSpace(key, h);

        h.execute(new ExportOperation().setManifest(key).setTarget(updateTarget));

        // nodes /always/ clean, master might need to keep things (e.g. for web-ui).
        if (clean || !root.isMaster()) {
            // clean up any version from the hive which is not the currently running and not the new target version
            SortedSet<String> tagsToKeep = new TreeSet<>();
            tagsToKeep.add(key.getTag());
            tagsToKeep.add(currentVersion.toString());

            // there is a tiny (acceptable) potential for left-over minion versions: if the 'name' of the
            // to-be installed update diverges from the name of the currently installed version, versions
            // with the old name are not cleaned up properly. Since the name is calculated by us, this
            // is not much of a problem.

            String toList = key.getName();
            ScopedManifestKey smk = ScopedManifestKey.parse(key);
            if (smk != null) {
                // rather use the name without OS part to be able to cleanup 'foreign' OS update packages
                // as well.
                toList = smk.getName();
            }

            Set<Key> allVersions = h.execute(new ManifestListOperation().setManifestName(toList));
            allVersions.stream().filter(k -> !tagsToKeep.contains(k.getTag()))
                    .forEach(k -> h.execute(new ManifestDeleteOperation().setToDelete(k)));
        }

        // update is now prepared in the update dir of the root.
    }

    private void checkDiscSpace(Key key, BHive h) {
        try {
            FileStore store = Files.getFileStore(root.getUpdateDir());
            long space = store.getUsableSpace();

            Set<ObjectId> objects = h.execute(new ObjectListOperation().addManifest(key));
            ObjectSizeOperation sop = new ObjectSizeOperation();
            objects.forEach(sop::addObject);
            long required = h.execute(sop);

            if ((required * 2) >= space) {
                throw new WebApplicationException("Not enough space available to safely perform update",
                        Status.PRECONDITION_FAILED);
            }
        } catch (IOException e) {
            log.warn("Cannot check space requirements for update", e);
        }
    }

    private void checkCompatiblity(Key key, BHive h) {
        try (InputStream is = h.execute(
                new TreeEntryLoadOperation().setRootTree(h.execute(new ManifestLoadOperation().setManifest(key)).getRoot())
                        .setRelativePath("version.properties"))) {
            Properties props = new Properties();
            props.load(is);

            if (props.containsKey("minSourceVersion")) {
                Version version = VersionHelper.tryParse(props.getProperty("minSourceVersion"));
                if (version == null) {
                    log.error("Cannot parse minimum source server version from update package");
                } else {
                    if (VersionHelper.getVersion().compareTo(version) < 0) {
                        throw new IllegalStateException(
                                "Running version is not compatible with update package. Server must be at least version "
                                        + version + " to be able to update.");
                    }
                    log.info("Version compatibility check OK: current={}, required={}", VersionHelper.getVersion(), version);
                }
            }
        } catch (Exception e) {
            log.warn("Cannot read version.properties from update package.");
            if (log.isTraceEnabled()) {
                log.trace("Exception:", e);
            }
        }
    }

}
