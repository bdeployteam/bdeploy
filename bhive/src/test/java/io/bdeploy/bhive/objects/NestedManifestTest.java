package io.bdeploy.bhive.objects;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
import io.bdeploy.bhive.objects.view.TreeView;
import io.bdeploy.bhive.objects.view.scanner.TreeVisitor;
import io.bdeploy.bhive.op.ImportOperation;
import io.bdeploy.bhive.op.InsertArtificialTreeOperation;
import io.bdeploy.bhive.op.InsertManifestOperation;
import io.bdeploy.bhive.op.InsertManifestRefOperation;
import io.bdeploy.bhive.op.ManifestDeleteOperation;
import io.bdeploy.bhive.op.ManifestLoadOperation;
import io.bdeploy.bhive.op.ScanOperation;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.ContentHelper;
import io.bdeploy.common.TestActivityReporter;

@ExtendWith(TestActivityReporter.class)
@ExtendWith(TestHive.class)
public class NestedManifestTest extends DbTestBase {

    @Test
    public void nestedManifests(@TempDir Path rootd, ActivityReporter reporter) throws IOException {
        Path tmp = ContentHelper.genSimpleTestTree(rootd, "source");
        Path mdbDir = rootd.resolve("mf");

        ExecutorService s = Executors.newSingleThreadExecutor();
        try {
            ManifestDatabase mdb = new ManifestDatabase(mdbDir);
            ObjectManager om = new ObjectManager(getObjectDatabase(), mdb, reporter, s);

            ObjectId treeId = om.importTree(tmp, false);

            Manifest.Key na = new Manifest.Key("nested-a", "v1");
            Manifest.Key nb = new Manifest.Key("nested-b", "v1");
            Manifest.Key root = new Manifest.Key("root", "v1");

            Manifest.Builder mbr = new Manifest.Builder(root);
            Manifest.Builder amb = new Manifest.Builder(na);
            Manifest.Builder bmb = new Manifest.Builder(nb);

            amb.setRoot(treeId);
            bmb.setRoot(treeId);

            ObjectId aRef = om.insertManifestReference(na);
            ObjectId bRef = om.insertManifestReference(nb);

            Tree rootTree = new Tree.Builder().add(new Tree.Key("nested-a", EntryType.MANIFEST), aRef)
                    .add(new Tree.Key("nested-b", EntryType.MANIFEST), bRef).build();
            mbr.setRoot(om.insertTree(rootTree));

            mdb.addManifest(amb.build(null));
            mdb.addManifest(bmb.build(null));
            mdb.addManifest(mbr.build(null));

            Manifest m = mdb.getManifest(root);

            Path target = rootd.resolve("exp");
            om.exportTree(m.getRoot(), target, null);

            ContentHelper.checkDirsEqual(target.resolve("nested-a"), tmp);
            ContentHelper.checkDirsEqual(target.resolve("nested-b"), tmp);

            SortedMap<String, Manifest.Key> refKeys = new TreeMap<>();
            TreeView scan = om.scan(m.getRoot(), Integer.MAX_VALUE, true);
            scan.visit(new TreeVisitor.Builder().onManifestRef(mfr -> refKeys.put(mfr.getPathString(), mfr.getReferenced()))
                    .build());

            assertThat(refKeys.size(), is(2));
            assertTrue(refKeys.containsKey("nested-a"));
            assertTrue(refKeys.containsKey("nested-b"));
            assertThat(refKeys.get("nested-a"), is(na));
        } finally {
            s.shutdownNow();
        }
    }

    @Test
    public void refCache(BHive hive, @TempDir Path rootd) throws IOException {
        Path tmp = ContentHelper.genSimpleTestTree(rootd, "source");

        Manifest.Key na = new Manifest.Key("nested-a", "v1");
        Manifest.Key nb = new Manifest.Key("nested-b", "v1");
        Manifest.Key root = new Manifest.Key("root", "v1");

        Manifest builtMf;
        try (Transaction t = hive.getTransactions().begin()) {
            hive.execute(new ImportOperation().setManifest(na).setSourcePath(tmp));
            hive.execute(new ImportOperation().setManifest(nb).setSourcePath(tmp));

            Tree.Builder rootTree = new Tree.Builder()
                    .add(new Tree.Key("nested-a", EntryType.MANIFEST),
                            hive.execute(new InsertManifestRefOperation().setManifest(na)))
                    .add(new Tree.Key("nested-b", EntryType.MANIFEST),
                            hive.execute(new InsertManifestRefOperation().setManifest(nb)));

            Manifest.Builder mbr = new Manifest.Builder(root);
            mbr.setRoot(hive.execute(new InsertArtificialTreeOperation().setTree(rootTree)));

            builtMf = mbr.build(hive);
            hive.execute(new InsertManifestOperation().addManifest(builtMf));
        }

        SortedMap<String, Manifest.Key> refKeys = new TreeMap<>();
        TreeVisitor visitor = new TreeVisitor.Builder()
                .onManifestRef(mfr -> refKeys.put(mfr.getPathString(), mfr.getReferenced())).build();

        hive.execute(new ScanOperation().setManifest(root)).visit(visitor);

        assertThat(refKeys.size(), is(2));
        assertTrue(refKeys.containsKey("nested-a"));
        assertTrue(refKeys.containsKey("nested-b"));
        assertThat(refKeys.get("nested-a"), is(na));

        // now check that the cache is equal.
        Manifest rootMf = hive.execute(new ManifestLoadOperation().setManifest(root));
        assertTrue(builtMf == rootMf); // check instance equal since it comes from a cache even after first insert above

        SortedMap<String, Key> cachedRefs = rootMf.getCachedReferences(hive, Integer.MAX_VALUE, false);
        assertTrue(refKeys.equals(cachedRefs));

        Manifest.Key outerKey = new Manifest.Key("outer", "v1");
        try (Transaction t = hive.getTransactions().begin()) {
            // now add another level and check again the same
            Tree.Builder outerTree = new Tree.Builder().add(new Tree.Key("nested-root", EntryType.MANIFEST),
                    hive.execute(new InsertManifestRefOperation().setManifest(root)));

            Manifest.Builder outerBuilder = new Manifest.Builder(outerKey);
            outerBuilder.setRoot(hive.execute(new InsertArtificialTreeOperation().setTree(outerTree)));
            hive.execute(new InsertManifestOperation().addManifest(outerBuilder.build(hive)));
        }

        refKeys.clear();
        hive.execute(new ScanOperation().setManifest(outerKey)).visit(visitor);

        assertThat(refKeys.size(), is(3));
        assertTrue(refKeys.containsKey("nested-root/nested-a"));
        assertTrue(refKeys.containsKey("nested-root/nested-b"));
        assertThat(refKeys.get("nested-root/nested-a"), is(na));
        assertThat(refKeys.get("nested-root"), is(root));

        // now again the cache for double-nested
        assertTrue(hive.execute(new ManifestLoadOperation().setManifest(outerKey))
                .getCachedReferences(hive, Integer.MAX_VALUE, false).equals(refKeys));

        // finally check whether the manifest cache is evicted properly when deleting a manifest
        hive.execute(new ManifestDeleteOperation().setToDelete(root));
        assertThrows(IllegalStateException.class, () -> {
            hive.execute(new ManifestLoadOperation().setManifest(root));
        });
    }
}
