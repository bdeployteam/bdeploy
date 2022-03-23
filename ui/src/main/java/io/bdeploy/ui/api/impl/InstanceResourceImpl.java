package io.bdeploy.ui.api.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;

import org.apache.commons.codec.binary.Base64;
import org.glassfish.jersey.media.multipart.MultiPart;
import org.glassfish.jersey.media.multipart.file.StreamDataBodyPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.api.product.v1.impl.ScopedManifestKey;
import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.model.Tree;
import io.bdeploy.bhive.op.ManifestExistsOperation;
import io.bdeploy.bhive.op.ManifestListOperation;
import io.bdeploy.bhive.op.ManifestLoadOperation;
import io.bdeploy.bhive.op.TreeEntryLoadOperation;
import io.bdeploy.bhive.op.TreeLoadOperation;
import io.bdeploy.bhive.op.remote.PushOperation;
import io.bdeploy.bhive.op.remote.TransferStatistics;
import io.bdeploy.bhive.remote.jersey.BHiveRegistry;
import io.bdeploy.bhive.remote.jersey.JerseyRemoteBHive;
import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.ActivityReporter.Activity;
import io.bdeploy.common.NoThrowAutoCloseable;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.FormatHelper;
import io.bdeploy.common.util.OsHelper.OperatingSystem;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.common.util.StreamHelper;
import io.bdeploy.common.util.TemplateHelper;
import io.bdeploy.common.util.UuidHelper;
import io.bdeploy.interfaces.InstanceImportExportHelper;
import io.bdeploy.interfaces.configuration.dcu.ApplicationConfiguration;
import io.bdeploy.interfaces.configuration.instance.ApplicationValidationDto;
import io.bdeploy.interfaces.configuration.instance.FileStatusDto;
import io.bdeploy.interfaces.configuration.instance.FileStatusDto.FileStatusType;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration.InstancePurpose;
import io.bdeploy.interfaces.configuration.instance.InstanceConfigurationDto;
import io.bdeploy.interfaces.configuration.instance.InstanceNodeConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceNodeConfigurationDto;
import io.bdeploy.interfaces.configuration.instance.InstanceUpdateDto;
import io.bdeploy.interfaces.configuration.pcu.InstanceNodeStatusDto;
import io.bdeploy.interfaces.configuration.pcu.InstanceStatusDto;
import io.bdeploy.interfaces.configuration.pcu.ProcessStatusDto;
import io.bdeploy.interfaces.descriptor.application.ProcessControlDescriptor.ApplicationStartType;
import io.bdeploy.interfaces.descriptor.client.ClickAndStartDescriptor;
import io.bdeploy.interfaces.directory.EntryChunk;
import io.bdeploy.interfaces.directory.RemoteDirectory;
import io.bdeploy.interfaces.directory.RemoteDirectoryEntry;
import io.bdeploy.interfaces.manifest.ApplicationManifest;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.manifest.InstanceNodeManifest;
import io.bdeploy.interfaces.manifest.ProductManifest;
import io.bdeploy.interfaces.manifest.attributes.CustomAttributesRecord;
import io.bdeploy.interfaces.manifest.banner.InstanceBannerRecord;
import io.bdeploy.interfaces.manifest.history.InstanceManifestHistory;
import io.bdeploy.interfaces.manifest.managed.ControllingMaster;
import io.bdeploy.interfaces.manifest.managed.ManagedMasterDto;
import io.bdeploy.interfaces.manifest.managed.ManagedMasters;
import io.bdeploy.interfaces.manifest.managed.ManagedMastersConfiguration;
import io.bdeploy.interfaces.manifest.managed.MasterProvider;
import io.bdeploy.interfaces.manifest.state.InstanceStateRecord;
import io.bdeploy.interfaces.manifest.statistics.ClientUsageData;
import io.bdeploy.interfaces.minion.MinionDto;
import io.bdeploy.interfaces.minion.MinionStatusDto;
import io.bdeploy.interfaces.plugin.VersionSorterService;
import io.bdeploy.interfaces.remote.MasterNamedResource;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.jersey.JerseyClientFactory;
import io.bdeploy.jersey.JerseyOnBehalfOfFilter;
import io.bdeploy.jersey.JerseyWriteLockService.WriteLock;
import io.bdeploy.ui.ProductUpdateService;
import io.bdeploy.ui.RemoteEntryStreamRequestService;
import io.bdeploy.ui.RemoteEntryStreamRequestService.EntryRequest;
import io.bdeploy.ui.api.AuthService;
import io.bdeploy.ui.api.ConfigFileResource;
import io.bdeploy.ui.api.InstanceGroupResource;
import io.bdeploy.ui.api.InstanceResource;
import io.bdeploy.ui.api.ManagedServersResource;
import io.bdeploy.ui.api.Minion;
import io.bdeploy.ui.api.MinionMode;
import io.bdeploy.ui.api.NodeManager;
import io.bdeploy.ui.api.ProcessResource;
import io.bdeploy.ui.api.SoftwareUpdateResource;
import io.bdeploy.ui.dto.ApplicationDto;
import io.bdeploy.ui.dto.HistoryFilterDto;
import io.bdeploy.ui.dto.HistoryResultDto;
import io.bdeploy.ui.dto.InstanceDto;
import io.bdeploy.ui.dto.InstanceManifestHistoryDto;
import io.bdeploy.ui.dto.InstanceNodeConfigurationListDto;
import io.bdeploy.ui.dto.InstanceOverallStatusDto;
import io.bdeploy.ui.dto.InstanceOverallStatusDto.OverallStatus;
import io.bdeploy.ui.dto.InstanceVersionDto;
import io.bdeploy.ui.dto.ObjectChangeDetails;
import io.bdeploy.ui.dto.ObjectChangeHint;
import io.bdeploy.ui.dto.ObjectChangeType;
import io.bdeploy.ui.dto.ProductDto;
import io.bdeploy.ui.dto.StringEntryChunkDto;
import io.bdeploy.ui.utils.WindowsInstaller;
import io.bdeploy.ui.utils.WindowsInstallerConfig;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.Response.Status.Family;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriBuilder;

public class InstanceResourceImpl implements InstanceResource {

