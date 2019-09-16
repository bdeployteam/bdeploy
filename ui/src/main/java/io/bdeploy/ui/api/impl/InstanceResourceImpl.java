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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
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
import io.bdeploy.interfaces.directory.EntryChunk;
import io.bdeploy.interfaces.directory.InstanceDirectory;
import io.bdeploy.interfaces.directory.InstanceDirectoryEntry;
import io.bdeploy.interfaces.manifest.ApplicationManifest;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.manifest.InstanceManifest.Builder;
import io.bdeploy.interfaces.manifest.InstanceNodeManifest;
import io.bdeploy.interfaces.manifest.ProductManifest;
import io.bdeploy.interfaces.manifest.history.InstanceManifestHistory;
import io.bdeploy.interfaces.manifest.history.InstanceManifestHistory.Action;
import io.bdeploy.interfaces.manifest.state.InstanceStateRecord;
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
import io.bdeploy.ui.dto.InstanceConfigurationDto;
import io.bdeploy.ui.dto.InstanceDto;
import io.bdeploy.ui.dto.InstanceManifestHistoryDto;
import io.bdeploy.ui.dto.InstanceNodeConfigurationDto;
import io.bdeploy.ui.dto.InstanceNodeConfigurationListDto;
import io.bdeploy.ui.dto.InstanceVersionDto;
import io.bdeploy.ui.dto.ProductDto;
import io.bdeploy.ui.dto.StringEntryChunkDto;

@LockingResource(InstanceResourceImpl.GLOBAL_INSTANCE_LOCK)
public class InstanceResourceImpl implements InstanceResource {

    private static final String ATTACHMENT_DISPOSITION = "attachment";
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
    public List<InstanceDto> list() {
        List<InstanceDto> result = new ArrayList<>();
        auth.addRecentlyUsedInstanceGroup(context.getUserPrincipal().getName(), group);

        SortedSet<Key> imKeys = InstanceManifest.scan(hive, true);

        for (Key imKey : imKeys) {
            InstanceConfiguration config = InstanceManifest.of(hive, imKey).getConfiguration();

            ProductDto productDto = null;
            try {
                productDto = ProductDto.create(ProductManifest.of(hive, config.product));
            } catch (Exception e) {
                // ignore: product not found
            }

            Key activeProduct = null;
            ProductDto activeProductDto = null;
            try {
                MasterRootResource r = ResourceProvider.getResource(config.target, MasterRootResource.class);
                MasterNamedResource master = r.getNamedMaster(group);
                InstanceStateRecord state = master.getInstanceState(config.uuid);

                if (state.activeTag != null) {
                    try {
                        InstanceManifest mf = InstanceManifest.of(hive, new Manifest.Key(imKey.getName(), state.activeTag));
                        activeProduct = mf.getConfiguration().product;
                        activeProductDto = ProductDto.create(ProductManifest.of(hive, activeProduct));
                    } catch (Exception e) {
                        // ignore: product of active version not found
                    }
                }
            } catch (Exception e) {
                // in case the token is invalid, master not reachable, etc.
                log.error("Cannot contact master {}.", config.target.getUri(), e);
                log.info("Tried with token {}", config.target.getAuthPack());
            }

            // Clear security token before sending via REST
            clearToken(config);
            result.add(InstanceDto.create(config, productDto, activeProduct, activeProductDto));
        }
        return result;
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
                log.error("cannot load instance from key {}", key, e);
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
            SortedSet<Key> scan = InstanceManifest.scan(hive, true);
            List<InstanceConfiguration> list = scan.stream().map(k -> InstanceManifest.of(hive, k).getConfiguration())
                    .collect(Collectors.toList());
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
        Manifest.Key key = new InstanceManifest.Builder().setInstanceConfiguration(instanceConfig).insert(hive);
        InstanceManifest.of(hive, key).getHistory(hive).record(Action.CREATE, context.getUserPrincipal().getName(), null);
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
            cfg = applyExistingTarget(instance, oldConfig, config);
        } else {
            // no new config - load existing one.
            cfg = oldConfig.getConfiguration();
        }

        newConfig.setInstanceConfiguration(cfg);

        if (dto.nodeDtos != null) {
            // Update the node assignment and the configuration
            updateNewNodes(instance, dto, newConfig, rootTag, cfg);
        } else {
            // no new node config - re-apply existing one with new tag, align redundant fields.
            updateExistingNodes(oldConfig, newConfig, rootTag, cfg);
        }

