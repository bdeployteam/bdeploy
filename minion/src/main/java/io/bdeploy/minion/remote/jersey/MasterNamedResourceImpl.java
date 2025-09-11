package io.bdeploy.minion.remote.jersey;

import static io.bdeploy.common.util.RuntimeAssert.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;

import org.apache.commons.codec.binary.Base64;
import org.apache.poi.util.StringUtil;
import org.glassfish.jersey.media.multipart.ContentDisposition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.api.product.v1.impl.LocalDependencyFetcher;
import io.bdeploy.api.product.v1.impl.ScopedManifestKey;
import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.BHiveTransactions.Transaction;
import io.bdeploy.bhive.meta.MetaManifest;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.objects.view.TreeView;
import io.bdeploy.bhive.objects.view.scanner.TreeVisitor;
import io.bdeploy.bhive.op.ExportTreeOperation;
import io.bdeploy.bhive.op.ImportTreeOperation;
import io.bdeploy.bhive.op.ManifestDeleteOperation;
import io.bdeploy.bhive.op.ManifestExistsOperation;
import io.bdeploy.bhive.op.ManifestListOperation;
import io.bdeploy.bhive.op.ManifestNextIdOperation;
import io.bdeploy.bhive.op.ObjectLoadOperation;
import io.bdeploy.bhive.op.ScanOperation;
import io.bdeploy.bhive.op.remote.PushOperation;
import io.bdeploy.common.Version;
import io.bdeploy.common.actions.Actions;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.common.util.RuntimeAssert;
import io.bdeploy.common.util.StreamHelper;
import io.bdeploy.common.util.StringHelper;
import io.bdeploy.common.util.TemplateHelper;
import io.bdeploy.common.util.UuidHelper;
import io.bdeploy.common.util.VariableResolver;
import io.bdeploy.common.util.VersionHelper;
import io.bdeploy.common.util.ZipHelper;
import io.bdeploy.interfaces.VerifyOperationResultDto;
import io.bdeploy.interfaces.configuration.dcu.ApplicationConfiguration;
import io.bdeploy.interfaces.configuration.dcu.CommandConfiguration;
import io.bdeploy.interfaces.configuration.dcu.ParameterConfiguration;
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
import io.bdeploy.interfaces.configuration.pcu.ProcessControlConfiguration;
import io.bdeploy.interfaces.configuration.pcu.ProcessControlGroupConfiguration;
import io.bdeploy.interfaces.configuration.pcu.ProcessDetailDto;
import io.bdeploy.interfaces.configuration.pcu.ProcessState;
import io.bdeploy.interfaces.configuration.pcu.ProcessStatusDto;
import io.bdeploy.interfaces.configuration.system.SystemConfiguration;
import io.bdeploy.interfaces.descriptor.application.ProcessControlDescriptor.ApplicationStartType;
import io.bdeploy.interfaces.directory.EntryChunk;
import io.bdeploy.interfaces.directory.RemoteDirectory;
import io.bdeploy.interfaces.directory.RemoteDirectoryEntry;
import io.bdeploy.interfaces.manifest.ApplicationManifest;
import io.bdeploy.interfaces.manifest.InstanceGroupManifest;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.manifest.InstanceNodeManifest;
import io.bdeploy.interfaces.manifest.ProductManifest;
import io.bdeploy.interfaces.manifest.SystemManifest;
import io.bdeploy.interfaces.manifest.attributes.CustomAttributesRecord;
import io.bdeploy.interfaces.manifest.banner.InstanceBannerRecord;
import io.bdeploy.interfaces.manifest.history.InstanceManifestHistory;
import io.bdeploy.interfaces.manifest.history.InstanceManifestHistory.Action;
import io.bdeploy.interfaces.manifest.history.InstanceManifestHistoryRecord;
import io.bdeploy.interfaces.manifest.history.runtime.MasterRuntimeHistoryDto;
import io.bdeploy.interfaces.manifest.state.InstanceOverallStateRecord;
import io.bdeploy.interfaces.manifest.state.InstanceOverallStateRecord.OverallStatus;
import io.bdeploy.interfaces.manifest.state.InstanceState;
import io.bdeploy.interfaces.manifest.state.InstanceStateRecord;
import io.bdeploy.interfaces.manifest.statistics.ClientUsage;
import io.bdeploy.interfaces.manifest.statistics.ClientUsageData;
import io.bdeploy.interfaces.minion.MinionDto;
import io.bdeploy.interfaces.minion.MinionStatusDto;
import io.bdeploy.interfaces.nodes.NodeType;
import io.bdeploy.interfaces.remote.CommonDirectoryEntryResource;
import io.bdeploy.interfaces.remote.MasterNamedResource;
import io.bdeploy.interfaces.remote.MasterSystemResource;
import io.bdeploy.interfaces.remote.NodeDeploymentResource;
import io.bdeploy.interfaces.remote.NodeProcessResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.interfaces.settings.MailSenderSettingsDto;
import io.bdeploy.jersey.JerseyWriteLockService.LockingResource;
import io.bdeploy.jersey.JerseyWriteLockService.WriteLock;
import io.bdeploy.jersey.actions.ActionFactory;
import io.bdeploy.jersey.ws.change.msg.ObjectScope;
import io.bdeploy.messaging.MessageDataHolder;
import io.bdeploy.messaging.MessageSender;
import io.bdeploy.messaging.MimeFile;
import io.bdeploy.messaging.util.MessagingUtils;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.minion.mail.AttachmentUtils;
import io.bdeploy.minion.mail.MinionSignedAttachment;
import io.bdeploy.ui.RequestScopedParallelOperationsService;
import io.bdeploy.ui.api.MinionMode;
import io.bdeploy.ui.api.NodeManager;
import io.bdeploy.ui.api.impl.ChangeEventManager;
import io.bdeploy.ui.dto.ObjectChangeDetails;
import io.bdeploy.ui.dto.ObjectChangeHint;
import io.bdeploy.ui.dto.ObjectChangeType;
import jakarta.inject.Inject;
import jakarta.mail.internet.InternetAddress;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.StreamingOutput;

@LockingResource
public class MasterNamedResourceImpl implements MasterNamedResource {

    private static final Logger log = LoggerFactory.getLogger(MasterNamedResourceImpl.class);

    private final BHive hive;
    private final MinionRoot root;

    @Context
    private ResourceContext rc;

    @Context
    private SecurityContext context;

    @Inject
    private NodeManager nodes;

    @Inject
    private ActionFactory af;

    @Inject
    private RequestScopedParallelOperationsService rspos;

    @Inject
    private MessageSender mailSender;

    @Inject
    private ChangeEventManager changes;

    private final String name;

    public MasterNamedResourceImpl(MinionRoot root, BHive hive, String name) {
        this.root = root;
        this.hive = hive;
        this.name = name;
    }

