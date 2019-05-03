package io.bdeploy.ui.api.impl;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

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
import io.bdeploy.bhive.op.InsertManifestOperation;
import io.bdeploy.bhive.op.ManifestDeleteOperation;
import io.bdeploy.bhive.op.ManifestExistsOperation;
import io.bdeploy.bhive.op.ManifestListOperation;
import io.bdeploy.bhive.op.ManifestLoadOperation;
import io.bdeploy.bhive.op.ManifestNextIdOperation;
import io.bdeploy.bhive.op.remote.PushOperation;
import io.bdeploy.bhive.op.remote.TransferStatistics;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.ActivityReporter.Activity;
import io.bdeploy.common.NoThrowAutoCloseable;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.common.util.RuntimeAssert;
import io.bdeploy.common.util.StringHelper;
import io.bdeploy.common.util.UnitHelper;
import io.bdeploy.interfaces.NodeStatus;
import io.bdeploy.interfaces.configuration.dcu.ApplicationConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration.InstancePurpose;
import io.bdeploy.interfaces.configuration.instance.InstanceNodeConfiguration;
import io.bdeploy.interfaces.configuration.pcu.InstanceStatusDto;
import io.bdeploy.interfaces.configuration.pcu.ProcessStatusDto;
import io.bdeploy.interfaces.descriptor.client.ClientDescriptor;
import io.bdeploy.interfaces.manifest.ApplicationManifest;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.manifest.InstanceManifest.Builder;
import io.bdeploy.interfaces.manifest.InstanceNodeManifest;
import io.bdeploy.interfaces.manifest.ProductManifest;
import io.bdeploy.interfaces.remote.MasterNamedResource;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.ui.api.AuthService;
import io.bdeploy.ui.api.InstanceResource;
import io.bdeploy.ui.api.Minion;
import io.bdeploy.ui.api.ProcessResource;
import io.bdeploy.ui.dto.DeploymentStateDto;
import io.bdeploy.ui.dto.InstanceConfigurationDto;
import io.bdeploy.ui.dto.InstanceNodeConfigurationDto;
import io.bdeploy.ui.dto.InstanceNodeConfigurationListDto;
import io.bdeploy.ui.dto.InstanceVersionDto;

public class InstanceResourceImpl implements InstanceResource {

    private static final Logger log = LoggerFactory.getLogger(InstanceResourceImpl.class);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
            .withLocale(Locale.getDefault()).withZone(ZoneId.systemDefault());

    private final BHive hive;
    private final String group;

    @Inject
    private AuthService auth;

    @Inject
    private Minion minion;

    @Inject
    private ActivityReporter reporter;

    @Context
    private SecurityContext context;

    @Context
    private ResourceContext rc;

    public InstanceResourceImpl(String group, BHive hive) {
        this.group = group;
        this.hive = hive;
    }

    @Override
    public List<InstanceConfiguration> list() {
        auth.addRecentlyUsedInstanceGroup(context.getUserPrincipal().getName(), group);

        // Clear security token before sending via REST
        List<InstanceConfiguration> configurations = internalList();
        configurations.forEach(c -> clearToken(c));
        return configurations;
    }

    @Override
    public List<InstanceVersionDto> listVersions(String instanceId) {
        String rootName = InstanceManifest.getRootName(instanceId);
        SortedSet<Manifest.Key> all = hive.execute(new ManifestListOperation().setManifestName(rootName));
        if (all.isEmpty()) {
            return Collections.emptyList();
        }
        List<InstanceVersionDto> result = new ArrayList<>();
        for (Manifest.Key key : all) {
            try {
                InstanceManifest imf = InstanceManifest.of(hive, key);
                InstanceVersionDto dto = new InstanceVersionDto();
                dto.key = key;
                dto.product = imf.getConfiguration().product;
                result.add(dto);
            } catch (Exception e) {
                log.error("cannot load instance from key " + key, e);
            }
        }
        return result;
    }

    @Override
    public InstanceConfiguration readVersion(String instanceId, String versionTag) {
        String rootName = InstanceManifest.getRootName(instanceId);
        Manifest.Key key = new Manifest.Key(rootName, versionTag);
        if (!hive.execute(new ManifestExistsOperation().setManifest(key))) {
            throw new WebApplicationException("Given instance version does not exist", Status.NOT_FOUND);
        }
        return InstanceManifest.of(hive, key).getConfiguration();
    }

    private List<InstanceConfiguration> internalList() {
        SortedSet<Key> scan = InstanceManifest.scan(hive, true);
        return scan.stream().map(k -> InstanceManifest.of(hive, k).getConfiguration()).collect(Collectors.toList());
    }

