package io.bdeploy.minion.multi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.configuration.instance.InstanceGroupConfiguration;
import io.bdeploy.interfaces.remote.CommonRootResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.minion.TestMinion;
import io.bdeploy.ui.api.BackendInfoResource;
import io.bdeploy.ui.api.ManagedServersResource;
import io.bdeploy.ui.api.MinionMode;
import io.bdeploy.ui.api.NodeManagementResource;
import io.bdeploy.ui.dto.NodeAttachDto;

class MinionConnectionTest {

    @RegisterExtension
    private final TestMinion central = new TestMinion(MinionMode.CENTRAL);
    @RegisterExtension
    private final TestMinion managed = new TestMinion(MinionMode.MANAGED);
    @RegisterExtension
    private final TestMinion node = new TestMinion(MinionMode.NODE);

    @Test
    void testCentralManagedConnection(@TestMinion.SourceMinion(MinionMode.CENTRAL) RemoteService centralSvc,
            @TestMinion.SourceMinion(MinionMode.MANAGED) RemoteService managedSvc) {
        // 1. create group on central
        var grp = new InstanceGroupConfiguration();
        grp.name = "Test";
        grp.title = "TestTitle";
        var root = ResourceProvider.getResource(centralSvc, CommonRootResource.class, null);
        root.addInstanceGroup(grp, root.getStorageLocations().iterator().next());

        // 2. download managed id
        var managedId = ResourceProvider.getResource(managedSvc, BackendInfoResource.class, null)
                .getManagedMasterIdentification();

        // 3. connect via auto-attach
        ResourceProvider.getResource(centralSvc, ManagedServersResource.class, null).tryAutoAttach("Test", managedId);

        // 4. verify group exists on managed
        var grps = ResourceProvider.getResource(managedSvc, CommonRootResource.class, null).getInstanceGroups();
        assertEquals(1, grps.size());
        assertEquals("Test", grps.getFirst().name);
    }

    @Test
    void testManagedNodeConnection(@TestMinion.SourceMinion(MinionMode.MANAGED) RemoteService managedSvc,
            @TestMinion.SourceMinion(MinionMode.NODE) RemoteService nodeSvc) {

        var nodeMgmt = ResourceProvider.getResource(managedSvc, NodeManagementResource.class, null);

        NodeAttachDto dto = new NodeAttachDto();
        dto.name = "TestNode";
        dto.sourceMode = MinionMode.NODE;
        dto.remote = nodeSvc;

        nodeMgmt.addServerNode(dto);
        var nodes = nodeMgmt.getNodeList().nodes;

        assertEquals(2, nodes.size()); // includes "master" (self)
        assertNotNull(nodes.get("TestNode"));
        assertEquals(nodeSvc.getUri(), nodes.get("TestNode").config.remote.getUri());
    }

}
