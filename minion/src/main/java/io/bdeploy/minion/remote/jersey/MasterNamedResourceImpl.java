package io.bdeploy.minion.remote.jersey;

import static io.bdeploy.common.util.RuntimeAssert.assertNotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.api.product.v1.impl.LocalDependencyFetcher;
import io.bdeploy.api.product.v1.impl.ScopedManifestKey;
import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.BHiveTransactions.Transaction;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.op.ExportTreeOperation;
import io.bdeploy.bhive.op.ImportTreeOperation;
import io.bdeploy.bhive.op.ManifestDeleteOperation;
import io.bdeploy.bhive.op.ManifestExistsOperation;
import io.bdeploy.bhive.op.ManifestListOperation;
import io.bdeploy.bhive.op.ManifestNextIdOperation;
import io.bdeploy.bhive.op.remote.PushOperation;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.ActivityReporter.Activity;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.common.util.RuntimeAssert;
import io.bdeploy.common.util.TaskExecutor;
import io.bdeploy.common.util.UuidHelper;
import io.bdeploy.interfaces.configuration.dcu.ApplicationConfiguration;
import io.bdeploy.interfaces.configuration.instance.ClientApplicationConfiguration;
import io.bdeploy.interfaces.configuration.instance.FileStatusDto;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceConfigurationDto;
import io.bdeploy.interfaces.configuration.instance.InstanceGroupConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceNodeConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceNodeConfigurationDto;
import io.bdeploy.interfaces.configuration.instance.InstanceUpdateDto;
import io.bdeploy.interfaces.configuration.pcu.InstanceNodeStatusDto;
import io.bdeploy.interfaces.configuration.pcu.InstanceStatusDto;
import io.bdeploy.interfaces.configuration.pcu.ProcessDetailDto;
import io.bdeploy.interfaces.directory.EntryChunk;
import io.bdeploy.interfaces.directory.RemoteDirectory;
import io.bdeploy.interfaces.directory.RemoteDirectoryEntry;
import io.bdeploy.interfaces.manifest.ApplicationManifest;
import io.bdeploy.interfaces.manifest.InstanceGroupManifest;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.manifest.InstanceNodeManifest;
import io.bdeploy.interfaces.manifest.attributes.CustomAttributesRecord;
import io.bdeploy.interfaces.manifest.banner.InstanceBannerRecord;
import io.bdeploy.interfaces.manifest.history.InstanceManifestHistory.Action;
import io.bdeploy.interfaces.manifest.history.runtime.MasterRuntimeHistoryDto;
import io.bdeploy.interfaces.manifest.state.InstanceState;
import io.bdeploy.interfaces.manifest.state.InstanceStateRecord;
import io.bdeploy.interfaces.manifest.statistics.ClientUsage;
import io.bdeploy.interfaces.manifest.statistics.ClientUsageData;
import io.bdeploy.interfaces.minion.MinionConfiguration;
import io.bdeploy.interfaces.minion.MinionDto;
import io.bdeploy.interfaces.remote.CommonDirectoryEntryResource;
import io.bdeploy.interfaces.remote.MasterNamedResource;
import io.bdeploy.interfaces.remote.NodeDeploymentResource;
import io.bdeploy.interfaces.remote.NodeProcessResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.jersey.JerseyPathWriter.DeleteAfterWrite;
import io.bdeploy.jersey.JerseyWriteLockService.LockingResource;
import io.bdeploy.jersey.JerseyWriteLockService.WriteLock;
import io.bdeploy.minion.MinionRoot;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.SecurityContext;

@LockingResource
public class MasterNamedResourceImpl implements MasterNamedResource {

    private static final Logger log = LoggerFactory.getLogger(MasterNamedResourceImpl.class);

    private final BHive hive;
    private final ActivityReporter reporter;
    private final MinionRoot root;

    @Context
    private SecurityContext context;

    public MasterNamedResourceImpl(MinionRoot root, BHive hive, ActivityReporter reporter) {
        this.root = root;
        this.hive = hive;
        this.reporter = reporter;
    }

