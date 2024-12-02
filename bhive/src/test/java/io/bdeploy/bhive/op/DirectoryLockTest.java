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

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.TestHive;
import io.bdeploy.common.util.FutureHelper;

@ExtendWith(TestHive.class)
class DirectoryLockTest {

    @Test
    void testLockOperations(BHive hive, @TempDir Path tmp) {
        hive.setLockContentSupplier(() -> "12345678");
        hive.setLockContentValidator(c -> c.equals("12345678"));

        AtomicLong sem = new AtomicLong(0);

        Runnable r = () -> {
            var lck = hive.execute(new DirectoryLockOperation().setDirectory(tmp));
            long l = sem.incrementAndGet();
            if (l != 1) {
                throw new IllegalStateException("More than one thread got the lock");
            }
            sem.decrementAndGet();
            lck.unlock();
        };

        ExecutorService x = Executors.newFixedThreadPool(16);
        List<Future<?>> f = new ArrayList<>();

        for (int i = 0; i < 10000; ++i) {
            f.add(x.submit(r));
        }

        // Should not throw any exception. Also, there should not be any "stale lock file detected" in the logs.
        FutureHelper.awaitAll(f);
    }
}
