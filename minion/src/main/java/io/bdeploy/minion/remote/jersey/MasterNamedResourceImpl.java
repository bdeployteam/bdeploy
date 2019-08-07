package io.bdeploy.minion.remote.jersey;

import static io.bdeploy.common.util.RuntimeAssert.assertNotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.op.remote.PushOperation;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.ActivityReporter.Activity;
import io.bdeploy.common.security.ApiAccessToken;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.security.SecurityHelper;
import io.bdeploy.interfaces.ScopedManifestKey;
import io.bdeploy.interfaces.configuration.dcu.ApplicationConfiguration;
import io.bdeploy.interfaces.configuration.instance.ClientApplicationConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration;
import io.bdeploy.interfaces.configuration.pcu.InstanceNodeStatusDto;
import io.bdeploy.interfaces.configuration.pcu.InstanceStatusDto;
import io.bdeploy.interfaces.directory.EntryChunk;
import io.bdeploy.interfaces.directory.InstanceDirectory;
import io.bdeploy.interfaces.directory.InstanceDirectoryEntry;
import io.bdeploy.interfaces.manifest.ApplicationManifest;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.manifest.InstanceNodeManifest;
import io.bdeploy.interfaces.manifest.dependencies.LocalDependencyFetcher;
import io.bdeploy.interfaces.remote.MasterNamedResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.interfaces.remote.SlaveDeploymentResource;
import io.bdeploy.interfaces.remote.SlaveProcessResource;
import io.bdeploy.jersey.JerseyPathWriter.DeleteAfterWrite;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.minion.MinionState;

public class MasterNamedResourceImpl implements MasterNamedResource {

    private static final Logger log = LoggerFactory.getLogger(MasterNamedResourceImpl.class);

    private final BHive hive;
    private final ActivityReporter reporter;
    private final MinionRoot root;

    public MasterNamedResourceImpl(MinionRoot root, BHive hive, ActivityReporter reporter) {
        this.root = root;
        this.hive = hive;
        this.reporter = reporter;
    }

    @Override
    public void install(Key key) {
        InstanceManifest imf = InstanceManifest.of(hive, key);
        SortedMap<String, Key> fragmentReferences = imf.getInstanceNodeManifests();

        try (Activity deploying = reporter.start("Deploying to minions...", fragmentReferences.size())) {
            for (Map.Entry<String, Manifest.Key> entry : fragmentReferences.entrySet()) {
                String minionName = entry.getKey();
                if (InstanceManifest.CLIENT_NODE_NAME.equals(minionName)) {
                    continue;
                }
                Manifest.Key toDeploy = entry.getValue();
                RemoteService minion = root.getState().minions.get(minionName);

                assertNotNull(minion, "Cannot lookup minion on master: " + minionName);
                assertNotNull(toDeploy, "Cannot lookup minion manifest on master: " + toDeploy);

                // make sure the minion has the manifest.
                hive.execute(new PushOperation().setRemote(minion).addManifest(toDeploy));

                SlaveDeploymentResource deployment = ResourceProvider.getResource(minion, SlaveDeploymentResource.class);
                try {
                    deployment.install(toDeploy);
                } catch (Exception e) {
                    throw new WebApplicationException("Cannot deploy to " + minionName, e, Status.INTERNAL_SERVER_ERROR);
                }

                deploying.worked(1);
            }
        }
    }

