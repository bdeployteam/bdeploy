package io.bdeploy.bhive.objects.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.LongAdder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.BHiveTransactions.Transaction;
import io.bdeploy.bhive.TestHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.objects.ObjectManager.DbCallable;
import io.bdeploy.bhive.objects.view.TreeView;
import io.bdeploy.bhive.objects.view.scanner.TreeDiff;
import io.bdeploy.bhive.objects.view.scanner.TreeElementDiff;
import io.bdeploy.bhive.objects.view.scanner.TreeVisitor;
import io.bdeploy.bhive.op.ImportOperation;
import io.bdeploy.bhive.op.ScanOperation;
import io.bdeploy.common.ContentHelper;
import io.bdeploy.common.util.PathHelper;

@ExtendWith(TestHive.class)
public class SnapshotAndDiffTest {

    @Test
    void scannerTest(@TempDir Path tmp, BHive hive) throws IOException {
        Path plain = ContentHelper.genSimpleTestTree(tmp, "s1");
        Path newFile = ContentHelper.genSimpleTestTree(tmp, "s2");
        Path rmFile = ContentHelper.genSimpleTestTree(tmp, "s3");
        Path chFile = ContentHelper.genSimpleTestTree(tmp, "s4");
        Path file2Dir = ContentHelper.genSimpleTestTree(tmp, "s5");

        Files.write(newFile.resolve("new.txt"), Arrays.asList("Test Content"));
        Files.delete(rmFile.resolve("test.txt"));
        Files.write(chFile.resolve("text.txt"), Arrays.asList("Changed Content"));
        Files.delete(file2Dir.resolve("test.txt"));
        PathHelper.mkdirs(file2Dir.resolve("test.txt"));
        Files.write(file2Dir.resolve("test.txt/file.txt"), Arrays.asList("New nested content"));

        Manifest.Key keyPlain = new Manifest.Key("k", "1");
        Manifest.Key keyNew = new Manifest.Key("k", "2");
        Manifest.Key keyRm = new Manifest.Key("k", "3");
        Manifest.Key keyCh = new Manifest.Key("k", "4");
        Manifest.Key keyF2d = new Manifest.Key("k", "5");

        try (Transaction t = hive.getTransactions().begin()) {
            hive.execute(new ImportOperation().setManifest(keyPlain).setSourcePath(plain));
            hive.execute(new ImportOperation().setManifest(keyNew).setSourcePath(newFile));
            hive.execute(new ImportOperation().setManifest(keyRm).setSourcePath(rmFile));
            hive.execute(new ImportOperation().setManifest(keyCh).setSourcePath(chFile));
            hive.execute(new ImportOperation().setManifest(keyF2d).setSourcePath(file2Dir));
        }

        TreeView snPlain = hive.execute(new ScanOperation().setManifest(keyPlain));
        TreeView snNew = hive.execute(new ScanOperation().setManifest(keyNew));
        TreeView snRm = hive.execute(new ScanOperation().setManifest(keyRm));
        TreeView snCh = hive.execute(new ScanOperation().setManifest(keyCh));
        TreeView snF2d = hive.execute(new ScanOperation().setManifest(keyF2d));

        List<TreeElementDiff> diffsPlain2New = new TreeDiff(snPlain, snNew).diff();
        List<TreeElementDiff> diffsPlain2Rm = new TreeDiff(snPlain, snRm).diff();
        List<TreeElementDiff> diffsPlain2Ch = new TreeDiff(snPlain, snCh).diff();
        List<TreeElementDiff> diffsPlain2F2d = new TreeDiff(snPlain, snF2d).diff();

        // always includes a content diff for the root tree
        assertEquals(2, diffsPlain2New.size());
        assertEquals(2, diffsPlain2Rm.size());
        assertEquals(2, diffsPlain2Ch.size());

        // includes a content diff for the root and the 'test.txt' entry, and the new nested file.
        assertEquals(3, diffsPlain2F2d.size());
    }

    @Test
    void missingTest(@TempDir Path tmp, BHive hive) throws IOException {
        Path src = ContentHelper.genSimpleTestTree(tmp, "s1");
        Manifest.Key key = new Manifest.Key("k", "1");

        try (Transaction t = hive.getTransactions().begin()) {
            hive.execute(new ImportOperation().setManifest(key).setSourcePath(src));
        }
        // remove a blob
        hive.execute(new BHive.Operation<Void>() {

            @Override
            public Void call() throws Exception {
                DbCallable<Void> db = x -> {
                    x.removeObject(ObjectId.parse(ContentHelper.TEST_TXT_OID));
                    return null;
                };
                getObjectManager().db(db);
                return null;
            }
        });

        TreeView snap = hive.execute(new ScanOperation().setManifest(key));
        LongAdder missingCount = new LongAdder();
        LongAdder damageCount = new LongAdder();

        snap.visit(new TreeVisitor.Builder().onDamaged(s -> damageCount.increment()).onMissing(m -> missingCount.increment())
                .build());

        assertEquals(1, missingCount.sum());
        assertEquals(0, damageCount.sum());
    }

    @Test
    void damageTest(@TempDir Path tmp, BHive hive) throws IOException {
        Path src = ContentHelper.genSimpleTestTree(tmp, "s1");
        Manifest.Key key = new Manifest.Key("k", "1");

        try (Transaction t = hive.getTransactions().begin()) {
            hive.execute(new ImportOperation().setManifest(key).setSourcePath(src));
        }

        // remove a tree
        hive.execute(new BHive.Operation<Void>() {

            @Override
            public Void call() throws Exception {
                DbCallable<Void> db = x -> {
                    Path tf = x.getObjectFile(ObjectId.parse(ContentHelper.DIR_TREE_OID));
                    Files.write(tf, Arrays.asList("Bullshit"));
                    return null;
                };
                getObjectManager().db(db);
                return null;
            }
        });

        TreeView snap = hive.execute(new ScanOperation().setManifest(key));
        LongAdder missingCount = new LongAdder();
        LongAdder damageCount = new LongAdder();

        snap.visit(new TreeVisitor.Builder().onDamaged(s -> damageCount.increment()).onMissing(m -> missingCount.increment())
                .build());

        assertEquals(1, missingCount.sum());
        assertEquals(1, damageCount.sum());
    }

    @Test
    void skipTest(@TempDir Path tmp, BHive hive) throws IOException {
        Path src = ContentHelper.genSimpleTestTree(tmp, "s1");
        Manifest.Key key = new Manifest.Key("k", "1");

        try (Transaction t = hive.getTransactions().begin()) {
            hive.execute(new ImportOperation().setManifest(key).setSourcePath(src));
        }
        TreeView snap = hive.execute(new ScanOperation().setManifest(key).setMaxDepth(1));

        LongAdder skipCount = new LongAdder();

        snap.visit(new TreeVisitor.Builder().onSkipped(s -> skipCount.increment()).build());

        assertEquals(1, skipCount.sum());
    }
}
