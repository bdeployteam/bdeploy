package io.bdeploy.bhive.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import java.util.SortedMap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.BHiveTransactions.Transaction;
import io.bdeploy.bhive.TestHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.model.Tree;
import io.bdeploy.bhive.model.Tree.EntryType;
import io.bdeploy.bhive.op.ImportOperation;
import io.bdeploy.bhive.op.InsertArtificialTreeOperation;
import io.bdeploy.bhive.op.InsertManifestOperation;
import io.bdeploy.bhive.op.InsertManifestRefOperation;
import io.bdeploy.bhive.op.ManifestDeleteOperation;
import io.bdeploy.bhive.op.ManifestListOperation;
import io.bdeploy.bhive.op.remote.FetchOperation;
import io.bdeploy.bhive.op.remote.PushOperation;
import io.bdeploy.bhive.op.remote.TransferStatistics;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.ContentHelper;
import io.bdeploy.common.TestActivityReporter;
import io.bdeploy.common.security.RemoteService;

@ExtendWith(TestHive.class)
@ExtendWith(TestActivityReporter.class)
class FetchPushTest extends RemoteHiveTestBase {

    @Test
    void testRePushWithExistingRemoteRoot(@TempDir Path tmp, RemoteService svc, ActivityReporter r) throws IOException {
        // note: the TestHive.class provided hive is used in the base class, don't use.
        try (BHive local = new BHive(tmp.resolve("h1").toUri(), null, r);
                BHive fetchHive = new BHive(tmp.resolve("h2").toUri(), null, r)) {
            Path src = ContentHelper.genSimpleTestTree(tmp, "app");
            Manifest.Key key = new Manifest.Key("app", "v1");
            try (Transaction t = local.getTransactions().begin()) {
                local.execute(new ImportOperation().setManifest(key).setSourcePath(src));
            }

            // STEP 1: push the manifest to the remote.
            TransferStatistics s = local.execute(new PushOperation().setRemote(svc).addManifest(key));
            assertEquals(1, s.sumManifests);
            assertEquals(3, s.sumTrees);
            assertEquals(3, s.sumMissingTrees);

            // STEP 2: remove manifest remotely, but don't prune -> root tree still exists in the remote
            getRemote().removeManifest(key);

            // STEP 3: push once more.
            s = local.execute(new PushOperation().setRemote(svc).addManifest(key));
            assertEquals(1, s.sumManifests);
            assertEquals(3, s.sumTrees);
            assertEquals(0, s.sumMissingTrees);
            assertEquals(0, s.sumMissingObjects);

            SortedMap<Key, ObjectId> remoteMfs = getRemote().getManifestInventory();
            assertEquals(1, remoteMfs.size());
            assertEquals(key, remoteMfs.keySet().iterator().next());
        }
    }

    @Test
    void testReFetchWithExistingLocalRoot(@TempDir Path tmp, RemoteService svc, ActivityReporter r) throws IOException {
        // note: the TestHive.class provided hive is used in the base class, don't use.
        try (BHive local = new BHive(tmp.resolve("h1").toUri(), null, r);
                BHive fetchHive = new BHive(tmp.resolve("h2").toUri(), null, r)) {
            Path src = ContentHelper.genSimpleTestTree(tmp, "app");
            Manifest.Key key = new Manifest.Key("app", "v1");
            try (Transaction t = local.getTransactions().begin()) {
                local.execute(new ImportOperation().setManifest(key).setSourcePath(src));
            }

            // STEP 1: push the manifest to the remote.
            local.execute(new PushOperation().setRemote(svc).addManifest(key));

            // STEP 2: fetch from remote to another hive
            try (Transaction t = fetchHive.getTransactions().begin()) {
                TransferStatistics s = fetchHive.execute(new FetchOperation().setRemote(svc).addManifest(key));
                assertEquals(1, s.sumManifests);
                assertEquals(3, s.sumTrees);
                assertEquals(3, s.sumMissingTrees);
            }

            // STEP 3: fetch remote manifest again with existing root tree in local
            fetchHive.execute(new ManifestDeleteOperation().setToDelete(key));
            try (Transaction t = fetchHive.getTransactions().begin()) {
                TransferStatistics s = fetchHive.execute(new FetchOperation().setRemote(svc).addManifest(key));
                assertEquals(1, s.sumManifests);
                assertEquals(3, s.sumTrees);
                assertEquals(0, s.sumMissingTrees);
                assertEquals(0, s.sumMissingObjects);
            }

            Set<Key> localMfs = fetchHive.execute(new ManifestListOperation());
            assertEquals(1, localMfs.size());
            assertEquals(key, localMfs.iterator().next());
        }
    }

