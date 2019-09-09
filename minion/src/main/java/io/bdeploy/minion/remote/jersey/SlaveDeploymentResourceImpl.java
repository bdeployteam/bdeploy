package io.bdeploy.minion.remote.jersey;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.ActivityReporter.Activity;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.dcu.InstanceNodeController;
import io.bdeploy.interfaces.configuration.pcu.InstanceNodeStatusDto;
import io.bdeploy.interfaces.directory.EntryChunk;
import io.bdeploy.interfaces.directory.InstanceDirectoryEntry;
import io.bdeploy.interfaces.manifest.InstanceNodeManifest;
import io.bdeploy.interfaces.manifest.state.InstanceState;
import io.bdeploy.interfaces.manifest.state.InstanceStateRecord;
import io.bdeploy.interfaces.remote.SlaveDeploymentResource;
import io.bdeploy.interfaces.variables.DeploymentPathProvider;
import io.bdeploy.interfaces.variables.DeploymentPathProvider.SpecialDirectory;
import io.bdeploy.minion.MinionConfigVariableResolver;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.minion.MinionState;
import io.bdeploy.pcu.InstanceProcessController;
import io.bdeploy.pcu.MinionProcessController;

public class SlaveDeploymentResourceImpl implements SlaveDeploymentResource {

    @Inject
    private MinionRoot root;

    @Inject
    private ActivityReporter reporter;

    /**
     * @param inm the {@link InstanceNodeManifest} to read state from.
     * @return the {@link InstanceState}, potentially migrated from "old" information in {@link MinionState}.
     */
    private InstanceState getState(InstanceNodeManifest inm, BHive hive) {
        return inm.getState(hive).setMigrationProvider(() -> migrateState(inm.getUUID()));
    }

    /**
     * @deprecated only used to migrate state, see {@link #getState(InstanceNodeManifest, BHive)}
     */
    @Deprecated(forRemoval = true, since = "1.2.0")
    private InstanceStateRecord migrateState(String uuid) {
        InstanceStateRecord record = new InstanceStateRecord();
        // find all versions of the given INM.
        SortedSet<Key> manifests = InstanceNodeManifest.scan(root.getHive());
        for (Key key : manifests) {
            InstanceNodeManifest mf = InstanceNodeManifest.of(root.getHive(), key);
            if (!mf.getUUID().equals(uuid)) {
                continue;
            }
            InstanceNodeController controller = new InstanceNodeController(root.getHive(), root.getDeploymentDir(), mf);
            if (!controller.isInstalled()) {
                continue;
            }
            record.installedTags.add(mf.getKey().getTag());
        }

        Manifest.Key active = root.getState().activeVersions.get(uuid);
        record.activeTag = active == null ? null : active.getTag();

        return record;
    }

    @Override
    public void install(Key key) {
        BHive hive = root.getHive();

        Activity deploying = reporter.start("Deploying " + key);
        try {
            InstanceNodeManifest inm = InstanceNodeManifest.of(hive, key);
            InstanceNodeController inc = new InstanceNodeController(hive, root.getDeploymentDir(), inm);
            inc.addAdditionalVariableResolver(new MinionConfigVariableResolver(root));
            inc.install();
            getState(inm, hive).install(key.getTag());

            // Notify that there is a new deployment
            MinionProcessController processController = root.getProcessController();
            InstanceProcessController controller = processController.getOrCreate(inm.getUUID());
            controller.addProcessGroup(inc.getDeploymentPathProvider(), inm.getKey().getTag(),
                    inc.getProcessGroupConfiguration());
        } finally {
            deploying.done();
        }
    }

    @Override
    public void activate(Key key) {
        BHive hive = root.getHive();

        InstanceNodeManifest inm = InstanceNodeManifest.of(hive, key);
        InstanceNodeController toActivate = new InstanceNodeController(hive, root.getDeploymentDir(), inm);
        if (!toActivate.isInstalled()) {
            throw new WebApplicationException("Key " + key + " is not deployed", Status.NOT_FOUND);
        }

        // Notify that there is a new active version
        MinionProcessController processController = root.getProcessController();
        InstanceProcessController controller = processController.getOrCreate(inm.getUUID());
        controller.setActiveTag(key.getTag());
        getState(inm, hive).activate(key.getTag());
    }

