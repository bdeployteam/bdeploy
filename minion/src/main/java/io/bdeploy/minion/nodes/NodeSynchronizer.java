package io.bdeploy.minion.nodes;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.interfaces.configuration.instance.InstanceGroupConfigurationDto;
import io.bdeploy.interfaces.minion.MinionDto;
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

    public NodeSynchronizer(MinionDto self) {
        this.self = self;
    }

    public void sync(String nodeName) {
        executor.execute(() -> {
            InstanceGroupResource igr = ResourceProvider.getResource(self.remote, InstanceGroupResource.class, null);
            MasterRootResource mrr = ResourceProvider.getResource(self.remote, MasterRootResource.class, null);
            List<InstanceGroupConfigurationDto> groups = igr.list();
            for (InstanceGroupConfigurationDto group : groups) {
                InstanceResource ir = igr.getInstanceResource(group.instanceGroupConfiguration.name);
                MasterNamedResource mnr = mrr.getNamedMaster(group.instanceGroupConfiguration.name);
                syncInstanceGroup(ir, mnr, group, nodeName);
            }
        });
    }

    private void syncInstanceGroup(InstanceResource ir, MasterNamedResource mnr, InstanceGroupConfigurationDto group,
            String nodeName) {
        List<InstanceDto> instances = ir.list();
        for (InstanceDto instance : instances) {
            try {
                mnr.syncNode(nodeName, instance.instanceConfiguration.id);
            } catch (Exception e) {
                log.warn("Failed to sync node {} for instance {} of group {}", nodeName, instance.instanceConfiguration.id,
                        group.instanceGroupConfiguration.name, e);
            }
        }
    }
}
