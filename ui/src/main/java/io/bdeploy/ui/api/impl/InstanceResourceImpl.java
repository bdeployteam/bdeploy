package io.bdeploy.ui.api.impl;

import java.io.ByteArrayOutputStream;
import java.io.File;
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
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeMap;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.Response.Status.Family;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriBuilder;

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
import io.bdeploy.common.util.OsHelper.OperatingSystem;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.common.util.StreamHelper;
import io.bdeploy.common.util.TagComparator;
import io.bdeploy.common.util.TemplateHelper;
import io.bdeploy.common.util.UnitHelper;
import io.bdeploy.common.util.UuidHelper;
import io.bdeploy.interfaces.InstanceImportExportHelper;
import io.bdeploy.interfaces.configuration.dcu.ApplicationConfiguration;
import io.bdeploy.interfaces.configuration.instance.FileStatusDto;
import io.bdeploy.interfaces.configuration.instance.FileStatusDto.FileStatusType;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration.InstancePurpose;
import io.bdeploy.interfaces.configuration.instance.InstanceConfigurationDto;
import io.bdeploy.interfaces.configuration.instance.InstanceNodeConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceNodeConfigurationDto;
import io.bdeploy.interfaces.configuration.instance.InstanceUpdateDto;
import io.bdeploy.interfaces.configuration.pcu.InstanceStatusDto;
import io.bdeploy.interfaces.configuration.pcu.ProcessStatusDto;
import io.bdeploy.interfaces.descriptor.client.ClickAndStartDescriptor;
import io.bdeploy.interfaces.directory.EntryChunk;
import io.bdeploy.interfaces.directory.InstanceDirectory;
import io.bdeploy.interfaces.directory.InstanceDirectoryEntry;
import io.bdeploy.interfaces.manifest.ApplicationManifest;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.manifest.InstanceNodeManifest;
import io.bdeploy.interfaces.manifest.ProductManifest;
import io.bdeploy.interfaces.manifest.history.InstanceManifestHistory;
import io.bdeploy.interfaces.manifest.managed.ControllingMaster;
import io.bdeploy.interfaces.manifest.managed.ManagedMasterDto;
import io.bdeploy.interfaces.manifest.managed.ManagedMasters;
import io.bdeploy.interfaces.manifest.managed.ManagedMastersConfiguration;
import io.bdeploy.interfaces.manifest.managed.MasterProvider;
import io.bdeploy.interfaces.manifest.state.InstanceStateRecord;
import io.bdeploy.interfaces.minion.MinionConfiguration;
import io.bdeploy.interfaces.minion.MinionDto;
import io.bdeploy.interfaces.minion.MinionStatusDto;
import io.bdeploy.interfaces.remote.MasterNamedResource;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.jersey.JerseyClientFactory;
import io.bdeploy.jersey.JerseyWriteLockService.WriteLock;
import io.bdeploy.ui.InstanceEntryStreamRequestService;
import io.bdeploy.ui.InstanceEntryStreamRequestService.EntryRequest;
import io.bdeploy.ui.api.AuthService;
import io.bdeploy.ui.api.ConfigFileResource;
import io.bdeploy.ui.api.InstanceGroupResource;
import io.bdeploy.ui.api.InstanceResource;
import io.bdeploy.ui.api.ManagedServersResource;
import io.bdeploy.ui.api.Minion;
import io.bdeploy.ui.api.MinionMode;
import io.bdeploy.ui.api.ProcessResource;
import io.bdeploy.ui.api.SoftwareUpdateResource;
import io.bdeploy.ui.branding.Branding;
import io.bdeploy.ui.branding.BrandingConfig;
import io.bdeploy.ui.dto.HistoryEntryVersionDto;
import io.bdeploy.ui.dto.HistoryFilterDto;
import io.bdeploy.ui.dto.HistoryResultDto;
import io.bdeploy.ui.dto.InstanceDto;
import io.bdeploy.ui.dto.InstanceManifestHistoryDto;
import io.bdeploy.ui.dto.InstanceNodeConfigurationListDto;
import io.bdeploy.ui.dto.InstanceVersionDto;
import io.bdeploy.ui.dto.ProductDto;
import io.bdeploy.ui.dto.StringEntryChunkDto;

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
    private MasterProvider mp;

    @Context
    private SecurityContext context;

    @Context
    private ResourceContext rc;

    @Inject
    private InstanceEventManager iem;

    @Inject
    private InstanceEntryStreamRequestService iesrs;

    @Inject
    private InstanceHistoryManager instanceHistory;

    public InstanceResourceImpl(String group, BHive hive) {
        this.group = group;
        this.hive = hive;
    }

    @Override
    public List<InstanceDto> list() {
        List<InstanceDto> result = new ArrayList<>();
        auth.addRecentlyUsedInstanceGroup(context.getUserPrincipal().getName(), group);

        SortedSet<Key> imKeys = InstanceManifest.scan(hive, true);
        SortedSet<Key> scan = ProductManifest.scan(hive);

        for (Key imKey : imKeys) {
            InstanceManifest im = InstanceManifest.of(hive, imKey);
            InstanceConfiguration config = im.getConfiguration();

            ProductDto productDto = null;
            try {
                productDto = ProductDto.create(ProductManifest.of(hive, config.product));
            } catch (Exception e) {
                // ignore: product not found
            }

            Key activeProduct = null;
            ProductDto activeProductDto = null;
            try {
                InstanceStateRecord state = getDeploymentStates(config.uuid);

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
                log.error("Cannot contact master for {}.", config.uuid, e);
            }

            boolean newerVersionAvailable = false;

            if (productDto != null && productDto.key != null) {
                String productName = productDto.key.getName();
                String productTag = productDto.key.getTag();

                int asc = -1;

                Optional<String> newestProductVersion = scan.stream().filter(key -> key.getName().equals(productName))
                        .map(key -> key.getTag()).sorted((a, b) -> (new TagComparator()).compare(a, b) * asc).findFirst();

                if (newestProductVersion.isPresent()) {
                    String newestProductTag = newestProductVersion.get();
                    newerVersionAvailable = productTag.compareTo(newestProductTag) * asc >= 0 ? false : true;
                }
            }

            // Clear security token before sending via REST
            result.add(InstanceDto.create(config, productDto, activeProduct, activeProductDto, newerVersionAvailable));
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

        // immediately fetch back so we have it to create the association. don't use #syncInstance here,
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
    public void update(String instance, InstanceConfigurationDto dto, String managedServer, String expectedTag) {
        InstanceManifest oldConfig = InstanceManifest.load(hive, instance, null);

        if (!oldConfig.getManifest().getTag().equals(expectedTag)) {
            throw new WebApplicationException("Expected version is not the current one: expected=" + expectedTag + ", current="
                    + oldConfig.getManifest().getTag(), Status.CONFLICT);
        }

        if (dto.config == null) {
            // no new config - load existing one.
            dto.config = oldConfig.getConfiguration();
        }

        MasterRootResource root = getManagingRootResource(managedServer);
        Manifest.Key key = root.getNamedMaster(group).update(new InstanceUpdateDto(dto, Collections.emptyList()), expectedTag);

        // immediately fetch back so we have it to create the association
        syncInstance(minion, rc, group, instance);

        iem.create(key);
    }

    @Override
    public void delete(String instance) {
        // prevent delete if processes are running.
        InstanceManifest im = readInstance(instance);
        RemoteService master = mp.getControllingMaster(hive, im.getManifest());
        try (Activity deploy = reporter.start("Deleting " + instance + "...");
                NoThrowAutoCloseable proxy = reporter.proxyActivities(master)) {
            MasterRootResource root = ResourceProvider.getVersionedResource(master, MasterRootResource.class, context);
            InstanceStatusDto status = root.getNamedMaster(group).getStatus(instance);
            for (String app : status.getAppStatus().keySet()) {
                if (status.isAppRunningOrScheduled(app)) {
                    throw new WebApplicationException("Application still running, cannot delete: " + app,
                            Status.EXPECTATION_FAILED);
                }
            }

            // cleanup is done periodically in background, still uninstall installed versions to prevent re-start of processes later
            List<InstanceVersionDto> versions = listVersions(instance);
            for (InstanceVersionDto dto : versions) {
                root.getNamedMaster(group).uninstall(dto.key);
            }

            root.getNamedMaster(group).delete(instance);
        }

        syncInstance(minion, rc, group, instance);
    }

    @Override
    public List<InstancePurpose> getPurposes() {
        return Arrays.asList(InstancePurpose.values());
    }

    @Override
    public Map<String, MinionDto> getMinionConfiguration(String instance, String versionTag) {
        if (minion.getMode() != MinionMode.CENTRAL) {
            return minion.getMinions().values();
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
            return root.getMinions();
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

        // Create a configuration entry for each configured minion
        Map<String, MinionDto> minions = getMinionConfiguration(instance, versionTag);
        for (Map.Entry<String, MinionDto> entry : minions.entrySet()) {
            String minionName = entry.getKey();
            result.nodeConfigDtos.add(new InstanceNodeConfigurationDto(minionName));
        }

        // Insert configuration and create nodes where we still have a configuration
        for (Key imKey : InstanceManifest.scan(hive, true)) {
            boolean isForeign = !imKey.getName().equals(thisIm.getManifest().getName());
            if (!isForeign) {
                imKey = thisIm.getManifest(); // go on with requested tag (versionTag)
            }
            String imMaster = new ControllingMaster(hive, imKey).read().getName();
            if (thisMaster != null && !thisMaster.equals(imMaster)) {
                continue;
            }
            InstanceManifest imf = InstanceManifest.of(hive, imKey);
            gatherNodeConfigurations(result, imf, isForeign);
        }

        // Load all available applications
        Key productKey = thisIm.getConfiguration().product;
        try {
            ProductManifest productManifest = ProductManifest.of(hive, productKey);
            for (Key applicationKey : productManifest.getApplications()) {
                ApplicationManifest manifest = ApplicationManifest.of(hive, applicationKey);
                result.applications.put(applicationKey.getName(), manifest.getDescriptor());
            }
        } catch (Exception e) {
            log.warn("Cannot load product of instance version {}: {}", thisIm.getManifest(), productKey, e);
        }
        return result;
    }

    /** Create a new node configuration for each configured instance node configuration */
    private void gatherNodeConfigurations(InstanceNodeConfigurationListDto result, InstanceManifest im, boolean isForeign) {
        // Build a map of configurations indexed by the node name
        Map<String, InstanceNodeConfigurationDto> node2Config = new TreeMap<>();
        result.nodeConfigDtos.forEach(dto -> node2Config.put(dto.nodeName, dto));

        // Update the node configuration. Create entries for nodes that are configured but missing
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

            if (!isForeign) {
                nodeConfig.nodeConfiguration = configuration;
            } else {
                nodeConfig.foreignNodeConfigurations.add(configuration);
            }
        }
    }

    @Override
    public void install(String instanceId, String tag) {
        InstanceManifest instance = InstanceManifest.load(hive, instanceId, tag);
        RemoteService svc = mp.getControllingMaster(hive, instance.getManifest());
        try (Activity deploy = reporter.start("Installing " + instance.getConfiguration().name + ":" + tag);
                NoThrowAutoCloseable proxy = reporter.proxyActivities(svc)) {
            // 1. push config to remote (small'ish).
            hive.execute(new PushOperation().setRemote(svc).addManifest(instance.getManifest()).setHiveName(group));

            // 2. push product to remote in case it is not yet there.
            TransferStatistics stats = hive.execute(
                    new PushOperation().setRemote(svc).addManifest(instance.getConfiguration().product).setHiveName(group));

            log.info("Pushed {} to {}; trees={}, objs={}, size={}", instance.getConfiguration().product, svc.getUri(),
                    stats.sumMissingTrees, stats.sumMissingObjects, UnitHelper.formatFileSize(stats.transferSize));

            // 3: tell master to deploy
            MasterRootResource master = ResourceProvider.getVersionedResource(svc, MasterRootResource.class, context);
            master.getNamedMaster(group).install(instance.getManifest());
        }

        syncInstance(minion, rc, group, instanceId);
        iem.stateChanged(instance.getManifest());
    }

    @Override
    public void uninstall(String instanceId, String tag) {
        InstanceManifest instance = InstanceManifest.load(hive, instanceId, tag);
        RemoteService svc = mp.getControllingMaster(hive, instance.getManifest());
        try (Activity deploy = reporter.start("Uninstalling " + instance.getConfiguration().name + ":" + tag);
                NoThrowAutoCloseable proxy = reporter.proxyActivities(svc)) {

            // 1: check for running or scheduled applications
            MasterRootResource master = ResourceProvider.getVersionedResource(svc, MasterRootResource.class, context);
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
            master.getNamedMaster(group).uninstall(instance.getManifest());
        }

        syncInstance(minion, rc, group, instanceId);
        iem.stateChanged(instance.getManifest());
    }

    @Override
    public void activate(String instanceId, String tag) {
        InstanceManifest instance = InstanceManifest.load(hive, instanceId, tag);
        RemoteService svc = mp.getControllingMaster(hive, instance.getManifest());
        try (Activity deploy = reporter.start("Activating " + instanceId + ":" + tag);
                NoThrowAutoCloseable proxy = reporter.proxyActivities(svc)) {
            MasterRootResource master = ResourceProvider.getVersionedResource(svc, MasterRootResource.class, context);
            master.getNamedMaster(group).activate(instance.getManifest());
        }

        syncInstance(minion, rc, group, instanceId);
        iem.stateChanged(instance.getManifest());
    }

    @Override
    public void updateTo(String instanceId, String productTag) {
        InstanceManifest instance = InstanceManifest.load(hive, instanceId, null);
        RemoteService svc = mp.getControllingMaster(hive, instance.getManifest());

        MasterRootResource master = ResourceProvider.getVersionedResource(svc, MasterRootResource.class, context);
        master.getNamedMaster(group).updateTo(instanceId, productTag);

        syncInstance(minion, rc, group, instanceId);
        iem.stateChanged(instance.getManifest());
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
            // delegate to the actual master, so it will return a descriptor which has the "local" URI.
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
        return rc.initResource(new ConfigFileResourceImpl(hive, group, instanceId));
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
        // We intentionally do not optimize the installer to use the URI from the central server
        // so that installers can be shared and used regardless from where they have been downloaded from
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
                .setRelativePath(SoftwareUpdateResourceImpl.INSTALLER_EXE);
        try (InputStream in = rootHive.execute(findInstallerOp); OutputStream os = Files.newOutputStream(installerPath)) {
            in.transferTo(os);
        } catch (IOException ioe) {
            throw new WebApplicationException("Cannot create windows installer.", ioe);
        }

        // Load product of instance to set the vendor
        ProductManifest pm = ProductManifest.of(hive, im.getConfiguration().product);

        // Brand the executable and embed the required information
        File installer = installerPath.toFile();
        try {
            BrandingConfig config = new BrandingConfig();
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

            Branding branding = new Branding(installer);
            branding.updateConfig(config);
            branding.write(installer);
        } catch (Exception ioe) {
            throw new WebApplicationException("Cannot apply branding to windows installer.", ioe);
        }

        // Now sign the executable with our certificate
        minion.signExecutable(installer, appConfig.name, clickAndStart.host.getUri().toString());
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

            try (MultiPart mp = new MultiPart()) {
                StreamDataBodyPart bp = new StreamDataBodyPart("file", inputStream);
                bp.setFilename("instance.zip");
                bp.setMediaType(MediaType.APPLICATION_OCTET_STREAM_TYPE);
                mp.bodyPart(bp);

                WebTarget target = JerseyClientFactory.get(svc).getBaseTarget()
                        .path("/group/" + group + "/instance/" + instanceId + "/import");
                Response response = target.request().post(Entity.entity(mp, MediaType.MULTIPART_FORM_DATA_TYPE));

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

            MinionConfiguration config = new MinionConfiguration();
            Map<String, MinionDto> nodes = getMinionConfiguration(instanceId, null);
            nodes.forEach(config::addMinion);

            Key newKey = InstanceImportExportHelper.importFrom(zip, hive, instanceId, config);
            iem.create(newKey);
            return Collections.singletonList(newKey);
        } catch (IOException e) {
            throw new WebApplicationException("Cannot import from uploaded ZIP", e);
        } finally {
            PathHelper.deleteRecursive(zip);
        }
    }

    @Override
    public InstanceDirectory getOutputEntry(String instanceId, String tag, String app) {
        InstanceManifest im = readInstance(instanceId, tag);
        if (im == null) {
            throw new WebApplicationException("Cannot load " + instanceId + ":" + tag, Status.NOT_FOUND);
        }

        RemoteService svc = mp.getControllingMaster(hive, im.getManifest());
        MasterRootResource root = ResourceProvider.getVersionedResource(svc, MasterRootResource.class, context);
        return root.getNamedMaster(group).getOutputEntry(instanceId, tag, app);
    }

    @Override
    public StringEntryChunkDto getContentChunk(String instanceId, String minion, InstanceDirectoryEntry entry, long offset,
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
    public String getContentStreamRequest(String instanceId, String minion, InstanceDirectoryEntry entry) {
        return iesrs.createRequest(new EntryRequest(minion, instanceId, entry));
    }

    @Override
    public void deleteDataFile(String instanceId, String minion, InstanceDirectoryEntry entry) {
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
        EntryRequest rq = iesrs.consumeRequestToken(token);

        InstanceManifest im = readInstance(instanceId, rq.entry.tag);
        if (im == null) {
            throw new WebApplicationException("Cannot load " + instanceId + ":" + rq.entry.tag, Status.NOT_FOUND);
        }

        RemoteService svc = mp.getControllingMaster(hive, im.getManifest());
        MasterRootResource root = ResourceProvider.getVersionedResource(svc, MasterRootResource.class, context);
        return root.getNamedMaster(group).getEntryStream(rq.minion, rq.entry);
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
    public HistoryResultDto getInstanceHistory(String instanceId, String startTag, int maxResults, String filter,
            boolean showCreate, boolean showDeployment, boolean showRuntime) {
        HistoryFilterDto filterDto = new HistoryFilterDto();
        filterDto.filterText = filter;
        filterDto.startTag = startTag;
        filterDto.maxResults = maxResults;
        filterDto.showCreateEvents = showCreate;
        filterDto.showDeploymentEvents = showDeployment;
        filterDto.showRuntimeEvents = showRuntime;
        return instanceHistory.getInstanceHistory(hive, group, instanceId, filterDto);
    }

    @Override
    public HistoryEntryVersionDto compareInstanceHistory(String instanceId, int versionA, int versionB) {
        return instanceHistory.compareVersions(hive, instanceId, versionA, versionB);
    }

}
