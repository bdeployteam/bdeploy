package io.bdeploy.minion.remote.jersey;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.glassfish.jersey.media.multipart.ContentDisposition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.j256.simplemagic.ContentInfo;
import com.j256.simplemagic.ContentInfoUtil;

import io.bdeploy.api.deploy.v1.InstanceDeploymentInformationApi;
import io.bdeploy.api.remote.v1.dto.InstanceConfigurationApi;
import io.bdeploy.api.remote.v1.dto.InstanceConfigurationApi.InstancePurposeApi;
import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.op.ManifestDeleteOperation;
import io.bdeploy.bhive.op.ManifestExistsOperation;
import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.ActivityReporter.Activity;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.dcu.InstanceNodeController;
import io.bdeploy.interfaces.configuration.instance.InstanceNodeConfiguration;
import io.bdeploy.interfaces.configuration.pcu.InstanceNodeStatusDto;
import io.bdeploy.interfaces.directory.EntryChunk;
import io.bdeploy.interfaces.directory.RemoteDirectoryEntry;
import io.bdeploy.interfaces.manifest.InstanceNodeManifest;
import io.bdeploy.interfaces.manifest.state.InstanceState;
import io.bdeploy.interfaces.manifest.state.InstanceStateRecord;
import io.bdeploy.interfaces.remote.NodeDeploymentResource;
import io.bdeploy.interfaces.variables.DeploymentPathProvider;
import io.bdeploy.interfaces.variables.DeploymentPathProvider.SpecialDirectory;
import io.bdeploy.jersey.fs.FileSystemSpaceService;
import io.bdeploy.minion.MinionConfigVariableResolver;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.minion.MinionState;
import io.bdeploy.pcu.InstanceProcessController;
import io.bdeploy.pcu.MinionProcessController;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.ResponseBuilder;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.StreamingOutput;

public class NodeDeploymentResourceImpl implements NodeDeploymentResource {

    private static final Logger log = LoggerFactory.getLogger(NodeDeploymentResourceImpl.class);

    @Inject
    private MinionRoot root;

    @Inject
    private ActivityReporter reporter;

    @Inject
    private FileSystemSpaceService fsss;

    /**
     * @param inm the {@link InstanceNodeManifest} to read state from.
     * @return the {@link InstanceState}, potentially migrated from "old" information in {@link MinionState}.
     */
    private InstanceState getState(InstanceNodeManifest inm, BHive hive) {
        return inm.getState(hive);
    }

    @Override
    public void install(Key key) {
        BHive hive = root.getHive();
        if (!fsss.hasFreeSpace(root.getDeploymentDir())) {
            throw new WebApplicationException("Not enough free space in " + root.getDeploymentDir(), Status.SERVICE_UNAVAILABLE);
        }
        try (Activity deploying = reporter.start("Deploying " + key)) {
            InstanceNodeManifest inm = InstanceNodeManifest.of(hive, key);
            InstanceNodeController inc = new InstanceNodeController(hive, root.getDeploymentDir(), inm);
            inc.addAdditionalVariableResolver(new MinionConfigVariableResolver(root));
            inc.install();
            getState(inm, hive).install(key.getTag());

            // Notify that there is a new deployment
            MinionProcessController processController = root.getProcessController();
            InstanceProcessController controller = processController.getOrCreate(hive, inm);
            controller.createProcessControllers(inc.getDeploymentPathProvider(), inc.getResolver(), inm.getKey().getTag(),
                    inc.getProcessGroupConfiguration(), inm.getRuntimeHistory(hive));
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
        InstanceProcessController controller = processController.getOrCreate(hive, inm);
        controller.setActiveTag(key.getTag());
        getState(inm, hive).activate(key.getTag());

        // update the public descriptor
        updateDeploymentInfoFile(toActivate.getDeploymentPathProvider(), inm);
    }

    private void updateDeploymentInfoFile(DeploymentPathProvider deploymentPathProvider, InstanceNodeManifest inm) {
        BHive hive = root.getHive();
        Path file = deploymentPathProvider.get(SpecialDirectory.ROOT).resolve(InstanceDeploymentInformationApi.FILE_NAME);

        // instance description is not available on the node, thus we cannot provide this information here
        InstanceDeploymentInformationApi desc = new InstanceDeploymentInformationApi();
        InstanceNodeConfiguration cfg = inm.getConfiguration();
        desc.instance = new InstanceConfigurationApi();
        desc.instance.uuid = cfg.uuid;
        desc.instance.name = cfg.name;
        desc.instance.product = cfg.product;
        desc.instance.purpose = cfg.purpose == null ? null : InstancePurposeApi.valueOf(cfg.purpose.name());

        desc.activeInstanceVersion = getState(inm, hive).read().activeTag;

        try (OutputStream os = Files.newOutputStream(file, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.SYNC)) {
            os.write(StorageHelper.toRawBytes(desc));
        } catch (Exception e) {
            log.warn("Cannot write information file to {}", file, e);
        }
    }

    @Override
    public void deactivate(Key key) {
        BHive hive = root.getHive();

        if (!Boolean.TRUE.equals(hive.execute(new ManifestExistsOperation().setManifest(key)))) {
            return;
        }

        InstanceNodeManifest inm = InstanceNodeManifest.of(hive, key);

        // tell the process controller that there is no active tag anymore...
        MinionProcessController processController = root.getProcessController();
        InstanceProcessController controller = processController.getOrCreate(hive, inm);
        controller.setActiveTag(null);

        // deactivate by marking as removed and re-installed (there is no actual de-activation).
        getState(inm, hive).uninstall(key.getTag());
        getState(inm, hive).install(key.getTag());
    }

    @Override
    public void remove(Key key) {
        BHive hive = root.getHive();

        if (!Boolean.TRUE.equals(hive.execute(new ManifestExistsOperation().setManifest(key)))) {
            return;
        }

        InstanceNodeManifest inm = InstanceNodeManifest.of(hive, key);
        InstanceNodeController inc = new InstanceNodeController(hive, root.getDeploymentDir(), inm);

        // check currently active deployment
        MinionProcessController processController = root.getProcessController();
        InstanceProcessController controller = processController.getOrCreate(hive, inm);
        InstanceNodeStatusDto status = controller.getStatus();
        if (status.areAppsRunningOrScheduledInVersion(key.getName())) {
            throw new WebApplicationException("Key " + key + " has one or more applications running.", Status.BAD_REQUEST);
        }

        // Remove active version from state if removed.
        getState(inm, hive).uninstall(key.getTag());

        // cleanup the deployment directory.
        inc.uninstall();

        // Remove the InstanceNodeManifest
        hive.execute(new ManifestDeleteOperation().setToDelete(key));
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
            // happens if no version of the instance was actually ever pushed to the node.
            return new InstanceStateRecord();
        }
        return getState(inmf, root.getHive()).read();
    }

