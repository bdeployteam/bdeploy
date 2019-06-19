package io.bdeploy.ui.api.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
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
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.core.UriBuilder;

import org.glassfish.jersey.media.multipart.ContentDisposition;
import org.glassfish.jersey.media.multipart.ContentDisposition.ContentDispositionBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.op.ManifestDeleteOperation;
import io.bdeploy.bhive.op.ManifestExistsOperation;
import io.bdeploy.bhive.op.ManifestListOperation;
import io.bdeploy.bhive.op.ManifestLoadOperation;
import io.bdeploy.bhive.op.ManifestNextIdOperation;
import io.bdeploy.bhive.op.TreeEntryLoadOperation;
import io.bdeploy.bhive.op.remote.PushOperation;
import io.bdeploy.bhive.op.remote.TransferStatistics;
import io.bdeploy.bhive.remote.jersey.BHiveRegistry;
import io.bdeploy.bhive.remote.jersey.JerseyRemoteBHive;
import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.ActivityReporter.Activity;
import io.bdeploy.common.NoThrowAutoCloseable;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.OsHelper.OperatingSystem;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.common.util.RuntimeAssert;
import io.bdeploy.common.util.StringHelper;
import io.bdeploy.common.util.TemplateHelper;
import io.bdeploy.common.util.UnitHelper;
import io.bdeploy.common.util.UuidHelper;
import io.bdeploy.interfaces.InstanceImportExportHelper;
import io.bdeploy.interfaces.NodeStatus;
import io.bdeploy.interfaces.ScopedManifestKey;
import io.bdeploy.interfaces.configuration.dcu.ApplicationConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration.InstancePurpose;
import io.bdeploy.interfaces.configuration.instance.InstanceNodeConfiguration;
import io.bdeploy.interfaces.configuration.pcu.InstanceStatusDto;
import io.bdeploy.interfaces.configuration.pcu.ProcessStatusDto;
import io.bdeploy.interfaces.descriptor.client.ClickAndStartDescriptor;
import io.bdeploy.interfaces.manifest.ApplicationManifest;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.manifest.InstanceManifest.Builder;
import io.bdeploy.interfaces.manifest.InstanceNodeManifest;
import io.bdeploy.interfaces.manifest.ProductManifest;
import io.bdeploy.interfaces.remote.MasterNamedResource;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.jersey.JerseyWriteLockService.LockingResource;
import io.bdeploy.jersey.JerseyWriteLockService.WriteLock;
import io.bdeploy.ui.api.AuthService;
import io.bdeploy.ui.api.ConfigFileResource;
import io.bdeploy.ui.api.InstanceResource;
import io.bdeploy.ui.api.Minion;
import io.bdeploy.ui.api.ProcessResource;
import io.bdeploy.ui.api.SoftwareUpdateResource;
import io.bdeploy.ui.branding.Branding;
import io.bdeploy.ui.branding.BrandingConfig;
import io.bdeploy.ui.dto.DeploymentStateDto;
import io.bdeploy.ui.dto.InstanceConfigurationDto;
import io.bdeploy.ui.dto.InstanceNodeConfigurationDto;
import io.bdeploy.ui.dto.InstanceNodeConfigurationListDto;
import io.bdeploy.ui.dto.InstanceVersionDto;

@LockingResource(InstanceResourceImpl.GLOBAL_INSTANCE_LOCK)
public class InstanceResourceImpl implements InstanceResource {

    protected static final String GLOBAL_INSTANCE_LOCK = "GlobalInstanceLock";

    private static final Logger log = LoggerFactory.getLogger(InstanceResourceImpl.class);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
            .withLocale(Locale.getDefault()).withZone(ZoneId.systemDefault());
    /**
     * Name and path of the native Windows installer stored in the launcher ZIP
     */
    private static final String INSTALLER_EXE = "bin/Installer.bin";

    /**
     * Name and path of the native Linux (shell script) installer template stored in the launcher ZIP
     */
    private static final String INSTALLER_SH = "bin/installer.tpl";

    private final BHive hive;
    private final String group;

    @Inject
    private BHiveRegistry reg;

    @Inject
    private AuthService auth;