    @Test
    void testPushWithExistingRemoteLeafTree(@TempDir Path tmp, RemoteService svc, ActivityReporter r) throws IOException {
        // note: the TestHive.class provided hive is used in the base class, don't use.
        try (BHive local = new BHive(tmp.resolve("h1").toUri(), null, r);
                BHive fetchHive = new BHive(tmp.resolve("h2").toUri(), null, r)) {
            Path src = ContentHelper.genSimpleTestTree(tmp, "app");
            Manifest.Key key = new Manifest.Key("app", "v1");
            try (Transaction t = local.getTransactions().begin()) {
                local.execute(new ImportOperation().setManifest(key).setSourcePath(src));
            }

            Manifest.Key subKey = new Manifest.Key("sub", "v1");
            try (Transaction t = local.getTransactions().begin()) {
                local.execute(new ImportOperation().setManifest(subKey).setSourcePath(src.resolve("dir")));
            }

            // STEP 1: push the manifest to the remote.
            TransferStatistics s = local.execute(new PushOperation().setRemote(svc).addManifest(subKey));
            assertEquals(1, s.sumManifests);
            assertEquals(2, s.sumTrees);
            assertEquals(2, s.sumMissingTrees);

            // STEP 2: remove manifest remotely, but don't prune -> root tree still exists in the remote
            getRemote().removeManifest(subKey);

            // STEP 3: push a manifest which has the previous tree as leaf tree
            s = local.execute(new PushOperation().setRemote(svc).addManifest(key));
            assertEquals(1, s.sumManifests);
            assertEquals(3, s.sumTrees);
            assertEquals(1, s.sumMissingTrees);
        }
    }

    @Test
    void testFetchWithExistingRemoteLeafTree(@TempDir Path tmp, RemoteService svc, ActivityReporter r) throws IOException {
        // note: the TestHive.class provided hive is used in the base class, don't use.
        try (BHive local = new BHive(tmp.resolve("h1").toUri(), null, r);
                BHive fetchHive = new BHive(tmp.resolve("h2").toUri(), null, r)) {
            Path src = ContentHelper.genSimpleTestTree(tmp, "app");
            Manifest.Key key = new Manifest.Key("app", "v1");
            try (Transaction t = local.getTransactions().begin()) {
                local.execute(new ImportOperation().setManifest(key).setSourcePath(src));
            }

            Manifest.Key subKey = new Manifest.Key("sub", "v1");
            try (Transaction t = local.getTransactions().begin()) {
                local.execute(new ImportOperation().setManifest(subKey).setSourcePath(src.resolve("dir")));
            }

            // STEP 1: push the manifest(s) to the remote.
            local.execute(new PushOperation().setRemote(svc).addManifest(subKey));
            local.execute(new PushOperation().setRemote(svc).addManifest(key));

            // STEP 2: fetch locally
            try (Transaction t = fetchHive.getTransactions().begin()) {
                TransferStatistics s = fetchHive.execute(new FetchOperation().setRemote(svc).addManifest(subKey));
                assertEquals(1, s.sumManifests);
                assertEquals(2, s.sumTrees);
                assertEquals(2, s.sumMissingTrees);
            }

            // STEP 3: remove manifest locally, but don't prune -> root tree still exists in the hive
            fetchHive.execute(new ManifestDeleteOperation().setToDelete(subKey));

            // STEP 4: fetch manifest which has subKeys root as leaf tree
            try (Transaction t = fetchHive.getTransactions().begin()) {
                TransferStatistics s = fetchHive.execute(new FetchOperation().setRemote(svc).addManifest(key));
                assertEquals(1, s.sumManifests);
                assertEquals(3, s.sumTrees);
                assertEquals(1, s.sumMissingTrees);
            }
        }
    }

