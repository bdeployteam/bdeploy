package io.bdeploy.minion.remote.jersey;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;

import org.jvnet.hk2.annotations.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.op.ExportOperation;
import io.bdeploy.bhive.op.ManifestDeleteOperation;
import io.bdeploy.bhive.op.ManifestListOperation;
import io.bdeploy.bhive.op.ObjectListOperation;
import io.bdeploy.bhive.op.ObjectSizeOperation;
import io.bdeploy.common.util.VersionHelper;
import io.bdeploy.interfaces.ScopedManifestKey;
import io.bdeploy.interfaces.UpdateHelper;
import io.bdeploy.interfaces.remote.MinionUpdateResource;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.ui.api.Minion;

public class MinionUpdateResourceImpl implements MinionUpdateResource {

    private static final Logger log = LoggerFactory.getLogger(MinionUpdateResourceImpl.class);
    @Inject
    private MinionRoot root;

    @Inject
    @Optional
    @Named(Minion.MASTER)
    private Boolean isMaster;

    @Override
    public void update(Manifest.Key key) {
        Path updateTarget = root.getUpdateDir().resolve(UpdateHelper.UPDATE_DIR);

        if (!Files.isDirectory(updateTarget)) {
            throw new WebApplicationException("Update has not been prepared, missing " + updateTarget,
                    Status.PRECONDITION_FAILED);
        }

        root.getUpdateManager().performUpdate(1_000);
    }

    @Override
    public void prepare(Key key, boolean clean) {
        String currentVersion = VersionHelper.readVersion();
        if (currentVersion.equals(VersionHelper.UNKNOWN)) {
            // cannot prevent at LEAST for unit tests.
            log.error("I don't know my own version, this might not work well");
        }

        BHive h = root.getHive();

        SortedSet<Key> mfs = h.execute(new ManifestListOperation().setManifestName(key.toString()));
        if (!mfs.contains(key)) {
            throw new WebApplicationException("Cannot find version to update to: " + key, Status.NOT_FOUND);
        }

        Path updateDir = root.getUpdateDir();
        Path updateTarget = UpdateHelper.prepareUpdateDirectory(updateDir);

        try {
            FileStore store = Files.getFileStore(root.getUpdateDir());
            long space = store.getUsableSpace();

            SortedSet<ObjectId> objects = h.execute(new ObjectListOperation().addManifest(key));
            ObjectSizeOperation sop = new ObjectSizeOperation();
            objects.forEach(sop::addObject);
            long required = h.execute(sop);

            if ((required * 2) >= space) {
                throw new WebApplicationException("Not enough space available to savely perform update",
                        Status.PRECONDITION_FAILED);
            }
        } catch (IOException e) {
            log.warn("Cannot check space requirements for update", e);
        }

        h.execute(new ExportOperation().setManifest(key).setTarget(updateTarget));

        // slaves /always/ clean, master might need to keep things (e.g. for web-ui).
        if (clean || isMaster == null || !isMaster) {
            // clean up any version from the hive which is not the currently running and not the new target version
            SortedSet<String> tagsToKeep = new TreeSet<>();
            tagsToKeep.add(key.getTag());
            tagsToKeep.add(currentVersion);

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

            SortedSet<Key> allVersions = h.execute(new ManifestListOperation().setManifestName(toList));
            allVersions.stream().filter(k -> !tagsToKeep.contains(k.getTag()))
                    .forEach(k -> h.execute(new ManifestDeleteOperation().setToDelete(k)));
        }

        // update is now prepared in the update dir of the root.
    }

}