    @Override
    public void create(InstanceConfiguration instanceConfig) {
        String rootName = InstanceManifest.getRootName(instanceConfig.uuid);
        SortedSet<Key> existing = hive.execute(new ManifestListOperation().setManifestName(rootName));
        if (!existing.isEmpty()) {
            throw new WebApplicationException("Instance already exists: " + instanceConfig.uuid, Status.CONFLICT);
        }

        // this means there is no token, and no way to establish a secure connection.
        // in this case we're looking up a target with the same URI from all other
        // available instances in the group.
        if (instanceConfig.target.getKeyStore() == null) {
            List<InstanceConfiguration> list = internalList();
            for (InstanceConfiguration config : list) {
                if (config.target.getUri().equals(instanceConfig.target.getUri())) {
                    instanceConfig.target = config.target;
                    break;
                }
            }
        }

        if (instanceConfig.target.getKeyStore() == null) {
            // still null, no strategy here => error
            throw new WebApplicationException("Cannot find existing master with given URL", Status.NOT_FOUND);
        }

        ProductManifest product = ProductManifest.of(hive, instanceConfig.product);
        if (product == null) {
            throw new WebApplicationException("Product not found: " + instanceConfig.product, Status.NOT_FOUND);
        }

        new InstanceManifest.Builder().setInstanceConfiguration(instanceConfig).insert(hive);
    }

    @Override
    public InstanceConfiguration read(String instance) {
        InstanceManifest manifest = InstanceManifest.load(hive, instance, null);
        InstanceConfiguration configuration = manifest.getConfiguration();
        clearToken(configuration);
        return configuration;
    }

    @Override
    public void update(String instance, InstanceConfigurationDto dto) {
        InstanceManifest oldConfig = InstanceManifest.load(hive, instance, null);

        Builder newConfig = new InstanceManifest.Builder();
        InstanceConfiguration config = dto.config;

        // calculate target key.
        String rootName = InstanceManifest.getRootName(oldConfig.getConfiguration().uuid);
        String rootTag = hive.execute(new ManifestNextIdOperation().setManifestName(rootName)).toString();
        Manifest.Key rootKey = new Manifest.Key(rootName, rootTag);
        InstanceConfiguration cfg;

        if (config != null) {
            // TODO: assert that manifest version is the same as when the client loaded the config.
            RuntimeAssert.assertEquals(oldConfig.getConfiguration().uuid, config.uuid, "Instance UUID changed");
            RuntimeAssert.assertEquals(oldConfig.getConfiguration().uuid, instance, "Instance UUID changed");

            // Client will only send a token if he wants to update it
            // Thus we take the token from the old version if it is empty
            RemoteService newTarget = config.target;
            RemoteService oldTarget = oldConfig.getConfiguration().target;
            if (StringHelper.isNullOrEmpty(newTarget.getAuthPack())) {
                config.target = new RemoteService(newTarget.getUri(), oldTarget.getAuthPack());
            }

            // apply node configurations from the previous instance version on update.
            cfg = config;
        } else {
            // no new config - load existing one.
            cfg = oldConfig.getConfiguration();
        }

        newConfig.setInstanceConfiguration(cfg);

        if (dto.nodeDtos != null) {
            // Update the node assignment and the configuration
            for (InstanceNodeConfigurationDto nodeDto : dto.nodeDtos) {
                String minionName = nodeDto.nodeName;
                InstanceNodeConfiguration nodeConfig = nodeDto.nodeConfiguration;

                // when a user removes all applications then we also delete the node configuration
                if (nodeConfig == null || nodeConfig.applications.isEmpty()) {
                    continue;
                }

                // make sure redundant data is equal to instance data.
                if (!cfg.name.equals(nodeConfig.name)) {
                    log.warn("Instance name of node (" + nodeConfig.name + ") not equal to instance name (" + cfg.name
                            + ") - aligning.");
                    nodeConfig.name = cfg.name;
                }

                nodeConfig.autoStart = cfg.autoStart;

                RuntimeAssert.assertEquals(nodeDto.nodeConfiguration.uuid, instance, "Instance ID not set on nodes");
                try {
                    Path cfgTmpDir = Files.createTempDirectory(minion.getTempDir(), "cfg-");
                    try {
                        // FIXME: REAL configuration file data :)
                        fixmeExtractDefaultConfig(cfgTmpDir, hive, nodeConfig);

                        String mfName = instance + "/" + minionName;
                        Key instanceNodeKey = new InstanceNodeManifest.Builder().setInstanceNodeConfiguration(nodeConfig)
                                .setMinionName(minionName).setConfigSource(cfgTmpDir).setKey(new Manifest.Key(mfName, rootTag))
                                .insert(hive);
                        newConfig.addInstanceNodeManifest(minionName, instanceNodeKey);
                    } finally {
                        PathHelper.deleteRecursive(cfgTmpDir);
                    }
                } catch (IOException e) {
                    throw new WebApplicationException("Internal IO error", e);
                }
            }
        } else {
            // no new node config - apply existing one.

            // BUG: DCS-197: we need to copy the instance node manifests to a new version to force re-deploy.
            // This is required to allow undeploy unconditionally and have a proper "deployed" flag.
            // Ideally no modification to the nodes would mean no need to update something on the node. But
            // we cannot (currently) identify whether a single node config version is used by multiple
            // instance configurations, thus we need to artificially keep them separate.

            // in addition, version number is right now fully aligned with the root instance manifest. The
            // node manifest tag is used when manifesting to the target slave. Diverging tags is not really
            // nice for the user when navigating the deploy directory directly..

            for (Map.Entry<String, Manifest.Key> entry : oldConfig.getInstanceNodeManifests().entrySet()) {
                // no logic, simply assign the new manifest version to the exact same "thing".
                Manifest.Builder builder = new Manifest.Builder(new Manifest.Key(entry.getValue().getName(), rootTag));

                // TODO: update redundant data (autoStart, description, ...)
                Manifest oldMf = hive.execute(new ManifestLoadOperation().setManifest(entry.getValue()));
                builder.setRoot(oldMf.getRoot());
                oldMf.getLabels().forEach((k, v) -> builder.addLabel(k, v));
                hive.execute(new InsertManifestOperation().addManifest(builder.build()));

                newConfig.addInstanceNodeManifest(entry.getKey(), builder.getKey());
            }
        }

        newConfig.setKey(rootKey).insert(hive);
    }