    private static final Logger log = LoggerFactory.getLogger(InstanceResourceImpl.class);

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

    @Inject
    private NodeManager nodes;

    @Inject
    private MasterProvider mp;

    @Context
    private SecurityContext context;

    @Context
    private ResourceContext rc;

    @Inject
    private ChangeEventManager changes;

    @Inject
    private RemoteEntryStreamRequestService resrs;

    @Inject
    private VersionSorterService vss;

    @Inject
    private ProductUpdateService pus;

    public InstanceResourceImpl(String group, BHive hive) {
        this.group = group;
        this.hive = hive;
    }

    @Override
    public List<InstanceDto> list() {
        List<InstanceDto> result = new ArrayList<>();

        Map<String, Comparator<String>> comparators = new TreeMap<>();

        SortedSet<Key> imKeys = InstanceManifest.scan(hive, true);
        SortedSet<Key> scan = ProductManifest.scan(hive);

        for (Key imKey : imKeys) {
            InstanceManifest im = InstanceManifest.of(hive, imKey);
            InstanceConfiguration config = im.getConfiguration();

            // comparator only computed once per product (name), regardless of tag.
            Comparator<String> productVersionComparator = comparators.computeIfAbsent(im.getConfiguration().product.getName(),
                    k -> vss.getTagComparator(group, im.getConfiguration().product));

            ProductDto productDto = null;
            try {
                productDto = ProductDto.create(ProductManifest.of(hive, config.product));
            } catch (Exception e) {
                // ignore: product not found
            }

            Key activeVersion = null;
            Key activeProduct = null;
            ProductDto activeProductDto = null;
            try {
                InstanceStateRecord state = getDeploymentStates(config.uuid);

                if (state.activeTag != null) {
                    try {
                        InstanceManifest mf = InstanceManifest.of(hive, new Manifest.Key(imKey.getName(), state.activeTag));
                        activeVersion = mf.getManifest();
                        activeProduct = mf.getConfiguration().product;
                        activeProductDto = ProductDto.create(ProductManifest.of(hive, activeProduct));
                    } catch (Exception e) {
                        // ignore: product of active version not found
                    }
                }
            } catch (Exception e) {
                // in case the token is invalid, master not reachable, etc.
                log.error("Cannot contact master for {}.", config.uuid, e);
            }

            boolean newerVersionAvailable = false;

            if (productDto != null && productDto.key != null) {
                String productName = productDto.key.getName();
                String productTag = productDto.key.getTag();

                // reverse order of comparison to get newest version first.
                Optional<String> newestProductVersion = scan.stream().filter(key -> key.getName().equals(productName))
                        .map(Key::getTag).sorted((a, b) -> productVersionComparator.compare(b, a)).findFirst();

                if (newestProductVersion.isPresent()) {
                    String newestProductTag = newestProductVersion.get();
                    newerVersionAvailable = productVersionComparator.compare(productTag, newestProductTag) < 0;
                }
            }

            ManagedMasterDto managedMaster = null;
            if (minion.getMode() == MinionMode.CENTRAL) {
                ManagedServersResource ms = rc.initResource(new ManagedServersResourceImpl());
                managedMaster = ms.getServerForInstance(group, config.uuid, imKey.getTag());
            }

            CustomAttributesRecord attributes = im.getAttributes(hive).read();
            InstanceBannerRecord banner = im.getBanner(hive).read();

            // Clear security token before sending via REST
            result.add(InstanceDto.create(imKey, config, productDto, activeProduct, activeProductDto, newerVersionAvailable,
                    managedMaster, attributes, banner, im.getManifest(), activeVersion));
        }
        return result;
    }

    @Override
    public List<InstanceOverallStatusDto> syncAllAndGetStatus() {
        List<InstanceOverallStatusDto> listResult = new ArrayList<>();

        // 1) on CENTRAL only, synchronize ALL managed servers. only after that we know all instances.
        if (minion.getMode() == MinionMode.CENTRAL) {
            ManagedServersResource rs = rc.initResource(new ManagedServersResourceImpl());
            List<ManagedMasterDto> servers = rs.getManagedServers(group);

            log.info("Mass-synchronize {} server(s).", servers.size());

            try (Activity sync = reporter.start("Synchronize Servers", servers.size())) {
                for (ManagedMasterDto host : servers) {
                    rs.synchronize(group, host.hostName);
                    sync.workAndCancelIfRequested(1);
                }
            }
        }

        // 2) fetch all instances, and query their process state.
        SortedSet<Key> imKeys = InstanceManifest.scan(hive, true);
        try (Activity all = reporter.start("Fetch Instance Status", imKeys.size())) {
            for (Key imKey : imKeys) {
                InstanceManifest im = InstanceManifest.of(hive, imKey);

                if (im.getState(hive).read().activeTag == null) {
                    continue; // no active tag means there cannot be any status.
                }

                InstanceConfiguration config = im.getConfiguration();
                InstanceOverallStatusDto result = new InstanceOverallStatusDto();
                result.uuid = config.uuid;

                // get all node status of the responsible master.
                RemoteService svc = mp.getControllingMaster(hive, im.getManifest());
                try (Activity syncFetch = reporter.start("Querying " + config.name, 3);
                        NoThrowAutoCloseable proxy = reporter.proxyActivities(svc)) {
                    // sync just in case we're on central, does nothing on standalone/managed
                    syncInstance(minion, rc, group, config.uuid);
                    syncFetch.workAndCancelIfRequested(1);

                    MasterRootResource master = ResourceProvider.getVersionedResource(svc, MasterRootResource.class, context);
                    MasterNamedResource namedMaster = master.getNamedMaster(group);

                    Map<String, MinionStatusDto> nodeStatus = master.getNodes();
                    syncFetch.workAndCancelIfRequested(1);

                    InstanceStatusDto processStatus = namedMaster.getStatus(config.uuid);
                    InstanceNodeConfigurationListDto nodeDtos = new InstanceNodeConfigurationListDto();
                    gatherNodeConfigurations(nodeDtos, im);

                    List<String> stoppedApps = new ArrayList<>();
                    List<String> runningApps = new ArrayList<>();

                    for (var nodeCfg : nodeDtos.nodeConfigDtos) {
                        if (InstanceManifest.CLIENT_NODE_NAME.equals(nodeCfg.nodeName)) {
                            continue; // don't check client.
                        }

                        MinionStatusDto state = nodeStatus.get(nodeCfg.nodeName);

                        if (state == null || state.offline) {
                            result.status = OverallStatus.WARNING;
                            result.messages.add("Node " + nodeCfg.nodeName + " is not available");
                            continue;
                        }

                        InstanceNodeStatusDto statusOnNode = processStatus.node2Applications.get(nodeCfg.nodeName);

                        for (var app : nodeCfg.nodeConfiguration.applications) {
                            if (app.processControl.startType != ApplicationStartType.INSTANCE) {
                                continue;
                            }

                            if (!statusOnNode.isAppDeployed(app.uid)) {
                                log.warn("Expected application is not currently deployed: {}", app.uid);
                                continue;
                            }

                            // instance application, check status
                            ProcessStatusDto status = statusOnNode.getStatus(app.uid);
                            if (status.processState.isStopped()) {
                                stoppedApps.add(app.name);
                            } else {
                                runningApps.add(app.name);
                            }
                        }
                    }

                    if (stoppedApps.isEmpty() && runningApps.isEmpty()) {
                        // this means that ther are no instance type applications on the instance.
                        if (result.status != OverallStatus.WARNING) {
                            result.status = OverallStatus.STOPPED;
                        }
                    } else if (stoppedApps.isEmpty() || runningApps.isEmpty()) {
                        // valid - either all stopped or all running.
                        if (result.status != OverallStatus.WARNING) {
                            result.status = runningApps.isEmpty() ? OverallStatus.STOPPED : OverallStatus.RUNNING;
                        }
                    } else {
                        // not ok, some apps started, some stopped - that will be a warning.
                        result.status = OverallStatus.WARNING;
                        result.messages.add(stoppedApps.size() + " instance type applications are not running.");
                    }

                    syncFetch.workAndCancelIfRequested(1);
                }

                all.workAndCancelIfRequested(1);
                listResult.add(result);
            }
        }
        return listResult;
    }

