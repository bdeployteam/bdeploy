package io.bdeploy.minion.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.TestHive;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.common.TempDirectory;
import io.bdeploy.common.TempDirectory.TempDir;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.interfaces.manifest.history.InstanceManifestHistory;
import io.bdeploy.interfaces.manifest.history.InstanceManifestHistory.Action;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.minion.TestFactory;
import io.bdeploy.minion.TestMinion;

@ExtendWith(TestHive.class)
@ExtendWith(TestMinion.class)
@ExtendWith(TempDirectory.class)
public class InstanceManifestHistoryTest {

    @Test
    void writeRead(@TempDir Path tmp, MinionRoot root, MasterRootResource master, RemoteService remote, BHive local)
            throws IOException, InterruptedException {
        Key instanceKey = TestFactory.createApplicationsAndInstance(local, master, remote, tmp, false);
        InstanceManifestHistory history = new InstanceManifestHistory(instanceKey, local);

        long old = System.currentTimeMillis();
        history.record(Action.INSTALL, "test", "comment1");
        assertTrue(history.findMostRecent(Action.INSTALL).timestamp >= old);
        assertEquals("comment1", history.findMostRecent(Action.INSTALL).comment);

        Thread.sleep(10);
        long now = System.currentTimeMillis();
        history.record(Action.INSTALL, "test", "comment2");
        assertTrue(history.findMostRecent(Action.INSTALL).timestamp >= now);
        assertEquals("comment2", history.findMostRecent(Action.INSTALL).comment);
    }

}
