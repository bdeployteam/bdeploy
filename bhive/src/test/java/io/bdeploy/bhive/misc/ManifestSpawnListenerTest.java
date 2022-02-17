package io.bdeploy.bhive.misc;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.BHiveTransactions.Transaction;
import io.bdeploy.bhive.ManifestSpawnListener;
import io.bdeploy.bhive.TestHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.model.Tree;
import io.bdeploy.bhive.op.InsertArtificialTreeOperation;
import io.bdeploy.bhive.op.InsertManifestOperation;
import io.bdeploy.common.util.FutureHelper;

@ExtendWith(TestHive.class)
public class ManifestSpawnListenerTest {

    @Test
    void testSpawnListener(BHive test) throws Exception {
        Manifest.Key key1 = new Manifest.Key("test", "1");
        Manifest.Key key2 = new Manifest.Key("test", "2");
        Manifest.Key key3 = new Manifest.Key("test", "3");

        CompletableFuture<Collection<Manifest.Key>> notifyOne = new CompletableFuture<>();
        CompletableFuture<Collection<Manifest.Key>> notifyTwo = new CompletableFuture<>();

        ManifestSpawnListener l1 = (k) -> {
            notifyOne.complete(k);
        };

        test.addSpawnListener(l1);

        try (Transaction t = test.getTransactions().begin()) {
            ObjectId emptyTree = test.execute(new InsertArtificialTreeOperation().setTree(new Tree.Builder()));
            test.execute(new InsertManifestOperation().addManifest(new Manifest.Builder(key1).setRoot(emptyTree).build(test)));
            test.execute(new InsertManifestOperation().addManifest(new Manifest.Builder(key2).setRoot(emptyTree).build(test)));
        }

        Collection<Manifest.Key> keys = notifyOne.get(1, TimeUnit.SECONDS); // await.

        assertTrue(notifyOne.isDone());
        assertEquals(2, keys.size());
        assertTrue(keys.contains(key1));
        assertTrue(keys.contains(key2));

        test.removeSpawnListener(l1);

        ManifestSpawnListener l2 = (k) -> {
            notifyTwo.complete(k);
        };

        test.addSpawnListener(l2);

        try (Transaction t = test.getTransactions().begin()) {
            ObjectId emptyTree = test.execute(new InsertArtificialTreeOperation().setTree(new Tree.Builder()));
            test.execute(new InsertManifestOperation().addManifest(new Manifest.Builder(key3).setRoot(emptyTree).build(test)));
        }

        Collection<Manifest.Key> key = notifyTwo.get(1, TimeUnit.SECONDS); // await.

        assertTrue(notifyTwo.isDone());
        assertEquals(1, key.size());
        assertTrue(key.contains(key3));
    }

    @Test
    void faultyListener(BHive test) {
        Manifest.Key key1 = new Manifest.Key("test", "1");

        ManifestSpawnListener l1 = (k) -> {
            throw new RuntimeException("Ouch");
        };

        test.addSpawnListener(l1);

        assertDoesNotThrow(() -> {
            try (Transaction t = test.getTransactions().begin()) {
                ObjectId emptyTree = test.execute(new InsertArtificialTreeOperation().setTree(new Tree.Builder()));
                test.execute(
                        new InsertManifestOperation().addManifest(new Manifest.Builder(key1).setRoot(emptyTree).build(test)));
            }
        });

    }

    @Test
    void parallelAddStorm(BHive test) throws Exception {
        LongAdder notifyCount = new LongAdder();

        ManifestSpawnListener l1 = (k) -> {
            notifyCount.add(k.size());
        };

        test.addSpawnListener(l1);

        Consumer<String> creator = (name) -> {
            try (Transaction t = test.getTransactions().begin()) {
                Manifest.Key key1 = new Manifest.Key(name, "1");
                ObjectId emptyTree = test.execute(new InsertArtificialTreeOperation().setTree(new Tree.Builder()));
                test.execute(
                        new InsertManifestOperation().addManifest(new Manifest.Builder(key1).setRoot(emptyTree).build(test)));
            }
        };

        ExecutorService exec = Executors.newFixedThreadPool(20);
        List<Future<?>> tasks = new ArrayList<>();
        for (int i = 0; i < 1000; ++i) {
            String name = "Create" + i;
            tasks.add(exec.submit(() -> creator.accept(name)));
        }

        FutureHelper.awaitAll(tasks);

        // debounce for last event is ~100ms. make sure we wait until that happened.
        Thread.sleep(200);

        assertEquals(1000, notifyCount.sum());
    }

}