    private InstanceState getState(InstanceManifest im, BHive hive) {
        return im.getState(hive);
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
        fragmentReferences.remove(InstanceManifest.CLIENT_NODE_NAME);

        // assure that the product has been pushed to the master (e.g. by central).
        Boolean productExists = hive.execute(new ManifestExistsOperation().setManifest(imf.getConfiguration().product));
        if (!Boolean.TRUE.equals(productExists)) {
            throw new WebApplicationException("Cannot find required product " + imf.getConfiguration().product, Status.NOT_FOUND);
        }

        TaskExecutor executor = new TaskExecutor(reporter);
        for (Map.Entry<String, Manifest.Key> entry : fragmentReferences.entrySet()) {
            String minionName = entry.getKey();
            Manifest.Key toDeploy = entry.getValue();
            RemoteService minion = root.getMinions().getRemote(minionName);

            assertNotNull(minion, "Cannot lookup minion on master: " + minionName);
            assertNotNull(toDeploy, "Cannot lookup minion manifest on master: " + toDeploy);

            // grab all required manifests from the applications
            InstanceNodeManifest inm = InstanceNodeManifest.of(hive, toDeploy);
            LocalDependencyFetcher localDeps = new LocalDependencyFetcher();
            PushOperation pushOp = new PushOperation().setRemote(minion);
            for (ApplicationConfiguration app : inm.getConfiguration().applications) {
                pushOp.addManifest(app.application);
                ApplicationManifest amf = ApplicationManifest.of(hive, app.application);

                // applications /must/ follow the ScopedManifestKey rules.
                ScopedManifestKey smk = ScopedManifestKey.parse(app.application);

                // the dependency must be here. it has been pushed here with the product,
                // since the product /must/ reference all direct dependencies.
                localDeps.fetch(hive, amf.getDescriptor().runtimeDependencies, smk.getOperatingSystem())
                        .forEach(pushOp::addManifest);
            }

            // Make sure the node has the manifest
            pushOp.addManifest(toDeploy);

            // Create the task that pushes all manifests and then installs them on the
            // remote
            NodeDeploymentResource deployment = ResourceProvider.getVersionedResource(minion, NodeDeploymentResource.class,
                    context);
            executor.add(() -> {
                hive.execute(pushOp);
                deployment.install(toDeploy);
            });
        }

        // Execute all tasks
        try {
            executor.run("Installing");
        } catch (Exception ex) {
            throw new WebApplicationException("Installation failed", ex, Status.INTERNAL_SERVER_ERROR);
        }

        getState(imf, hive).install(key.getTag());
        imf.getHistory(hive).record(Action.INSTALL, context.getUserPrincipal().getName(), null);
    }

    @Override
    public void activate(Key key) {
        InstanceManifest imf = InstanceManifest.of(hive, key);

        if (!isFullyDeployed(imf)) {
            throw new WebApplicationException(
                    "Given manifest for UUID " + imf.getConfiguration().uuid + " is not fully deployed: " + key,
                    Status.NOT_FOUND);
        }

        // record de-activation
        String activeTag = imf.getState(hive).read().activeTag;
        if (activeTag != null) {
            try {
                InstanceManifest oldIm = InstanceManifest.load(hive, imf.getConfiguration().uuid, activeTag);
                oldIm.getHistory(hive).record(Action.DEACTIVATE, context.getUserPrincipal().getName(), null);

                // make sure all nodes which no longer participate are deactivated.
                for (Map.Entry<String, Manifest.Key> oldNode : oldIm.getInstanceNodeManifests().entrySet()) {
                    // deactivation by activation later on.
                    if (imf.getInstanceNodeManifests().containsKey(oldNode.getKey())) {
                        continue;
                    }

                    RemoteService rs = root.getMinions().getRemote(oldNode.getKey());
                    if (rs == null) {
                        log.info("Minion {} no longer available for de-activation", oldNode.getKey());
                        continue;
                    }

                    ResourceProvider.getVersionedResource(rs, NodeDeploymentResource.class, context)
                            .deactivate(oldNode.getValue());
                }
            } catch (Exception e) {
                // in case the old version disappeared (manual deletion, automatic migration,
                // ...) we do not
                // want to fail to activate the new version...
                log.debug("Cannot set old version to de-activated", e);
            }
        }

        SortedMap<String, Key> fragments = imf.getInstanceNodeManifests();
        try (Activity activating = reporter.start("Activating on minions...", fragments.size())) {
            for (Map.Entry<String, Manifest.Key> entry : fragments.entrySet()) {
                String minionName = entry.getKey();
                if (InstanceManifest.CLIENT_NODE_NAME.equals(minionName)) {
                    continue;
                }
                Manifest.Key toDeploy = entry.getValue();
                assertNotNull(toDeploy, "Cannot lookup minion manifest on master: " + toDeploy);

                RemoteService minion = root.getMinions().getRemote(minionName);
                NodeDeploymentResource deployment = ResourceProvider.getVersionedResource(minion, NodeDeploymentResource.class,
                        context);
                try {
                    deployment.activate(toDeploy);
                } catch (Exception e) {
                    // log but don't forward exception to the client
                    throw new WebApplicationException("Cannot activate on " + minionName, e, Status.BAD_GATEWAY);
                }

                activating.worked(1);
            }
        }

        getState(imf, hive).activate(key.getTag());
        imf.getHistory(hive).record(Action.ACTIVATE, context.getUserPrincipal().getName(), null);
    }

