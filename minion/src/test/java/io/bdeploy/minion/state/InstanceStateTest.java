package io.bdeploy.minion.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.TestHive;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.manifest.state.InstanceState;
import io.bdeploy.interfaces.remote.CommonRootResource;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.minion.TestFactory;
import io.bdeploy.minion.TestMinion;

@ExtendWith(TestHive.class)
@ExtendWith(TestMinion.class)
class InstanceStateTest {

    @Test
    void testWriteRead(@TempDir Path tmp, MinionRoot root, CommonRootResource master, RemoteService remote, BHive local)
            throws IOException {
        Key instanceKey = TestFactory.createApplicationsAndInstance(local, master, remote, tmp, false);

        InstanceState state = InstanceManifest.of(local, instanceKey).getState(local);

        assertNull(state.read().activeTag);
        assertEquals(0, state.read().installedTags.size());

        state.install(instanceKey.getTag());

        assertNull(state.read().activeTag);
        assertEquals(1, state.read().installedTags.size());
        assertTrue(state.read().installedTags.contains(instanceKey.getTag()));

        state.activate(instanceKey.getTag());

        assertEquals(instanceKey.getTag(), state.read().activeTag);
        assertEquals(1, state.read().installedTags.size());
        assertTrue(state.read().installedTags.contains(instanceKey.getTag()));

        state.uninstall(instanceKey.getTag());

        assertNull(state.read().activeTag);
        assertEquals(0, state.read().installedTags.size());
    }
}