    @Override
    public void remove(Key key) {
        BHive hive = root.getHive();

        InstanceNodeManifest inm = InstanceNodeManifest.of(hive, key);
        InstanceNodeController inc = new InstanceNodeController(hive, root.getDeploymentDir(), inm);

        // check currently active deployment
        MinionProcessController processController = root.getProcessController();
        InstanceProcessController controller = processController.getOrCreate(inm.getUUID());
        InstanceNodeStatusDto status = controller.getStatus();
        if (status.areAppsRunningOrScheduledInVersion(key.getName())) {
            throw new WebApplicationException("Key " + key + " has one ore more applications running.", Status.BAD_REQUEST);
        }

        // Remove active version from state if removed.
        getState(inm, hive).uninstall(key.getTag());

        // cleanup the deployment directory.
        inc.uninstall();
    }

    private InstanceNodeManifest findInstanceNodeManifest(String instanceId) {
        SortedSet<Key> manifests = InstanceNodeManifest.scan(root.getHive());
        for (Key key : manifests) {
            InstanceNodeManifest mf = InstanceNodeManifest.of(root.getHive(), key);
            if (!mf.getUUID().equals(instanceId)) {
                continue;
            }
            return mf;
        }
        return null;
    }

    @Override
    public InstanceStateRecord getInstanceState(String instanceId) {
        InstanceNodeManifest inmf = findInstanceNodeManifest(instanceId);
        if (inmf == null) {
            return null;
        }
        return getState(inmf, root.getHive()).read();
    }

    @Override
    public List<InstanceDirectoryEntry> getDataDirectoryEntries(String instanceId) {
        List<InstanceDirectoryEntry> result = new ArrayList<>();
        InstanceNodeManifest newest = findInstanceNodeManifest(instanceId);
        if (newest == null) {
            throw new WebApplicationException("Cannot find instance " + instanceId, Status.NOT_FOUND);
        }
        String activeTag = getState(newest, root.getHive()).read().activeTag;
        if (activeTag == null) {
            throw new WebApplicationException("Cannot find active version for instance " + instanceId, Status.NOT_FOUND);
        }

        Key activeKey = new Manifest.Key(newest.getKey().getName(), activeTag);

        InstanceNodeController inc = new InstanceNodeController(root.getHive(), root.getDeploymentDir(),
                InstanceNodeManifest.of(root.getHive(), activeKey));

        Path dataRoot = inc.getDeploymentPathProvider().get(SpecialDirectory.DATA);

        try (Stream<Path> paths = Files.walk(dataRoot)) {
            paths.filter(Files::isRegularFile).forEach(f -> {
                InstanceDirectoryEntry entry = new InstanceDirectoryEntry();
                File asFile = f.toFile();

                entry.path = PathHelper.separatorsToUnix(dataRoot.relativize(f));
                entry.lastModified = asFile.lastModified();
                entry.size = asFile.length();
                entry.root = SpecialDirectory.DATA;
                entry.uuid = instanceId;
                entry.tag = activeKey.getTag(); // providing the tag of the active version here

                result.add(entry);
            });
        } catch (IOException e) {
            throw new WebApplicationException("Cannot list files in data directory for instance " + instanceId, e);
        }

        return result;
    }

    @Override
    public EntryChunk getEntryContent(InstanceDirectoryEntry entry, long offset, long limit) {
        // determine file first...
        DeploymentPathProvider dpp = new DeploymentPathProvider(root.getDeploymentDir().resolve(entry.uuid), entry.tag);

        Path rootDir = dpp.get(entry.root).toAbsolutePath();
        Path actual = rootDir.resolve(entry.path);

        if (!actual.startsWith(rootDir)) {
            throw new WebApplicationException("Trying to escape " + rootDir, Status.BAD_REQUEST);
        }

        if (!Files.exists(actual)) {
            throw new WebApplicationException("Cannot find " + actual, Status.NOT_FOUND);
        }

        if (limit == 0) {
            limit = Long.MAX_VALUE;
        }

        File file = actual.toFile();
        long currentSize = file.length();
        if (currentSize < offset) {
            // file has been reset.
            return EntryChunk.ROLLOVER_CHUNK;
        } else if (currentSize > offset) {
            try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                raf.seek(offset);
                try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                    int b;
                    long c = 0;
                    while ((b = raf.read()) != -1) {
                        if (c++ >= limit) {
                            break;
                        }

                        baos.write(b);
                    }

                    return new EntryChunk(baos.toByteArray(), offset, raf.getFilePointer());
                }
            } catch (IOException e) {
                throw new WebApplicationException("Cannot read chunk of " + actual, e);
            }
        }

        return null; // offset == size...
    }

}