    @Override
    public List<InstanceVersionDto> listVersions(String instanceId) {
        String rootName = InstanceManifest.getRootName(instanceId);
        Set<Manifest.Key> all = hive.execute(new ManifestListOperation().setManifestName(rootName));
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
        return readInstance(instanceId, versionTag).getConfiguration();
    }

    @Override
    public void create(InstanceConfiguration instanceConfig, String managedServer) {
        ProductManifest product = ProductManifest.of(hive, instanceConfig.product);
        if (product == null) {
            throw new WebApplicationException("Product not found: " + instanceConfig.product, Status.NOT_FOUND);
        }

        MasterRootResource root = getManagingRootResource(managedServer);

        root.getNamedMaster(group)
                .update(new InstanceUpdateDto(new InstanceConfigurationDto(instanceConfig, Collections.emptyList()),
                        getUpdatesFromTree("", new ArrayList<>(), product.getConfigTemplateTreeId())), null);

        // immediately fetch back so we have it to create the association. don't use
        // #syncInstance here,
        // it requires the association to already exist.
        rc.initResource(new ManagedServersResourceImpl()).synchronize(group, managedServer);
    }

    private MasterRootResource getManagingRootResource(String managedServer) {
        RemoteService remote;
        if (minion.getMode() == MinionMode.CENTRAL && managedServer == null) {
            throw new WebApplicationException("Managed server is not set on central", Status.EXPECTATION_FAILED);
        } else if (minion.getMode() == MinionMode.CENTRAL) {
            ManagedMastersConfiguration masters = new ManagedMasters(hive).read();
            ManagedMasterDto ident = masters.getManagedMaster(managedServer);
            if (ident == null) {
                throw new WebApplicationException("Managed server '" + managedServer + "' is not attached to this instance group",
                        Status.EXPECTATION_FAILED);
            }
            remote = new RemoteService(UriBuilder.fromUri(ident.uri).build(), ident.auth);
        } else {
            remote = mp.getControllingMaster(hive, null);
        }

        return ResourceProvider.getVersionedResource(remote, MasterRootResource.class, context);
    }

    static void syncInstance(Minion minion, ResourceContext rc, String groupName, String instanceId) {
        if (minion.getMode() != MinionMode.CENTRAL) {
            return;
        }

        ManagedServersResource rs = rc.initResource(new ManagedServersResourceImpl());
        ManagedMasterDto server = rs.getServerForInstance(groupName, instanceId, null);
        rs.synchronize(groupName, server.hostName);
    }

    private List<FileStatusDto> getUpdatesFromTree(String path, List<FileStatusDto> target, ObjectId cfgTree) {
        if (cfgTree == null) {
            return target;
        }

        Tree tree = hive.execute(new TreeLoadOperation().setTree(cfgTree));
        for (Entry<Tree.Key, ObjectId> entry : tree.getChildren().entrySet()) {
            switch (entry.getKey().getType()) {
                case BLOB:
                    FileStatusDto fsd = new FileStatusDto();

                    fsd.file = path + entry.getKey().getName();
                    fsd.type = FileStatusType.ADD;
                    try (InputStream is = hive.execute(
                            new TreeEntryLoadOperation().setRootTree(cfgTree).setRelativePath(entry.getKey().getName()))) {
                        fsd.content = Base64.encodeBase64String(StreamHelper.read(is));
                    } catch (IOException ioe) {
                        throw new IllegalStateException("Cannot read " + path + entry.getKey().getName() + " from config tree",
                                ioe);
                    }

                    target.add(fsd);
                    break;
                case TREE:
                    getUpdatesFromTree(path + entry.getKey().getName() + "/", target, entry.getValue());
                    break;
                default:
                    throw new IllegalStateException("Unsupported entry type in config tree: " + entry);
            }
        }

        return target;
    }

    @Override
    public InstanceConfiguration read(String instance) {
        return readInstance(instance).getConfiguration();
    }

    private InstanceManifest readInstance(String instance) {
        return readInstance(instance, null);
    }

