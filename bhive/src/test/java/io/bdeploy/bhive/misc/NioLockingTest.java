/*
 * Copyright (c) SSI Schaefer IT Solutions GmbH
 */
package io.bdeploy.bhive.misc;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

import io.bdeploy.common.util.PathHelper;

class NioLockingTest {

    @Test
    void testLockingPerformance() throws IOException {
        Path lockFile = Files.createTempFile("locktest-", ".tmp");
        try {
            long start = System.currentTimeMillis();
            for (int i = 0; i < 10_000; ++i) {
                try (RandomAccessFile raf = new RandomAccessFile(lockFile.toFile(), "rw");
                        FileChannel channel = raf.getChannel();
                        FileLock lock = channel.lock()) {
                    assertTrue(lock.isValid());
                    lock.release();
                }
            }
            System.out.println("raw time: " + (System.currentTimeMillis() - start) + "ms.");
        } finally {
            PathHelper.deleteIfExistsRetry(lockFile);
        }
    }

    @Test
    void testTwoThreadLock() throws Exception {
        Path lockFile = Files.createTempFile("locktest-", ".tmp");
        ExecutorService pool = Executors.newFixedThreadPool(4);
        long start = System.currentTimeMillis();
        List<Future<Long>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < 10_000; ++i) {
                futures.add(pool.submit(() -> doLocked(lockFile)));
            }

            for (Future<Long> f : futures) {
                long l = f.get();
                assertTrue(l >= start);
                assertTrue(l <= System.currentTimeMillis());
            }
        } finally {
            PathHelper.deleteIfExistsRetry(lockFile);
        }
    }

    private synchronized static long doLocked(Path lockFile) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(lockFile.toFile(), "rw");
                FileChannel channel = raf.getChannel();
                FileLock lock = channel.lock()) {
            return System.currentTimeMillis(); // dummy
        }
    }

}