    private static InstanceState getState(InstanceManifest im, BHive hive) {
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
        InstanceConfiguration instanceConfig = imf.getConfiguration();
        String instanceId = instanceConfig.id;
        Key productKey = instanceConfig.product;

        try (var handle = af.run(Actions.INSTALL, name, instanceId, key.getTag())) {
            // Assure that the product has been pushed to the master (e.g. by central).
            Boolean productExists = hive.execute(new ManifestExistsOperation().setManifest(productKey));
            if (!Boolean.TRUE.equals(productExists)) {
                throw new WebApplicationException("Cannot find required product " + productKey, Status.NOT_FOUND);
            }

            // Assure that the minion has the minimum required BDeploy version
            String minMinionVersionString = ProductManifest.of(hive, productKey).getProductDescriptor().minMinionVersion;
            if (StringUtil.isNotBlank(minMinionVersionString)) {
                Version minMinionVersion;
                try {
                    minMinionVersion = VersionHelper.parse(minMinionVersionString);
                } catch (RuntimeException e) {
                    throw new WebApplicationException(
                            "Failed to parse minimum BDeploy version '" + minMinionVersionString + "' of product " + productKey,
                            e);
                }
                if (root.getNodeManager().getSelf().version.compareTo(minMinionVersion) < 0) {
                    throw new WebApplicationException(
                            "Installation aborted because minion does not meet the minimum BDeploy version of "
                                    + minMinionVersionString);
                }
            }

            // Create the runnables
            List<Runnable> runnables = new ArrayList<>();
            for (Map.Entry<String, Manifest.Key> entry : imf.getNonClientInstanceNodeManifestKeys(hive).entrySet()) {
                String nodeName = entry.getKey();
                Manifest.Key toDeploy = entry.getValue();
                assertNotNull(toDeploy, "Cannot lookup node manifest on master: " + toDeploy);

                Map<String, MinionDto> onlineNodes = nodes.getOnlineNodeConfigs(nodeName);
                if (onlineNodes.isEmpty()) {
                    log.info("Node {} is offline. Skipping install", nodeName);
                    continue;
                }

                onlineNodes.forEach((name, node) -> runnables.add(() -> {
                    // grab all required manifests from the applications
                    InstanceNodeManifest inm = InstanceNodeManifest.of(hive, toDeploy);
                    LocalDependencyFetcher localDeps = new LocalDependencyFetcher();
                    PushOperation pushOp = new PushOperation().setRemote(node.remote);
                    for (ApplicationConfiguration app : inm.getConfiguration().applications) {
                        pushOp.addManifest(app.application);
                        ApplicationManifest amf = ApplicationManifest.of(hive, app.application, null);

                        // applications /must/ follow the ScopedManifestKey rules.
                        ScopedManifestKey smk = ScopedManifestKey.parse(app.application);
                        if (smk == null) {
                            log.error("Manifest for application '{}' could not be found - it will not be installed",
                                    app.application);
                            continue;
                        }

                        // the dependency must be here. it has been pushed here with the product,
                        // since the product /must/ reference all direct dependencies.
                        localDeps.fetch(hive, amf.getDescriptor().runtimeDependencies, smk.getOperatingSystem())
                                .forEach(pushOp::addManifest);
                    }

                    // Make sure the node has the manifest
                    pushOp.addManifest(toDeploy);

                    // Create the task that pushes all manifests and then installs them on the remote
                    NodeDeploymentResource deployment = ResourceProvider.getVersionedResource(node.remote,
                            NodeDeploymentResource.class, context);
                    hive.execute(pushOp);
                    deployment.install(toDeploy);
                }));
            }

            // Execute all tasks
            rspos.runAndAwaitAll("Install", runnables, hive.getTransactions());

            getState(imf, hive).install(key.getTag());
            imf.getHistory(hive).recordAction(Action.INSTALL, context.getUserPrincipal().getName(), null);

            // Inform about changes
            changes.change(ObjectChangeType.INSTANCE, imf.getKey(), new ObjectScope(this.name, instanceId),
                    Map.of(ObjectChangeDetails.CHANGE_HINT, ObjectChangeHint.STATE));
        }
    }

    @Override
    public void syncNode(String nodeName, String instanceId) {
        // Hint: this is never called for nodes of type MULTI. only for SERVER and MULTI_RUNTIMNE, which
        // are always single nodes, the this map will always only contain a single node.
        MinionDto node = nodes.getSingleOnlineNodeConfig(nodeName);

        if (node == null) {
            String msg = "Node " + nodeName + " is offline. Cannot sync instance " + instanceId;
            log.info(msg);
            throw new WebApplicationException(msg, Status.EXPECTATION_FAILED);
        }
        InstanceManifest imf = InstanceManifest.load(hive, instanceId, null);

        try (var handle = af.run(Actions.SYNC_NODE, name, imf.getConfiguration().id, nodeName)) {
            InstanceStateRecord state = imf.getState(hive).read();
            String instanceActiveTag = state.activeTag;

            NodeProcessResource npr = nodes.getNodeResourceIfOnlineOrThrow(nodeName, NodeProcessResource.class, context);
            InstanceNodeStatusDto nodeStatus = npr.getStatus(instanceId);

            NodeDeploymentResource deployment = ResourceProvider.getResource(node.remote, NodeDeploymentResource.class, context);

            String nodeConfigName =
                    node.minionNodeType == MinionDto.MinionNodeType.MULTI_RUNTIME ? nodes.getMultiNodeConfigNameForRuntimeNode(
                            nodeName) : nodeName;

            // if node active tag is different from current active tag -> deactivate
            String nodeActiveTag = nodeStatus.activeTag;
            if (nodeActiveTag != null && !nodeActiveTag.equals(instanceActiveTag)) {
                Manifest.Key toDeactivate = getNodeConfigKey(nodeConfigName, instanceId, nodeActiveTag);
                log.debug("Deactivating {}", toDeactivate);
                deployment.deactivate(toDeactivate);
            }

            // make sure every installed version is installed on the node if applicable
            for (String installedTag : state.installedTags) {
                Manifest.Key toInstall = getNodeConfigKey(nodeConfigName, instanceId, installedTag);
                if (toInstall == null) {
                    continue;
                }

                log.debug("Installing {}", toInstall);
                installOnNode(node, toInstall);
            }

            // activate current active version on node if applicable
            if (instanceActiveTag != null && !instanceActiveTag.equals(nodeActiveTag)) {
                Manifest.Key toActivate = getNodeConfigKey(nodeConfigName, instanceId, instanceActiveTag);
                if (toActivate != null) {
                    log.debug("Activating {}", toActivate);
                    deployment.activate(toActivate);
                }
            }
        }
    }

    // returns null if node is not present in instance version
    private Manifest.Key getNodeConfigKey(String nodeName, String instanceId, String versionTag) {
        InstanceManifest imf = InstanceManifest.load(hive, instanceId, versionTag);
        SortedMap<String, Key> fragmentReferences = imf.getInstanceNodeManifestKeys();
        return fragmentReferences.get(nodeName);
    }

    private void installOnNode(MinionDto node, Manifest.Key toDeploy) {
        NodeDeploymentResource deployment = ResourceProvider.getVersionedResource(node.remote, NodeDeploymentResource.class,
                context);

        // grab all required manifests from the applications
        InstanceNodeManifest inm = InstanceNodeManifest.of(hive, toDeploy);
        LocalDependencyFetcher localDeps = new LocalDependencyFetcher();
        PushOperation pushOp = new PushOperation().setRemote(node.remote);
        for (ApplicationConfiguration app : inm.getConfiguration().applications) {
            pushOp.addManifest(app.application);
            ApplicationManifest amf = ApplicationManifest.of(hive, app.application, null);

            // applications /must/ follow the ScopedManifestKey rules.
            ScopedManifestKey smk = ScopedManifestKey.parse(app.application);
            if (smk == null) {
                log.error("Manifest for application '{}' could not be found - it will not be installed", app.application);
                continue;
            }

            // the dependency must be here. it has been pushed here with the product,
            // since the product /must/ reference all direct dependencies.
            localDeps.fetch(hive, amf.getDescriptor().runtimeDependencies, smk.getOperatingSystem()).forEach(pushOp::addManifest);
        }

        // Make sure the node has the manifest
        pushOp.addManifest(toDeploy);

        // Create the task that pushes all manifests and then installs them on the remote
        hive.execute(pushOp);

        deployment.install(toDeploy);
    }