    @Inject
    private ActivityReporter reporter;

    @Inject
    private Minion minion;

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

    @WriteLock
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

        instanceConfig.configTree = product.getConfigTemplateTreeId();
        new InstanceManifest.Builder().setInstanceConfiguration(instanceConfig).insert(hive);
    }

    @Override
    public InstanceConfiguration read(String instance) {
        InstanceManifest manifest = InstanceManifest.load(hive, instance, null);
        InstanceConfiguration configuration = manifest.getConfiguration();
        clearToken(configuration);
        return configuration;
    }

    @WriteLock
    @Override
    public void update(String instance, InstanceConfigurationDto dto, String expectedTag) {
        InstanceManifest oldConfig = InstanceManifest.load(hive, instance, null);

        if (!oldConfig.getManifest().getTag().equals(expectedTag)) {
            throw new WebApplicationException("Expected version is not the current one: expected=" + expectedTag + ", current="
                    + oldConfig.getManifest().getTag(), Status.CONFLICT);
        }

        Builder newConfig = new InstanceManifest.Builder();
        InstanceConfiguration config = dto.config;

        // calculate target key.
        String rootName = InstanceManifest.getRootName(oldConfig.getConfiguration().uuid);
        String rootTag = hive.execute(new ManifestNextIdOperation().setManifestName(rootName)).toString();
        Manifest.Key rootKey = new Manifest.Key(rootName, rootTag);
        InstanceConfiguration cfg;

        if (config != null) {
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

                nodeConfig.copyRedundantFields(cfg);

                RuntimeAssert.assertEquals(nodeDto.nodeConfiguration.uuid, instance, "Instance ID not set on nodes");
                String mfName = instance + "/" + minionName;
                Key instanceNodeKey = new InstanceNodeManifest.Builder().setInstanceNodeConfiguration(nodeConfig)
                        .setMinionName(minionName).setConfigTreeId(cfg.configTree).setKey(new Manifest.Key(mfName, rootTag))
                        .insert(hive);
                newConfig.addInstanceNodeManifest(minionName, instanceNodeKey);
            }
        } else {
            // no new node config - re-apply existing one with new tag, align redundant fields.
            for (Map.Entry<String, Manifest.Key> entry : oldConfig.getInstanceNodeManifests().entrySet()) {
                InstanceNodeManifest oldInmf = InstanceNodeManifest.of(hive, entry.getValue());
                InstanceNodeManifest.Builder inmBuilder = new InstanceNodeManifest.Builder();
                InstanceNodeConfiguration nodeConfig = oldInmf.getConfiguration();

                nodeConfig.copyRedundantFields(cfg);

                inmBuilder.setConfigTreeId(cfg.configTree);
                inmBuilder.setInstanceNodeConfiguration(nodeConfig);
                inmBuilder.setMinionName(entry.getKey());
                inmBuilder.setKey(new Manifest.Key(entry.getValue().getName(), rootTag)); // make sure tag is equal.

                newConfig.addInstanceNodeManifest(entry.getKey(), inmBuilder.insert(hive));
            }
        }

        newConfig.setKey(rootKey).insert(hive);
    }

    @WriteLock
    @Override
    public void delete(String instance) {
        // prevent delete if processes are running.
        InstanceConfiguration cfg = read(instance);
        RemoteService svc = cfg.target;
        try (Activity deploy = reporter.start("Deleting " + instance + "...");
                NoThrowAutoCloseable proxy = reporter.proxyActivities(svc)) {
            MasterRootResource master = ResourceProvider.getResource(svc, MasterRootResource.class);
            InstanceStatusDto status = master.getNamedMaster(group).getStatus(instance);
            for (String app : status.getAppStatus().keySet()) {
                if (status.isAppRunningOrScheduled(app)) {
                    throw new WebApplicationException("Application still running, cannot delete: " + app,
                            Status.EXPECTATION_FAILED);
                }
            }

            // cleanup is done periodically in background, still uninstall installed versions to prevent re-start of processes later
            List<InstanceVersionDto> versions = listVersions(instance);
            for (InstanceVersionDto dto : versions) {
                master.getNamedMaster(group).remove(dto.key);
            }
        }

        // find all root and node manifests by uuid
        SortedSet<Key> allInstanceObjects = hive.execute(new ManifestListOperation().setManifestName(instance));
        allInstanceObjects.forEach(x -> hive.execute(new ManifestDeleteOperation().setToDelete(x)));
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

            // 2: tell master to undeploy
            master.getNamedMaster(group).remove(instance.getManifest());
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
    public ClickAndStartDescriptor getClickAndStartDescriptor(String instanceId, String applicationId) {
        InstanceManifest im = InstanceManifest.load(hive, instanceId, null);

        RemoteService server = im.getConfiguration().target;
        MasterNamedResource master = ResourceProvider.getResource(server, MasterRootResource.class).getNamedMaster(group);

        ClickAndStartDescriptor desc = new ClickAndStartDescriptor();
        desc.applicationId = applicationId;
        desc.groupId = group;
        desc.instanceId = instanceId;
        desc.host = new RemoteService(server.getUri(), master.generateWeakToken(context.getUserPrincipal().getName()));

        return desc;
    }

    @Override
    public ProcessResource getProcessResource(String instanceId) {
        return rc.initResource(new ProcessResourceImpl(hive, group, instanceId));
    }

    @Override
    public ConfigFileResource getConfigResource(String instanceId) {
        return rc.initResource(new ConfigFileResourceImpl(hive, instanceId));
    }

    @Override
    public String createClientInstaller(String instanceId, String applicationId) {
        InstanceManifest im = InstanceManifest.load(hive, instanceId, null);
        ApplicationConfiguration appConfig = im.getApplicationConfiguration(hive, applicationId);

        String tokenName = UuidHelper.randomId();
        Path installerPath = minion.getDownloadDir().resolve(tokenName + ".bin");

        // Determine the target OS, and build the according installer.
        ScopedManifestKey applicationKey = ScopedManifestKey.parse(appConfig.application);

        // Determine latest version of launcher
        SoftwareUpdateResourceImpl swr = rc.initResource(new SoftwareUpdateResourceImpl());
        ScopedManifestKey launcherKey = swr.getNewestLauncher(applicationKey.getOperatingSystem());
        if (launcherKey == null) {
            throw new WebApplicationException(
                    "Cannot find launcher for target OS. Ensure there is one available in the System Software.",
                    Status.NOT_FOUND);
        }

        UriBuilder launcherUri = UriBuilder.fromUri(im.getConfiguration().target.getUri());
        launcherUri.path(SoftwareUpdateResource.ROOT_PATH);
        launcherUri.path(SoftwareUpdateResource.DOWNLOAD_PATH);

        UriBuilder iconUri = UriBuilder.fromUri(im.getConfiguration().target.getUri());
        iconUri.path("/group/{group}/instance/");
        iconUri.path(InstanceResource.PATH_DOWNLOAD_APP_ICON);

        URI launcherLocation = launcherUri.build(new Object[] { launcherKey.getKey().getName(), launcherKey.getTag() }, false);
        URI iconLocation = iconUri.build(group, im.getConfiguration().uuid, appConfig.uid);

        if (applicationKey.getOperatingSystem() == OperatingSystem.WINDOWS) {
            createWindowsInstaller(im, appConfig, installerPath, launcherKey, launcherLocation, iconLocation);
        } else if (applicationKey.getOperatingSystem() == OperatingSystem.LINUX) {
            createLinuxInstaller(im, appConfig, installerPath, launcherKey, launcherLocation, iconLocation);
        } else {
            throw new WebApplicationException("No installer provided for the target OS");
        }

        // Return the name of the token for downloading
        return tokenName;
    }

    private void createLinuxInstaller(InstanceManifest im, ApplicationConfiguration appConfig, Path installerPath,
            ScopedManifestKey launcherKey, URI launcherLocation, URI iconLocation) {
        BHive rootHive = reg.get(JerseyRemoteBHive.DEFAULT_NAME);
        Manifest mf = rootHive.execute(new ManifestLoadOperation().setManifest(launcherKey.getKey()));
        TreeEntryLoadOperation findInstallerOp = new TreeEntryLoadOperation().setRootTree(mf.getRoot())
                .setRelativePath(INSTALLER_SH);
        String template;
        try (InputStream in = rootHive.execute(findInstallerOp); ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            in.transferTo(os);
            template = os.toString(StandardCharsets.UTF_8);
        } catch (IOException ioe) {
            throw new WebApplicationException("Cannot create native Windows launcher.", ioe);
        }

        ClickAndStartDescriptor clickAndStart = getClickAndStartDescriptor(im.getConfiguration().uuid, appConfig.uid);

        // must match the values required in the installer.tpl file
        Map<String, String> values = new TreeMap<>();
        values.put("LAUNCHER_URL", launcherLocation.toString());
        values.put("ICON_URL", iconLocation.toString());
        values.put("APP_UID", appConfig.uid);
        values.put("APP_NAME", im.getConfiguration().name + " - " + appConfig.name);
        values.put("BDEPLOY_FILE", new String(StorageHelper.toRawBytes(clickAndStart), StandardCharsets.UTF_8));

        String content = TemplateHelper.process(template, (k) -> values.get(k), "{{", "}}");
        try {
            Files.write(installerPath, content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new WebApplicationException("Cannot write installer " + installerPath, e);
        }
    }

    private void createWindowsInstaller(InstanceManifest im, ApplicationConfiguration appConfig, Path installerPath,
            ScopedManifestKey launcherKey, URI launcherLocation, URI iconLocation) {
        File installer = installerPath.toFile();
        // Try to load the installer stored in the manifest tree
        BHive rootHive = reg.get(JerseyRemoteBHive.DEFAULT_NAME);
        Manifest mf = rootHive.execute(new ManifestLoadOperation().setManifest(launcherKey.getKey()));
        TreeEntryLoadOperation findInstallerOp = new TreeEntryLoadOperation().setRootTree(mf.getRoot())
                .setRelativePath(INSTALLER_EXE);
        try (InputStream in = rootHive.execute(findInstallerOp); OutputStream os = Files.newOutputStream(installerPath)) {
            in.transferTo(os);
        } catch (IOException ioe) {
            throw new WebApplicationException("Cannot create native Windows launcher.", ioe);
        }

        // Brand the executable and embed the required information
        try {
            ClickAndStartDescriptor clickAndStart = getClickAndStartDescriptor(im.getConfiguration().uuid, appConfig.uid);

            BrandingConfig config = new BrandingConfig();
            config.launcherUrl = launcherLocation.toString();
            config.iconUrl = iconLocation.toString();
            config.applicationUid = appConfig.uid;
            config.applicationName = im.getConfiguration().name + " - " + appConfig.name;
            config.applicationJson = new String(StorageHelper.toRawBytes(clickAndStart), StandardCharsets.UTF_8);

            Branding branding = new Branding(installer);
            branding.updateConfig(config);
            branding.write(installer);
        } catch (Exception ioe) {
            throw new WebApplicationException("Cannot create native Windows launcher.", ioe);
        }
    }

    @Override
    public Response downloadClientInstaller(String instanceId, String applicationId, String token) {
        // File must be downloaded within a given timeout
        Path targetFile = minion.getDownloadDir().resolve(token + ".bin");
        File file = targetFile.toFile();
        if (!file.isFile()) {
            throw new WebApplicationException("Token to download client installer is not valid any more.", Status.BAD_REQUEST);
        }

        long lastModified = file.lastModified();
        long validUntil = lastModified + TimeUnit.MINUTES.toMillis(5);
        if (System.currentTimeMillis() > validUntil) {
            throw new WebApplicationException("Token to download client installer is not valid any more.", Status.BAD_REQUEST);
        }

        // Build a response with the stream
        ResponseBuilder responeBuilder = Response.ok(new StreamingOutput() {

            @Override
            public void write(OutputStream output) throws IOException, WebApplicationException {
                try (InputStream is = Files.newInputStream(targetFile)) {
                    is.transferTo(output);

                    // Intentionally not in finally block to allow resuming of the download
                    PathHelper.deleteRecursive(targetFile);
                } catch (IOException ioe) {
                    if (log.isDebugEnabled()) {
                        log.debug("Could not fully write output", ioe);
                    } else {
                        log.warn("Could not fully write output: " + ioe.toString());
                    }
                }
            }
        }, MediaType.APPLICATION_OCTET_STREAM);

        // Load metadata to give the file a nice name
        InstanceManifest im = InstanceManifest.load(hive, instanceId, null);
        ApplicationConfiguration appConfig = im.getApplicationConfiguration(hive, applicationId);
        ScopedManifestKey appKey = ScopedManifestKey.parse(appConfig.application);

        String fileName;
        if (appKey.getOperatingSystem() == OperatingSystem.WINDOWS) {
            fileName = im.getConfiguration().name + "-" + appConfig.name + "-Installer.exe";
        } else if (appKey.getOperatingSystem() == OperatingSystem.LINUX) {
            fileName = im.getConfiguration().name + "-" + appConfig.name + "-Installer.run";
        } else {
            throw new WebApplicationException("Unsupported OS for installer download: " + appKey.getOperatingSystem());
        }

        // Serve file to the client
        ContentDispositionBuilder<?, ?> builder = ContentDisposition.type("attachement");
        builder.size(file.length()).fileName(fileName);
        responeBuilder.header("Content-Disposition", builder.build());
        responeBuilder.header("Content-Length", file.length());
        return responeBuilder.build();
    }

    @Override
    public Response downloadIcon(String instanceId, String applicationId) {
        InstanceManifest im = InstanceManifest.load(hive, instanceId, null);
        ApplicationConfiguration appConfig = im.getApplicationConfiguration(hive, applicationId);
        ApplicationManifest appMf = ApplicationManifest.of(hive, appConfig.application);
        byte[] brandingIcon = appMf.readBrandingIcon(hive);
        if (brandingIcon == null) {
            return Response.serverError().status(Status.NOT_FOUND).build();
        }
        ResponseBuilder responeBuilder = Response.ok(new StreamingOutput() {

            @Override
            public void write(OutputStream output) throws IOException, WebApplicationException {
                try (InputStream is = new ByteArrayInputStream(brandingIcon)) {
                    is.transferTo(output);
                }
            }
        }, MediaType.APPLICATION_OCTET_STREAM);
        ContentDispositionBuilder<?, ?> builder = ContentDisposition.type("attachement");
        builder.size(brandingIcon.length).fileName(appConfig.name + ".ico");
        responeBuilder.header("Content-Disposition", builder.build());
        responeBuilder.header("Content-Length", brandingIcon.length);
        return responeBuilder.build();
    }

    @Override
    public Response exportInstance(String instanceId, String tag) {
        Path zip;
        try {
            zip = Files.createTempFile(minion.getTempDir(), "exp-", ".zip");
            Files.deleteIfExists(zip);
        } catch (IOException e) {
            throw new WebApplicationException("Cannot create temporary file", e);
        }

        String rootName = InstanceManifest.getRootName(instanceId);
        Manifest.Key key = new Manifest.Key(rootName, tag);

        InstanceImportExportHelper.exportTo(zip, hive, InstanceManifest.of(hive, key));

        ResponseBuilder responeBuilder = Response.ok(new StreamingOutput() {

            @Override
            public void write(OutputStream output) throws IOException, WebApplicationException {
                try (InputStream is = Files.newInputStream(zip)) {
                    is.transferTo(output);
                } finally {
                    Files.deleteIfExists(zip);
                }
            }
        }, MediaType.APPLICATION_OCTET_STREAM);

        ContentDisposition contentDisposition = ContentDisposition.type("attachement").fileName(instanceId + "-" + tag + ".zip")
                .build();
        responeBuilder.header("Content-Disposition", contentDisposition);
        return responeBuilder.build();
    }

    @WriteLock
    @Override
    public List<Key> importInstance(InputStream inputStream, String instanceId) {
        Path zip = minion.getDownloadDir().resolve(UuidHelper.randomId() + ".zip");
        try {
            Files.copy(inputStream, zip);
            return Collections.singletonList(InstanceImportExportHelper.importFrom(zip, hive, instanceId));
        } catch (IOException e) {
            throw new WebApplicationException("Cannot import from uploaded ZIP", e);
        } finally {
            PathHelper.deleteRecursive(zip);
        }
    }

}