    private InstanceManifest readInstance(String instanceId, String versionTag) {
        try {
            return InstanceManifest.load(hive, instanceId, versionTag);
        } catch (IllegalStateException e) {
            throw new WebApplicationException("Cannot find instance id=" + instanceId + ", tag=" + versionTag, e,
                    Status.NOT_FOUND);
        }
    }

    @Override
    public void update(String instanceId, InstanceUpdateDto config, String managedServer, String expectedTag) {
        InstanceManifest oldConfig = InstanceManifest.load(hive, instanceId, null);

        if (!oldConfig.getManifest().getTag().equals(expectedTag)) {
            throw new WebApplicationException("Expected version is not the current one: expected=" + expectedTag + ", current="
                    + oldConfig.getManifest().getTag(), Status.CONFLICT);
        }

        MasterRootResource root = getManagingRootResource(managedServer);
        root.getNamedMaster(group).update(config, expectedTag);

        // immediately fetch back so we have it to create the association
        syncInstance(minion, rc, group, instanceId);
    }

    @Override
    public void delete(String instance) {
        // prevent delete if processes are running.
        InstanceManifest im = readInstance(instance);
        List<InstanceVersionDto> versions = listVersions(instance);
        RemoteService master = mp.getControllingMaster(hive, im.getManifest());
        try (Activity deploy = reporter.start("Deleting " + im.getConfiguration().name);
                NoThrowAutoCloseable proxy = reporter.proxyActivities(master)) {
            MasterRootResource root = ResourceProvider.getVersionedResource(master, MasterRootResource.class, context);
            InstanceStatusDto status = root.getNamedMaster(group).getStatus(instance);
            for (String app : status.getAppStatus().keySet()) {
                if (status.isAppRunningOrScheduled(app)) {
                    throw new WebApplicationException("Application still running, cannot delete: " + app,
                            Status.EXPECTATION_FAILED);
                }
            }

            // cleanup is done periodically in background, still uninstall installed
            // versions to prevent re-start of processes later
            for (InstanceVersionDto dto : versions) {
                root.getNamedMaster(group).uninstall(dto.key);
            }

            root.getNamedMaster(group).delete(instance);
        }

        syncInstance(minion, rc, group, instance);

        versions.forEach(v -> changes.remove(ObjectChangeType.INSTANCE, v.key));
    }

    @Override
    public void deleteVersion(String instanceId, String tag) {
        // prevent delete if processes are running.
        InstanceManifest im = readInstance(instanceId);
        Manifest.Key key = new Manifest.Key(InstanceManifest.getRootName(instanceId), tag);
        RemoteService master = mp.getControllingMaster(hive, im.getManifest());
        try (Activity deploy = reporter.start("Deleting Ver. " + tag + " of " + im.getConfiguration().name);
                NoThrowAutoCloseable proxy = reporter.proxyActivities(master)) {
            MasterRootResource root = ResourceProvider.getVersionedResource(master, MasterRootResource.class, context);
            if (getDeploymentStates(instanceId).installedTags.contains(tag)) {
                throw new WebApplicationException("Version " + tag + " is still installed, cannot delete",
                        Status.EXPECTATION_FAILED);
            }

            root.getNamedMaster(group).deleteVersion(instanceId, tag);

            // now delete also on the central...
            if (minion.getMode() == MinionMode.CENTRAL) {
                InstanceManifest.delete(hive, key);
            }
        }

        syncInstance(minion, rc, group, instanceId);
        changes.remove(ObjectChangeType.INSTANCE, key);
    }

    @Override
    public List<InstancePurpose> getPurposes() {
        return Arrays.asList(InstancePurpose.values());
    }

    @Override
    public Map<String, MinionDto> getMinionConfiguration(String instance, String versionTag) {
        if (minion.getMode() != MinionMode.CENTRAL) {
            return nodes.getAllNodes();
        }
        ManagedServersResource ms = rc.initResource(new ManagedServersResourceImpl());
        ManagedMasterDto server = ms.getServerForInstance(group, instance, versionTag);
        return server.minions.values();
    }

    @Override
    public Map<String, MinionStatusDto> getMinionState(String instanceId, String versionTag) {
        if (minion.getMode() != MinionMode.CENTRAL) {
            RemoteService remote = minion.getSelf();
            MasterRootResource root = ResourceProvider.getVersionedResource(remote, MasterRootResource.class, null);
            return root.getNodes();
        }
        ManagedServersResource msr = rc.initResource(new ManagedServersResourceImpl());
        ManagedMasterDto master = msr.getServerForInstance(group, instanceId, versionTag);
        return msr.getMinionStateOfManagedServer(group, master.hostName);
    }

    @Override
    public InstanceNodeConfigurationListDto getNodeConfigurations(String instance, String versionTag) {

        InstanceNodeConfigurationListDto result = new InstanceNodeConfigurationListDto();

        // Collect node information
        InstanceManifest thisIm = InstanceManifest.load(hive, instance, versionTag);
        String thisMaster = new ControllingMaster(hive, thisIm.getManifest()).read().getName();

        // Insert configuration and create nodes where we have a configuration
        for (Key imKey : InstanceManifest.scan(hive, true)) {
            if (!imKey.getName().equals(thisIm.getManifest().getName())) {
                continue;
            }

            imKey = thisIm.getManifest(); // go on with requested tag (versionTag)
            String imMaster = new ControllingMaster(hive, imKey).read().getName();
            if (thisMaster != null && !thisMaster.equals(imMaster)) {
                continue;
            }
            InstanceManifest imf = InstanceManifest.of(hive, imKey);
            gatherNodeConfigurations(result, imf);
        }

        // Load all available applications
        Key productKey = thisIm.getConfiguration().product;
        try {
            ProductManifest productManifest = ProductManifest.of(hive, productKey);
            result.applications
                    .addAll(productManifest.getApplications().stream().map(k -> ApplicationManifest.of(hive, k)).map(mf -> {
                        ApplicationDto descriptor = new ApplicationDto();
                        descriptor.key = mf.getKey();
                        descriptor.name = mf.getDescriptor().name;
                        descriptor.descriptor = mf.getDescriptor();
                        return descriptor;
                    }).toList());
        } catch (Exception e) {
            log.warn("Cannot load product of instance version {}: {}", thisIm.getManifest(), productKey, e);
        }
        return result;
    }

