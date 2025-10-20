package io.bdeploy.minion.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Arrays;

import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.OsHelper;
import io.bdeploy.interfaces.descriptor.node.MultiNodeMasterFile;
import io.bdeploy.interfaces.minion.MultiNodeDto;
import io.bdeploy.interfaces.minion.NodeSynchronizationStatus;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.minion.TestMinion;
import io.bdeploy.ui.api.NodeManagementResource;
import io.bdeploy.ui.dto.CreateMultiNodeDto;

public class MultiNodeTestActions {

    public static void createMultiNode(RemoteService standaloneSvc, String multiNodeName) {
        // create a multi node configuration on the master
        NodeManagementResource rsrc = ResourceProvider.getResource(standaloneSvc, NodeManagementResource.class, null);
        CreateMultiNodeDto createDto = new CreateMultiNodeDto();
        createDto.config = new MultiNodeDto();
        createDto.config.operatingSystem = OsHelper.getRunningOs();
        createDto.name = multiNodeName;
        rsrc.addMultiNode(createDto);
    }

    public static void attachMultiNodes(RemoteService standaloneSvc, MultiNodeMasterFile masterFile,
            TestMinion.MultiNodeCompletion... runtimeNodes) throws InterruptedException {
        Arrays.stream(runtimeNodes).forEach(nodeCompletion -> nodeCompletion.complete(masterFile));

        // need to wait until synchronization and startup finished. should not take long but still...
        NodeManagementResource rsrc = ResourceProvider.getResource(standaloneSvc, NodeManagementResource.class, null);
        int expectedNumberOfNodes = runtimeNodes.length + 2;
        for (int i = 0; i < 10; i++) {
            var nodes = rsrc.getNodeList().nodes;

            if (nodes.size() != expectedNumberOfNodes || !nodes.values().stream().map(n -> n.nodeSynchronizationStatus)
                    .allMatch(s -> s == NodeSynchronizationStatus.SYNCHRONIZED)) {
                // node manager only updates the sync state every fetchState loop (= 10 seconds).
                Thread.currentThread().sleep(500 * i);
                continue;
            }

            assertEquals(expectedNumberOfNodes, nodes.size());
            return;
        }

        fail();
    }

}