    /**
     * @param imf the {@link InstanceManifest} to check.
     * @param ignoreOffline whether to regard an instance as deployed even if a
     *            participating node is offline.
     * @return whether the given {@link InstanceManifest} is fully deployed to all
     *         required minions.
     */
    private boolean isFullyDeployed(InstanceManifest imf) {
        SortedMap<String, Key> imfs = imf.getInstanceNodeManifests();
        // No configuration -> no requirements, so always fully deployed.
        if (imfs.isEmpty()) {
            return true;
        }
        // check all minions for their respective availability.
        String instanceId = imf.getConfiguration().uuid;
        for (Map.Entry<String, Manifest.Key> entry : imfs.entrySet()) {
            String nodeName = entry.getKey();
            if (InstanceManifest.CLIENT_NODE_NAME.equals(nodeName)) {
                continue;
            }
            Manifest.Key toDeploy = entry.getValue();
            assertNotNull(toDeploy, "Cannot lookup minion manifest on master: " + toDeploy);

            RemoteService minion = root.getMinions().getRemote(nodeName);
            InstanceStateRecord deployments;
            try {
                NodeDeploymentResource node = ResourceProvider.getVersionedResource(minion, NodeDeploymentResource.class,
                        context);
                deployments = node.getInstanceState(instanceId);
            } catch (Exception e) {
                throw new IllegalStateException("Node offline while checking state: " + nodeName);
            }

            if (deployments.installedTags.isEmpty()) {
                if (log.isDebugEnabled()) {
                    log.debug("Minion {} does not contain any deployment for {}", nodeName, instanceId);
                }
                return false;
            }
            if (!deployments.installedTags.contains(toDeploy.getTag())) {
                if (log.isDebugEnabled()) {
                    log.debug("Minion {} does not have {} available", nodeName, toDeploy);
                }
                return false;
            }
        }
        return true;
    }

    @Override
    public void uninstall(Key key) {
        InstanceManifest imf = InstanceManifest.of(hive, key);

        SortedMap<String, Key> fragments = imf.getInstanceNodeManifests();
        fragments.remove(InstanceManifest.CLIENT_NODE_NAME);

        TaskExecutor executor = new TaskExecutor(reporter);
        for (Map.Entry<String, Manifest.Key> entry : fragments.entrySet()) {
            String minionName = entry.getKey();
            Manifest.Key toRemove = entry.getValue();
            assertNotNull(toRemove, "Cannot lookup minion manifest on master: " + toRemove);

            if (!root.getMinions().hasMinion(minionName)) {
                // minion no longer exists!?
                log.warn("Minion no longer existing: {}. Ignoring.", minionName);
                continue;
            }

            RemoteService minion = root.getMinions().getRemote(minionName);
            NodeDeploymentResource deployment = ResourceProvider.getVersionedResource(minion, NodeDeploymentResource.class,
                    context);
            executor.add(() -> deployment.remove(toRemove));
        }

        // Execute all tasks
        try {
            executor.run("Uninstalling");
        } catch (Exception ex) {
            throw new WebApplicationException("Failed to uninstall.", ex, Status.INTERNAL_SERVER_ERROR);
        }

        getState(imf, hive).uninstall(key.getTag());
        imf.getHistory(hive).record(Action.UNINSTALL, context.getUserPrincipal().getName(), null);
    }

