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
import java.util.Map.Entry;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeMap;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.model.Tree;
import io.bdeploy.bhive.op.ManifestDeleteOperation;
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
import io.bdeploy.common.util.TemplateHelper;
import io.bdeploy.common.util.UnitHelper;
import io.bdeploy.common.util.UuidHelper;
import io.bdeploy.interfaces.InstanceImportExportHelper;
import io.bdeploy.interfaces.NodeStatus;
import io.bdeploy.interfaces.ScopedManifestKey;
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
import io.bdeploy.interfaces.manifest.state.InstanceStateRecord;
import io.bdeploy.interfaces.remote.MasterNamedResource;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.jersey.JerseyWriteLockService.WriteLock;
import io.bdeploy.ui.api.AuthService;
import io.bdeploy.ui.api.ConfigFileResource;
import io.bdeploy.ui.api.InstanceResource;
import io.bdeploy.ui.api.MasterProvider;
import io.bdeploy.ui.api.Minion;
import io.bdeploy.ui.api.ProcessResource;
import io.bdeploy.ui.api.SoftwareUpdateResource;
import io.bdeploy.ui.branding.Branding;
import io.bdeploy.ui.branding.BrandingConfig;
import io.bdeploy.ui.dto.InstanceDto;
import io.bdeploy.ui.dto.InstanceManifestHistoryDto;
import io.bdeploy.ui.dto.InstanceNodeConfigurationListDto;
import io.bdeploy.ui.dto.InstanceVersionDto;
import io.bdeploy.ui.dto.ProductDto;
import io.bdeploy.ui.dto.StringEntryChunkDto;

public class InstanceResourceImpl implements InstanceResource {

    private static final Logger log = LoggerFactory.getLogger(InstanceResourceImpl.class);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
            .withLocale(Locale.getDefault()).withZone(ZoneId.systemDefault());

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

