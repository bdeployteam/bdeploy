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
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.codec.binary.Base64;
import org.apache.poi.util.StringUtil;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.api.product.v1.impl.ScopedManifestKey;
import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.model.Tree;
import io.bdeploy.bhive.objects.view.ElementView;
import io.bdeploy.bhive.objects.view.TreeView;
import io.bdeploy.bhive.op.ManifestExistsOperation;
import io.bdeploy.bhive.op.ManifestListOperation;
import io.bdeploy.bhive.op.ManifestLoadOperation;
import io.bdeploy.bhive.op.ScanOperation;
import io.bdeploy.bhive.op.TreeEntryLoadOperation;
import io.bdeploy.bhive.op.TreeLoadOperation;
import io.bdeploy.bhive.op.remote.PushOperation;
import io.bdeploy.bhive.op.remote.TransferStatistics;
import io.bdeploy.bhive.remote.jersey.BHiveRegistry;
import io.bdeploy.bhive.remote.jersey.JerseyRemoteBHive;
import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.common.Version;
import io.bdeploy.common.actions.Actions;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.FormatHelper;
import io.bdeploy.common.util.OsHelper.OperatingSystem;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.common.util.StreamHelper;
import io.bdeploy.common.util.TemplateHelper;
import io.bdeploy.common.util.UuidHelper;
import io.bdeploy.common.util.VersionHelper;
import io.bdeploy.interfaces.InstanceImportExportHelper;
import io.bdeploy.interfaces.configuration.VariableConfiguration;
import io.bdeploy.interfaces.configuration.dcu.ApplicationConfiguration;
import io.bdeploy.interfaces.configuration.instance.ApplicationValidationDto;
import io.bdeploy.interfaces.configuration.instance.FileStatusDto;
import io.bdeploy.interfaces.configuration.instance.FileStatusDto.FileStatusType;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceConfigurationDto;
import io.bdeploy.interfaces.configuration.instance.InstanceGroupConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceNodeConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceNodeConfigurationDto;
import io.bdeploy.interfaces.configuration.instance.InstanceUpdateDto;
import io.bdeploy.interfaces.configuration.pcu.InstanceStatusDto;
import io.bdeploy.interfaces.configuration.pcu.ProcessStatusDto;
import io.bdeploy.interfaces.configuration.system.SystemConfiguration;
import io.bdeploy.interfaces.descriptor.application.ApplicationDescriptor;
import io.bdeploy.interfaces.descriptor.application.HttpEndpoint;
import io.bdeploy.interfaces.descriptor.client.ClickAndStartDescriptor;
import io.bdeploy.interfaces.descriptor.variable.VariableDescriptor;
import io.bdeploy.interfaces.directory.EntryChunk;
import io.bdeploy.interfaces.directory.RemoteDirectory;
import io.bdeploy.interfaces.directory.RemoteDirectoryEntry;
import io.bdeploy.interfaces.endpoints.CommonEndpointHelper;
import io.bdeploy.interfaces.manifest.ApplicationManifest;
import io.bdeploy.interfaces.manifest.InstanceGroupManifest;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.manifest.InstanceNodeManifest;
import io.bdeploy.interfaces.manifest.ProductManifest;
import io.bdeploy.interfaces.manifest.SystemManifest;
import io.bdeploy.interfaces.manifest.attributes.CustomAttributesRecord;
import io.bdeploy.interfaces.manifest.banner.InstanceBannerRecord;
import io.bdeploy.interfaces.manifest.managed.ControllingMaster;
import io.bdeploy.interfaces.manifest.managed.ManagedMasterDto;
import io.bdeploy.interfaces.manifest.managed.ManagedMasters;
import io.bdeploy.interfaces.manifest.managed.ManagedMastersConfiguration;
import io.bdeploy.interfaces.manifest.managed.MasterProvider;
import io.bdeploy.interfaces.manifest.state.InstanceOverallStateRecord;
import io.bdeploy.interfaces.manifest.state.InstanceStateRecord;
import io.bdeploy.interfaces.manifest.statistics.ClientUsageData;
import io.bdeploy.interfaces.minion.MinionDto;
import io.bdeploy.interfaces.minion.MinionStatusDto;
import io.bdeploy.interfaces.plugin.VersionSorterService;
import io.bdeploy.interfaces.remote.MasterNamedResource;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.interfaces.variables.CompositeResolver;
import io.bdeploy.interfaces.variables.DeploymentPathDummyResolver;
import io.bdeploy.interfaces.variables.Resolvers;
import io.bdeploy.jersey.JerseyWriteLockService.WriteLock;
import io.bdeploy.jersey.actions.ActionFactory;
import io.bdeploy.jersey.actions.ActionService.ActionHandle;
import io.bdeploy.ui.FormDataHelper;
import io.bdeploy.ui.ProductUpdateService;
import io.bdeploy.ui.RemoteEntryStreamRequestService;
import io.bdeploy.ui.RemoteEntryStreamRequestService.EntryRequest;
import io.bdeploy.ui.api.AuthService;
import io.bdeploy.ui.api.BackendInfoResource;
import io.bdeploy.ui.api.ConfigFileResource;
import io.bdeploy.ui.api.InstanceBulkResource;
import io.bdeploy.ui.api.InstanceGroupResource;
import io.bdeploy.ui.api.InstanceResource;
import io.bdeploy.ui.api.InstanceTemplateResource;
import io.bdeploy.ui.api.ManagedServersResource;
import io.bdeploy.ui.api.Minion;
import io.bdeploy.ui.api.MinionMode;
import io.bdeploy.ui.api.NodeManager;
import io.bdeploy.ui.api.ProcessResource;
import io.bdeploy.ui.api.SoftwareUpdateResource;
import io.bdeploy.ui.dto.ApplicationDto;
import io.bdeploy.ui.dto.ConfigDirDto;
import io.bdeploy.ui.dto.HistoryFilterDto;
import io.bdeploy.ui.dto.HistoryResultDto;
import io.bdeploy.ui.dto.InstanceDto;
import io.bdeploy.ui.dto.InstanceNodeConfigurationListDto;
import io.bdeploy.ui.dto.InstanceVersionDto;
import io.bdeploy.ui.dto.ObjectChangeDetails;
import io.bdeploy.ui.dto.ObjectChangeHint;
import io.bdeploy.ui.dto.ObjectChangeType;
import io.bdeploy.ui.dto.ProductUpdateDto;
import io.bdeploy.ui.dto.StringEntryChunkDto;
import io.bdeploy.ui.utils.WindowsInstaller;
import io.bdeploy.ui.utils.WindowsInstallerConfig;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
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

    @Inject
    private ActionFactory af;

    public InstanceResourceImpl(String group, BHive hive) {
        this.group = group;
        this.hive = hive;
    }

    @Override
    public List<InstanceDto> list() {
        List<InstanceDto> result = new ArrayList<>();

        SortedSet<Key> imKeys = InstanceManifest.scan(hive, true);

        for (Key imKey : imKeys) {
            var idto = getInstanceDto(imKey);
            if (idto != null) {
                result.add(idto);
            }
        }
        return result;
    }

    @Override
    public ProductUpdateDto getProductUpdates(String instanceId) {
        InstanceGroupConfiguration igc = new InstanceGroupManifest(hive).read();

        // load all products from repos as required by the instance group to repo mapping.
        Map<String, SortedSet<Manifest.Key>> productKeysPerRepo = new TreeMap<>();
        if (igc.productToRepo != null) {
            for (String repo : igc.productToRepo.values()) {
                BHive repoHive = reg.get(repo);
                if (repoHive == null) {
                    continue; // not found (anymore)
                }
                productKeysPerRepo.put(repo, ProductManifest.scan(repoHive));
            }
        }

        var im = readInstance(instanceId);
        // comparator only computed once per product (name), regardless of tag.
        Comparator<String> productVersionComparator = vss.getTagComparator(group, im.getConfiguration().product);

        var result = new ProductUpdateDto();

        SortedSet<Key> productsInGroup = ProductManifest.scan(hive);
        result.newerVersionAvailable = isNewerVersionAvailable(productsInGroup, im.getConfiguration(), productVersionComparator);
        result.newerVersionAvailableInRepository = getNewerVersionAvailableInRepository(igc, im.getConfiguration(),
                productsInGroup, productVersionComparator, productKeysPerRepo);

        return result;
    }

    private static String getNewerVersionAvailableInRepository(InstanceGroupConfiguration igc, InstanceConfiguration config,
            Set<Key> instanceGroupProductKeys, Comparator<String> productVersionComparator,
            Map<String, SortedSet<Key>> productKeysPerRepo) {
        Key productKey = config.product;
        if (igc.productToRepo == null || !igc.productToRepo.containsKey(productKey.getName())) {
            return null;
        }

        String repo = igc.productToRepo.get(productKey.getName());
        SortedSet<Key> keysInRepo = productKeysPerRepo.get(repo);

        if (keysInRepo == null || keysInRepo.isEmpty()) {
            return null;
        }

        List<Key> productKeys = keysInRepo.stream().filter(p -> p.getName().equals(productKey.getName()))
                .filter(key -> !instanceGroupProductKeys.contains(key)) // filter out product keys already imported/uploaded to instance group
                .toList();

        if (productKeys.isEmpty()) {
            return null;
        }

        return isNewerVersionAvailable(productKeys, config, productVersionComparator) ? repo : null;
    }

    private static boolean isNewerVersionAvailable(Collection<Key> keys, InstanceConfiguration config,
            Comparator<String> productVersionComparator) {
        String productName = config.product.getName();
        String productTag = config.product.getTag();
        String productFilterRegex = config.productFilterRegex;
        return keys.stream().filter(key -> key.getName().equals(productName)).map(Key::getTag)
                .filter(tag -> matchesProductFilterRegex(tag, productFilterRegex))
                .anyMatch(tag -> productVersionComparator.compare(tag, productTag) > 0); // filter out older or current versions
    }

    static boolean matchesProductFilterRegex(String tag, String productFilterRegex) {
        if (productFilterRegex == null || productFilterRegex.isEmpty()) {
            return true;
        }
        try {
            Pattern pattern = Pattern.compile(productFilterRegex);
            return pattern.matcher(tag).find();
        } catch (Exception e) {
            return false;
        }
    }

    private static void readTree(ConfigDirDto parent, TreeView tv) {
        for (Map.Entry<String, ElementView> entry : tv.getChildren().entrySet()) {
            if (entry.getValue() instanceof TreeView x) {
                ConfigDirDto child = new ConfigDirDto();
                child.name = entry.getKey();
                readTree(child, x);

                parent.children.add(child);
            }
        }
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

        RemoteService remote = mp.getNamedMasterOrSelf(hive, managedServer);

        String minMinionVersionString = product.getProductDescriptor().minMinionVersion;
        if (StringUtil.isNotBlank(minMinionVersionString)) {
            Version minMinionVersion;
            try {
                minMinionVersion = VersionHelper.parse(minMinionVersionString);
            } catch (RuntimeException e) {
                throw new WebApplicationException(
                        "Failed to parse minimum BDeploy version '" + minMinionVersionString + "' of product " + product.getKey(),
                        e);
            }
            if (ResourceProvider.getVersionedResource(remote, BackendInfoResource.class, context).getVersion().version
                    .compareTo(minMinionVersion) < 0) {
                throw new WebApplicationException(
                        "Creation of instance aborted because minion does not meet the minimum BDeploy version of "
                                + minMinionVersionString);
            }
        }

        applyProductInstanceVariables(instanceConfig, product);

        ResourceProvider.getVersionedResource(remote, MasterRootResource.class, context).getNamedMaster(group)
                .update(new InstanceUpdateDto(new InstanceConfigurationDto(instanceConfig, Collections.emptyList()),
                        getUpdatesFromTree(hive, "", new ArrayList<>(), product.getConfigTemplateTreeId())), null);

        // immediately fetch back so we have it to create the association.
        // don't use #syncInstance here; it requires the association to already exist
        rc.initResource(new ManagedServersResourceImpl()).synchronize(group, managedServer);
    }

    private static void applyProductInstanceVariables(InstanceConfiguration config, ProductManifest product) {
        if (product.getInstanceVariables() != null) {
            for (VariableDescriptor instVar : product.getInstanceVariables()) {
                config.instanceVariables.add(new VariableConfiguration(instVar));
            }
        }
    }

    private static void syncInstance(Minion minion, ResourceContext rc, String groupName, String instanceId) {
        if (minion.getMode() != MinionMode.CENTRAL) {
            return;
        }

        ManagedServersResource rs = rc.initResource(new ManagedServersResourceImpl());
        ManagedMasterDto server = rs.getServerForInstance(groupName, instanceId, null);
        rs.synchronize(groupName, server.hostName);
    }

    public static List<FileStatusDto> getUpdatesFromTree(BHive hive, String path, List<FileStatusDto> target, ObjectId cfgTree) {
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
                    getUpdatesFromTree(hive, path + entry.getKey().getName() + "/", target, entry.getValue());
                    break;
                default:
                    throw new IllegalStateException("Unsupported entry type in config tree: " + entry);
            }
        }

        return target;
    }

    private InstanceDto getInstanceDto(Manifest.Key imKey) {
        InstanceManifest im = InstanceManifest.of(hive, imKey);
        InstanceConfiguration config = im.getConfiguration();

        Key activeVersion = null;
        Key activeProduct = null;
        try {
            InstanceStateRecord state = im.getState(hive).read();

            if (state.activeTag != null) {
                try {
                    InstanceManifest mf = InstanceManifest.of(hive, new Manifest.Key(imKey.getName(), state.activeTag));
                    activeVersion = mf.getKey();
                    activeProduct = mf.getConfiguration().product;
                } catch (Exception e) {
                    // ignore: product of active version not found
                }
            }
        } catch (Exception e) {
            // in case the token is invalid, master not reachable, etc.
            log.error("Cannot contact master for {}.", config.id, e);
        }

        ManagedMasterDto managedMaster = null;
        if (minion.getMode() == MinionMode.CENTRAL) {
            try {
                // nearly the same as ManagedServersResource, but we can speed up a few things as we read a lot already.
                String selected = new ControllingMaster(hive, im.getKey()).read().getName();
                if (selected == null) {
                    return null;
                }

                ManagedMastersConfiguration masters = new ManagedMasters(hive).read();
                managedMaster = masters.getManagedMaster(selected);
                managedMaster.auth = null; // make sure to clear auth.
                managedMaster.minions.values().forEach((k, v) -> v.remote = new RemoteService(v.remote.getUri()));
            } catch (WebApplicationException e) {
                log.warn("Cannot load managed server for group {}, instance {}", group, config.id);
                if (log.isDebugEnabled()) {
                    log.debug("Exception", e);
                }
            }
        }

        CustomAttributesRecord attributes = im.getAttributes(hive).read();
        InstanceBannerRecord banner = im.getBanner(hive).read();
        InstanceOverallStateRecord overallState = im.getOverallState(hive).read();

        // load config directories
        ConfigDirDto configRoot = null;
        if (config.configTree != null) {
            TreeView rootTv = hive.execute(new ScanOperation().setTree(config.configTree));
            configRoot = new ConfigDirDto();
            configRoot.name = "/";
            readTree(configRoot, rootTv);
        }

        return InstanceDto.create(imKey, config, activeProduct, managedMaster, attributes, banner, im.getKey(), activeVersion,
                overallState, configRoot);
    }

    @Override
    public InstanceDto read(String instanceId) {
        InstanceManifest im = readInstance(instanceId);
        return getInstanceDto(im.getKey());
    }

    private InstanceManifest readInstance(String instanceId) {
        return readInstance(instanceId, null);
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

        if (!oldConfig.getKey().getTag().equals(expectedTag)) {
            throw new WebApplicationException("Expected version is not the current one: expected=" + expectedTag + ", current="
                    + oldConfig.getKey().getTag(), Status.BAD_REQUEST);
        }

        ResourceProvider.getVersionedResource(mp.getNamedMasterOrSelf(hive, managedServer), MasterRootResource.class, context)
                .getNamedMaster(group).update(config, expectedTag);

        // immediately fetch back so we have it to create the association
        syncInstance(minion, rc, group, instanceId);
    }

    @Override
    public void delete(String instanceId) {
        ResourceProvider.getVersionedResource(mp.getControllingMaster(hive, readInstance(instanceId).getKey()),
                MasterRootResource.class, context).getNamedMaster(group).delete(instanceId);

        syncInstance(minion, rc, group, instanceId);
    }

    @Override
    public void deleteVersion(String instanceId, String tag) {
        // prevent delete if processes are installed.
        if (getDeploymentStates(instanceId).installedTags.contains(tag)) {
            throw new WebApplicationException("Version " + tag + " is still installed, cannot delete", Status.EXPECTATION_FAILED);
        }

        ResourceProvider.getVersionedResource(mp.getControllingMaster(hive, readInstance(instanceId).getKey()),
                MasterRootResource.class, context).getNamedMaster(group).deleteVersion(instanceId, tag);

        syncInstance(minion, rc, group, instanceId);
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
        String thisMaster = new ControllingMaster(hive, thisIm.getKey()).read().getName();

        // Insert configuration and create nodes where we have a configuration
        for (Key imKey : InstanceManifest.scan(hive, true)) {
            if (!imKey.getName().equals(thisIm.getKey().getName())) {
                continue;
            }

            imKey = thisIm.getKey(); // go on with requested tag (versionTag)
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
            result.applications.addAll(productManifest.getApplications().stream()
                    .map(k -> ApplicationManifest.of(hive, k, productManifest)).map(mf -> {
                        ApplicationDto descriptor = new ApplicationDto();
                        descriptor.key = mf.getKey();
                        descriptor.name = mf.getDescriptor().name;
                        descriptor.descriptor = mf.getDescriptor();
                        return descriptor;
                    }).toList());
        } catch (Exception e) {
            log.warn("Cannot load product of instance version {}: {}", thisIm.getKey(), productKey);
            if (log.isDebugEnabled()) {
                log.debug("Exception", e);
            }
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

        // Update the node configuration. Create entries for nodes that are configured but missing
        for (var entry : im.getInstanceNodeConfigurations(hive).entrySet()) {
            String nodeName = entry.getKey();
            InstanceNodeConfigurationDto nodeConfig = node2Config.computeIfAbsent(nodeName, k -> {
                // Node is not known any more but has configured applications
                InstanceNodeConfigurationDto inc = new InstanceNodeConfigurationDto(k, null);
                result.nodeConfigDtos.add(inc);
                return inc;
            });
            nodeConfig.nodeConfiguration = entry.getValue();
        }
    }

    @Override
    public void install(String instanceId, String tag) {
        InstanceManifest instance = InstanceManifest.load(hive, instanceId, tag);
        RemoteService svc = mp.getControllingMaster(hive, instance.getKey());

        // If we have the product -> push it to the remote master to ensure that it's there
        // If we don't have the product, we assume that the remote master already has it -> else the install below will just fail
        if (Boolean.TRUE.equals(hive.execute(new ManifestExistsOperation().setManifest(instance.getConfiguration().product)))) {
            try (ActionHandle handle = af.run(Actions.PUSH_PRODUCT, group, instanceId,
                    instance.getConfiguration().product.toString())) {
                TransferStatistics stats = hive.execute(
                        new PushOperation().setRemote(svc).addManifest(instance.getConfiguration().product).setHiveName(group));

                if (log.isInfoEnabled()) {
                    log.info("Pushed {} to {}; trees={}, objs={}, size={}, duration={}, rate={}",
                            instance.getConfiguration().product, svc.getUri(), stats.sumMissingTrees, stats.sumMissingObjects,
                            FormatHelper.formatFileSize(stats.transferSize), FormatHelper.formatDuration(stats.duration),
                            FormatHelper.formatTransferRate(stats.transferSize, stats.duration));
                }
            }
        }

        // Tell master to deploy
        MasterRootResource master = ResourceProvider.getVersionedResource(svc, MasterRootResource.class, context);
        master.getNamedMaster(group).install(instance.getKey());

        syncInstance(minion, rc, group, instanceId);

        if (minion.getMode() == MinionMode.CENTRAL) {
            // done on the master directly as well.
            // TODO: replace with a bridge like ActionBridge
            // hint: no resource in "ui" package should ever fire these - should be all master.
            changes.change(ObjectChangeType.INSTANCE, instance.getKey(),
                    Map.of(ObjectChangeDetails.CHANGE_HINT, ObjectChangeHint.STATE));
        }
    }

    @Override
    public void uninstall(String instanceId, String tag) {
        // 1: check that version is not active
        String activeTag = getDeploymentStates(instanceId).activeTag;
        if (tag.equals(activeTag)) {
            throw new WebApplicationException("Cannot uninstall active version", Status.EXPECTATION_FAILED);
        }

        // 2: check for running or scheduled applications
        InstanceManifest instance = InstanceManifest.load(hive, instanceId, tag);
        RemoteService svc = mp.getControllingMaster(hive, instance.getKey());
        MasterRootResource master = ResourceProvider.getVersionedResource(svc, MasterRootResource.class, context);
        MasterNamedResource namedMaster = master.getNamedMaster(group);
        InstanceStatusDto instanceStatus = namedMaster.getStatus(instanceId);
        Map<String, ProcessStatusDto> appStatus = instanceStatus.getAppStatus();
        Optional<ProcessStatusDto> runningOrScheduledInVersion = appStatus.values().stream()
                .filter(p -> tag.equals(p.instanceTag) && p.processState.isRunningOrScheduled()).findFirst();
        if (runningOrScheduledInVersion.isPresent()) {
            throw new WebApplicationException("Cannot uninstall instance version " + instance.getConfiguration().name + ":" + tag
                    + " because it has running or scheduled applications", Status.EXPECTATION_FAILED);
        }

        // 3: tell master to undeploy
        master.getNamedMaster(group).uninstall(instance.getKey());

        syncInstance(minion, rc, group, instanceId);

        if (minion.getMode() == MinionMode.CENTRAL) {
            changes.change(ObjectChangeType.INSTANCE, instance.getKey(),
                    Map.of(ObjectChangeDetails.CHANGE_HINT, ObjectChangeHint.STATE));
        }
    }

    @Override
    public void activate(String instanceId, String tag) {
        InstanceManifest instance = InstanceManifest.load(hive, instanceId, tag);
        RemoteService svc = mp.getControllingMaster(hive, instance.getKey());

        MasterRootResource master = ResourceProvider.getVersionedResource(svc, MasterRootResource.class, context);
        master.getNamedMaster(group).activate(instance.getKey());

        syncInstance(minion, rc, group, instanceId);

        // TODO: see install - should be a bridge
        if (minion.getMode() == MinionMode.CENTRAL) {
            changes.change(ObjectChangeType.INSTANCE, instance.getKey(),
                    Map.of(ObjectChangeDetails.CHANGE_HINT, ObjectChangeHint.STATE));
        }
    }

    @Override
    public InstanceUpdateDto updateProductVersion(String instanceId, String productTag, InstanceUpdateDto state) {
        try (ActionHandle h = af.run(Actions.UPDATE_PRODUCT_VERSION, group, instanceId)) {
            ProductManifest current = null;
            try {
                current = ProductManifest.of(hive, state.config.config.product);
            } catch (Exception e) {
                log.info("Missing source product on product update: {}", state.config.config.product);
                if (log.isDebugEnabled()) {
                    log.debug("Exception", e);
                }
            }

            ProductManifest target = ProductManifest.of(hive,
                    new Manifest.Key(state.config.config.product.getName(), productTag));

            String minMinionVersionString = target.getProductDescriptor().minMinionVersion;
            if (StringUtil.isNotBlank(minMinionVersionString)) {
                Version minMinionVersion;
                try {
                    minMinionVersion = VersionHelper.parse(minMinionVersionString);
                } catch (RuntimeException e) {
                    throw new WebApplicationException("Failed to parse minimum BDeploy version '" + minMinionVersionString
                            + "' of product " + target.getKey(), e);
                }

                if (minion.getSelfConfig().version.compareTo(minMinionVersion) < 0) {
                    throw new WebApplicationException(
                            "Update of instance aborted because target minion does not meet the minimum BDeploy version of "
                                    + minMinionVersionString);
                }
            }

            List<ApplicationManifest> targetApps = target.getApplications().stream()
                    .map(k -> ApplicationManifest.of(hive, k, target)).toList();

            ProductManifest pm = current;
            List<ApplicationManifest> currentApps = current == null ? null
                    : current.getApplications().stream().map(k -> ApplicationManifest.of(hive, k, pm)).toList();

            return pus.update(state, target, current, targetApps, currentApps);
        }
    }

    @Override
    public List<ApplicationValidationDto> validate(String instanceId, InstanceUpdateDto state) {
        ProductManifest pm = ProductManifest.of(hive, state.config.config.product);
        if (pm == null) {
            throw new WebApplicationException("Cannot load product " + state.config.config.product, Status.EXPECTATION_FAILED);
        }

        List<ApplicationManifest> am = pm.getApplications().stream().map(k -> ApplicationManifest.of(hive, k, pm)).toList();

        SystemConfiguration system = null;
        if (state.config.config.system != null) {
            system = SystemManifest.of(hive, state.config.config.system).getConfiguration();
        }

        List<FileStatusDto> existing = InstanceResourceImpl.getUpdatesFromTree(hive, "", new ArrayList<>(),
                state.config.config.configTree);

        return pus.validate(state, am, system, existing);
    }

    @Override
    public InstanceStateRecord getDeploymentStates(String instanceId) {
        InstanceManifest im = InstanceManifest.load(hive, instanceId, null);
        return im.getState(hive).read();
    }

    @Override
    public ClickAndStartDescriptor getClickAndStartDescriptor(String instanceId, String applicationId) {
        InstanceManifest im = InstanceManifest.load(hive, instanceId, null);
        RemoteService svc = mp.getControllingMaster(hive, im.getKey());

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
    public InstanceTemplateResource getTemplateResource() {
        return rc.initResource(new InstanceTemplateResourceImpl(group, hive));
    }

    @Override
    public String createClientInstaller(String instanceId, String applicationId) {
        try (ActionHandle h = af.run(Actions.DOWNLOAD_CLIENT_INSTALLER, group, instanceId, applicationId)) {
            InstanceStateRecord state = getDeploymentStates(instanceId);
            InstanceManifest im = InstanceManifest.load(hive, instanceId, state.activeTag);
            ApplicationConfiguration appConfig = im.getApplicationConfiguration(hive, applicationId);

            // Determine the target OS, and build the according installer.
            ScopedManifestKey applicationKey = ScopedManifestKey.parse(appConfig.application);
            if (applicationKey == null) {
                throw new WebApplicationException("Cannot identify target application: " + appConfig.application,
                        Status.EXPECTATION_FAILED);
            }

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
            ClickAndStartDescriptor clickAndStart = getClickAndStartDescriptor(im.getConfiguration().id, appConfig.id);
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
            URI iconLocation = iconUri.build(group, im.getConfiguration().id, appConfig.id);
            URI splashLocation = splashUrl.build(group, im.getConfiguration().id, appConfig.id);

            // Request a new file where we can store the installer
            DownloadServiceImpl ds = rc.initResource(new DownloadServiceImpl());

            String fileName = "%1$s (%2$s - %3$s) - Installer";
            String token = ds.createNewToken();
            Path installerPath = ds.getStoragePath(token);
            switch (applicationOs) {
                case WINDOWS:
                    fileName += ".exe";
                    createWindowsInstaller(im,//
                            appConfig, clickAndStart, installerPath, launcherKey, launcherLocation, iconLocation, splashLocation);
                    break;
                case LINUX, LINUX_AARCH64:
                    fileName += ".run";
                    createLinuxInstaller(im,//
                            appConfig, clickAndStart, installerPath, launcherKey, launcherLocation, iconLocation);
                    break;
                default:
                    throw new WebApplicationException("Unsupported OS for installer: " + applicationOs);
            }
            fileName = String.format(fileName, appConfig.name, group, im.getConfiguration().name);

            // Return the name of the token for downloading
            ds.registerForDownload(token, fileName);
            return token;
        }
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
        values.put("APP_UID", appConfig.id);
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
            config.applicationUid = appConfig.id;
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
        return downloadImage(instanceId, applicationId, appMf -> appMf.readBrandingIcon(hive),//
                descr -> descr.branding.icon, "icon");
    }

    @Override
    public Response downloadSplash(String instanceId, String applicationId) {
        return downloadImage(instanceId, applicationId, appMf -> appMf.readBrandingSplashScreen(hive),//
                descr -> descr.branding.splash.image, "splash");
    }

    private Response downloadImage(String instanceId, String applicationId, Function<ApplicationManifest, byte[]> imgExtractor,
            Function<ApplicationDescriptor, String> formatExtractor, String identifier) {
        InstanceManifest im = InstanceManifest.load(hive, instanceId, null);
        ApplicationConfiguration appConfig = im.getApplicationConfiguration(hive, applicationId);
        ApplicationManifest appMf = ApplicationManifest.of(hive, appConfig.application, null);
        byte[] img = imgExtractor.apply(appMf);
        if (img == null) {
            return Response.serverError().status(Status.NOT_FOUND).build();
        }
        String format = PathHelper.getExtension(formatExtractor.apply(appMf.getDescriptor()));
        DownloadServiceImpl ds = rc.initResource(new DownloadServiceImpl());
        return ds.serveBytes(img, identifier + "." + format);
    }

    @Override
    public Response exportInstance(String instanceId, String tag) {
        Path zip;
        try {
            zip = Files.createTempFile(minion.getTempDir(), "exp-", ".zip");
            PathHelper.deleteIfExistsRetry(zip);
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
    public List<Key> importInstance(FormDataMultiPart fdmp, String instanceId) {
        InstanceManifest im = readInstance(instanceId);
        if (im == null) {
            throw new WebApplicationException("Cannot load " + instanceId, Status.NOT_FOUND);
        }

        if (minion.getMode() == MinionMode.CENTRAL) {
            // MUST delegate this 1:1 to managed
            RemoteService svc = mp.getControllingMaster(hive, im.getKey());

            List<Key> keys = ResourceProvider.getResource(svc, InstanceGroupResource.class, context).getInstanceResource(group)
                    .importInstance(fdmp, instanceId);

            syncInstance(minion, rc, group, instanceId);

            return keys;
        }

        Path zip = minion.getDownloadDir().resolve(UuidHelper.randomId() + ".zip");
        try {
            Files.copy(FormDataHelper.getStreamFromMultiPart(fdmp), zip);

            Map<String, MinionDto> nodeMap = getMinionConfiguration(instanceId, null);
            Key newKey = InstanceImportExportHelper.importFrom(zip, hive, instanceId, nodeMap, context);
            return Collections.singletonList(newKey);
        } catch (IOException e) {
            throw new WebApplicationException("Cannot import from uploaded ZIP", e);
        } finally {
            PathHelper.deleteRecursiveRetry(zip);
        }
    }

    @Override
    public RemoteDirectory getOutputEntry(String instanceId, String tag, String app) {
        InstanceManifest im = readInstance(instanceId, tag);
        if (im == null) {
            throw new WebApplicationException("Cannot load " + instanceId + ":" + tag, Status.NOT_FOUND);
        }

        RemoteService svc = mp.getControllingMaster(hive, im.getKey());
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

        RemoteService svc = mp.getControllingMaster(hive, im.getKey());
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

        RemoteService svc = mp.getControllingMaster(hive, im.getKey());
        MasterRootResource root = ResourceProvider.getVersionedResource(svc, MasterRootResource.class, context);
        root.getNamedMaster(group).updateDataEntries(instanceId, minion, updates);
    }

    @Override
    public void deleteDataFiles(String instanceId, String minion, List<RemoteDirectoryEntry> entries) {
        Set<String> tags = entries.stream().map(e -> e.tag).collect(Collectors.toSet());
        if (tags.size() != 1) {
            throw new WebApplicationException("Data file tag is not consistent", Status.BAD_REQUEST);
        }

        String tag = tags.iterator().next();
        InstanceManifest im = readInstance(instanceId, tag);
        if (im == null) {
            throw new WebApplicationException("Cannot load " + instanceId + ":" + tag, Status.NOT_FOUND);
        }
        RemoteService svc = mp.getControllingMaster(hive, im.getKey());
        MasterRootResource root = ResourceProvider.getVersionedResource(svc, MasterRootResource.class, context);

        for (RemoteDirectoryEntry entry : entries) {
            try {
                root.getNamedMaster(group).deleteDataEntry(minion, entry);
            } catch (Exception e) {
                log.error("Failed to delete data file {}", entry.path, e);
                if (log.isTraceEnabled()) {
                    log.trace("Exception", e);
                }
            }
        }
    }

    @Override
    public Response getContentStream(String instanceId, String token) {
        EntryRequest rq = resrs.consumeRequestToken(token);

        InstanceManifest im = readInstance(instanceId, rq.getEntry().tag);
        if (im == null) {
            throw new WebApplicationException("Cannot load " + instanceId + ":" + rq.getEntry().tag, Status.NOT_FOUND);
        }

        RemoteService svc = mp.getControllingMaster(hive, im.getKey());
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

        RemoteService svc = mp.getControllingMaster(hive, im.getKey());
        MasterRootResource root = ResourceProvider.getVersionedResource(svc, MasterRootResource.class, context);
        return root.getNamedMaster(group).getEntriesZipSteam(rq.minion, rq.entries);
    }

    @Override
    public Map<Integer, Boolean> getPortStates(String instanceId, String minion, List<Integer> ports) {
        InstanceManifest im = readInstance(instanceId);
        if (im == null) {
            throw new WebApplicationException("Cannot load " + instanceId, Status.NOT_FOUND);
        }
        RemoteService svc = mp.getControllingMaster(hive, im.getKey());
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

        RemoteService svc = mp.getControllingMaster(hive, im.getKey());
        MasterRootResource root = ResourceProvider.getVersionedResource(svc, MasterRootResource.class, context);
        instanceBannerRecord.user = context.getUserPrincipal().getName();
        instanceBannerRecord.timestamp = System.currentTimeMillis();
        root.getNamedMaster(group).updateBanner(instanceId, instanceBannerRecord);
        syncInstance(minion, rc, group, instanceId);
        changes.change(ObjectChangeType.INSTANCE, im.getKey(), Map.of(ObjectChangeDetails.CHANGE_HINT, ObjectChangeHint.BANNER));
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
        RemoteService svc = mp.getControllingMaster(hive, im.getKey());
        MasterRootResource root = ResourceProvider.getVersionedResource(svc, MasterRootResource.class, context);
        root.getNamedMaster(group).updateAttributes(instanceId, attributes);
        syncInstance(minion, rc, group, instanceId);

        changes.change(ObjectChangeType.INSTANCE, im.getKey(),
                Map.of(ObjectChangeDetails.CHANGE_HINT, ObjectChangeHint.ATTRIBUTES));
    }

    @Override
    public ClientUsageData getClientUsageData(String instanceId) {
        InstanceManifest im = readInstance(instanceId);
        if (im == null) {
            throw new WebApplicationException("Cannot load " + instanceId, Status.NOT_FOUND);
        }

        // locally - we either have it or the last sync gave it to us.
        return im.getClientUsage(hive).read();
    }

    @Override
    public String getUiDirectUrl(String instance, String application, String endpoint) {
        // we try to get by without contacting the node, so we use our own data here.
        InstanceStateRecord state = getDeploymentStates(instance);
        InstanceManifest im = InstanceManifest.load(hive, instance, state.activeTag);

        String nodeName = null;
        InstanceNodeManifest inm = null;
        InstanceNodeConfiguration ic = null;
        ApplicationConfiguration app = null;

        for (Map.Entry<String, Manifest.Key> entry : im.getInstanceNodeManifestKeys().entrySet()) {
            InstanceNodeManifest inmf = InstanceNodeManifest.of(hive, entry.getValue());
            InstanceNodeConfiguration inc = inmf.getConfiguration();

            for (ApplicationConfiguration ac : inc.applications) {
                if (ac.id.equals(application)) {
                    app = ac;
                    break;
                }
            }

            if (app != null) {
                ic = inc;
                inm = inmf;
                nodeName = entry.getKey();
                break;
            }
        }

        if (app == null || ic == null || nodeName == null) {
            throw new WebApplicationException("Cannot find application or node for " + application + " in instance " + instance,
                    Status.NOT_FOUND);
        }

        Optional<HttpEndpoint> ep = app.endpoints.http.stream().filter(e -> e.id.equals(endpoint)).findAny();

        if (ep.isEmpty()) {
            throw new WebApplicationException(
                    "Cannot find endpoint " + endpoint + " for application " + application + " in instance " + instance,
                    Status.NOT_FOUND);
        }

        // note that we cannot fully resolve paths as we might be on the wrong node. thus we're using a dummy
        // resolver as paths do not play any role in calculating the URL.
        CompositeResolver instanceResolver = Resolvers.forInstancePathIndependent(inm.getConfiguration());
        instanceResolver.add(new DeploymentPathDummyResolver());
        CompositeResolver resolver = Resolvers.forApplication(instanceResolver, inm.getConfiguration(), app);

        HttpEndpoint processed = CommonEndpointHelper.processEndpoint(resolver, ep.get());
        if (processed == null) {
            throw new WebApplicationException(
                    "Endpoint not enabled: " + endpoint + " for application " + application + " in instance " + instance,
                    Status.PRECONDITION_FAILED);
        }

        MinionDto node = getMinionConfiguration(instance, state.activeTag).get(nodeName);
        if (node.minionNodeType == MinionDto.MinionNodeType.MULTI) {
            // this is not possible, as we'd need to multiplex calls.
            throw new RuntimeException("UI Endpoints are not supported on multi-nodes");
        }
        return CommonEndpointHelper.initUri(processed, node.remote.getUri().getHost(), processed.contextPath.getPreRenderable());
    }

    @Override
    public InstanceBulkResource getBulkResource() {
        return rc.initResource(new InstanceBulkResourceImpl(hive, group));
    }
}
