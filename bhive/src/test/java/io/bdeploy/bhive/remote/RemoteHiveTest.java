package io.bdeploy.bhive.remote;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeSet;

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
import io.bdeploy.bhive.objects.DbTestBase;
import io.bdeploy.bhive.op.ExportOperation;
import io.bdeploy.bhive.op.ImportFileOperation;
import io.bdeploy.bhive.op.ImportOperation;
import io.bdeploy.bhive.op.InsertArtificialTreeOperation;
import io.bdeploy.bhive.op.InsertManifestOperation;
import io.bdeploy.bhive.op.InsertManifestRefOperation;
import io.bdeploy.bhive.op.ManifestListOperation;
import io.bdeploy.bhive.op.ManifestLoadOperation;
import io.bdeploy.bhive.op.ObjectListOperation;
import io.bdeploy.bhive.op.remote.FetchOperation;
import io.bdeploy.bhive.op.remote.PushOperation;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.ContentHelper;
import io.bdeploy.common.TestActivityReporter;
import io.bdeploy.common.security.RemoteService;

@ExtendWith(TestHive.class)
@ExtendWith(TestActivityReporter.class)
public class RemoteHiveTest extends RemoteHiveTestBase {

    @Test
    public void testRemote(RemoteService svc, BHive hive, @TempDir Path tmp, ActivityReporter r) throws Exception {
        Path src = ContentHelper.genSimpleTestTree(tmp, "src");
        Manifest.Key key = new Manifest.Key("app", "v1");

        // import something
        try (Transaction t = hive.getTransactions().begin()) {
            hive.execute(new ImportOperation().setManifest(key).setSourcePath(src).addLabel("x", "v"));
        }
        Manifest reference = hive.execute(new ManifestLoadOperation().setManifest(key));
        Set<ObjectId> rq = hive.execute(new ObjectListOperation().addManifest(key));

        // Verify that elements are in expected order
        List<ObjectId> ordered = new ArrayList<>(rq);
        assertThat(ordered.get(0).getId(), is(ContentHelper.SUBDIR_TXT_OID));
        assertThat(ordered.get(1).getId(), is(ContentHelper.SUBDIR_TREE_OID));
        assertThat(ordered.get(2).getId(), is(ContentHelper.DIR_TXT_OID));
        assertThat(ordered.get(3).getId(), is(ContentHelper.DIR_TREE_OID));
        assertThat(ordered.get(4).getId(), is(ContentHelper.TEST_TXT_OID));
        assertThat(ordered.get(5).getId(), is(ContentHelper.ROOT_TREE_OID));
        assertThat(ordered.size(), is(6));

        // request all manifests.
        SortedMap<Key, ObjectId> mfs = getRemote().getManifestInventory();
        assertThat(mfs.size(), is(1));
        assertThat(mfs.get(key), is(notNullValue()));
        assertThat(mfs.get(key), is(reference.getRoot()));

        // request objectids. send the root id of the manifest and a random id. the random id must be returned
        SortedSet<ObjectId> testIds = new TreeSet<>();
        testIds.add(reference.getRoot());
        ObjectId randomId = DbTestBase.randomId();
        testIds.add(randomId);
        Set<ObjectId> existing = getRemote().getMissingObjects(testIds);
        assertThat(existing.size(), is(1));
        assertThat(existing.iterator().next(), is(randomId));

        // request all objectIds required for the manifest.
        SortedSet<ObjectId> check = new TreeSet<>();
        check.add(mfs.get(key));
        Set<ObjectId> required = getRemote().getRequiredObjects(check, new TreeSet<>());
        assertThat(required, is(rq));

        Path tmpHive = tmp.resolve("tmphive");
        Path tmpSrc = tmp.resolve("tmpsrc");
        try (BHive other = new BHive(tmpHive.toUri(), null, r)) {
            ContentHelper.genTestFile(tmpSrc, 1024 * 1024 * 40);

            Manifest.Key tmpKey = new Manifest.Key("other", "v1");
            try (Transaction t = other.getTransactions().begin()) {
                other.execute(new ImportOperation().setSourcePath(tmpSrc).setManifest(tmpKey));
            }
            other.execute(new PushOperation().setRemote(svc).addManifest(tmpKey));

            SortedMap<Key, ObjectId> newMfs = getRemote().getManifestInventory();
            assertThat(newMfs.size(), is(2));
            assertThat(newMfs.get(tmpKey), is(notNullValue()));

            SortedMap<Key, ObjectId> scopedInventory = getRemote().getManifestInventory("other");
            assertThat(scopedInventory.size(), is(1));
        }
    }