    @Context
    private UriInfo info;

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
                MasterRootResource root = ResourceProvider.getResource(mp.getControllingMaster(hive, imKey),
                        MasterRootResource.class, context);
                MasterNamedResource master = root.getNamedMaster(group);
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
                log.error("Cannot contact master for {}.", config.uuid, e);
            }

            // Clear security token before sending via REST
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
        return readInstance(instanceId, versionTag).getConfiguration();
    }

    @Override
    public void create(InstanceConfiguration instanceConfig) {
        ProductManifest product = ProductManifest.of(hive, instanceConfig.product);
        if (product == null) {
            throw new WebApplicationException("Product not found: " + instanceConfig.product, Status.NOT_FOUND);
        }

        // FIXME: need to create association to "local server" from the configuration data passed in case mode = CENTRAL
        // This needs to be done here AND in the code which syncs instances to central.
        MasterRootResource root = ResourceProvider.getResource(mp.getControllingMaster(hive, null), MasterRootResource.class,
                context);

        root.getNamedMaster(group)
                .update(new InstanceUpdateDto(new InstanceConfigurationDto(instanceConfig, Collections.emptyList()),
                        getUpdatesFromTree("", new ArrayList<>(), product.getConfigTemplateTreeId())), null);
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
    public void update(String instance, InstanceConfigurationDto dto, String expectedTag) {
        InstanceManifest oldConfig = InstanceManifest.load(hive, instance, null);

        if (!oldConfig.getManifest().getTag().equals(expectedTag)) {
            throw new WebApplicationException("Expected version is not the current one: expected=" + expectedTag + ", current="
                    + oldConfig.getManifest().getTag(), Status.CONFLICT);
        }

        if (dto.config == null) {
            // no new config - load existing one.
            dto.config = oldConfig.getConfiguration();
        }

        MasterRootResource root = ResourceProvider.getResource(mp.getControllingMaster(hive, oldConfig.getManifest()),
                MasterRootResource.class, context);
        Manifest.Key key = root.getNamedMaster(group).update(new InstanceUpdateDto(dto, Collections.emptyList()), expectedTag);

        UiResources.getInstanceEventManager().create(instance, key);
    }

    @Override
    public void delete(String instance) {
        // TODO: delegate to master
        // prevent delete if processes are running.
        InstanceManifest im = readInstance(instance);
        RemoteService master = mp.getControllingMaster(hive, im.getManifest());
        try (Activity deploy = reporter.start("Deleting " + instance + "...");
                NoThrowAutoCloseable proxy = reporter.proxyActivities(master)) {
            MasterRootResource root = ResourceProvider.getResource(master, MasterRootResource.class, context);
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
        RemoteService rsvc = mp.getControllingMaster(hive, thisIm.getManifest());
        try (Activity fetchNodes = reporter.start("Fetching nodes from master");
                AutoCloseable proxy = reporter.proxyActivities(rsvc)) {
            MasterRootResource master = ResourceProvider.getResource(rsvc, MasterRootResource.class, context);
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
            log.warn("Master offline: {}", thisIm.getConfiguration().uuid);
            if (log.isTraceEnabled()) {
                log.trace("Exception", e);
            }

            // make sure there is at least a master... even if the master is not reachable.
            node2Dto.put(Minion.DEFAULT_MASTER_NAME, new InstanceNodeConfigurationDto(Minion.DEFAULT_MASTER_NAME, null,
                    "Error contacting master: " + e.getMessage()));
        }

        return node2Dto;
    }

    @Override
    public void install(String instanceId, String tag) {
        InstanceManifest instance = InstanceManifest.load(hive, instanceId, tag);
        RemoteService svc = mp.getControllingMaster(hive, instance.getManifest());
        try (Activity deploy = reporter.start("Deploying " + instance.getConfiguration().name + ":" + tag);
                NoThrowAutoCloseable proxy = reporter.proxyActivities(svc)) {
            // 1. push manifest to remote
            TransferStatistics stats = hive
                    .execute(new PushOperation().setRemote(svc).addManifest(instance.getManifest()).setHiveName(group));

            log.info("Pushed {} to {}; trees={}, objs={}, size={}", instance.getManifest(), svc.getUri(), stats.sumMissingTrees,
                    stats.sumMissingObjects, UnitHelper.formatFileSize(stats.transferSize));

            // 2: tell master to deploy
            MasterRootResource master = ResourceProvider.getResource(svc, MasterRootResource.class, context);
            master.getNamedMaster(group).install(instance.getManifest());
        }

        UiResources.getInstanceEventManager().stateChanged(instanceId, instance.getManifest());
    }

    @Override
    public void uninstall(String instanceId, String tag) {
        InstanceManifest instance = InstanceManifest.load(hive, instanceId, tag);
        RemoteService svc = mp.getControllingMaster(hive, instance.getManifest());
        try (Activity deploy = reporter.start("Undeploying " + instance.getConfiguration().name + ":" + tag);
                NoThrowAutoCloseable proxy = reporter.proxyActivities(svc)) {

            // 1: check for running or scheduled applications
            MasterRootResource master = ResourceProvider.getResource(svc, MasterRootResource.class, context);
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

        UiResources.getInstanceEventManager().stateChanged(instanceId, instance.getManifest());
    }

    @Override
    public void activate(String instanceId, String tag) {
        InstanceManifest instance = InstanceManifest.load(hive, instanceId, tag);
        RemoteService svc = mp.getControllingMaster(hive, instance.getManifest());
        try (Activity deploy = reporter.start("Activating " + instanceId + ":" + tag);
                NoThrowAutoCloseable proxy = reporter.proxyActivities(svc)) {
            MasterRootResource master = ResourceProvider.getResource(svc, MasterRootResource.class, context);
            master.getNamedMaster(group).activate(instance.getManifest());
        }

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
        RemoteService svc = mp.getControllingMaster(hive, instance.getManifest());
        MasterRootResource master = ResourceProvider.getResource(svc, MasterRootResource.class, context);
        return master.getNamedMaster(group).getInstanceState(instanceId);
    }

    @Override
    public ClickAndStartDescriptor getClickAndStartDescriptor(String instanceId, String applicationId) {
        InstanceManifest im = InstanceManifest.load(hive, instanceId, null);

        RemoteService svc = mp.getControllingMaster(hive, im.getManifest());
        MasterNamedResource master = ResourceProvider.getResource(svc, MasterRootResource.class, context).getNamedMaster(group);

        ClickAndStartDescriptor desc = new ClickAndStartDescriptor();
        desc.applicationId = applicationId;
        desc.groupId = group;
        desc.instanceId = instanceId;
        desc.host = new RemoteService(info.getBaseUri(), master.generateWeakToken(context.getUserPrincipal().getName()));

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
        InstanceManifest im = InstanceManifest.load(hive, instanceId, null);
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

        UriBuilder launcherUri = UriBuilder.fromUri(info.getBaseUri());
        launcherUri.path(SoftwareUpdateResource.ROOT_PATH);
        launcherUri.path(SoftwareUpdateResource.DOWNLOAD_LATEST_PATH);

        UriBuilder iconUri = UriBuilder.fromUri(info.getBaseUri());
        iconUri.path("/group/{group}/instance/");
        iconUri.path(InstanceResource.PATH_DOWNLOAD_APP_ICON);

        UriBuilder splashUrl = UriBuilder.fromUri(info.getBaseUri());
        splashUrl.path("/group/{group}/instance/");
        splashUrl.path(InstanceResource.PATH_DOWNLOAD_APP_SPLASH);

        URI launcherLocation = launcherUri.build(new Object[] { applicationOs.name().toLowerCase() }, false);
        URI iconLocation = iconUri.build(group, im.getConfiguration().uuid, appConfig.uid);
        URI splashLocation = splashUrl.build(group, im.getConfiguration().uuid, appConfig.uid);

        String fileName;
        if (applicationOs == OperatingSystem.WINDOWS) {
            fileName = im.getConfiguration().name + " - " + appConfig.name + " - Installer.exe";
            createWindowsInstaller(im, appConfig, installerPath, launcherKey, launcherLocation, iconLocation, splashLocation);
        } else if (applicationOs == OperatingSystem.LINUX || applicationOs == OperatingSystem.MACOS) {
            fileName = im.getConfiguration().name + "-" + appConfig.name + "-Installer.run";
            createLinuxInstaller(im, appConfig, installerPath, launcherKey, launcherLocation, iconLocation);
        } else {
            throw new WebApplicationException("Unsupported OS for installer: " + applicationOs);
        }

        // Return the name of the token for downloading
        ds.registerForDownload(token, fileName);
        return token;
    }

    private void createLinuxInstaller(InstanceManifest im, ApplicationConfiguration appConfig, Path installerPath,
            ScopedManifestKey launcherKey, URI launcherLocation, URI iconLocation) {
        BHive rootHive = reg.get(JerseyRemoteBHive.DEFAULT_NAME);
        Manifest mf = rootHive.execute(new ManifestLoadOperation().setManifest(launcherKey.getKey()));
        TreeEntryLoadOperation findInstallerOp = new TreeEntryLoadOperation().setRootTree(mf.getRoot())
                .setRelativePath(SoftwareUpdateResourceImpl.INSTALLER_SH);
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
        values.put("REMOTE_SERVICE_URL", ""); // not used, only in standalone
        values.put("REMOTE_SERVICE_TOKEN", ""); // not used, only in standalone

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
                .setRelativePath(SoftwareUpdateResourceImpl.INSTALLER_EXE);
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
            config.remoteService = clickAndStart.host;
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
        return ds.serveFile(zip, "icon." + instanceId + "-" + tag + ".zip");
    }

    @WriteLock
    @Override
    public List<Key> importInstance(InputStream inputStream, String instanceId) {
        InstanceManifest im = readInstance(instanceId);
        if (im == null) {
            throw new WebApplicationException("Cannot load " + instanceId, Status.NOT_FOUND);
        }

        RemoteService svc = mp.getControllingMaster(hive, im.getManifest());
        MasterRootResource root = ResourceProvider.getResource(svc, MasterRootResource.class, context);
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
        InstanceManifest im = readInstance(instanceId, tag);
        if (im == null) {
            throw new WebApplicationException("Cannot load " + instanceId + ":" + tag, Status.NOT_FOUND);
        }

        RemoteService svc = mp.getControllingMaster(hive, im.getManifest());
        MasterRootResource root = ResourceProvider.getResource(svc, MasterRootResource.class, context);
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
        MasterRootResource root = ResourceProvider.getResource(svc, MasterRootResource.class, context);
        EntryChunk chunk = root.getNamedMaster(group).getEntryContent(minion, entry, offset, limit);
        if (chunk == null) {
            return null;
        }
        return new StringEntryChunkDto(chunk);
    }

}
