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

import org.junit.jupiter.api.Test;

public class NioLockingTest {

    @Test
    public void testLockingPerformance() throws IOException {
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
            Files.deleteIfExists(lockFile);
        }
    }

}