    @Test
    public void testNestedManifests(RemoteService svc, BHive hive, @TempDir Path tmp, ActivityReporter r) throws IOException {
        Path src1 = ContentHelper.genSimpleTestTree(tmp, "src1");
        Path src2 = ContentHelper.genSimpleTestTree(tmp, "src2");

        ContentHelper.genTestFile(src1, 1024 * 1024);
        ContentHelper.genTestFile(src2, 1024 * 512);

        Path topTmp = tmp.resolve("top");
        Path topLvlFile = ContentHelper.genTestFile(topTmp, 4096);

        Manifest.Key keyN1 = new Manifest.Key("app1", "v1");
        Manifest.Key keyN2 = new Manifest.Key("app2", "v1");
        Manifest.Key keyD = new Manifest.Key("deployment", "v1");

        // import something
        try (Transaction t = hive.getTransactions().begin()) {
            hive.execute(new ImportOperation().setManifest(keyN1).setSourcePath(src1).addLabel("n1", "v"));
            hive.execute(new ImportOperation().setManifest(keyN2).setSourcePath(src2).addLabel("n2", "z"));

            Manifest.Builder rmb = new Manifest.Builder(keyD).addLabel("r", "y");

            Tree.Builder builder = new Tree.Builder();
            builder.add(new Tree.Key("app-install-1", EntryType.MANIFEST),
                    hive.execute(new InsertManifestRefOperation().setManifest(keyN1)));
            builder.add(new Tree.Key("app-install-2", EntryType.MANIFEST),
                    hive.execute(new InsertManifestRefOperation().setManifest(keyN2)));
            builder.add(new Tree.Key("top-lvl", EntryType.BLOB), hive.execute(new ImportFileOperation().setFile(topLvlFile)));

            ObjectId root = hive.execute(new InsertArtificialTreeOperation().setTree(builder));
            hive.execute(new InsertManifestOperation().addManifest(rmb.setRoot(root).build(hive)));
        }

        Path exp = tmp.resolve("exp");
        hive.execute(new ExportOperation().setTarget(exp).setManifest(keyD));
        ContentHelper.checkDirsEqual(exp.resolve("app-install-1"), src1);
        ContentHelper.checkDirsEqual(exp.resolve("app-install-2"), src2);

        assertThat(Files.size(topLvlFile), is(Files.size(exp.resolve("top-lvl"))));

        Path tmpRemote = tmp.resolve("push");
        hive.execute(new PushOperation().addManifest(keyD).setRemote(new RemoteService(tmpRemote.toUri())));

        try (BHive h = new BHive(tmpRemote.toUri(), null, r)) {
            Set<Key> mfs = h.execute(new ManifestListOperation());
            assertThat(mfs.size(), is(3));
            assertTrue(mfs.contains(keyD));
            assertTrue(mfs.contains(keyN1));
            assertTrue(mfs.contains(keyN2));
        }

        tmpRemote = tmp.resolve("fetch");
        try (BHive h = new BHive(tmpRemote.toUri(), null, r); Transaction t = h.getTransactions().begin()) {
            h.execute(new FetchOperation().addManifest(keyD).setRemote(svc));
            Set<Key> mfs = h.execute(new ManifestListOperation());
            assertThat(mfs.size(), is(3));
            assertTrue(mfs.contains(keyD));
            assertTrue(mfs.contains(keyN1));
            assertTrue(mfs.contains(keyN2));
        }
    }

}
