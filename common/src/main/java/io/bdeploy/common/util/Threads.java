package io.bdeploy.common.util;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.function.BooleanSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common helpers for waiting and sleeping.
 */
public class Threads {

    private static final Logger log = LoggerFactory.getLogger(Threads.class);

    private Threads() {
    }

    /**
     * Waits until notified or interrupted or the condition is met. The caller needs to be the owner of the lock.
     *
     * @return {@code false} if the thread was interrupted while waiting
     */
    public static boolean wait(Object lock, BooleanSupplier condition) {
        try {
            synchronized (lock) {
                while (condition.getAsBoolean()) {
                    lock.wait();
                }
            }
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Causes the current thread to sleep the given amount of time or until interrupted.
     *
     * @param millis the length of time to sleep in milliseconds
     * @return {@code false} if the thread was interrupted while sleeping
     */
    public static boolean sleep(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static void cycleDumps(Path name) {
        // reverse move files to keep max 4 (three numbered 1 through 3).
        for (int i = 2; i >= 0; --i) {
            rollOverFiles(name, i);
        }
    }

    private static void rollOverFiles(Path filePath, int count) {
        Path parent = filePath.getParent();
        String fileName = filePath.getFileName().toString();

        Path source = parent.resolve(fileName + (count > 0 ? ("." + count) : ""));
        if (!Files.exists(source)) {
            return;
        }

        Path target = parent.resolve(fileName + "." + (count + 1));
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("Cannot move {} to {}", source, target);
        }
    }

    /**
     * Create a stackdump of the running server.
     * 
     * @param targetDir the target directory to create dumps in
     * @param dumpName the dump base name. existing dumps will be cycled (max 3 dumps).
     */
    public static void dump(Path targetDir, String dumpName) {
        Thread dumpThread = new Thread(() -> {
            Path outputName = targetDir.resolve(dumpName);
            cycleDumps(outputName);
            try (PrintWriter pw = new PrintWriter(Files.newOutputStream(outputName))) {
                ThreadMXBean mxb = ManagementFactory.getThreadMXBean();
                for (ThreadInfo i : mxb.dumpAllThreads(true, true)) {
                    pw.append(i.toString());
                }
            } catch (Exception e) {
                log.warn("Cannot create thread dump.", e);
            }
        });

        dumpThread.start();

        // give the trace a maximum of 10 seconds time.
        try {
            dumpThread.join(Duration.ofSeconds(10).toMillis());
        } catch (InterruptedException e) {
            // ignored, we want to restart anyhow!
        }
    }

}
