package io.bdeploy.minion.remote.jersey;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.SecurityContext;

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
import io.bdeploy.common.util.SortOneAsLastComparator;
import io.bdeploy.interfaces.ScopedManifestKey;
import io.bdeploy.interfaces.minion.MinionConfiguration;
import io.bdeploy.interfaces.minion.MinionDto;
import io.bdeploy.interfaces.minion.MinionStatusDto;
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

    @Context
    private ResourceContext rc;

    @Context
    private SecurityContext context;

    @Override
    public SortedMap<String, MinionStatusDto> getMinions() {
        SortedMap<String, MinionStatusDto> result = new TreeMap<>();

        MinionConfiguration minionConfig = root.getMinions();
        try (Activity contacting = reporter.start("Contacting Minions...", minionConfig.size())) {
            for (Map.Entry<String, MinionDto> entry : minionConfig.entrySet()) {
                String name = entry.getKey();
                MinionDto config = entry.getValue();
                try {
                    RemoteService service = config.remote;
                    MinionStatusResource client = ResourceProvider.getResource(service, MinionStatusResource.class, context);
                    result.put(name, client.getStatus());
                } catch (Exception e) {
                    String message = e.getMessage();
                    log.warn("Problem while contacting minion: {}", name);
                    if (log.isTraceEnabled()) {
                        log.trace("Exception", e);
                    }
                    result.put(name, MinionStatusDto.createOffline(config, "Node not online: " + message));
                }
                contacting.worked(1);
            }
        }
        return result;
    }

    @Override
    public void update(Manifest.Key version, boolean clean) {
        BHive bhive = registry.get(JerseyRemoteBHive.DEFAULT_NAME);

        SortedSet<Key> keys = bhive.execute(new ManifestListOperation().setManifestName(version.toString()));
        if (!keys.contains(version)) {
            throw new WebApplicationException("Key not found: + " + version, Status.NOT_FOUND);
        }

        // find target OS for update package
        OperatingSystem updateOs = getTargetOsFromUpdate(version);

        // Push the update to the removes. Ensure that master is the last one
        MinionConfiguration minionConfig = root.getMinions();
        String masterName = root.getState().self;
        SortedMap<String, MinionUpdateResource> toUpdate = new TreeMap<>(new SortOneAsLastComparator(masterName));
        pushUpdate(version, bhive, updateOs, minionConfig, toUpdate);

        // DON'T check for cancel from here on anymore to avoid inconsistent setups
        // (inconsistent setups can STILL occur in mixed-OS setups)
        prepareUpdate(version, clean, toUpdate);

        // now perform the update on all
        List<Throwable> problems = performUpdate(version, toUpdate);
        if (!problems.isEmpty()) {
            WebApplicationException ex = new WebApplicationException("Problem(s) updating minion(s)",
                    Status.INTERNAL_SERVER_ERROR);
            problems.forEach(ex::addSuppressed);
            throw ex;
        }
    }

    private List<Throwable> performUpdate(Manifest.Key version, SortedMap<String, MinionUpdateResource> toUpdate) {
        List<Throwable> problems = new ArrayList<>();
        toUpdate.entrySet().forEach(entry -> {
            try {
                MinionUpdateResource updateResource = entry.getValue();
                updateResource.update(version); // update schedules and delays, so we have a chance to return this call.
            } catch (Exception e) {
                // don't immediately throw to update as many minions as possible.
                // this Exception should actually never happen according to the contract.
                log.error("Cannot schedule update on minion: {}", entry.getKey(), e);
                problems.add(e);
            }
        });
        return problems;
    }

    private void prepareUpdate(Manifest.Key version, boolean clean, SortedMap<String, MinionUpdateResource> toUpdate) {
        Activity preparing = reporter.start("Preparing update on Minions...", toUpdate.size());
        // prepare the update on all minions
        for (Map.Entry<String, MinionUpdateResource> ur : toUpdate.entrySet()) {
            try {
                ur.getValue().prepare(version, clean);
            } catch (Exception e) {
                // don't immediately throw to update as many minions as possible.
                // this Exception should actually never happen according to the contract.
                throw new WebApplicationException("Cannot preapre update on " + ur.getKey(), e);
            }
            preparing.worked(1);
        }
        preparing.done();
    }

    private void pushUpdate(Manifest.Key version, BHive h, OperatingSystem updateOs, MinionConfiguration minionConfig,
            SortedMap<String, MinionUpdateResource> toUpdate) {
        Activity pushing = reporter.start("Pushing update to Minions...", minionConfig.size());
        for (Entry<String, MinionDto> entry : minionConfig.entrySet()) {
            MinionDto minionDto = entry.getValue();
            RemoteService service = minionDto.remote;
            try {
                if (minionDto.os == updateOs) {
                    MinionUpdateResource resource = ResourceProvider.getResource(service, MinionUpdateResource.class, context);
                    toUpdate.put(entry.getKey(), resource);
                } else {
                    log.warn("Not updating {}, wrong os ({} != {})", entry.getKey(), minionDto.os, updateOs);
                    pushing.workAndCancelIfRequested(1);
                    continue;
                }
            } catch (Exception e) {
                log.warn("Cannot contact minion: {} - not updating.", entry.getKey());
                pushing.workAndCancelIfRequested(1);
                continue;
            }

            try {
                h.execute(new PushOperation().addManifest(version).setRemote(service));
            } catch (Exception e) {
                log.error("Cannot push update to minion: {}", entry.getKey(), e);
                throw new WebApplicationException("Cannot push update to minions", e, Status.BAD_GATEWAY);
            }
            pushing.workAndCancelIfRequested(1);
        }
        pushing.done();
    }

    private OperatingSystem getTargetOsFromUpdate(Key version) {
        ScopedManifestKey scoped = ScopedManifestKey.parse(version);
        if (scoped == null) {
            throw new IllegalStateException("Cannot determin OS from key " + version);
        }

        return scoped.getOperatingSystem();
    }

    @Override
    public MasterNamedResource getNamedMaster(String name) {
        BHive h = registry.get(name);
        if (h == null) {
            throw new WebApplicationException("Hive not found: " + name, Status.NOT_FOUND);
        }

        return rc.initResource(new MasterNamedResourceImpl(root, h, reporter));
    }

}