    /**
     * Create a new node configuration for each configured instance node
     * configuration
     */
    private void gatherNodeConfigurations(InstanceNodeConfigurationListDto result, InstanceManifest im) {
        // Build a map of configurations indexed by the node name
        Map<String, InstanceNodeConfigurationDto> node2Config = new TreeMap<>();
        result.nodeConfigDtos.forEach(dto -> node2Config.put(dto.nodeName, dto));

        // Update the node configuration. Create entries for nodes that are configured
        // but missing
        for (Map.Entry<String, Manifest.Key> entry : im.getInstanceNodeManifests().entrySet()) {
            String nodeName = entry.getKey();
            Manifest.Key manifestKey = entry.getValue();

            InstanceNodeManifest manifest = InstanceNodeManifest.of(hive, manifestKey);
            InstanceNodeConfiguration configuration = manifest.getConfiguration();
            InstanceNodeConfigurationDto nodeConfig = node2Config.computeIfAbsent(nodeName, k -> {
                // Node is not known any more but has configured applications
                InstanceNodeConfigurationDto inc = new InstanceNodeConfigurationDto(k);
                result.nodeConfigDtos.add(inc);
                return inc;
            });

            nodeConfig.nodeConfiguration = configuration;
        }
    }

    @Override
    public void install(String instanceId, String tag) {
        InstanceManifest instance = InstanceManifest.load(hive, instanceId, tag);
        RemoteService svc = mp.getControllingMaster(hive, instance.getManifest());
        try (Activity deploy = reporter.start("Installing Ver. " + tag + " of " + instance.getConfiguration().name);
                NoThrowAutoCloseable proxy = reporter.proxyActivities(svc)) {
            // 1. push config to remote (small'ish).
            hive.execute(new PushOperation().setRemote(svc).addManifest(instance.getManifest()).setHiveName(group));

            // 2. push product to remote in case it is not yet there, and we have it.
            if (Boolean.TRUE
                    .equals(hive.execute(new ManifestExistsOperation().setManifest(instance.getConfiguration().product)))) {
                TransferStatistics stats = hive.execute(
                        new PushOperation().setRemote(svc).addManifest(instance.getConfiguration().product).setHiveName(group));

                log.info("Pushed {} to {}; trees={}, objs={}, size={}, duration={}, rate={}", instance.getConfiguration().product,
                        svc.getUri(), stats.sumMissingTrees, stats.sumMissingObjects,
                        FormatHelper.formatFileSize(stats.transferSize), FormatHelper.formatDuration(stats.duration),
                        FormatHelper.formatTransferRate(stats.transferSize, stats.duration));
            }

            // 3: tell master to deploy
            MasterRootResource master = ResourceProvider.getVersionedResource(svc, MasterRootResource.class, context);
            master.getNamedMaster(group).install(instance.getManifest());
        }

        syncInstance(minion, rc, group, instanceId);
        changes.change(ObjectChangeType.INSTANCE, instance.getManifest(),
                Map.of(ObjectChangeDetails.CHANGE_HINT, ObjectChangeHint.STATE));
    }

    @Override
    public void uninstall(String instanceId, String tag) {
        InstanceManifest instance = InstanceManifest.load(hive, instanceId, tag);
        RemoteService svc = mp.getControllingMaster(hive, instance.getManifest());
        try (Activity deploy = reporter.start("Uninstalling Ver. " + tag + " of " + instance.getConfiguration().name);
                NoThrowAutoCloseable proxy = reporter.proxyActivities(svc)) {

            // 1: check for running or scheduled applications
            MasterRootResource master = ResourceProvider.getVersionedResource(svc, MasterRootResource.class, context);
            MasterNamedResource namedMaster = master.getNamedMaster(group);
            InstanceStatusDto instanceStatus = namedMaster.getStatus(instanceId);
            Map<String, ProcessStatusDto> appStatus = instanceStatus.getAppStatus();
            Optional<ProcessStatusDto> runningOrScheduledInVersion = appStatus.values().stream()
                    .filter(p -> tag.equals(p.instanceTag) && p.processState.isRunningOrScheduled()).findFirst();
            if (runningOrScheduledInVersion.isPresent()) {
                throw new WebApplicationException("Cannot uninstall instance version " + instance.getConfiguration().name + ":"
                        + tag + " because it has running or scheduled applications", Status.FORBIDDEN);
            }

            // 2: tell master to undeploy
            master.getNamedMaster(group).uninstall(instance.getManifest());
        }

        syncInstance(minion, rc, group, instanceId);
        changes.change(ObjectChangeType.INSTANCE, instance.getManifest(),
                Map.of(ObjectChangeDetails.CHANGE_HINT, ObjectChangeHint.STATE));
    }

    @Override
    public void activate(String instanceId, String tag) {
        InstanceManifest instance = InstanceManifest.load(hive, instanceId, tag);
        RemoteService svc = mp.getControllingMaster(hive, instance.getManifest());
        try (Activity deploy = reporter.start("Activating Ver. " + tag + " of " + instance.getConfiguration().name);
                NoThrowAutoCloseable proxy = reporter.proxyActivities(svc)) {
            MasterRootResource master = ResourceProvider.getVersionedResource(svc, MasterRootResource.class, context);
            master.getNamedMaster(group).activate(instance.getManifest());
        }

        syncInstance(minion, rc, group, instanceId);
        changes.change(ObjectChangeType.INSTANCE, instance.getManifest(),
                Map.of(ObjectChangeDetails.CHANGE_HINT, ObjectChangeHint.STATE));
    }

    @Override
    public InstanceUpdateDto updateProductVersion(String instanceId, String productTag, InstanceUpdateDto state) {
        ProductManifest current = null;
        try {
            current = ProductManifest.of(hive, state.config.config.product);
        } catch (Exception e) {
            log.info("Missing source product on product update: {}", state.config.config.product);
        }
        List<ApplicationManifest> currentApps = current == null ? null
                : current.getApplications().stream().map(k -> ApplicationManifest.of(hive, k)).toList();

        ProductManifest target = ProductManifest.of(hive, new Manifest.Key(state.config.config.product.getName(), productTag));
        List<ApplicationManifest> targetApps = target.getApplications().stream().map(k -> ApplicationManifest.of(hive, k))
                .toList();

        return pus.update(state, target, current, targetApps, currentApps);
    }

