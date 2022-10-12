package io.bdeploy.minion.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import io.bdeploy.common.util.Threads;
import io.bdeploy.interfaces.manifest.history.InstanceManifestHistory;
import io.bdeploy.interfaces.manifest.history.InstanceManifestHistory.Action;
import io.bdeploy.interfaces.manifest.history.InstanceManifestHistoryRecord;
import io.bdeploy.interfaces.remote.CommonRootResource;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.minion.TestFactory;
import io.bdeploy.minion.TestMinion;

@ExtendWith(TestHive.class)
@ExtendWith(TestMinion.class)
class InstanceManifestHistoryTest {

    /**
     * Finds the timestamp of the most recent record of the given {@link Action}.
     *
     * @param action the action to look up.
     * @return the record, or <code>null</code> if none has been found.
     */
    public static InstanceManifestHistoryRecord findMostRecent(InstanceManifestHistory history, Action action) {
        return history.getFullHistory().stream().filter(a -> a.action == action)
                .sorted((a, b) -> Long.compare(b.timestamp, a.timestamp)).findFirst().orElse(null);
    }

    @Test
    void writeRead(@TempDir Path tmp, MinionRoot root, CommonRootResource master, RemoteService remote, BHive local)
            throws IOException, InterruptedException {
        Key instanceKey = TestFactory.createApplicationsAndInstance(local, master, remote, tmp, false);
        InstanceManifestHistory history = new InstanceManifestHistory(instanceKey, local);

        long old = System.currentTimeMillis();
        history.recordAction(Action.INSTALL, "test", "comment1");
        assertTrue(findMostRecent(history, Action.INSTALL).timestamp >= old);
        assertEquals("comment1", findMostRecent(history, Action.INSTALL).comment);

        Threads.sleep(10);
        long now = System.currentTimeMillis();
        history.recordAction(Action.INSTALL, "test", "comment2");
        assertTrue(findMostRecent(history, Action.INSTALL).timestamp >= now);
        assertEquals("comment2", findMostRecent(history, Action.INSTALL).comment);
    }

}