    private Manifest.Key createInstanceVersion(Manifest.Key target, InstanceConfiguration config,
            SortedMap<String, InstanceNodeConfiguration> nodes) {

        InstanceManifest.Builder builder = new InstanceManifest.Builder();
        builder.setInstanceConfiguration(config);
        builder.setKey(target);

        for (Entry<String, InstanceNodeConfiguration> entry : nodes.entrySet()) {
            InstanceNodeConfiguration inc = entry.getValue();
            if (inc == null) {
                continue;
            }

            // make sure redundant data is equal to instance data.
            if (!config.name.equals(inc.name)) {
                log.warn("Instance name of node ({}) not equal to instance name ({}) - aligning.", inc.name, config.name);
                inc.name = config.name;
            }

            inc.copyRedundantFields(config);

            // make sure every application has an ID. NEW applications might have a null ID
            // to be filled out.
            for (ApplicationConfiguration cfg : inc.applications) {
                if (cfg.uid == null) {
                    cfg.uid = UuidHelper.randomId();
                    log.info("New Application {} received ID {}", cfg.name, cfg.uid);
                }
            }

            RuntimeAssert.assertEquals(inc.uuid, config.uuid, "Instance ID not set on nodes");

            InstanceNodeManifest.Builder inmb = new InstanceNodeManifest.Builder();
            inmb.setConfigTreeId(config.configTree);
            inmb.setMinionName(entry.getKey());
            inmb.setInstanceNodeConfiguration(inc);
            inmb.setKey(new Manifest.Key(config.uuid + '/' + entry.getKey(), target.getTag()));

            builder.addInstanceNodeManifest(entry.getKey(), inmb.insert(hive));
        }

        Manifest.Key key = builder.insert(hive);
        InstanceManifest.of(hive, key).getHistory(hive).record(Action.CREATE, context.getUserPrincipal().getName(), null);
        return key;
    }

    @WriteLock
    @Override
    public Manifest.Key update(InstanceUpdateDto update, String expectedTag) {
        InstanceConfigurationDto state = update.config;
        List<FileStatusDto> configUpdates = update.files;

        InstanceConfiguration instanceConfig = state.config;
        String rootName = InstanceManifest.getRootName(instanceConfig.uuid);
        Set<Key> existing = hive.execute(new ManifestListOperation().setManifestName(rootName));
        InstanceManifest oldConfig = null;
        if (expectedTag == null && !existing.isEmpty()) {
            throw new WebApplicationException("Instance already exists: " + instanceConfig.uuid, Status.CONFLICT);
        } else if (expectedTag != null) {
            oldConfig = InstanceManifest.load(hive, instanceConfig.uuid, null);
            if (!oldConfig.getManifest().getTag().equals(expectedTag)) {
                throw new WebApplicationException("Expected version is not the current one: expected=" + expectedTag
                        + ", current=" + oldConfig.getManifest().getTag(), Status.CONFLICT);
            }
        }

        try (Transaction t = hive.getTransactions().begin()) {
            if (configUpdates != null && !configUpdates.isEmpty()) {
                // export existing tree and apply updates.
                // set/reset config tree ID on instanceConfig.
                instanceConfig.configTree = applyConfigUpdates(instanceConfig.configTree, configUpdates);
            }

            // calculate target key.
            String rootTag = hive.execute(new ManifestNextIdOperation().setManifestName(rootName)).toString();
            Manifest.Key rootKey = new Manifest.Key(rootName, rootTag);

            if ((state.nodeDtos == null || state.nodeDtos.isEmpty()) && oldConfig != null) {
                // no new node config - re-apply existing one with new tag, align redundant
                // fields.
                state.nodeDtos = readExistingNodeConfigs(oldConfig);
            }

            // does NOT validate that the product exists, as it might still reside on the
            // central server, not this one.

            SortedMap<String, InstanceNodeConfiguration> nodes = new TreeMap<>();
            if (state.nodeDtos != null) {
                state.nodeDtos.forEach(n -> nodes.put(n.nodeName, n.nodeConfiguration));
            }

            return createInstanceVersion(rootKey, state.config, nodes);
        }
    }