    @Override
    public List<ApplicationValidationDto> validate(String instanceId, InstanceUpdateDto state) {
        ProductManifest pm = ProductManifest.of(hive, state.config.config.product);
        List<ApplicationManifest> am = pm.getApplications().stream().map(k -> ApplicationManifest.of(hive, k)).toList();

        return pus.validate(state, am);
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
        InstanceManifest im = InstanceManifest.load(hive, instanceId, null);
        return im.getState(hive).read();
    }

    @Override
    public ClickAndStartDescriptor getClickAndStartDescriptor(String instanceId, String applicationId) {
        InstanceManifest im = InstanceManifest.load(hive, instanceId, null);
        RemoteService svc = mp.getControllingMaster(hive, im.getManifest());

        if (minion.getMode() == MinionMode.CENTRAL) {
            // delegate to the actual master, so it will return a descriptor which has the
            // "local" URI.
            return ResourceProvider.getResource(svc, InstanceGroupResource.class, context).getInstanceResource(group)
                    .getClickAndStartDescriptor(instanceId, applicationId);
        }

        MasterNamedResource master = ResourceProvider.getVersionedResource(svc, MasterRootResource.class, context)
                .getNamedMaster(group);

        ClickAndStartDescriptor desc = new ClickAndStartDescriptor();
        desc.applicationId = applicationId;
        desc.groupId = group;
        desc.instanceId = instanceId;
        desc.host = new RemoteService(svc.getUri(), master.generateWeakToken(context.getUserPrincipal().getName()));

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
        InstanceStateRecord state = getDeploymentStates(instanceId);
        InstanceManifest im = InstanceManifest.load(hive, instanceId, state.activeTag);
        ApplicationConfiguration appConfig = im.getApplicationConfiguration(hive, applicationId);

        // Request a new file where we can store the installer
        DownloadServiceImpl ds = rc.initResource(new DownloadServiceImpl());
        String token = ds.createNewToken();
        Path installerPath = ds.getStoragePath(token);

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

        // The URI for the installer will use the URI from the target server
        // We intentionally do not optimize the installer to use the URI from the
        // central server
        // so that installers can be shared and used regardless from where they have
        // been downloaded from
        ClickAndStartDescriptor clickAndStart = getClickAndStartDescriptor(im.getConfiguration().uuid, appConfig.uid);
        URI baseUri = clickAndStart.host.getUri();

        UriBuilder launcherUri = UriBuilder.fromUri(baseUri);
        launcherUri.path(SoftwareUpdateResource.ROOT_PATH);
        launcherUri.path(SoftwareUpdateResource.DOWNLOAD_LATEST_PATH);

        UriBuilder iconUri = UriBuilder.fromUri(baseUri);
        iconUri.path("/group/{group}/instance/");
        iconUri.path(InstanceResource.PATH_DOWNLOAD_APP_ICON);

        UriBuilder splashUrl = UriBuilder.fromUri(baseUri);
        splashUrl.path("/group/{group}/instance/");
        splashUrl.path(InstanceResource.PATH_DOWNLOAD_APP_SPLASH);

        URI launcherLocation = launcherUri.build(new Object[] { applicationOs.name().toLowerCase() }, false);
        URI iconLocation = iconUri.build(group, im.getConfiguration().uuid, appConfig.uid);
        URI splashLocation = splashUrl.build(group, im.getConfiguration().uuid, appConfig.uid);

        String fileName = "%1$s (%2$s - %3$s) - Installer";
        if (applicationOs == OperatingSystem.WINDOWS) {
            fileName = fileName + ".exe";
            createWindowsInstaller(im, appConfig, clickAndStart, installerPath, launcherKey, launcherLocation, iconLocation,
                    splashLocation);
        } else if (applicationOs == OperatingSystem.LINUX || applicationOs == OperatingSystem.MACOS) {
            fileName = fileName + ".run";
            createLinuxInstaller(im, appConfig, clickAndStart, installerPath, launcherKey, launcherLocation, iconLocation);
        } else {
            throw new WebApplicationException("Unsupported OS for installer: " + applicationOs);
        }
        fileName = String.format(fileName, appConfig.name, group, im.getConfiguration().name);

        // Return the name of the token for downloading
        ds.registerForDownload(token, fileName);
        return token;
    }

