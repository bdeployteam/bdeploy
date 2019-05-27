package io.bdeploy.minion.remote.jersey;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.op.ManifestListOperation;
import io.bdeploy.bhive.op.remote.PushOperation;
import io.bdeploy.bhive.remote.jersey.BHiveRegistry;
import io.bdeploy.bhive.remote.jersey.JerseyRemoteBHive;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.ActivityReporter.Activity;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.OsHelper.OperatingSystem;
import io.bdeploy.interfaces.NodeStatus;
import io.bdeploy.interfaces.ScopedManifestKey;
import io.bdeploy.interfaces.configuration.instance.InstanceGroupConfiguration;
import io.bdeploy.interfaces.configuration.instance.SoftwareRepositoryConfiguration;
import io.bdeploy.interfaces.manifest.InstanceGroupManifest;
import io.bdeploy.interfaces.manifest.SoftwareRepositoryManifest;
import io.bdeploy.interfaces.remote.MasterNamedResource;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.interfaces.remote.MinionStatusResource;
import io.bdeploy.interfaces.remote.MinionUpdateResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.minion.MinionRoot;

public class MasterRootResourceImpl implements MasterRootResource {

    private static final Logger log = LoggerFactory.getLogger(MasterRootResourceImpl.class);

    @Inject
    private MinionRoot root;

    @Inject
    private BHiveRegistry registry;

    @Inject
    private ActivityReporter reporter;

    @Override
    public SortedMap<String, NodeStatus> getMinions() {
        SortedMap<String, RemoteService> minions = root.getState().minions;
        SortedMap<String, NodeStatus> result = new TreeMap<>();

        try (Activity contacting = reporter.start("Contacting Minions...", minions.size())) {
            for (Map.Entry<String, RemoteService> entry : minions.entrySet()) {
                try {
                    MinionStatusResource client = ResourceProvider.getResource(entry.getValue(), MinionStatusResource.class);
                    result.put(entry.getKey(), client.getStatus());
                } catch (Exception e) {
                    log.warn("Problem while contacting minion: " + entry.getKey());
                    if (log.isTraceEnabled()) {
                        log.trace("Exception", e);
                    }
                    result.put(entry.getKey(), null);
                }

                contacting.worked(1);
            }
        }
        return result;
    }

    @Override
    public void update(Manifest.Key version) {
        BHive h = registry.get(JerseyRemoteBHive.DEFAULT_NAME);

        SortedSet<Key> keys = h.execute(new ManifestListOperation().setManifestName(version.toString()));
        if (!keys.contains(version)) {
            throw new WebApplicationException("Key not found: + " + version, Status.NOT_FOUND);
        }

        // find target OS for update package
        OperatingSystem updateOs = getTargetOsFromUpdate(version);

        // check each minion's state and OS and push the update.
        SortedMap<String, RemoteService> minions = root.getState().minions;
        SortedMap<String, MinionUpdateResource> toUpdate = new TreeMap<>();
        String masterName = null;
        Activity pushing = reporter.start("Pushing update to Minions...", minions.size());
        for (Entry<String, RemoteService> entry : minions.entrySet()) {
            RemoteService service = entry.getValue();
            try {
                MinionStatusResource sr = ResourceProvider.getResource(service, MinionStatusResource.class);
                NodeStatus status = sr.getStatus();

                if (status.master) {
                    if (masterName != null) {
                        throw new WebApplicationException("Multiple masters found in setup!");
                    }
                    masterName = entry.getKey();
                }

                if (status.os == updateOs) {
                    MinionUpdateResource resource = ResourceProvider.getResource(service, MinionUpdateResource.class);
                    toUpdate.put(entry.getKey(), resource);
                } else {
                    log.warn("Not updating " + entry.getKey() + ", wrong os (" + status.os + " != " + updateOs + ")");
                    pushing.workAndCancelIfRequested(1);
                    continue;
                }
            } catch (Exception e) {
                log.warn("Cannot contact minion: " + entry.getKey() + " - not updating.");
                pushing.workAndCancelIfRequested(1);
                continue;
            }

            try {
                h.execute(new PushOperation().addManifest(version).setRemote(service));
            } catch (Exception e) {
                log.error("Cannot push update to minion: " + entry.getKey(), e);
                throw new WebApplicationException("Cannot push update to minions", e, Status.BAD_GATEWAY);
            }
            pushing.workAndCancelIfRequested(1);
        }
        pushing.done();

        if (masterName == null) {
            log.warn("Cannot identify node running master from node registrations");
            masterName = ""; // to make comparator for sorting nodes happy.
        }

        // DON'T check for cancel from here on anymore to avoid inconsistent setups
        // (inconsistent setups can STILL occur in mixed-OS setups)

        Activity preparing = reporter.start("Preparing update on Minions...", minions.size());
        // prepare the update on all minions
        for (Map.Entry<String, MinionUpdateResource> ur : toUpdate.entrySet()) {
            try {
                ur.getValue().prepare(version);
            } catch (Exception e) {
                // don't immediately throw to update as many minions as possible.
                // this Exception should actually never happen according to the contract.
                throw new WebApplicationException("Cannot preapre update on " + ur.getKey(), e);
            }
            preparing.worked(1);
        }
        preparing.done();

        // now schedule update on all but the master minions.
        List<Throwable> problems = new ArrayList<>();
        String finalMaster = masterName;
        toUpdate.entrySet().stream().sorted((a, b) -> {
            if (finalMaster.equals(a.getKey())) {
                return 1;
            } else if (finalMaster.equals(b.getKey())) {
                return -1;
            }
            return a.getKey().compareTo(b.getKey());
        }).forEach((entry) -> {
            try {
                entry.getValue().update(version); // update schedules and delays, so we have a chance to return this call.
            } catch (Exception e) {
                // don't immediately throw to update as many minions as possible.
                // this Exception should actually never happen according to the contract.
                log.error("Cannot schedule update on minion: " + entry.getKey(), e);
                problems.add(e);
            }
        });

        if (!problems.isEmpty()) {
            WebApplicationException ex = new WebApplicationException("Problem(s) updating minion(s)",
                    Status.INTERNAL_SERVER_ERROR);
            problems.forEach(ex::addSuppressed);
            throw ex;
        }
    }

