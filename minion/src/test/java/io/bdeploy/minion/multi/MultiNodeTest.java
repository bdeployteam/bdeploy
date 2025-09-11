package io.bdeploy.minion.multi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.OsHelper;
import io.bdeploy.interfaces.descriptor.node.MultiNodeMasterFile;
import io.bdeploy.interfaces.minion.MinionDto;
import io.bdeploy.interfaces.minion.MultiNodeDto;
import io.bdeploy.interfaces.minion.NodeSynchronizationStatus;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.minion.TestMinion;
import io.bdeploy.minion.TestMinion.MultiNodeCompletion;
import io.bdeploy.minion.TestMinion.MultiNodeMaster;
import io.bdeploy.minion.TestMinion.SourceMinion;
import io.bdeploy.ui.api.MinionMode;
import io.bdeploy.ui.api.NodeManagementResource;
import io.bdeploy.ui.dto.CreateMultiNodeDto;

public class MultiNodeTest {

    private static final String MULTINODE_NAME = "multiNode";

    @RegisterExtension
    private final TestMinion exStandalone = new TestMinion(MinionMode.STANDALONE);
    @RegisterExtension
    private final TestMinion exNode1 = new TestMinion(MinionMode.NODE, "1", MinionDto.MinionNodeType.MULTI_RUNTIME);
    @RegisterExtension
    private final TestMinion exNode2 = new TestMinion(MinionMode.NODE, "2", MinionDto.MinionNodeType.MULTI_RUNTIME);

    @Test
    void testMultiNode(@SourceMinion(MinionMode.STANDALONE) RemoteService standaloneSvc,
            @MultiNodeMaster(MULTINODE_NAME) MultiNodeMasterFile masterFile,
            @SourceMinion(value = MinionMode.NODE, disambiguation = "1") MultiNodeCompletion start1,
            @SourceMinion(value = MinionMode.NODE, disambiguation = "1") MultiNodeCompletion start2) throws InterruptedException {
        // create a multi node configuration on the master
        NodeManagementResource rsrc = ResourceProvider.getResource(standaloneSvc, NodeManagementResource.class, null);

        CreateMultiNodeDto createDto = new CreateMultiNodeDto();
        createDto.config = new MultiNodeDto();
        createDto.config.operatingSystem = OsHelper.getRunningOs();
        createDto.name = MULTINODE_NAME;

        rsrc.addMultiNode(createDto);

        // now continue startup and synchronization on the nodes.
        start1.complete(masterFile);
        start2.complete(masterFile);

        // need to wait until synchronization and startup finished. should not take long but still...
        boolean checked = false;
        for(int i = 0; i < 20; i++) {
            var nodes = rsrc.getNodes();

            // first make sure we waited long enough.
            if(nodes.size() != 4 || !nodes.values().stream().map(n -> n.nodeSynchronizationStatus).allMatch(s ->
                    s == NodeSynchronizationStatus.SYNCHRONIZED)) {
                // unfortunately this currently needs to sleep for ~10 seconds in total, since the node manager only
                // updates the sync state ever fetchState loop (= 10 seconds).
                Thread.currentThread().sleep(100 * i);
                continue;
            }

            // we expect 4 nodes in the result. the master, the "virtual" multi-node, and two multi-runtime nodes which are in
            // sync. synchronization is super fast as there are no instance groups on the test server.
            assertEquals(4, nodes.size());

            for(var entry : nodes.entrySet()) {
                if(entry.getKey().equals("master")) {
                    assertTrue(entry.getValue().config.master);
                    assertEquals(MinionDto.MinionNodeType.SERVER, entry.getValue().config.minionNodeType);
                } else if(entry.getKey().equals(MULTINODE_NAME)) {
                    assertEquals(MinionDto.MinionNodeType.MULTI, entry.getValue().config.minionNodeType);
                } else {
                    assertEquals(MinionDto.MinionNodeType.MULTI_RUNTIME, entry.getValue().config.minionNodeType);
                }
            }

            checked = true;
            break;
        }

        assertTrue(checked, "Cannot check nodes as synchronization never finished!");
    }
}