    @Override
    public void delete(String instance) {
        // find all root and node manifests by uuid
        SortedSet<Key> allInstanceObjects = hive.execute(new ManifestListOperation().setManifestName(instance));
        allInstanceObjects.forEach(x -> hive.execute(new ManifestDeleteOperation().setToDelete(x)));

        // cleanup is done periodically in background.

        // TODO: remote delete on master?
    }

    @Override
    public List<InstancePurpose> getPurposes() {
        return Arrays.asList(InstancePurpose.values());
    }

    @Override
    public InstanceNodeConfigurationListDto getNodeConfigurations(String instance, String versionTag) {
        Map<String, InstanceNodeConfigurationDto> node2Dto = new HashMap<>();

        // List all manifest that are stored configured along with the target nodes
        InstanceManifest thisIm = InstanceManifest.load(hive, instance, versionTag);

        // Request a list of all nodes from the master
        RemoteService rsvc = thisIm.getConfiguration().target;
        try (Activity fetchNodes = reporter.start("Fetching nodes from master");
                AutoCloseable proxy = reporter.proxyActivities(rsvc)) {
            MasterRootResource master = ResourceProvider.getResource(rsvc, MasterRootResource.class);
            for (Map.Entry<String, NodeStatus> entry : master.getMinions().entrySet()) {
                String nodeName = entry.getKey();
                NodeStatus nodeStatus = entry.getValue();
                if (node2Dto.containsKey(nodeName)) {
                    continue;
                }
                node2Dto.put(nodeName,
                        new InstanceNodeConfigurationDto(nodeName, nodeStatus,
                                (nodeStatus != null
                                        ? "Up since " + FORMATTER.format(nodeStatus.startup) + ", version: " + nodeStatus.version
                                        : "Node not currently online")));
            }
        } catch (Exception e) {
            log.warn("Master offline: " + thisIm.getConfiguration().target.getUri());
            if (log.isTraceEnabled()) {
                log.trace("Exception", e);
            }

            // make sure there is at least a master... even if the master is not reachable.
            node2Dto.put(Minion.DEFAULT_MASTER_NAME, new InstanceNodeConfigurationDto(Minion.DEFAULT_MASTER_NAME, null,
                    "Error contacting master: " + e.getMessage()));
        }

        // Get all instance manifests in this hive
        for (Manifest.Key instanceManifestKey : InstanceManifest.scan(hive, true)) {
            // always only the latest is given... in case it's our own key we might be interested in another version,
            // so explicitly reconstruct the manifest key with the correct tag in that case.
            String rootName = InstanceManifest.getRootName(instance);
            if (instanceManifestKey.getName().equals(rootName)) {
                instanceManifestKey = new Manifest.Key(instanceManifestKey.getName(), thisIm.getManifest().getTag());
            }

            InstanceManifest im = InstanceManifest.of(hive, instanceManifestKey);
            boolean isForeign = !instanceManifestKey.getName().equals(thisIm.getManifest().getName());
            if (!isForeign && !instanceManifestKey.getTag().equals(thisIm.getManifest().getTag())) {
                // other version of myself, ignore.
            }

            for (Map.Entry<String, Manifest.Key> entry : im.getInstanceNodeManifests().entrySet()) {
                String nodeName = entry.getKey();
                Manifest.Key manifestKey = entry.getValue();

                InstanceNodeManifest manifest = InstanceNodeManifest.of(hive, manifestKey);
                InstanceNodeConfiguration configuration = manifest.getConfiguration();

                InstanceNodeConfigurationDto descriptor = node2Dto.get(nodeName);
                if (descriptor == null) {
                    descriptor = new InstanceNodeConfigurationDto(nodeName, null,
                            "Node '" + nodeName + "' not configured on master, or master offline.");
                    node2Dto.put(nodeName, descriptor);
                }
                if (!isForeign) {
                    descriptor.nodeConfiguration = configuration;
                } else {
                    descriptor.foreignNodeConfigurations.add(configuration);
                }
            }
        }

        InstanceNodeConfigurationListDto instanceDto = new InstanceNodeConfigurationListDto();
        instanceDto.nodeConfigDtos.addAll(node2Dto.values());

        // Load all available applications
        Key productKey = thisIm.getConfiguration().product;
        ProductManifest productManifest = ProductManifest.of(hive, productKey);
        for (Key applicationKey : productManifest.getApplications()) {
            ApplicationManifest manifest = ApplicationManifest.of(hive, applicationKey);
            instanceDto.applications.put(applicationKey.getName(), manifest.getDescriptor());
        }
        return instanceDto;
    }

