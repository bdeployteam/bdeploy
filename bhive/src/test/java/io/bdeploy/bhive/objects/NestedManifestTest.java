package io.bdeploy.bhive.objects;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.model.Tree;
import io.bdeploy.bhive.model.Tree.EntryType;
import io.bdeploy.bhive.objects.view.TreeView;
import io.bdeploy.bhive.objects.view.scanner.TreeVisitor;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.ContentHelper;
import io.bdeploy.common.TempDirectory;
import io.bdeploy.common.TempDirectory.TempDir;
import io.bdeploy.common.TestActivityReporter;

@ExtendWith(TempDirectory.class)
@ExtendWith(TestActivityReporter.class)
public class NestedManifestTest extends DbTestBase {

    @Test
    public void nestedManifests(@TempDir Path rootd, ActivityReporter reporter) throws IOException {
        Path tmp = ContentHelper.genSimpleTestTree(rootd, "source");
        Path mdbDir = rootd.resolve("mf");

        ExecutorService s = Executors.newSingleThreadExecutor();
        try {
            ManifestDatabase mdb = new ManifestDatabase(mdbDir);
            ObjectManager om = new ObjectManager(getObjectDatabase(), mdb, reporter, s);

            ObjectId treeId = om.importTree(tmp);

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

            mdb.addManifest(amb.build());
            mdb.addManifest(bmb.build());
            mdb.addManifest(mbr.build());

            Manifest m = mdb.getManifest(root);

            Path target = rootd.resolve("exp");
            om.exportTree(m.getRoot(), target, null);

            ContentHelper.checkDirsEqual(target.resolve("nested-a"), tmp);
            ContentHelper.checkDirsEqual(target.resolve("nested-b"), tmp);

            SortedMap<String, Manifest.Key> refKeys = new TreeMap<>();
            TreeView scan = om.scan(m.getRoot());
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

}
