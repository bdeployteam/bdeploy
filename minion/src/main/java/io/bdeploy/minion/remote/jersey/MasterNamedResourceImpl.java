package io.bdeploy.minion.remote.jersey;

import static io.bdeploy.common.util.RuntimeAssert.assertNotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.op.ManifestExistsOperation;
import io.bdeploy.bhive.op.ManifestNextIdOperation;
import io.bdeploy.bhive.op.remote.PushOperation;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.ActivityReporter.Activity;
import io.bdeploy.common.security.ApiAccessToken;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.security.SecurityHelper;
import io.bdeploy.interfaces.ScopedManifestKey;
import io.bdeploy.interfaces.configuration.dcu.ApplicationConfiguration;
import io.bdeploy.interfaces.configuration.dcu.CommandConfiguration;
import io.bdeploy.interfaces.configuration.dcu.ParameterConfiguration;
import io.bdeploy.interfaces.configuration.instance.ClientApplicationConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceNodeConfiguration;
import io.bdeploy.interfaces.configuration.pcu.InstanceNodeStatusDto;
import io.bdeploy.interfaces.configuration.pcu.InstanceStatusDto;
import io.bdeploy.interfaces.descriptor.application.ExecutableDescriptor;
import io.bdeploy.interfaces.descriptor.application.ParameterDescriptor;
import io.bdeploy.interfaces.directory.EntryChunk;
import io.bdeploy.interfaces.directory.InstanceDirectory;
import io.bdeploy.interfaces.directory.InstanceDirectoryEntry;
import io.bdeploy.interfaces.manifest.ApplicationManifest;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.manifest.InstanceNodeManifest;
import io.bdeploy.interfaces.manifest.ProductManifest;
import io.bdeploy.interfaces.manifest.dependencies.LocalDependencyFetcher;
import io.bdeploy.interfaces.manifest.state.InstanceState;
import io.bdeploy.interfaces.manifest.state.InstanceStateRecord;
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

    private InstanceState getState(InstanceManifest im, BHive hive) {
        return im.getState(hive).setMigrationProvider(() -> migrate(im.getConfiguration().uuid));
    }

    /**
     * @deprecated only used to migrate state, see {@link #getState(InstanceManifest, BHive)}
     */
    @Deprecated(forRemoval = true, since = "1.2.0")
    private InstanceStateRecord migrate(String instance) {
        SortedSet<String> installed = new TreeSet<>();

        // figure out which instance versions and which node manifests exist on which node (configuration wise).
        SortedMap<Key, SortedMap<String, Manifest.Key>> requirements = new TreeMap<>();
        SortedSet<Key> scan = InstanceManifest.scan(hive, false);
        for (Manifest.Key k : scan) {
            InstanceManifest imf = InstanceManifest.of(hive, k);
            String instanceId = imf.getConfiguration().uuid;
            if (!instanceId.equals(instance)) {
                continue;
            }

            if (imf.getInstanceNodeManifests().isEmpty()) {
                continue;
            } else {
                boolean hasApps = false;
                for (Manifest.Key inmk : imf.getInstanceNodeManifests().values()) {
                    InstanceNodeManifest inmf = InstanceNodeManifest.of(hive, inmk);
                    if (!inmf.getConfiguration().applications.isEmpty()) {
                        hasApps = true;
                        break;
                    }
                }
                if (!hasApps) {
                    continue; // ignore this version, cannot be "installed".
                }
            }

            // found one, calculate which minion needs which manifest installed
            requirements.put(k, imf.getInstanceNodeManifests());
        }

        // figure out which minions we need to contact in total.
        SortedSet<String> minions = requirements.values().stream().flatMap(v -> v.entrySet().stream()).map(e -> e.getKey())
                .collect(Collectors.toCollection(TreeSet::new));

        // for each required minion, figure out available versions.
        SortedMap<String, List<String>> available = new TreeMap<>();
        SortedSet<String> offline = new TreeSet<>();
        for (String minion : minions) {
            // don't check client node, it will never be online, and is not required (offline = OK).
            if (minion.equals(InstanceManifest.CLIENT_NODE_NAME)) {
                offline.add(minion);
                continue;
            }

            RemoteService remote = root.getMinions().get(minion);
            try {
                SlaveDeploymentResource sdr = ResourceProvider.getResource(remote, SlaveDeploymentResource.class);
                InstanceStateRecord instanceState = sdr.getInstanceState(instance);
                available.put(minion, instanceState == null ? Collections.emptyList() : instanceState.installedTags);
            } catch (Exception e) {
                log.warn("Problem contacting minion to fetch available deployments: {}", minion);
                offline.add(minion);
            }
        }

        // cross check which requirement is fulfilled, regarding offline minions as OK.
        check: for (Entry<Key, SortedMap<String, Key>> entry : requirements.entrySet()) {
            SortedMap<String, Key> rqs = entry.getValue();
            for (Entry<String, Key> toFulfill : rqs.entrySet()) {
                if (!offline.contains(toFulfill.getKey())
                        && !available.get(toFulfill.getKey()).contains(toFulfill.getValue().getTag())) {
                    // at least one requirement failed, continue with next version.
                    continue check;
                }
            }
            installed.add(entry.getKey().getTag());
        }

        InstanceStateRecord result = new InstanceStateRecord();
        result.installedTags.addAll(installed);

        Manifest.Key masterActive = root.getState().activeMasterVersions.get(instance);
        if (masterActive != null) {
            result.activeTag = masterActive.getTag();
        }

        return result;
    }

    @Override
    public InstanceStateRecord getInstanceState(String instance) {
        InstanceManifest im = InstanceManifest.load(hive, instance, null);
        return getState(im, hive).read();
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

        getState(imf, hive).install(key.getTag());
    }

    @Override
    public void activate(Key key) {
        InstanceManifest imf = InstanceManifest.of(hive, key);

        if (!isFullyDeployed(imf)) {
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

        getState(imf, hive).activate(key.getTag());
    }

    /**
     * @param imf the {@link InstanceManifest} to check.
     * @param ignoreOffline whether to regard an instance as deployed even if a participating node is offline.
     * @return whether the given {@link InstanceManifest} is fully deployed to all required minions.
     */
    private boolean isFullyDeployed(InstanceManifest imf) {
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

            InstanceStateRecord deployments;
            try {
                SlaveDeploymentResource slave = ResourceProvider.getResource(minion, SlaveDeploymentResource.class);
                deployments = slave.getInstanceState(instanceId);
            } catch (Exception e) {
                throw new IllegalStateException("Node offline while checking state: " + minionName);
            }

            if (deployments.installedTags.isEmpty()) {
                if (log.isDebugEnabled()) {
                    log.debug("Minion {} does not contain any deployment for {}", minionName, instanceId);
                }
                return false;
            }
            if (!deployments.installedTags.contains(toDeploy.getTag())) {
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

        if (!isFullyDeployed(imf)) {
            return; // no need to.
        }

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
            getState(imf, hive).uninstall(key.getTag());
        } finally {
            removing.done();
        }

        // no need to clean up the hive, this is done elsewhere.
    }

    @Override
    public void updateTo(String uuid, String productTag) {
        InstanceManifest latest = InstanceManifest.load(hive, uuid, null);
        Manifest.Key product = latest.getConfiguration().product;
        Manifest.Key updateTo = new Manifest.Key(product.getName(), productTag);

        // validate that the product is there.
        if (!hive.execute(new ManifestExistsOperation().setManifest(updateTo))) {
            throw new WebApplicationException("Cannot find product tag " + productTag, Status.NOT_FOUND);
        }

        ProductManifest pm = ProductManifest.of(hive, updateTo);
        InstanceConfiguration updatedIc = latest.getConfiguration();

        // update product on manifest
        updatedIc.product = pm.getKey();

        SortedMap<String, InstanceNodeConfiguration> incs = new TreeMap<>();
        for (Entry<String, Key> nodeEntry : latest.getInstanceNodeManifests().entrySet()) {
            InstanceNodeManifest inm = InstanceNodeManifest.of(hive, nodeEntry.getValue());
            InstanceNodeConfiguration cfg = inm.getConfiguration();

            updateNodeTo(cfg, pm);

            incs.put(nodeEntry.getKey(), cfg);
        }

        // update config tree if the instance does not yet have a config tree.
        if (updatedIc.configTree == null) {
            updatedIc.configTree = pm.getConfigTemplateTreeId();
        }

        // actually persist, calculate a new key.
        String name = InstanceManifest.getRootName(uuid);
        Long next = hive.execute(new ManifestNextIdOperation().setManifestName(name));
        Manifest.Key target = new Manifest.Key(name, next.toString());

        createInstanceVersion(target, pm, updatedIc, incs);
    }

    private void updateNodeTo(InstanceNodeConfiguration cfg, ProductManifest pm) {
        cfg.product = pm.getKey();

        // verify all applications are still there & update their version
        for (ApplicationConfiguration appCfg : cfg.applications) {
            String appName = appCfg.application.getName();
            Manifest.Key updateAppTo = null;
            for (Manifest.Key available : pm.getApplications()) {
                if (available.getName().equals(appName)) {
                    updateAppTo = available;
                }
            }
            if (updateAppTo == null) {
                throw new WebApplicationException(
                        "Application " + appName + " used in instance configuration, but no longer available in " + pm.getKey(),
                        Status.PRECONDITION_FAILED);
            }
            ApplicationManifest amf = ApplicationManifest.of(hive, updateAppTo);

            updateApplicationTo(appCfg, amf);
        }
    }

    private void updateApplicationTo(ApplicationConfiguration appCfg, ApplicationManifest amf) {
        appCfg.application = amf.getKey();

        // verify that all parameters which are currently configured are still there, update fixed parameter values
        appCfg.start = updateApplicationCommandTo(appCfg, appCfg.start, amf.getDescriptor().startCommand);
        appCfg.stop = updateApplicationCommandTo(appCfg, appCfg.stop, amf.getDescriptor().stopCommand);
    }

    private CommandConfiguration updateApplicationCommandTo(ApplicationConfiguration appCfg, CommandConfiguration command,
            ExecutableDescriptor desc) {
        if (desc == null) {
            // command was removed, remove as well.
            return null;
        }

        if (command == null) {
            throw new WebApplicationException(
                    "Headless update of application (" + appCfg.name + ") not possible, command has been added",
                    Status.PRECONDITION_FAILED);
        }

        command.executable = desc.launcherPath;

        Map<String, ParameterConfiguration> byUid = new TreeMap<>();
        for (ParameterConfiguration cfg : command.parameters) {
            // find according description.
            Optional<ParameterDescriptor> match = desc.parameters.stream().filter(p -> p.uid.equals(cfg.uid)).findAny();
            if (!match.isPresent()) {
                throw new WebApplicationException("Headless update of application (" + appCfg.name
                        + ") not possible, parameter not found (" + cfg.uid + ")", Status.PRECONDITION_FAILED);
            }

            if (match.get().fixed) {
                cfg.value = match.get().defaultValue;
                cfg.preRender(match.get());
            }
            byUid.put(cfg.uid, cfg);
        }

        for (ParameterDescriptor pd : desc.parameters) {
            if (pd.mandatory && !byUid.containsKey(pd.uid)) {
                throw new WebApplicationException("Headless update of application (" + appCfg.name
                        + ") not possible, missing mandatory parameter (" + pd.uid + ")", Status.PRECONDITION_FAILED);
            }
        }

        return command;
    }

    private void createInstanceVersion(Manifest.Key target, ProductManifest pm, InstanceConfiguration config,
            SortedMap<String, InstanceNodeConfiguration> nodes) {

        InstanceManifest.Builder builder = new InstanceManifest.Builder();
        builder.setInstanceConfiguration(config);
        builder.setKey(target);

        for (Entry<String, InstanceNodeConfiguration> entry : nodes.entrySet()) {
            InstanceNodeManifest.Builder inmb = new InstanceNodeManifest.Builder();
            inmb.setConfigTreeId(config.configTree);
            inmb.setMinionName(entry.getKey());
            inmb.setInstanceNodeConfiguration(entry.getValue());
            inmb.setKey(new Manifest.Key(config.uuid + '/' + entry.getKey(), target.getTag()));

            builder.addInstanceNodeManifest(entry.getKey(), inmb.insert(hive));
        }

        builder.insert(hive);
    }

    @Override
    public List<InstanceDirectory> getDataDirectorySnapshots(String instanceId) {
        List<InstanceDirectory> result = new ArrayList<>();

        String activeTag = getInstanceState(instanceId).activeTag;
        if (activeTag == null) {
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
        String activeTag = getInstanceState(uuid).activeTag;
        if (activeTag == null) {
            throw new WebApplicationException("No active deployment for " + uuid, Status.NOT_FOUND);
        }

        InstanceManifest imf = InstanceManifest.load(hive, uuid, activeTag);
        ClientApplicationConfiguration cfg = new ClientApplicationConfiguration();
        cfg.clientConfig = imf.getApplicationConfiguration(hive, application);
        if (cfg.clientConfig == null) {
            throw new WebApplicationException("Cannot find application " + application + " in instance " + uuid,
                    Status.NOT_FOUND);
        }

        ApplicationManifest amf = ApplicationManifest.of(hive, cfg.clientConfig.application);
        cfg.clientDesc = amf.getDescriptor();
        cfg.instanceKey = imf.getManifest();
        cfg.configTreeId = imf.getConfiguration().configTree;

        // application key MUST be a ScopedManifestKey. dependencies /must/ be present
        ScopedManifestKey smk = ScopedManifestKey.parse(cfg.clientConfig.application);
        cfg.resolvedRequires.addAll(
                new LocalDependencyFetcher().fetch(hive, amf.getDescriptor().runtimeDependencies, smk.getOperatingSystem()));

        // load splash screen and icon from hive and send along.
        cfg.clientSplashData = amf.readBrandingSplashScreen(hive);
        cfg.clientImageIcon = amf.readBrandingIcon(hive);

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
    public SortedMap<Manifest.Key, InstanceConfiguration> listInstanceConfigurations(boolean latest) {
        SortedSet<Key> scan = InstanceManifest.scan(hive, latest);
        SortedMap<Manifest.Key, InstanceConfiguration> result = new TreeMap<>();

        scan.stream().forEach(k -> result.put(k, InstanceManifest.of(hive, k).getConfiguration()));
        return result;
    }

}