    private void createLinuxInstaller(InstanceManifest im, ApplicationConfiguration appConfig,
            ClickAndStartDescriptor clickAndStart, Path installerPath, ScopedManifestKey launcherKey, URI launcherLocation,
            URI iconLocation) {
        BHive rootHive = reg.get(JerseyRemoteBHive.DEFAULT_NAME);
        Manifest mf = rootHive.execute(new ManifestLoadOperation().setManifest(launcherKey.getKey()));
        TreeEntryLoadOperation findInstallerOp = new TreeEntryLoadOperation().setRootTree(mf.getRoot())
                .setRelativePath(SoftwareUpdateResource.INSTALLER_SH);
        String template;
        try (InputStream in = rootHive.execute(findInstallerOp); ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            in.transferTo(os);
            template = os.toString(StandardCharsets.UTF_8);
        } catch (IOException ioe) {
            throw new WebApplicationException("Cannot create linux installer.", ioe);
        }

        // must match the values required in the installer.tpl file
        Map<String, String> values = new TreeMap<>();
        values.put("LAUNCHER_URL", launcherLocation.toString());
        values.put("ICON_URL", iconLocation.toString());
        values.put("APP_UID", appConfig.uid);
        values.put("APP_NAME", appConfig.name + " (" + group + " - " + im.getConfiguration().name + ")");
        values.put("BDEPLOY_FILE", new String(StorageHelper.toRawBytes(clickAndStart), StandardCharsets.UTF_8));
        values.put("REMOTE_SERVICE_URL", ""); // not used, only in standalone
        values.put("REMOTE_SERVICE_TOKEN", ""); // not used, only in standalone

        String content = TemplateHelper.process(template, values::get);
        try {
            Files.write(installerPath, content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new WebApplicationException("Cannot write installer " + installerPath, e);
        }
    }

    private void createWindowsInstaller(InstanceManifest im, ApplicationConfiguration appConfig,
            ClickAndStartDescriptor clickAndStart, Path installerPath, ScopedManifestKey launcherKey, URI launcherLocation,
            URI iconLocation, URI splashLocation) {
        // Try to load the installer stored in the manifest tree
        BHive rootHive = reg.get(JerseyRemoteBHive.DEFAULT_NAME);
        Manifest mf = rootHive.execute(new ManifestLoadOperation().setManifest(launcherKey.getKey()));
        TreeEntryLoadOperation findInstallerOp = new TreeEntryLoadOperation().setRootTree(mf.getRoot())
                .setRelativePath(SoftwareUpdateResource.INSTALLER_EXE);
        try (InputStream in = rootHive.execute(findInstallerOp); OutputStream os = Files.newOutputStream(installerPath)) {
            in.transferTo(os);
        } catch (IOException ioe) {
            throw new WebApplicationException("Cannot create windows installer.", ioe);
        }

        // Load product of instance to set the vendor
        ProductManifest pm = ProductManifest.of(hive, im.getConfiguration().product);

        // Embed the configuration into the executable
        try {
            WindowsInstallerConfig config = new WindowsInstallerConfig();
            config.remoteService = clickAndStart.host;
            config.launcherUrl = launcherLocation.toString();
            config.iconUrl = iconLocation.toString();
            config.splashUrl = splashLocation.toString();
            config.instanceGroupName = group;
            config.instanceName = im.getConfiguration().name;
            config.applicationUid = appConfig.uid;
            config.applicationName = appConfig.name;
            config.applicationJson = new String(StorageHelper.toRawBytes(clickAndStart), StandardCharsets.UTF_8);
            config.productVendor = pm.getProductDescriptor().vendor;
            WindowsInstaller.embedConfig(installerPath, config);
        } catch (Exception ioe) {
            throw new WebApplicationException("Cannot embed configuration into windows installer.", ioe);
        }
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

        DownloadServiceImpl ds = rc.initResource(new DownloadServiceImpl());
        return ds.serveBytes(brandingIcon, "icon." + iconFormat);
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
        DownloadServiceImpl ds = rc.initResource(new DownloadServiceImpl());
        return ds.serveBytes(brandingSplash, "splash." + splashFormat);
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

        DownloadServiceImpl ds = rc.initResource(new DownloadServiceImpl());
        return ds.serveFile(zip, instanceId + "-" + tag + ".zip");
    }

    @WriteLock
    @Override
    public List<Key> importInstance(InputStream inputStream, String instanceId) {
        InstanceManifest im = readInstance(instanceId);
        if (im == null) {
            throw new WebApplicationException("Cannot load " + instanceId, Status.NOT_FOUND);
        }

        if (minion.getMode() == MinionMode.CENTRAL) {
            // MUST delegate this 1:1 to managed
            RemoteService svc = mp.getControllingMaster(hive, im.getManifest());

            try (MultiPart multiPart = new MultiPart()) {
                StreamDataBodyPart bp = new StreamDataBodyPart("file", inputStream);
                bp.setFilename("instance.zip");
                bp.setMediaType(MediaType.APPLICATION_OCTET_STREAM_TYPE);
                multiPart.bodyPart(bp);

                WebTarget target = JerseyClientFactory.get(svc).getBaseTarget(new JerseyOnBehalfOfFilter(context))
                        .path("/group/" + group + "/instance/" + instanceId + "/import");
                Response response = target.request().post(Entity.entity(multiPart, MediaType.MULTIPART_FORM_DATA_TYPE));

                if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
                    throw new IllegalStateException("Import failed: " + response.getStatusInfo().getStatusCode() + ": "
                            + response.getStatusInfo().getReasonPhrase());
                }
                syncInstance(minion, rc, group, instanceId);

                return response.readEntity(new GenericType<List<Key>>() {
                });
            } catch (IOException e) {
                throw new WebApplicationException("Cannot delegate import to managed server", e);
            }
        }

        Path zip = minion.getDownloadDir().resolve(UuidHelper.randomId() + ".zip");
        try {
            Files.copy(inputStream, zip);

            Map<String, MinionDto> nodeMap = getMinionConfiguration(instanceId, null);
            Key newKey = InstanceImportExportHelper.importFrom(zip, hive, instanceId, nodeMap, context);
            return Collections.singletonList(newKey);
        } catch (IOException e) {
            throw new WebApplicationException("Cannot import from uploaded ZIP", e);
        } finally {
            PathHelper.deleteRecursive(zip);
        }
    }

    @Override
    public RemoteDirectory getOutputEntry(String instanceId, String tag, String app) {
        InstanceManifest im = readInstance(instanceId, tag);
        if (im == null) {
            throw new WebApplicationException("Cannot load " + instanceId + ":" + tag, Status.NOT_FOUND);
        }

        RemoteService svc = mp.getControllingMaster(hive, im.getManifest());
        MasterRootResource root = ResourceProvider.getVersionedResource(svc, MasterRootResource.class, context);
        return root.getNamedMaster(group).getOutputEntry(instanceId, tag, app);
    }

    @Override
    public StringEntryChunkDto getContentChunk(String instanceId, String minion, RemoteDirectoryEntry entry, long offset,
            long limit) {
        InstanceManifest im = readInstance(instanceId, entry.tag);
        if (im == null) {
            throw new WebApplicationException("Cannot load " + instanceId + ":" + entry.tag, Status.NOT_FOUND);
        }

        RemoteService svc = mp.getControllingMaster(hive, im.getManifest());
        MasterRootResource root = ResourceProvider.getVersionedResource(svc, MasterRootResource.class, context);
        EntryChunk chunk = root.getNamedMaster(group).getEntryContent(minion, entry, offset, limit);
        if (chunk == null) {
            return null;
        }
        return new StringEntryChunkDto(chunk);
    }

    @Override
    public String getContentStreamRequest(String instanceId, String minion, RemoteDirectoryEntry entry) {
        return resrs.createRequest(new EntryRequest(minion, instanceId, entry));
    }