    @Override
    @WriteLock
    public void activate(Key key) {
        InstanceManifest imf = InstanceManifest.of(hive, key);

        try (var handle = af.run(Actions.ACTIVATE, name, imf.getConfiguration().id, key.getTag())) {
            // record de-activation
            String activeTag = imf.getState(hive).read().activeTag;
            if (activeTag != null) {
                try {
                    InstanceManifest oldIm = InstanceManifest.load(hive, imf.getConfiguration().id, activeTag);
                    oldIm.getHistory(hive).recordAction(Action.DEACTIVATE, context.getUserPrincipal().getName(), null);

                    // make sure all nodes which no longer participate are deactivated.
                    for (Map.Entry<String, Manifest.Key> oldNode : oldIm.getInstanceNodeManifestKeys().entrySet()) {
                        // deactivation by activation later on.
                        if (imf.getInstanceNodeManifestKeys().containsKey(oldNode.getKey())) {
                            continue;
                        }

                        Map<String, MinionDto> onlineNodes = nodes.getOnlineNodeConfigs(oldNode.getKey());
                        if (onlineNodes == null) {
                            log.info("No node online for de-activation for {}", oldNode.getKey());
                            continue;
                        }

                        onlineNodes.values().forEach(node -> {
                            ResourceProvider.getVersionedResource(node.remote, NodeDeploymentResource.class, context)
                                    .deactivate(oldNode.getValue());
                        });
                    }
                } catch (Exception e) {
                    // in case the old version disappeared (manual deletion, automatic migration,
                    // ...) we do not want to fail to activate the new version...
                    if (log.isDebugEnabled()) {
                        log.debug("Cannot set old version to de-activated", e);
                    }
                }
            }

            SortedMap<String, Key> fragments = imf.getNonClientInstanceNodeManifestKeys(hive);
            for (Map.Entry<String, Manifest.Key> entry : fragments.entrySet()) {
                String nodeName = entry.getKey();
                Manifest.Key toDeploy = entry.getValue();
                Map<String, MinionDto> onlineNodes = nodes.getOnlineNodeConfigs(nodeName);

                assertNotNull(toDeploy, "Cannot lookup node manifest on master: " + toDeploy);
                if (onlineNodes.isEmpty()) {
                    log.info("Skipping activation on node {}. No node is online", nodeName);
                    continue;
                }

                onlineNodes.forEach((name, node) -> {
                    NodeDeploymentResource deployment = ResourceProvider.getVersionedResource(node.remote,
                            NodeDeploymentResource.class, context);

                    try {
                        deployment.activate(toDeploy);
                    } catch (Exception e) {
                        // log but don't forward exception to the client
                        throw new WebApplicationException("Cannot activate on " + name, e, Status.BAD_GATEWAY);
                    }
                });
            }

            getState(imf, hive).activate(key.getTag());
            imf.getHistory(hive).recordAction(Action.ACTIVATE, context.getUserPrincipal().getName(), null);

            // inform about changes
            changes.change(ObjectChangeType.INSTANCE, imf.getKey(), new ObjectScope(this.name, imf.getConfiguration().id),
                    Map.of(ObjectChangeDetails.CHANGE_HINT, ObjectChangeHint.STATE));
        }
    }

    @Override
    public void uninstall(Key key) {
        InstanceManifest imf = InstanceManifest.of(hive, key);

        try (var handle = af.run(Actions.UNINSTALL, name, imf.getConfiguration().id, key.getTag())) {
            List<Runnable> runnables = new ArrayList<>();
            for (Map.Entry<String, Manifest.Key> entry : imf.getNonClientInstanceNodeManifestKeys(hive).entrySet()) {
                String nodeName = entry.getKey();
                Manifest.Key toRemove = entry.getValue();

                Map<String, MinionDto> onlineNodes = nodes.getOnlineNodeConfigs(nodeName);
                if (onlineNodes.isEmpty()) {
                    // minion no longer exists or node is offline. this is recoverable as when the node is online
                    // during the next cleanup cycle, it will clean itself.
                    log.warn("Node not available: {}. Ignoring.", nodeName);
                    return;
                }

                onlineNodes.forEach((name, node) -> runnables.add(() -> {
                    assertNotNull(toRemove, "Cannot lookup node manifest on master: " + toRemove);

                    NodeDeploymentResource deployment = ResourceProvider.getVersionedResource(node.remote,
                            NodeDeploymentResource.class, context);
                    deployment.remove(toRemove);
                }));
            }

            // Execute all tasks
            rspos.runAndAwaitAll("Uninstall", runnables, hive.getTransactions());

            getState(imf, hive).uninstall(key.getTag());
            imf.getHistory(hive).recordAction(Action.UNINSTALL, context.getUserPrincipal().getName(), null);

            // inform about changes
            changes.change(ObjectChangeType.INSTANCE, imf.getKey(), new ObjectScope(this.name, imf.getConfiguration().id),
                    Map.of(ObjectChangeDetails.CHANGE_HINT, ObjectChangeHint.STATE));
        }
    }

    private static void assureTagMatch(String targetTag, String actualTag) {
        if (targetTag == null || actualTag == null) {
            throw new WebApplicationException("Cannot determine target or actual tag of instance object",
                    Status.INTERNAL_SERVER_ERROR);
        }

        if (!actualTag.equals(targetTag)) {
            throw new WebApplicationException("Tag mismatch in instance configuation, " + targetTag + " != " + actualTag,
                    Status.EXPECTATION_FAILED);
        }
    }

    private Manifest.Key createInstanceVersion(Manifest.Key target, InstanceConfiguration config,
            SortedMap<String, InstanceNodeConfiguration> nodes) {

        InstanceManifest.Builder builder = new InstanceManifest.Builder();
        builder.setInstanceConfiguration(config);
        builder.setKey(target);

        // load system if we have one.
        SystemConfiguration system = null;
        if (config.system != null) {
            system = SystemManifest.of(hive, config.system).getConfiguration();
        }

        var targetTag = config.product.getTag();

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
                if (cfg.id == null) {
                    cfg.id = UuidHelper.randomId();
                    log.info("New Application {} received ID {}", cfg.name, cfg.id);
                }

                // also make sure that every parameter is present in the command line only once.
                cleanCommandDuplicates(cfg.start);
                cleanCommandDuplicates(cfg.stop);
            }

            // make sure that all copies of the tags throughout the configuration
            // are consistent (product, applications, ...)
            assureTagMatch(targetTag, inc.product.getTag());
            for (var a : inc.applications) {
                assureTagMatch(targetTag, a.application.getTag());
            }

            RuntimeAssert.assertEquals(inc.id, config.id, "Instance ID not set on nodes");

            InstanceNodeManifest.Builder inmb = new InstanceNodeManifest.Builder();
            inmb.addConfigTreeId(InstanceNodeManifest.ROOT_CONFIG_NAME, config.configTree);
            inmb.setMinionName(entry.getKey());
            inmb.setInstanceNodeConfiguration(inc);
            inmb.setKey(new Manifest.Key(config.id + '/' + entry.getKey(), target.getTag()));

            // create dedicated configuration trees for client applications where required.
            if (inc.nodeType == NodeType.CLIENT) {
                List<ObjectId> configTrees = new ArrayList<>();
                // client applications *may* specify config directories.
                for (var app : inc.applications) {
                    String configDirs = app.processControl.configDirs;
                    if (app.processControl == null || StringHelper.isNullOrEmpty(configDirs)) {
                        continue; // no dirs set.
                    }

                    // we have directories set, and need to create a dedicated config tree for the application.
                    String[] allowedPaths = ProcessControlConfiguration.CONFIG_DIRS_SPLIT_PATTERN.split(configDirs);

                    // remove unwanted paths from p.
                    ObjectId appTree = applyConfigUpdates(config.configTree, p -> applyConfigRestrictions(allowedPaths, p, p));

                    // record the config tree for this application.
                    inmb.addConfigTreeId(app.id, appTree);
                    configTrees.add(appTree);
                }
                inc.mergeVariables(config, system, v -> processConfigFilesInMemory(configTrees, v));
            } else {
                Consumer<VariableResolver> cfgResolver = null != config.configTree
                        ? v -> processConfigFilesInMemory(Collections.singletonList(config.configTree), v)
                        : null;
                inc.mergeVariables(config, system, cfgResolver);
            }