    private void fixmeExtractDefaultConfig(Path cfgTmpDir, BHive hive, InstanceNodeConfiguration nodeConfig) {
        for (ApplicationConfiguration cfg : nodeConfig.applications) {
            ApplicationManifest amf = ApplicationManifest.of(hive, cfg.application);
            if (amf == null) {
                throw new WebApplicationException("Cannot find application: " + cfg.application, Status.NOT_FOUND);
            }

            amf.exportConfigTemplatesTo(hive, cfgTmpDir);
        }
    }

    /** Clears the token from the remote service */
    private void clearToken(InstanceConfiguration config) {
        RemoteService service = config.target;
        if (service == null) {
            return;
        }
        config.target = new RemoteService(service.getUri(), "");
    }

    @Override
    public void install(String instanceId, String tag) {
        InstanceManifest instance = InstanceManifest.load(hive, instanceId, tag);
        RemoteService svc = instance.getConfiguration().target;
        try (Activity deploy = reporter.start("Deploying " + instance.getConfiguration().name + ":" + tag);
                NoThrowAutoCloseable proxy = reporter.proxyActivities(svc)) {
            // 1. push manifest to remote
            TransferStatistics stats = hive
                    .execute(new PushOperation().setRemote(svc).addManifest(instance.getManifest()).setHiveName(group));

            log.info("Pushed " + instance.getManifest() + " to " + svc.getUri() + "; trees=" + stats.sumMissingTrees + ", objs="
                    + stats.sumMissingObjects + ", size=" + UnitHelper.formatFileSize(stats.transferSize));

            // 2: tell master to deploy
            MasterRootResource master = ResourceProvider.getResource(svc, MasterRootResource.class);
            master.getNamedMaster(group).install(instance.getManifest());
        }
    }

    @Override
    public void uninstall(String instanceId, String tag) {
        InstanceManifest instance = InstanceManifest.load(hive, instanceId, tag);
        RemoteService svc = instance.getConfiguration().target;
        try (Activity deploy = reporter.start("Undeploying " + instance.getConfiguration().name + ":" + tag);
                NoThrowAutoCloseable proxy = reporter.proxyActivities(svc)) {

            // 1: check for running or scheduled applications
            MasterRootResource master = ResourceProvider.getResource(svc, MasterRootResource.class);
            MasterNamedResource namedMaster = master.getNamedMaster(group);
            InstanceStatusDto instanceStatus = namedMaster.getStatus(instanceId);
            Map<String, ProcessStatusDto> appStatus = instanceStatus.getAppStatus();
            Optional<ProcessStatusDto> runningOrScheduledInVersion = appStatus.values().stream()
                    .filter(p -> tag.equals(p.instanceTag)).findFirst();
            if (runningOrScheduledInVersion.isPresent()) {
                throw new WebApplicationException("Cannot uninstall instance version " + instance.getConfiguration().name + ":"
                        + tag + " because it has running or scheduled applications", Status.FORBIDDEN);
            }

            // 1: tell master to undeploy
            master.getNamedMaster(group).remove(instance.getManifest());

            // 2: TODO: cleanup in hives - how, where, who?
        }
    }