    @Test
    void testPushWithPrunedRemoteLeafTree(@TempDir Path tmp, RemoteService svc, ActivityReporter r) throws IOException {
        // note: the TestHive.class provided hive is used in the base class, don't use.
        try (BHive local = new BHive(tmp.resolve("h1").toUri(), null, r)) {
            Path src = ContentHelper.genSimpleTestTree(tmp, "app");
            Manifest.Key key = new Manifest.Key("app", "v1");
            try (Transaction t = local.getTransactions().begin()) {
                local.execute(new ImportOperation().setManifest(key).setSourcePath(src));
            }

            Manifest.Key subKey = new Manifest.Key("sub", "v1");
            try (Transaction t = local.getTransactions().begin()) {
                local.execute(new ImportOperation().setManifest(subKey).setSourcePath(src.resolve("dir")));
            }

            // STEP 1: push the manifest to the remote.
            TransferStatistics s = local.execute(new PushOperation().setRemote(svc).addManifest(subKey));
            assertEquals(1, s.sumManifests);
            assertEquals(2, s.sumTrees);
            assertEquals(2, s.sumMissingTrees);

            // STEP 2: remove manifest remotely, but don't prune -> root tree still exists in the remote
            getRemote().removeManifest(subKey);
            getRemote().prune();

            // STEP 3: push a manifest which has the previous tree as leaf tree
            s = local.execute(new PushOperation().setRemote(svc).addManifest(key));
            assertEquals(1, s.sumManifests);
            assertEquals(3, s.sumTrees);
            assertEquals(3, s.sumMissingTrees);
        }
    }

    @Test
    void testPushFetchWithRefsClean(BHive target, @TempDir Path tmp, RemoteService svc, ActivityReporter r) throws IOException {
        try (BHive local = new BHive(tmp.resolve("h1").toUri(), null, r);
                BHive fetchHive = new BHive(tmp.resolve("h2").toUri(), null, r)) {
            Manifest.Key root = createManifestWithRefs(tmp, local);

            // push the manifest to the remote.
            TransferStatistics s = local.execute(new PushOperation().setRemote(svc).addManifest(root));
            assertEquals(3, s.sumManifests);

            // Check if all manifests arrived correctly.
            assertEquals(1, target.execute(new ManifestListOperation().setManifestName("root")).size());
            assertEquals(1, target.execute(new ManifestListOperation().setManifestName("nested-a")).size());
            assertEquals(1, target.execute(new ManifestListOperation().setManifestName("nested-b")).size());

            // fetch the manifest to the second local hive.
            try (Transaction t = fetchHive.getTransactions().begin()) {
                TransferStatistics fs = fetchHive.execute(new FetchOperation().setRemote(svc).addManifest(root));
                assertEquals(3, fs.sumManifests);
            }

            // Check if all manifests arrived correctly.
            assertEquals(1, fetchHive.execute(new ManifestListOperation().setManifestName("root")).size());
            assertEquals(1, fetchHive.execute(new ManifestListOperation().setManifestName("nested-a")).size());
            assertEquals(1, fetchHive.execute(new ManifestListOperation().setManifestName("nested-b")).size());
        }
    }