            builder.addInstanceNodeManifest(entry.getKey(), inmb.insert(hive));
        }

        Manifest.Key key = builder.insert(hive);
        InstanceManifest.of(hive, key).getHistory(hive).recordAction(Action.CREATE, context.getUserPrincipal().getName(), null);
        return key;
    }

    private static void cleanCommandDuplicates(CommandConfiguration command) {
        if (command == null || command.parameters == null || command.parameters.isEmpty()) {
            return;
        }

        Set<String> idCache = new TreeSet<>();
        List<ParameterConfiguration> deduped = new ArrayList<>();
        for (ParameterConfiguration cfg : command.parameters) {
            if (idCache.contains(cfg.id)) {
                continue; // skip.
            }
            idCache.add(cfg.id);
            deduped.add(cfg);
        }

        command.parameters = deduped;
    }

    private void processConfigFilesInMemory(List<ObjectId> configTrees, VariableResolver resolver) {
        if (configTrees == null || configTrees.isEmpty()) {
            return;
        }

        // for each tree, process all files once.
        for (ObjectId tree : configTrees) {
            if (tree == null) {
                continue;
            }
            TreeView scan = hive.execute(new ScanOperation().setTree(tree));
            scan.visit(new TreeVisitor.Builder().onBlob(bv -> {
                // this is a config file - need to process it.
                try (InputStream is = hive.execute(new ObjectLoadOperation().setObject(bv.getElementId()))) {
                    // process whole files.
                    TemplateHelper.process(new String(StreamHelper.read(is), StandardCharsets.UTF_8), resolver);
                } catch (Exception e) {
                    log.warn("Cannot process {}", bv.getPathString(), e);
                }
            }).build());
        }
    }

    /**
     * Client applications can specify a set of allowed paths. From the current config tree and this set of allowed paths, we
     * remove all paths which are not allowed. This builds a dedicated per-application configuration which is allowed to be
     * provided to clients - this avoids sending sensitive config files which should only be available on servers.
     */
    private static void applyConfigRestrictions(String[] allowedPaths, Path p, Path root) {
        try (DirectoryStream<Path> list = Files.newDirectoryStream(p)) {
            for (Path path : list) {
                if (Files.isDirectory(path)) {
                    // need to recurse to check.
                    applyConfigRestrictions(allowedPaths, path, root);
                } else {
                    // only accept if the file relative path starts with the
                    boolean ok = false;
                    for (String x : allowedPaths) {
                        Path rel = root.relativize(path);
                        if (("/" + rel.toString().replace("\\", "/")).startsWith(x.endsWith("/") ? x : (x + "/"))) {
                            ok = true;
                        }
                    }

                    if (!ok) {
                        PathHelper.deleteRecursiveRetry(path);
                    }
                }
            }
        } catch (IOException e) {
            throw new WebApplicationException("Cannot apply content restriction", e);
        }
    }

    @WriteLock
    @Override
    public Manifest.Key update(InstanceUpdateDto update, String expectedTag) {
        InstanceConfigurationDto state = update.config;
        List<FileStatusDto> configUpdates = update.files;
        InstanceConfiguration instanceConfig = state.config;

        try (var handle = af.run(Actions.CREATE_INSTANCE_VERSION, name, instanceConfig.id)) {

            String rootName = InstanceManifest.getRootName(instanceConfig.id);
            Set<Key> existing = hive.execute(new ManifestListOperation().setManifestName(rootName));
            InstanceManifest oldConfig = null;
            if (expectedTag == null && !existing.isEmpty()) {
                throw new WebApplicationException("Instance already exists: " + instanceConfig.id, Status.BAD_REQUEST);
            } else if (expectedTag != null) {
                oldConfig = InstanceManifest.load(hive, instanceConfig.id, null);
                if (!oldConfig.getKey().getTag().equals(expectedTag)) {
                    throw new WebApplicationException("Expected version is not the current one: expected=" + expectedTag
                            + ", current=" + oldConfig.getKey().getTag(), Status.BAD_REQUEST);
                }
            }

            try (Transaction t = hive.getTransactions().begin()) {
                if (configUpdates != null && !configUpdates.isEmpty()) {
                    // export existing tree and apply updates.
                    // set/reset config tree ID on instanceConfig.
                    instanceConfig.configTree = applyConfigUpdates(instanceConfig.configTree,
                            p -> applyUpdates(configUpdates, p));
                }

                // calculate target key.
                String rootTag = this.getNextRootTag(rootName);
                Manifest.Key rootKey = new Manifest.Key(rootName, rootTag);

                if ((state.nodeDtos == null || state.nodeDtos.isEmpty()) && oldConfig != null) {
                    // no new node config - re-apply existing one with new tag, align redundant
                    // fields.
                    state.nodeDtos = readExistingNodeConfigs(oldConfig);
                }

                // does NOT validate that the product exists, as it might still reside on the
                // central server, not this one.

                SortedMap<String, InstanceNodeConfiguration> nodeMap = new TreeMap<>();
                if (state.nodeDtos != null) {
                    state.nodeDtos.forEach(n -> nodeMap.put(n.nodeName, updateControlGroups(n.nodeConfiguration)));
                }

                Key newInstanceVersionKey = createInstanceVersion(rootKey, state.config, nodeMap);

                if (root.getMode() == MinionMode.MANAGED) {
                    try {
                        sendConfigurationChanges(newInstanceVersionKey, instanceConfig);
                    } catch (RuntimeException e) {
                        log.error("Failed to send configuration changes.", e);
                    }
                }

                return newInstanceVersionKey;
            }
        }
    }

    private void sendConfigurationChanges(Key key, InstanceConfiguration cfg) {
        MailSenderSettingsDto settings = root.getSettings().mailSenderSettings;
        if (settings == null || !settings.enabled || StringHelper.isNullOrBlank(settings.receiverAddress)) {
            if (log.isTraceEnabled()) {
                log.trace("Mail sending not enabled or receiver unset, not sending update for {}", key);
            }
            return;
        }

        if (StringHelper.isNullOrBlank(settings.managedServerName)) {
            log.warn("No managed server name configured, cannot send mail");
            return;
        }

        String uniqueId = AttachmentUtils.getAttachmentNameFromData(name, cfg.id, settings.managedServerName);
        Path targetFile = root.getTempDir().resolve("mail-" + uniqueId + "-" + key.getTag() + ".zip");
        URI targetUri = targetFile.toUri();

        PushOperation pushOp = new PushOperation().setRemote(new RemoteService(targetUri));
        // we need to push:
        // 1) ALL manifests starting with the instance ID (root, nodes, etc.) with tag matching key.tag
        hive.execute(new ManifestListOperation().setManifestName(cfg.id)).stream().filter(k -> k.getTag().equals(key.getTag()))
                .forEach(pushOp::addManifest);

        // 2) ALL meta-manifests starting with the instance ID - the *latest* of each *would* be enough, but we're keeping it simple and send all of them.
        hive.execute(new ManifestListOperation().setManifestName(MetaManifest.META_PREFIX + cfg.id)).forEach(pushOp::addManifest);

        // 3) In case the instance is part of a system, send the system configuration as well.
        if (cfg.system != null) {
            pushOp.addManifest(cfg.system);
        }

        hive.execute(pushOp);

        byte[] allBytes;
        String contentType;
        try {
            allBytes = Files.readAllBytes(targetFile);
            contentType = Files.probeContentType(targetFile);
        } catch (IOException e) {
            log.error("Parsing failed.", e);
            return;
        } finally {
            PathHelper.deleteIfExistsRetry(targetFile);
        }

        sendMail(MinionRoot.MAIL_SUBJECT_PATTERN_CONFIG_OF + uniqueId,// subject
                "Instance group ID: " + name + "\n"// text
                        + "Instance ID: " + cfg.id + "\n"//
                        + "Datetime: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss +S")),//
                MediaType.TEXT_PLAIN,// MIME type of text
                uniqueId + ".zip",// name of attachment
                allBytes,// attachment as byte[]
                contentType// MIME type of attachment
        );
    }

    private void sendMail(String subject, String text, String textMimeType, String attachmentName, byte[] attachment,
            String attachmentMimeType) {
        MailSenderSettingsDto mailSenderSettingsDto = root.getSettings().mailSenderSettings;

        InternetAddress senderAddress = StringHelper.isNullOrBlank(mailSenderSettingsDto.senderAddress) ? null
                : MessagingUtils.checkAndParseAddress(mailSenderSettingsDto.senderAddress);
        InternetAddress receiverAddress = MessagingUtils.checkAndParseAddress(mailSenderSettingsDto.receiverAddress);

        // encode the attachment as base64, and sign the whole thing.
        MinionSignedAttachment signed = new MinionSignedAttachment(attachmentName, attachment, attachmentMimeType);

        MessageDataHolder dataHolder = new MessageDataHolder(senderAddress, List.of(receiverAddress), subject, text, textMimeType,
                List.of(new MimeFile(signed.getSignedName(), signed.getSigned(root), MinionSignedAttachment.SIGNED_MIME_TYPE)));

        mailSender.send(dataHolder);
    }

    private String getNextRootTag(String rootName) {
        Long next = hive.execute(new ManifestNextIdOperation().setManifestName(rootName));

        // Keep incrementing next until number with no historical records is found
        for (int i = 0; i < 100; i++) {
            Manifest.Key key = new Manifest.Key(rootName, next.toString());
            List<InstanceManifestHistoryRecord> events = new InstanceManifestHistory(key, hive).getFullHistory();
            if (events.isEmpty()) {
                return next.toString();
            }
            next++;
        }

        log.warn("Failed to find instance version without historical records. Returning {} ", next);

        return next.toString();
    }

    private static InstanceNodeConfiguration updateControlGroups(InstanceNodeConfiguration nodeConfiguration) {
        if (nodeConfiguration.controlGroups.isEmpty()) {
            // nothing defined yet, fill it with the default - processes in configuration order.
            ProcessControlGroupConfiguration defGrp = new ProcessControlGroupConfiguration();
            defGrp.processOrder.addAll(nodeConfiguration.applications.stream().map(a -> a.id).toList());

            nodeConfiguration.controlGroups.add(defGrp);
        } else {
            // make sure that all processes are in *SOME* group. if not, we try to find or create a default group.
            for (ApplicationConfiguration app : nodeConfiguration.applications) {
                Optional<ProcessControlGroupConfiguration> group = nodeConfiguration.controlGroups.stream()
                        .filter(g -> g.processOrder.contains(app.id)).findAny();
                if (group.isEmpty()) {
                    ProcessControlGroupConfiguration defGrp = nodeConfiguration.controlGroups.stream()
                            .filter(g -> ProcessControlGroupConfiguration.DEFAULT_GROUP.equals(g.name)).findAny()
                            .orElseGet(() -> {
                                ProcessControlGroupConfiguration newDefGrp = new ProcessControlGroupConfiguration();
                                nodeConfiguration.controlGroups.add(0, newDefGrp); // default should be in front.
                                return newDefGrp;
                            });

                    // application not in any group.
                    defGrp.processOrder.add(app.id);
                }
            }
        }

        return nodeConfiguration;
    }

    private ObjectId applyConfigUpdates(ObjectId configTree, Consumer<Path> updater) {
        Path tmpDir = null;
        try {
            tmpDir = Files.createTempDirectory(root.getTempDir(), "cfgUp-");
            Path cfgDir = tmpDir.resolve("cfg");

            // 1. export current tree to temp directory
            exportConfigTree(configTree, cfgDir);

            // 2. apply updates to files
            updater.accept(cfgDir);

            // 3. re-import new tree from temp directory
            return hive.execute(new ImportTreeOperation().setSkipEmpty(true).setSourcePath(cfgDir));
        } catch (IOException e) {
            throw new WebApplicationException("Cannot update configuration files", e);
        } finally {
            if (tmpDir != null) {
                PathHelper.deleteRecursiveRetry(tmpDir);
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

    private static void applyUpdates(List<FileStatusDto> updates, Path cfgDir) {
        for (FileStatusDto update : updates) {
            Path file = cfgDir.resolve(update.file.replace("\\", "/"));
            if (!file.normalize().startsWith(cfgDir)) {
                throw new WebApplicationException("Update wants to write to file outside update directory: " + update.file,
                        Status.BAD_REQUEST);
            }

            try {
                switch (update.type) {
                    case ADD:
                        PathHelper.mkdirs(file.getParent());
                        Files.write(file, Base64.decodeBase64(update.content), StandardOpenOption.CREATE_NEW,
                                StandardOpenOption.SYNC);
                        break;
                    case DELETE:
                        if (!PathHelper.exists(file)) {
                            // this is an invalid operation!
                            throw new IllegalStateException("Cannot delete non-existing path");
                        }
                        PathHelper.deleteIfExistsRetry(file);
                        break;
                    case EDIT:
                        Files.write(file, Base64.decodeBase64(update.content), StandardOpenOption.CREATE,
                                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.SYNC);
                        break;
                }
            } catch (IOException e) {
                throw new WebApplicationException("Cannot apply update to " + update.file, e);
            }
        }
    }

    private List<InstanceNodeConfigurationDto> readExistingNodeConfigs(InstanceManifest oldConfig) {
        List<InstanceNodeConfigurationDto> result = new ArrayList<>();
        for (var entry : oldConfig.getInstanceNodeConfigurations(hive).entrySet()) {
            result.add(new InstanceNodeConfigurationDto(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    @WriteLock
    @Override
    public void delete(String instanceId) {
        try (var handle = af.run(Actions.DELETE_INSTANCE, name, instanceId)) {
            SortedSet<Key> allInstanceObjects = hive.execute(new ManifestListOperation().setManifestName(instanceId));

            // make sure all is uninstalled.
            allInstanceObjects.stream().filter(i -> i.getName().equals(InstanceManifest.getRootName(instanceId)))
                    .forEach(this::uninstall);

            allInstanceObjects.forEach(x -> hive.execute(new ManifestDeleteOperation().setToDelete(x)));

            changes.remove(ObjectChangeType.INSTANCE, allInstanceObjects.first(), new ObjectScope(name, instanceId));
        }
    }

    @Override
    public void deleteVersion(String instanceId, String tag) {
        try (var handle = af.run(Actions.DELETE_INSTANCE_VERSION, name, instanceId, tag)) {
            Manifest.Key key = new Manifest.Key(InstanceManifest.getRootName(instanceId), tag);
            InstanceManifest.of(hive, key).getHistory(hive).recordAction(Action.DELETE, context.getUserPrincipal().getName(),
                    null);
            InstanceManifest.delete(hive, key);

            changes.remove(ObjectChangeType.INSTANCE, key, new ObjectScope(name, instanceId, tag), Map.of("partial", "true"));
        }
    }

    @Override
    public List<RemoteDirectory> getDataDirectorySnapshots(String instanceId) {
        return getDirectorySnapshots(instanceId, ndr -> ndr.getDataDirectoryEntries(instanceId));
    }

    @Override
    public List<RemoteDirectory> getLogDataDirectorySnapshots(String instanceId) {
        return getDirectorySnapshots(instanceId, ndr -> ndr.getLogDataDirectoryEntries(instanceId));
    }

    private List<RemoteDirectory> getDirectorySnapshots(String instanceId,
            Function<NodeDeploymentResource, List<RemoteDirectoryEntry>> extractor) {
        List<RemoteDirectory> result = new CopyOnWriteArrayList<>();

        try (var handle = af.run(Actions.READ_DATA_DIRS, name, instanceId)) {
            String activeTag = getInstanceState(instanceId).activeTag;
            if (activeTag == null) {
                throw new WebApplicationException("Cannot find active version for instance " + instanceId, Status.NOT_FOUND);
            }

            List<Runnable> runnables = new ArrayList<>();
            InstanceStatusDto status = getStatus(instanceId);
            for (String nodeName : status.getNodesWithApps()) {

                runnables.add(() -> {
                    RemoteDirectory idd = new RemoteDirectory();
                    idd.minion = nodeName;
                    idd.id = instanceId;
                    try {
                        MinionDto node = nodes.getSingleOnlineNodeConfig(nodeName);

                        if (node == null) {
                            idd.problem = "Node is offline";
                        } else {
                            List<RemoteDirectoryEntry> iddes = extractor.apply(
                                    ResourceProvider.getVersionedResource(node.remote, NodeDeploymentResource.class, context));
                            idd.entries.addAll(iddes);
                        }
                    } catch (Exception e) {
                        log.warn("Problem fetching directory of {}", nodeName, e);
                        idd.problem = e.toString();
                    }

                    result.add(idd);
                });
            }

            rspos.runAndAwaitAll("Read-Data-Dirs", runnables, hive.getTransactions());
        }

        return result;
    }

    @Override
    public EntryChunk getEntryContent(String nodeName, RemoteDirectoryEntry entry, long offset, long limit) {
        return nodes.getNodeResourceIfOnlineOrThrow(nodeName, CommonDirectoryEntryResource.class, context).getEntryContent(entry,
                offset, limit);
    }

    @Override
    public Response getEntryStream(String nodeName, RemoteDirectoryEntry entry) {
        return nodes.getNodeResourceIfOnlineOrThrow(nodeName, CommonDirectoryEntryResource.class, context).getEntryStream(entry);
    }

    @Override
    public Response getEntriesZipSteam(String nodeName, List<RemoteDirectoryEntry> entries) {
        return nodes.getNodeResourceIfOnlineOrThrow(nodeName, CommonDirectoryEntryResource.class, context)
                .getEntriesZipStream(entries);
    }

    @Override
    public void updateDataEntries(String id, String nodeName, List<FileStatusDto> updates) {
        nodes.getNodeResourceIfOnlineOrThrow(nodeName, NodeDeploymentResource.class, context).updateDataEntries(id, updates);
    }

    @Override
    public void deleteDataEntry(String nodeName, RemoteDirectoryEntry entry) {
        nodes.getNodeResourceIfOnlineOrThrow(nodeName, NodeDeploymentResource.class, context).deleteDataEntry(entry);
    }

    @Override
    public ClientApplicationConfiguration getClientConfiguration(String id, String application) {
        String activeTag = getInstanceState(id).activeTag;
        if (activeTag == null) {
            throw new WebApplicationException("No active deployment for " + id, Status.NOT_FOUND);
        }

        InstanceManifest imf = InstanceManifest.load(hive, id, activeTag);
        InstanceGroupConfiguration groupCfg = new InstanceGroupManifest(hive).read();

        ClientApplicationConfiguration cfg = new ClientApplicationConfiguration();
        cfg.activeTag = activeTag;
        cfg.instanceGroupTitle = groupCfg.title;
        cfg.appConfig = imf.getApplicationConfiguration(hive, application);
        if (cfg.appConfig == null) {
            throw new WebApplicationException("Cannot find application " + application + " in instance " + id, Status.NOT_FOUND);
        }
        cfg.instanceConfig = imf.getInstanceNodeConfiguration(hive, application);

        ProductManifest pmf = ProductManifest.of(hive, imf.getConfiguration().product);
        ApplicationManifest amf = ApplicationManifest.of(hive, cfg.appConfig.application, pmf);
        cfg.appDesc = amf.getDescriptor();

        // application key MUST be a ScopedManifestKey. dependencies /must/ be present
        ScopedManifestKey smk = ScopedManifestKey.parse(cfg.appConfig.application);
        if (smk == null) {
            throw new WebApplicationException("Cannot identify target application: " + cfg.appConfig.application,
                    Status.EXPECTATION_FAILED);
        }
        cfg.resolvedRequires.addAll(
                new LocalDependencyFetcher().fetch(hive, amf.getDescriptor().runtimeDependencies, smk.getOperatingSystem()));

        // load splash screen and icon from hive and send along.
        cfg.clientSplashData = amf.readBrandingSplashScreen(hive);
        cfg.clientImageIcon = amf.readBrandingIcon(hive);

        // set dedicated config tree.
        InstanceNodeManifest inmf = imf.getClientNodeInstanceNodeManifest(hive);
        if (inmf != null) {
            if (inmf != null && !inmf.getConfigTrees().isEmpty()) {
                // we either have a dedicated one, or not :)
                cfg.configTree = inmf.getConfigTrees().get(application);
            }
        }

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
    public void start(String instanceId) {
        try (var handle = af.run(Actions.START_INSTANCE, name, instanceId)) {
            InstanceStatusDto status = getStatus(instanceId);

            // find all nodes where applications are deployed.
            Collection<String> nodesWithApps = status.getNodesWithApps();

            List<Runnable> runnables = new ArrayList<>();
            for (String nodeName : nodesWithApps) {
                runnables.add(() -> {
                    try {
                        nodes.getNodeResourceIfOnlineOrThrow(nodeName, NodeProcessResource.class, context).start(instanceId);
                    } catch (Exception e) {
                        log.error("Cannot start {} on node {}", instanceId, nodeName, e);
                    }
                });
            }
            rspos.runAndAwaitAll("Start-Instance", runnables, hive.getTransactions());
        }
    }

    @Override
    public void start(String instanceId, List<String> applicationIds) {
        try (var handle = af.runMulti(Actions.START_PROCESS, name, instanceId, applicationIds)) {
            InstanceStatusDto status = getStatus(instanceId);
            Map<String, List<String>> groupedByNode = new TreeMap<>();

            for (String applicationId : applicationIds) {
                // Find minion where the application is deployed
                String nodeName = status.getNodeWhereAppIsDeployed(applicationId);
                if (nodeName == null) {
                    throw new WebApplicationException("Application is not deployed on any node: " + applicationId,
                            Status.INTERNAL_SERVER_ERROR);
                }
                groupedByNode.computeIfAbsent(nodeName, n -> new ArrayList<>()).add(applicationId);
            }

            List<Runnable> runnables = new ArrayList<>();
            for (var entry : groupedByNode.entrySet()) {
                // Now launch this application on the node
                runnables.add(() -> nodes.getNodeResourceIfOnlineOrThrow(entry.getKey(), NodeProcessResource.class, context)
                        .start(instanceId, entry.getValue()));
            }
            rspos.runAndAwaitAll("Start-Processes", runnables, hive.getTransactions());
        }
    }

    @Override
    public void stop(String instanceId) {
        try (var handle = af.run(Actions.STOP_INSTANCE, name, instanceId)) {
            InstanceStatusDto status = getStatus(instanceId);

            // Find out all nodes where at least one application is running
            Collection<String> runningNodes = status.getNodesWhereAppsAreRunningOrScheduled();

            List<Runnable> runnables = new ArrayList<>();
            for (String nodeName : runningNodes) {
                runnables.add(() -> {
                    try {
                        nodes.getNodeResourceIfOnlineOrThrow(nodeName, NodeProcessResource.class, context).stop(instanceId);
                    } catch (Exception e) {
                        log.error("Cannot stop {} on node {}", instanceId, nodeName, e);
                    }
                });
            }
            rspos.runAndAwaitAll("Stop-Instance", runnables, hive.getTransactions());
        }
    }

    @Override
    public void stop(String instanceId, List<String> applicationIds) {
        try (var handle = af.runMulti(Actions.STOP_PROCESS, name, instanceId, applicationIds)) {
            InstanceStatusDto status = getStatus(instanceId);
            Map<String, List<String>> groupedByNode = new TreeMap<>();

            for (var applicationId : applicationIds) {
                // Find node where the application is running
                Optional<String> node = status.node2Applications.entrySet().stream()
                        .filter(e -> e.getValue().hasApps() && e.getValue().getStatus(applicationId) != null
                                && e.getValue().getStatus(applicationId).processState != ProcessState.STOPPED)
                        .map(Entry::getKey).findFirst();

                if (node.isEmpty()) {
                    continue; // ignore - not deployed.
                }

                groupedByNode.computeIfAbsent(node.get(), n -> new ArrayList<>()).add(applicationId);
            }

            List<Runnable> runnables = new ArrayList<>();
            for (var entry : groupedByNode.entrySet()) {
                // Now stop the applications on the node
                runnables.add(() -> nodes.getNodeResourceIfOnlineOrThrow(entry.getKey(), NodeProcessResource.class, context)
                        .stop(instanceId, entry.getValue()));
            }
            rspos.runAndAwaitAll("Stop-Processes", runnables, hive.getTransactions());
        }
    }

    @Override
    public RemoteDirectory getOutputEntry(String instanceId, String tag, String applicationId) {
        // master has the instance manifest.
        Manifest.Key instanceKey = new Manifest.Key(InstanceManifest.getRootName(instanceId), tag);
        InstanceManifest imf = InstanceManifest.of(hive, instanceKey);

        for (Map.Entry<String, Manifest.Key> entry : imf.getInstanceNodeManifestKeys().entrySet()) {
            String nodeName = entry.getKey();
            InstanceNodeManifest inmf = InstanceNodeManifest.of(hive, entry.getValue());

            for (ApplicationConfiguration app : inmf.getConfiguration().applications) {
                if (!app.id.equals(applicationId)) {
                    continue;
                }

                // this is our app
                RemoteDirectory id = new RemoteDirectory();
                id.minion = nodeName;
                id.id = instanceId;

                try {
                    RemoteDirectoryEntry oe = nodes.getNodeResourceIfOnlineOrThrow(nodeName, NodeProcessResource.class, context)
                            .getOutputEntry(instanceId, tag, applicationId);

                    if (oe != null) {
                        id.entries.add(oe);
                    }
                } catch (Exception e) {
                    log.warn("Problem fetching output entry from {} for {}, {}, {}", nodeName, instanceId, tag, applicationId, e);
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
        // we don't use the instance manifest to figure out nodes, since node assignment can change over versions.
        // we simply query all nodes, as the alternative would be more expensive, even though local: read all versions
        // of the instance and figure out which nodes could *potentially* have running processes.
        InstanceStatusDto instanceStatus = new InstanceStatusDto(instanceId);

        // all node names regardless of their status.
        Map<String, MinionDto> allNodes = nodes.getAllNodes();

        List<Runnable> actions = new ArrayList<>();
        for (var entry : allNodes.entrySet()) {
            if (entry.getValue().minionNodeType == MinionDto.MinionNodeType.MULTI) {
                continue; // cannot have status.
            }

            String nodeName = entry.getKey();
            MinionDto node = nodes.getSingleOnlineNodeConfig(nodeName);
            if (node == null) {
                continue; // don't log to avoid flooding - node manager will log once.
            }

            actions.add(() -> {
                NodeProcessResource spc = ResourceProvider.getVersionedResource(node.remote, NodeProcessResource.class, context);
                try {
                    InstanceNodeStatusDto nodeStatus = spc.getStatus(instanceId);
                    instanceStatus.add(nodeName, nodeStatus);
                } catch (Exception e) {
                    log.error("Cannot fetch process status of {}", nodeName);
                    if (log.isDebugEnabled()) {
                        log.debug("Exception:", e);
                    }
                }
            });
        }
        rspos.runAndAwaitAll("Node-Process-Status", actions, hive.getTransactions());
        return instanceStatus;
    }

    @Override
    public ProcessDetailDto getProcessDetails(String instanceId, String appId) {
        try (var handle = af.run(Actions.READ_PROCESS_STATUS, name, instanceId, appId)) {
            // Check if the application is running on a node
            InstanceStatusDto status = getStatus(instanceId); // this is super-slow (potentially), thus this method is deprecated.
            String nodeName = status.getNodeWhereAppIsRunning(appId);

            // Check if the application is deployed on a node
            if (nodeName == null) {
                nodeName = status.getNodeWhereAppIsDeployed(appId);
            }

            // Application is nowhere deployed and nowhere running
            if (nodeName == null) {
                return null;
            }

            // Query process details
            return getProcessDetailsFromNode(instanceId, appId, nodeName);
        }
    }

    @Override
    public ProcessDetailDto getProcessDetailsFromNode(String instanceId, String appId, String node) {
        try {
            return nodes.getNodeResourceIfOnlineOrThrow(node, NodeProcessResource.class, context).getProcessDetails(instanceId,
                    appId);
        } catch (Exception e) {
            throw new WebApplicationException("Cannot fetch process status from " + node + " for " + instanceId + ", " + appId,
                    e);
        }
    }

    @Override
    public String generateWeakToken(String principal) {
        return root.createWeakToken(principal);
    }

    @Override
    public void writeToStdin(String instanceId, String applicationId, String data) {
        InstanceStatusDto status = getStatus(instanceId);
        String nodeName = status.getNodeWhereAppIsRunning(applicationId);
        if (nodeName == null) {
            throw new WebApplicationException("Application is not running on any node.", Status.INTERNAL_SERVER_ERROR);
        }

        nodes.getNodeResourceIfOnlineOrThrow(nodeName, NodeProcessResource.class, context).writeToStdin(instanceId, applicationId,
                data);
    }

    @Override
    public Map<Integer, Boolean> getPortStates(String nodeName, List<Integer> ports) {
        return nodes.getNodeResourceIfOnlineOrThrow(nodeName, NodeDeploymentResource.class, context).getPortStates(ports);
    }

    @Override
    public MasterRuntimeHistoryDto getRuntimeHistory(String instanceId) {
        // we don't use the instance manifest to figure out nodes, since node assignment can change over versions. we simply query all nodes for now.
        MasterRuntimeHistoryDto history = new MasterRuntimeHistoryDto();

        // all node names regardless of their status.
        Collection<String> nodeNames = nodes.getAllNodes().entrySet().stream()
                .filter(e -> e.getValue().minionNodeType != MinionDto.MinionNodeType.MULTI).map(e -> e.getKey()).toList();

        List<Runnable> runnables = new ArrayList<>();
        for (String nodeName : nodeNames) {
            runnables.add(() -> {
                try {
                    history.add(nodeName, nodes.getNodeResourceIfOnlineOrThrow(nodeName, NodeProcessResource.class, context)
                            .getRuntimeHistory(instanceId));
                } catch (Exception e) {
                    history.addError(nodeName, "Cannot load runtime history (" + e.toString() + ")");
                }
            });
        }

        rspos.runAndAwaitAll("Runtime-History", runnables, hive.getTransactions());

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

        im.getHistory(hive).recordAction(instanceBannerRecord.text != null ? Action.BANNER_SET : Action.BANNER_CLEAR,
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

    @Override
    public void updateOverallStatus() {
        SortedSet<Key> imKeys = InstanceManifest.scan(hive, true);
        Map<String, MinionStatusDto> nodeStatus = nodes.getAllNodeStatus();

        try (var handle = af.run(Actions.UPDATE_OVERALL_STATUS, name)) {
            for (Key imKey : imKeys) {
                InstanceManifest im = InstanceManifest.of(hive, imKey);

                if (im.getState(hive).read().activeTag == null) {
                    continue; // no active tag means there cannot be any status.
                }

                InstanceConfiguration config = im.getConfiguration();

                // get all node status of the responsible master.
                InstanceStatusDto processStatus = getStatus(config.id);
                List<InstanceNodeConfigurationDto> nodeConfigs = readExistingNodeConfigs(im);

                int stoppedApps = 0;
                int runningWithProbeApps = 0;
                int runningWithoutProbeApps = 0;
                int transitioningApps = 0;

                OverallStatus overallStatus = OverallStatus.RUNNING;
                List<String> overallStatusMessages = new ArrayList<>();

                for (var nodeCfg : nodeConfigs) {
                    if (nodeCfg.nodeConfiguration.nodeType == NodeType.CLIENT) {
                        continue; // don't check client.
                    }

                    MinionStatusDto state = nodeStatus.get(nodeCfg.nodeName);

                    if (state == null || state.offline) {
                        overallStatus = InstanceOverallStateRecord.OverallStatus.WARNING;
                        overallStatusMessages.add("Node " + nodeCfg.nodeName + " is not available");
                        continue;
                    }

                    InstanceNodeStatusDto statusOnNode = processStatus.node2Applications.get(nodeCfg.nodeName);

                    for (var app : nodeCfg.nodeConfiguration.applications) {
                        if (app.processControl.startType != ApplicationStartType.INSTANCE) {
                            continue;
                        }

                        if (!statusOnNode.isAppDeployed(app.id)) {
                            log.warn("Expected application is not currently deployed: {}", app.id);
                            continue;
                        }

                        // instance application, check status
                        ProcessStatusDto status = statusOnNode.getStatus(app.id);
                        switch (status.processState) {
                            case RUNNING, RUNNING_UNSTABLE:
                                runningWithProbeApps++;
                                break;
                            case RUNNING_NOT_ALIVE:
                                runningWithoutProbeApps++;
                                break;
                            case STOPPED, CRASHED_PERMANENTLY:
                                stoppedApps++;
                                break;
                            case RUNNING_STOP_PLANNED, RUNNING_NOT_STARTED, CRASHED_WAITING, STOPPED_START_PLANNED:
                                transitioningApps++;
                                break;
                        }
                    }
                }

                // logic for state determination:
                // * RUNNING: all applications of type `INSTANCE` are running.
                // * STOPPED: all applications of type `INSTANCE` are stopped.
                // * WARNING: one or more applications of type `INSTANCE` are stopped.
                // * INDETERMINATE: one or more applications of type `INSTANCE` are starting or stopping.
                boolean hasStoppedApps = stoppedApps > 0;
                boolean hasRunningWithProbeApps = runningWithProbeApps > 0;
                boolean hasRunningWithoutProbeApps = runningWithoutProbeApps > 0;
                boolean hasTransitioningApps = transitioningApps > 0;

                if (!hasStoppedApps && !hasRunningWithProbeApps && !hasRunningWithoutProbeApps && !hasTransitioningApps) {
                    // valid - this means that there are no instance type applications on the instance -> stopped
                    if (overallStatus != OverallStatus.WARNING) {
                        overallStatus = OverallStatus.STOPPED;
                    }
                } else if (hasStoppedApps && !hasRunningWithoutProbeApps && !hasRunningWithProbeApps && !hasTransitioningApps) {
                    // valid - all stopped, nothing running, nothing transitioning -> stopped
                    if (overallStatus != OverallStatus.WARNING) {
                        overallStatus = OverallStatus.STOPPED;
                    }
                } else if (hasRunningWithProbeApps && !hasRunningWithoutProbeApps && !hasStoppedApps && !hasTransitioningApps) {
                    // valid - nothing stopped, nothing transitioning, no liveness issues -> running
                    if (overallStatus != OverallStatus.WARNING) {
                        overallStatus = OverallStatus.RUNNING;
                    }
                } else if (hasTransitioningApps) {
                    // some apps are transitioning -> indeterminate
                    if (overallStatus != OverallStatus.WARNING) {
                        overallStatus = OverallStatus.INDETERMINATE;
                        overallStatusMessages.add(transitioningApps + " instance type application"
                                + (transitioningApps == 1 ? " is" : "s are") + " in indeterminate state");
                    }
                } else {
                    // not ok, some apps started, some stopped, or a failed liveness probe -> warning.
                    overallStatus = OverallStatus.WARNING;
                    if (hasRunningWithoutProbeApps) {
                        overallStatusMessages.add(runningWithoutProbeApps + " instance type application"
                                + (runningWithoutProbeApps == 1 ? " has" : "s have") + " failed their liveness probe check");
                    }
                    if (hasStoppedApps) {
                        overallStatusMessages.add(stoppedApps + " instance type application"
                                + (stoppedApps == 1 ? " is" : "s are") + " not running");
                    }
                }

                im.getOverallState(hive).update(overallStatus, overallStatusMessages);
            }
        }
    }

    @Override
    public Response getConfigZipSteam(String instanceId, String application) {
        InstanceStateRecord state = getInstanceState(instanceId);
        if (state.activeTag == null) {
            throw new WebApplicationException("Instance has no active tag: " + instanceId, Status.NOT_FOUND);
        }

        InstanceNodeManifest inmf = InstanceManifest.load(hive, instanceId, state.activeTag)
                .getClientNodeInstanceNodeManifest(hive); // only for clients

        if (inmf == null) {
            throw new WebApplicationException("Instance has no client node: " + instanceId, Status.NOT_FOUND);
        }

        ObjectId configTree = inmf.getConfigTrees().get(application);

        if (configTree == null) {
            throw new WebApplicationException(
                    "Application " + application + " in instance " + instanceId + " does not have config files",
                    Status.NOT_FOUND);
        }

        // Build a response with the stream
        var responseBuilder = Response.ok((StreamingOutput) output -> zipConfigTree(output, configTree));

        // Load and attach metadata to give the file a nice name
        var contentDisposition = ContentDisposition.type("attachement").fileName("DataFiles.zip").build();
        responseBuilder.header("Content-Disposition", contentDisposition);
        return responseBuilder.build();
    }

    private void zipConfigTree(OutputStream output, ObjectId configTree) {
        Path tmpDir = null;
        try {
            tmpDir = Files.createTempDirectory(root.getTempDir(), "cfgUp-");
            Path cfgDir = tmpDir.resolve("cfg");

            // 1. export current tree to temp directory
            exportConfigTree(configTree, cfgDir);

            // 2. create ZIP stream.
            ZipHelper.zip(output, cfgDir);
        } catch (IOException e) {
            throw new WebApplicationException("Cannot update configuration files", e);
        } finally {
            if (tmpDir != null) {
                PathHelper.deleteRecursiveRetry(tmpDir);
            }
        }
    }

    @Override
    public MasterSystemResource getSystemResource() {
        return rc.initResource(new MasterSystemResourceImpl(hive, name));
    }

    @Override
    public VerifyOperationResultDto verify(String instanceId, String appId) {
        Map.Entry<String, Manifest.Key> node = getInstanceNodeManifest(instanceId, appId);
        InstanceNodeManifest inm = InstanceNodeManifest.of(hive, node.getValue());
        String item = inm.getConfiguration().applications.stream().filter(a -> a.id.equals(appId))
                .map(a -> a.application.toString()).findFirst().orElseThrow();
        try (var handle = af.run(Actions.VERIFY_APPLICATION, null, null, item)) {
            NodeDeploymentResource ndr = nodes.getNodeResourceIfOnlineOrThrow(node.getKey(), NodeDeploymentResource.class,
                    context);
            return ndr.verify(appId, node.getValue());
        }
    }

    @Override
    public void reinstall(String instanceId, String appId) {
        Map.Entry<String, Manifest.Key> node = getInstanceNodeManifest(instanceId, appId);
        InstanceNodeManifest inm = InstanceNodeManifest.of(hive, node.getValue());
        String item = inm.getConfiguration().applications.stream().filter(a -> a.id.equals(appId))
                .map(a -> a.application.toString()).findFirst().orElseThrow();
        try (var handle = af.run(Actions.REINSTALL_APPLICATION, null, null, item)) {
            NodeDeploymentResource ndr = nodes.getNodeResourceIfOnlineOrThrow(node.getKey(), NodeDeploymentResource.class,
                    context);
            ndr.reinstall(appId, node.getValue());
        }
    }

    private Map.Entry<String, Manifest.Key> getInstanceNodeManifest(String instanceId, String appId) {
        String activeTag = getInstanceState(instanceId).activeTag;
        InstanceManifest imf = InstanceManifest.load(hive, instanceId, activeTag);
        Map.Entry<String, Manifest.Key> node = null;
        for (Map.Entry<String, Manifest.Key> entry : imf.getInstanceNodeManifestKeys().entrySet()) {
            InstanceNodeManifest inm = InstanceNodeManifest.of(hive, entry.getValue());
            boolean found = inm.getConfiguration().applications.stream().anyMatch(a -> a.id.equals(appId));
            if (found) {
                node = entry;
                break;
            }
        }

        // Application is nowhere deployed and nowhere running
        if (node == null) {
            throw new WebApplicationException("Application is not deployed on any node: " + appId, Status.INTERNAL_SERVER_ERROR);
        }
        return node;
    }
}