    @Override
    public void activate(Key key) {
        InstanceManifest imf = InstanceManifest.of(hive, key);

        if (!isFullyDeployed(imf, false)) {
            throw new WebApplicationException(
                    "Given manifest for UUID " + imf.getConfiguration().uuid + " is not fully deployed: " + key,
                    Status.NOT_FOUND);
        }

        SortedMap<String, Key> fragments = imf.getInstanceNodeManifests();
        try (Activity activating = reporter.start("Activating on minions...", fragments.size())) {
            for (Map.Entry<String, Manifest.Key> entry : fragments.entrySet()) {
                String minionName = entry.getKey();
                if (InstanceManifest.CLIENT_NODE_NAME.equals(minionName)) {
                    continue;
                }
                Manifest.Key toDeploy = entry.getValue();
                RemoteService minion = root.getState().minions.get(minionName);

                assertNotNull(minion, "Cannot lookup minion on master: " + minionName);
                assertNotNull(toDeploy, "Cannot lookup minion manifest on master: " + toDeploy);

                SlaveDeploymentResource deployment = ResourceProvider.getResource(minion, SlaveDeploymentResource.class);
                try {
                    deployment.activate(toDeploy);
                } catch (Exception e) {
                    throw new WebApplicationException("Cannot activate on " + minionName, e, Status.INTERNAL_SERVER_ERROR);
                }

                activating.worked(1);
            }
        }

        // TODO: don't record this in the master state. Record in BHive instead. IMPORTANT as hive must be self-contained.
        // record the master manifest as deployed.
        root.modifyState(s -> s.activeMasterVersions.put(imf.getConfiguration().uuid, key));
    }

    /**
     * @param imf the {@link InstanceManifest} to check.
     * @param ignoreOffline whether to regard an instance as deployed even if a participating node is offline.
     * @return whether the given {@link InstanceManifest} is fully deployed to all required minions.
     */
    private boolean isFullyDeployed(InstanceManifest imf, boolean ignoreOffline) {
        SortedMap<String, Key> imfs = imf.getInstanceNodeManifests();
        // No configuration -> cannot be deployed
        if (imfs.isEmpty()) {
            return false;
        }
        // check all minions for their respective availability.
        String instanceId = imf.getConfiguration().uuid;
        for (Map.Entry<String, Manifest.Key> entry : imfs.entrySet()) {
            String minionName = entry.getKey();
            if (InstanceManifest.CLIENT_NODE_NAME.equals(minionName)) {
                continue;
            }
            Manifest.Key toDeploy = entry.getValue();
            RemoteService minion = root.getState().minions.get(minionName);

            assertNotNull(minion, "Cannot lookup minion on master: " + minionName);
            assertNotNull(toDeploy, "Cannot lookup minion manifest on master: " + toDeploy);

            SortedSet<Key> deployments;
            try {
                SlaveDeploymentResource slave = ResourceProvider.getResource(minion, SlaveDeploymentResource.class);
                deployments = slave.getAvailableDeploymentsOfInstance(instanceId);
            } catch (Exception e) {
                if (ignoreOffline) {
                    log.info("Ignoring offline node while checking deployment state: " + minionName);
                    continue;
                }
                throw new IllegalStateException("Node offline while checking state: " + minionName);
            }

            if (deployments.isEmpty()) {
                if (log.isDebugEnabled()) {
                    log.debug("Minion {} does not contain any deployment for {}", minionName, instanceId);
                }
                return false;
            }
            if (!deployments.contains(toDeploy)) {
                if (log.isDebugEnabled()) {
                    log.debug("Minion {} does not have {} available", minionName, toDeploy);
                }
                return false;
            }
        }
        return true;
    }

    @Override
    public void remove(Key key) {
        InstanceManifest imf = InstanceManifest.of(hive, key);

        if (!isFullyDeployed(imf, false)) {
            return; // no need to.
        }

        root.modifyState(s -> {
            if (key.equals(s.activeMasterVersions.get(imf.getConfiguration().uuid))) {
                log.warn("Removing active version for {}", imf.getConfiguration().uuid);
                s.activeMasterVersions.remove(imf.getConfiguration().uuid);
            }
        });

        SortedMap<String, Key> fragments = imf.getInstanceNodeManifests();
        Activity removing = reporter.start("Removing on minions...", fragments.size());

        try {
            for (Map.Entry<String, Manifest.Key> entry : fragments.entrySet()) {
                String minionName = entry.getKey();
                if (InstanceManifest.CLIENT_NODE_NAME.equals(minionName)) {
                    continue;
                }
                Manifest.Key toRemove = entry.getValue();
                RemoteService minion = root.getState().minions.get(minionName);

                assertNotNull(minion, "Cannot lookup minion on master: " + minionName);
                assertNotNull(toRemove, "Cannot lookup minion manifest on master: " + toRemove);

                SlaveDeploymentResource deployment = ResourceProvider.getResource(minion, SlaveDeploymentResource.class);
                try {
                    deployment.remove(toRemove);
                } catch (Exception e) {
                    throw new WebApplicationException("Cannot remove on " + minionName, e);
                }

                removing.worked(1);
            }
        } finally {
            removing.done();
        }

        // no need to clean up the hive, this is done elsewhere.
    }

