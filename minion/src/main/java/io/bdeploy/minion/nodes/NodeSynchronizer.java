package io.bdeploy.minion.nodes;

import static io.bdeploy.interfaces.minion.NodeSynchronizationStatus.NOT_SYNCHRONIZED;
import static io.bdeploy.interfaces.minion.NodeSynchronizationStatus.SYNCHRONIZATION_FAILED;
import static io.bdeploy.interfaces.minion.NodeSynchronizationStatus.SYNCHRONIZED;
import static io.bdeploy.interfaces.minion.NodeSynchronizationStatus.SYNCHRONIZING;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.interfaces.configuration.instance.InstanceGroupConfigurationDto;
import io.bdeploy.interfaces.minion.MinionDto;
import io.bdeploy.interfaces.minion.NodeSynchronizationStatus;
import io.bdeploy.interfaces.remote.MasterNamedResource;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.ui.api.InstanceGroupResource;
import io.bdeploy.ui.api.InstanceResource;
import io.bdeploy.ui.dto.InstanceDto;

public class NodeSynchronizer {

    private static final Logger log = LoggerFactory.getLogger(NodeSynchronizer.class);

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final MinionDto self;
    private final ConcurrentMap<String, NodeSynchronizationStatus> statusMap;

    public NodeSynchronizer(String selfName, MinionDto self) {
        this.self = self;
        this.statusMap = new ConcurrentHashMap<>();
        this.statusMap.put(selfName, SYNCHRONIZED);
    }

    public void sync(String nodeName) {
        statusMap.put(nodeName, SYNCHRONIZING);
        executor.execute(() -> {
            InstanceGroupResource igr = ResourceProvider.getResource(self.remote, InstanceGroupResource.class, null);
            MasterRootResource mrr = ResourceProvider.getResource(self.remote, MasterRootResource.class, null);
            List<InstanceGroupConfigurationDto> groups = igr.list();
            boolean allSynced = true;
            for (InstanceGroupConfigurationDto group : groups) {
                InstanceResource ir = igr.getInstanceResource(group.instanceGroupConfiguration.name);
                MasterNamedResource mnr = mrr.getNamedMaster(group.instanceGroupConfiguration.name);
                if (!syncInstanceGroup(ir, mnr, group, nodeName)) {
                    allSynced = false;
                    break;
                }
            }

            statusMap.put(nodeName, allSynced ? SYNCHRONIZED : SYNCHRONIZATION_FAILED);
        });
    }

    private boolean syncInstanceGroup(InstanceResource ir, MasterNamedResource mnr, InstanceGroupConfigurationDto group,
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
        return statusMap.getOrDefault(nodeName, NOT_SYNCHRONIZED);
    }
}
