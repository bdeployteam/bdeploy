package io.bdeploy.minion.nodes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.NamedDaemonThreadFactory;
import io.bdeploy.common.util.VersionHelper;
import io.bdeploy.interfaces.manifest.MinionManifest;
import io.bdeploy.interfaces.minion.MinionConfiguration;
import io.bdeploy.interfaces.minion.MinionDto;
import io.bdeploy.interfaces.minion.MinionStatusDto;
import io.bdeploy.interfaces.minion.MultiNodeDto;
import io.bdeploy.interfaces.remote.MinionStatusResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.ui.api.MinionMode;
import io.bdeploy.ui.api.NodeManager;
import io.bdeploy.ui.api.impl.ChangeEventManager;
import io.bdeploy.ui.dto.ObjectChangeDetails;
import io.bdeploy.ui.dto.ObjectChangeHint;
import io.bdeploy.ui.dto.ObjectChangeType;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.SecurityContext;

@Service
public class NodeManagerImpl implements NodeManager, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(NodeManagerImpl.class);

    private MinionRoot root;
    private String self;

    private MinionConfiguration config;
    private NodeSynchronizer nodeSynchronizer;

    private ChangeEventManager changes;

    private final Map<String, Map<String, MinionDto>> multiNodes = new ConcurrentHashMap<>();
    private final Map<String, Boolean> contactWarning = new ConcurrentHashMap<>();
    private final Map<String, MinionStatusDto> status = new ConcurrentHashMap<>();
    private final ScheduledExecutorService schedule = Executors
            .newSingleThreadScheduledExecutor(new NamedDaemonThreadFactory("Scheduled Node Update"));

    private final AtomicLong contactNumber = new AtomicLong(0);
    private final ExecutorService contact = Executors
            .newCachedThreadPool(new NamedDaemonThreadFactory(() -> "Node Contact " + contactNumber.incrementAndGet()));
    private final Map<String, Future<?>> requests = new TreeMap<>();

    private ScheduledFuture<?> saveJob;

    public void initialize(MinionRoot root, boolean initialFetch) {
        if (this.config != null) {
            // re-initialization is ignored. this might happen in complex startup sequences like
            // multi-nodes for example.
            return;
        }

        this.root = root;
        this.config = new MinionManifest(root.getHive()).read();
        this.self = root.getState().self;
        this.nodeSynchronizer = new NodeSynchronizer(this.self, this.config.getMinion(this.self));

        // initially, all nodes are offline.
        this.config.entrySet().forEach(e -> this.status.put(e.getKey(),
                e.getValue().minionNodeType == MinionDto.MinionNodeType.MULTI
                        ? createMultiNodeStatus(e.getKey(), e.getValue())
                        : createStartingStatus(e.getValue())));

        if (root.getMode() == MinionMode.CENTRAL) {
            // no need to periodically fetch states here. However we *do* want to verify connectivity
            // to our own backend once. This is required for things like log file fetching, etc.
            initialFetchNodeStates();
            return;
        }

        // initially, all nodes are marked as "warn on contact failure".
        this.config.entrySet().forEach(e -> this.contactWarning.put(e.getKey(), Boolean.TRUE));
        this.schedule.scheduleAtFixedRate(this::fetchNodeStates, 0, 10, TimeUnit.SECONDS);

        if (initialFetch) {
            log.info("Synchronous initial state fetching in Node Manager...");
            // since this delays startup for synchronous state fetching, we only want this in tests.
            initialFetchNodeStates();

            log.info("... done");
        }
    }

    @Override
    public void close() {
        schedule.shutdownNow();
        contact.shutdownNow();
    }

    private void initialFetchNodeStates() {
        fetchNodeStates();
        this.requests.forEach((n, r) -> {
            try {
                r.get();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.error("Unexpected exception on initial node contact", ie);
            } catch (Exception e) {
                // should never happen
                log.error("Unexpected exception on initial node contact", e);
            }
        });
    }

    private static MinionStatusDto createStartingStatus(MinionDto node) {
        return MinionStatusDto.createOffline(node, "Starting...");
    }

    private MinionStatusDto createMultiNodeStatus(String name, MinionDto node) {
        return MinionStatusDto.createMulti(node, multiNodes.getOrDefault(name, Collections.emptyMap()).size());
    }

    private void fetchNodeStates() {
        if (log.isDebugEnabled()) {
            log.debug("Fetch status of {} minions", getAllNodes().size());
        }

        try {
            for (String minion : getAllNodes().keySet()) {
                // fetch an existing request.
                var existing = requests.get(minion);

                if (existing != null && !existing.isDone()) {
                    // something is already running - we don't start another one.
                    // best case: it finishes "soon" - worst case it is "stuck"
                    if (log.isDebugEnabled()) {
                        log.debug("Status request to {} still running", minion);
                    }
                    continue;
                }

                // start async request to a single minion.
                requests.put(minion, contact.submit(() -> fetchNodeState(minion)));
            }
        } catch (Exception e) {
            log.warn("Unexpected exception while checking node states", e);
        }
    }

    public void synchronizeNode(String node, MinionDto nodeDetails) {
        boolean isStandaloneOrManaged = root.getMode() == MinionMode.MANAGED || root.getMode() == MinionMode.STANDALONE;
        if (!isStandaloneOrManaged) {
            log.warn("Skipping node synchronization - only possible on MANAGED or STANDALONE.");
            return;
        }
        nodeSynchronizer.sync(node, nodeDetails);
    }

    /**
     * @param node the minion to contact. The state is recorded in the status map.
     */
    private void fetchNodeState(String node) {
        MinionDto mdto = getAllNodes().get(node);
        try {
            if (log.isDebugEnabled()) {
                log.debug("Contacting node {}", node);
            }

            // in case the configuration was removed while we were scheduled.
            if (mdto != null) {
                if(mdto.minionNodeType == MinionDto.MinionNodeType.MULTI) {
                    log.debug("Nothing to fetch/synchronize for multi-node configuration {}.", node);
                    return;
                }

                MinionStatusResource msr = ResourceProvider.getResource(mdto.remote, MinionStatusResource.class, null);

                long start = System.currentTimeMillis();
                // this call only grabs in-memory information. even though there is *some* amount, its not much, and should be rather fast.
                // a typical *local* duration for this call is between 100 and 150ms in case there are many things going on in parallel.
                MinionStatusDto msd = msr.getStatus();
                long duration = System.currentTimeMillis() - start;
                if (duration > 250) {
                    log.warn("Slow response from {}: {}ms", node, duration);
                }

                msd.lastRoundtrip = duration;

                boolean isStandaloneOrManaged = root.getMode() == MinionMode.MANAGED || root.getMode() == MinionMode.STANDALONE;
                if (isStandaloneOrManaged && !Objects.equals(node, self) && status.get(node).offline && !msd.offline) {
                    synchronizeNode(node, mdto);
                }
                msd.nodeSynchronizationStatus = nodeSynchronizer.getStatus(node);

                status.put(node, msd);

                // previously inhibited contact warning means node was not reachable. log recovery
                if (Boolean.FALSE.equals(contactWarning.get(node))) {
                    log.info("Node {} connection recovered", node);
                    if (changes != null) {
                        changes.change(ObjectChangeType.NODES,
                                Map.of(ObjectChangeDetails.NODE, node, ObjectChangeDetails.CHANGE_HINT, ObjectChangeHint.STATE));
                    }
                }

                contactWarning.put(node, Boolean.TRUE);

                if (log.isDebugEnabled()) {
                    log.debug("Node {} contacted successfully, offline={}, version={}, info={}", node, msd.offline,
                            msd.config == null ? "unknown" : msd.config.version, msd.infoText);
                }

                // we compare whether some relevant information has changed and schedule saving in case it has.
                if (!VersionHelper.equals(mdto.version, msd.config.version)) {
                    mdto.version = msd.config.version;
                    scheduleSave(); // schedule immediate when configuration changes.
                }
            }
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.debug("Failed to contact {}", node, e);
            }

            if (mdto.minionNodeType == MinionDto.MinionNodeType.MULTI_RUNTIME) {
                removeNode(node);
                return; // no status, no warnings.
            }

            if (e instanceof ProcessingException x) {
                status.put(node, MinionStatusDto.createOffline(mdto, x.getCause().toString()));
            } else {
                status.put(node, MinionStatusDto.createOffline(mdto, e.toString()));
            }

            // log it, we don't want to hold status in the futures.
            if (Boolean.TRUE.equals(contactWarning.get(node))) {
                contactWarning.put(node, Boolean.FALSE); // no warning, contact failed.
                log.warn("Failed to fetch node {} status: {}", node, e.toString());
                if (changes != null) {
                    changes.change(ObjectChangeType.NODES,
                            Map.of(ObjectChangeDetails.NODE, node, ObjectChangeDetails.CHANGE_HINT, ObjectChangeHint.STATE));
                }
            }
        }
    }

    @Override
    public Map<String, MinionDto> getAllNodes() {
        List<Map<String, MinionDto>> allNodes = new ArrayList<>();
        allNodes.add(config.minionMap());
        allNodes.addAll(multiNodes.values());
        Map<String, MinionDto> allCombined = allNodes.stream().flatMap(m -> m.entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return Collections.unmodifiableMap(allCombined);
    }

    @Override
    public Map<String, MinionDto> getMultiNodeRuntimeNodes(String name) {
        return Collections.unmodifiableMap(multiNodes.get(name));
    }

    @Override
    public String getMultiNodeConfigNameForRuntimeNode(String runtimeNode) {
        for (var entry : multiNodes.entrySet()) {
            if (entry.getValue().containsKey(runtimeNode)) {
                return entry.getKey();
            }
        }
        throw new RuntimeException("Multi-node runtime node " + runtimeNode + " not found.");
    }

    @Override
    public Map<String, MinionStatusDto> getAllNodeStatus() {
        return Collections.unmodifiableMap(status);
    }

    @Override
    public MinionDto getNodeConfig(String name) {
        return getAllNodes().get(name);
    }

    @Override
    public MinionStatusDto getNodeStatus(String name) {
        return status.get(name);
    }

    @Override
    public Map<String, MinionDto> getOnlineNodeConfigs(String name) {
        var state = status.get(name);
        if (state == null) {
            return Collections.emptyMap();
        }

        if (state.config.minionNodeType == MinionDto.MinionNodeType.MULTI) {
            // figure out the actually connected runtime nodes here.
            return multiNodes.getOrDefault(name, Collections.emptyMap()).entrySet().stream()
                    .filter(e -> !status.get(e.getKey()).offline)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }

        if (state.offline) {
            // there might be a connection request running which *may* return,
            // however we cannot do anything about unlucky timing and even a
            // small wait here might slow down things *significantly* in case
            // of many nodes.
            return Collections.emptyMap();
        }
        return Map.of(name, state.config);
    }

    @Override
    public MinionDto getSingleOnlineNodeConfig(String name) {
        var state = status.get(name);
        if (state == null) {
            return null;
        }

        if (state.config.minionNodeType == MinionDto.MinionNodeType.MULTI) {
            throw new RuntimeException("Multi-node cannot be guaranteed to only have a single node");
        }

        if (state.offline) {
            return null;
        }
        return state.config;
    }

    @Override
    public <T> T getNodeResourceIfOnlineOrThrow(String minion, Class<T> clazz, SecurityContext context) {
        MinionDto node = getAllNodes().get(minion);
        if (node == null) {
            throw new WebApplicationException("Node not known " + minion, Status.EXPECTATION_FAILED);
        }
        if (node.minionNodeType == MinionDto.MinionNodeType.MULTI) {
            // TODO: this is not possible. callers need to be able to handle multiple resources (all the
            //  attached multi nodes).
            throw new WebApplicationException("Cannot create resource for multi-node", Status.EXPECTATION_FAILED);
        }
        // can only be a single node in case it is not a multi-node
        if (getOnlineNodeConfigs(minion).isEmpty()) {
            throw new WebApplicationException("Node not available " + minion, Status.EXPECTATION_FAILED);
        }
        return ResourceProvider.getVersionedResource(node.remote, clazz, context);
    }

    @Override
    public MinionDto getSelf() {
        return config.getMinion(self);
    }

    @Override
    public String getSelfName() {
        return self;
    }

    private synchronized void scheduleSave() {
        // in case there was one scheduled - cancel it and reschedule.
        if (saveJob != null && !saveJob.isDone()) {
            saveJob.cancel(false);
        }

        // schedule saving after a short timeout to avoid spamming saves.
        saveJob = schedule.schedule(() -> {
            MinionManifest mm = new MinionManifest(root.getHive());
            mm.update(config);
        }, 500, TimeUnit.MILLISECONDS);
    }

    @Override
    public void addNode(String name, MinionDto minion) {
        log.info("Adding node {}", name);

        config.addMinion(name, minion);
        status.put(name, createStartingStatus(minion));
        contactWarning.put(name, Boolean.FALSE); // was not reachable (new), issue recovery log.
        scheduleSave();

        log.info("Updating state for added node {}", name);
        fetchNodeState(name);
    }

    @Override
    public void editNode(String name, RemoteService node) {
        log.info("Editing node {}", name);

        MinionDto m = config.getMinion(name);
        m.remote = node;
        scheduleSave();

        log.info("Updating state for edited node {}", name);
        fetchNodeState(name);
    }

    @Override
    public void removeNode(String name) {
        log.info("Removing node {}", name);

        MinionDto minion = getNodeConfig(name);
        if (minion != null) {
            if (minion.minionNodeType == MinionDto.MinionNodeType.MULTI) {
                var runtimes = multiNodes.get(name);
                if (runtimes != null && !runtimes.isEmpty()) {
                    runtimes.keySet().forEach(this::removeNode);
                }
            } else if (minion.minionNodeType == MinionDto.MinionNodeType.MULTI_RUNTIME) {
                var parent = getMultiNodeConfigNameForRuntimeNode(name);
                if (parent != null) {
                    multiNodes.getOrDefault(parent, Collections.emptyMap()).remove(name);
                    status.put(parent, createMultiNodeStatus(parent, getNodeConfig(parent)));
                }
            }
        }

        config.removeMinion(name);
        status.remove(name);
        contactWarning.remove(name);

        if (minion == null || minion.minionNodeType != MinionDto.MinionNodeType.MULTI_RUNTIME) {
            scheduleSave();
        }
    }

    @Override
    public void setChangeEventManager(ChangeEventManager changes) {
        this.changes = changes;
    }

    @Override
    public void addMultiNode(String name, MultiNodeDto multiNodeDto) {
        log.info("Adding multi-node {}", name);

        MinionDto minion = MinionDto.createMultiNode(multiNodeDto.operatingSystem);
        config.addMinion(name, minion);
        status.put(name, createMultiNodeStatus(name, minion));
        contactWarning.put(name, Boolean.FALSE); // was not reachable (new), issue recovery log.
        scheduleSave();
    }

    @Override
    public void attachMultiNodeRuntime(String name, String runtimeName, MinionDto multiNodeDto) {
        log.info("Attaching multi-node {} to {}", runtimeName, name);

        if (multiNodeDto.minionNodeType != MinionDto.MinionNodeType.MULTI_RUNTIME) {
            throw new RuntimeException("Invalid minion node type " + multiNodeDto.minionNodeType);
        }

        MinionDto hostMultiNode = getAllNodes().get(name);
        if (hostMultiNode == null) {
            throw new RuntimeException("Multi-node " + name + " not found");
        }

        multiNodes.computeIfAbsent(name, k -> new ConcurrentHashMap<>()).put(runtimeName, multiNodeDto);
        status.put(runtimeName, createStartingStatus(multiNodeDto));
        contactWarning.put(runtimeName, Boolean.FALSE);

        // update the status of the hosting configuration node
        status.put(name, createMultiNodeStatus(name, hostMultiNode));

        // no need to save anything -> all ephemeral
        log.info("Updating state for added multi-node {}", runtimeName);
        fetchNodeState(runtimeName);
    }

}
