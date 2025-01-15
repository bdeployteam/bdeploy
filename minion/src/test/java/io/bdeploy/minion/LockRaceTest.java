package io.bdeploy.minion;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.BHiveTransactions;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.op.ImportOperation;
import io.bdeploy.bhive.op.remote.PushOperation;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.ContentHelper;
import io.bdeploy.common.audit.NullAuditor;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.FutureHelper;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.common.util.UuidHelper;
import io.bdeploy.interfaces.configuration.instance.InstanceGroupConfiguration;
import io.bdeploy.interfaces.remote.CommonRootResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.ui.api.HiveResource;

@ExtendWith(TestMinion.class)
class LockRaceTest {

    private static final Logger log = LoggerFactory.getLogger(LockRaceTest.class);

    @Disabled("Manual Test for lock file race conditions - read instructions below!")
    @Test
    void testLockRace(MinionRoot root, RemoteService remote, @TempDir Path tmp) {

        // ATTENTION: Instructions:
        // you need to make actions FSCK_BHIVE and PRUNE_BHIVE non-exclusive. this is to enable us to
        // storm the prune operation on the hive, which will race for locks.

        root.modifyState(s -> s.poolUsageThreshold = 1);
        root.modifyState(s -> s.poolOrganizationSchedule = "* * * * * ?"); // run every second

        // trigger cleanup once to force new schedule
        for (var x : root.listJobs()) {
            if ("PoolReorgJob".equals(x.name)) {
                root.runJob(x);
            }
        }

        List<String> groups = List.of("GroupA", "GroupB");
        groups.forEach(g -> setupGroup(g, remote));

        AtomicBoolean running = new AtomicBoolean(true);
        AtomicLong tid = new AtomicLong(0);
        CompletableFuture<?> block = new CompletableFuture<>();
        List<Future<?>> futures = new CopyOnWriteArrayList<>();
        List<Thread> pruners = new ArrayList<>();
        List<Thread> others = new ArrayList<>();

        try (ExecutorService tasks = Executors.newFixedThreadPool(8);
                ExecutorService deletes = Executors.newVirtualThreadPerTaskExecutor()) {
            for (String group : groups) {
                Thread schedulerThread = Thread.startVirtualThread(() -> {
                    for (int i = 0; i < 500; ++i) {
                        futures.add(tasks.submit(() -> {
                            long localTid = tid.incrementAndGet();
                            // create data (manifest) in the hive and push it. MUST use unique data which has a high
                            // probability of not yet existing on the remote hive.
                            try {
                                log.info("Preparing data {}, {}", group, localTid);
                                Path src = Files.createTempDirectory(tmp, "t");
                                ContentHelper.genTestTree(src, 30, 10, 2, 0, 0, 0);
                                Path h = Files.createTempDirectory(tmp, "h");
                                try (BHive impHive = new BHive(h.toUri(), new NullAuditor(), new ActivityReporter.Null());
                                        BHiveTransactions.Transaction t = impHive.getTransactions().begin()) {
                                    Manifest.Key k = new Manifest.Key(UuidHelper.randomId(), "0");
                                    impHive.execute(new ImportOperation().setSourcePath(src).setManifest(k));

                                    PathHelper.deleteRecursiveRetry(src);

                                    log.info("Pushing data {}, {}", group, localTid);
                                    impHive.execute(new PushOperation().setHiveName(group).addManifest(k).setRemote(remote));

                                    futures.add(deletes.submit(() -> {
                                        // wait a while until we remove the pushed manifest again for cleanup.
                                        silentSleep(3000);

                                        log.info("Deleting data {}, {}", group, localTid);
                                        ResourceProvider.getResource(remote, HiveResource.class, null).delete(group, k.getName(),
                                                k.getTag());
                                    }));
                                }
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }));
                    }

                    log.info("Done submitting {}", group);
                    block.complete(null);
                });

                for (int i = 0; i < 5; ++i) {
                    Thread prunerThread = Thread.startVirtualThread(() -> {
                        // delay at the start to give the imports a chance to warm up
                        silentSleep(10_000);

                        log.info("Begin hot pruning {}", group);
                        while (running.get()) {
                            try {
                                ResourceProvider.getResource(remote, HiveResource.class, null).repairAndPrune(group, true);
                            } catch (Exception e) {
                                // log but continue
                                log.error("Prune failed", e);
                            }

                            // make it a *LITTLE* less hot.
                            silentSleep(200);
                        }
                    });
                    pruners.add(prunerThread);
                }

                others.add(schedulerThread);
            }

            block.join(); // after all tasks have been submitted...
            FutureHelper.awaitAll(futures); // ...and wait for all to finish.

            running.set(false); // stop the pruner scheduling.
            for (Thread t : pruners) {
                t.interrupt();
                t.join();
            }
            others.forEach(Thread::interrupt);

            tasks.shutdownNow();
            deletes.shutdownNow();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static void setupGroup(String groupName, RemoteService remote) {
        InstanceGroupConfiguration cfg = new InstanceGroupConfiguration();

        cfg.name = groupName;
        cfg.description = groupName;
        cfg.title = groupName;

        ResourceProvider.getResource(remote, CommonRootResource.class, null).addInstanceGroup(cfg, null);
    }

    private static void silentSleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

}
