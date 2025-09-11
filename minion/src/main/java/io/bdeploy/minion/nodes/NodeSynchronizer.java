package io.bdeploy.minion.nodes;

import static io.bdeploy.interfaces.minion.NodeSynchronizationStatus.NOT_SYNCHRONIZED;
import static io.bdeploy.interfaces.minion.NodeSynchronizationStatus.SYNCHRONIZATION_FAILED;
import static io.bdeploy.interfaces.minion.NodeSynchronizationStatus.SYNCHRONIZED;
import static io.bdeploy.interfaces.minion.NodeSynchronizationStatus.SYNCHRONIZING;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.interfaces.configuration.instance.InstanceGroupConfigurationDto;
import io.bdeploy.interfaces.minion.MinionDto;
import io.bdeploy.interfaces.minion.NodeSynchronizationStatus;
import io.bdeploy.interfaces.remote.MasterNamedResource;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.interfaces.remote.NodeSyncResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.ui.api.InstanceGroupResource;
import io.bdeploy.ui.api.InstanceResource;
import io.bdeploy.ui.dto.InstanceDto;

public class NodeSynchronizer {

    private static final Logger log = LoggerFactory.getLogger(NodeSynchronizer.class);

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final MinionDto self;
    private final Map<String, NodeSynchronizationStatus> statusMap;

    public NodeSynchronizer(String selfName, MinionDto self) {
        this.self = self;
        this.statusMap = new TreeMap<>();
        this.statusMap.put(selfName, SYNCHRONIZED);
    }

    public void sync(String nodeName, MinionDto nodeDetails) {
        synchronized (statusMap) {
            if (getStatus(nodeName) == SYNCHRONIZING) {
                // ignore duplicate requests for whatever reason - the running one will eventually set the state.
                return;
            }
            statusMap.put(nodeName, SYNCHRONIZING);
        }

        executor.execute(() -> {
            log.info("Synchronizing node {}", nodeName);
            InstanceGroupResource igr = ResourceProvider.getResource(self.remote, InstanceGroupResource.class, null);
            MasterRootResource mrr = ResourceProvider.getResource(self.remote, MasterRootResource.class, null);
            List<InstanceGroupConfigurationDto> groups = igr.list();
            boolean allSynced = true;
            for (InstanceGroupConfigurationDto group : groups) {
                InstanceResource ir = igr.getInstanceResource(group.instanceGroupConfiguration.name);
                MasterNamedResource mnr = mrr.getNamedMaster(group.instanceGroupConfiguration.name);
                if (!syncInstanceGroup(ir, mnr, group, nodeName)) {
                    allSynced = false;
                    log.warn("Failed to synchronize instance group {} on node {}", group.instanceGroupConfiguration.name,
                            nodeName);
                    break;
                }
            }

            try {
                // independent of the actual status, we tell the node that synchronization is done.
                ResourceProvider.getVersionedResource(nodeDetails.remote, NodeSyncResource.class, null).synchronizationFinished();
            } catch (RuntimeException e) {
                log.info("Cannot inform node about synchronization state: {}", e.toString());
                if (log.isDebugEnabled()) {
                    log.debug("Exception: ", e);
                }
            }

            synchronized (statusMap) {
                statusMap.put(nodeName, allSynced ? SYNCHRONIZED : SYNCHRONIZATION_FAILED);
            }

            log.info("Finished synchronizing node {}. Success={}", nodeName, allSynced);
        });
    }

    private static boolean syncInstanceGroup(InstanceResource ir, MasterNamedResource mnr, InstanceGroupConfigurationDto group,
            String nodeName) {
        List<InstanceDto> instances = ir.list();
        for (InstanceDto instance : instances) {
            try {
                mnr.syncNode(nodeName, instance.instanceConfiguration.id);
            } catch (Exception e) {
                log.warn("Failed to sync node {} for instance {} of group {}", nodeName, instance.instanceConfiguration.id,
                        group.instanceGroupConfiguration.name, e);
                return false;
            }
        }
        return true;
    }

    public NodeSynchronizationStatus getStatus(String nodeName) {
        synchronized (statusMap) {
            return statusMap.getOrDefault(nodeName, NOT_SYNCHRONIZED);
        }
    }
}
