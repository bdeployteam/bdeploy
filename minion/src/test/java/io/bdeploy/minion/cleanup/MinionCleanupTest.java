package io.bdeploy.minion.cleanup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.TestHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.op.ImportOperation;
import io.bdeploy.bhive.op.remote.PushOperation;
import io.bdeploy.bhive.remote.RemoteBHive;
import io.bdeploy.bhive.remote.jersey.JerseyRemoteBHive;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.TempDirectory;
import io.bdeploy.common.TempDirectory.TempDir;
import io.bdeploy.common.TestActivityReporter;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.interfaces.cleanup.CleanupAction;
import io.bdeploy.interfaces.cleanup.CleanupAction.CleanupType;
import io.bdeploy.interfaces.remote.SlaveCleanupResource;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.minion.TestMinion;
import io.bdeploy.pcu.TestAppFactory;

@ExtendWith(TestMinion.class)
@ExtendWith(TestHive.class)
@ExtendWith(TempDirectory.class)
@ExtendWith(TestActivityReporter.class)
public class MinionCleanupTest {

    @Test
    void mfCleanup(BHive local, SlaveCleanupResource scr, RemoteService remote, ActivityReporter reporter, @TempDir Path tmp) {
        // Collect all that is at the beginning of the test in the hive so that we can compare if we
        // the cleanup removes everything that has been created
        SortedMap<Key, ObjectId> inventoryStart = null;
        try (RemoteBHive rbh = RemoteBHive.forService(remote, JerseyRemoteBHive.DEFAULT_NAME, reporter)) {
            inventoryStart = rbh.getManifestInventory();
        }

        Path app1 = TestAppFactory.createDummyApp("test1", tmp);
        Path app2 = TestAppFactory.createDummyApp("test2", tmp);

        Manifest.Key app1key = new Manifest.Key("test1", "1.0");
        Manifest.Key app2key = new Manifest.Key("test2", "1.0");

        local.execute(new ImportOperation().setSourcePath(app1).setManifest(app1key));
        local.execute(new ImportOperation().setSourcePath(app2).setManifest(app2key));

        // shortcut to default hive
        local.execute(new PushOperation().addManifest(app1key).addManifest(app2key).setRemote(remote)
                .setHiveName(JerseyRemoteBHive.DEFAULT_NAME));

        // remote should have both manifests now
        try (RemoteBHive rbh = RemoteBHive.forService(remote, JerseyRemoteBHive.DEFAULT_NAME, reporter)) {
            SortedMap<Key, ObjectId> mfs = rbh.getManifestInventory();

            assertTrue(mfs.containsKey(app1key));
            assertTrue(mfs.containsKey(app2key));
        }

        SortedSet<Manifest.Key> toKeep = new TreeSet<>();
        toKeep.add(app2key);

        // cleanup should find the /not/ mentioned manifest
        List<CleanupAction> actions = scr.cleanup(toKeep);

        assertEquals(1, actions.size());
        assertEquals(CleanupType.DELETE_MANIFEST, actions.get(0).type);
        assertEquals(app1key.toString(), actions.get(0).what);

        // non-immediate cleanup should deliver only the planned actions, so both should still be there.
        try (RemoteBHive rbh = RemoteBHive.forService(remote, JerseyRemoteBHive.DEFAULT_NAME, reporter)) {
            SortedMap<Key, ObjectId> mfs = rbh.getManifestInventory();

            assertTrue(mfs.containsKey(app1key));
            assertTrue(mfs.containsKey(app2key));
        }

        // now actually perform
        scr.perform(actions);

        // now app1 should be gone
        try (RemoteBHive rbh = RemoteBHive.forService(remote, JerseyRemoteBHive.DEFAULT_NAME, reporter)) {
            SortedMap<Key, ObjectId> mfs = rbh.getManifestInventory();

            assertFalse(mfs.containsKey(app1key));
            assertTrue(mfs.containsKey(app2key));
        }

        // now clean the other app immediately
        scr.perform(scr.cleanup(new TreeSet<>()));

        // Check that the hive has nothing in - except the stuff that was in at the beginning
        try (RemoteBHive rbh = RemoteBHive.forService(remote, JerseyRemoteBHive.DEFAULT_NAME, reporter)) {
            SortedMap<Key, ObjectId> mfs = rbh.getManifestInventory();
            mfs.keySet().removeAll(inventoryStart.keySet());
            assertTrue(mfs.isEmpty());
        }
    }

    @Test
    void dirCleanup(SlaveCleanupResource scr, MinionRoot root, @TempDir Path tmp) {
        Path dd = root.getDeploymentDir();
        Path testDir = dd.resolve("123-dummy-abc");

        PathHelper.mkdirs(testDir);

        List<CleanupAction> cleanup = scr.cleanup(new TreeSet<>());
        assertEquals(1, cleanup.size());
        assertEquals(testDir.toString(), cleanup.get(0).what);
        assertEquals("Remove spurious directory (no binary directory found)", cleanup.get(0).description);
        scr.perform(cleanup);

        assertFalse(Files.isDirectory(testDir));

        Path testPoolDir = dd.resolve("pool/dummy-app");
        PathHelper.mkdirs(testPoolDir);

        cleanup = scr.cleanup(new TreeSet<>());
        assertEquals(1, cleanup.size());
        assertEquals(testPoolDir.toString(), cleanup.get(0).what);
        assertEquals("Remove stale pooled application", cleanup.get(0).description);
        scr.perform(cleanup);

        assertFalse(Files.isDirectory(testPoolDir));
    }

}