    private ObjectId applyConfigUpdates(ObjectId configTree, List<FileStatusDto> updates) {
        Path tmpDir = null;
        try {
            tmpDir = Files.createTempDirectory(root.getTempDir(), "cfgUp-");
            Path cfgDir = tmpDir.resolve("cfg");

            // 1. export current tree to temp directory
            exportConfigTree(configTree, cfgDir);

            // 2. apply updates to files
            applyUpdates(updates, cfgDir);

            // 3. re-import new tree from temp directory
            return hive.execute(new ImportTreeOperation().setSkipEmpty(true).setSourcePath(cfgDir));
        } catch (IOException e) {
            throw new WebApplicationException("Cannot update configuration files", e);
        } finally {
            if (tmpDir != null) {
                PathHelper.deleteRecursive(tmpDir);
            }
        }
    }

    private void exportConfigTree(ObjectId configTree, Path cfgDir) {
        if (configTree == null) {
            PathHelper.mkdirs(cfgDir);
            return;
        }

        try {
            hive.execute(new ExportTreeOperation().setSourceTree(configTree).setTargetPath(cfgDir));
        } catch (Exception e) {
            // this can happen if the hive was damaged. we allow this case to have a way out
            // if all things break badly.
            log.error("Cannot load existing configuration files", e);
        }
    }

    private void applyUpdates(List<FileStatusDto> updates, Path cfgDir) throws IOException {
        for (FileStatusDto update : updates) {
            Path file = cfgDir.resolve(update.file);
            if (!file.startsWith(cfgDir)) {
                throw new WebApplicationException("Update wants to write to file outside update directory", Status.BAD_REQUEST);
            }

            switch (update.type) {
                case ADD:
                    PathHelper.mkdirs(file.getParent());
                    Files.write(file, Base64.decodeBase64(update.content), StandardOpenOption.CREATE_NEW,
                            StandardOpenOption.SYNC);
                    break;
                case DELETE:
                    Files.delete(file);
                    break;
                case EDIT:
                    Files.write(file, Base64.decodeBase64(update.content), StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.SYNC);
                    break;
            }
        }
    }

    private List<InstanceNodeConfigurationDto> readExistingNodeConfigs(InstanceManifest oldConfig) {
        List<InstanceNodeConfigurationDto> result = new ArrayList<>();
        for (Map.Entry<String, Manifest.Key> entry : oldConfig.getInstanceNodeManifests().entrySet()) {
            InstanceNodeManifest oldInmf = InstanceNodeManifest.of(hive, entry.getValue());
            InstanceNodeConfiguration nodeConfig = oldInmf.getConfiguration();

            InstanceNodeConfigurationDto dto = new InstanceNodeConfigurationDto(entry.getKey());
            dto.nodeConfiguration = nodeConfig;

            result.add(dto);
        }
        return result;
    }

    @WriteLock
    @Override
    public void delete(String instanceUuid) {
        Set<Key> allInstanceObjects = hive.execute(new ManifestListOperation().setManifestName(instanceUuid));
        allInstanceObjects.forEach(x -> hive.execute(new ManifestDeleteOperation().setToDelete(x)));
    }

    @Override
    public void deleteVersion(String instanceUuid, String tag) {
        InstanceManifest.delete(hive, new Manifest.Key(InstanceManifest.getRootName(instanceUuid), tag));
    }

