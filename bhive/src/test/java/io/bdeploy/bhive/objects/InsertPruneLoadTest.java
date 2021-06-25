package io.bdeploy.bhive.objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.BHiveTransactions.Transaction;
import io.bdeploy.bhive.TestHive;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.model.Tree;
import io.bdeploy.bhive.objects.view.ElementView;
import io.bdeploy.bhive.op.FsckOperation;
import io.bdeploy.bhive.op.ImportObjectOperation;
import io.bdeploy.bhive.op.InsertArtificialTreeOperation;
import io.bdeploy.bhive.op.InsertManifestOperation;
import io.bdeploy.bhive.op.ManifestDeleteOldByIdOperation;
import io.bdeploy.bhive.op.ManifestMaxIdOperation;
import io.bdeploy.bhive.op.ManifestNextIdOperation;
import io.bdeploy.bhive.op.PruneOperation;
import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.common.ActivityReporter;

/**
 * This tests simultaneous writes/prunes on multiple threads. The same test can be run through
 * gradle in multiple JVMs to test/simulate multiple JVMs doing the same to a single BHive.
 * <p>
 * To do this, use this command as a template:
 *
 * <pre>
 * $ ./gradlew :bhive:test \
 *      --tests io.bdeploy.bhive.objects.InsertPruneLoadTest \
 *      -Ptest.hive.override=E:/path/to/empty/dir \
 *      -Ptest.manifestName.override=X
 * </pre>
 *
 * Replace 'X' with a unique string per started JVM to avoid manifest name collisions in the test.
 */
@ExtendWith(TestHive.class)
public class InsertPruneLoadTest {

    private static final Logger log = LoggerFactory.getLogger(InsertPruneLoadTest.class);

    @ParameterizedTest
    @ValueSource(ints = { 1, 3, 7 })
    void testMulti(int producerCount, BHive tstHive) throws Exception {
        BHive hive;
        String override = System.getProperty("test.hive.override");
        if (override != null) {
            hive = new BHive(Paths.get(override).toUri(), new ActivityReporter.Null());
        } else {
            hive = tstHive;
        }

        LongAdder productions = new LongAdder();
        LongAdder prunes = new LongAdder();
        LongAdder errors = new LongAdder();
        AtomicBoolean stop = new AtomicBoolean(false);

        List<Thread> threads = new ArrayList<>();

        for (int x = 0; x < producerCount; ++x) {
            int num = x;
            threads.add(new Thread(() -> {
                while (!stop.get()) {
                    try {
                        produce(hive, errors, num);
                        productions.increment();
                    } catch (Exception e) {
                        errors.increment();
                        log.error("Error in producer", e);
                    }
                }
            }, "Producer-" + x));

            threads.add(new Thread(() -> {
                while (!stop.get()) {
                    try {
                        prune(hive, errors);
                        prunes.increment();
                    } catch (Exception e) {
                        errors.increment();
                        log.error("Error in pruner", e);
                    }
                }
            }, "Pruner-" + x));
        }

        threads.forEach(t -> t.start());

        // let the jobs work for some time :)
        TimeUnit.SECONDS.sleep(producerCount * 10);

        stop.set(true);
        threads.forEach(t -> {
            try {
                t.join(30_000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        threads.forEach(t -> {
            if (t.isAlive()) {
                errors.add(1);
                log.error("Thread refused to stop: " + t.getName());
                t.interrupt();
            }
        });
        threads.forEach(t -> {
            try {
                t.join(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });

        log.info("Performed {} productions and {} prunes in this JVM.", productions.sum(), prunes.sum());

        Set<ElementView> damaged = hive.execute(new FsckOperation());

        if (!damaged.isEmpty()) {
            for (ElementView dmg : damaged) {
                log.error("{}: {}", dmg.getElementId(), dmg.getPathString());
            }
            assertTrue(damaged.isEmpty());
        }

        assertEquals(0, errors.sum());
    }

    void prune(BHive hive, LongAdder errors) {
        hive.execute(new PruneOperation());
        try {
            TimeUnit.MILLISECONDS.sleep((long) (Math.random() * 10));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    void produce(BHive hive, LongAdder errors, int num) {
        long start = System.currentTimeMillis();

        String manifestName = System.getProperty("test.manifestName.override", "X") + num;
        String content = "{" + start + "_" + num + "}";

        Optional<Long> currentId = hive.execute(new ManifestMaxIdOperation().setManifestName(manifestName));
        if (currentId.isPresent()) {
            hive.execute(new FsckOperation().addManifest(new Manifest.Key(manifestName, currentId.get().toString())));
        }

        Long newId = hive.execute(new ManifestNextIdOperation().setManifestName(manifestName));
        Manifest.Builder mfb = new Manifest.Builder(new Manifest.Key(manifestName, newId.toString()));

        List<ObjectId> oids = new ArrayList<>();
        try (Transaction t = hive.getTransactions().begin()) {
            ObjectId descOid = hive.execute(new ImportObjectOperation().setData(StorageHelper.toRawBytes(content)));
            Tree.Builder tb = new Tree.Builder().add(new Tree.Key("file.x", Tree.EntryType.BLOB), descOid);
            ObjectId treeOid = hive.execute(new InsertArtificialTreeOperation().setTree(tb));
            mfb.setRoot(treeOid);

            oids.add(descOid);
            oids.add(treeOid);

            hive.execute(new InsertManifestOperation().addManifest(mfb.build(hive)));
        }

        hive.execute(new ManifestDeleteOldByIdOperation().setToDelete(manifestName).setAmountToKeep(1));

        log.info("Producer: {} {} took {}ms.", newId, oids, System.currentTimeMillis() - start);
    }

}