    @Override
    public SortedSet<Key> getAvailableDeploymentsOfInstance(String instance) {
        SortedSet<Key> scan = InstanceManifest.scan(hive, false);
        SortedSet<Key> result = new TreeSet<>();

        for (Manifest.Key k : scan) {
            InstanceManifest imf = InstanceManifest.of(hive, k);
            String instanceId = imf.getConfiguration().uuid;
            if (!instanceId.equals(instance)) {
                continue;
            }
            try {
                // ignore offline nodes. we still want to show the user the instance as installed even if "only"
                // online nodes have it fully deployed. otherwise it is possible to have processes running from
                // an instance which is shown as "not installed" in the UI.
                if (!isFullyDeployed(imf, true)) {
                    continue;
                }
                result.add(imf.getManifest());
            } catch (Exception e) {
                log.warn("{}: Cannot check deployment state of: {}", instance, imf.getManifest());
                continue;
            }

        }
        return result;
    }

    @Override
    public SortedMap<String, Key> getActiveDeployments() {
        return root.getState().activeMasterVersions;
    }

    @Override
    public SortedSet<Key> getAvailableDeploymentsOfMinion(String minion, String instance) {
        RemoteService m = root.getState().minions.get(minion);
        assertNotNull(m, "Cannot find minion " + minion);

        SlaveDeploymentResource client = ResourceProvider.getResource(m, SlaveDeploymentResource.class);
        try {
            return client.getAvailableDeploymentsOfInstance(instance);
        } catch (Exception e) {
            throw new WebApplicationException("Cannot read deployments from minion " + minion, e, Status.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public SortedMap<String, Key> getActiveDeploymentsOfMinion(String minion) {
        RemoteService m = root.getState().minions.get(minion);
        assertNotNull(m, "Cannot find minion " + minion);

        SlaveDeploymentResource client = ResourceProvider.getResource(m, SlaveDeploymentResource.class);
        try {
            return client.getActiveDeployments();
        } catch (Exception e) {
            throw new WebApplicationException("Cannot read deployments from minion " + minion, e, Status.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public List<InstanceDirectory> getDataDirectorySnapshots(String instanceId) {
        List<InstanceDirectory> result = new ArrayList<>();

        Key key = getActiveDeployments().get(instanceId);
        if (key == null) {
            throw new WebApplicationException("Cannot find active version for instance " + instanceId, Status.NOT_FOUND);
        }

        SortedMap<String, RemoteService> minions = root.getState().minions;
        InstanceStatusDto status = getStatus(instanceId);
        for (String nodeName : status.getNodesWithApps()) {
            InstanceDirectory idd = new InstanceDirectory();
            idd.minion = nodeName;
            idd.uuid = instanceId;

            try {
                RemoteService service = minions.get(nodeName);

                SlaveDeploymentResource sdr = ResourceProvider.getResource(service, SlaveDeploymentResource.class);
                List<InstanceDirectoryEntry> iddes = sdr.getDataDirectoryEntries(instanceId);
                idd.entries.addAll(iddes);
            } catch (Exception e) {
                log.warn("Problem fetching data directory of {}", nodeName, e);
                idd.problem = e.toString();
            }

            result.add(idd);
        }

        return result;
    }

    @Override
    public EntryChunk getEntryContent(String minion, InstanceDirectoryEntry entry, long offset, long limit) {
        RemoteService svc = root.getMinions().get(minion);
        if (svc == null) {
            throw new WebApplicationException("Cannot find minion " + minion, Status.NOT_FOUND);
        }
        SlaveDeploymentResource sdr = ResourceProvider.getResource(svc, SlaveDeploymentResource.class);
        return sdr.getEntryContent(entry, offset, limit);
    }

    @Override
    public ClientApplicationConfiguration getClientConfiguration(String uuid, String application) {
        Manifest.Key active = getActiveDeployments().get(uuid);

        if (active == null) {
            throw new WebApplicationException("No active deployment for " + uuid, Status.NOT_FOUND);
        }

        InstanceManifest imf = InstanceManifest.of(hive, active);
        ClientApplicationConfiguration cfg = new ClientApplicationConfiguration();
        cfg.clientConfig = imf.getApplicationConfiguration(hive, application);
        if (cfg.clientConfig == null) {
            throw new WebApplicationException("Cannot find application " + application + " in instance " + uuid,
                    Status.NOT_FOUND);
        }

        ApplicationManifest amf = ApplicationManifest.of(hive, cfg.clientConfig.application);
        cfg.clientDesc = amf.getDescriptor();
        cfg.instanceKey = active;
        cfg.configTreeId = imf.getConfiguration().configTree;

        // application key MUST be a ScopedManifestKey. dependencies /must/ be present
        ScopedManifestKey smk = ScopedManifestKey.parse(cfg.clientConfig.application);
        cfg.resolvedRequires.addAll(
                new LocalDependencyFetcher().fetch(hive, amf.getDescriptor().runtimeDependencies, smk.getOperatingSystem()));

        // load splash screen from hive and send along.
        cfg.clientSplashData = amf.readBrandingSplashScreen(hive);

        return cfg;
    }

    @Override
    @DeleteAfterWrite
    public Path getClientInstanceConfiguration(Manifest.Key instanceId) {
        return null; // FIXME: DCS-396: client config shall not contain server config files.
    }

    @Override
    public void start(String instanceId) {
        SortedMap<String, RemoteService> minions = root.getState().minions;
        InstanceStatusDto status = getStatus(instanceId);
        for (String nodeName : status.getNodesWithApps()) {
            RemoteService service = minions.get(nodeName);
            SlaveProcessResource spc = ResourceProvider.getResource(service, SlaveProcessResource.class);
            spc.start(instanceId);
        }
    }

    @Override
    public void start(String instanceId, String applicationId) {
        // Check if this version is already running on a node
        InstanceStatusDto status = getStatus(instanceId);
        if (status.isAppRunning(applicationId)) {
            String node = status.getNodeWhereAppIsRunningOrScheduled(applicationId);
            throw new WebApplicationException("Application is already running on node '" + node + "'.",
                    Status.INTERNAL_SERVER_ERROR);
        }

        // Find minion where the application is deployed
        String minion = status.getNodeWhereAppIsDeployedInActiveVersion(applicationId);
        if (minion == null) {
            throw new WebApplicationException("Application is not deployed on any node.", Status.INTERNAL_SERVER_ERROR);
        }

        // Now launch this application on the minion
        try (Activity activity = reporter.start("Starting application...", -1)) {
            SortedMap<String, RemoteService> minions = root.getState().minions;
            RemoteService service = minions.get(minion);
            SlaveProcessResource spc = ResourceProvider.getResource(service, SlaveProcessResource.class);
            spc.start(instanceId, applicationId);
        }
    }

    @Override
    public void stop(String instanceId) {
        InstanceStatusDto status = getStatus(instanceId);
        SortedMap<String, RemoteService> minions = root.getState().minions;

        // Find out all nodes where at least one application is running
        Collection<String> nodes = status.getNodesWhereAppsAreRunningOrScheduled();

        try (Activity activity = reporter.start("Stopping applications...", nodes.size())) {
            for (String node : nodes) {
                RemoteService service = minions.get(node);
                SlaveProcessResource spc = ResourceProvider.getResource(service, SlaveProcessResource.class);
                spc.stop(instanceId);
                activity.worked(1);
            }
        }
    }

    @Override
    public void stop(String instanceId, String applicationId) {
        // Find out where the application is running on
        InstanceStatusDto status = getStatus(instanceId);
        if (!status.isAppRunningOrScheduled(applicationId)) {
            throw new WebApplicationException("Application is not running on any node.", Status.INTERNAL_SERVER_ERROR);
        }
        String nodeName = status.getNodeWhereAppIsRunningOrScheduled(applicationId);

        // Now stop this application on the minion
        try (Activity activity = reporter.start("Stopping application...", -1)) {
            SortedMap<String, RemoteService> minions = root.getState().minions;
            RemoteService service = minions.get(nodeName);
            SlaveProcessResource spc = ResourceProvider.getResource(service, SlaveProcessResource.class);
            spc.stop(instanceId, applicationId);
        }
    }

    @Override
    public InstanceDirectory getOutputEntry(String instanceId, String tag, String applicationId) {
        // master has the instance manifest.
        Manifest.Key instanceKey = new Manifest.Key(InstanceManifest.getRootName(instanceId), tag);
        InstanceManifest imf = InstanceManifest.of(hive, instanceKey);

        for (Map.Entry<String, Manifest.Key> entry : imf.getInstanceNodeManifests().entrySet()) {
            InstanceNodeManifest inmf = InstanceNodeManifest.of(hive, entry.getValue());
            for (ApplicationConfiguration app : inmf.getConfiguration().applications) {
                if (app.uid.equals(applicationId)) {
                    // this is our app :)
                    InstanceDirectory id = new InstanceDirectory();
                    id.minion = entry.getKey();
                    id.uuid = instanceId;

                    try {
                        RemoteService svc = root.getMinions().get(entry.getKey());
                        SlaveProcessResource spr = ResourceProvider.getResource(svc, SlaveProcessResource.class);
                        InstanceDirectoryEntry oe = spr.getOutputEntry(instanceId, tag, applicationId);

                        if (oe != null) {
                            id.entries.add(oe);
                        }
                    } catch (Exception e) {
                        log.warn("Problem fetching output entry from {} for {}, {}, {}", entry.getKey(), instanceId, tag,
                                applicationId, e);
                        id.problem = e.toString();
                    }

                    return id;
                }
            }
        }

        throw new WebApplicationException("Cannot find application " + applicationId + " in " + instanceId + ":" + tag,
                Status.NOT_FOUND);
    }

    @Override
    public InstanceStatusDto getStatus(String instanceId) {
        InstanceStatusDto instanceStatus = new InstanceStatusDto(instanceId);

        SortedMap<String, RemoteService> minions = root.getState().minions;
        try (Activity activity = reporter.start("Get node status...", minions.size())) {
            for (Entry<String, RemoteService> entry : minions.entrySet()) {
                String minion = entry.getKey();
                SlaveProcessResource spc = ResourceProvider.getResource(entry.getValue(), SlaveProcessResource.class);
                try {
                    InstanceNodeStatusDto nodeStatus = spc.getStatus(instanceId);
                    instanceStatus.add(minion, nodeStatus);
                } catch (Exception e) {
                    log.error("Cannot fetch process status of {}", minion);
                    if (log.isDebugEnabled()) {
                        log.debug("Exception:", e);
                    }
                }
                activity.worked(1);
            }
        }
        return instanceStatus;
    }

    @Override
    public String generateWeakToken(String principal) {
        ApiAccessToken token = new ApiAccessToken.Builder().setIssuedTo(principal).setWeak(true).build();
        SecurityHelper sh = SecurityHelper.getInstance();

        MinionState state = root.getState();
        KeyStore ks;
        try {
            ks = sh.loadPrivateKeyStore(state.keystorePath, state.keystorePass);
        } catch (GeneralSecurityException | IOException e) {
            throw new WebApplicationException("Cannot generate weak token", e);
        }

        try {
            return SecurityHelper.getInstance().createSignaturePack(token, ks, state.keystorePass);
        } catch (Exception e) {
            throw new WebApplicationException("Cannot create weak token", e);
        }
    }

    @Override
    public Collection<InstanceConfiguration> listInstanceConfigurations() {
        SortedSet<Key> scan = InstanceManifest.scan(hive, true);
        return scan.stream().map(k -> InstanceManifest.of(hive, k).getConfiguration()).collect(Collectors.toList());
    }

}