    @Override
    public void activate(String instanceId, String tag) {
        InstanceManifest instance = InstanceManifest.load(hive, instanceId, tag);
        RemoteService svc = instance.getConfiguration().target;
        try (Activity deploy = reporter.start("Activating " + instanceId + ":" + tag);
                NoThrowAutoCloseable proxy = reporter.proxyActivities(svc)) {
            MasterRootResource master = ResourceProvider.getResource(svc, MasterRootResource.class);
            master.getNamedMaster(group).activate(instance.getManifest());
        }
    }

    @Override
    public DeploymentStateDto getDeploymentStates(String instanceId) {
        DeploymentStateDto result = new DeploymentStateDto();
        AtomicLong max = new AtomicLong(-1);
        LongAdder current = new LongAdder();
        try (Activity deploy = reporter.start("Fetching deployment state...", () -> max.get(), () -> current.longValue())) {
            // need to fetch information from all master URIs that are historically available.
            String rootName = InstanceManifest.getRootName(instanceId);
            List<InstanceManifest> allMfs = InstanceManifest.scan(hive, false).stream().filter(m -> m.getName().equals(rootName))
                    .map(k -> InstanceManifest.of(hive, k)).collect(Collectors.toList());

            Map<URI, List<InstanceManifest>> remotes = new HashMap<>();
            for (InstanceManifest imf : allMfs) {
                remotes.computeIfAbsent(imf.getConfiguration().target.getUri(), (k) -> new ArrayList<>()).add(imf);
            }

            // switch from indeterminate to determinate progress
            max.set(remotes.size());

            for (Map.Entry<URI, List<InstanceManifest>> entry : remotes.entrySet()) {
                // use target of latest manifest
                Optional<InstanceManifest> optManifest = entry.getValue().stream()
                        .max(Comparator.comparing(mf -> Long.valueOf(mf.getManifest().getTag())));
                if (!optManifest.isPresent()) {
                    throw new WebApplicationException(
                            "Cannot determine max version of " + instanceId + ": cannot find any version", Status.NOT_FOUND);
                }
                RemoteService remote = optManifest.get().getConfiguration().target;

                try (NoThrowAutoCloseable proxy = reporter.proxyActivities(remote)) {
                    MasterRootResource r = ResourceProvider.getResource(remote, MasterRootResource.class);
                    SortedMap<String, SortedSet<Key>> groupDeployments = r.getNamedMaster(group).getAvailableDeployments();
                    SortedMap<String, Key> groupActiveDeployments = r.getNamedMaster(group).getActiveDeployments();

                    Key active = groupActiveDeployments.get(instanceId);
                    if (active != null) {
                        if (result.activatedVersion != null) {
                            log.warn("Multiple active versions found for " + instanceId + " of group " + group);
                        }
                        result.activatedVersion = active.getTag();
                    }

                    SortedSet<Key> deployed = groupDeployments.get(instanceId);
                    if (deployed != null) {
                        deployed.forEach(d -> result.deployedVersions.add(d.getTag()));
                    }
                } catch (Exception e) {
                    // mark all as offline.
                    entry.getValue().forEach(im -> result.offlineMasterVersions.add(im.getManifest().getTag()));
                }
                current.add(1);
                deploy.workAndCancelIfRequested(0); // working is done through 'current'.
            }

        }
        return result;
    }

    @Override
    public ClientDescriptor getNewClientLauncherDescriptor(String instanceId, String processId) {
        InstanceManifest im = InstanceManifest.load(hive, instanceId, null);

        MasterNamedResource master = ResourceProvider.getResource(im.getConfiguration().target, MasterRootResource.class)
                .getNamedMaster(group);

        ClientDescriptor desc = new ClientDescriptor();
        desc.clientId = processId;
        desc.groupId = group;
        desc.instanceId = instanceId;
        desc.host = new RemoteService(im.getConfiguration().target.getUri(),
                master.generateWeakToken(context.getUserPrincipal().getName()));

        return desc;
    }

    @Override
    public ProcessResource getProcessResource(String instanceId) {
        return rc.initResource(new ProcessResourceImpl(hive, group, instanceId));
    }

}
