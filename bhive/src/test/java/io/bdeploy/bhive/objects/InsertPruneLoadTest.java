package io.bdeploy.bhive.objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.BHiveTestUtils;
import io.bdeploy.bhive.TestHive;
import io.bdeploy.bhive.objects.view.ElementView;
import io.bdeploy.bhive.op.FsckOperation;
import io.bdeploy.bhive.op.ManifestDeleteOldByIdOperation;
import io.bdeploy.bhive.op.PruneOperation;
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
@Disabled(value = "Produces heavy load and *sometimes* still fails on windows with production-irrelevant errors")
@ExtendWith(TestHive.class)
class InsertPruneLoadTest {

    private static final Logger log = LoggerFactory.getLogger(InsertPruneLoadTest.class);

    @ParameterizedTest
    @ValueSource(ints = { 1, 3, 7 })
    void testMulti(int producerCount, BHive tstHive) throws Exception {
        BHive hive;
        String override = System.getProperty("test.hive.override");
        if (override != null) {
            hive = new BHive(Paths.get(override).toUri(), null, new ActivityReporter.Null());
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
                        produce(hive, num);
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
                        prune(hive);
                        prunes.increment();
                    } catch (Exception e) {
                        errors.increment();
                        log.error("Error in pruner", e);
                    }
                }
            }, "Pruner-" + x));
        }

        threads.forEach(Thread::start);

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
                log.error("Thread refused to stop: {}", t.getName());
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

    void prune(BHive hive) {
        hive.execute(new PruneOperation());
        try {
            TimeUnit.MILLISECONDS.sleep(new Random().nextInt(10));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    static void produce(BHive hive, int num) {
        String manifestName = System.getProperty("test.manifestName.override", "X") + num;

        BHiveTestUtils.createManifest(hive, manifestName, false);

        hive.execute(new ManifestDeleteOldByIdOperation().setToDelete(manifestName).setAmountToKeep(1));
    }

}
