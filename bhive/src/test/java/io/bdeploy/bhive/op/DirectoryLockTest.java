package io.bdeploy.bhive.op;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.TestHive;
import io.bdeploy.common.util.FutureHelper;

@ExtendWith(TestHive.class)
class DirectoryLockTest {

    private static final Logger log = LoggerFactory.getLogger(DirectoryLockTest.class);

    @Test
    void testLockOperations(BHive hive, @TempDir Path tmp) {
        hive.setLockContentSupplier(() -> "1");
        hive.setLockContentValidator((c) -> c.equals("1"));

        AtomicLong sem = new AtomicLong(0);

        Runnable r = () -> {
            hive.execute(new DirectoryLockOperation().setDirectory(tmp));
            long l = sem.incrementAndGet();
            if (l != 1) {
                throw new IllegalStateException("More than one thread got the lock");
            }
            log.info("Got the lock!");
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
            log.info("Releasing the lock!");
            sem.decrementAndGet();
            hive.execute(new DirectoryReleaseOperation().setDirectory(tmp));
        };

        ExecutorService x = Executors.newFixedThreadPool(8);
        List<Future<?>> f = new ArrayList<>();

        for (int i = 0; i < 8; ++i) {
            f.add(x.submit(r));
        }

        // Should not throw any exception. Also, there should not be any "stale lock file detected" in the logs.
        FutureHelper.awaitAll(f);
    }
}