    @Override
    public List<RemoteDirectory> getDataDirectorySnapshots(String instanceId) {
        List<RemoteDirectory> result = new ArrayList<>();

        String activeTag = getInstanceState(instanceId).activeTag;
        if (activeTag == null) {
            throw new WebApplicationException("Cannot find active version for instance " + instanceId, Status.NOT_FOUND);
        }

        MinionConfiguration minions = root.getMinions();
        InstanceStatusDto status = getStatus(instanceId);
        for (String nodeName : status.getNodesWithApps()) {
            RemoteDirectory idd = new RemoteDirectory();
            idd.minion = nodeName;
            idd.uuid = instanceId;

            try {
                RemoteService service = minions.getRemote(nodeName);

                NodeDeploymentResource sdr = ResourceProvider.getVersionedResource(service, NodeDeploymentResource.class,
                        context);
                List<RemoteDirectoryEntry> iddes = sdr.getDataDirectoryEntries(instanceId);
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
    public EntryChunk getEntryContent(String minion, RemoteDirectoryEntry entry, long offset, long limit) {
        RemoteService svc = root.getMinions().getRemote(minion);
        if (svc == null) {
            throw new WebApplicationException("Cannot find minion " + minion, Status.NOT_FOUND);
        }
        CommonDirectoryEntryResource sdr = ResourceProvider.getVersionedResource(svc, CommonDirectoryEntryResource.class,
                context);
        return sdr.getEntryContent(entry, offset, limit);
    }

    @Override
    public Response getEntryStream(String minion, RemoteDirectoryEntry entry) {
        RemoteService svc = root.getMinions().getRemote(minion);
        if (svc == null) {
            throw new WebApplicationException("Cannot find minion " + minion, Status.NOT_FOUND);
        }
        CommonDirectoryEntryResource sdr = ResourceProvider.getVersionedResource(svc, CommonDirectoryEntryResource.class,
                context);
        return sdr.getEntryStream(entry);
    }

    @Override
    public void deleteDataEntry(String minion, RemoteDirectoryEntry entry) {
        RemoteService svc = root.getMinions().getRemote(minion);
        if (svc == null) {
            throw new WebApplicationException("Cannot find minion " + minion, Status.NOT_FOUND);
        }
        NodeDeploymentResource sdr = ResourceProvider.getVersionedResource(svc, NodeDeploymentResource.class, context);
        sdr.deleteDataEntry(entry);
    }

    @Override
    public ClientApplicationConfiguration getClientConfiguration(String uuid, String application) {
        String activeTag = getInstanceState(uuid).activeTag;
        if (activeTag == null) {
            throw new WebApplicationException("No active deployment for " + uuid, Status.NOT_FOUND);
        }

        InstanceManifest imf = InstanceManifest.load(hive, uuid, activeTag);
        InstanceGroupConfiguration groupCfg = new InstanceGroupManifest(hive).read();

        ClientApplicationConfiguration cfg = new ClientApplicationConfiguration();
        cfg.activeTag = activeTag;
        cfg.instanceGroupTitle = groupCfg.title;
        cfg.appConfig = imf.getApplicationConfiguration(hive, application);
        if (cfg.appConfig == null) {
            throw new WebApplicationException("Cannot find application " + application + " in instance " + uuid,
                    Status.NOT_FOUND);
        }
        cfg.instanceConfig = imf.getInstanceNodeConfiguration(hive, application);

        ApplicationManifest amf = ApplicationManifest.of(hive, cfg.appConfig.application);
        cfg.appDesc = amf.getDescriptor();

        // application key MUST be a ScopedManifestKey. dependencies /must/ be present
        ScopedManifestKey smk = ScopedManifestKey.parse(cfg.appConfig.application);
        cfg.resolvedRequires.addAll(
                new LocalDependencyFetcher().fetch(hive, amf.getDescriptor().runtimeDependencies, smk.getOperatingSystem()));

        // load splash screen and icon from hive and send along.
        cfg.clientSplashData = amf.readBrandingSplashScreen(hive);
        cfg.clientImageIcon = amf.readBrandingIcon(hive);

        return cfg;
    }

    @Override
    public void logClientStart(String instanceId, String applicationId, String hostname) {
        log.debug("client start for {}, application {} on host {}", instanceId, applicationId, hostname);
        InstanceManifest im = InstanceManifest.load(hive, instanceId, null);
        ClientUsage clientUsage = im.getClientUsage(hive);
        ClientUsageData data = clientUsage.read();
        data.increment(applicationId, hostname);
        clientUsage.set(data);
    }

    @Override
    public ClientUsageData getClientUsage(String instanceId) {
        return InstanceManifest.load(hive, instanceId, null).getClientUsage(hive).read();
    }

    @Override
    @DeleteAfterWrite
    public Path getClientInstanceConfiguration(Manifest.Key instanceId) {
        return null; // FIXME: DCS-396: client config shall not contain server config files.
    }

    @Override
    public void start(String instanceId) {
        MinionConfiguration minions = root.getMinions();
        InstanceStatusDto status = getStatus(instanceId);
        for (String nodeName : status.getNodesWithApps()) {
            RemoteService service = minions.getRemote(nodeName);
            NodeProcessResource spc = ResourceProvider.getVersionedResource(service, NodeProcessResource.class, context);
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
        String minion = status.getNodeWhereAppIsDeployed(applicationId);
        if (minion == null) {
            throw new WebApplicationException("Application is not deployed on any node.", Status.INTERNAL_SERVER_ERROR);
        }

        // Now launch this application on the minion
        try (Activity activity = reporter.start("Launching " + status.getAppStatus(applicationId).appName, -1)) {
            RemoteService service = root.getMinions().getRemote(minion);
            NodeProcessResource spc = ResourceProvider.getVersionedResource(service, NodeProcessResource.class, context);
            spc.start(instanceId, applicationId);
        }
    }

    @Override
    public void stop(String instanceId) {
        InstanceStatusDto status = getStatus(instanceId);
        MinionConfiguration minions = root.getMinions();

        // Find out all nodes where at least one application is running
        Collection<String> nodes = status.getNodesWhereAppsAreRunningOrScheduled();

        try (Activity activity = reporter.start("Stopping Instance", nodes.size())) {
            for (String node : nodes) {
                RemoteService service = minions.getRemote(node);
                NodeProcessResource spc = ResourceProvider.getVersionedResource(service, NodeProcessResource.class, context);
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
        try (Activity activity = reporter.start("Stopping " + status.getAppStatus(applicationId).appName, -1)) {
            RemoteService service = root.getMinions().getRemote(nodeName);
            NodeProcessResource spc = ResourceProvider.getVersionedResource(service, NodeProcessResource.class, context);
            spc.stop(instanceId, applicationId);
        }
    }

    @Override
    public RemoteDirectory getOutputEntry(String instanceId, String tag, String applicationId) {
        // master has the instance manifest.
        Manifest.Key instanceKey = new Manifest.Key(InstanceManifest.getRootName(instanceId), tag);
        InstanceManifest imf = InstanceManifest.of(hive, instanceKey);

        for (Map.Entry<String, Manifest.Key> entry : imf.getInstanceNodeManifests().entrySet()) {
            InstanceNodeManifest inmf = InstanceNodeManifest.of(hive, entry.getValue());
            for (ApplicationConfiguration app : inmf.getConfiguration().applications) {
                if (!app.uid.equals(applicationId)) {
                    continue;
                }

                // this is our app
                RemoteDirectory id = new RemoteDirectory();
                id.minion = entry.getKey();
                id.uuid = instanceId;

                try {
                    RemoteService svc = root.getMinions().getRemote(entry.getKey());
                    NodeProcessResource spr = ResourceProvider.getVersionedResource(svc, NodeProcessResource.class, context);
                    RemoteDirectoryEntry oe = spr.getOutputEntry(instanceId, tag, applicationId);

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

        throw new WebApplicationException("Cannot find application " + applicationId + " in " + instanceId + ":" + tag,
                Status.NOT_FOUND);
    }

    @Override
    public InstanceStatusDto getStatus(String instanceId) {
        InstanceStatusDto instanceStatus = new InstanceStatusDto(instanceId);

        MinionConfiguration minions = root.getMinions();
        try (Activity activity = reporter.start("Read Node Processes", minions.size())) {
            for (Entry<String, MinionDto> entry : minions.entrySet()) {
                String minion = entry.getKey();
                MinionDto dto = entry.getValue();
                NodeProcessResource spc = ResourceProvider.getVersionedResource(dto.remote, NodeProcessResource.class, context);
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
    public ProcessDetailDto getProcessDetails(String instanceId, String appUid) {
        // Check if the application is running on a node
        InstanceStatusDto status = getStatus(instanceId);
        String nodeName = status.getNodeWhereAppIsRunning(appUid);

        // Check if the application is deployed on a node
        if (nodeName == null) {
            nodeName = status.getNodeWhereAppIsDeployed(appUid);
        }

        // Application is nowhere deployed and nowhere running
        if (nodeName == null) {
            return null;
        }

        // Query process details
        try {
            RemoteService svc = root.getMinions().getRemote(nodeName);
            NodeProcessResource spr = ResourceProvider.getVersionedResource(svc, NodeProcessResource.class, context);
            return spr.getProcessDetails(instanceId, appUid);
        } catch (Exception e) {
            throw new WebApplicationException(
                    "Cannot fetch process status from " + nodeName + " for " + instanceId + ", " + appUid, e);
        }
    }

    @Override
    public String generateWeakToken(String principal) {
        return root.createWeakToken(principal);
    }

    @Override
    public void writeToStdin(String instanceId, String applicationId, String data) {
        InstanceStatusDto status = getStatus(instanceId);
        String minion = status.getNodeWhereAppIsRunning(applicationId);
        if (minion == null) {
            throw new WebApplicationException("Application is not running on any node.", Status.INTERNAL_SERVER_ERROR);
        }

        RemoteService service = root.getMinions().getRemote(minion);
        NodeProcessResource spc = ResourceProvider.getVersionedResource(service, NodeProcessResource.class, context);
        spc.writeToStdin(instanceId, applicationId, data);
    }

    @Override
    public Map<Integer, Boolean> getPortStates(String minion, List<Integer> ports) {
        RemoteService svc = root.getMinions().getRemote(minion);
        if (svc == null) {
            throw new WebApplicationException("Cannot find minion " + minion, Status.NOT_FOUND);
        }
        NodeDeploymentResource sdr = ResourceProvider.getVersionedResource(svc, NodeDeploymentResource.class, context);
        return sdr.getPortStates(ports);
    }

    @Override
    public MasterRuntimeHistoryDto getRuntimeHistory(String instanceId) {
        MasterRuntimeHistoryDto history = new MasterRuntimeHistoryDto();
        for (Map.Entry<String, MinionDto> entry : root.getMinions().entrySet()) {
            String minionName = entry.getKey();
            RemoteService service = entry.getValue().remote;
            try {
                NodeProcessResource spr = ResourceProvider.getVersionedResource(service, NodeProcessResource.class, context);
                history.add(minionName, spr.getRuntimeHistory(instanceId));
            } catch (Exception e) {
                history.addError(minionName, "Cannot load runtime history (" + e.getMessage() + ")");
            }
        }
        return history;
    }

    @Override
    public InstanceBannerRecord getBanner(String instanceId) {
        InstanceManifest im = InstanceManifest.load(hive, instanceId, null);
        return im.getBanner(hive).read();
    }

    @Override
    public void updateBanner(String instanceId, InstanceBannerRecord instanceBannerRecord) {
        InstanceManifest im = InstanceManifest.load(hive, instanceId, null);
        im.getBanner(hive).set(instanceBannerRecord);

        im.getHistory(hive).record(instanceBannerRecord.text != null ? Action.BANNER_SET : Action.BANNER_CLEAR,
                context.getUserPrincipal().getName(), null);
    }

    @Override
    public CustomAttributesRecord getAttributes(String instanceId) {
        InstanceManifest im = InstanceManifest.load(hive, instanceId, null);
        return im.getAttributes(hive).read();
    }

    @Override
    public void updateAttributes(String instanceId, CustomAttributesRecord attributes) {
        InstanceManifest im = InstanceManifest.load(hive, instanceId, null);
        im.getAttributes(hive).set(attributes);
    }
}