    @Override
    public String getContentMultiZipStreamRequest(String instanceId, String minion, List<RemoteDirectoryEntry> entries) {
        return resrs.createRequest(new EntryRequest(minion, instanceId, entries));
    }

    @Override
    public void updateDataFiles(String instanceId, String minion, List<FileStatusDto> updates) {
        InstanceManifest im = readInstance(instanceId);
        if (im == null) {
            throw new WebApplicationException("Cannot load " + instanceId, Status.NOT_FOUND);
        }

        RemoteService svc = mp.getControllingMaster(hive, im.getManifest());
        MasterRootResource root = ResourceProvider.getVersionedResource(svc, MasterRootResource.class, context);
        root.getNamedMaster(group).updateDataEntries(instanceId, minion, updates);
    }

    @Override
    public void deleteDataFile(String instanceId, String minion, RemoteDirectoryEntry entry) {
        InstanceManifest im = readInstance(instanceId, entry.tag);
        if (im == null) {
            throw new WebApplicationException("Cannot load " + instanceId + ":" + entry.tag, Status.NOT_FOUND);
        }

        RemoteService svc = mp.getControllingMaster(hive, im.getManifest());
        MasterRootResource root = ResourceProvider.getVersionedResource(svc, MasterRootResource.class, context);
        root.getNamedMaster(group).deleteDataEntry(minion, entry);
    }

    @Override
    public Response getContentStream(String instanceId, String token) {
        EntryRequest rq = resrs.consumeRequestToken(token);

        InstanceManifest im = readInstance(instanceId, rq.getEntry().tag);
        if (im == null) {
            throw new WebApplicationException("Cannot load " + instanceId + ":" + rq.getEntry().tag, Status.NOT_FOUND);
        }

        RemoteService svc = mp.getControllingMaster(hive, im.getManifest());
        MasterRootResource root = ResourceProvider.getVersionedResource(svc, MasterRootResource.class, context);
        return root.getNamedMaster(group).getEntryStream(rq.minion, rq.getEntry());
    }

    @Override
    public Response getContentMultiZipStream(String instanceId, String token) {
        EntryRequest rq = resrs.consumeRequestToken(token);

        InstanceManifest im = readInstance(instanceId, rq.entries.get(0).tag);
        if (im == null) {
            throw new WebApplicationException("Cannot load " + instanceId + ":" + rq.entries.get(0).tag, Status.NOT_FOUND);
        }

        RemoteService svc = mp.getControllingMaster(hive, im.getManifest());
        MasterRootResource root = ResourceProvider.getVersionedResource(svc, MasterRootResource.class, context);
        return root.getNamedMaster(group).getEntriesZipSteam(rq.minion, rq.entries);
    }

    @Override
    public Map<Integer, Boolean> getPortStates(String instanceId, String minion, List<Integer> ports) {
        InstanceManifest im = readInstance(instanceId);
        if (im == null) {
            throw new WebApplicationException("Cannot load " + instanceId, Status.NOT_FOUND);
        }
        RemoteService svc = mp.getControllingMaster(hive, im.getManifest());
        MasterRootResource root = ResourceProvider.getVersionedResource(svc, MasterRootResource.class, context);
        return root.getNamedMaster(group).getPortStates(minion, ports);
    }

    @Override
    public InstanceBannerRecord getBanner(String instanceId) {
        InstanceManifest im = InstanceManifest.load(hive, instanceId, null);
        return im.getBanner(hive).read();
    }

    @Override
    public void updateBanner(String instanceId, InstanceBannerRecord instanceBannerRecord) {
        InstanceManifest im = readInstance(instanceId);
        if (im == null) {
            throw new WebApplicationException("Cannot load " + instanceId, Status.NOT_FOUND);
        }

        RemoteService svc = mp.getControllingMaster(hive, im.getManifest());
        MasterRootResource root = ResourceProvider.getVersionedResource(svc, MasterRootResource.class, context);
        instanceBannerRecord.user = context.getUserPrincipal().getName();
        instanceBannerRecord.timestamp = System.currentTimeMillis();
        root.getNamedMaster(group).updateBanner(instanceId, instanceBannerRecord);
        syncInstance(minion, rc, group, instanceId);
        changes.change(ObjectChangeType.INSTANCE, im.getManifest(),
                Map.of(ObjectChangeDetails.CHANGE_HINT, ObjectChangeHint.BANNER));
    }

    @Override
    public HistoryResultDto getInstanceHistory(String instanceId, HistoryFilterDto filterDto) {
        InstanceHistoryManager manager = new InstanceHistoryManager(auth, context, mp, hive);
        return manager.getInstanceHistory(group, instanceId, filterDto);
    }

    @Override
    public CustomAttributesRecord getAttributes(String instanceId) {
        InstanceManifest im = readInstance(instanceId);
        if (im == null) {
            throw new WebApplicationException("Cannot load " + instanceId, Status.NOT_FOUND);
        }
        return im.getAttributes(hive).read();
    }

    @Override
    public void updateAttributes(String instanceId, CustomAttributesRecord attributes) {
        InstanceManifest im = readInstance(instanceId);
        if (im == null) {
            throw new WebApplicationException("Cannot load " + instanceId, Status.NOT_FOUND);
        }
        RemoteService svc = mp.getControllingMaster(hive, im.getManifest());
        MasterRootResource root = ResourceProvider.getVersionedResource(svc, MasterRootResource.class, context);
        root.getNamedMaster(group).updateAttributes(instanceId, attributes);
        syncInstance(minion, rc, group, instanceId);

        changes.change(ObjectChangeType.INSTANCE, im.getManifest(),
                Map.of(ObjectChangeDetails.CHANGE_HINT, ObjectChangeHint.ATTRIBUTES));
    }

    @Override
    public ClientUsageData getClientUsageData(String instanceId) {
        InstanceManifest im = readInstance(instanceId);
        if (im == null) {
            throw new WebApplicationException("Cannot load " + instanceId, Status.NOT_FOUND);
        }
        RemoteService svc = mp.getControllingMaster(hive, im.getManifest());
        MasterRootResource root = ResourceProvider.getVersionedResource(svc, MasterRootResource.class, context);
        return root.getNamedMaster(group).getClientUsage(instanceId);
    }

}
