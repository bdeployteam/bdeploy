package io.bdeploy.minion.multi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.minion.TestMinion;
import io.bdeploy.ui.api.BackendInfoResource;
import io.bdeploy.ui.api.MinionMode;
import io.bdeploy.ui.dto.BackendInfoDto;

class MultipleMinionsTest {

    @RegisterExtension
    private final TestMinion exStandalone = new TestMinion(MinionMode.STANDALONE);
    @RegisterExtension
    private final TestMinion exCentral = new TestMinion(MinionMode.CENTRAL);
    @RegisterExtension
    private final TestMinion exManaged = new TestMinion(MinionMode.MANAGED);
    @RegisterExtension
    private final TestMinion exNode1 = new TestMinion(MinionMode.NODE, "1");
    @RegisterExtension
    private final TestMinion exNode2 = new TestMinion(MinionMode.NODE, "2");

    @Test
    void testMinions(@TestMinion.SourceMinion(MinionMode.STANDALONE) RemoteService standaloneSvc,
            @TestMinion.SourceMinion(MinionMode.CENTRAL) RemoteService centralSvc,
            @TestMinion.SourceMinion(MinionMode.MANAGED) RemoteService managedSvc,
            @TestMinion.SourceMinion(value = MinionMode.NODE, disambiguation = "1") RemoteService nodeSvc1,
            @TestMinion.SourceMinion(value = MinionMode.NODE, disambiguation = "2") RemoteService nodeSvc2) {

        assertType(standaloneSvc, MinionMode.STANDALONE);
        assertType(centralSvc, MinionMode.CENTRAL);
        assertType(managedSvc, MinionMode.MANAGED);
        assertType(nodeSvc1, MinionMode.NODE);
        assertType(nodeSvc2, MinionMode.NODE);

        assertNotEquals(nodeSvc1.getUri(), nodeSvc2.getUri());

    }

    void assertType(RemoteService svc, MinionMode mode) {
        BackendInfoDto dto = ResourceProvider.getResource(svc, BackendInfoResource.class, null).getVersion();
        assertEquals(mode, dto.mode);
    }
}