    @Override
    public List<RemoteDirectoryEntry> getDataDirectoryEntries(String instanceId) {
        List<RemoteDirectoryEntry> result = new ArrayList<>();
        InstanceNodeManifest newest = findInstanceNodeManifest(instanceId);
        if (newest == null) {
            throw new WebApplicationException("Cannot find instance " + instanceId, Status.NOT_FOUND);
        }
        BHive hive = root.getHive();
        String activeTag = getState(newest, hive).read().activeTag;
        if (activeTag == null) {
            throw new WebApplicationException("Cannot find active version for instance " + instanceId, Status.NOT_FOUND);
        }

        Key activeKey = new Manifest.Key(newest.getKey().getName(), activeTag);

        InstanceNodeController inc = new InstanceNodeController(hive, root.getDeploymentDir(),
                InstanceNodeManifest.of(hive, activeKey));

        Path dataRoot = inc.getDeploymentPathProvider().get(SpecialDirectory.DATA);

        try (Stream<Path> paths = Files.walk(dataRoot)) {
            paths.filter(Files::isRegularFile).forEach(f -> {
                RemoteDirectoryEntry entry = new RemoteDirectoryEntry();
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
    public EntryChunk getEntryContent(RemoteDirectoryEntry entry, long offset, long limit) {
        // determine file first...
        Path actual = getEntryPath(entry);

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

    private Path getEntryPath(RemoteDirectoryEntry entry) {
        Path rootDir;
        if (entry.root != null) {
            DeploymentPathProvider dpp = new DeploymentPathProvider(root.getDeploymentDir().resolve(entry.uuid), entry.tag);
            rootDir = dpp.get(entry.root).toAbsolutePath();
        } else {
            rootDir = root.getRootDir();
        }
        Path actual = rootDir.resolve(entry.path);

        if (!actual.startsWith(rootDir)) {
            throw new WebApplicationException("Trying to escape " + rootDir, Status.BAD_REQUEST);
        }

        if (!Files.exists(actual)) {
            throw new WebApplicationException("Cannot find " + actual, Status.NOT_FOUND);
        }
        return actual;
    }

    @Override
    public Response getEntryStream(RemoteDirectoryEntry entry) {
        Path actual = getEntryPath(entry);
        String mediaType = MediaType.APPLICATION_OCTET_STREAM;
        try {
            ContentInfo ci = ContentInfoUtil.findExtensionMatch(actual.getFileName().toString());
            ci = PathHelper.getContentInfo(actual, ci);

            // dynamic return mime type
            if (ci != null && ci.getMimeType() != null) {
                mediaType = ci.getMimeType();
            }
        } catch (IOException e) {
            log.warn("Cannot determine mime type of {}", actual, e);
        }

        // Build a response with the stream
        ResponseBuilder responeBuilder = Response.ok(new StreamingOutput() {

            @Override
            public void write(OutputStream output) throws IOException {
                try (InputStream is = Files.newInputStream(actual)) {
                    is.transferTo(output);
                } catch (IOException ioe) {
                    if (log.isDebugEnabled()) {
                        log.debug("Could not fully write output", ioe);
                    } else {
                        log.warn("Could not fully write output: {}", ioe.toString());
                    }
                }
            }
        }, mediaType);

        // Load and attach metadata to give the file a nice name
        try {
            long size = Files.size(actual);
            ContentDisposition contentDisposition = ContentDisposition.type("attachement").size(size)
                    .fileName(actual.getFileName().toString()).build();
            responeBuilder.header("Content-Disposition", contentDisposition);
            responeBuilder.header("Content-Length", size);
            return responeBuilder.build();
        } catch (IOException e) {
            throw new WebApplicationException("Cannot provide download for entry", e);
        }
    }

    @Override
    public void deleteDataEntry(RemoteDirectoryEntry entry) {
        Path actual = getEntryPath(entry);
        try {
            Files.delete(actual);
        } catch (IOException e) {
            throw new WebApplicationException("Could not delete data file " + entry.path, e);
        }
    }

    @Override
    public Map<Integer, Boolean> getPortStates(List<Integer> ports) {
        Map<Integer, Boolean> result = new TreeMap<>();

        for (Integer port : ports) {
            try (ServerSocket ss = new ServerSocket(port)) {
                ss.setReuseAddress(true);
                result.put(port, false); // free
            } catch (IOException e) {
                result.put(port, true); // used
            }
        }

        return result;
    }

}
