package io.bdeploy.minion.remote.jersey;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.ActivityReporter.Activity;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.dcu.InstanceNodeController;
import io.bdeploy.interfaces.configuration.pcu.InstanceNodeStatusDto;
import io.bdeploy.interfaces.directory.EntryChunk;
import io.bdeploy.interfaces.directory.InstanceDirectoryEntry;
import io.bdeploy.interfaces.manifest.InstanceNodeManifest;
import io.bdeploy.interfaces.remote.SlaveDeploymentResource;
import io.bdeploy.interfaces.variables.DeploymentPathProvider;
import io.bdeploy.interfaces.variables.DeploymentPathProvider.SpecialDirectory;
import io.bdeploy.minion.MinionConfigVariableResolver;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.pcu.InstanceProcessController;
import io.bdeploy.pcu.MinionProcessController;

public class SlaveDeploymentResourceImpl implements SlaveDeploymentResource {

    @Inject
    private MinionRoot root;

    @Inject
    private ActivityReporter reporter;

    @Override
    public void install(Key key) {
        BHive hive = root.getHive();

        Activity deploying = reporter.start("Deploying " + key);
        try {
            InstanceNodeManifest inm = InstanceNodeManifest.of(hive, key);
            InstanceNodeController inc = new InstanceNodeController(hive, root.getDeploymentDir(), inm);
            inc.addAdditionalVariableResolver(new MinionConfigVariableResolver(root));
            inc.install();

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
        root.modifyState(s -> s.activeVersions.put(inm.getUUID(), key));
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
        Key activeVersion = root.getState().activeVersions.get(inm.getUUID());
        if (key.equals(activeVersion)) {
            root.modifyState(s -> s.activeVersions.remove(inm.getUUID()));
        }

        // cleanup the deployment directory.
        inc.uninstall();
    }

    @Override
    public SortedSet<Key> getAvailableDeploymentsOfInstance(String instanceId) {
        SortedSet<Key> manifests = InstanceNodeManifest.scan(root.getHive());
        TreeSet<Key> result = new TreeSet<>();
        for (Key key : manifests) {
            InstanceNodeManifest mf = InstanceNodeManifest.of(root.getHive(), key);
            if (!mf.getUUID().equals(instanceId)) {
                continue;
            }
            InstanceNodeController controller = new InstanceNodeController(root.getHive(), root.getDeploymentDir(), mf);
            if (!controller.isInstalled()) {
                continue;
            }
            result.add(mf.getKey());
        }
        return result;
    }

    @Override
    public SortedMap<String, Key> getActiveDeployments() {
        return root.getState().activeVersions;
    }

    @Override
    public List<InstanceDirectoryEntry> getDataDirectoryEntries(String instanceId) {
        List<InstanceDirectoryEntry> result = new ArrayList<>();
        Key key = getActiveDeployments().get(instanceId);

        if (key == null) {
            throw new WebApplicationException("Cannot find active version for instance " + instanceId, Status.NOT_FOUND);
        }

        InstanceNodeController inc = new InstanceNodeController(root.getHive(), root.getDeploymentDir(),
                InstanceNodeManifest.of(root.getHive(), key));

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
                entry.tag = key.getTag(); // providing the tag of the active version here

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
