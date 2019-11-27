/*
 * Copyright (c) SSI Schaefer IT Solutions GmbH
 */
package io.bdeploy.bhive.objects;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.model.Tree;
import io.bdeploy.bhive.model.Tree.EntryType;
import io.bdeploy.bhive.objects.view.ElementView;
import io.bdeploy.bhive.objects.view.ManifestRefView;
import io.bdeploy.bhive.objects.view.MissingObjectView;
import io.bdeploy.bhive.objects.view.TreeView;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.ActivityReporter.Activity;
import io.bdeploy.common.ContentHelper;
import io.bdeploy.common.SlowTest;
import io.bdeploy.common.TempDirectory;
import io.bdeploy.common.TempDirectory.TempDir;
import io.bdeploy.common.TestActivityReporter;

@ExtendWith(TempDirectory.class)
@ExtendWith(TestActivityReporter.class)
public class ObjectManagerTest extends DbTestBase {

    @Test
    public void testImportExport(@TempDir Path tmp, ActivityReporter r) throws IOException {
        Path mySource = ContentHelper.genSimpleTestTree(tmp, "source");
        Path myTarget = tmp.resolve("target");

        ExecutorService s = Executors.newFixedThreadPool(1);
        try {
            ObjectManager mgr = new ObjectManager(getObjectDatabase(), null, r, s);
            ObjectId tree = mgr.importTree(mySource);

            // 2 trees (root, "dir"), 2 blobs (test.txt, file.txt).
            assertThat(getObjectDatabase().getAllObjects().size(), is(4));

            // re-create tree in other directory.
            mgr.exportTree(tree, myTarget, new DefaultReferenceHandler(mgr));

            Path t1 = myTarget.resolve("test.txt");
            Path t2 = myTarget.resolve(Paths.get("dir", "file.txt"));

            assertTrue(Files.exists(t1));
            assertTrue(Files.exists(t2));

            try (InputStream is = mgr.getStreamForRelativePath(tree, "dir", "file.txt")) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
                    assertThat(br.readLine(), is(ContentHelper.T2_L1));
                    assertThat(br.readLine(), is(ContentHelper.T2_L2));
                }
            }
        } finally {
            s.shutdownNow();
        }

    }

    @SlowTest
    @Test
    public void importLarge(@TempDir Path tmp, ActivityReporter r) throws IOException {
        Path mySource = tmp.resolve("src");

        ExecutorService s = Executors.newFixedThreadPool(4);
        try {
            int num1K = 4000;
            int num4K = 2000;
            int num4M = 200;
            int num16M = 20;
            int num64M = 2;
            int num128M = 1;

            Activity genActivity = r.start("Generating test files...");
            ContentHelper.genTestTree(mySource, num1K, num4K, num4M, num16M, num64M, num128M);
            genActivity.done();

            Activity importActivity = r.start("Importing test files...");
            ObjectManager mgr = new ObjectManager(getObjectDatabase(), null, r, s);
            mgr.importTree(mySource);
            importActivity.done();
            System.err.println("Importing took " + importActivity.duration() + "ms.");
        } finally {
            s.shutdownNow();
        }
    }

    @Test
    void testTreeSnapshot(@TempDir Path tmp, ActivityReporter r) throws IOException {
        Path mySource = ContentHelper.genSimpleTestTree(tmp, "source");
        ManifestDatabase mdb = new ManifestDatabase(tmp.resolve("mdb"));

        ExecutorService s = Executors.newFixedThreadPool(1);
        try {
            ObjectManager mgr = new ObjectManager(getObjectDatabase(), mdb, r, s);
            ObjectId tree = mgr.importTree(mySource);

            Manifest.Key refKey = new Manifest.Key("ref", "1");
            mdb.addManifest(new Manifest.Builder(refKey).setRoot(tree).build(null));

            ObjectId testTree = mgr.insertTree(
                    new Tree.Builder().add(new Tree.Key("app", EntryType.MANIFEST), mgr.insertManifestReference(refKey))
                            .add(new Tree.Key("missing", EntryType.BLOB), randomId())
                            .add(new Tree.Key("apptree", EntryType.TREE), tree).build());

            TreeView snapshot = mgr.scan(testTree, Integer.MAX_VALUE, true);

            Map<String, ElementView> l1 = snapshot.getChildren();
            assertTrue(l1.get("app") instanceof ManifestRefView);
            assertEquals(refKey, ((ManifestRefView) l1.get("app")).getReferenced());
            assertTrue(l1.get("missing") instanceof MissingObjectView);
            assertTrue(l1.get("apptree") instanceof TreeView);
            assertIterableEquals(((TreeView) l1.get("app")).getChildren().keySet(),
                    ((TreeView) l1.get("apptree")).getChildren().keySet());

            assertIterableEquals(Arrays.asList("app", "test.txt"),
                    ((TreeView) l1.get("app")).getChildren().get("test.txt").getPath());
        } finally {
            s.shutdownNow();
        }
    }

}