    private static Manifest.Key createManifestWithRefs(Path tmp, BHive local) throws IOException {
        Path source = ContentHelper.genSimpleTestTree(tmp, "source");

        Manifest.Key na = new Manifest.Key("nested-a", "v1");
        Manifest.Key nb = new Manifest.Key("nested-b", "v1");
        Manifest.Key root = new Manifest.Key("root", "v1");

        try (Transaction t = local.getTransactions().begin()) {
            local.execute(new ImportOperation().setManifest(na).setSourcePath(source));
            local.execute(new ImportOperation().setManifest(nb).setSourcePath(source));

            Tree.Builder rootTree = new Tree.Builder()
                    .add(new Tree.Key("nested-a", EntryType.MANIFEST),
                            local.execute(new InsertManifestRefOperation().setManifest(na)))
                    .add(new Tree.Key("nested-b", EntryType.MANIFEST),
                            local.execute(new InsertManifestRefOperation().setManifest(nb)));

            Manifest.Builder mbr = new Manifest.Builder(root);
            mbr.setRoot(local.execute(new InsertArtificialTreeOperation().setTree(rootTree)));

            Manifest builtMf = mbr.build(local);
            local.execute(new InsertManifestOperation().addManifest(builtMf));
        }
        return root;
    }

    @Test
    void testPushFetchWithRefsPartial(BHive target, @TempDir Path tmp, RemoteService svc, ActivityReporter r) throws IOException {
        try (BHive local = new BHive(tmp.resolve("h1").toUri(), null, r);
                BHive fetchHive = new BHive(tmp.resolve("h2").toUri(), null, r)) {
            Manifest.Key root = createManifestWithRefs(tmp, local);

            // first push one of the leaves
            Manifest.Key leaf = new Manifest.Key("nested-a", "v1");
            TransferStatistics ps = local.execute(new PushOperation().setRemote(svc).addManifest(leaf));
            assertEquals(1, ps.sumManifests);

            // push the manifest to the remote.
            TransferStatistics s = local.execute(new PushOperation().setRemote(svc).addManifest(root));
            assertEquals(2, s.sumManifests);

            // Check if all manifests arrived correctly.
            assertEquals(1, target.execute(new ManifestListOperation().setManifestName("root")).size());
            assertEquals(1, target.execute(new ManifestListOperation().setManifestName("nested-a")).size());
            assertEquals(1, target.execute(new ManifestListOperation().setManifestName("nested-b")).size());

            // now fetch one of the leaves
            try (Transaction t = fetchHive.getTransactions().begin()) {
                TransferStatistics pfs = fetchHive.execute(new FetchOperation().setRemote(svc).addManifest(leaf));
                assertEquals(1, pfs.sumManifests);
            }

            // fetch the manifest to the second local hive.
            try (Transaction t = fetchHive.getTransactions().begin()) {
                TransferStatistics fs = fetchHive.execute(new FetchOperation().setRemote(svc).addManifest(root));
                assertEquals(2, fs.sumManifests);
            }

            // Check if all manifests arrived correctly.
            assertEquals(1, fetchHive.execute(new ManifestListOperation().setManifestName("root")).size());
            assertEquals(1, fetchHive.execute(new ManifestListOperation().setManifestName("nested-a")).size());
            assertEquals(1, fetchHive.execute(new ManifestListOperation().setManifestName("nested-b")).size());
        }
    }

    @Test
    void testFetchWithRemovedManifest(@TempDir Path tmp, RemoteService svc, ActivityReporter r) {
        try (BHive fetchHive = new BHive(tmp.resolve("hive").toUri(), null, r)) {
            FetchOperation op = new FetchOperation();
            op.setRemote(svc);
            op.addManifest(new Manifest.Key("does-not-exist", "v1"));

            try (Transaction t = fetchHive.getTransactions().begin()) {
                TransferStatistics tx = fetchHive.execute(op);
                assertEquals(0, tx.sumManifests);
            }

            // another case would be that the manifest "disappears" in between the rh.getManifestInventory() call in FetchOperation
            // and the initiation of the actual transfer from the remote (which checks again). However this is not unit testable
            // due to the atomicity of the operation.
        }
    }
}