    private OperatingSystem getTargetOsFromUpdate(Key version) {
        ScopedManifestKey scoped = ScopedManifestKey.parse(version);
        if (scoped == null) {
            throw new IllegalStateException("Cannot determin OS from key " + version);
        }

        return scoped.getOperatingSystem();
    }

    @Override
    public List<SoftwareRepositoryConfiguration> getSoftwareRepositories() {
        List<SoftwareRepositoryConfiguration> result = new ArrayList<>();
        for (Map.Entry<String, BHive> entry : registry.getAll().entrySet()) {
            SoftwareRepositoryConfiguration cfg = new SoftwareRepositoryManifest(entry.getValue()).read();
            if (cfg != null) {
                result.add(cfg);
            }
        }
        return result;
    }

    @Override
    public void addSoftwareRepository(SoftwareRepositoryConfiguration config, String storage) {
        if (storage == null) {
            storage = getStorageLocations().iterator().next();
        }

        if (!getStorageLocations().contains(storage)) {
            log.warn("Tried to use storage location: " + storage + ", valid are: " + getStorageLocations());
            throw new WebApplicationException("Invalid Storage Location", Status.NOT_FOUND);
        }

        Path hive = Paths.get(storage, config.name);
        if (Files.isDirectory(hive)) {
            throw new WebApplicationException("Hive path already exists", Status.NOT_ACCEPTABLE);
        }

        BHive h = new BHive(hive.toUri(), reporter);
        new SoftwareRepositoryManifest(h).update(config);
        registry.register(config.name, h);
    }

    @Override
    public Set<String> getStorageLocations() {
        return registry.getLocations().stream().map(Path::toString).collect(Collectors.toSet());
    }

    @Override
    public void addInstanceGroup(InstanceGroupConfiguration meta, String storage) {
        if (storage == null) {
            storage = getStorageLocations().iterator().next();
        }

        if (!getStorageLocations().contains(storage)) {
            log.warn("Tried to use storage location: " + storage + ", valid are: " + getStorageLocations());
            throw new WebApplicationException("Invalid Storage Location", Status.NOT_FOUND);
        }

        Path hive = Paths.get(storage, meta.name);
        if (Files.isDirectory(hive)) {
            throw new WebApplicationException("Hive path already exists", Status.NOT_ACCEPTABLE);
        }

        BHive h = new BHive(hive.toUri(), reporter);
        new InstanceGroupManifest(h).update(meta);
        registry.register(meta.name, h);
    }

    @Override
    public MasterNamedResource getNamedMaster(String name) {
        BHive h = registry.get(name);
        if (h == null) {
            throw new WebApplicationException("Hive not found: " + name, Status.NOT_FOUND);
        }

        return new MasterNamedResourceImpl(root, h, reporter);
    }

}