        newConfig.setKey(rootKey).insert(hive);
        InstanceManifest.of(hive, rootKey).getHistory(hive).record(Action.CREATE, context.getUserPrincipal().getName(), null);
        UiResources.getInstanceEventManager().create(instance, rootKey);
    }

    private void updateExistingNodes(InstanceManifest oldConfig, Builder newConfig, String rootTag, InstanceConfiguration cfg) {
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

    private void updateNewNodes(String instance, InstanceConfigurationDto dto, Builder newConfig, String rootTag,
            InstanceConfiguration cfg) {
        for (InstanceNodeConfigurationDto nodeDto : dto.nodeDtos) {
            String minionName = nodeDto.nodeName;
            InstanceNodeConfiguration nodeConfig = nodeDto.nodeConfiguration;

            // when a user removes all applications then we also delete the node configuration
            if (nodeConfig == null || nodeConfig.applications.isEmpty()) {
                continue;
            }

            // make sure redundant data is equal to instance data.
            if (!cfg.name.equals(nodeConfig.name)) {
                log.warn("Instance name of node ({}) not equal to instance name ({}) - aligning.", nodeConfig.name, cfg.name);
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
    }

    private InstanceConfiguration applyExistingTarget(String instance, InstanceManifest oldConfig, InstanceConfiguration config) {
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
        return config;
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
        // List all manifest that are stored configured along with the target nodes
        InstanceManifest thisIm = InstanceManifest.load(hive, instance, versionTag);

        // Request a list of all nodes from the master
        Map<String, InstanceNodeConfigurationDto> node2Dto = getExistingNodes(thisIm);

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

            gatherNodeConfigurations(node2Dto, im, isForeign);
        }

        InstanceNodeConfigurationListDto instanceDto = new InstanceNodeConfigurationListDto();
        instanceDto.nodeConfigDtos.addAll(node2Dto.values());

        // Load all available applications
        Key productKey = thisIm.getConfiguration().product;

        try {
            ProductManifest productManifest = ProductManifest.of(hive, productKey);
            for (Key applicationKey : productManifest.getApplications()) {
                ApplicationManifest manifest = ApplicationManifest.of(hive, applicationKey);
                instanceDto.applications.put(applicationKey.getName(), manifest.getDescriptor());
            }
        } catch (Exception e) {
            log.warn("Cannot load product of instance version {}: {}", thisIm.getManifest(), productKey);
        }
        return instanceDto;
    }

    private void gatherNodeConfigurations(Map<String, InstanceNodeConfigurationDto> node2Dto, InstanceManifest im,
            boolean isForeign) {
        for (Map.Entry<String, Manifest.Key> entry : im.getInstanceNodeManifests().entrySet()) {
            String nodeName = entry.getKey();
            Manifest.Key manifestKey = entry.getValue();

            InstanceNodeManifest manifest = InstanceNodeManifest.of(hive, manifestKey);
            InstanceNodeConfiguration configuration = manifest.getConfiguration();

            InstanceNodeConfigurationDto descriptor = node2Dto.computeIfAbsent(nodeName, k -> new InstanceNodeConfigurationDto(k,
                    null, "Node '" + k + "' not configured on master, or master offline."));
            if (!isForeign) {
                descriptor.nodeConfiguration = configuration;
            } else {
                descriptor.foreignNodeConfigurations.add(configuration);
            }
        }
    }

    private Map<String, InstanceNodeConfigurationDto> getExistingNodes(InstanceManifest thisIm) {
        Map<String, InstanceNodeConfigurationDto> node2Dto = new HashMap<>();
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
            log.warn("Master offline: {}", thisIm.getConfiguration().target.getUri());
            if (log.isTraceEnabled()) {
                log.trace("Exception", e);
            }

            // make sure there is at least a master... even if the master is not reachable.
            node2Dto.put(Minion.DEFAULT_MASTER_NAME, new InstanceNodeConfigurationDto(Minion.DEFAULT_MASTER_NAME, null,
                    "Error contacting master: " + e.getMessage()));
        }

        return node2Dto;
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

            log.info("Pushed {} to {}; trees={}, objs={}, size={}", instance.getManifest(), svc.getUri(), stats.sumMissingTrees,
                    stats.sumMissingObjects, UnitHelper.formatFileSize(stats.transferSize));

            // 2: tell master to deploy
            MasterRootResource master = ResourceProvider.getResource(svc, MasterRootResource.class);
            master.getNamedMaster(group).install(instance.getManifest());
        }
        instance.getHistory(hive).record(Action.INSTALL, context.getUserPrincipal().getName(), null);
        UiResources.getInstanceEventManager().stateChanged(instanceId, instance.getManifest());
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
        instance.getHistory(hive).record(Action.UNINSTALL, context.getUserPrincipal().getName(), null);
        UiResources.getInstanceEventManager().stateChanged(instanceId, instance.getManifest());
    }

    @Override
    public void activate(String instanceId, String tag) {
        InstanceManifest instance = InstanceManifest.load(hive, instanceId, tag);
        RemoteService svc = instance.getConfiguration().target;
        try (Activity deploy = reporter.start("Activating " + instanceId + ":" + tag);
                NoThrowAutoCloseable proxy = reporter.proxyActivities(svc)) {
            MasterRootResource master = ResourceProvider.getResource(svc, MasterRootResource.class);

            String activeTag = master.getNamedMaster(group).getInstanceState(instanceId).activeTag;
            if (activeTag != null) {
                InstanceManifest.load(hive, instanceId, activeTag).getHistory(hive).record(Action.DEACTIVATE,
                        context.getUserPrincipal().getName(), null);
                // this one is /not/ required, as the stateChanged at the end of the call should be enough.
                // UiResources.getInstanceEventManager().stateChanged(instanceId, instance.getManifest());
            }

            master.getNamedMaster(group).activate(instance.getManifest());
        }
        instance.getHistory(hive).record(Action.ACTIVATE, context.getUserPrincipal().getName(), null);
        UiResources.getInstanceEventManager().stateChanged(instanceId, instance.getManifest());
    }

    @Override
    public InstanceManifestHistoryDto getHistory(String instanceId, String tag) {
        InstanceManifest instance = InstanceManifest.load(hive, instanceId, tag);
        InstanceManifestHistory history = instance.getHistory(hive);

        InstanceManifestHistoryDto dto = new InstanceManifestHistoryDto();
        dto.records.addAll(history.getFullHistory());

        return dto;
    }

    @Override
    public InstanceStateRecord getDeploymentStates(String instanceId) {
        InstanceManifest instance = InstanceManifest.load(hive, instanceId, null);
        RemoteService svc = instance.getConfiguration().target;
        MasterRootResource master = ResourceProvider.getResource(svc, MasterRootResource.class);
        return master.getNamedMaster(group).getInstanceState(instanceId);
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
        OperatingSystem applicationOs = applicationKey.getOperatingSystem();

        // Determine latest version of launcher
        SoftwareUpdateResourceImpl swr = rc.initResource(new SoftwareUpdateResourceImpl());
        ScopedManifestKey launcherKey = swr.getNewestLauncher(applicationOs);
        if (launcherKey == null) {
            throw new WebApplicationException(
                    "Cannot find launcher for target OS. Ensure there is one available in the System Software.",
                    Status.NOT_FOUND);
        }

        UriBuilder launcherUri = UriBuilder.fromUri(im.getConfiguration().target.getUri());
        launcherUri.path(SoftwareUpdateResource.ROOT_PATH);
        launcherUri.path(SoftwareUpdateResource.DOWNLOAD_LATEST_PATH);

        UriBuilder iconUri = UriBuilder.fromUri(im.getConfiguration().target.getUri());
        iconUri.path("/group/{group}/instance/");
        iconUri.path(InstanceResource.PATH_DOWNLOAD_APP_ICON);

        UriBuilder splashUrl = UriBuilder.fromUri(im.getConfiguration().target.getUri());
        splashUrl.path("/group/{group}/instance/");
        splashUrl.path(InstanceResource.PATH_DOWNLOAD_APP_SPLASH);

        URI launcherLocation = launcherUri.build(new Object[] { applicationOs.name().toLowerCase() }, false);
        URI iconLocation = iconUri.build(group, im.getConfiguration().uuid, appConfig.uid);
        URI splashLocation = splashUrl.build(group, im.getConfiguration().uuid, appConfig.uid);

        if (applicationOs == OperatingSystem.WINDOWS) {
            createWindowsInstaller(im, appConfig, installerPath, launcherKey, launcherLocation, iconLocation, splashLocation);
        } else if (applicationOs == OperatingSystem.LINUX) {
            createLinuxInstaller(im, appConfig, installerPath, launcherKey, launcherLocation, iconLocation);
        } else {
            throw new WebApplicationException("Installer not supported for OS '" + applicationOs + "'");
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
            throw new WebApplicationException("Cannot create linux installer.", ioe);
        }

        ClickAndStartDescriptor clickAndStart = getClickAndStartDescriptor(im.getConfiguration().uuid, appConfig.uid);

        // must match the values required in the installer.tpl file
        Map<String, String> values = new TreeMap<>();
        values.put("LAUNCHER_URL", launcherLocation.toString());
        values.put("ICON_URL", iconLocation.toString());
        values.put("APP_UID", appConfig.uid);
        values.put("APP_NAME", im.getConfiguration().name + " - " + appConfig.name);
        values.put("BDEPLOY_FILE", new String(StorageHelper.toRawBytes(clickAndStart), StandardCharsets.UTF_8));

        String content = TemplateHelper.process(template, values::get, "{{", "}}");
        try {
            Files.write(installerPath, content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new WebApplicationException("Cannot write installer " + installerPath, e);
        }
    }

    private void createWindowsInstaller(InstanceManifest im, ApplicationConfiguration appConfig, Path installerPath,
            ScopedManifestKey launcherKey, URI launcherLocation, URI iconLocation, URI splashLocation) {
        File installer = installerPath.toFile();
        // Try to load the installer stored in the manifest tree
        BHive rootHive = reg.get(JerseyRemoteBHive.DEFAULT_NAME);
        Manifest mf = rootHive.execute(new ManifestLoadOperation().setManifest(launcherKey.getKey()));
        TreeEntryLoadOperation findInstallerOp = new TreeEntryLoadOperation().setRootTree(mf.getRoot())
                .setRelativePath(INSTALLER_EXE);
        try (InputStream in = rootHive.execute(findInstallerOp); OutputStream os = Files.newOutputStream(installerPath)) {
            in.transferTo(os);
        } catch (IOException ioe) {
            throw new WebApplicationException("Cannot create windows installer.", ioe);
        }

        // Load product of instance to set the vendor
        ProductManifest pm = ProductManifest.of(hive, im.getConfiguration().product);

        // Brand the executable and embed the required information
        try {
            ClickAndStartDescriptor clickAndStart = getClickAndStartDescriptor(im.getConfiguration().uuid, appConfig.uid);

            BrandingConfig config = new BrandingConfig();
            config.launcherUrl = launcherLocation.toString();
            config.iconUrl = iconLocation.toString();
            config.splashUrl = splashLocation.toString();
            config.applicationUid = appConfig.uid;
            config.applicationName = im.getConfiguration().name + " - " + appConfig.name;
            config.applicationJson = new String(StorageHelper.toRawBytes(clickAndStart), StandardCharsets.UTF_8);
            config.productVendor = pm.getProductDescriptor().vendor;

            Branding branding = new Branding(installer);
            branding.updateConfig(config);
            branding.write(installer);
        } catch (Exception ioe) {
            throw new WebApplicationException("Cannot apply branding to windows installer.", ioe);
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
            public void write(OutputStream output) throws IOException {
                try (InputStream is = Files.newInputStream(targetFile)) {
                    is.transferTo(output);

                    // Intentionally not in finally block to allow resuming of the download
                    PathHelper.deleteRecursive(targetFile);
                } catch (IOException ioe) {
                    if (log.isDebugEnabled()) {
                        log.debug("Could not fully write output", ioe);
                    } else {
                        log.warn("Could not fully write output: {}", ioe);
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
            fileName = im.getConfiguration().name + " - " + appConfig.name + " - Installer.exe";
        } else if (appKey.getOperatingSystem() == OperatingSystem.LINUX) {
            fileName = im.getConfiguration().name + "-" + appConfig.name + "-Installer.run";
        } else {
            throw new WebApplicationException("Unsupported OS for installer download: " + appKey.getOperatingSystem());
        }

        // Serve file to the client
        ContentDispositionBuilder<?, ?> builder = ContentDisposition.type(ATTACHMENT_DISPOSITION);
        builder.size(file.length()).fileName(fileName);
        responeBuilder.header(HttpHeaders.CONTENT_DISPOSITION, builder.build());
        responeBuilder.header(HttpHeaders.CONTENT_LENGTH, file.length());
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
        String iconFormat = PathHelper.getExtension(appMf.getDescriptor().branding.icon);
        ResponseBuilder responeBuilder = Response.ok(new StreamingOutput() {

            @Override
            public void write(OutputStream output) throws IOException {
                try (InputStream is = new ByteArrayInputStream(brandingIcon)) {
                    is.transferTo(output);
                }
            }
        }, MediaType.APPLICATION_OCTET_STREAM);
        ContentDispositionBuilder<?, ?> builder = ContentDisposition.type(ATTACHMENT_DISPOSITION);
        builder.size(brandingIcon.length).fileName("icon." + iconFormat);
        responeBuilder.header(HttpHeaders.CONTENT_DISPOSITION, builder.build());
        responeBuilder.header(HttpHeaders.CONTENT_LENGTH, brandingIcon.length);
        return responeBuilder.build();
    }

    @Override
    public Response downloadSplash(String instanceId, String applicationId) {
        InstanceManifest im = InstanceManifest.load(hive, instanceId, null);
        ApplicationConfiguration appConfig = im.getApplicationConfiguration(hive, applicationId);
        ApplicationManifest appMf = ApplicationManifest.of(hive, appConfig.application);
        byte[] brandingSplash = appMf.readBrandingSplashScreen(hive);
        if (brandingSplash == null) {
            return Response.serverError().status(Status.NOT_FOUND).build();
        }
        String splashFormat = PathHelper.getExtension(appMf.getDescriptor().branding.splash.image);
        ResponseBuilder responeBuilder = Response.ok(new StreamingOutput() {

            @Override
            public void write(OutputStream output) throws IOException {
                try (InputStream is = new ByteArrayInputStream(brandingSplash)) {
                    is.transferTo(output);
                }
            }
        }, MediaType.APPLICATION_OCTET_STREAM);
        ContentDispositionBuilder<?, ?> builder = ContentDisposition.type(ATTACHMENT_DISPOSITION);
        builder.size(brandingSplash.length).fileName("splash." + splashFormat);
        responeBuilder.header(HttpHeaders.CONTENT_DISPOSITION, builder.build());
        responeBuilder.header(HttpHeaders.CONTENT_LENGTH, brandingSplash.length);
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
            public void write(OutputStream output) throws IOException {
                try (InputStream is = Files.newInputStream(zip)) {
                    is.transferTo(output);
                } finally {
                    Files.deleteIfExists(zip);
                }
            }
        }, MediaType.APPLICATION_OCTET_STREAM);

        ContentDisposition contentDisposition = ContentDisposition.type(ATTACHMENT_DISPOSITION)
                .fileName(instanceId + "-" + tag + ".zip").build();
        responeBuilder.header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition);
        return responeBuilder.build();
    }

    @WriteLock
    @Override
    public List<Key> importInstance(InputStream inputStream, String instanceId) {
        InstanceConfiguration cfg = read(instanceId);
        if (cfg == null) {
            throw new WebApplicationException("Cannot load " + instanceId, Status.NOT_FOUND);
        }

        MasterRootResource root = ResourceProvider.getResource(cfg.target, MasterRootResource.class);
        Path zip = minion.getDownloadDir().resolve(UuidHelper.randomId() + ".zip");
        try {
            Files.copy(inputStream, zip);
            Key newKey = InstanceImportExportHelper.importFrom(zip, hive, instanceId, root);
            UiResources.getInstanceEventManager().create(instanceId, newKey);
            return Collections.singletonList(newKey);
        } catch (IOException e) {
            throw new WebApplicationException("Cannot import from uploaded ZIP", e);
        } finally {
            PathHelper.deleteRecursive(zip);
        }
    }

    @Override
    public InstanceDirectory getOutputEntry(String instanceId, String tag, String app) {
        InstanceConfiguration cfg = readVersion(instanceId, tag);
        if (cfg == null) {
            throw new WebApplicationException("Cannot load " + instanceId + ":" + tag, Status.NOT_FOUND);
        }

        MasterRootResource root = ResourceProvider.getResource(cfg.target, MasterRootResource.class);
        return root.getNamedMaster(group).getOutputEntry(instanceId, tag, app);
    }

    @Override
    public StringEntryChunkDto getContentChunk(String instanceId, String minion, InstanceDirectoryEntry entry, long offset,
            long limit) {
        InstanceConfiguration cfg = readVersion(instanceId, entry.tag);
        if (cfg == null) {
            throw new WebApplicationException("Cannot load " + instanceId + ":" + entry.tag, Status.NOT_FOUND);
        }

        MasterRootResource root = ResourceProvider.getResource(cfg.target, MasterRootResource.class);
        EntryChunk chunk = root.getNamedMaster(group).getEntryContent(minion, entry, offset, limit);
        if (chunk == null) {
            return null;
        }
        return new StringEntryChunkDto(chunk);
    }

}
